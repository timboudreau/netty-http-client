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
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.url.Protocol;
import com.mastfrog.url.URL;
import com.mastfrog.util.thread.Receiver;
import java.io.IOException;
import java.time.Duration;

/**
 * Builds an HTTP request, allows for adding listeners for response events, and
 * for launching such a request.
 *
 * @author Tim Boudreau
 */
public interface HttpRequestBuilder {

    /**
     * Add a request header.  The <code>Headers</code> class implements
     * a lot of common header types.
     *
     * @param <T> The header type
     * @param type The header type
     * @param value The value
     * @return this
     */
    <T> HttpRequestBuilder addHeader(HeaderValueType<T> type, T value);

    /**
     * Append a path element to the URL
     *
     * @param element a path element
     * @return this
     */
    HttpRequestBuilder addPathElement(String element);

    /**
     * Add a query key/value pair to the URL
     *
     * @param key A key
     * @param value A value
     * @return this
     */
    HttpRequestBuilder addQueryPair(String key, String value);

    /**
     * Set the anchor portion of the URL
     *
     * @param anchor The anchor
     * @return this
     */
    HttpRequestBuilder setAnchor(String anchor);

    /**
     * Set the host portion of the URL
     *
     * @param host
     * @return
     */
    HttpRequestBuilder setHost(String host);

    /**
     * Set the complete path of the URL (clobbering any earlier calls to add
     * path elements)
     *
     * @param path the path
     * @return this
     */
    HttpRequestBuilder setPath(String path);

    /**
     * Set the port
     *
     * @param port The port
     * @return this
     */
    HttpRequestBuilder setPort(int port);

    /**
     * Set the protocol. See the Protocols enum for common values.
     *
     * @param protocol
     * @return
     */
    HttpRequestBuilder setProtocol(Protocol protocol);

    /**
     * Set the whole URL, clobbering any earlier settings
     *
     * @param url
     * @return
     */
    HttpRequestBuilder setURL(URL url);
    
    /**
     * Set the whole URL, clobbering any earlier settings
     *
     * @param url
     * @return
     */
    HttpRequestBuilder setURL(String url);    

    /**
     * Set the user name that will be put in the URL - note this is distinct
     * from basic authentication - it results in urls such as
     * <code>http://user:password&#064;host/path</code>
     *
     * @param userName
     * @return
     */
    HttpRequestBuilder setUserName(String userName);

    /**
     * Set the password that will be put in the URL - note this is distinct from
     * basic authentication - it results in urls such as
     * <code>http://user:password&#064;host/path</code>
     *
     * @param userName
     * @return this
     */
    HttpRequestBuilder setPassword(String password);

    /**
     * Set basic auth credentials
     *
     * @param username The username
     * @param password The password
     * @return this
     */
    HttpRequestBuilder basicAuthentication(String username, String password);

    /**
     * Launch the request
     *
     * @param response
     * @return
     */
    ResponseFuture execute(ResponseHandler<?> response);

    /**
     * Launch the request
     *
     * @return
     */
    ResponseFuture execute();

    /**
     * Set the request body.  May be a string, byte array, ByteBuf, InputStream,
     * Image or an Object which can be converted to JSON by a vanilla ObjectMapper.
     * <p/>
     * For custom serialization, convert to a byte stream first.
     *
     * @param o The body
     * @return
     */
    HttpRequestBuilder setBody(Object o, MediaType contentType) throws IOException;

    /**
     * Add an event handler for a particular event
     *
     * @param <T>
     * @param event The event type
     * @param r The handler.  If the handler is the wrong type for the object
     * of this event, it will be called with null.
     * @return this
     */
    <T> HttpRequestBuilder on(Class<? extends State<T>> event, Receiver<T> r);
    /**
     * Add an event handler for a particular event, using enum constants.
     * Use this to pick up events like connected, closed, etc.  Note that it
     * <i>is</i> possible to pass a Receiver for the wrong type here.  In that
     * case a warning will be logged when it is called, and you will be passed
     * null instead.  Simple events like connecting and close take Void, and 
     * will always be passed null.
     * @param <T> The type of object this event produces
     * @param event The type of event
     * @param r A callback which will be called with the event contents
     * zero or more times
     * @return this
     */
    <T> HttpRequestBuilder on(StateType event, Receiver<T> r);

    /**
     * Add an event handler which will be called for every event
     *
     * @param r An event handler
     * @return this
     */
    HttpRequestBuilder onEvent(Receiver<State<?>> r);
    
    /**
     * Get the URL as it currently stands for this request
     * @return A url
     */
    URL toURL();
    
    /**
     * Don't sent the host header from the URL
     * @return this
     */
    HttpRequestBuilder noHostHeader();
    /**
     * Don't send the connection header
     * @return this
     */
    HttpRequestBuilder noConnectionHeader();
    /**
     * Don't create a Date: header
     * @return this
     */
    HttpRequestBuilder noDateHeader();
    
    /**
     * Set a cookie store which will be updated from Set-Cookie headers in the
     * response, and which will decorate the request with any Cookies it has
     * that match the request URL.
     * 
     * @param store The cookie store
     * @return this
     */
    HttpRequestBuilder setCookieStore(CookieStore store);
    
    HttpRequestBuilder setTimeout(Duration timeout);
    /**
     * If called, the request builder will not aggregate http chunks,
     * and the full http response will not be sent to listeners.  Use this
     * for large uploads where you intend to store the incoming chunks to disk
     * or some other not-in-ram storage as they arrive.
     * 
     * @return this
     */
    HttpRequestBuilder dontAggregateResponse();
}
