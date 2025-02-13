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
package org.neo4j.index.internal.gbptree;

import static java.lang.ProcessBuilder.Redirect.INHERIT;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.neo4j.index.internal.gbptree.GBPTree.NO_HEADER_READER;
import static org.neo4j.index.internal.gbptree.SimpleLongLayout.longLayout;
import static org.neo4j.io.pagecache.context.CursorContext.NULL_CONTEXT;
import static org.neo4j.io.pagecache.impl.muninn.MuninnPageCache.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import org.eclipse.collections.api.factory.Sets;
import org.junit.jupiter.api.Test;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.impl.SingleFilePageSwapperFactory;
import org.neo4j.io.pagecache.impl.muninn.MuninnPageCache;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.scheduler.JobScheduler;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.pagecache.PageCacheExtension;
import org.neo4j.test.proc.ProcessUtil;
import org.neo4j.test.scheduler.ThreadPoolJobScheduler;
import org.neo4j.test.utils.TestDirectory;

/**
 * Tests functionality around process crashing, or similar, when having started, but not completed creation of an index file,
 * i.e. opening an index file for the first time.
 *
 * This test is an asset in finding potentially new issues regarding partially created index files over time.
 * It will not guarantee, in one run, that every case has been covered. There are other specific test cases for that.
 * When this test finds a new issue that should be encoded into a proper unit test in {@link GBPTreeTest} or similar.
 */
@PageCacheExtension
class GBPTreePartialCreateFuzzIT {
    @Inject
    private TestDirectory testDirectory;

    @Inject
    private FileSystemAbstraction fileSystem;

    @Inject
    private PageCache pageCache;

    @Test
    void shouldDetectAndThrowIOExceptionOnPartiallyCreatedFile() throws Exception {
        // given a crashed-on-open index
        Path file = testDirectory.file("index");
        Process process = ProcessUtil.start(
                pb -> pb.redirectError(INHERIT).redirectOutput(INHERIT),
                getClass().getName(),
                file.toAbsolutePath().toString());
        Thread.sleep(ThreadLocalRandom.current().nextInt(1_000));
        int exitCode = process.destroyForcibly().waitFor();

        // then reading it should either work or throw IOException
        SimpleLongLayout layout = longLayout().build();

        // check readHeader
        try {
            GBPTree.readHeader(
                    pageCache,
                    file,
                    NO_HEADER_READER,
                    testDirectory.homePath().getFileName().toString(),
                    NULL_CONTEXT,
                    Sets.immutable.empty());
        } catch (MetadataMismatchException | IOException e) {
            // It's OK if the process was destroyed
            assertNotEquals(0, exitCode);
        }

        // check constructor
        try {
            new GBPTreeBuilder<>(pageCache, fileSystem, file, layout).build().close();
        } catch (MetadataMismatchException | IOException e) {
            // It's OK if the process was destroyed
            assertNotEquals(0, exitCode);
        }
    }

    public static void main(String[] args) throws Exception {
        // Just start and immediately close. The process spawning this subprocess will kill it in the middle of all this
        Path file = Path.of(args[0]);
        try (FileSystemAbstraction fs = new DefaultFileSystemAbstraction();
                JobScheduler jobScheduler = new ThreadPoolJobScheduler()) {
            SingleFilePageSwapperFactory swapper =
                    new SingleFilePageSwapperFactory(fs, PageCacheTracer.NULL, EmptyMemoryTracker.INSTANCE);
            try (PageCache pageCache = new MuninnPageCache(swapper, jobScheduler, config(10))) {
                fs.deleteFile(file);
                new GBPTreeBuilder<>(pageCache, fs, file, longLayout().build())
                        .build()
                        .close();
            }
        }
    }
}
