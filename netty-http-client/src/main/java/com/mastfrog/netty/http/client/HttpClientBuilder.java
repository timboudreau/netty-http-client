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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.marshallers.ContentMarshallers;
import com.mastfrog.util.Checks;
import com.mastfrog.marshallers.Marshaller;
import com.mastfrog.marshallers.netty.NettyContentMarshallers;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Builds an HTTP client.
 *
 * @author Tim Boudreau
 */
public final class HttpClientBuilder {

    private static final int DEFAULT_THREAD_COUNT = 4;
    private int threadCount = -1;
    private int maxChunkSize = 65_536;
    private boolean compression = true;
    private int maxInitialLineLength = 2_048;
    private int maxHeadersSize = 16_384;
    private boolean followRedirects = true;
    private String userAgent;
    private final List<RequestInterceptor> interceptors = new LinkedList<>();
    private boolean send100continue = true;
    private CookieStore cookies;
    private Duration timeout;
    private SslContext sslContext;
    private AddressResolverGroup<? extends SocketAddress> resolver;
    private NioEventLoopGroup group;
    private int maxRedirects = -1;
    private ObjectMapper mapper = new ObjectMapper();
    private final Set<MarshallerEntry<?>> marshallers = new HashSet<>();

    /**
     * Set the ObjectMapper used to read and write JSON.
     *
     * @param mapper The mapper
     * @return this
     */
    public HttpClientBuilder setObjectMapper(ObjectMapper mapper) {
        Checks.notNull("mapper", mapper);
        this.mapper = mapper;
        return this;
    }

    /**
     * Set the marshaller used to convert inbound and outbound message bodies to
     * / from objects. If you wanted to, say, read a response that is CSV as
     * some kind of object, you would write a marshaller that can read and write
     * that to a ByteBuf and add it here.
     *
     * @param marshaller The marshaller
     * @return this
     */
    public <T> HttpClientBuilder addMarshaller(Class<T> forType, Marshaller<T, ByteBuf> marshaller) {
        Checks.notNull("marshaller", marshaller);
        marshallers.add(new MarshallerEntry<>(forType, marshaller));
        return this;
    }

    /**
     * Set the SSL context to use when accessing HTTPS addresses. In particular,
     * use this if you want to abort untrusted connections.
     *
     * @param ctx The context
     * @return this
     */
    public HttpClientBuilder setSslContext(SslContext ctx) {
        this.sslContext = ctx;
        return this;
    }

    /**
     * Set the timeout for requests. Note that this timeout is independent of
     * the timeout that can be set individually on requests, but whichever
     * timeout is shorter will take precedence. The default is no timeout.
     *
     * @param timeout The timeout, or null for no timeout (the default)
     * @return This
     */
    public HttpClientBuilder setTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * HTTP requests will transparently load a redirects. Note that this means
     * that handlers for events such as Connected may be called more than once -
     * once for each request. Following redirects is the default behavior.
     *
     * @return
     */
    public HttpClientBuilder followRedirects() {
        followRedirects = true;
        return this;
    }

    /**
     * Http requests will where appropriate set the Expect: 100-CONTINUE header
     *
     * @return this
     */
    public HttpClientBuilder send100Continue() {
        send100continue = true;
        return this;
    }

    /**
     * Turn off the default behavior of setting the Expect: 100-CONTINUE header
     * when
     *
     * @return
     */
    public HttpClientBuilder dontSend100Continue() {
        send100continue = false;
        return this;
    }

    /**
     * Turn off following of redirects
     *
     * @return this
     */
    public HttpClientBuilder dontFollowRedirects() {
        followRedirects = false;
        return this;
    }

    /**
     * The number of worker threads for processing requests and responses. Netty
     * is asynchronous, so you do not need as many threads as you will have
     * simultaneous requests; the default is 4. Best to see if you have
     * problems, and increase this value only if it makes a measurable
     * improvement in throughput.
     *
     * @param count The number of threads
     * @return this
     */
    public HttpClientBuilder threadCount(int count) {
        Checks.nonNegative("threadCount", count);
        Checks.nonZero("threadCount", count);
        if (group != null) {
            throw new IllegalStateException("Cannot set threadCount if you are"
                    + " providing the NioEventLoopGroup");
        }
        this.threadCount = count;
        return this;
    }

