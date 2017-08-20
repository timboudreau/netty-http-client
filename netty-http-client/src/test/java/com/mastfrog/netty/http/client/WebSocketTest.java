/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.netty.http.client;

import com.google.common.net.MediaType;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.stringHeader;
import static com.mastfrog.acteur.util.Connection.upgrade;
import com.mastfrog.util.net.PortFinder;
import com.mastfrog.util.thread.Receiver;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContentCompressor;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_VERSION;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.UPGRADE_REQUIRED;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.JdkLoggerFactory;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class WebSocketTest {

    private static final String WEBSOCKET_PATH = "/websocket";

    EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    EventLoopGroup workerGroup = new NioEventLoopGroup();

    int port = new PortFinder().findAvailableServerPort();
    InetSocketAddress addr = new InetSocketAddress("127.0.0.1", port);
    WebSocketFrameHandler frameHandler = new WebSocketFrameHandler();
    Throwable thrown;
    Channel channel;

    HttpClient client = HttpClient.builder().withWebsocketSupport().useCompression().build();
    String url = "http://" + addr.getHostString() + ":" + port + WEBSOCKET_PATH;
    String wsUrl = "ws://" + addr.getHostString() + ":" + port + WEBSOCKET_PATH;

    ResponseHandler<String> rh = new ResponseHandler<String>(String.class) {
        @Override
        protected void receive(String obj) {
            System.out.println("RECEIVED " + obj);
        }

        @Override
        protected void onError(Throwable err) {
            System.out.println("ON ERROR " + err);
            thrown = err;
            super.onError(err);
        }

    };

    private final TxT A = new TxT("hey there");
    private final TxT B = new TxT("hello");
    private final TxT C = new TxT("world");

    @Test(timeout = 10000)
    public void testWebsockets() throws Throwable {
        ResponseFuture fut = client.post()
                .setURL(url)
                .addHeader(Headers.CONNECTION, upgrade)
                .addHeader(stringHeader("Upgrade"), "websocket")
                .addHeader(stringHeader("Origin"), "http://localhost:" + port + WEBSOCKET_PATH)
                .setBody("x", MediaType.PLAIN_TEXT_UTF_8)
                .setWebSocketVersion(WebSocketVersion.V13)
//                .dontAggregateResponse()
                .onEvent(Receiver.of((Throwable err, State<?> obj) -> {
                    if (err != null) {
                        err.printStackTrace();
                        thrown = err;
                    } else {
                        System.out.println("EVENT: " + obj + " " + obj.get());
                        if (obj.get() instanceof Throwable) {
                            System.out.println("\n\nTHROWN IN CLIENT\n\n");
                            thrown = (Throwable) obj.get();
                            thrown.printStackTrace();
                        }
                    }
                })).execute(rh)
                .sendOn(StateType.WebsocketHandshakeComplete, A)
                ;

        fut.on(StateType.WebSocketFrameReceived, Receiver.of((Throwable err, State.WebSocketFrameReceived obj) -> {
            System.out.println("\n\n***********\nFRAME RECEIVED: " + obj + "\n\n");
        }));

        fut.await(1, TimeUnit.SECONDS);
        throwIfNecessary();
        System.out.println("SEND MORE STUFF");
        fut.sendOn(StateType.WebsocketHandshakeComplete, B);
        fut.sendOn(StateType.WebsocketHandshakeComplete, C);
        Thread.sleep(2);
        A.assertContentCalled();
        B.assertContentCalled();
        C.assertContentCalled();
        throwIfNecessary();
    }

    void throwIfNecessary() throws Throwable {
        if (thrown != null) {
            Throwable t = thrown;
            thrown = null;
            throw t;
        }
    }

    @Before
    public void startServer() throws Throwable {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LH())
                .childHandler(new WebSocketServerInitializer());

        channel = b.bind(addr).sync().channel();
        System.out.println("Server started on " + port);
    }

    @After
    public void shutdownThreadPools() throws Throwable {
        System.out.println("Shut down");
        if (channel.isOpen()) {
            channel.close().sync();
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        client.shutdown();
    }

    static class TxT extends TextWebSocketFrame {

        private boolean contentCalled;
        private String txt;

        public TxT(String text) {
            super(text);
            this.txt = text;
        }

        public String toString() {
            boolean old = contentCalled;
            String result = txt + " (ri: " + content().readerIndex() + ")";
            contentCalled = old;
            return result;
        }

        void assertContentCalled() {
            boolean old = contentCalled;
            contentCalled = false;
            assertTrue("Content was not fetched", old);
        }

        @Override
        public ByteBuf content() {
            contentCalled = true;
            return super.content();
        }
    }

    public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast("decoder", new HttpRequestDecoder());
            pipeline.addLast("encoder", new HttpResponseEncoder());
            pipeline.addLast("aggregator", new HttpObjectAggregator(1024 * 1024 * 5));
            pipeline.addLast("compressor", new HttpContentCompressor());
            pipeline.addLast("index", new WebSocketIndexPageHandler());
            pipeline.addLast("wscompress", new WebSocketServerCompressionHandler());
//            pipeline.addLast("wsproto", new WebSocketServerProtocolHandler(WEBSOCKET_PATH));
            pipeline.addLast("websocket", frameHandler);
        }
    }

    final class LH extends LoggingHandler {

        LH() {
            super(LogLevel.INFO);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Handler removed: " + ctx.pipeline());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("SERVER CHANNEL READ " + msg.getClass().getSimpleName() + " : " + msg);
            super.channelRead(ctx, msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            thrown = cause;

            System.out.println("\n\nTHROWN IN SERVER\n\n");
            thrown.printStackTrace();
        }
    }

    @Sharable
    public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("WS CHANNEL READ " + msg.getClass().getSimpleName() + " : " + msg);
            super.channelRead(ctx, msg); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
            System.out.println("WS read0 " + frame);
            // ping and pong frames already handled
            if (frame instanceof TextWebSocketFrame) {
                // Send the uppercase string back.
                String request = ((TextWebSocketFrame) frame).text();
                System.out.println("Received " + ctx.channel() + " " + request);
                ctx.channel().writeAndFlush(new TextWebSocketFrame(request.toUpperCase(Locale.US)));
            } else if (frame instanceof PingWebSocketFrame) {
                PingWebSocketFrame ping = (PingWebSocketFrame) frame;
                ctx.write(new PongWebSocketFrame(ping.content().retain()));
            } else if (frame instanceof CloseWebSocketFrame) {
                ctx.channel().close();
            } else {
                String message = "unsupported frame type: " + frame.getClass().getName();
                throw new UnsupportedOperationException(message);
            }
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            System.out.println("  SERVER handlerAdded PIPELINE NOW: ");
            ctx.pipeline().forEach((Map.Entry<String, ChannelHandler> e) -> {
                System.out.println("    - " + e.getKey() + "\t" + e.getValue());
            });
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            System.out.println("  SERVER handlerRemoved PIPELINE NOW: ");
            ctx.pipeline().forEach((Map.Entry<String, ChannelHandler> e) -> {
                System.out.println("    - " + e.getKey() + "\t" + e.getValue());
            });
        }

    }

    class WebSocketIndexPageHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
            if (!req.decoderResult().isSuccess()) {
                sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
                return;
            }

            WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                    wsUrl, null, true, 1024 * 1024 * 5, true);
            WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(req);
            if (handshaker == null) {
                DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, UPGRADE_REQUIRED);
                System.out.println("SEND NULL HS RESPONSE");
                resp.headers().set(SEC_WEBSOCKET_VERSION, WebSocketVersion.V13.toHttpHeaderValue());
                sendHttpResponse(ctx, req, resp);
            } else {
                ChannelFuture future = handshaker.handshake(ctx.channel(), req);
                future.addListener((ChannelFutureListener) (ChannelFuture future1) -> {
                    if (future1.isSuccess()) {
                        Channel ch = future1.channel();
                        System.out.println("Websocket connected " + handshaker.version() + " " + handshaker.selectedSubprotocol());
                        ctx.pipeline().remove(WebSocketIndexPageHandler.this);

//                        ch.writeAndFlush(DefaultLastHttpContent.EMPTY_LAST_CONTENT);
//                        ch.writeAndFlush(new TxT("Woo hoo!")).addListener(new ChannelFutureListener() {
//                            @Override
//                            public void operationComplete(ChannelFuture future) throws Exception {
//                                if (!future.isSuccess()) {
//                                    thrown = future.cause();
//                                }
//                            }
//
//                        });
                        return;
                    } else if (future1.cause() != null) {
                        thrown = future1.cause();
                        thrown.printStackTrace();
                    }
                    future1.addListener(ChannelFutureListener.CLOSE);
                });
            }
        }

        private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
            // Generate an error page if response getStatus code is not OK (200).
            if (res.status().code() != 200) {
                ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
                res.content().writeBytes(buf);
                buf.release();
                HttpUtil.setContentLength(res, res.content().readableBytes());
            }

            System.out.println("Send response " + req);

            // Send the response and close the connection if necessary.
            ChannelFuture f = ctx.channel().writeAndFlush(res);
//            if (!HttpUtil.isKeepAlive(req) || res.status().code() != 200) {
//                f.addListener(ChannelFutureListener.CLOSE);
//            }
        }
    }

    @BeforeClass
    public static void configureLogging() {
        InternalLoggerFactory.setDefaultFactory(new JdkLoggerFactory());
    }

    public static void main(String[] args) throws Throwable {
        WebSocketTest test = new WebSocketTest();
        try {
            test.startServer();
            test.channel.closeFuture().sync();
        } finally {
            test.shutdownThreadPools();
        }
    }
}
