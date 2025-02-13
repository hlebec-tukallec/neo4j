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

import static java.lang.String.format;
import static java.util.Arrays.sort;

import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;

/**
 * Locks nodes as traversal goes down the tree. The locking scheme is a variant of what is known as "Better Latch Crabbing" and consists of
 * an optimistic and a pessimistic mode.
 * <p>
 * Optimistic mode uses {@link LongSpinLatch#acquireRead() read latches} all the way down to leaf.
 * Down at the leaf the latch is upgraded to write (if child pointers would have leaf/internal bit this step could be skipped).
 * If operation is unsafe (split/merge) then first an optimistic latch upgrade on parent is attempted - if successful the operation
 * can continue. Otherwise, as well as for failure to upgrade latches will result in releasing of the latches and flip to pessimistic mode.
 * <p>
 * Pessimistic mode uses {@link LongSpinLatch#acquireWrite() write latches} all the way down to leaf and performs the change.
 * Even split/merge can be done since write latches on parents are also acquired. In typical latch crabbing write latches on parents can be released
 * when traversing down if the operation on the lower level is considered safe, i.e. taking into consideration that a split could occur and
 * that the parent has space enough to hold one more key. In the case of dynamically sized keys, together with "minimal splitter", knowing the
 * size of the key to potentially insert into the parent is not known before actually doing the operation and therefore the parent latches cannot
 * be released when traversing down the tree.
 *
 * <pre>
 *     The locking scheme in picture:
 *
 *                              [  1  ]
 *                   ┌──────────┘ │ │ └───────────┐
 *                   │        ┌───┘ └───┐         │
 *                 [ 2 ]   [ 3 ]      [ 4 ]     [ 5 ]
 *
 *     Optimistic:
 *
 *     Example of non-contending, simple insertion:
 *     - Read latch on [1]
 *     - Read latch on [3]
 *     - Upgrade read latch on [3] to write
 *     - Insert into [3]
 *
 *     Example of non-contending, split insertion:
 *     - Read latch on [1]
 *     - Read latch on [3]
 *     - Upgrade read latch on [3] to write
 *     - Notice that [3] needs to split, ask to upgrade read latch on [1] to write
 *     - Split [3] and insert
 *     - Insert splitter key into [1]
 *
 *     Example of non-contending, split insertion where [1] cannot fit the bubble key:
 *     - Read latch on [1]
 *     - Read latch on [3]
 *     - Upgrade read latch on [3] to write
 *     - Notice that [3] needs to split, ask to upgrade read latch on [1] to write
 *     - Notice that [1] cannot fit the bubble key, abort and flip to pessimistic mode
 *
 *     Example of contending, simple insertion:
 *     - Read latch on [1]
 *     - Read latch on [3]
 *     - Fail upgrade read latch on [3] to write since another writer has read latch too
 *     - Flip to pessimistic mode
 *
 *     Example of contending, split insertion:
 *     - Read latch on [1]
 *     - Read latch on [3]
 *     - Notice that [3] needs to split, ask to upgrade read latch on [1] to write, but fails since another writer has read lock too
 *     - Flip to pessimistic mode
 * </pre>
 */
class LatchCrabbingCoordination implements TreeWriterCoordination {
    private final TreeNodeLatchService latchService;
    private final int leafUnderflowThreshold;
    private DepthData[] dataByDepth = new DepthData[10];
    private int depth = -1;
    private boolean pessimistic;
    private volatile long latchAcquisitionId;
    private volatile boolean latchAcquisitionIsWrite;

    LatchCrabbingCoordination(TreeNodeLatchService latchService, int leafUnderflowThreshold) {
        this.latchService = latchService;
        this.leafUnderflowThreshold = leafUnderflowThreshold;
    }

    @Override
    public boolean mustStartFromRoot() {
        return true;
    }

    @Override
    public void initialize() {
        assert depth == -1;
        this.pessimistic = false;
        inc(Stat.TOTAL_OPERATIONS);
    }

    @Override
    public void beforeTraversingToChild(long childTreeNodeId, int childPos) {
        // Acquire latch on the child node
        latchAcquisitionId = childTreeNodeId;
        latchAcquisitionIsWrite = pessimistic;
        LongSpinLatch latch =
                pessimistic ? latchService.acquireWrite(childTreeNodeId) : latchService.acquireRead(childTreeNodeId);
        latchAcquisitionId = -1;

        // Remember information about the latch
        depth++;
        if (depth >= dataByDepth.length) {
            dataByDepth = Arrays.copyOf(dataByDepth, dataByDepth.length * 2);
        }
        DepthData depthData = dataByDepth[depth];
        if (depthData == null) {
            depthData = dataByDepth[depth] = new DepthData();
        }
        depthData.latch = latch;
        depthData.latchTypeIsWrite = pessimistic;
        depthData.childPos = childPos;
    }

