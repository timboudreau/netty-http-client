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

import com.google.common.net.MediaType;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.marshallers.netty.NettyContentMarshallers;
import com.mastfrog.url.Protocol;
import com.mastfrog.url.URL;
import com.mastfrog.url.URLBuilder;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Either;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Strings;
import com.mastfrog.util.thread.Receiver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
abstract class RequestBuilder implements HttpRequestBuilder {

    private URLBuilder url = URL.builder();
    final List<Entry<?>> entries = new LinkedList<>();
    private final Method method;
    private HttpVersion version = HttpVersion.HTTP_1_1;
    protected CookieStore store;
    Duration timeout;
    private final ByteBufAllocator alloc;
    protected boolean noAggregate;
    static final WebSocketVersion DEFAULT_WEBSOCKET_VERSION = WebSocketVersion.values()[WebSocketVersion.values().length-1];
    protected WebSocketVersion websocketVersion = DEFAULT_WEBSOCKET_VERSION;

    RequestBuilder(Method method, ByteBufAllocator alloc) {
        this.method = method;
        this.alloc = alloc;
    }

    abstract NettyContentMarshallers marshallers();

    @Override
    public RequestBuilder setTimeout(Duration timeout) {
        if (timeout != null && timeout.toMillis() <= 0) {
            throw new IllegalArgumentException("Cannot set timeout to <= 0");
        }
        this.timeout = timeout;
        return this;
    }

    @Override
    public RequestBuilder setWebSocketVersion(WebSocketVersion version) {
        if (version == WebSocketVersion.UNKNOWN) {
            Set<WebSocketVersion> versions = EnumSet.allOf(WebSocketVersion.class);
            versions.remove(version);
            throw new IllegalArgumentException(version + " not allowed - must "
                    + "be one of " + Strings.join(',', versions));
        }
        this.websocketVersion = version;
        return this;
    }

    @Override
    public RequestBuilder setURL(URL url) {
        this.url = URL.builder(url);
        return this;
    }

    @Override
    public RequestBuilder addPathElement(String element) {
        url.addPathElement(element);
        return this;
    }

    @Override
    public RequestBuilder addQueryPair(String key, String value) {
        url.addQueryPair(key, value);
        return this;
    }

    @Override
    public RequestBuilder setProtocol(Protocol protocol) {
        url.setProtocol(protocol);
        return this;
    }

    @Override
    public RequestBuilder setAnchor(String anchor) {
        url.setAnchor(anchor);
        return this;
    }

    @Override
    public RequestBuilder setPassword(String password) {
        url.setPassword(password);
        return this;
    }

    @Override
    public RequestBuilder setPath(String path) {
        url.setPath(path);
        return this;
    }

    @Override
    public RequestBuilder setPort(int port) {
        url.setPort(port);
        return this;
    }

    @Override
    public RequestBuilder setUserName(String userName) {
        url.setUserName(userName);
        return this;
    }

    @Override
    public RequestBuilder setHost(String host) {
        url.setHost(host);
        return this;
    }

    @Override
    public RequestBuilder basicAuthentication(String username, String password) {
        addHeader(Headers.AUTHORIZATION, new BasicCredentials(username, password));
        return this;
    }

    @Override
    public <T> RequestBuilder addHeader(HeaderValueType<T> type, T value) {
        entries.add(new Entry<>(type, value));
        return this;
    }

    URL getURL() {
        return url.create();
    }

    private boolean noDateHeader;

    @Override
    public RequestBuilder noDateHeader() {
        noDateHeader = true;
        return this;
    }

    private boolean noConnectionHeader;

    @Override
    public RequestBuilder noConnectionHeader() {
        noConnectionHeader = true;
        return this;
    }

    private boolean noHostHeader;

    @Override
    public RequestBuilder noHostHeader() {
        noHostHeader = true;
        return this;
    }

    @Override
    public RequestBuilder setCookieStore(CookieStore store) {
        this.store = store;
        return this;
    }

    ChunkedContent chunkedContent() {
        return body.getB();
    }

