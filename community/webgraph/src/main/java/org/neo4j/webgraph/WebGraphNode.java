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
package org.neo4j.webgraph;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.webgraph.exceptions.WebGraphEntityException;
import org.neo4j.webgraph.exceptions.WebGraphNodeException;
import org.neo4j.webgraph.exceptions.WebGraphRelationshipException;
import org.neo4j.webgraph.iterators.WebGraphIterator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebGraphNode implements Node {

    private final WebGraphDatabase graph;

    private final long reference;


    public WebGraphNode(long reference, WebGraphDatabase graph) {
        this.graph = graph;
        this.reference = reference;
    }

    @Override
    public long getId() {
        return reference;
    }

    @Override
    public String getElementId() {
        return String.valueOf(reference);
    }

    @Override
    public boolean hasProperty(String key) {
        return graph.getPropertyProvider().hasNodeProperty(key, reference);
    }

    @Override
    public Object getProperty(String key) {
        return graph.getPropertyProvider().getNodeProperty(key, reference);
    }

    @Override
    public Object getProperty(String key, Object defaultValue) {
        var value = graph.getPropertyProvider().getNodeProperty(key, reference);
        return value == null ? defaultValue : value;
    }

    @Override
    public Iterable<String> getPropertyKeys() {
        return List.of(graph.getPropertyProvider().getNodePropertyKeys(reference));
    }

    @Override
    public Map<String, Object> getProperties(String... keys) {
        Map<String, Object> properties = new HashMap<>();
        for (String key : keys) {
            properties.put(key, graph.getPropertyProvider().getNodeProperty(key, reference));
        }
        return properties;
    }

    @Override
    public Map<String, Object> getAllProperties() {
        return graph.getPropertyProvider().getAllNodeProperties(reference);
    }


    @Override
    public ResourceIterable<Relationship> getRelationships() {
        return new WebGraphIterator<>(graph.getRelationships(getId(), Direction.BOTH));
    }

    @Override
    public boolean hasRelationship() {
        return graph.hasRelationship(getId(), Direction.BOTH);
    }

    @Override
    public ResourceIterable<Relationship> getRelationships(Direction dir) {
        return new WebGraphIterator<>(graph.getRelationships(getId(), dir));
    }

    @Override
    public boolean hasRelationship(Direction dir) {
        return graph.hasRelationship(getId(), dir);
    }

    @Override
    public boolean hasLabel(Label label) {
        return graph.getPropertyProvider().hasNodeLabel(reference, label.name());
    }

    @Override
    public Iterable<Label> getLabels() {
        Label label = Label.label(graph.getPropertyProvider().getNodeLabel(reference));
        return List.of(label);
    }

    @Override
    public int getDegree() {
        return (int) graph.nodeDegree(reference, Direction.BOTH);
    }

    @Override
    public int getDegree(Direction direction) {
        return (int) graph.nodeDegree(reference, direction);
    }

    @Override
    public String toString() {
        return "WebGraphNode " + getId();
    }

    @Override
    public ResourceIterable<Relationship> getRelationships(RelationshipType... types) {
        throw WebGraphRelationshipException.relationshipTypesNotSupported();
    }

    @Override
    public ResourceIterable<Relationship> getRelationships(Direction direction, RelationshipType... types) {
        throw WebGraphRelationshipException.relationshipTypesNotSupported();
    }

    @Override
    public boolean hasRelationship(RelationshipType... types) {
        throw WebGraphRelationshipException.relationshipTypesNotSupported();
    }

    @Override
    public boolean hasRelationship(Direction direction, RelationshipType... types) {
        throw WebGraphRelationshipException.relationshipTypesNotSupported();
    }

    @Override
    public Relationship getSingleRelationship(RelationshipType type, Direction dir) {
        throw WebGraphRelationshipException.relationshipTypesNotSupported();
    }

    @Override
    public Iterable<RelationshipType> getRelationshipTypes() {
        throw WebGraphRelationshipException.relationshipTypesNotSupported();
    }


    @Override
    public int getDegree(RelationshipType type) {
        throw WebGraphRelationshipException.relationshipTypesNotSupported();
    }


    @Override
    public int getDegree(RelationshipType type, Direction direction) {
        throw WebGraphRelationshipException.relationshipTypesNotSupported();
    }


    @Override
    public void addLabel(Label label) {
        throw WebGraphNodeException.nodeLabelAdditionsNotSupported();
    }

    @Override
    public void removeLabel(Label label) {
        throw WebGraphNodeException.nodeLabelRemovalNotSupported();
    }

    @Override
    public Relationship createRelationshipTo(Node otherNode, RelationshipType type) {
        throw WebGraphNodeException.relationshipCreationNotSupported();
    }

    @Override
    public void delete() {
        throw WebGraphEntityException.entityRemovalNotSupported();
    }

    @Override
    public void setProperty(String key, Object value) {
        throw WebGraphEntityException.unsupportedPropertyOperation();
    }

    @Override
    public Object removeProperty(String key) {
        throw WebGraphEntityException.unsupportedPropertyOperation();
    }
}
