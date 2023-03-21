package org.neo4j.property;

import org.neo4j.webgraph.property.WebGraphPropertyProvider;
import org.softwareheritage.graph.SwhBidirectionalGraph;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SwhPropertyProvider implements WebGraphPropertyProvider {
    private final SwhBidirectionalGraph graph;

    public SwhPropertyProvider(SwhBidirectionalGraph graph) {
        this.graph = graph;
    }

    @Override
    public String getNodePropertyNameById(int id) {
        return PropertyUtils.NodeProperty.toString(PropertyUtils.NodeProperty.fromInt(id));
    }

    @Override
    public int getNodePropertyIdByName(String key) {
        return PropertyUtils.NodeProperty.toInt(PropertyUtils.NodeProperty.toNodeProperty(key));
    }

    @Override
    public String getRelationshipPropertyNameById(int id) {
        return PropertyUtils.RelationshipProperty.toString(PropertyUtils.RelationshipProperty.fromInt(id));
    }

    @Override
    public int getRelationshipPropertyIdByName(String key) {
        return PropertyUtils.RelationshipProperty.toInt(PropertyUtils.RelationshipProperty.toRelationshipProperty(key));
    }

    @Override
    public String getNodeLabelNameById(int id) {
        return PropertyUtils.NodeLabel.fromInt(id).toString();
    }

    @Override
    public int getNodeLabelIdByName(String key) {
        return PropertyUtils.NodeLabel.toInt(PropertyUtils.NodeLabel.toNodeLabel(key));
    }

    @Override
    public String[] getNodePropertyKeys(long nodeId) {
        PropertyUtils.NodeProperty[] properties = PropertyUtils.NodeProperty.values();
        return (String[]) Arrays.stream(properties).map(prop -> PropertyUtils.NodeProperty.toString(prop)).toArray();
    }

    @Override
    public Object getNodeProperty(String key, long nodeId) {
        return switch (PropertyUtils.NodeProperty.toNodeProperty(key)) {
            case AUTHOR_TIMESTAMP -> graph.getAuthorTimestamp(nodeId);
            case SWHID -> graph.getSWHID(nodeId);
            case LABEL -> graph.getNodeType(nodeId);
            default -> throw new RuntimeException("Unknown node property key: " + key);
        };
    }

    @Override
    public Map<String, Object> getAllNodeProperties(long nodeId) {
        Map<String, Object> properties = new HashMap<>();
        if (graph.getAuthorTimestamp(nodeId) != null) {
            properties.put("author_timestamp", graph.getAuthorTimestamp(nodeId));
        }
        properties.put("SWHID", graph.getSWHID(nodeId));
        properties.put("label", graph.getNodeType(nodeId));
        return properties;
    }

    @Override
    public boolean hasNodeProperty(String key, long nodeId) {
        Map<String, Object> properties = getAllNodeProperties(nodeId);
        return properties.containsKey(key);
    }

    @Override
    public String getNodeLabel(long nodeId) {
        return graph.getNodeType(nodeId).toString();
    }

    @Override
    public boolean hasNodeLabel(long nodeId, String label) {
        return graph.getNodeType(nodeId).toString().equals(label);
    }

    @Override
    public String[] getRelationshipPropertyKeys(long startNode, long endNode) {
        PropertyUtils.RelationshipProperty[] properties = PropertyUtils.RelationshipProperty.values();
        return (String[]) Arrays.stream(properties).map(prop -> PropertyUtils.RelationshipProperty.toString(prop)).toArray();
    }

    @Override
    public Object getRelationshipProperty(String key, long startNode, long endNode) {
        if (!key.equals("dir_entry")) {
            throw new RuntimeException("Unknown relationship property key: " + key);
        }
        var labelledArcIterator = graph.labelledSuccessors(startNode);
        long successor;
        while ((successor = labelledArcIterator.nextLong()) != -1) {
            if (successor == endNode) {
                return labelledArcIterator.label().get();
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> getAllRelationshipProperties(long startNode, long endNode) {
        Map<String, Object> properties = new HashMap<>();
        var dir_entry = getRelationshipProperty("dir_entry", startNode, endNode);
        if (dir_entry != null) {
            properties.put("dir_entry", dir_entry);
        }
        return properties;
    }

    @Override
    public boolean hasRelationshipProperty(String key, long startNode, long endNode) {
        Map<String, Object> properties = getAllRelationshipProperties(startNode, endNode);
        return properties.containsKey(key);
    }
}
