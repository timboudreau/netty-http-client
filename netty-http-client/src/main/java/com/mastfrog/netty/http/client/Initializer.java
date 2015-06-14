/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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

import static com.mastfrog.netty.http.client.HttpClient.KEY;
import com.mastfrog.url.HostAndPort;
import com.sun.nio.sctp.MessageInfo;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import javax.net.ssl.SSLEngine;

/**
 *
 * @author Tim Boudreau
 */
final class Initializer extends ChannelInitializer<Channel> {

    private final HostAndPort hostPort;

    private final ChannelInboundHandlerAdapter handler;
    private final SslContext context;
    private final boolean ssl;
    private final int maxChunkSize;
    private final int maxInitialLineLength;
    private final boolean compress;

    public Initializer(HostAndPort hostPort, ChannelInboundHandlerAdapter handler, SslContext context, boolean ssl, int maxChunkSize, int maxInitialLineLength, int maxHeadersSize, boolean compress) {
        this.hostPort = hostPort;
        this.handler = handler;
        this.context = context;
        this.ssl = ssl;
        this.maxChunkSize = maxChunkSize;
        this.maxInitialLineLength = maxInitialLineLength;
        this.compress = compress;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (ssl) {
            SslContext clientContext = context == null ? SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build() : context;
            pipeline.addLast("ssl", new ExceptionForwardingSslHandler(clientContext.newEngine(ByteBufAllocator.DEFAULT, hostPort.host(), hostPort.port())));
//            pipeline.addLast("ssl", clientContext.newHandler(ByteBufAllocator.DEFAULT, hostPort.host(), hostPort.port()));
        }
        pipeline.addLast("http-codec", new HttpClientCodec(maxInitialLineLength, maxChunkSize, maxChunkSize));
        if (compress) {
            pipeline.addLast("decompressor", new HttpContentDecompressor());
        }
        pipeline.addLast("handler", handler);
    }

    // Ensure exceptions during handshaking get propagated
    private static class ExceptionForwardingSslHandler extends SslHandler {

        public ExceptionForwardingSslHandler(SSLEngine engine) {
            super(engine);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            RequestInfo info = ctx.channel().attr(HttpClient.KEY).get();
            if (info != null) {
                info.handle.event(new State.Error(cause));
            }
//            super.exceptionCaught(ctx, cause);
            cause.printStackTrace(System.err);
        }
    }
}
