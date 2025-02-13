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
package org.neo4j.bolt.protocol.common.transaction;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.neo4j.bolt.dbapi.BoltGraphDatabaseServiceSPI;
import org.neo4j.bolt.dbapi.BoltQueryExecution;
import org.neo4j.bolt.dbapi.BoltQueryExecutor;
import org.neo4j.bolt.dbapi.BoltTransaction;
import org.neo4j.bolt.protocol.common.bookmark.Bookmark;
import org.neo4j.bolt.protocol.common.connector.tx.TransactionOwner;
import org.neo4j.bolt.protocol.common.message.AccessMode;
import org.neo4j.bolt.protocol.common.message.result.BoltResult;
import org.neo4j.bolt.protocol.common.message.result.BoltResultHandle;
import org.neo4j.bolt.protocol.common.transaction.result.AdaptingBoltQuerySubscriber;
import org.neo4j.bolt.protocol.common.transaction.statement.StatementProcessorReleaseManager;
import org.neo4j.bolt.protocol.v40.bookmark.BookmarkWithDatabaseId;
import org.neo4j.bolt.protocol.v41.message.request.RoutingContext;
import org.neo4j.exceptions.KernelException;
import org.neo4j.internal.kernel.api.security.LoginContext;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.impl.query.QueryExecution;
import org.neo4j.kernel.impl.query.QueryExecutionKernelException;
import org.neo4j.time.SystemNanoClock;
import org.neo4j.values.virtual.MapValue;

public abstract class AbstractTransactionStateMachineSPI implements TransactionStateMachineSPI {
    private final BoltGraphDatabaseServiceSPI boltGraphDatabaseServiceSPI;
    private final Clock clock;
    private final TransactionOwner owner;
    private final StatementProcessorReleaseManager resourceReleaseManager;
    private final String transactionId;

    public AbstractTransactionStateMachineSPI(
            BoltGraphDatabaseServiceSPI boltGraphDatabaseServiceSPI,
            TransactionOwner owner,
            SystemNanoClock clock,
            StatementProcessorReleaseManager resourceReleaseManager,
            String transactionId) {
        this.boltGraphDatabaseServiceSPI = boltGraphDatabaseServiceSPI;
        this.owner = owner;
        this.clock = clock;
        this.resourceReleaseManager = resourceReleaseManager;
        this.transactionId = transactionId;
    }

    @Override
    public Bookmark newestBookmark(BoltTransaction tx) {
        var bookmarkMetadata = tx.getBookmarkMetadata();
        return bookmarkMetadata.toBookmark(BookmarkWithDatabaseId::new);
    }

    @Override
    public BoltTransaction beginTransaction(
            KernelTransaction.Type transactionType,
            LoginContext loginContext,
            List<Bookmark> bookmarks,
            Duration txTimeout,
            AccessMode accessMode,
            Map<String, Object> txMetadata,
            RoutingContext routingContext) {
        return boltGraphDatabaseServiceSPI.beginTransaction(
                transactionType,
                loginContext,
                owner.info(),
                bookmarks,
                txTimeout,
                accessMode,
                txMetadata,
                routingContext);
    }

    @Override
    public BoltResultHandle executeQuery(BoltQueryExecutor boltQueryExecutor, String statement, MapValue params) {
        return newBoltResultHandle(statement, params, boltQueryExecutor);
    }

    @Override
    public boolean supportsNestedStatementsInTransaction() {
        return false;
    }

    @Override
    public void transactionClosed() {
        boltGraphDatabaseServiceSPI.freeTransaction();
        resourceReleaseManager.releaseStatementProcessor(transactionId);
    }

    protected abstract BoltResultHandle newBoltResultHandle(
            String statement, MapValue params, BoltQueryExecutor boltQueryExecutor);

    public abstract class AbstractBoltResultHandle implements BoltResultHandle {
        private final String statement;
        private final MapValue params;
        private final BoltQueryExecutor boltQueryExecutor;
        private BoltQueryExecution boltQueryExecution;

        public AbstractBoltResultHandle(String statement, MapValue params, BoltQueryExecutor boltQueryExecutor) {
            this.statement = statement;
            this.params = params;
            this.boltQueryExecutor = boltQueryExecutor;
        }

        @Override
        public BoltResult start() throws KernelException {
            try {
                AdaptingBoltQuerySubscriber subscriber = new AdaptingBoltQuerySubscriber();
                boltQueryExecution = boltQueryExecutor.executeQuery(statement, params, true, subscriber);
                QueryExecution result = boltQueryExecution.getQueryExecution();
                subscriber.assertSucceeded();
                return newBoltResult(result, subscriber, clock);
            } catch (KernelException e) {
                close(false);
                throw new QueryExecutionKernelException(e);
            } catch (Throwable e) {
                close(false);
                throw e;
            }
        }

        protected abstract BoltResult newBoltResult(
                QueryExecution result, AdaptingBoltQuerySubscriber subscriber, Clock clock);

        @Override
        public void close(boolean success) {
            if (boltQueryExecution != null) {
                boltQueryExecution.close();
            }
        }

        @Override
        public void terminate() {
            if (boltQueryExecution != null) {
                boltQueryExecution.terminate();
            }
        }
    }
}
