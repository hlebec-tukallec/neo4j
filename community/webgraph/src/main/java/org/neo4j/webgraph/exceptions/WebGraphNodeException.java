package org.neo4j.webgraph.exceptions;

public class WebGraphNodeException {
    public static UnsupportedOperationException nodeLabelAdditionsNotSupported() {
        return new UnsupportedOperationException("Node label additions are not supported");
    }

    public static UnsupportedOperationException nodeLabelRemovalNotSupported() {
        return new UnsupportedOperationException("Node label removal is not supported");
    }

    public static UnsupportedOperationException relationshipCreationNotSupported() {
        return new UnsupportedOperationException("Relationship creation is not supported");
    }
}
