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

import com.mastfrog.util.Checks;
import io.netty.channel.ChannelOption;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import org.joda.time.Duration;

/**
 * Builds an HTTP client
 *
 * @author Tim Boudreau
 */
public final class HttpClientBuilder {

    private int threadCount = 4;
    private int maxChunkSize = 65536;
    private boolean compression = false;
    private int maxInitialLineLength = 2048;
    private int maxHeadersSize = 16384;
    private boolean followRedirects = true;
    private String userAgent;
    private final List<RequestInterceptor> interceptors = new LinkedList<>();
    private boolean send100continue = true;
    private CookieStore cookies;
    private Duration timeout;
    private final List<TrustManager> managers = new LinkedList<>();
    private SSLContext sslContext;

    public HttpClientBuilder setSslContext(SSLContext ctx) {
        this.sslContext = ctx;
        return this;
    }

    public HttpClientBuilder addTrustManager(TrustManager mgr) {
        this.managers.add(mgr);
        return this;
    }

    /**
     * Set the timeout for requests.  Note that this timeout
     * is independent of the timeout that can be set individually on
     * requests, but whichever timeout is shorter will take precedence.
     * The default is no timeout.
     * @param timeout The timeout
     * @return This
     */
    public HttpClientBuilder setTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * HTTP requests will transparently load a redirects. Note that this means
     * that handlers for events such as Connected may be called more than once -
     * once for each request.  Following redirects is the default behavior.
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
     * @return
     */
    public HttpClientBuilder dontSend100Continue() {
        send100continue = false;
        return this;
    }

    /**
     * Turn off following of redirects
     * @return this
     */
    public HttpClientBuilder dontFollowRedirects() {
        followRedirects = false;
        return this;
    }

    /**
     * The number of worker threads for processing requests and responses.
     * Netty is asynchronous, so you do not need as many threads as you will
     * have simultaneous requests;  the default is 4.  Best to see if you
     * have problems, and increase this value only if it makes a measurable
     * improvement in throughput.
     *
     * @param count The number of threads
     * @return this
     */
    public HttpClientBuilder threadCount(int count) {
        Checks.nonNegative("threadCount", count);
        this.threadCount = count;
        return this;
    }

    /**
     * The maximum size of a chunk in bytes.  The default is 64K.
     * @param bytes A number of bytes
     * @return this
     */
    public HttpClientBuilder maxChunkSize(int bytes) {
        Checks.nonNegative("bytes", bytes);
        this.maxChunkSize = bytes;
        return this;
    }

    /**
     * Set the maximum length of the HTTP initial line, e.g.
     * <code>HTTP/1.1 GET /path/to/something</code>. Unless you will be
     * sending extremely long URLs, the default of 2048 should be plenty.
     * @param max
     * @return
     */
    public HttpClientBuilder maxInitialLineLength(int max) {
        maxInitialLineLength = max;
        return this;
    }
    /**
     * Set the maximum size of headers in bytes
     * @return this
     */
    public HttpClientBuilder maxHeadersSize(int max) {
        maxHeadersSize = max;
        return this;
    }

    /**
     * Turn on HTTP gzip or deflate compression
     * @return this
     */
    public HttpClientBuilder useCompression() {
        compression = true;
        return this;
    }

    /**
     * Turn off HTTP gzip or deflate compression
     * @return this
     */
    public HttpClientBuilder noCompression() {
        compression = false;
        return this;
    }

    /**
     * Build an HTTP client
     * @return an http client
     */
    public HttpClient build() {
        return new HttpClient(compression, maxChunkSize, threadCount,
                maxInitialLineLength, maxHeadersSize, followRedirects,
                userAgent, interceptors, settings, send100continue,
                cookies, timeout, sslContext, managers.toArray(new TrustManager[0]));
    }

    /**
     * Set the user agent
     * @param userAgent
     * @return
     */
    public HttpClientBuilder setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Add an interceptor which should get a chance to process every request
     * before it is invoked;  useful for things that sign requests and such.
     * @param interceptor An interceptor
     * @return this
     */
    public HttpClientBuilder addRequestInterceptor(RequestInterceptor interceptor) {
        this.interceptors.add(interceptor);
        return this;
    }

    private final List<ChannelOptionSetting> settings = new LinkedList<>();

    /**
     * Set a low-level setting for the Netty pipeline.  See the
     * <a href="http://netty.io/4.0/api/io/netty/channel/ChannelOption.html">Netty documentation</a>
     * for what these are.
     *
     * @param <T> The type
     * @param option The option
     * @param value The value type
     * @return this
     */
    public <T> HttpClientBuilder setChannelOption(ChannelOption<T> option, T value) {
        for (Iterator<ChannelOptionSetting> it = settings.iterator(); it.hasNext();) {
            ChannelOptionSetting setting = it.next();
            if (setting.equals(option)) {
                it.remove();
            }
        }
        settings.add(new ChannelOptionSetting(option, value));
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
     * Encapsulates a setting that can be set on the Netty Bootstrap;  not
     * really an API class, but exposed so that the HttpClient constructor
     * can be invoked directly if someone wants to (using
     * <a href="HttpClientBuilder.html">HttpClientBuilder</a> is much easier).
     *
     * @param <T> A type
     */
    protected static class ChannelOptionSetting<T> {

        private final ChannelOption<T> option;
        private final T value;

        public ChannelOptionSetting(ChannelOption option, T value) {
            this.option = option;
            this.value = value;
        }

        public ChannelOption<T> option() {
            return option;
        }

        public T value() {
            return value;
        }
    }
}
