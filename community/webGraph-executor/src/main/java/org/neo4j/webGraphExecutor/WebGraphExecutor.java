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
package org.neo4j.webGraphExecutor;

import it.unimi.dsi.big.webgraph.BidirectionalImmutableGraph;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.webgraph.WebGraphDatabase;
import org.neo4j.webgraph.property.WebGraphPropertyProvider;

import java.nio.file.Path;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

public class WebGraphExecutor {
    private GraphDatabaseAPI db;

    public void initialise(BidirectionalImmutableGraph graph, WebGraphPropertyProvider propertyProvider, String logPath) {
        WebGraphDatabase.initialise(graph, propertyProvider);

        Path path = Path.of(logPath);
        DatabaseManagementService managementService =
                new TestDatabaseManagementServiceBuilder(path).build();
        this.db = (GraphDatabaseAPI) managementService.database(DEFAULT_DATABASE_NAME);
    }

    public String executeQuery(String query) {
        String queryResult;
        try (Transaction transaction = db.beginTx()) {
            Result result = transaction.execute(query);
            queryResult = result.resultAsString();
            transaction.commit();
        }
        return queryResult;
    }
}
