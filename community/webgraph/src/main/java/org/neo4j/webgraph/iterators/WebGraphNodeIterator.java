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
package org.neo4j.webgraph.iterators;

import org.neo4j.webgraph.WebGraphDatabase;
import org.neo4j.webgraph.WebGraphNode;

import java.util.Iterator;

public class WebGraphNodeIterator {
    private final WebGraphDatabase graph;
    private Iterator<WebGraphNode> iter;
    private long currentId = WebGraphDatabase.NO_ID;

    private WebGraphNode currentNode = null;

    private WebGraphNode previousNode = null;

    private WebGraphNode startNode = null;
    private boolean isFinished = true;

    private long relationshipNodeId = WebGraphDatabase.NO_ID;

    private static WebGraphNodeIterator singleInstance = null;

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
        return currentId;
    }

    public WebGraphNode getCurrentNode() {
        return currentNode;
    }

    public WebGraphNode getPreviousNode() {
        return previousNode;
    }

    public long getNodesCount() {
        return graph.getAllNodes();
    }

    public void next() {
        if (iter.hasNext()) {
            isFinished = false;
            previousNode = currentNode;
            currentNode = iter.next();
            currentId = currentNode.getId();
            if (currentId == 0) {
                startNode = currentNode;
            }
        } else {
            currentNode = null;
            currentId = WebGraphDatabase.NO_ID;
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

    public void setRelationshipNodeId(long id) {
        relationshipNodeId = id;
    }

    public long getRelationshipNodeId() {
        return relationshipNodeId;
    }

    public WebGraphNode getStartNode() {
        return startNode;
    }
}
