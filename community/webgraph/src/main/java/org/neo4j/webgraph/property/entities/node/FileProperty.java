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
package org.neo4j.webgraph.property.entities.node;

import org.neo4j.webgraph.property.entities.node.propertyGetters.LongFilePropertyGetter;
import org.neo4j.webgraph.property.entities.node.propertyGetters.NodePropertyGetter;

import java.io.IOException;

public class FileProperty<T> extends NodeProperty<T> {

    public FileProperty(String key, String path, Class<T> classType) throws IOException {
        super(key, getFilePropertyGetterForType(classType, path));
    }

    private static <T> NodePropertyGetter<T> getFilePropertyGetterForType(Class<T> classType, String path) throws IOException {
        if (classType == Long.class) {
            return (NodePropertyGetter<T>) new LongFilePropertyGetter(path);
        } else {
            throw new RuntimeException("Unsupported property type: " + classType.getSimpleName());
        }
    }
}
