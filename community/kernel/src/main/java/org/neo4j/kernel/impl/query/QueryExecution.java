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
package org.neo4j.kernel.impl.query;

import org.neo4j.graphdb.ExecutionPlanDescription;
import org.neo4j.graphdb.Notification;
import org.neo4j.graphdb.QueryExecutionType;

/**
 * The execution of a query is a {@link QuerySubscription} with added methods describing the actual execution of a
 * query.
 */
public interface QueryExecution extends QuerySubscription {
    /**
     * The {@link QueryExecutionType} of the query,
     */
    QueryExecutionType executionType();

    /**
     * @return The plan description of the query.
     */
    ExecutionPlanDescription executionPlanDescription();

    /**
     * @return all notifications and warnings of the query.
     */
    Iterable<Notification> getNotifications();

    /**
     * The name of the fields of each record
     *
     * @return an array of the field names of each record.
     */
    String[] fieldNames();
}
