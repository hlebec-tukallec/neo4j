package org.neo4j.webgraph;

import org.neo4j.graphdb.*;

public class WebGraphNode extends WebGraphEntity implements Node  {

    private final WebGraphDatabase graph;

    public WebGraphNode(long reference, WebGraphDatabase graph) {
        super(reference);
        this.graph = graph;
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
    public ResourceIterable<Relationship> getRelationships(RelationshipType... types) {
        return null;
    }

    @Override
    public ResourceIterable<Relationship> getRelationships(Direction direction, RelationshipType... types) {
        return null;
    }

    @Override
    public boolean hasRelationship(RelationshipType... types) {
        return false;
    }

    @Override
    public boolean hasRelationship(Direction direction, RelationshipType... types) {
        return false;
    }

    @Override
    public Relationship getSingleRelationship(RelationshipType type, Direction dir) {
        return null;
    }

    @Override
    public Iterable<RelationshipType> getRelationshipTypes() {
        return null;
    }

    @Override
    public int getDegree() {
        return 0;
    }

    @Override
    public int getDegree(RelationshipType type) {
        return 0;
    }

    @Override
    public int getDegree(Direction direction) {
        return 0;
    }

    @Override
    public int getDegree(RelationshipType type, Direction direction) {
        return 0;
    }

    @Override
    public boolean hasLabel(Label label) {
        return false;
    }

    @Override
    public Iterable<Label> getLabels() {
        return null;
    }

    @Override
    public String toString() {
        return "WebGraphNode " + getId();
    }

    @Override
    public void addLabel(Label label) {
        //exception
    }

    @Override
    public void removeLabel(Label label) {
        //exception
    }

    @Override
    public Relationship createRelationshipTo(Node otherNode, RelationshipType type) {
        //exception
        return null;
    }
}
