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
import org.neo4j.webgraph.WebGraphRelationship;

import java.util.Iterator;

public class WebGraphRelationshipIterator {
    private Iterator<WebGraphRelationship> iter;
    private final WebGraphDatabase graph;
    private long currentId = WebGraphDatabase.NO_ID;
    private WebGraphRelationship currentRelationship = null;

    private WebGraphRelationship previousRelationship = null;
    private static WebGraphRelationshipIterator singleInstance = null;

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
            previousRelationship = currentRelationship;
            currentRelationship = iter.next();
//            System.out.println("current rel: " + currentRelationship.toString());
            currentId = currentRelationship.getId();
        } else {
            previousRelationship = currentRelationship;
            currentRelationship = null;
            currentId = WebGraphDatabase.NO_ID;
        }
    }

    public void reset() {
        iter = this.graph.resetRelationshipIterator();
    }

    public long getRelationshipsCount() {
        return this.graph.getAllRelationships();
    }

    public WebGraphRelationship getPreviousRelationship() {
        return previousRelationship;
    }
}
