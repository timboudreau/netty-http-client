/*
 * The MIT License
 *
 * Copyright 2016 tim.
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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 *
 * @author Tim Boudreau
 */
public abstract class CallbackRequestBody {

    public abstract CallbackResult onPublish(ChannelHandlerContext ctx);

    protected final CallbackResult chunk(String data, boolean last) {
        return null;
    }

    public static final class CallbackResult {
        private final CallbackStatus status;
        private final ByteBuf data;
        private final Throwable error;

        private CallbackResult(CallbackStatus status, ByteBuf data, Throwable error) {
            this.status = status;
            this.data = data;
            this.error = error;
        }
    }

    public enum CallbackStatus {
        CALL_WHEN_FLUSHED,
        ERROR,
        END
    }
}
