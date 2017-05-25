/*
 * The MIT License
 *
 * Copyright 2013-2015 Tim Boudreau.
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
import com.google.common.collect.Sets;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.netty.http.client.HttpClientBuilder.ChannelOptionSetting;
import com.mastfrog.url.HostAndPort;
import com.mastfrog.url.URL;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.thread.Receiver;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ChannelFactory;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AttributeKey;
import io.netty.util.IllegalReferenceCountException;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import java.util.concurrent.atomic.AtomicReference;
import org.joda.time.Duration;

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
 * Build a request, then call <code>send()</code> on the builder to trigger
 * sending the request. When building the request, you can add handlers for
 * different event types. So, for example, if you're only interested in the
 * content, you can receive that as a string, a byte array or decoded JSON
 * simply based on the type of callback you provide.
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
 * three <code>receive</code> methods you can override which give you varying
 * levels of detail - the signature of the <code>receive()</code> method could
 * also be:
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
 * </pre> If you just want a Netty <code>HttpResponse</code> or
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
    private final Iterable<ChannelOptionSetting<?>> settings;
    private final boolean send100continue;
    private final CookieStore cookies;
    private final Duration timeout;
    private final Timer timer = new Timer("HttpClient timeout for HttpClient@" + System.identityHashCode(this));
    private final SslBootstrapCache sslBootstraps;
    private final int connectionPoolSize;
    private final MultiHostChannelPool connectionPools;
    private final MessageHandlerImpl handler;
    private final AddressResolverGroup<?> resolver;
    private final NioChannelFactory channelFactory = new NioChannelFactory();

    public HttpClient() {
        this(false, 128 * 1024, 12, 8192, 16383, true, null,
                Collections.<RequestInterceptor>emptyList(),
                Collections.<ChannelOptionSetting<?>>emptyList(),
                true, null, null, null, null, null, -1);
    }

    /**
     * Create a new HTTP client; prefer HttpClient.builder() where possible, as
     * that is much simpler. HttpClientBuilder will remain backward compatible;
     * this constructor may be changed if new parameters are needed (all state
     * of an HTTP client is immutable and must be passed to the constructor).
     *
     * @param compress Enable http compression
     * @param maxChunkSize Max buffer size for chunked encoding
     * @param threads Number of threads to dedicate to network I/O
     * @param maxInitialLineLength Maximum length the initial line (method +
     * url) will have
     * @param maxHeadersSize Maximum buffer size for HTTP headers
     * @param followRedirects If true, client will transparently follow
     * redirects
     * @param userAgent The user agent string - may be null
     * @param interceptors A list of interceptors which can decorate all http
     * requests created by this client. May be null.
     * @param settings Netty channel options for
     * @param send100continue If true, requests with payloads will have the
     * <code>Expect: 100-CONTINUE</code> header set
     * @param cookies A place to store http cookies, which will be re-sent where
     * appropriate; may be null.
     * @param timeout Maximum time a connection will be open; may be null to
     * keep open indefinitely.
      * @param connectionPoolSize - 0 for unlimited size, -1 for don't use a
     * connection pool, otherwise a number of connections <b>per host</b> that
     * should be maintained
    * @param sslContext Ssl context for secure connections. May be null.
     * @param resolver An alternate DNS resolver (may be null).
     * @param threadPool The thread pool to use - if null, a private one will be
     * created
     * @param maxRedirects The maximum number of redirects that can be
     * encountered before the client considers itself in a redirect loop and
     * cancels the request (sending a cancelled event) If -1, the default of 15
     * will be used.
     */
    public HttpClient(boolean compress, int maxChunkSize, int threads,
            int maxInitialLineLength, int maxHeadersSize, boolean followRedirects,
            String userAgent, List<RequestInterceptor> interceptors,
            Iterable<ChannelOptionSetting<?>> settings, boolean send100continue,
            CookieStore cookies, Duration timeout, SslContext sslContext, AddressResolverGroup<?> resolver,
            NioEventLoopGroup threadPool, int maxRedirects, int connectionPoolSize) {
        group = threadPool == null ? new NioEventLoopGroup(threads, new TF()) : threadPool;
        this.compress = compress;
        this.maxInitialLineLength = maxInitialLineLength;
        this.maxChunkSize = maxChunkSize;
        this.maxHeadersSize = maxHeadersSize;
        this.followRedirects = followRedirects;
        this.userAgent = userAgent;
        this.interceptors = interceptors == null ? Collections.<RequestInterceptor>emptyList()
                : new ImmutableList.Builder<RequestInterceptor>()
                .addAll(interceptors).build();
        this.settings = settings == null ? Collections.<ChannelOptionSetting<?>>emptySet() : settings;
        this.send100continue = send100continue;
        this.cookies = cookies;
        this.timeout = timeout;
        this.handler = new MessageHandlerImpl(followRedirects, this, maxRedirects);
        sslBootstraps = new SslBootstrapCache(group, this.timeout, sslContext, this.handler,
                this.maxChunkSize, this.maxInitialLineLength, this.maxHeadersSize, this.compress,
                this.settings, resolver, channelFactory);
    }

    private static class TF implements ThreadFactory {

        private int threadsCreated = 0;

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "HttpClient event loop " + ++threadsCreated);
            t.setDaemon(true);
            return t;
        }
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
     * Build an HTTP HEAD request Spi
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

    @SuppressWarnings("unchecked")
    private synchronized Bootstrap start(HostAndPort hostAndPort) {
        if (bootstrap == null) {
            bootstrap = new Bootstrap();
            if (resolver != null) {
                bootstrap.resolver(resolver);
            }
            bootstrap.group(group);
            bootstrap.handler(new Initializer(hostAndPort,
                    handler, null, false, maxChunkSize, maxInitialLineLength, maxHeadersSize, compress)
            );
            bootstrap.option(ChannelOption.TCP_NODELAY, true);
            bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            bootstrap.option(ChannelOption.SO_REUSEADDR, false);
            if (timeout != null) {
                bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.getMillis());
            }
            for (ChannelOptionSetting<?> setting : settings) {
                setting.apply(bootstrap);
            }
            bootstrap.channelFactory(channelFactory);
        }
        return bootstrap;
    }

    @SuppressWarnings("unchecked")
    private synchronized Bootstrap startSsl(HostAndPort hostAndPort) {
        return sslBootstraps.sslBootstrap(hostAndPort);
    }

    /**
     * Shut down any running connections
     */
    @SuppressWarnings("deprecation")
    public void shutdown() {
        try {
        if (group != null) {
            group.shutdownGracefully(0, 10, TimeUnit.SECONDS);
            if (!group.isTerminated()) {
                group.shutdownNow();
            }
        }
        } finally {
        timer.cancel();
    }
    }

    void copyHeaders(HttpRequest from, HttpRequest to, HeaderValueType<?>... exclude) {
        copy:
        for (Map.Entry<String, String> e : from.headers().entries()) {
            String header = e.getKey();
            for (HeaderValueType<?> ex : exclude) {
                if (ex.name().equalsIgnoreCase(header)) {
                    continue copy;
                }
            }
            to.headers().add(header, e.getValue());
        }
    }

    void redirect(Method method, URL url, RequestInfo info) {
        HttpRequest nue;
        if (method.toString().equals(info.req.method().toString())) {
            if (info.req instanceof DefaultFullHttpRequest) {
                DefaultFullHttpRequest dfrq = (DefaultFullHttpRequest) info.req;
                FullHttpRequest rq;
                try {
                    rq = dfrq.copy();
                } catch (IllegalReferenceCountException e) { // Empty bytebuf
                    rq = dfrq;
                }
                rq.setUri(url.getPathAndQuery());
                nue = rq;
            } else {
                nue = new DefaultHttpRequest(info.req.protocolVersion(), info.req.method(), url.getPathAndQuery());
            }
        } else {
            nue = new DefaultHttpRequest(info.req.protocolVersion(), HttpMethod.valueOf(method.name()), url.getPathAndQuery());
        }
        copyHeaders(info.req, nue, Headers.HOST);
        nue.headers().set(Headers.HOST.name(), url.getHost().toString());
        submit(url, nue, info.cancelled, info.handle, info.r, info, info.remaining(), info.dontAggregate, info.chunkedBody);
    }

    private Bootstrap bootstrap;

    static final AttributeKey<RequestInfo> KEY = AttributeKey.<RequestInfo>valueOf("info");

    private final Set<ActivityMonitor> monitors = Sets.newConcurrentHashSet();

    public void addActivityMonitor(ActivityMonitor monitor) {
        monitors.add(monitor);
    }

    public void removeActivityMonitor(ActivityMonitor monitor) {
        monitors.remove(monitor);
    }

    private class AdapterCloseNotifier implements ChannelFutureListener {

        private final URL url;

        public AdapterCloseNotifier(URL url) {
            this.url = url;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            for (ActivityMonitor m : monitors) {
                m.onEndRequest(url);
            }
        }
    }

    static final class TimeoutTimerTask extends TimerTask implements ChannelFutureListener {

        private final AtomicBoolean cancelled;
        private final ResponseFuture handle;
        private final ResponseHandler<?> r;
        private final RequestInfo in;

        public TimeoutTimerTask(AtomicBoolean cancelled, ResponseFuture handle, ResponseHandler<?> r, RequestInfo in) {
            Checks.notNull("in", in);
            Checks.notNull("cancelled", cancelled);
            this.cancelled = cancelled;
            this.handle = handle;
            this.r = r;
            this.in = in;
        }

        @Override
        public void run() {
            if (!cancelled.get()) {
                if (r != null) {
                    r.onError(new NoStackTimeoutException(in.timeout.toString()));
                }
                if (handle != null) {
                    handle.onTimeout(in.age());
                }
            }
            super.cancel();
        }

        @Override
        public void operationComplete(ChannelFuture f) throws Exception {
            cancelled.set(true);
            super.cancel();
        }
    }

    private static class NoStackTimeoutException extends TimeoutException {
        // Minor optimization - creating the stack trace is expensive if
        // the stack is deep
        NoStackTimeoutException(String msg) {
            super(msg);
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return new StackTraceElement[0];
        }
        
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    private void submit(final URL url, HttpRequest rq, final AtomicBoolean cancelled, final ResponseFuture handle,
            final ResponseHandler<?> r, RequestInfo info, Duration timeout, boolean noAggregate, final ChunkedContent chunked) {
        if (info != null && info.isExpired()) {
            // In case of a redirect, we may be called with an info that has
            // already expired if the timeout set in the builder has elapsed
            handle.event(new State.Timeout(info.age()));
            cancelled.set(true);
        }
        // Ensure the cancelled event is sent
        if (cancelled.get()) {
            handle.event(new State.Cancelled());
            return;
        }
        // Assign a reference to the channel as soon as it is available,
        // so that we can close it in case of an exception
        final AtomicReference<Channel> theChannel = new AtomicReference<>();
        if (!url.isValid() && url.getProblems() != null) {
            url.getProblems().throwIfFatalPresent("Invalid URL");
        }
        try {
            // Allow any interceptors to amend or even replace the
            // request
            for (RequestInterceptor i : interceptors) {
                rq = i.intercept(rq);
            }
            final HttpRequest req = rq;
        try {
            Bootstrap bootstrap = url.getProtocol().isSecure() ? startSsl(url.getHostAndPort())
                    : start(url.getHostAndPort());
            TimeoutTimerTask timerTask = null;
            boolean newRequest = info == null;
            if (info == null) {
                info = new RequestInfo(url, req, cancelled, handle, r, timeout, timerTask, noAggregate, chunked);
                if (timeout != null) {
                    timerTask = new TimeoutTimerTask(cancelled, handle, r, info);
                    timer.schedule(timerTask, timeout.getMillis());
                }
                info.timer = timerTask;
            }
            if (info.isExpired()) {
                // Check expiration again
                handle.event(new State.Timeout(info.age()));
                return;
            }
            handle.event(new State.Connecting());
            //XXX who is escaping this?
            req.setUri(req.getUri().replaceAll("%5f", "_"));
            final TimeoutTimerTask ft = tt;
            final RequestInfo requestInfo = info;
            System.out.println("Acquire from pool " + hostKey);
            Future<Channel> poolAcquire = connectionPools.acquire(hostKey, new DefaultPromise<Channel>() {

                @Override
                public boolean trySuccess(Channel channel) {
                    System.out.println("trySuccess");
                    System.out.println("POOL SET CHANNEL TO " + channel.getClass().getName());
                    channel.attr(KEY).set(requestInfo);
                    return super.trySuccess(channel);
            }

                @Override
                public Promise<Channel> setSuccess(Channel channel) {
                    System.out.println("setSuccess");
                    channel.attr(KEY).set(requestInfo);
                    return super.setSuccess(channel);
                }

            });
            poolAcquire.addListener(new GenericFutureListener<Future< Channel>>() {

                @Override
                public void operationComplete(Future<Channel> future) throws Exception {
                    System.out.println("PoolAcquire complete " + future.isSuccess() + " FUTURE " + future.getClass().getName());
                    if (!future.isSuccess()) {
                        Throwable cause = future.cause();
                        if (cause == null) {
                            cause = new ConnectException(url.getHostAndPort().toString());
                        }
                        handle.event(new State.Error(cause));
                        if (r != null) {
                            r.onError(cause);
                        }
                        cancelled.set(true);
                        return;
                    }
                    Channel channel = future.getNow();
                    System.out.println("INITIAL CHANNEL " + channel.getClass().getName() + " from " + future);
                    if (ft != null) {
                        channel.closeFuture().addListener(ft);
                    }
                    channel.attr(KEY).set(requestInfo);
                    handle.setFuture(future);


            ChannelFuture fut = bootstrap.connect(url.getHost().toString(), url.getPort().intValue());
            theChannel.set(fut.channel());
            if (timerTask != null) {
                fut.channel().closeFuture().addListener(timerTask);
            }
            fut.channel().attr(KEY).set(info);
            handle.setFuture(fut);
            if (!monitors.isEmpty()) {
                for (ActivityMonitor m : monitors) {
                    m.onStartRequest(url);
                }
                fut.channel().closeFuture().addListener(new AdapterCloseNotifier(url));
            }
            if (newRequest && r != null) {
                handle.on(State.Error.class, new Receiver<Throwable>() {
                    @Override
                    public void receive(Throwable object) {
                        r.onError(object);
                    }
                });
                handle.on(StateType.Cancelled, new Receiver<Void>() {
                    @Override
                    public void receive(Void object) {
                        r.onError(new CancellationException("Cancelled"));
                    }
                });
            }

            fut.addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        Throwable cause = future.cause();
                        if (cause == null) {
                            cause = new ConnectException("Unknown problem connecting to " + url);
                        }
                        handle.event(new State.Error(cause));
//                        if (r != null) {
//                            r.onError(cause);
//                        }
                        cancelled.set(true);
                    }
                    if (cancelled.get()) {
                        future.cancel(true);
                        if (future.channel().isOpen()) {
                            future.channel().close();
                        }
                        for (ActivityMonitor m : monitors) {
                            m.onEndRequest(url);
                        }
                        return;
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
                }
            });
        } catch (Exception ex) {
            Exceptions.chuck(ex);
        }
    }

    static final class NioChannelFactory implements ChannelFactory<NioSocketChannel> {

        public NioSocketChannel newChannel() {
            try {
                return new NioSocketChannel(SocketChannel.open());
            } catch (IOException ioe) {
                return Exceptions.chuck(ioe);
            }
        }

        public Channel newChannel(EventLoop eventLoop) {
            return newChannel();
        }
    }

    private static final class StoreHandler extends Receiver<HttpResponse> {

        private final CookieStore store;

        public StoreHandler(CookieStore store) {
            this.store = store;
        }

        @Override
        public void receive(HttpResponse headerContainer) {
            store.extract(headerContainer.headers());
        }
    }
    
    private ByteBufAllocator alloc() {
        for (ChannelOptionSetting<?> setting : this.settings) {
            if (setting.option().equals(ChannelOption.ALLOCATOR)) {
                return (ByteBufAllocator) setting.value();
            }
        }
        return PooledByteBufAllocator.DEFAULT;
    }

    private final class RB extends RequestBuilder {

        RB(Method method) {
            super(method, alloc(), HttpClient.this.connectionPoolSize != 0);
            this.send100Continue = HttpClient.this.send100continue;
        }

        @Override
        public ResponseFuture execute(ResponseHandler<?> r) {
            URL u = getURL();
            HttpRequest req = build();
            if (userAgent != null) {
                req.headers().add(HttpHeaderNames.USER_AGENT, userAgent);
            }
            if (compress) {
                req.headers().add(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
            }
            AtomicBoolean cancelled = new AtomicBoolean();
            ResponseFuture handle = new ResponseFuture(cancelled);
            handle.handlers.addAll(super.handlers);
            handle.any.addAll(super.any);
            CookieStore theStore = super.store;
            if (theStore == null) {
                theStore = HttpClient.this.cookies;
            }
            if (theStore != null) {
                HandlerEntry<? extends HttpResponse> entry
                        = createHandler(State.HeadersReceived.class, new StoreHandler(theStore));
                handle.handlers.add(entry);
            }
            submit(u, req, cancelled, handle, r, null, this.timeout, noAggregate, chunkedContent());
            return handle;
        }

        private <T> HandlerEntry<T> createHandler(Class<? extends State<T>> event, Receiver<T> r) {
            HandlerEntry<T> result = new HandlerEntry<>(event);
            result.add(r);
            return result;
        }

        @Override
        public ResponseFuture execute() {
            return execute(null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpRequestBuilder on(StateType event, Receiver<T> r) {
            super.on((Class<? extends State<T>>) event.type(), (Receiver) event.wrapperReceiver(r));
            return this;
        }
    }
}
