/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.url.URL;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.thread.Receiver;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Stores cookies from responses and decorates requests with them where
 * appropriate.
 *
 * @author Tim Boudreau
 */
public final class CookieStore implements Iterable<Cookie> {

    final Set<DateCookie> cookies = new HashSet<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final boolean checkDomain;
    private final boolean checkPath;
    private Receiver<Throwable> errorHandler;

    /**
     * Create a cookie store.
     *
     * @param checkDomain Ignore cookies with the wrong domain
     * @param checkPath Ignore cookies that do not match the path
     */
    public CookieStore(boolean checkDomain, boolean checkPath) {
        this.checkDomain = checkDomain;
        this.checkPath = checkPath;
    }

    /**
     * Create a cookie store which will check domains and paths when adding
     * cookies.
     */
    public CookieStore() {
        this(true, true);
    }

    CookieStore(Set<DateCookie> cookies, boolean checkDomain, boolean checkPath) {
        Checks.notNull("cookies", cookies);
        this.cookies.addAll(cookies);
        this.checkDomain = checkDomain;
        this.checkPath = checkPath;
    }

    public CookieStore checkDomain() {
        return new CookieStore(cookies, true, checkPath);
    }

    public CookieStore dontCheckDomain() {
        return new CookieStore(cookies, false, checkPath);
    }

    public int size() {
        Lock readLock = lock.readLock();
        try {
            readLock.lock();
            return cookies.size();
        } finally {
            readLock.unlock();
        }
    }

