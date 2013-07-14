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

import com.google.common.collect.ImmutableList;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.netty.http.client.HttpClientBuilder.ChannelOptionSetting;
import com.mastfrog.url.URL;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.thread.Receiver;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ChannelFactory;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A simple asynchronous HTTP client with an emphasis on ease of use and a
 * simple callback-based API to discourage blocking calls - use
 * <code>HttpClient.builder()</code> to configure and create an instance. Use
 * <code><a href="HttpRequestBuilder.html">HttpRequestBuilder</a></code>
 * instances obtained from the get/put/post/options methods to construct and
 * send requests.
 * <p/>
 * By default, handles http or https and allows for untrusted ssl certs.
 * <p/>
 * HTTP compression is supported.
 * <h3>Usage</h3>
 * Build a request, then call
 * <code>send()</code> on the builder to trigger sending the request. When
 * building the request, you can add handlers for different event types. So, for
 * example, if you're only interested in the content, you can receive that as a
 * string, a byte array or decoded JSON simply based on the type of callback you
 * provide.
 * <p/>
 * <
 * pre>
 * HttpClient client = HttpClient.builder().useCompression().build();
 * ResponseHandler&lt;String&gt; receiver = new
 * ResponseHandler&lt;&gt;(String.class) { public void receive(String body) { //
 * do something } } HttpRequestBuilder bldr =
 * client.get().setURL(URL.parse("http://mail-vm.timboudreau.org/blog/api-list"));
 * ResponseFuture h = bldr.execute(receiver);
 * </pre> When the request is completed, the callback will be invoked. There are
 * three
 * <code>receive</code> methods you can override which give you varying levels
 * of detail - the signature of the
 * <code>receive()</code> method could also be:
 * <pre>
 *      public void receive(HttpResponseStatus status, String body) {
 * </pre> or it could be
 * <pre>
 *      public void receive(HttpResponseStatus status, HttpHeaders h, String body) {
 * </pre>
 * <p/>
 * Now say we want to set some headers - for example, to get a NOT MODIFIED
 * response if the server sees the data is older than the If-Modified-Since
 * header (in this case, three minutes ago - this uses Joda Time's DateTime and
 * Duration classes):
 * <pre>
 *      bldr.add(Headers.IF_MODIFIED_SINCE, DateTime.now().minus(Duration.standardMinutes(3)));
 * </pre>
 * <h3>States / Events</h3>
 * There are a number of states possible - see the <a
 * href="StateType.html"><code>StateType</code></a>
 * enum for all of them. Some can be called multiple times - for example, you
 * can examine every chunk of a chunked response by adding a listener for
 * State.ContentReceived:
 * <p/>
 * <
 * pre>
 * bldr.on(State.ContentReceived.class, new Receiver&lt;ByteBuf&gt;(){ public
 * void receive(ByteBuf buf) { ... } })
 * </pre> If you just want a Netty
 * <code>HttpResponse</code> or
 * <code>FullHttpResponse</code> just ask for a
 * <code>State.HeadersReceived</code> or a
 * <code>State.FullContentReceived</code> instead.
 * <p/>
 * To catch the details, use the
 * <code><a href="StateType.html">StateType</a></code> enum instead - a number
 * of events don't have public classes because there is no data to pass with
 * them. So, to detect, say, when the channel for a request is closed, use
 * <code>StateType.Closed</code>
 *
 * @author Tim Boudreau
 */
public final class HttpClient {

    private final NioEventLoopGroup group;
    final boolean compress;
    private final int maxInitialLineLength;
    private final int maxChunkSize;
    private final int maxHeadersSize;
    private final boolean followRedirects;
    private final String userAgent;
    private final List<RequestInterceptor> interceptors;
    private final Iterable<ChannelOptionSetting> settings;

    public HttpClient() {
        this(false, 128 * 1024, 12, 8192, 16383, true, null, Collections.<RequestInterceptor>emptyList(), Collections.<ChannelOptionSetting>emptyList());
    }

    public HttpClient(boolean compress, int maxChunkSize, int threads,
            int maxInitialLineLength, int maxHeadersSize, boolean followRedirects,
            String userAgent, List<RequestInterceptor> interceptors,
            Iterable<ChannelOptionSetting> settings) {
        group = new NioEventLoopGroup(threads);
        this.compress = compress;
        this.maxInitialLineLength = maxInitialLineLength;
        this.maxChunkSize = maxChunkSize;
        this.maxHeadersSize = maxHeadersSize;
        this.followRedirects = followRedirects;
        this.userAgent = userAgent;
        this.interceptors = new ImmutableList.Builder<RequestInterceptor>()
                .addAll(interceptors).build();
        this.settings = settings;
    }

    /**
     * Create a builder to configure connecting
     *
     * @return A builder
     */
    public static HttpClientBuilder builder() {
        return new HttpClientBuilder();
    }

    public HttpRequestBuilder request(Method method) {
        Checks.notNull("method", method);
        return new RB(method);
    }

    /**
     * Build an HTTP GET request
     *
     * @return a request builder
     */
    public HttpRequestBuilder get() {
        return new RB(Method.GET);
    }

    /**
     * Build an HTTP HEAD request
     *
     * @return a request builder
     */
    public HttpRequestBuilder head() {
        return new RB(Method.HEAD);
    }

    /**
     * Build an HTTP PUT request
     *
     * @return a request builder
     */
    public HttpRequestBuilder put() {
        return new RB(Method.PUT);
    }

    /**
     * Build an HTTP POST request
     *
     * @return a request builder
     */
    public HttpRequestBuilder post() {
        return new RB(Method.POST);
    }

    /**
     * Build an HTTP DELETE request
     *
     * @return a request builder
     */
    public HttpRequestBuilder delete() {
        return new RB(Method.DELETE);
    }

    /**
     * Build an HTTP OPTIONS request
     *
     * @return a request builder
     */
    public HttpRequestBuilder options() {
        return new RB(Method.OPTIONS);
    }

    private <T> void option(Bootstrap bootstrap, ChannelOptionSetting<T> setting) {
        bootstrap.option(setting.option(), setting.value());
    }

    private synchronized Bootstrap start() {
        if (bootstrap == null) {
            bootstrap = new Bootstrap();
            bootstrap.group(group);
            bootstrap.handler(new Initializer(new MessageHandlerImpl(followRedirects, this), false, maxChunkSize, maxInitialLineLength, maxHeadersSize, compress));
            bootstrap.option(ChannelOption.TCP_NODELAY, true);
            bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            for (ChannelOptionSetting setting : settings) {
                option(bootstrap, setting);
            }
            bootstrap.channelFactory(new NioChannelFactory());
        }
        return bootstrap;
    }

    private synchronized Bootstrap startSsl() {
        if (bootstrapSsl == null) {
            bootstrapSsl = new Bootstrap();
            bootstrapSsl.group(group);
            bootstrapSsl.handler(new Initializer(new MessageHandlerImpl(followRedirects, this), true, maxChunkSize, maxInitialLineLength, maxHeadersSize, compress));
            bootstrapSsl.option(ChannelOption.TCP_NODELAY, true);
            bootstrapSsl.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            for (ChannelOptionSetting setting : settings) {
                option(bootstrap, setting);
            }
            bootstrapSsl.channelFactory(new NioChannelFactory());
        }
        return bootstrapSsl;
    }

    /**
     * Shut down any running connections
     */
    public void shutdown() {
        if (group != null) {
            group.shutdownGracefully(0, 10, TimeUnit.SECONDS);
        }
    }

    void copyHeaders(HttpRequest from, HttpRequest to) {
        for (Map.Entry<String, String> e : from.headers().entries()) {
            to.headers().add(e.getKey(), e.getValue());
        }
    }

    void redirect(Method method, URL url, RequestInfo info) {
        HttpRequest nue;
        if (method.toString().equals(info.req.getMethod().toString())) {
            if (info.req instanceof DefaultFullHttpRequest) {
                FullHttpRequest rq = ((DefaultFullHttpRequest) info.req).copy();
                rq.setUri(url.getPathAndQuery());
                nue = rq;
            } else {
                nue = new DefaultHttpRequest(info.req.getProtocolVersion(), info.req.getMethod(), url.getPathAndQuery());
            }
        } else {
            nue = new DefaultHttpRequest(info.req.getProtocolVersion(), HttpMethod.valueOf(method.name()), url.getPathAndQuery());
        }
        copyHeaders(info.req, nue);
        submit(url, nue, new AtomicBoolean(), info.handle, info.r, info);
    }

    private Bootstrap bootstrap;
    private Bootstrap bootstrapSsl;

    static final AttributeKey<RequestInfo> KEY = new AttributeKey<>("info");

    private void submit(URL url, HttpRequest rq, final AtomicBoolean cancelled, final ResponseFuture handle, ResponseHandler<?> r, RequestInfo info) {
        if (cancelled.get()) {
            handle.event(new State.Cancelled());
            return;
        }
        try {
            for (RequestInterceptor i : interceptors) {
                rq = i.intercept(rq);
            }
            final HttpRequest req = rq;
            Bootstrap bootstrap;
            if (url.getProtocol().isSecure()) {
                bootstrap = startSsl();
            } else {
                bootstrap = start();
            }
            if (!url.isValid()) {
                throw new IllegalArgumentException(url.getProblems() + "");
            }
            if (info == null) {
                info = new RequestInfo(url, req, cancelled, handle, r);
            }
            handle.event(new State.Connecting());
            //XXX who is escaping this?
            req.setUri(req.getUri().replaceAll("%5f", "_"));
            ChannelFuture fut = bootstrap.connect(url.getHost().toString(), url.getPort().intValue());
            handle.setFuture(fut);
            fut.channel().attr(KEY).set(info);
            fut.addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (cancelled.get()) {
                        future.cancel(true);
                        future.channel().close();
                    }
                    handle.event(new State.Connected(future.channel()));
                    handle.event(new State.SendRequest(req));
                    future = future.channel().writeAndFlush(req);
                    future.addListener(new ChannelFutureListener() {

                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (cancelled.get()) {
                                future.cancel(true);
                                future.channel().close();
                            }
                            handle.event(new State.AwaitingResponse());
                        }

                    });
                }

            });
        } catch (Exception ex) {
            Exceptions.chuck(ex);
        }
    }

    private static final class NioChannelFactory implements ChannelFactory {

        @Override
        public Channel newChannel() {
            return new NioSocketChannel();
        }
    }

    private final class RB extends RequestBuilder {

        RB(Method method) {
            super(method);
        }

        @Override
        public ResponseFuture execute(ResponseHandler<?> r) {
            URL u = getURL();
            HttpRequest req = build();
            if (userAgent != null) {
                req.headers().add(HttpHeaders.Names.USER_AGENT, userAgent);
            }
            if (compress) {
                req.headers().add(HttpHeaders.Names.ACCEPT_ENCODING, "gzip");
            }
            AtomicBoolean cancelled = new AtomicBoolean();
            ResponseFuture handle = new ResponseFuture(cancelled);
            handle.handlers.addAll(super.handlers);
            handle.any.addAll(super.any);

            submit(u, req, cancelled, handle, r, null);
            return handle;
        }

        @Override
        public ResponseFuture execute() {
            return execute(null);
        }

        @Override
        public <T> HttpRequestBuilder on(StateType event, Receiver<T> r) {
            super.on((Class<? extends State<T>>) event.type(), (Receiver) event.wrapperReceiver(r));
            return this;
        }
    }
}
