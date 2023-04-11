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
package org.neo4j.webgraph.property.entities.relationship;

import org.neo4j.webgraph.property.entities.relationship.propertyGetters.ArcLabelRelationshipSubPropertyGetter;
import org.neo4j.webgraph.property.entities.relationship.propertyGetters.RelationshipPropertyGetter;

/**
 * Relationship property getter based on {@link ArcLabelRelationshipProperty}.
 * <p>
 * Allows deconstructing the single WebGraph relationship label into separate properties.
 */
public class ArcLabelRelationshipSubProperty<T> extends RelationshipProperty<T> {

    public <E> ArcLabelRelationshipSubProperty(String key, ArcLabelRelationshipProperty<E> arcLabelProperty, ArcLabelRelationshipSubPropertyGetter<E, T> getter) {
        super(key, getArcLabelPropertyGetter(arcLabelProperty, getter));
    }

    public static <E, T> RelationshipPropertyGetter<T> getArcLabelPropertyGetter(ArcLabelRelationshipProperty<E> arcLabelProperty, ArcLabelRelationshipSubPropertyGetter<E, T> getter) {
        return (fromId, toId) -> {
            E value = arcLabelProperty.get(fromId, toId);
            return getter.get(value);
        };
    }
}
