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
package org.neo4j.bolt.transport;

import static org.neo4j.memory.MemoryGroup.BOLT;

import io.netty.buffer.ByteBufAllocatorMetric;
import org.neo4j.memory.GlobalMemoryGroupTracker;
import org.neo4j.memory.MemoryPools;

public class BoltMemoryPool extends GlobalMemoryGroupTracker {
    private final ByteBufAllocatorMetric allocatorMetric;

    public BoltMemoryPool(MemoryPools memoryPools, ByteBufAllocatorMetric allocatorMetric) {
        super(memoryPools, BOLT, 0, false, true, null);
        this.allocatorMetric = allocatorMetric;
        memoryPools.registerPool(this);
    }

    @Override
    public long usedHeap() {
        return allocatorMetric.usedHeapMemory() + super.usedHeap();
    }

    @Override
    public long usedNative() {
        return allocatorMetric.usedDirectMemory() + super.usedNative();
    }

    @Override
    public long totalUsed() {
        return usedHeap() + usedNative();
    }
}
