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
package org.neo4j.webgraph;

import it.unimi.dsi.big.webgraph.BidirectionalImmutableGraph;
import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.big.webgraph.LazyLongIterators;
import it.unimi.dsi.big.webgraph.NodeIterator;

import java.util.Iterator;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Relationship;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.webgraph.property.WebGraphPropertyProvider;

public class WebGraphDatabase {
    public static final long NO_ID = -1;
    private final BidirectionalImmutableGraph graph;

    private final WebGraphPropertyProvider propertyProvider;
    private final String graphName;

    private static WebGraphDatabase singleInstance = null;

    private WebGraphDatabase(BidirectionalImmutableGraph graph, WebGraphPropertyProvider propertyProvider) {
        this.graph = graph;
        this.propertyProvider = propertyProvider;
        this.graphName = graph.basename().toString();
    }

    public static void initialise(BidirectionalImmutableGraph graph, WebGraphPropertyProvider propertyProvider) {
        singleInstance = new WebGraphDatabase(graph, propertyProvider);
    }

    public static WebGraphDatabase getSingleInstance() {
        if (singleInstance == null) {
            throw new RuntimeException("WebGraphDatabase is not initialised.");
        }
        return singleInstance;
    }

    public String databaseName() {
        return graphName;
    }

    public long getAllNodes() {
        return graph.numNodes();
    }

    public long getAllRelationships() {
        return graph.numArcs();
    }

    public long nodeDegree(long nodeId, Direction direction) {
        return switch (direction) {
            case OUTGOING -> graph.outdegree(nodeId);
            case INCOMING -> graph.indegree(nodeId);
            case BOTH -> graph.outdegree(nodeId) + graph.indegree(nodeId);
        };
    }

    public Iterator<WebGraphRelationship> getRelationshipIterator() {
        return new Iterator<>() {
            long relationshipId = NO_ID;
            NodeIterator startNodes;
            LazyLongIterator endNodes;
            long nextStartNode;
            long nextEndNode;
            WebGraphRelationship nextRelationship = getNextRelationship();

            @Override
            public boolean hasNext() {
                return nextRelationship != null;
            }

            @Override
            public WebGraphRelationship next() {
                WebGraphRelationship next = nextRelationship;
                nextRelationship = getNextRelationship();
                return next;
            }

            private WebGraphRelationship getNextRelationship() {
                if (startNodes == null) {
                    relationshipId = 0;
                    startNodes = graph.nodeIterator();
                    nextStartNode = startNodes.hasNext() ? startNodes.nextLong() : NO_ID;
                    endNodes =
                            nextStartNode == NO_ID ? LazyLongIterators.EMPTY_ITERATOR : graph.successors(nextStartNode);
                }
                assert endNodes != null;
                nextEndNode = endNodes.nextLong();
                while (nextEndNode == NO_ID) {
                    nextStartNode = startNodes.hasNext() ? startNodes.nextLong() : NO_ID;
                    if (nextStartNode == NO_ID) {
                        return null;
                    }
                    endNodes = graph.successors(nextStartNode);
                    nextEndNode = endNodes.nextLong();
                }
                return new WebGraphRelationship(nextStartNode, nextEndNode, relationshipId++, WebGraphDatabase.this);
            }
        };
    }

    public Iterator<Relationship> getRelationships(long nodeId, Direction direction) {
        return switch (direction) {
            case OUTGOING -> getOutgoingRelationshipsIterator(nodeId);
            case INCOMING -> getIncomingRelationshipsIterator(nodeId);
            case BOTH -> Iterators.concat(
                    getOutgoingRelationshipsIterator(nodeId), getIncomingRelationshipsIterator(nodeId));
        };
    }

    private Iterator<Relationship> getOutgoingRelationshipsIterator(long nodeId) {
        return new Iterator<>() {
            long relationshipId = 0;
            LazyLongIterator endNodes;
            long nextEndNode;
            WebGraphRelationship nextRelationship = getNextOutgoingRelationship();

            private WebGraphRelationship getNextOutgoingRelationship() {
                endNodes = nodeId == NO_ID ? LazyLongIterators.EMPTY_ITERATOR : graph.successors(nodeId);
                nextEndNode = endNodes.nextLong();
                return new WebGraphRelationship(nodeId, nextEndNode, relationshipId++, WebGraphDatabase.this);
            }

            @Override
            public boolean hasNext() {
                return nextRelationship != null;
            }

            @Override
            public WebGraphRelationship next() {
                WebGraphRelationship next = nextRelationship;
                nextRelationship = getNextOutgoingRelationship();
                return next;
            }
        };
    }

    private Iterator<Relationship> getIncomingRelationshipsIterator(long nodeId) {
        return new Iterator<>() {
            long relationshipId = 0;
            LazyLongIterator startNodes;
            long nextStartNode;
            WebGraphRelationship nextRelationship = getNextIncomingRelationship();

            private WebGraphRelationship getNextIncomingRelationship() {
                startNodes = nodeId == NO_ID ? LazyLongIterators.EMPTY_ITERATOR : graph.predecessors(nodeId);
                nextStartNode = startNodes.nextLong();
                return new WebGraphRelationship(nextStartNode, nodeId, relationshipId++, WebGraphDatabase.this);
            }

            @Override
            public boolean hasNext() {
                return nextRelationship != null;
            }

            @Override
            public WebGraphRelationship next() {
                WebGraphRelationship next = nextRelationship;
                nextRelationship = getNextIncomingRelationship();
                return next;
            }
        };
    }

    public boolean hasRelationship(long nodeId, Direction direction) {
        return switch (direction) {
            case OUTGOING -> hasOutgoingRelationshipsIterator(nodeId);
            case INCOMING -> hasIncomingRelationshipsIterator(nodeId);
            case BOTH -> hasOutgoingRelationshipsIterator(nodeId) || hasIncomingRelationshipsIterator(nodeId);
        };
    }

    private boolean hasOutgoingRelationshipsIterator(long nodeId) {
        if (nodeId == NO_ID) {
            return false;
        }
        return graph.successors(nodeId) != LazyLongIterators.EMPTY_ITERATOR;
    }

    private boolean hasIncomingRelationshipsIterator(long nodeId) {
        if (nodeId == NO_ID) {
            return false;
        }
        return graph.predecessors(nodeId) != LazyLongIterators.EMPTY_ITERATOR;
    }

    public Iterator<WebGraphNode> getNodeIterator() {
        NodeIterator iter = graph.nodeIterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public WebGraphNode next() {
                return new WebGraphNode(iter.nextLong(), WebGraphDatabase.this);
            }
        };
    }

    public Iterator<WebGraphNode> resetNodeIterator() {
        return getNodeIterator();
    }

    public Iterator<WebGraphRelationship> resetRelationshipIterator() {
        return getRelationshipIterator();
    }

    public WebGraphPropertyProvider getPropertyProvider() {
        return propertyProvider;
    }
}
