/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;
import static org.neo4j.io.pagecache.context.CursorContext.NULL_CONTEXT;
import static org.neo4j.io.pagecache.impl.muninn.StandalonePageCacheFactory.createPageCache;
import static org.neo4j.logging.LogAssertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.dbms.DatabaseStateService;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilderImplementation;
import org.neo4j.graphdb.DatabaseShutdownException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.facade.DatabaseManagementServiceFactory;
import org.neo4j.graphdb.facade.ExternalDependencies;
import org.neo4j.graphdb.factory.module.GlobalModule;
import org.neo4j.graphdb.factory.module.edition.CommunityEditionModule;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.io.fs.EphemeralFileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.CommonDatabaseStores;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.layout.Neo4jLayout;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.kernel.impl.factory.DbmsInfo;
import org.neo4j.kernel.impl.store.MetaDataStore;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.storageengine.api.StoreId;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.Neo4jLayoutExtension;
import org.neo4j.test.scheduler.ThreadPoolJobScheduler;

@Neo4jLayoutExtension
class DatabaseStartupTest {
    @Inject
    FileSystemAbstraction fs;

    @Inject
    private Neo4jLayout neoLayout;

    @Test
    void startStop() {
        for (int i = 0; i < 50; i++) {
            DatabaseManagementService dbms = new TestDatabaseManagementServiceBuilder(neoLayout).build();
            GraphDatabaseService db = dbms.database(DEFAULT_DATABASE_NAME);
            assertTrue(db.isAvailable());
            dbms.shutdown();
        }
    }

    @Test
    void startDatabaseWithWrongVersionShouldFail() throws Throwable {
        // given
        // create a store

        DatabaseManagementService managementService = new TestDatabaseManagementServiceBuilder(neoLayout).build();
        GraphDatabaseAPI db = (GraphDatabaseAPI) managementService.database(DEFAULT_DATABASE_NAME);
        try (Transaction tx = db.beginTx()) {
            tx.createNode();
            tx.commit();
        }
        DatabaseLayout databaseLayout = db.databaseLayout();
        managementService.shutdown();

        // mess up the version in the metadatastore
        try (FileSystemAbstraction fileSystem = new DefaultFileSystemAbstraction();
                ThreadPoolJobScheduler scheduler = new ThreadPoolJobScheduler();
                PageCache pageCache = createPageCache(fileSystem, scheduler, PageCacheTracer.NULL)) {
            var fieldAccess = MetaDataStore.getFieldAccess(
                    pageCache,
                    databaseLayout.pathForStore(CommonDatabaseStores.METADATA),
                    databaseLayout.getDatabaseName(),
                    NULL_CONTEXT);
            StoreId originalId = fieldAccess.readStoreId();
            fieldAccess.writeStoreId(
                    new StoreId(originalId.getCreationTime(), originalId.getRandom(), "bad", "even_worse", 1, 1));
        }

        managementService = new TestDatabaseManagementServiceBuilder(databaseLayout).build();
        GraphDatabaseAPI databaseService = (GraphDatabaseAPI) managementService.database(DEFAULT_DATABASE_NAME);
        try {

            assertThrows(DatabaseShutdownException.class, databaseService::beginTx);
            DatabaseStateService dbStateService =
                    databaseService.getDependencyResolver().resolveDependency(DatabaseStateService.class);
            assertTrue(
                    dbStateService.causeOfFailure(databaseService.databaseId()).isPresent());
            assertThat(dbStateService
                            .causeOfFailure(databaseService.databaseId())
                            .get())
                    .hasRootCauseExactlyInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("Unknown store version 'bad-even_worse-1.1'");
        } finally {
            managementService.shutdown();
        }
    }

    @Test
    void startDatabaseWithWrongTransactionFilesShouldFail() throws IOException {
        // Create a store
        DatabaseManagementService managementService = new TestDatabaseManagementServiceBuilder(neoLayout).build();
        GraphDatabaseAPI db = (GraphDatabaseAPI) managementService.database(DEFAULT_DATABASE_NAME);
        DatabaseLayout databaseLayout = db.databaseLayout();
        try (Transaction tx = db.beginTx()) {
            tx.createNode();
            tx.commit();
        }
        managementService.shutdown();

        // Change store id component
        try (FileSystemAbstraction fileSystem = new DefaultFileSystemAbstraction();
                ThreadPoolJobScheduler scheduler = new ThreadPoolJobScheduler();
                PageCache pageCache = createPageCache(fileSystem, scheduler, PageCacheTracer.NULL)) {
            var fieldAccess = MetaDataStore.getFieldAccess(
                    pageCache,
                    databaseLayout.pathForStore(CommonDatabaseStores.METADATA),
                    databaseLayout.getDatabaseName(),
                    NULL_CONTEXT);
            StoreId originalId = fieldAccess.readStoreId();
            fieldAccess.writeStoreId(new StoreId(
                    System.currentTimeMillis() + 1,
                    originalId.getRandom(),
                    originalId.getStorageEngineName(),
                    originalId.getFormatName(),
                    originalId.getMajorVersion(),
                    originalId.getMinorVersion()));
        }

        // Try to start
        managementService = new TestDatabaseManagementServiceBuilder(databaseLayout).build();
        try {
            db = (GraphDatabaseAPI) managementService.database(DEFAULT_DATABASE_NAME);
            assertFalse(db.isAvailable(10));

            DatabaseStateService dbStateService =
                    db.getDependencyResolver().resolveDependency(DatabaseStateService.class);
            Optional<Throwable> cause = dbStateService.causeOfFailure(db.databaseId());
            assertTrue(cause.isPresent());
            assertTrue(cause.get().getCause().getMessage().contains("Mismatching store id"));
        } finally {
            managementService.shutdown();
        }
    }