    public HttpRequest build() {
        if (url == null) {
            throw new IllegalStateException("URL not set");
        }
        URL u = getURL();
        if (!u.isValid()) {
            if (u.getProblems() != null) {
                u.getProblems().throwIfFatalPresent();
            } else {
                throw new IllegalArgumentException("Invalid url " + u);
            }
        }
        if (u.getHost() == null) {
            throw new IllegalStateException("URL host not set: " + u);
        }
        String uri = u.getPathAndQuery();
        if (uri.isEmpty()) {
            uri = "/";
        }
        HttpMethod mth = HttpMethod.valueOf(method.name());
        DefaultHttpRequest h = !body.isA()
                ? new DefaultHttpRequest(version, mth, uri)
                : new DefaultFullHttpRequest(version, mth, uri, body.getA());
        for (Entry<?> e : entries) {
            e.addTo(h.headers());
        }
        if (!noHostHeader) {
            h.headers().add(HttpHeaderNames.HOST, u.getHost().toString());
        }
        if (!h.headers().contains(HttpHeaderNames.CONNECTION) && !noConnectionHeader) {
            h.headers().add(HttpHeaderNames.CONNECTION, "close");
        }
        if (!noDateHeader) {
            h.headers().add(HttpHeaderNames.DATE, Headers.DATE.toCharSequence(ZonedDateTime.now()));
        }
        if (store != null) {
            store.decorate(h);
        }
        return h;
    }

    @Override
    public URL toURL() {
        return url.create();
    }

    private final Either<ByteBuf, ChunkedContent> body = new Either<>(ByteBuf.class, ChunkedContent.class);
    boolean send100Continue = true;

    @Override
    public HttpRequestBuilder setBody(Object bodyObject, MediaType contentType) throws IOException {
        Checks.notNull("body", bodyObject);
        ByteBuf buf;
        if (bodyObject instanceof ChunkedContent) {
            body.setB((ChunkedContent) bodyObject);
            if (send100Continue) {
                addHeader(Headers.EXPECT, HttpHeaderValues.CONTINUE);
            }
            addHeader(Headers.CONTENT_TYPE, contentType);
            addHeader(Headers.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            return this;
        } else if (bodyObject instanceof ByteBuf) {
            buf = (ByteBuf) bodyObject;
        } else {
            buf = newByteBuf();
            try {
                marshallers().write(bodyObject, buf, contentType);
            } catch (Exception ex) {
                Exceptions.chuck(ex);
            }
        }
        body.set(buf);
        if (send100Continue) {
            addHeader(Headers.EXPECT, HttpHeaderValues.CONTINUE);
        }
        addHeader(Headers.CONTENT_LENGTH, (long) buf.readableBytes());
        addHeader(Headers.CONTENT_TYPE, contentType);
        return this;
    }

    protected final List<Receiver<State<?>>> any = new LinkedList<>();

    protected ByteBuf newByteBuf() {
        return alloc.buffer();
    }

    @Override
    public HttpRequestBuilder onEvent(Receiver<State<?>> r) {
        any.add(r);
        return this;
    }

    protected final List<HandlerEntry<?>> handlers = new LinkedList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpRequestBuilder on(Class<? extends State<T>> event, Receiver<T> r) {
        HandlerEntry<T> h = null;
        for (HandlerEntry<?> e : handlers) {
            if (e.state.equals(event)) {
                h = (HandlerEntry<T>) e;
                break;
            }
        }
        if (h == null) {
            h = new HandlerEntry<>(event);
            handlers.add(h);
        }
        h.add(r);
        return this;
    }

    @Override
    public HttpRequestBuilder setURL(String url) {
        setURL(URL.parse(url));
        return this;
    }

    private static final class Entry<T> {

        private final HeaderValueType<T> type;
        private final T value;

        Entry(HeaderValueType<T> type, T value) {
            this.type = type;
            this.value = value;
        }

        void addTo(HttpHeaders h) {
            h.add(type.name(), type.toCharSequence(value));
        }
    }

    @Override
    public HttpRequestBuilder dontAggregateResponse() {
        noAggregate = true;
        return this;
    }
}