    /**
     * The maximum size of a chunk in bytes. The default is 64K.
     *
     * @param bytes A number of bytes
     * @return this
     */
    public HttpClientBuilder maxChunkSize(int bytes) {
        Checks.nonNegative("bytes", bytes);
        Checks.nonZero("bytes", bytes);
        this.maxChunkSize = bytes;
        return this;
    }

    /**
     * Set the maximum length of the HTTP initial line, e.g.
     * <code>HTTP/1.1 GET /path/to/something</code>. Unless you will be sending
     * extremely long URLs, the default of 2048 should be plenty.
     *
     * @param max
     * @return this
     */
    public HttpClientBuilder maxInitialLineLength(int max) {
        Checks.nonZero("max", max);
        Checks.nonNegative("max", max);
        maxInitialLineLength = max;
        return this;
    }

    /**
     * Set the maximum size of headers in bytes
     *
     * @return this
     */
    public HttpClientBuilder maxHeadersSize(int max) {
        Checks.nonZero("max", max);
        Checks.nonNegative("max", max);
        maxHeadersSize = max;
        return this;
    }

    /**
     * Turn on HTTP gzip or deflate compression
     *
     * @return this
     */
    public HttpClientBuilder useCompression() {
        compression = true;
        return this;
    }

    /**
     * Turn off HTTP gzip or deflate compression
     *
     * @return this
     */
    public HttpClientBuilder noCompression() {
        compression = false;
        return this;
    }

    /**
     * For test environments using multiple host names - attaches a DNS resolver
     * that will resolve all host name addresses to
     * <code>InetAddress.getLocalHost()</code>.
     *
     * @return this
     */
    public HttpClientBuilder resolveAllHostsToLocalhost() {
        return resolver(new LocalhostOnlyAddressResolverGroup());
    }

    /**
     * Set the DNS resolver to use, bypassing the default one.the passed
     * resolver will be used to resolve <i>all</i> host names by the resulting
     * HttpClient.
     *
     * @param resolver The addresss setEventLoopGroup resolver
     * @return this
     */
    public HttpClientBuilder resolver(AddressResolverGroup<? extends SocketAddress> resolver) {
        this.resolver = resolver;
        return this;
    }

    /**
     * Set the DNS resolver to use, bypassing the default one.the passed
     * resolver will be used to resolve <i>all</i> host names by the resulting
     * HttpClient.
     *
     * @param <T> The type of address the resolver resolves
     * @param resolver The addresss setEventLoopGroup resolver
     * @return this
     */
    public <T extends SocketAddress> HttpClientBuilder resolver(AddressResolver<T> resolver) {
        return resolver(new OneResolverGroup<>(resolver));
    }

    /**
     * Set the maximum number of redirects this client can encounter before it
     * considers itself to be in a redirect loop and cancels the request,
     * sending a cancelled event.
     *
     * @param maxRedirects The maximum number of redirects
     * @return this
     */
    public HttpClientBuilder setMaxRedirects(int maxRedirects) {
        Checks.nonNegative("maxRedirects", maxRedirects);
        this.maxRedirects = maxRedirects;
        return this;
    }

    private static final class OneResolverGroup<T extends SocketAddress> extends AddressResolverGroup<T> {

        private final AddressResolver<T> singleResolver;

        OneResolverGroup(AddressResolver<T> singleResolver) {
            this.singleResolver = singleResolver;
        }

        @Override
        protected AddressResolver<T> newResolver(EventExecutor ee) throws Exception {
            return singleResolver;
        }
    }