    @Test
    void startDatabaseWithoutStoreFilesAndWithTransactionLogFilesFailure() throws IOException {
        // Create a store
        DatabaseManagementService managementService = new TestDatabaseManagementServiceBuilder(neoLayout).build();
        GraphDatabaseAPI db = (GraphDatabaseAPI) managementService.database(DEFAULT_DATABASE_NAME);
        DatabaseLayout databaseLayout = db.databaseLayout();
        try (Transaction tx = db.beginTx()) {
            tx.createNode();
            tx.commit();
        }
        managementService.shutdown();

        fs.deleteRecursively(databaseLayout.databaseDirectory());

        // Try to start
        managementService = new TestDatabaseManagementServiceBuilder(databaseLayout).build();
        try {
            db = (GraphDatabaseAPI) managementService.database(DEFAULT_DATABASE_NAME);
            assertFalse(db.isAvailable(10));

            DatabaseStateService dbStateService =
                    db.getDependencyResolver().resolveDependency(DatabaseStateService.class);
            Optional<Throwable> cause = dbStateService.causeOfFailure(db.databaseId());
            assertTrue(cause.isPresent());
            assertThat(cause.get())
                    .hasStackTraceContaining("Fail to start '" + db.databaseId()
                            + "' since transaction logs were found, while database ");
        } finally {
            managementService.shutdown();
        }
    }

    @Test
    void startTestDatabaseOnProvidedNonAbsoluteFile() {
        Path directory = Path.of("target/notAbsoluteDirectory");
        DatabaseManagementService managementService = new TestDatabaseManagementServiceBuilder(directory)
                .impermanent()
                .build();
        managementService.shutdown();
    }

    @Test
    void startCommunityDatabaseOnProvidedNonAbsoluteFile() {
        Path directory = Path.of("target/notAbsoluteDirectory");
        EphemeralCommunityManagementServiceFactory factory = new EphemeralCommunityManagementServiceFactory();
        EphemeralDatabaseManagementServiceBuilder databaseFactory =
                new EphemeralDatabaseManagementServiceBuilder(directory, factory);
        DatabaseManagementService managementService = databaseFactory.build();
        managementService.database(DEFAULT_DATABASE_NAME);
        managementService.shutdown();
    }

    @Test
    void dumpSystemDiagnosticLoggingOnStartup() {
        AssertableLogProvider logProvider = new AssertableLogProvider();
        DatabaseManagementService managementService = new TestDatabaseManagementServiceBuilder(neoLayout)
                .setInternalLogProvider(logProvider)
                .setConfig(GraphDatabaseInternalSettings.dump_diagnostics, true)
                .build();
        managementService.database(DEFAULT_DATABASE_NAME);
        try {
            assertThat(logProvider)
                    .containsMessages(
                            "System diagnostics",
                            "System memory information",
                            "JVM memory information",
                            "Operating system information",
                            "JVM information",
                            "Java classpath",
                            "Library path",
                            "System properties",
                            "(IANA) TimeZone database version",
                            "Network information",
                            "DBMS config");
        } finally {
            managementService.shutdown();
        }
    }

    private static class EphemeralCommunityManagementServiceFactory extends DatabaseManagementServiceFactory {
        EphemeralCommunityManagementServiceFactory() {
            super(DbmsInfo.COMMUNITY, CommunityEditionModule::new);
        }

        @Override
        protected GlobalModule createGlobalModule(Config config, ExternalDependencies dependencies) {
            return new GlobalModule(config, dbmsInfo, dependencies) {
                @Override
                protected FileSystemAbstraction createFileSystemAbstraction() {
                    return new EphemeralFileSystemAbstraction();
                }
            };
        }
    }

    private static class EphemeralDatabaseManagementServiceBuilder
            extends DatabaseManagementServiceBuilderImplementation {
        private final EphemeralCommunityManagementServiceFactory factory;

        EphemeralDatabaseManagementServiceBuilder(
                Path homeDirectory, EphemeralCommunityManagementServiceFactory factory) {
            super(homeDirectory);
            this.factory = factory;
        }

        @Override
        protected DatabaseManagementService newDatabaseManagementService(
                Config config, ExternalDependencies dependencies) {
            return factory.build(augmentConfig(config), dependencies);
        }
    }
}
