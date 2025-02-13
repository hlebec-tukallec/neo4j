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
package org.neo4j.bolt.protocol.common.handler;

import static org.neo4j.bolt.protocol.common.handler.ProtocolHandshakeHandler.BOLT_MAGIC_PREAMBLE;
import static org.neo4j.memory.HeapEstimator.shallowSizeOfInstance;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import java.util.List;
import org.neo4j.bolt.negotiation.codec.ProtocolNegotiationRequestDecoder;
import org.neo4j.bolt.negotiation.codec.ProtocolNegotiationResponseEncoder;
import org.neo4j.bolt.protocol.common.connector.Connector;
import org.neo4j.bolt.protocol.common.connector.connection.Connection;
import org.neo4j.configuration.Config;
import org.neo4j.internal.helpers.Exceptions;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.memory.HeapEstimator;
import org.neo4j.packstream.codec.transport.WebSocketFramePackingEncoder;
import org.neo4j.packstream.codec.transport.WebSocketFrameUnpackingDecoder;
import org.neo4j.util.VisibleForTesting;

public class TransportSelectionHandler extends ByteToMessageDecoder {
    public static final long SHALLOW_SIZE = HeapEstimator.shallowSizeOfInstance(TransportSelectionHandler.class);

    public static final long SSL_HANDLER_SHALLOW_SIZE = shallowSizeOfInstance(SslHandler.class);
    public static final long HTTP_SERVER_CODEC_SHALLOW_SIZE = shallowSizeOfInstance(HttpServerCodec.class);
    public static final long HTTP_OBJECT_AGGREGATOR_SHALLOW_SIZE = shallowSizeOfInstance(HttpObjectAggregator.class);
    public static final long WEB_SOCKET_SERVER_PROTOCOL_HANDLER_SHALLOW_SIZE =
            shallowSizeOfInstance(WebSocketServerProtocolHandler.class);
    public static final long WEB_SOCKET_FRAME_AGGREGATOR_SHALLOW_SIZE =
            shallowSizeOfInstance(WebSocketFrameAggregator.class);

    private static final String WEBSOCKET_MAGIC = "GET ";
    private static final int MAX_WEBSOCKET_HANDSHAKE_SIZE = 65536;
    private static final int MAX_WEBSOCKET_FRAME_SIZE = 65536;

    private final Config config;
    private final SslContext sslContext;
    private final InternalLogProvider logging;
    private final InternalLog log;
    private final boolean isEncrypted;

    private Connector connector;
    private Connection connection;

    @VisibleForTesting
    TransportSelectionHandler(Config config, SslContext sslContext, InternalLogProvider logging, boolean isEncrypted) {
        this.config = config;
        this.sslContext = sslContext;
        this.logging = logging;
        this.isEncrypted = isEncrypted;

        this.log = logging.getLog(TransportSelectionHandler.class);
    }

    public TransportSelectionHandler(Config config, SslContext sslContext, InternalLogProvider logging) {
        this(config, sslContext, logging, false);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.connection = Connection.getConnection(ctx.channel());
        this.connector = this.connection.connector();
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) {
        this.connection.memoryTracker().releaseHeap(SHALLOW_SIZE);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // Will use the first five bytes to detect a protocol.
        if (in.readableBytes() < 5) {
            return;
        }

        if (detectSsl(in)) {
            if (this.isEncrypted) {
                log.error(
                        "Fatal error: multiple levels of SSL encryption detected." + " Terminating connection: %s",
                        ctx.channel());
                ctx.close();

                return;
            }

            enableSsl(ctx);
        } else if (isHttp(in)) {
            switchToWebsocket(ctx);
        } else if (isBoltPreamble(in)) {
            switchToSocket(ctx);
        } else {
            // TODO: send a alert_message for a ssl connection to terminate the handshake
            in.clear();
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            // Netty throws a NativeIoException on connection reset - directly importing that class
            // caused a host of linking errors, because it depends on JNI to work. Hence, we just
            // test on the message we know we'll get.
            if (Exceptions.contains(cause, e -> e.getMessage().contains("Connection reset by peer"))) {
                log.warn(
                        "Fatal error occurred when initialising pipeline, "
                                + "remote peer unexpectedly closed connection: %s",
                        ctx.channel());
            } else {
                log.error("Fatal error occurred when initialising pipeline: " + ctx.channel(), cause);
            }
        } finally {
            ctx.close();
        }
    }