    /**
     * Set the thread pool used to perform network I/O.
     *
     * @param group The thread pool to use
     * @return this
     */
    public HttpClientBuilder setEventLoopGroup(NioEventLoopGroup group) {
        Checks.notNull("group", group);
        if (threadCount != -1) {
            throw new IllegalStateException("Thread count already set. If you want to provide "
                    + "your own NioEventLoopGroup, don't also set that - these options are "
                    + "mutually exclusive.");
        }
        this.group = group;
        return this;
    }

    /**
     * Build an HTTP client
     *
     * @return an http client
     */
    public HttpClient build() {
        NettyContentMarshallers marshallers = NettyContentMarshallers.getDefault(mapper);
        for (MarshallerEntry<?> m : this.marshallers) {
            m.apply(marshallers);
        }
        return new HttpClient(compression, maxChunkSize, threadCount == -1 ? DEFAULT_THREAD_COUNT : threadCount,
                maxInitialLineLength, maxHeadersSize, followRedirects,
                userAgent, interceptors, Collections.unmodifiableList(new ArrayList<>(settings)), send100continue,
                cookies, timeout, sslContext, resolver, group, maxRedirects,  marshallers, mapper);
    }

    /**
     * Set the user agent
     *
     * @param userAgent
     * @return this
     */
    public HttpClientBuilder setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Add an interceptor which should get a chance to process every request
     * before it is invoked; useful for things that sign requests and such.
     *
     * @param interceptor An interceptor
     * @return this
     */
    public HttpClientBuilder addRequestInterceptor(RequestInterceptor interceptor) {
        this.interceptors.add(interceptor);
        return this;
    }

    private final List<ChannelOptionSetting<?>> settings = new LinkedList<>();

    /**
     * Set a low-level setting for the Netty pipeline. See the
     * <a href="http://netty.io/4.0/api/io/netty/channel/ChannelOption.html">Netty
     * documentation</a>
     * for what these are.
     *
     * @param <T> The type
     * @param option The applyOption
     * @param value The value type
     * @return this
     */
    public <T> HttpClientBuilder setChannelOption(ChannelOption<T> option, T value) {
        for (Iterator<ChannelOptionSetting<?>> it = settings.iterator(); it.hasNext();) {
            ChannelOptionSetting setting = it.next();
            if (setting.equals(option)) {
                it.remove();
            }
        }
        settings.add(new ChannelOptionSetting<>(option, value));
        return this;
    }

    /**
     * Set a cookie store which will be used for all HTTP requests on the
     * resulting HttpClient (unless overriddeen in RequestBuilder).
     *
     * @param store A cookie store
     * @return this
     */
    public HttpClientBuilder setCookieStore(CookieStore store) {
        this.cookies = store;
        return this;
    }

    /**
     * Encapsulates a setting that can be set on the Netty Bootstrap; not really
     * an API class, but exposed so that the HttpClient constructor can be
     * invoked directly if someone wants to (using
     * <a href="HttpClientBuilder.html">HttpClientBuilder</a> is much easier).
     *
     * @param <T> A type
     */
    protected static final class ChannelOptionSetting<T> {

        private final ChannelOption<T> option;
        private final T value;

        ChannelOptionSetting(ChannelOption<T> option, T value) {
            this.option = option;
            this.value = value;
        }

        public ChannelOption<T> option() {
            return option;
        }

        public T value() {
            return value;
        }

        void apply(Bootstrap bootstrap) {
            bootstrap.option(option, value);
        }
    }
    
    private static final class MarshallerEntry<T> {
        private final Class<T> type;
        private final Marshaller<T,ByteBuf> marshaller;

        MarshallerEntry(Class<T> type, Marshaller<T, ByteBuf> marshaller) {
            this.type = type;
            this.marshaller = marshaller;
        }
        
        <R extends ContentMarshallers<ByteBuf, R>> void apply(ContentMarshallers<ByteBuf, R> marshallers) {
            marshallers.add(type, marshaller);
        }
        
        @Override
        public boolean equals(Object o) {
            return o == null ? false : o == this ? true :
                    o instanceof MarshallerEntry<?> && ((MarshallerEntry<?>) o).type == type;
        }
        
        @Override
        public int hashCode() {
            return type.hashCode();
        }
    }
}
