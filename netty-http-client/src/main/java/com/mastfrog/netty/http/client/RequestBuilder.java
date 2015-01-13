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
import com.google.common.net.MediaType;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.url.Protocol;
import com.mastfrog.url.URL;
import com.mastfrog.url.URLBuilder;
import com.mastfrog.util.Streams;
import com.mastfrog.util.thread.Receiver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import javax.imageio.ImageIO;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 *
 * @author tim
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

    RequestBuilder(Method method, ByteBufAllocator alloc) {
        this.method = method;
        this.alloc = alloc;
    }

    @Override
    public HttpRequestBuilder setTimeout(Duration timeout) {
        if (timeout != null && timeout.getMillis() == 0) {
            throw new IllegalArgumentException("Cannot set timeout to 0");
        }
        this.timeout = timeout;
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

    public RequestBuilder noDateHeader() {
        noDateHeader = true;
        return this;
    }

    private boolean noConnectionHeader;

    public RequestBuilder noConnectionHeader() {
        noConnectionHeader = true;
        return this;
    }

    private boolean noHostHeader;

    public RequestBuilder noHostHeader() {
        noHostHeader = true;
        return this;
    }

    public RequestBuilder setCookieStore(CookieStore store) {
        this.store = store;
        return this;
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
        DefaultHttpRequest h = body == null
                ? new DefaultHttpRequest(version, mth, uri)
                : new DefaultFullHttpRequest(version, mth, uri, body);
        for (Entry<?> e : entries) {
            e.addTo(h.headers());
        }
        if (!noHostHeader) {
            h.headers().add(HttpHeaders.Names.HOST, u.getHost().toString());
        }
        if (!h.headers().contains(HttpHeaders.Names.CONNECTION) && !noConnectionHeader) {
            h.headers().add(HttpHeaders.Names.CONNECTION, "close");
        }
        if (!noDateHeader) {
            h.headers().add(HttpHeaders.Names.DATE, Headers.DATE.toString(DateTime.now()));
        }
        if (store != null) {
            store.decorate(h);
        }
        return h;
    }

    public URL toURL() {
        return url.create();
    }

    private ByteBuf body;
    boolean send100Continue = true;

    @Override
    public HttpRequestBuilder setBody(Object o, MediaType contentType) throws IOException {
        if (o instanceof CharSequence) {
            CharSequence seq = (CharSequence) o;
            setBody(seq.toString().getBytes(CharsetUtil.UTF_8), contentType);
        } else if (o instanceof byte[]) {
            byte[] b = (byte[]) o;
            ByteBuf buffer = alloc.buffer(b.length).writeBytes(b);
            setBody(buffer, contentType);
        } else if (o instanceof ByteBuf) {
            body = (ByteBuf) o;
            if (send100Continue) {
                addHeader(Headers.stringHeader(HttpHeaders.Names.EXPECT), HttpHeaders.Values.CONTINUE);
            }
            addHeader(Headers.CONTENT_LENGTH, (long) body.readableBytes());
            addHeader(Headers.CONTENT_TYPE, contentType);
        } else if (o instanceof InputStream) {
            ByteBuf buf = newByteBuf();
            try (ByteBufOutputStream out = new ByteBufOutputStream(buf)) {
                try (InputStream in = (InputStream) o) {
                    Streams.copy(in, out, 1024);
                }
            }
            setBody(buf, contentType);
        } else if (o instanceof RenderedImage) {
            ByteBuf buf = newByteBuf();
            try (ByteBufOutputStream out = new ByteBufOutputStream(buf)) {
                String type = contentType.subtype();
                if ("jpeg".equals(type)) {
                    type = "jpg";
                }
                ImageIO.write((RenderedImage) o, type, out);
            }
            setBody(buf, contentType);
        } else {
            try {
                setBody(new ObjectMapper().writeValueAsBytes(o), contentType);
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        }
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

        public Entry(HeaderValueType<T> type, T value) {
            this.type = type;
            this.value = value;
        }

        void addTo(HttpHeaders h) {
            h.add(type.name(), type.toString(value));
        }
    }

    @Override
    public HttpRequestBuilder dontAggregateResponse() {
        noAggregate = true;
        return this;
    }
}
