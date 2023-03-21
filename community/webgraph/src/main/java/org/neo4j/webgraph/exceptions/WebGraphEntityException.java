package org.neo4j.webgraph.exceptions;

public class WebGraphEntityException {
    public static UnsupportedOperationException unsupportedPropertyOperation() {
        return new UnsupportedOperationException("Unsupported property operation");
    }

    public static UnsupportedOperationException entityRemovalNotSupported() {
        return new UnsupportedOperationException("Entity removal is not supported");
    }
}
