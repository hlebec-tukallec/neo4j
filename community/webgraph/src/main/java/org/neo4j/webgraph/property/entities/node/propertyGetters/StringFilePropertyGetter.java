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

import it.unimi.dsi.fastutil.bytes.ByteBigList;
import it.unimi.dsi.fastutil.bytes.ByteMappedBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongMappedBigList;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Node property getter for file property of type {@code String}.
 * <p>
 * Expects the property file to store a {@code ByteBigList} buffer and {@code LongBigList} with offsets.
 * Offsets correspond to node ids. At the given offset the buffer stores 4 bytes for the length of the message
 * in bytes then the string bytes.
 */
public class StringFilePropertyGetter implements NodePropertyGetter<String> {
    private final ByteBigList buffer;
    private final LongBigList offsets;

    /**
     * Constructs a property getter from buffer and offset file paths
     *
     * @param buffer the path to the buffer file with string lengths and bytes.
     * @param offset the path to the file with buffer offsets for each vertex.
     * @throws IOException if an I/O error occurs
     */
    public StringFilePropertyGetter(String buffer, String offset) throws IOException {
        Path bufferPath = Path.of(buffer);
        Path offsetPath = Path.of(offset);
        try (RandomAccessFile bufferFile = new RandomAccessFile(bufferPath.toFile(), "r");
             RandomAccessFile offsetFile = new RandomAccessFile(offsetPath.toFile(), "r")) {
            this.buffer = ByteMappedBigList.map(bufferFile.getChannel());
            this.offsets = LongMappedBigList.map(offsetFile.getChannel());
        }
    }

    @Override
    public String get(long nodeId) {
        long offset = offsets.getLong(nodeId);
        byte[] lengthBytes = new byte[Integer.BYTES];
        buffer.getElements(offset, lengthBytes, 0, Integer.BYTES);
        int length = bytesToInt(lengthBytes);

        byte[] stringBytes = new byte[length];
        buffer.getElements(offset + Integer.BYTES, stringBytes, 0, length);
        return new String(stringBytes);
    }

    private int bytesToInt(byte[] b) {
        int result = 0;
        for (int i = 0; i < Integer.BYTES; i++) {
            result <<= Byte.SIZE;
            result |= (b[i] & 0xFF);
        }
        return result;
    }
}