    @Override
    public boolean arrivedAtChild(boolean isInternal, int availableSpace, boolean isStable, int keyCount) {
        DepthData depthData = dataByDepth[depth];
        depthData.availableSpace = availableSpace;
        depthData.isStable = isStable;
        depthData.keyCount = keyCount;
        if (isInternal || pessimistic) {
            // Wait to make decision till we reach the leaf. Also if we're in pessimistic mode then bailing out isn't an
            // option.
            return true;
        }

        // If we have arrived at leaf in optimistic mode, upgrade to write latch
        boolean upgraded = tryUpgradeReadLatchToWrite(depth);
        if (!upgraded) {
            inc(Stat.FAIL_LEAF_UPGRADE);
            return false;
        }

        if (isStable) {
            if (depthData.positionedAtTheEdge()) {
                // If the leaf we're updating needs a successor and the position of this leaf in the parent is at the
                // edge
                // it means that one of its siblings sits in a neighbour parent, which isn't currently locked, so fall
                // back to pessimistic
                inc(Stat.FAIL_SUCCESSOR_SIBLING);
                return false;
            }
            return tryUpgradeUnstableParentReadLatchToWrite();
        }

        return true;
    }

    @Override
    public boolean beforeSplittingLeaf(int bubbleEntrySize) {
        inc(Stat.LEAF_SPLITS);
        if (pessimistic) {
            return true;
        }

        // We have one chance to do a simple optimization here, which is that if we can see that adding this bubble key
        // to the parent
        // without this parent change propagating any further we can try to upgrade the parent read latch and continue
        // with the leaf split
        // in optimistic mode
        DepthData parent = depth > 0 ? dataByDepth[depth - 1] : null;
        boolean parentSafe = parent != null && parent.availableSpace - bubbleEntrySize >= 0;
        if (!parentSafe) {
            // This split will cause parent split too... it's getting complicated at this point so bail out to
            // pessimistic mode
            inc(Stat.FAIL_LEAF_SPLIT_PARENT_UNSAFE);
            return false;
        }
        return tryUpgradeUnstableParentReadLatchToWrite();
    }

    @Override
    public boolean beforeRemovalFromLeaf(int sizeOfLeafEntryToRemove) {
        if (pessimistic) {
            return true;
        }

        int availableSpaceAfterRemoval = dataByDepth[depth].availableSpace + sizeOfLeafEntryToRemove;
        boolean leafWillUnderflow = availableSpaceAfterRemoval > leafUnderflowThreshold;
        if (leafWillUnderflow) {
            inc(Stat.FAIL_LEAF_UNDERFLOW);
            return false;
        }
        return true;
    }

    @Override
    public void beforeSplitInternal(long treeNodeId) {
        if (!pessimistic) {
            throw new IllegalStateException(
                    format("Unexpected split of internal node [%d] in optimistic mode", treeNodeId));
        }
    }

    @Override
    public void beforeUnderflowInLeaf(long treeNodeId) {
        if (!pessimistic) {
            throw new IllegalStateException(
                    format("Unexpected underflow of leaf node [%d] in optimistic mode", treeNodeId));
        }
    }

    @Override
    public void reset() {
        while (depth >= 0) {
            releaseLatchAtDepth(depth--);
        }
    }

    @Override
    public boolean flipToPessimisticMode() {
        reset();
        if (!pessimistic) {
            pessimistic = true;
            inc(Stat.PESSIMISTIC);
            return true;
        }
        return false;
    }

    /**
     * Tries to upgrade the parent read latch to write if the parent is unstable.
     * @return {@code true} if the parent is in unstable generation and latch was successfully upgraded from read to write, otherwise {@code false}.
     */
    private boolean tryUpgradeUnstableParentReadLatchToWrite() {
        if (depth == 0 || dataByDepth[depth - 1].isStable) {
            inc(Stat.FAIL_PARENT_NEEDS_SUCCESSOR);
            // If the parent needs to create successor it means that this will cause an update on grand parent.
            // Since this will only happen this first time this parent needs successor it's fine to bail out to
            // pessimistic.
            return false;
        }

        // Try to upgrade parent latch to write, otherwise bail out to pessimistic.
        boolean upgraded = tryUpgradeReadLatchToWrite(depth - 1);
        if (!upgraded) {
            inc(Stat.FAIL_PARENT_UPGRADE);
        }
        return upgraded;
    }

