package org.neo4j.webgraph;

import java.util.Iterator;

import static org.neo4j.webgraph.Utils.NO_ID;

public class WebGraphNodeIterator {
    private final WebGraphDatabase graph;
    private Iterator<WebGraphNode> iter;
    private long currentId = NO_ID;
    private boolean isFinished = true;

    private static WebGraphNodeIterator singleInstance= null;

    public WebGraphNodeIterator() {
        this.graph = WebGraphDatabase.getSingleInstance();
        iter = this.graph.getNodeIterator();
    }

    public static WebGraphNodeIterator getSingleInstance() {
        if (singleInstance == null) {
            singleInstance = new WebGraphNodeIterator();
        }
        return singleInstance;
    }

    public long getCurrentId() {
        return this.currentId;
    }

    public long getNodesCount() {
        return this.graph.getAllNodes();
    }

    public void next() {
        if (iter.hasNext()) {
            isFinished = false;
            currentId = iter.next().getId();
        } else {
            currentId = NO_ID;
        }
    }

    public void finishIteration() {
        isFinished = true;
    }

    public boolean isFinished() {
        return isFinished;
    }

    public void reset() {
        iter = this.graph.resetNodeIterator();
        isFinished = true;
    }
}
