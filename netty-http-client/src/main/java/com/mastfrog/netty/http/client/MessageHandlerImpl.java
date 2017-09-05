/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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

import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.url.URL;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author Tim Boudreau
 */
@Sharable
final class MessageHandlerImpl extends ChannelInboundHandlerAdapter {

    private static final int DEFAULT_MAX_REDIRECTS = 15;
    private final boolean followRedirects;
    private final HttpClient client;
    private final int maxRedirects;
    private final boolean supportWebsockets;

    MessageHandlerImpl(boolean followRedirects, HttpClient client, int maxRedirects, boolean supportWebsockets) {
        this.followRedirects = followRedirects;
        this.client = client;
        this.maxRedirects = maxRedirects == -1 ? DEFAULT_MAX_REDIRECTS : maxRedirects;
        this.supportWebsockets = supportWebsockets;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        RequestInfo info = ctx.channel().attr(HttpClient.KEY).get();
        if (!info.cancelled.get()) {
            // Premature close, which is a legitimate way of ending a request
            // with no chunks and no Content-Length header according to RFC
            try {
                sendFullResponse(ctx);
            } finally {
                info.handle.event(new State.Closed());
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        RequestInfo info = ctx.channel().attr(HttpClient.KEY).get();
        info.handle.event(new State.Error(cause));
    }

    private boolean checkCancelled(ChannelHandlerContext ctx) {
        RequestInfo info = ctx.channel().attr(HttpClient.KEY).get();
        boolean result = info != null && info.cancelled.get();
        if (result) {
            Channel ch = ctx.channel();
            if (ch.isOpen()) {
                ch.close();
            }
        }
        return result;
    }

    static class ResponseState {

        private final CompositeByteBuf aggregateContent;
        volatile HttpResponse resp;
        volatile boolean fullResponseSent;
        volatile boolean websocketHandshakeSucceeded;
        private int readableBytes;
        private final boolean dontAggregate;

        ResponseState(ChannelHandlerContext ctx, boolean dontAggregate) {
            aggregateContent = ctx.alloc().compositeBuffer();
            this.dontAggregate = dontAggregate;
        }

        int readableBytes() {
            return readableBytes;
        }

        void append(ByteBuf buf) {
            readableBytes += buf.readableBytes();
            if (!dontAggregate) {
                int ix = aggregateContent.writerIndex();
                int added = buf.readableBytes();
                aggregateContent.addComponent(buf);
                aggregateContent.writerIndex(ix + added);
            }
        }

        boolean hasResponse() {
            return resp != null;
        }
    }

    public static final AttributeKey<ResponseState> RS = AttributeKey.<ResponseState>valueOf("state");

    private ResponseState state(ChannelHandlerContext ctx, RequestInfo info) {
        Attribute<ResponseState> st = ctx.channel().attr(RS);
        ResponseState rs = st.get();
        if (rs == null) {
            rs = new ResponseState(ctx, info.dontAggregate);
            st.set(rs);
        }
        return rs;
    }

    private String isRedirect(RequestInfo info, HttpResponse msg) throws UnsupportedEncodingException {
        HttpResponseStatus status = msg.status();
        switch (status.code()) {
            case 300:
            case 301:
            case 302:
            case 303:
            case 305:
            case 307:
                String hdr = URLDecoder.decode(msg.headers().get(HttpHeaderNames.LOCATION), "UTF-8");
                if (hdr != null) {
                    if (hdr.toLowerCase().startsWith("http://") || hdr.toLowerCase().startsWith("https://")) {
                        return hdr;
                    } else {
                        URL orig = info.url;
                        String pth = orig.getPath() == null ? "/" : URLDecoder.decode(orig.getPath().toString(), "UTF-8");
                        if (hdr.startsWith("/")) {
                            pth = hdr;
                        } else if (pth.endsWith("/")) {
                            pth += hdr;
                        } else {
                            pth += "/" + hdr;
                        }
                        StringBuilder sb = new StringBuilder(orig.getProtocol().toString());
                        sb.append("://").append(orig.getHost());
                        if (!orig.getProtocol().getDefaultPort().equals(orig.getPort())) {
                            sb.append(":").append(orig.getPort());
                        }
                        if (pth.charAt(0) != '/') {
                            sb.append('/');
                        }
                        sb.append(pth);
                        return sb.toString();
                    }
                }
            default:
                return null;
        }
    }

    static boolean devLogging() {
        return Boolean.getBoolean("http.client.dev.logging");
    }

    static final void logHandlers(ChannelHandlerContext ctx, String msg) {
        if (devLogging()) {
            System.out.println("  CLIENT " + msg + " PIPELINE NOW: ");
            ctx.pipeline().forEach((Entry<String, ChannelHandler> e) -> {
                System.out.println("    - " + e.getKey() + "\t" + e.getValue());
            });
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
//        final RequestInfo info = ctx.channel().attr(HttpClient.KEY).get();
//        Protocol proto = info.url.getProtocol();
//        if (supportWebsockets && (Protocols.WS.match(proto) || Protocols.WSS.match(proto))) {
//            websocketHandshake(ctx, info);
//        }
        logHandlers(ctx, "handlerAdded");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        logHandlers(ctx, "handlerRemoved");
    }

    void websocketHandshake(ChannelHandlerContext ctx, RequestInfo info) {
        final ResponseState state = state(ctx, info);
        CharSequence connectionHeader = state.resp == null ? HttpHeaderValues.UPGRADE : state.resp.headers().get(HttpHeaderNames.CONNECTION);
        if (connectionHeader != null && HttpHeaderValues.UPGRADE.contentEqualsIgnoreCase(connectionHeader)) {
            CharSequence upgradeTo = state.resp == null ? HttpHeaderValues.WEBSOCKET : state.resp.headers().get(HttpHeaderNames.UPGRADE);
            if (upgradeTo != null && HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(upgradeTo)) {
                final WebSocketClientHandshaker hs = info.handshaker();
                ChannelPromise handshakePromise = ctx.newPromise();
                handshakePromise.addListener((ChannelFutureListener) (ChannelFuture future) -> {
                    if (!future.isSuccess()) {
                        info.handle.event(new State.Error(future.cause()));
                    } else {
                        state.websocketHandshakeSucceeded = true;

                        if (devLogging()) {
                            System.out.println("  CLIENT websocketHandshakeSucceeded PIPELINE NOW: ");
                        }
                        ctx.pipeline().addBefore("handler", "ws", new WSHandler(hs));

                        if (devLogging()) {
                            ctx.pipeline().forEach((Entry<String, ChannelHandler> e) -> {
                                System.out.println("    - " + e.getKey() + "\t" + e.getValue());
                            });
                        }
                        info.handle.event(new State.WebsocketHandshakeComplete(hs));
                    }
                });
                hs.handshake(ctx.channel(), handshakePromise);
            }
        }
    }

    final class WSHandler extends WebSocketClientProtocolHandler {

        WSHandler(WebSocketClientHandshaker handshaker) {
            super(handshaker, false);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (devLogging()) {
                System.out.println("WSCPH read " + msg);
            }
            super.channelRead(ctx, msg);
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) throws Exception {
            super.decode(ctx, frame, out);
            if (devLogging()) {
                System.out.println("WS DECODE " + frame + " into " + out);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            final RequestInfo info = ctx.channel().attr(HttpClient.KEY).get();
            info.handle.event(new State.Error(cause));
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        final RequestInfo info = ctx.channel().attr(HttpClient.KEY).get();
        if (checkCancelled(ctx)) {
            return;
        }
        if (msg instanceof FullHttpResponse && info.hasHandshaker() && !info.handshaker().isHandshakeComplete()) {
            info.handshaker().finishHandshake(ctx.channel(), (FullHttpResponse) msg);
            return;
        }
        if (checkCancelled(ctx)) {
            return;
        }
        final ResponseState state = state(ctx, info);
        if (msg instanceof HttpResponse) {
            state.resp = (HttpResponse) msg;
            boolean redirectLoop = false;
            String redirUrl = null;
            if (followRedirects) {
                redirUrl = isRedirect(info, state.resp);
                if (redirUrl != null) {
                    Method meth = state.resp.status().code() == 303 ? Method.GET : Method.valueOf(info.req.method().name());
                    // Shut off events from the old request
                    AtomicBoolean ab = new AtomicBoolean(true);
                    RequestInfo b = new RequestInfo(info.url, info.req, ab, new ResponseFuture(ab), null, info.timeout, info.timer, info.dontAggregate, info.chunkedBody, info.websocketVersion);
                    ctx.channel().attr(HttpClient.KEY).set(b);
                    if (info.redirectCount.getAndIncrement() < maxRedirects) {
                        URL url;
                        if (redirUrl.contains("://")) {
                            url = URL.parse(redirUrl);
                        } else {
                            if (redirUrl.length() > 0 && redirUrl.charAt(0) == '/') {
                                url = URL.parse(info.url.getBaseURL(true) + redirUrl);
                            } else if (redirUrl.length() > 0 && redirUrl.charAt(0) != '/') {
                                url = URL.parse(info.url.toString() + redirUrl);
                            } else {
                                url = URL.parse(redirUrl);
                            }
                        }
                        if (!url.isValid()) {
                            info.handle.event(new State.Error(new RedirectException(RedirectException.Kind.INVALID_REDIRECT_URL, redirUrl)));
                            return;
                        }
                        info.handle.event(new State.Redirect(url));
                        info.handle.cancelled = new AtomicBoolean();
                        client.redirect(meth, url, info);
                        return;
                    } else {
                        redirectLoop = true;
                    }
                }
            }
            info.handle.event(new State.HeadersReceived(state.resp));
            if (redirectLoop) {
                RedirectException redirException = new RedirectException(RedirectException.Kind.REDIRECT_LOOP, redirUrl);
                info.handle.event(new State.Error(redirException));
                info.handle.cancelled.lazySet(true);
            }
            if (redirUrl == null && supportWebsockets) {
                websocketHandshake(ctx, info);
            }
        } else if (msg instanceof HttpContent) {
            HttpContent c = (HttpContent) msg;
            info.handle.event(new State.ContentReceived(c));
            c.content().resetReaderIndex();
            if (c.content().readableBytes() > 0) {
                state.append(c.content());
            }
            state.aggregateContent.resetReaderIndex();
            boolean last = c instanceof LastHttpContent;
            if (!last && state.resp.headers().get(HttpHeaderNames.CONTENT_LENGTH) != null) {
                long len = Headers.CONTENT_LENGTH.toValue(state.resp.headers().get(HttpHeaderNames.CONTENT_LENGTH)).longValue();
                last = state.readableBytes() >= len;
            }
            if (last) {
                c.content().resetReaderIndex();
                sendFullResponse(ctx);
            }
        } else if (msg instanceof WebSocketFrame) {
            if (devLogging()) {
                System.out.println("Received web socket frame");
            }
            WebSocketFrame frame = (WebSocketFrame) msg;
            info.handle.event(new State.WebSocketFrameReceived(frame));
            frame.content().resetReaderIndex();
        } else {
            info.handle.event(new State.Error(new IllegalStateException("Unknown object decoded: " + msg)));
        }
    }

    void sendFullResponse(ChannelHandlerContext ctx) {
        RequestInfo info = ctx.channel().attr(HttpClient.KEY).get();
        if (info != null && info.dontAggregate) {
            return;
        }
        ResponseState state = state(ctx, info);
        Class<? extends State<?>> type = State.Finished.class;
        state.aggregateContent.resetReaderIndex();
        if (info != null) {
            info.cancelTimer();
        }
        if ((info.r != null || info.handle.has(type)) && !state.fullResponseSent && state.aggregateContent.readableBytes() > 0) {
            state.fullResponseSent = true;
            info.handle.event(new State.FullContentReceived(state.aggregateContent));
            DefaultFullHttpResponse full = new DefaultFullHttpResponse(state.resp.protocolVersion(), state.resp.status(), state.aggregateContent);
            for (Map.Entry<String, String> e : state.resp.headers().entries()) {
                full.headers().add(e.getKey(), e.getValue());
            }
            state.aggregateContent.resetReaderIndex();

            if (info.r != null) {
                info.r.internalReceive(state.resp.status(), state.resp.headers(), state.aggregateContent);
            }
            state.aggregateContent.resetReaderIndex();
            info.handle.event(new State.Finished(full));
            state.aggregateContent.resetReaderIndex();
            info.handle.trigger();
        }
    }
}
