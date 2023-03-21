package org.neo4j.webgraph.exceptions;

public class WebGraphRelationshipException {
    public static UnsupportedOperationException relationshipTypesNotSupported() {
        return new UnsupportedOperationException("Relationship types are not supported");
    }
}
