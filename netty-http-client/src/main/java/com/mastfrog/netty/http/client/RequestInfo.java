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

import com.mastfrog.url.URL;
import io.netty.handler.codec.http.HttpRequest;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
final class RequestInfo {

    final URL url;
    final HttpRequest req;
    final AtomicBoolean cancelled;
    final ResponseFuture handle;
    final ResponseHandler<?> r;
    final AtomicInteger redirectCount = new AtomicInteger();
    final Duration timeout;
    final DateTime startTime;
    volatile boolean listenerAdded;
    TimerTask timer;
    final boolean dontAggregate;
    final ChunkedContent chunkedBody;

    RequestInfo(URL url, HttpRequest req, AtomicBoolean cancelled, ResponseFuture handle, ResponseHandler<?> r, 
            Duration timeout, DateTime startTime, TimerTask timer, boolean noAggregate, ChunkedContent chunkedBody) {
        this.url = url;
        this.req = req;
        this.cancelled = cancelled;
        this.handle = handle;
        this.r = r;
        this.timeout = timeout;
        this.startTime = startTime;
        this.timer = timer;
        this.dontAggregate = noAggregate;
        this.chunkedBody = chunkedBody;
    }

    RequestInfo(URL url, HttpRequest req, AtomicBoolean cancelled, ResponseFuture handle, ResponseHandler<?> r, Duration timeout, TimerTask timer, boolean noAggregate, ChunkedContent chunkedBody) {
        this(url, req, cancelled, handle, r, timeout, DateTime.now(), timer, noAggregate, chunkedBody);
    }
    
    Duration age() {
        return new Duration(startTime, DateTime.now());
    }
    
    Duration remaining() {
        return timeout == null ? null : timeout.minus(age());
    }

    boolean isExpired() {
        if (timeout != null) {
            return DateTime.now().isAfter(startTime.plus(timeout));
        }
        return false;
    }
    
    void cancelTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    @Override
    public String toString() {
        return "RequestInfo{" + "url=" + url + ", req=" + req + ", cancelled="
                + cancelled + ", handle=" + handle + ", r=" + r + ", timeout=" + timeout + '}';
    }
}
