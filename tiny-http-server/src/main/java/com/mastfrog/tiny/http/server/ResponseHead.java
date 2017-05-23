/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
package com.mastfrog.tiny.http.server;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Passed to responders to set headers and the code and http version.
 *
 * @author Tim Boudreau
 */
public final class ResponseHead {

    int code = 200;
    final Map<CharSequence, Object> headers = new LinkedHashMap<>();

    HttpVersion version = HttpVersion.HTTP_1_1;

    public ValueReceiver header(final CharSequence header) {
        return (val) -> {
            headers.put(header, val);
            return ResponseHead.this;
        };
    }

    public ResponseHead httpVersion(HttpVersion version) {
        this.version = version;
        return this;
    }

    public ResponseHead status(HttpResponseStatus status) {
        this.code = status.code();
        return this;
    }

    public ResponseHead status(int code) {
        this.code = code;
        return this;
    }

    public interface ValueReceiver {

        ResponseHead set(Object val);
    }
}