    private static boolean isBoltPreamble(ByteBuf in) {
        return in.getInt(0) == BOLT_MAGIC_PREAMBLE;
    }

    private boolean detectSsl(ByteBuf buf) {
        return this.sslContext != null && SslHandler.isEncrypted(buf);
    }

    private static boolean isHttp(ByteBuf buf) {
        for (int i = 0; i < WEBSOCKET_MAGIC.length(); ++i) {
            if (buf.getUnsignedByte(buf.readerIndex() + i) != WEBSOCKET_MAGIC.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private void enableSsl(ChannelHandlerContext ctx) {
        // allocate sufficient space for another transport selection handlers as this instance will be freed upon
        // pipeline removal
        connection.memoryTracker().allocateHeap(SSL_HANDLER_SHALLOW_SIZE + SHALLOW_SIZE);

        ctx.pipeline()
                .addLast(this.sslContext.newHandler(ctx.alloc()))
                .remove(this)
                .addLast(new TransportSelectionHandler(config, this.sslContext, logging, true));
    }

    private void switchToSocket(ChannelHandlerContext ctx) {
        if (this.connector.isEncryptionRequired() && !isEncrypted) {
            throw new SecurityException("An unencrypted connection attempt was made where encryption is required.");
        }

        switchToHandshake(ctx);
    }

    private void switchToWebsocket(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.pipeline();

        connection
                .memoryTracker()
                .allocateHeap(HTTP_SERVER_CODEC_SHALLOW_SIZE
                        + HTTP_OBJECT_AGGREGATOR_SHALLOW_SIZE
                        + DiscoveryResponseHandler.SHALLOW_SIZE
                        + WEB_SOCKET_SERVER_PROTOCOL_HANDLER_SHALLOW_SIZE
                        + WEB_SOCKET_FRAME_AGGREGATOR_SHALLOW_SIZE
                        + WebSocketFramePackingEncoder.SHALLOW_SIZE
                        + WebSocketFrameUnpackingDecoder.SHALLOW_SIZE);

        p.addLast(
                new HttpServerCodec(),
                new HttpObjectAggregator(MAX_WEBSOCKET_HANDSHAKE_SIZE),
                new DiscoveryResponseHandler(this.connector.authConfigProvider()),
                new WebSocketServerProtocolHandler("/", null, false, MAX_WEBSOCKET_FRAME_SIZE),
                new WebSocketFrameAggregator(MAX_WEBSOCKET_FRAME_SIZE),
                new WebSocketFramePackingEncoder(),
                new WebSocketFrameUnpackingDecoder());

        switchToHandshake(ctx);
    }

    private void switchToHandshake(ChannelHandlerContext ctx) {
        connection
                .memoryTracker()
                .allocateHeap(ProtocolNegotiationResponseEncoder.SHALLOW_SIZE
                        + ProtocolNegotiationRequestDecoder.SHALLOW_SIZE
                        + ProtocolHandshakeHandler.SHALLOW_SIZE);

        ctx.pipeline()
                .addLast("protocolNegotiationRequestEncoder", new ProtocolNegotiationResponseEncoder())
                .addLast("protocolNegotiationRequestDecoder", new ProtocolNegotiationRequestDecoder());

        ProtocolLoggingHandler.shiftToEndIfPresent(ctx);

        ctx.pipeline().addLast("protocolHandshakeHandler", new ProtocolHandshakeHandler(config, logging));

        ctx.pipeline().remove(this);
    }
}
