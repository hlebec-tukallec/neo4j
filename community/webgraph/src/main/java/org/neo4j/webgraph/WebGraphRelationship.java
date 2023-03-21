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

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.webgraph.exceptions.WebGraphEntityException;
import org.neo4j.webgraph.exceptions.WebGraphRelationshipException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebGraphRelationship implements Relationship {
    private final WebGraphNode startNode;
    private final WebGraphNode endNode;

    private final WebGraphDatabase graph;

    private final long reference;

    public WebGraphRelationship(long startNode, long endNode, long reference, WebGraphDatabase graph) {
        this.reference = reference;
        this.graph = graph;
        this.startNode = new WebGraphNode(startNode, graph);
        this.endNode = new WebGraphNode(endNode, graph);
    }

    @Override
    public long getId() {
        return reference;
    }

    @Override
    public String getElementId() {
        return String.valueOf(reference);
    }

    public Node getStartNode() {
        return startNode;
    }

    @Override
    public Node getEndNode() {
        return endNode;
    }

    @Override
    public Node getOtherNode(Node node) {
        if (node.equals(startNode)) {
            return endNode;
        } else if (node.equals(endNode)) {
            return startNode;
        } else {
            throw new NotFoundException(
                    "Node[" + node.getElementId() + "]" + " not connected to this relationship[" + getId() + "]");
        }
    }

    @Override
    public Node[] getNodes() {
        return new Node[]{startNode, endNode};
    }

    @Override
    public boolean hasProperty(String key) {
        return graph.getPropertyProvider().hasRelationshipProperty(key, startNode.getId(), endNode.getId());
    }

    @Override
    public Object getProperty(String key) {
        return graph.getPropertyProvider().getRelationshipProperty(key, startNode.getId(), endNode.getId());
    }

    @Override
    public Object getProperty(String key, Object defaultValue) {
        var value = graph.getPropertyProvider().getRelationshipProperty(key, startNode.getId(), endNode.getId());
        return value == null ? defaultValue : value;
    }

    @Override
    public Iterable<String> getPropertyKeys() {
        return List.of(graph.getPropertyProvider().getRelationshipPropertyKeys(startNode.getId(), endNode.getId()));
    }

    @Override
    public Map<String, Object> getProperties(String... keys) {
        Map<String, Object> properties = new HashMap<>();
        for (String key : keys) {
            properties.put(key, graph.getPropertyProvider().getRelationshipProperty(key, startNode.getId(), endNode.getId()));
        }
        return properties;
    }

    @Override
    public Map<String, Object> getAllProperties() {
        return graph.getPropertyProvider().getAllRelationshipProperties(startNode.getId(), endNode.getId());
    }

    @Override
    public String toString() {
        return "WebGraphRelationship " + getId() + ", startNode= " + startNode + ", endNode= " + endNode;
    }

    @Override
    public void setProperty(String key, Object value) {
        throw WebGraphEntityException.unsupportedPropertyOperation();
    }

    @Override
    public Object removeProperty(String key) {
        throw WebGraphEntityException.unsupportedPropertyOperation();
    }

    @Override
    public void delete() {
        throw WebGraphEntityException.entityRemovalNotSupported();
    }

    @Override
    public RelationshipType getType() {
        throw WebGraphRelationshipException.relationshipTypesNotSupported();
    }

    @Override
    public boolean isType(RelationshipType type) {
        throw WebGraphRelationshipException.relationshipTypesNotSupported();
    }
}