    private boolean tryUpgradeReadLatchToWrite(int depth) {
        if (depth < 0) {
            // We're doing something on the root node, which is a leaf a.t.m? Anyway flip to pessimistic.
            return false;
        }

        DepthData depthData = dataByDepth[depth];
        if (!depthData.latchTypeIsWrite) {
            latchAcquisitionId = depthData.latch.treeNodeId();
            latchAcquisitionIsWrite = true;
            if (!depthData.latch.tryUpgradeToWrite()) {
                // To avoid deadlock.
                return false;
            }
            depthData.latchTypeIsWrite = true;
            latchAcquisitionId = -1;
        }
        return true;
    }

    private void releaseLatchAtDepth(int depth) {
        DepthData depthData = dataByDepth[depth];
        if (depthData.latchTypeIsWrite) {
            depthData.latch.releaseWrite();
        } else {
            depthData.latch.releaseRead();
        }
    }

    @Override
    public String toString() {
        StringBuilder builder =
                new StringBuilder(format("LATCHES %s depth:%d%n", pessimistic ? "PESSIMISTIC" : "OPTIMISTIC", depth));
        for (int i = 0; i <= depth; i++) {
            LongSpinLatch latch = dataByDepth[i].latch;
            builder.append(dataByDepth[i].latchTypeIsWrite ? "W" : "R")
                    .append(latch.toString())
                    .append(format("%n"));
        }
        builder.append(latchAcquisitionIsWrite ? "W" : "R").append("Acquiring:").append(latchAcquisitionId);
        return builder.toString();
    }

    private static class DepthData {
        private LongSpinLatch latch;
        private boolean latchTypeIsWrite;
        private int availableSpace;
        private int keyCount;
        private int childPos;
        private boolean isStable;

        boolean positionedAtTheEdge() {
            return childPos == 0 || childPos == keyCount;
        }
    }

    // === STATS FOR LEARNING PURPOSES ===

    private static final boolean KEEP_STATS = false;

    private enum Stat {
        /**
         * Total number of operations where one operation is one merge or remove on the writer.
         */
        TOTAL_OPERATIONS,
        /**
         * Number of operations that had to flip to pessimistic mode.
         */
        PESSIMISTIC(TOTAL_OPERATIONS),
        /**
         * Number of operations that resulted in leaf split.
         */
        LEAF_SPLITS(TOTAL_OPERATIONS),
        /**
         * Number of "flip to pessimistic" caused by failure to upgrade read latch on the leaf to write.
         */
        FAIL_LEAF_UPGRADE(PESSIMISTIC),
        /**
         * Number of "flip to pessimistic" caused by parent not considered safe on leaf split.
         */
        FAIL_LEAF_SPLIT_PARENT_UNSAFE(PESSIMISTIC),
        /**
         * Number of "flip to pessimistic" caused by leaf underflow.
         */
        FAIL_LEAF_UNDERFLOW(PESSIMISTIC),
        /**
         * Number of "flip to pessimistic" caused by leaf needing successor and leaf's childPos in parent being either 0 or keyCount.
         */
        FAIL_SUCCESSOR_SIBLING(PESSIMISTIC),
        /**
         * Number of "flip to pessimistic" caused by parent (due to leaf split) needing successor.
         */
        FAIL_PARENT_NEEDS_SUCCESSOR(PESSIMISTIC),
        /**
         * Number of "flip to pessimistic" caused by failure to upgrade parent read latch to write.
         */
        FAIL_PARENT_UPGRADE(PESSIMISTIC);

        private final Stat comparedTo;
        private final LongAdder count = new LongAdder();

        Stat() {
            this(null);
        }

        Stat(Stat comparedTo) {
            this.comparedTo = comparedTo;
        }
    }

    /**
     * Increments statistics about the effect this monitor has on traversals.
     * @param stat the specific statistic to increment.
     */
    private static void inc(Stat stat) {
        if (KEEP_STATS) {
            stat.count.add(1);
        }
    }

    static {
        if (KEEP_STATS) {
            Runtime.getRuntime().addShutdownHook(new Thread(LatchCrabbingCoordination::dumpStats));
        }
    }

    static void dumpStats() {
        System.out.println("Stats for GBPTree parallel writes locking:");
        Stat[] stats = Stat.values();
        sort(stats, (s1, s2) -> Long.compare(s2.count.sum(), s1.count.sum()));
        for (Stat stat : stats) {
            long sum = stat.count.sum();
            System.out.printf("  %s: %d", stat.name(), sum);
            if (stat.comparedTo != null) {
                long comparedToSum = stat.comparedTo.count.sum();
                double percentage = (100D * sum) / comparedToSum;
                System.out.printf(" (%.4f%% of %s)", percentage, stat.comparedTo.name());
            }
            System.out.println();
        }
    }
}
