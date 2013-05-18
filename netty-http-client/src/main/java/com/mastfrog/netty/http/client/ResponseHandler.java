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
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import com.mastfrog.util.Exceptions;
import io.netty.handler.codec.http.HttpHeaders;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Processes an HTTP response - can be passed to HttpRequestBuilder.execute().
 * Altenately, you can listen for individual events on the ResponseFuture.
 * Override one of the receive() methods to get responses.
 * <p/>
 * Basic types and JSON unmarshalling with Jackson are supported out-of-the-box.
 * To do anything else, override internalReceive.
 * <p/>
 * In the case of a response code greater than 399, the onErrorResponse will be
 * called with the raw bytes.
 *
 * @author Tim Boudreau
 */
public abstract class ResponseHandler<T> {

    private final Class<T> type;
    private final CountDownLatch latch = new CountDownLatch(1);

    public ResponseHandler(Class<T> type) {
        this.type = type;
    }

    public void await() throws InterruptedException {
        latch.await();
    }

    public boolean await(long l, TimeUnit tu) throws InterruptedException {
        return latch.await(l, tu);
    }

    protected void internalReceive(HttpResponseStatus status, HttpHeaders headers, ByteBuf content) {
        try {
            if (status.code() > 399) {
                byte[] b = new byte[content.readableBytes()];
                content.readBytes(b);
                onErrorResponse(status, headers, new String(b, CharsetUtil.UTF_8));
                return;
            }
            if (type == ByteBuf.class) {
                _doReceive(status, headers, type.cast(content));
            } else if (type == String.class || type == CharSequence.class) {
                byte[] b = new byte[content.readableBytes()];
                content.readBytes(b);
                _doReceive(status, headers, type.cast(new String(b, CharsetUtil.UTF_8)));
            } else if (type == byte[].class) {
                byte[] b = new byte[content.readableBytes()];
                content.readBytes(b);
                _doReceive(status, headers, type.cast(b));
            } else {
                ObjectMapper mapper = new ObjectMapper();
                byte[] b = new byte[content.readableBytes()];
                content.readBytes(b);
                try {
                    Object o = mapper.readValue(b, type);
                    _doReceive(status, headers, type.cast(o));
                } catch (Exception ex) {
                    Exceptions.chuck(ex);
                }
            }
        } finally {
            latch.countDown();
        }
    }

    void _doReceive(HttpResponseStatus status, HttpHeaders headers, T obj) {
        receive(status, headers, obj);
        receive(status, obj);
        receive(obj);
    }

    protected void receive(HttpResponseStatus status, T obj) {
    }

    protected void receive(HttpResponseStatus status, HttpHeaders headers, T obj) {
    }

    protected void receive(T obj) {
    }

    protected void onErrorResponse(String content) {
    }

    protected void onErrorResponse(HttpResponseStatus status, String content) {
        onErrorResponse(content);
    }

    protected void onErrorResponse(HttpResponseStatus status, HttpHeaders headers, String content) {
        onErrorResponse(status, content);
    }

    protected void onError(Throwable err) {
        err.printStackTrace();
    }

    public Class<T> type() {
        return type;
    }
}
