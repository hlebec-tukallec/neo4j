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
package org.neo4j.webgraph.property;

import org.neo4j.webgraph.property.entities.node.NodeProperty;
import org.neo4j.webgraph.property.entities.relationship.RelationshipProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Enables the user to provide access to node and relationship properties.
 * All registered properties are associated with every node/relationship. Nodes/relationships not
 * associated with a property are assumed to store a null value.
 */
public class DefaultPropertyProvider implements WebGraphPropertyProvider {

    private final Map<Integer, String> nodePropertiesIds = new HashMap<>();
    private final Map<Integer, String> relationshipPropertiesIds = new HashMap<>();

    private final Map<String, NodeProperty<?>> nodeProperties = new HashMap<>();
    private final Map<String, RelationshipProperty<?>> relationshipProperties = new HashMap<>();

    private Function<Long, String> nodeLabel = id -> "node";
    private Map<Integer, String> nodeLabelsIds = new HashMap<>() {{
        put(0, "node");
    }};

    /**
     * Register a node label in the provider.
     *
     * @param label       a function, which accepts a node id and returns the string label of that node.
     * @param labelsNames list of all node labels names
     */
    public void addNodeLabel(Function<Long, String> label, String[] labelsNames) {
        this.nodeLabel = label;

        Map<Integer, String> nodeLabels = new HashMap<>();
        for (int i = 0; i < labelsNames.length; i++) {
            if (nodeLabels.put(i, labelsNames[i]) != null) {
                throw new IllegalArgumentException("Node label name already exists: " + labelsNames[i]);
            }
        }
        nodeLabelsIds = nodeLabels;
    }

    /**
     * Register a node property in the provider.
     *
     * @param nodeProperty the property to register in the provider.
     * @see NodeProperty
     */
    public void addNodeProperty(NodeProperty<?> nodeProperty) {
        if (nodeProperties.put(nodeProperty.getKey(), nodeProperty) != null) {
            throw new IllegalArgumentException("Node property key already exists: " + nodeProperty.getKey());
        }
        nodePropertiesIds.put(nodePropertiesIds.size(), nodeProperty.getKey());
    }

    /**
     * Register a relationship property in the provider.
     *
     * @param relationshipProperty the property to register in the provider.
     * @see RelationshipProperty
     */
    public void addRelationshipProperty(RelationshipProperty<?> relationshipProperty) {
        if (relationshipProperties.put(relationshipProperty.getKey(), relationshipProperty) != null) {
            throw new IllegalArgumentException("Relationship property key already exists: " + relationshipProperty.getKey());
        }
        relationshipPropertiesIds.put(relationshipPropertiesIds.size(), relationshipProperty.getKey());
    }

    @Override
    public String getNodePropertyNameById(int id) {
        if (nodePropertiesIds.containsKey(id)) {
            return nodePropertiesIds.get(id);
        }
        return null;
    }

    @Override
    public int getNodePropertyIdByName(String key) {
        for (int i = 0; i < nodePropertiesIds.size(); i++) {
            if (Objects.equals(nodePropertiesIds.get(i), key)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String getRelationshipPropertyNameById(int id) {
        if (relationshipPropertiesIds.containsKey(id)) {
            return relationshipPropertiesIds.get(id);
        }
        return null;
    }

    @Override
    public int getRelationshipPropertyIdByName(String key) {
        for (int i = 0; i < relationshipPropertiesIds.size(); i++) {
            if (Objects.equals(relationshipPropertiesIds.get(i), key)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String getNodeLabelNameById(int id) {
        if (nodeLabelsIds.containsKey(id)) {
            return nodeLabelsIds.get(id);
        }
        return null;
    }

    @Override
    public int getNodeLabelIdByName(String key) {
        for (int i = 0; i < nodeLabelsIds.size(); i++) {
            if (Objects.equals(nodeLabelsIds.get(i), key)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String[] getNodePropertyKeys(long nodeId) {
        return nodeProperties.keySet().toArray(String[]::new);
    }

    @Override
    public Object getNodeProperty(String key, long nodeId) {
        NodeProperty<?> nodeProperty = nodeProperties.get(key);
        if (nodeProperty == null) {
            return null;
        }
        return nodeProperty.get(nodeId);
    }

    @Override
    public Map<String, Object> getAllNodeProperties(long nodeId) {
        String[] allProperties = getNodePropertyKeys(nodeId);
        Map<String, Object> allNodeProps = new HashMap<>();
        for (String prop : allProperties) {
            NodeProperty<?> nodeProperty = nodeProperties.get(prop);
            if (nodeProperty != null) {
                if (nodeProperty.get(nodeId) == null) {
                    continue;
                }
                allNodeProps.put(prop, nodeProperty.get(nodeId));
            }
        }
        return allNodeProps;
    }

    @Override
    public boolean hasNodeProperty(String key, long nodeId) {
        NodeProperty<?> nodeProperty = nodeProperties.get(key);
        return nodeProperty != null;
    }

    @Override
    public String getNodeLabel(long nodeId) {
        return nodeLabel.apply(nodeId);
    }

    @Override
    public boolean hasNodeLabel(long nodeId, String label) {
        return nodeLabel.apply(nodeId).equals(label);
    }

    @Override
    public String[] getRelationshipPropertyKeys(long startNode, long endNode) {
        return relationshipProperties.keySet().toArray(String[]::new);
    }

    @Override
    public Object getRelationshipProperty(String key, long startNode, long endNode) {
        RelationshipProperty<?> relationshipProperty = relationshipProperties.get(key);
        if (relationshipProperty == null) {
            return null;
        }
        return relationshipProperty.get(startNode, endNode);
    }

    @Override
    public Map<String, Object> getAllRelationshipProperties(long startNode, long endNode) {
        String[] allProperties = getRelationshipPropertyKeys(startNode, endNode);
        Map<String, Object> allRelationshipProps = new HashMap<>();
        for (String prop : allProperties) {
            RelationshipProperty<?> relationshipProperty = relationshipProperties.get(prop);
            if (relationshipProperty != null) {
                allRelationshipProps.put(prop, relationshipProperty.get(startNode, endNode));
            }
        }
        return allRelationshipProps;
    }

    @Override
    public boolean hasRelationshipProperty(String key, long startNode, long endNode) {
        RelationshipProperty<?> relationshipProperty = relationshipProperties.get(key);
        return relationshipProperty != null;
    }
}
