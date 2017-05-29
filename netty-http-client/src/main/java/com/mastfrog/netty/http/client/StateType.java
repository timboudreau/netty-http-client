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
import com.mastfrog.util.thread.Receiver;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.lang.reflect.Method;
import org.joda.time.Duration;

/**
 * Enumeration of states a request can be in.
 *
 * @author Tim Boudreau
 */
public enum StateType {
    /**
     * A connection has not been made yet.
     */
    Connecting,
    /**
     * A connection has been made.
     */
    Connected,
    /**
     * About to send a request
     */
    SendRequest,
    /**
     * The request has been sent.
     */
    AwaitingResponse,
    /**
     * The response headers have been received, but the response body has not
     * yet (or there will not be one).
     */
    HeadersReceived,
    /**
     * One chunk of content has been received - not necessarily the entire
     * response, but some content.
     */
    ContentReceived,
    /**
     * The response was a 300-307 HTTP redirect and the redirect is being
     * followed. Note this event will only be seen if the HttpClient is set to
     * follow redirects - otherwise, you will just see the redirect headers and
     * body.
     */
    Redirect,
    /**
     * The entire content of the response has arrived.
     */
    FullContentReceived,
    /**
     * The connection was closed.
     */
    Closed,
    /**
     * Similar to FullContentReceived, this event gives you a Netty
     * FullHttpRequest with the entire response.
     */
    Finished,
    /**
     * An exception was thrown.
     */
    Error,
    /**
     * Called when a timeout occurs.
     */
    Timeout,
    /**
     * The call was cancelled; useful for cleaning up resources.
     */
    Cancelled;

    Receiver<?> wrapperReceiver(Receiver<?> orig) {
        return wrapperReceiver(stateValueType(), orig);
    }

    public boolean isResponseComplete() {
        switch (this) {
            case AwaitingResponse:
            case Connected:
            case Connecting:
            case ContentReceived:
            case HeadersReceived:
            case SendRequest:
                return false;
        }
        return true;
    }

    public boolean isFailure() {
        switch (this) {
            case Cancelled:
            case Closed:
            case Error:
            case Timeout:
                return true;
        }
        return false;
    }

    private <T> Receiver<T> wrapperReceiver(final Class<T> type, final Receiver<?> orig) {
        return new Receiver<T>() {
            @Override
            @SuppressWarnings("unchecked")
            public void receive(T object) {
                Receiver r = orig;
                try {
                    r.receive(object);
                } catch (ClassCastException e) {
                    String typeName = null;
                    for (Method m : orig.getClass().getMethods()) {
                        if ("receive".equals(m.getName()) && m.getParameterTypes().length == 1) {
                            Class<?> what = m.getParameterTypes()[0];
                            if (what != Object.class) {
                                typeName = what.getName();
                                break;
                            }
                        }
                    }
                    System.err.println("Receiver " + orig + " for "
                            + type.getName() + " takes the "
                            + "wrong class " + typeName + " in its receive() "
                            + "method. Expected " + type + ". Passing null "
                            + "instead");
                    orig.receive(null);
                }
            }
        };
    }

    /**
     * Get the type of the State object tied to this event
     *
     * @return a type
     */
    public Class<?> stateValueType() {
        switch (this) {
            case Connecting:
                return Void.class;
            case Connected:
                return Channel.class;
            case SendRequest:
                return HttpRequest.class;
            case AwaitingResponse:
                return Void.class;
            case HeadersReceived:
                return HttpResponse.class;
            case ContentReceived:
                return HttpContent.class;
            case Redirect:
                return URL.class;
            case FullContentReceived:
                return ByteBuf.class;
            case Closed:
                return Void.class;
            case Finished:
                return FullHttpResponse.class;
            case Error:
                return Throwable.class;
            case Cancelled:
                return Boolean.class;
            case Timeout:
                return Duration.class;
            default:
                throw new AssertionError(this);
        }
    }

    /**
     * Get the type of the data payload of this event
     *
     * @return a type
     */
    public Class<? extends State<?>> type() {
        switch (this) {
            case Connecting:
                return State.Connecting.class;
            case Connected:
                return State.Connected.class;
            case SendRequest:
                return State.SendRequest.class;
            case AwaitingResponse:
                return State.AwaitingResponse.class;
            case HeadersReceived:
                return State.HeadersReceived.class;
            case ContentReceived:
                return State.ContentReceived.class;
            case Redirect:
                return State.Redirect.class;
            case FullContentReceived:
                return State.FullContentReceived.class;
            case Closed:
                return State.Closed.class;
            case Finished:
                return State.Finished.class;
            case Error:
                return State.Error.class;
            case Cancelled:
                return State.Cancelled.class;
            case Timeout:
                return State.Timeout.class;
            default:
                throw new AssertionError(this);
        }
    }

}
