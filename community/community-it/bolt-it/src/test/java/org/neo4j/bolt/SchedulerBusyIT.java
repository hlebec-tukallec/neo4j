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
package org.neo4j.bolt;

import static org.neo4j.logging.AssertableLogProvider.Level.ERROR;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.neo4j.bolt.test.annotation.BoltTestExtension;
import org.neo4j.bolt.test.annotation.connection.initializer.Authenticated;
import org.neo4j.bolt.test.annotation.connection.initializer.Negotiated;
import org.neo4j.bolt.test.annotation.setup.FactoryFunction;
import org.neo4j.bolt.test.annotation.setup.SettingsFunction;
import org.neo4j.bolt.test.annotation.test.TransportTest;
import org.neo4j.bolt.testing.assertions.BoltConnectionAssertions;
import org.neo4j.bolt.testing.client.TransportConnection;
import org.neo4j.bolt.testing.messages.BoltWire;
import org.neo4j.bolt.transport.Neo4jWithSocket;
import org.neo4j.bolt.transport.Neo4jWithSocketExtension;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.LogAssertions;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.EphemeralTestDirectoryExtension;

/**
 * Evaluates whether the Bolt server responds correctly when its thread pool is exhausted.
 */
@EphemeralTestDirectoryExtension
@Neo4jWithSocketExtension
@BoltTestExtension
public class SchedulerBusyIT {

    private final AssertableLogProvider internalLogProvider = new AssertableLogProvider();
    private final AssertableLogProvider userLogProvider = new AssertableLogProvider();

    @Inject
    private Neo4jWithSocket server;

    @FactoryFunction
    void customizeDatabase(TestDatabaseManagementServiceBuilder factory) {
        factory.setInternalLogProvider(this.internalLogProvider);
        factory.setUserLogProvider(this.userLogProvider);
    }

    @SettingsFunction
    static void customizeSettings(Map<Setting<?>, Object> settings) {
        settings.put(BoltConnector.thread_pool_min_size, 0);
        settings.put(BoltConnector.thread_pool_max_size, 2);
    }

    @AfterEach
    void cleanup() {
        this.internalLogProvider.clear();
        this.userLogProvider.clear();
    }

    private static void enterStreaming(BoltWire wire, TransportConnection connection) throws IOException {
        connection.send(wire.run("UNWIND RANGE (1, 100) AS x RETURN x"));

        BoltConnectionAssertions.assertThat(connection).receivesSuccess();
    }

    private static void exitStreaming(BoltWire wire, TransportConnection connection) throws IOException {
        connection.send(wire.discard());

        BoltConnectionAssertions.assertThat(connection).receivesSuccess();
    }

    @TransportTest
    void shouldReportFailureWhenAllThreadsInThreadPoolAreBusy(
            BoltWire wire,
            @Authenticated TransportConnection connection1,
            @Authenticated TransportConnection connection2,
            @Negotiated TransportConnection connection3)
            throws IOException {
        // saturate the thread pool using autocommit transactions (this works since open transactions currently force
        // Bolt to stick to the worker thread until closed or timed out)
        enterStreaming(wire, connection1);
        enterStreaming(wire, connection2);

        // send another request on a third connection in order to generate a new job submission
        connection3.send(wire.hello());

        BoltConnectionAssertions.assertThat(connection3)
                .receivesFailureFuzzy(
                        Status.Request.NoThreadsAvailable,
                        "There are no available threads to serve this request at the moment");

        LogAssertions.assertThat(userLogProvider)
                .forLevel(ERROR)
                .containsMessages(
                        "since there are no available threads to serve it at the moment. You can retry at a later time");
        LogAssertions.assertThat(internalLogProvider)
                .forLevel(ERROR)
                .containsMessages(
                        "since there are no available threads to serve it at the moment. You can retry at a later time");

        BoltConnectionAssertions.assertThat(connection3).isEventuallyTerminated();
    }

    @TransportTest
    void shouldOperateNormallyWhenThreadsFreeUp(
            BoltWire wire,
            @Authenticated TransportConnection connection1,
            @Authenticated TransportConnection connection2,
            @Negotiated TransportConnection connection3,
            @Negotiated TransportConnection connection4)
            throws IOException {
        // saturate the thread pool using autocommit transactions (this works since open transactions currently force
        // Bolt to stick to the worker thread until closed or timed out)
        enterStreaming(wire, connection1);
        enterStreaming(wire, connection2);

        // free up a slot for the new connection
        exitStreaming(wire, connection1);

        // send another request on a third connection in order to generate a new job submission
        connection3.send(wire.hello());

        BoltConnectionAssertions.assertThat(connection3).receivesSuccess();

        // free up another slot for the new connection
        exitStreaming(wire, connection2);

        // send another request on a fourth connection in order to generate a new job submission
        connection4.send(wire.hello());

        BoltConnectionAssertions.assertThat(connection4).receivesSuccess();
    }

    @TransportTest
    void shouldStopConnectionsWhenRelatedJobIsRejectedOnShutdown(
            BoltWire wire,
            @Authenticated TransportConnection connection1,
            @Authenticated TransportConnection connection2,
            @Authenticated TransportConnection connection3,
            @Authenticated TransportConnection connection4)
            throws IOException {
        // start and terminate a few jobs regularly
        enterStreaming(wire, connection1);
        exitStreaming(wire, connection1);

        enterStreaming(wire, connection2);
        exitStreaming(wire, connection2);

        // saturate the thread pool
        enterStreaming(wire, connection3);
        enterStreaming(wire, connection4);

        // shutdown the server in order to trigger Bolt shutdown procedures
        this.server.shutdownDatabase();

        LogAssertions.assertThat(userLogProvider)
                .doesNotContainMessage(
                        "since there are no available threads to serve it at the moment. You can retry at a later time");
        LogAssertions.assertThat(internalLogProvider)
                .doesNotContainMessage(
                        "since there are no available threads to serve it at the moment. You can retry at a later time");

        BoltConnectionAssertions.assertThat(connection1).isEventuallyTerminated();
        BoltConnectionAssertions.assertThat(connection2).isEventuallyTerminated();
        BoltConnectionAssertions.assertThat(connection3).isEventuallyTerminated();
        BoltConnectionAssertions.assertThat(connection4).isEventuallyTerminated();
    }
}
