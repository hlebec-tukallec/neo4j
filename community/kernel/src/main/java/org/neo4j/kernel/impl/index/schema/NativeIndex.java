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
package org.neo4j.kernel.impl.index.schema;

import static org.neo4j.index.internal.gbptree.GBPTree.NO_HEADER_READER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.eclipse.collections.api.set.ImmutableSet;
import org.neo4j.annotations.documented.ReporterFactory;
import org.neo4j.dbms.database.readonly.DatabaseReadOnlyChecker;
import org.neo4j.index.internal.gbptree.GBPTree;
import org.neo4j.index.internal.gbptree.GBPTreeConsistencyCheckVisitor;
import org.neo4j.index.internal.gbptree.MultiRootGBPTree;
import org.neo4j.index.internal.gbptree.RecoveryCleanupWorkCollector;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.io.IOUtils;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.PageCursor;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.io.pagecache.context.CursorContextFactory;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.kernel.api.index.IndexProvider;
import org.neo4j.monitoring.Monitors;

abstract class NativeIndex<KEY extends NativeIndexKey<KEY>> implements ConsistencyCheckable {
    final PageCache pageCache;
    final IndexFiles indexFiles;
    final IndexLayout<KEY> layout;
    final FileSystemAbstraction fileSystem;
    final IndexDescriptor descriptor;
    private final Monitors monitors;
    private final String monitorTag;
    private final DatabaseReadOnlyChecker readOnlyChecker;
    final String databaseName;
    private final CursorContextFactory contextFactory;
    private final ImmutableSet<OpenOption> openOptions;
    final PageCacheTracer pageCacheTracer;

    protected GBPTree<KEY, NullValue> tree;

    NativeIndex(
            DatabaseIndexContext databaseIndexContext,
            IndexLayout<KEY> layout,
            IndexFiles indexFiles,
            IndexDescriptor descriptor,
            ImmutableSet<OpenOption> openOptions) {
        this.pageCache = databaseIndexContext.pageCache;
        this.fileSystem = databaseIndexContext.fileSystem;
        this.monitors = databaseIndexContext.monitors;
        this.monitorTag = databaseIndexContext.monitorTag;
        this.readOnlyChecker = databaseIndexContext.readOnlyChecker;
        this.databaseName = databaseIndexContext.databaseName;
        this.contextFactory = databaseIndexContext.contextFactory;
        this.pageCacheTracer = databaseIndexContext.pageCacheTracer;
        this.indexFiles = indexFiles;
        this.layout = layout;
        this.descriptor = descriptor;
        this.openOptions = openOptions;
    }

    void instantiateTree(RecoveryCleanupWorkCollector recoveryCleanupWorkCollector, Consumer<PageCursor> headerWriter) {
        ensureDirectoryExist();
        MultiRootGBPTree.Monitor monitor = treeMonitor();
        Path storeFile = indexFiles.getStoreFile();
        tree = new GBPTree<>(
                pageCache,
                fileSystem,
                storeFile,
                layout,
                monitor,
                NO_HEADER_READER,
                headerWriter,
                recoveryCleanupWorkCollector,
                readOnlyChecker,
                openOptions,
                databaseName,
                descriptor.getName(),
                contextFactory,
                pageCacheTracer);
        afterTreeInstantiation(tree);
    }

    protected void assertWritable() {
        if (readOnlyChecker.isReadOnly()) {
            throw new UnsupportedOperationException(
                    "Database currently is in read only mode and can not perform writes");
        }
    }

    protected void afterTreeInstantiation(GBPTree<KEY, NullValue> tree) { // no-op per default
    }

    private MultiRootGBPTree.Monitor treeMonitor() {
        MultiRootGBPTree.Monitor treeMonitor = monitors.newMonitor(MultiRootGBPTree.Monitor.class, monitorTag);
        IndexProvider.Monitor indexMonitor = monitors.newMonitor(IndexProvider.Monitor.class, monitorTag);
        return new IndexMonitorAdaptor(treeMonitor, indexMonitor, indexFiles, descriptor);
    }

    private void ensureDirectoryExist() {
        indexFiles.ensureDirectoryExist();
    }

    void closeTree() {
        IOUtils.closeAllUnchecked(tree);
        tree = null;
    }

    void assertOpen() {
        if (tree == null) {
            throw new IllegalStateException("Index has been closed");
        }
    }

    @Override
    public boolean consistencyCheck(ReporterFactory reporterFactory, CursorContext cursorContext) {
        return consistencyCheck(reporterFactory.getClass(GBPTreeConsistencyCheckVisitor.class), cursorContext);
    }

    private boolean consistencyCheck(GBPTreeConsistencyCheckVisitor visitor, CursorContext cursorContext) {
        try {
            return tree.consistencyCheck(visitor, cursorContext);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
