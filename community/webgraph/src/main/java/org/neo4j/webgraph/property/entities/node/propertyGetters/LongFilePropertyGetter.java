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
package org.neo4j.webgraph.property.entities.node.propertyGetters;

import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongMappedBigList;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Node property getter for file property of type {@code Long}.
 * <p>
 * Expects the property file to store a {@code LongBigList} with indices corresponding to node ids.
 * A value of {@link Long#MIN_VALUE}  corresponds to empty value.
 */
public class LongFilePropertyGetter implements NodePropertyGetter<Long> {
    private final LongBigList list;

    /**
     * Constructs a property getter from file path.
     *
     * @param path the path to the property file containing a {@code LongBigList}.
     * @throws IOException if an I/O error occurs
     */
    public LongFilePropertyGetter(String path) throws IOException {
        Path filePath = Path.of(path);
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            this.list = LongMappedBigList.map(raf.getChannel());
        }
    }

    @Override
    public Long get(long vertexId) {
        long res = list.getLong(vertexId);
        if (res == Long.MIN_VALUE) {
            return null;
        }
        return res;
    }
}
