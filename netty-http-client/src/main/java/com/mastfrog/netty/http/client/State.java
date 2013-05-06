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
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Represents the current state of a request, used in notifications.
 * Subclass types can be used as keys for describing what events
 * to listen to in a way that carries type information.
 *
 * @author Tim Boudreau
 */
public abstract class State<T> {
    
    /**
     * State event when a connection is being attempted.  No payload.
     */
    static final class Connecting extends State<Void> {

        Connecting() {
            super(Void.class, StateType.Connecting, null);
        }
    }

    /**
     * State event when a request has been sent, before the response header
     * has completely arrived.  No payload.
     */
    static final class AwaitingResponse extends State<Void> {

        AwaitingResponse() {
            super(Void.class, StateType.AwaitingResponse, null);
        }
    }

    /**
     * State event when a connection has been achieved;  payload is the
     * Channel;  invoking close() on it will abort.
     */
    public static final class Connected extends State<Channel> {

        Connected(Channel channel) {
            super(Channel.class, StateType.Connected, channel);
        }
    }
    /**
     * State event when the HTTP request is about to be sent;  payload
     * is the HTTP request (you can still modify headers, etc at this point).
     */
    public static final class SendRequest extends State<HttpRequest> {

        SendRequest(HttpRequest req) {
            super(HttpRequest.class, StateType.SendRequest, req);
        }
    }

    /**
     * State event triggered when the response <i>header</i> has arrived,
     * but not the response body.
     */
    public static final class HeadersReceived extends State<HttpResponse> {

        HeadersReceived(HttpResponse headers) {
            super(HttpResponse.class, StateType.HeadersReceived, headers);
        }
    }

    /**
     * State event triggered when one chunk of content has arrived;  if the
     * server is using chunked transfer encoding, this state will be fired
     * once for each chunk;  when the FullContentReceived event is fired,
     * there will be no more ContentReceived events.
     */
    public static final class ContentReceived extends State<HttpContent> {

        ContentReceived(HttpContent headers) {
            super(HttpContent.class, StateType.ContentReceived, headers);
        }
    }

    /**
     * State event triggered when the entire response body has arrived.
     */
    public static final class FullContentReceived extends State<ByteBuf> {

        FullContentReceived(ByteBuf content) {
            super(ByteBuf.class, StateType.FullContentReceived, content);
        }
    }

    /**
     * State event triggered when a redirect is followed.
     */
    public static final class Redirect extends State<URL> {

        Redirect(URL content) {
            super(URL.class, StateType.Redirect, content);
        }
    }

    /**
     * Final state event triggered when the channel is unregistered.
     */
    static final class Closed extends State<Void> {

        Closed() {
            super(Void.class, StateType.Closed, null);
        }
    }

    /**
     * Convenience state event providing the entire response and its body
     * as a FullHttpResponse.
     */
    public static final class Finished extends State<FullHttpResponse> {

        Finished(FullHttpResponse buf) {
            super(FullHttpResponse.class, StateType.Finished, buf);
        }
    }

    /**
     * State event triggered when an exception is thrown somewhere in 
     * processing of the request or response.  Does not indicate that processing
     * is aborted (close the channel for that), or that further errors will
     * not be thrown.
     */
    public static final class Error extends State<Throwable> {

        Error(Throwable t) {
            super(Throwable.class, StateType.Error, t);
        }
    }

    /**
     * State event triggered by someone invoking cancel() on the ResponseFuture
     * for this request.
     */
    static final class Cancelled extends State<Void> {

        Cancelled() {
            super(Void.class, StateType.Cancelled, null);
        }
    }
    private final Class<T> type;
    private final StateType name;
    private final T state;

    State(Class<T> type, StateType name, T state) {
        this.type = type;
        this.name = name;
        this.state = state;
    }

    public Class<T> type() {
        return type;
    }

    public String name() {
        return name.name();
    }

    public StateType stateType() {
        return name;
    }

    public T get() {
        return state;
    }

    @Override
    public String toString() {
        return name();
    }
}
