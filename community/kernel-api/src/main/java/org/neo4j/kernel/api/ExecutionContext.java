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
package org.neo4j.kernel.api;

import org.neo4j.internal.kernel.api.security.AccessMode;
import org.neo4j.io.pagecache.context.CursorContext;

/**
 * Execution context that should be passed to workers in other threads but that are still belong to the transaction and need to have access to some
 * transactional resources.
 * Creation of context should be done in a transaction execution thread. Every other worker thread should have its own execution context.
 * In the end of evaluation worker thread should call {@link ExecutionContext#complete()} to mark context as completed and prepare data that needs
 * to be transferred back to owning transaction.
 * After that transaction executor thread should call {@link ExecutionContext#close()}
 */
public interface ExecutionContext extends AutoCloseable
{
    /**
     * Execution context cursor tracer. Page cache statistic recorded during execution reported back to owning transaction only when context is closed.
     *
     * @return execution context cursor tracer.
     */
    CursorContext cursorContext();

    /**
     * @return execution context security access mode
     */
    AccessMode accessMode();

    /**
     * Mark execution context as completed and prepare any data that needs to be reported back to owning transaction.
     * Should be called by thread where work was executed.
     */
    void complete();

    /**
     * Close execution context and merge back any data to the owning transaction if such exists.
     * Should be called by transaction thread.
     */
    @Override
    void close();
}
