/*
 * The MIT License
 *
 * Copyright 2015 tim.
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.mastfrog.url.HostAndPort;
import com.mastfrog.util.Exceptions;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContext;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.TrustManager;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
final class SslBootstrapCache {

    private final Ldr ldr = new Ldr();
    private final LoadingCache<HostAndPort, Bootstrap> bootstrapForHostPort = CacheBuilder.<HostAndPort, Bootstrap>newBuilder().concurrencyLevel(2).removalListener(ldr).expireAfterAccess(2, TimeUnit.MINUTES).build(ldr);
    private final EventLoopGroup group;
    private final Duration timeout;
    private final SslContext sslContext;
    private final MessageHandlerImpl handler;
    private final int maxChunkSize;
    private final int maxInitialLineLength;
    private final int maxHeadersSize;
    private final boolean compress;
    private final Iterable<HttpClientBuilder.ChannelOptionSetting<?>> settings;
    SslBootstrapCache(EventLoopGroup group, Duration timeout, SslContext sslContext, MessageHandlerImpl handler,
            int maxChunkSize, int maxInitialLineLength, int maxHeadersSize, boolean compress, Iterable<HttpClientBuilder.ChannelOptionSetting<?>> settings) {
        this.group = group;
        this.timeout = timeout;
        this.sslContext = sslContext;
        this.handler = handler;
        this.maxChunkSize = maxChunkSize;
        this.maxInitialLineLength = maxInitialLineLength;
        this.maxHeadersSize = maxHeadersSize;
        this.compress = compress;
        this.settings = settings;
    }

    Bootstrap sslBootstrap(HostAndPort hostAndPort) {
        try {
            return bootstrapForHostPort.get(hostAndPort);
        } catch (ExecutionException e) {
            return Exceptions.chuck(e);
        }
    }

    void clear() {
        bootstrapForHostPort.cleanUp();
        bootstrapForHostPort.invalidateAll();
    }

    private class Ldr extends CacheLoader<HostAndPort, Bootstrap> implements RemovalListener<HostAndPort, Bootstrap> {

        @Override
        public Bootstrap load(HostAndPort k) throws Exception {
            Bootstrap bootstrapSsl = new Bootstrap();
            bootstrapSsl.group(group);
            bootstrapSsl.handler(new Initializer(k, handler, sslContext, true, maxChunkSize, maxInitialLineLength, maxHeadersSize, compress));
            bootstrapSsl.option(ChannelOption.TCP_NODELAY, true);
            bootstrapSsl.option(ChannelOption.SO_REUSEADDR, false);
            bootstrapSsl.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            if (timeout != null) {
                bootstrapSsl.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.getMillis());
            }
            for (HttpClientBuilder.ChannelOptionSetting<?> setting : settings) {
                HttpClient.option(bootstrapSsl, setting);
            }
            bootstrapSsl.channelFactory(new HttpClient.NioChannelFactory());
            return bootstrapSsl;
        }

        @Override
        public void onRemoval(RemovalNotification<HostAndPort, Bootstrap> rn) {

        }
    }
}