    public boolean isEmpty() {
        Lock readLock = lock.readLock();
        try {
            readLock.lock();
            return cookies.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns a new CookieStore which <b>will</b> check the path parameter of
     * cookies when deciding to add them to a request. This is the default.
     *
     * @return A new CookieStore
     */
    public CookieStore checkPath() {
        return new CookieStore(cookies, checkDomain, true);
    }

    /**
     * Returns a new CookieStore which does not check the path parameter of
     * cookies when deciding to add them to a request.
     *
     * @return A new CookieStore
     */
    public CookieStore dontCheckPath() {
        return new CookieStore(cookies, checkDomain, false);
    }

    /**
     * Add a handler which will be called if an exception is thrown when parsing
     * cookies - i.e. a server gave an invalid value for the cookie header. If
     * not set, the exception will be thrown.
     *
     * @param errorHandler An error handler
     * @return this
     */
    public CookieStore errorHandler(Receiver<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
        return this;
    }

    void decorate(HttpRequest req) {
        URL url;
        if (!req.getUri().contains("://")) {
            String host = req.headers().get(Headers.HOST.name());
            url = URL.builder().setPath(req.getUri()).setHost(host).create();
        } else {
            url = URL.parse(req.getUri());
        }
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            List<Cookie> toSend = new ArrayList<>();
            for (Cookie cookie : cookies) {
                if (checkDomain) {
                    if (cookie.getDomain() != null && !cookie.getDomain().equals(url.getHost().toString())) {
                        continue;
                    }
                }
                if (checkPath) {
                    String pth = cookie.getPath();
                    if (pth != null) {
                        String compare = url.getPath().toStringWithLeadingSlash();
                        if (!"/".equals(pth) && !"".equals(pth) && !compare.equals(pth) && !compare.startsWith(pth)) {
                            continue;
                        }
                    }
                }
                toSend.add(cookie);
            }
            if (!toSend.isEmpty()) {
                for (Cookie ck : toSend) {
                    String headerValue = Headers.COOKIE.toString(new Cookie[]{ck});
                    req.headers().add(Headers.COOKIE.name(), headerValue);
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    public String get(String name) {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            for (Cookie ck : cookies) {
                if (name.equals(ck.getName())) {
                    return ck.getValue();
                }
            }
        } finally {
            readLock.unlock();
        }
        return null;
    }

    public void add(Cookie cookie) {
        String name = cookie.getName();
        Lock writeLock = lock.writeLock();
        try {
            writeLock.lock();
            for (Iterator<DateCookie> it = cookies.iterator(); it.hasNext();) {
                DateCookie ck = it.next();
                if (name.equals(ck.getName())) {
                    it.remove();
                } else if (ck.isExpired()) {
                    it.remove();
                }
            }
            if (!cookie.isDiscard() && cookie.getMaxAge() > 0) {
                cookies.add(new DateCookie(cookie));
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void remove(String name) {
        Lock writeLock = lock.writeLock();
        try {
            writeLock.lock();
            for (Iterator<DateCookie> it = cookies.iterator(); it.hasNext();) {
                DateCookie ck = it.next();
                if (name.equals(ck.getName())) {
                    it.remove();
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    void extract(HttpHeaders headers) {
        List<String> hdrs = headers.getAll(Headers.SET_COOKIE.name());
        if (!hdrs.isEmpty()) {
            Lock writeLock = lock.writeLock();
            try {
                writeLock.lock();
                for (String header : hdrs) {
                    try {
                        Cookie cookie = Headers.SET_COOKIE.toValue(header);
                        add(cookie);
                    } catch (Exception e) {
                        if (errorHandler != null) {
                            errorHandler.receive(e);
                        } else {
                            Exceptions.chuck(e);
                        }
                    }
                }
            } finally {
                writeLock.unlock();
            }
        }
    }

    public String toString() {
        Lock readLock = lock.readLock();
        List<Cookie> cks = new ArrayList<Cookie>();
        readLock.lock();
        try {
            if (cookies.isEmpty()) {
                return "[no cookies]";
            }
            cks.addAll(cookies);
        } finally {
            readLock.unlock();
        }
        Collections.sort(cks);
        return Headers.COOKIE.toString(cks.toArray(new Cookie[cks.size()]));
    }

    @Override
    public Iterator<Cookie> iterator() {
        Lock readLock = lock.readLock();
        List<Cookie> cks = new ArrayList<Cookie>();
        readLock.lock();
        try {
            cks.addAll(cookies);
        } finally {
            readLock.unlock();
        }
        Collections.sort(cks);
        return cks.iterator();
    }

    public void store(OutputStream out) throws IOException {
        ObjectMapper om = new ObjectMapper();
        Lock readLock = lock.readLock();
        List<DateCookie> cks = new ArrayList<>();
        readLock.lock();
        try {
            cks.addAll(cookies);
        } finally {
            readLock.unlock();
        }
        List<Map<String, Object>> list = new LinkedList<>();
        for (DateCookie ck : cks) {
            Map<String, Object> m = new HashMap<>();
            m.put("domain", ck.getDomain());
            m.put("maxAge", ck.getMaxAge());
            m.put("timestamp", ck.getTimestamp().getMillis());
            m.put("path", ck.getPath());
            m.put("name", ck.getName());
            m.put("value", ck.getValue());
            m.put("httpOnly", ck.isHttpOnly());
            m.put("secure", ck.isSecure());
            if (ck.getComment() != null) {
                m.put("comment", ck.getComment());
            }
            if (ck.getCommentUrl() != null) {
                m.put("commentUrl", ck.getCommentUrl());
            }
            if (ck.getPorts() != null && !ck.getPorts().isEmpty()) {
                m.put("ports", ck.getPorts().toArray(new Integer[0]));
            }
            list.add(m);
        }
        om.writeValue(out, list);
    }

    @SuppressWarnings("unchecked")
    public void read(InputStream in) throws IOException {
        ObjectMapper om = new ObjectMapper();
        List<Map<String, Object>> l = om.readValue(in, List.class);
        List<DateCookie> cks = new ArrayList<>();
        for (Map<String, Object> m : l) {
            String domain = (String) m.get("domain");
            Number maxAge = (Number) m.get("maxAge");
            Number timestamp = (Number) m.get("timestamp");
            String path = (String) m.get("path");
            String name = (String) m.get("name");
            String value = (String) m.get("value");
            Boolean httpOnly = (Boolean) m.get("httpOnly");
            Boolean secure = (Boolean) m.get("secure");
            String comment = (String) m.get("comment");
            String commentUrl = (String) m.get("commentUrl");
            List<Integer> ports = (List<Integer>) m.get("ports");
            DateTime ts = timestamp == null ? DateTime.now() : new DateTime(timestamp.longValue());
            DateCookie cookie = new DateCookie(new DefaultCookie(name, value), ts);
            if (cookie.isExpired()) {
                continue;
            }
            if (domain != null) {
                cookie.setDomain(domain);
            }
            if (maxAge != null) {
                cookie.setMaxAge(maxAge.longValue());
            }
            if (path != null) {
                cookie.setPath(path);
            }
            if (httpOnly != null) {
                cookie.setHttpOnly(httpOnly);
            }
            if (secure != null) {
                cookie.setSecure(secure);
            }
            if (comment != null) {
                cookie.setComment(comment);
            }
            if (commentUrl != null) {
                cookie.setCommentUrl(commentUrl);
            }
            if (ports != null) {
                cookie.setPorts(ports);
            }
            cks.add(cookie);
        }
        if (!cks.isEmpty()) {
            Lock writeLock = lock.writeLock();
            try {
                writeLock.lock();
                cookies.addAll(cks);
            } finally {
                writeLock.unlock();
            }
        }
    }

    public int hashCode() {
        return cookies.hashCode();
    }

    public boolean equals(Object o) {
        if (o instanceof CookieStore) {
            return toString().equals(o.toString());
        }
        return false;
    }

    static final class DateCookie implements Cookie {

        private final Cookie delegate;
        private final DateTime timestamp;

        public DateCookie(Cookie delegate) {
            this.delegate = delegate;
            timestamp = DateTime.now();
        }

        public DateCookie(Cookie delegate, DateTime timestamp) {
            this.delegate = delegate;
            this.timestamp = timestamp;
        }

        public DateTime getTimestamp() {
            return timestamp;
        }

        public boolean isExpired() {
            if (getMaxAge() == Long.MAX_VALUE) {
                return false;
            }
            Duration dur;
            try {
                dur = Duration.standardSeconds(getMaxAge());
            } catch (ArithmeticException ex) {
                // A number high enough that * 1000 it is > Long.MAX_VALUE will overflow
                dur = new Duration(Long.MAX_VALUE);
            }
            try {
                return timestamp.plus(dur).isBefore(DateTime.now());
            } catch (ArithmeticException e) {
                // This also can overflow
                return false;
            }
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getValue() {
            return delegate.getValue();
        }

        @Override
        public void setValue(String value) {
            delegate.setValue(value);
        }

        @Override
        public String getDomain() {
            return delegate.getDomain();
        }

        @Override
        public void setDomain(String domain) {
            delegate.setDomain(domain);
        }

        @Override
        public String getPath() {
            return delegate.getPath();
        }

        @Override
        public void setPath(String path) {
            delegate.setPath(path);
        }

        @Override
        public String getComment() {
            return delegate.getComment();
        }

        @Override
        public void setComment(String comment) {
            delegate.setComment(comment);
        }

        @Override
        public long getMaxAge() {
            return delegate.getMaxAge();
        }

        @Override
        public void setMaxAge(long maxAge) {
            delegate.setMaxAge(maxAge);
        }

        @Override
        public int getVersion() {
            return delegate.getVersion();
        }

        @Override
        public void setVersion(int version) {
            delegate.setVersion(version);
        }

        @Override
        public boolean isSecure() {
            return delegate.isSecure();
        }

        @Override
        public void setSecure(boolean secure) {
            delegate.setSecure(secure);
        }

        @Override
        public boolean isHttpOnly() {
            return delegate.isHttpOnly();
        }

        @Override
        public void setHttpOnly(boolean httpOnly) {
            delegate.setHttpOnly(httpOnly);
        }

        @Override
        public String getCommentUrl() {
            return delegate.getCommentUrl();
        }

        @Override
        public void setCommentUrl(String commentUrl) {
            delegate.setCommentUrl(commentUrl);
        }

        @Override
        public boolean isDiscard() {
            return delegate.isDiscard();
        }

        @Override
        public void setDiscard(boolean discard) {
            delegate.setDiscard(discard);
        }

        @Override
        public Set<Integer> getPorts() {
            return delegate.getPorts();
        }

        @Override
        public void setPorts(int... ports) {
            delegate.setPorts(ports);
        }

        @Override
        public void setPorts(Iterable<Integer> ports) {
            delegate.setPorts(ports);
        }

        public int compareTo(Cookie t) {
            return delegate.compareTo(t);
        }

        @Override
        public Set<Integer> ports() {
            return delegate.ports();
        }

        @Override
        public String comment() {
            return delegate.comment();
        }

        @Override
        public long maxAge() {
            return delegate.maxAge();
        }

        @Override
        public int version() {
            return delegate.version();
        }

        @Override
        public String commentUrl() {
            return delegate.commentUrl();
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String value() {
            return delegate.value();
        }

        @Override
        public boolean wrap() {
            return delegate.wrap();
        }

        @Override
        public void setWrap(boolean bln) {
            delegate.setWrap(bln);
        }

        @Override
        public String domain() {
            return delegate.domain();
        }

        @Override
        public String path() {
            return delegate.path();
        }

        @Override
        public int compareTo(io.netty.handler.codec.http.cookie.Cookie o) {
            return delegate.compareTo(o);
        }
    }
}
