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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import com.mastfrog.marshallers.netty.NettyContentMarshallers;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Streams;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import java.io.IOException;
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
    NettyContentMarshallers marshallers;

    public ResponseHandler(Class<T> type, NettyContentMarshallers marshallers) {
        this.marshallers = marshallers;
        this.type = type;
    }

    public ResponseHandler(Class<T> type, ObjectMapper mapper) {
        this(type, NettyContentMarshallers.getDefault(mapper));
    }

    public ResponseHandler(Class<T> type) {
        this(type, NettyContentMarshallers.getDefault(new ObjectMapper()));
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
                onErrorResponse(status, headers, content.readCharSequence(content.readableBytes(), CharsetUtil.UTF_8).toString());
                return;
            }
            MediaType mediaType = null;
            if (headers.contains(HttpHeaderNames.CONTENT_TYPE)) {
                try {
                    mediaType = MediaType.parse(headers.get(HttpHeaderNames.CONTENT_TYPE));
                } catch (Exception ex) {
                    //do nothing
                }
            }
            T o = mediaType == null ? marshallers.read(type, content) : marshallers.read(type, content, mediaType);
            _doReceive(status, headers, type.cast(o));
        } catch (Exception ex) {
            content.resetReaderIndex();
            try {
                String s = Streams.readString(new ByteBufInputStream(content), "UTF-8");
                onErrorResponse(HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE, headers, s);
            } catch (IOException ex1) {
                ex.addSuppressed(ex1);
            }
            Exceptions.chuck(ex);
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
//        err.printStackTrace();
    }

    public Class<T> type() {
        return type;
    }
}
