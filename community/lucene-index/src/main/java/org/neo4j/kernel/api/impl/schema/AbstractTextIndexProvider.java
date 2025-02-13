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
package org.neo4j.kernel.api.impl.schema;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import java.io.IOException;
import java.nio.file.OpenOption;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.set.ImmutableSet;
import org.neo4j.configuration.Config;
import org.neo4j.internal.kernel.api.InternalIndexState;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.IndexPrototype;
import org.neo4j.internal.schema.IndexProviderDescriptor;
import org.neo4j.internal.schema.IndexType;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.io.pagecache.context.CursorContextFactory;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.kernel.api.impl.index.LuceneMinimalIndexAccessor;
import org.neo4j.kernel.api.impl.index.MinimalDatabaseIndex;
import org.neo4j.kernel.api.impl.index.SchemaIndexMigrator;
import org.neo4j.kernel.api.impl.index.storage.DirectoryFactory;
import org.neo4j.kernel.api.impl.index.storage.IndexStorageFactory;
import org.neo4j.kernel.api.impl.index.storage.PartitionedIndexStorage;
import org.neo4j.kernel.api.index.IndexDirectoryStructure;
import org.neo4j.kernel.api.index.IndexProvider;
import org.neo4j.kernel.api.index.MinimalIndexAccessor;
import org.neo4j.kernel.impl.index.schema.IndexUpdateIgnoreStrategy;
import org.neo4j.monitoring.Monitors;
import org.neo4j.storageengine.api.StorageEngineFactory;
import org.neo4j.storageengine.migration.StoreMigrationParticipant;
import org.neo4j.util.VisibleForTesting;
import org.neo4j.values.storable.ValueCategory;

public abstract class AbstractTextIndexProvider extends IndexProvider {
    // Ignore everything except TEXT values
    public static final IndexUpdateIgnoreStrategy UPDATE_IGNORE_STRATEGY =
            values -> values[0].valueGroup().category() != ValueCategory.TEXT;

    private final IndexStorageFactory indexStorageFactory;
    private final Config config;
    private final Monitor monitor;
    private final IndexType supportedIndexType;

    public AbstractTextIndexProvider(
            IndexType supportedIndexType,
            IndexProviderDescriptor descriptor,
            FileSystemAbstraction fileSystem,
            DirectoryFactory directoryFactory,
            IndexDirectoryStructure.Factory directoryStructureFactory,
            Monitors monitors,
            Config config) {
        super(descriptor, directoryStructureFactory);
        this.supportedIndexType = supportedIndexType;
        this.monitor = monitors.newMonitor(Monitor.class, descriptor.toString());
        this.indexStorageFactory = buildIndexStorageFactory(fileSystem, directoryFactory);
        this.config = config;
    }

    @VisibleForTesting
    protected IndexStorageFactory buildIndexStorageFactory(
            FileSystemAbstraction fileSystem, DirectoryFactory directoryFactory) {
        return new IndexStorageFactory(directoryFactory, fileSystem, directoryStructure());
    }

    @Override
    public void validatePrototype(IndexPrototype prototype) {
        IndexType indexType = prototype.getIndexType();
        if (indexType != supportedIndexType) {
            String providerName = getProviderDescriptor().name();
            throw new IllegalArgumentException("The '" + providerName + "' index provider does not support " + indexType
                    + " indexes: " + prototype);
        }
        if (prototype.isUnique()) {
            throw new IllegalArgumentException("The '" + getProviderDescriptor().name()
                    + "' index provider does not support unique indexes: " + prototype);
        }
    }

    @Override
    public MinimalIndexAccessor getMinimalIndexAccessor(IndexDescriptor descriptor) {
        PartitionedIndexStorage indexStorage = indexStorageFactory.indexStorageOf(descriptor.getId());
        var index = new MinimalDatabaseIndex<>(indexStorage, descriptor, config);
        return new LuceneMinimalIndexAccessor<>(descriptor, index, true);
    }

    @Override
    public InternalIndexState getInitialState(
            IndexDescriptor descriptor, CursorContext cursorContext, ImmutableSet<OpenOption> openOptions) {
        PartitionedIndexStorage indexStorage = getIndexStorage(descriptor.getId());
        String failure = indexStorage.getStoredIndexFailure();
        if (failure != null) {
            return InternalIndexState.FAILED;
        }
        try {
            return indexIsOnline(indexStorage, descriptor, config)
                    ? InternalIndexState.ONLINE
                    : InternalIndexState.POPULATING;
        } catch (IOException e) {
            monitor.failedToOpenIndex(descriptor, "Requesting re-population.", e);
            return InternalIndexState.POPULATING;
        }
    }

    @Override
    public StoreMigrationParticipant storeMigrationParticipant(
            final FileSystemAbstraction fs,
            PageCache pageCache,
            PageCacheTracer pageCacheTracer,
            StorageEngineFactory storageEngineFactory,
            CursorContextFactory contextFactory) {
        return new SchemaIndexMigrator(
                getProviderDescriptor().name(),
                fs,
                pageCache,
                pageCacheTracer,
                this.directoryStructure(),
                storageEngineFactory,
                contextFactory);
    }

    @Override
    public String getPopulationFailure(
            IndexDescriptor descriptor, CursorContext cursorContext, ImmutableSet<OpenOption> openOptions)
            throws IllegalStateException {
        return defaultIfEmpty(getIndexStorage(descriptor.getId()).getStoredIndexFailure(), StringUtils.EMPTY);
    }

    protected PartitionedIndexStorage getIndexStorage(long indexId) {
        return indexStorageFactory.indexStorageOf(indexId);
    }

    public static boolean indexIsOnline(PartitionedIndexStorage indexStorage, IndexDescriptor descriptor, Config config)
            throws IOException {
        try (var index = new MinimalDatabaseIndex<>(indexStorage, descriptor, config)) {
            if (index.exists()) {
                index.open();
                return index.isOnline();
            }
            return false;
        }
    }

    @Override
    public IndexDescriptor completeConfiguration(IndexDescriptor index) {
        return index;
    }
}
