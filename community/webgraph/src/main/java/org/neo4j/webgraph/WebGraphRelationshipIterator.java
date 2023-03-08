package org.neo4j.webgraph;

import java.util.Iterator;

import static org.neo4j.webgraph.Utils.NO_ID;


public class WebGraphRelationshipIterator {
    private Iterator<WebGraphRelationship> iter;
    private final WebGraphDatabase graph;
    private long currentId = NO_ID;
    private WebGraphRelationship currentRelationship = null;
    private static WebGraphRelationshipIterator singleInstance= null;

    public WebGraphRelationshipIterator() {
        this.graph = WebGraphDatabase.getSingleInstance();
        iter = this.graph.getRelationshipIterator();
    }

    public static WebGraphRelationshipIterator getSingleInstance() {
        if (singleInstance == null) {
            singleInstance = new WebGraphRelationshipIterator();
        }
        return singleInstance;
    }

    public long getCurrentId() {
        return currentId;
    }

    public WebGraphRelationship getCurrentRelationship() {
        return currentRelationship;
    }

    public void next() {
        if (iter.hasNext()) {
            currentRelationship = iter.next();
            System.out.println("current rel: " + currentRelationship.toString());
            currentId = currentRelationship.getId();
        } else {
            currentRelationship = null;
            currentId = NO_ID;
        }
    }

    public void reset() {
        iter = this.graph.resetRelationshipIterator();
    }

    public long getRelationshipsCount() {
        return this.graph.getAllRelationships();
    }
}
