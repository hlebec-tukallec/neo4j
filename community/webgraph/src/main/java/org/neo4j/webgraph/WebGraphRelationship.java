package org.neo4j.webgraph;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

public class WebGraphRelationship extends WebGraphEntity implements Relationship {
    private final WebGraphNode startNode;
    private final WebGraphNode endNode;

    private final WebGraphDatabase graph;

    public WebGraphRelationship(long startNode, long endNode, long reference, WebGraphDatabase graph) {
        super(reference);
        this.graph = graph;
        this.startNode = new WebGraphNode(startNode, graph);
        this.endNode = new WebGraphNode(endNode, graph);
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
            throw new NotFoundException("Node[" + node.getElementId() + "]" +
                    " not connected to this relationship[" + getId() + "]");
        }
    }

    @Override
    public Node[] getNodes() {
        return new Node[]{startNode, endNode};
    }

    @Override
    public RelationshipType getType() {
        return null;
    }

    @Override
    public boolean isType(RelationshipType type) {
        return false;
    }

    @Override
    public String toString() {
        return "WebGraphRelationship " + getId() + ", startNode= " + startNode + ", endNode= " + endNode;
    }
}
