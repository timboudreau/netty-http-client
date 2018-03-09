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
package com.mastfrog.tiny.http.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderValues.CHUNKED;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import io.netty.util.CharsetUtil;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@Sharable
final class TinyHttpServerHandler extends ChannelInboundHandlerAdapter {

    private final Responder responder;

    TinyHttpServerHandler(Responder responder) {
        this.responder = responder;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;

            if (HttpUtil.is100ContinueExpected(req)) {
                ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
            }
            boolean keepAlive = HttpUtil.isKeepAlive(req) && req.headers().contains(CONNECTION);

            ResponseHead headers = new ResponseHead();
            Object theContent;
            try {
                theContent = responder.receive(req, headers);
            } catch (Exception ex) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (PrintStream ps = new PrintStream(baos)) {
                    ex.printStackTrace(ps);
                }
                headers = new ResponseHead();
                headers.code = 500;
                theContent = baos.toByteArray();
                Logger.getLogger(TinyHttpServerHandler.class.getName()).log(Level.SEVERE, null, ex);
            }
            final Object content = theContent;
            if (content instanceof ChunkedResponse) {
                DefaultHttpResponse response = new DefaultHttpResponse(headers.version, HttpResponseStatus.valueOf(headers.code));
                if (keepAlive) {
                    response.headers().set(CONNECTION, KEEP_ALIVE);
                }
                for (Map.Entry<CharSequence, Object> e : headers.headers.entrySet()) {
                    response.headers().set(e.getKey(), e.getValue());
                }
                response.headers().set(TRANSFER_ENCODING, CHUNKED);
                AtomicInteger count = new AtomicInteger();
                final ChannelFutureListener onFlush = new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture f) throws Exception {
                        Object chunk = ((ChunkedResponse) content).nextChunk(count.incrementAndGet());
                        boolean last = chunk == null;
                        if (last) {
                            ChannelFuture fut = f.channel().writeAndFlush(new DefaultLastHttpContent());
                            if (!keepAlive) {
                                fut.addListener(CLOSE);
                            }
                        } else {
                            HttpContent httpChunk;
                            if (!(chunk instanceof HttpContent)) {
                                httpChunk = new DefaultHttpContent(toByteBuf(chunk));
                            } else {
                                httpChunk = (HttpContent) chunk;
                            }
                            f.channel().writeAndFlush(httpChunk).addListener(this);
                        }
                    }
                };
                ctx.writeAndFlush(response).addListener(onFlush);
            } else {
                ByteBuf buf = toByteBuf(content);
                FullHttpResponse response = new DefaultFullHttpResponse(headers.version, HttpResponseStatus.valueOf(headers.code), buf);
                for (Map.Entry<CharSequence, Object> e : headers.headers.entrySet()) {
                    response.headers().set(e.getKey(), e.getValue());
                }
                response.headers().setInt(CONTENT_LENGTH, buf.readableBytes());
                if (!keepAlive) {
                    ctx.write(response).addListener(CLOSE);
                } else {
                    response.headers().set(CONNECTION, KEEP_ALIVE);
                    ctx.write(response);
                }
            }
        }
    }

    private Throwable lastCause;

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        lastCause = cause;
        ctx.close();
    }

    void throwLast() {
        Throwable thr = lastCause;
        lastCause = null;
        if (thr != null) {
            chuck(lastCause);
        }
    }

    public static <ReturnType> ReturnType chuck(Throwable t) {
        chuck(RuntimeException.class, t);
        throw new AssertionError(t); //should not get here
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> void chuck(Class<T> type, Throwable t) throws T {
        throw (T) t;
    }

    private ByteBuf toByteBuf(Object content) {
        if (content instanceof ByteBuf) {
            return (ByteBuf) content;
        } else if (content instanceof byte[]) {
            return Unpooled.wrappedBuffer((byte[]) content);
        } else if (content instanceof CharSequence) {
            return toByteBuf(content.toString().getBytes(CharsetUtil.UTF_8));
        } else if (content == null) {
            return Unpooled.EMPTY_BUFFER;
        } else {
            return toByteBuf(content.toString());
        }
    }
}
