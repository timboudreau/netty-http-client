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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.MediaType;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.util.Connection;
import com.mastfrog.netty.http.client.DeferredAssertions.Assertion;
import com.mastfrog.tiny.http.server.ChunkedResponse;
import com.mastfrog.tiny.http.server.Responder;
import com.mastfrog.tiny.http.server.ResponseHead;
import com.mastfrog.tiny.http.server.TinyHttpServer;
import com.mastfrog.url.URL;
import com.mastfrog.util.thread.Receiver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.util.CharsetUtil;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLException;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.Before;
import org.netbeans.validation.api.InvalidInputException;

/**
 *
 * @author tim
 */
public class HttpClientTest {

    private TinyHttpServer server;
    private HttpClient client;

    @Before
    public void setup() throws CertificateException, SSLException, InterruptedException {
        server = new TinyHttpServer(new ResponderImpl());
        client = HttpClient.builder()
                .resolver(new LocalhostOnlyAddressResolverGroup())
                .followRedirects()
                .useCompression()
                .setMaxRedirects(5)
                .build();
    }

    @After
    public void tearDown() throws Exception {
        Thread.sleep(200);
        if (server != null) {
            server.shutdown();
        }
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    public void testChunkedPut() throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);
        ChunkedBody body = new ChunkedBody();
        final AtomicReference<String> content = new AtomicReference<>();
        final AtomicReference<HttpResponseStatus> theStatus = new AtomicReference<>();
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        client.put().setURL("http://chunky.thing:" + server.httpPort() + "/").setBody(body, MediaType.PLAIN_TEXT_UTF_8)
                .onEvent(new Receiver<State<?>>() {
                    @Override
                    public void receive(State<?> state) {
                        if (state.stateType() == StateType.Closed) {
                            latch.countDown();
                        }
                    }
                })
                .execute(new ResponseHandler<String>(String.class) {
                    @Override
                    protected void receive(HttpResponseStatus status, HttpHeaders headers, String responseBody) {
                        theStatus.set(status);
                        content.set(responseBody);
                    }

                    @Override
                    protected void onError(Throwable err) {
                        thrown.set(err);
                        err.printStackTrace(System.out);
                    }

                    @Override
                    protected void onErrorResponse(HttpResponseStatus status, HttpHeaders headers, String content) {
                        theStatus.set(status);
                    }

                }).await(5, TimeUnit.SECONDS);
        latch.await(10, TimeUnit.SECONDS);
        server.throwLast();
        if (thrown.get() != null) {
            throw thrown.get();
        }
        assertEquals(CREATED, theStatus.get());
        assertEquals("You sent:\n" + body.content, content.get());
    }

    static final class ChunkedBody implements ChunkedContent {

        StringBuilder content = new StringBuilder();

        @Override
        public Object nextChunk(int callCount) {
            if (callCount < 10) {
                String line = "Chunk-" + (callCount + 1) + "\n";
                content.append(line);
                return Unpooled.wrappedBuffer(line.getBytes(CharsetUtil.UTF_8));
            }
            return null;
        }
    }

    @Test
    public void testRelativeRedirects() throws Throwable {
        try (DeferredAssertions as = new DeferredAssertions()) {
            client.get().setURL("http://messy.re:" + server.httpPort() + "/").onEvent(new Receiver<State<?>>() {
                @Override
                public void receive(final State<?> object) {
                    if (object.stateType() == StateType.Redirect) {
                        as.add(new Assertion() {
                            @Override
                            public void exec() throws Throwable {
                                assertEquals("http://messy.re:" + server.httpPort() + "/foo/bar", object.get().toString());
                            }
                        });
                    }
                }
            }).execute().await(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testRedirectLimit() throws Throwable {
        final List<URL> redirects = new CopyOnWriteArrayList<>();
        final Set<StateType> states = Sets.newConcurrentHashSet();
        final AtomicBoolean errorResponse = new AtomicBoolean();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicBoolean received = new AtomicBoolean();
        client.get().setURL("http://redirect.me:" + server.httpPort() + "/").on(State.Redirect.class, new Receiver<URL>() {
            @Override
            public void receive(URL object) {
                redirects.add(object);
            }
        }).onEvent(new Receiver<State<?>>() {
            @Override
            public void receive(State<?> object) {
                states.add(object.stateType());
            }
        }).execute(new ResponseHandler<String>(String.class) {
            @Override
            protected void onError(Throwable err) {
                error.set(err);
            }

            @Override
            protected void onErrorResponse(HttpResponseStatus status, HttpHeaders headers, String content) {
                errorResponse.set(true);
            }

            @Override
            protected void receive(HttpResponseStatus status, HttpHeaders headers, String obj) {
                received.set(true);
            }

        }).await(5, TimeUnit.SECONDS);
        Thread.sleep(200);
        assertEquals(5, redirects.size());
        assertTrue(states.contains(StateType.Connecting));
        assertTrue(states.contains(StateType.Connected));
        assertTrue(states.contains(StateType.SendRequest));
        assertTrue(states.contains(StateType.AwaitingResponse));
        assertTrue(states.contains(StateType.HeadersReceived));
        assertTrue(states.contains(StateType.Error));
        assertTrue(states.contains(StateType.Redirect));

        assertFalse(states.contains(StateType.Timeout));
        assertFalse(states.contains(StateType.Finished));
        assertFalse(states.contains(StateType.FullContentReceived));
        assertFalse(errorResponse.get());
        assertFalse(received.get());
        assertNotNull(error.get());
        assertTrue(error.get() instanceof RedirectException);
        assertTrue(((RedirectException) error.get()).kind() == RedirectException.Kind.REDIRECT_LOOP);
    }

    @Test(expected = InvalidInputException.class)
    public void testInvalidUrl() throws Throwable {
        client.get().setURL(URL.parse("!garbage!!")).execute();
    }

    @Test
    public void testCancellation() throws Throwable {
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<ResponseFuture> future = new AtomicReference<>();
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean receiveCalled = new AtomicBoolean();
        final AtomicBoolean errorResponseCalled = new AtomicBoolean();
        future.set(client.get().setURL("http://cancel.me:" + server.httpPort()).onEvent(new Receiver<State<?>>() {
            @Override
            public void receive(State<?> object) {
                if (object.stateType() == StateType.Cancelled) {
                    cancelled.set(true);
                    latch.countDown();
                }
            }
        }).execute(new ResponseHandler<String>(String.class) {
            @Override
            protected void onError(Throwable err) {
                exception.set(err);
                latch.countDown();
            }

            @Override
            protected void onErrorResponse(HttpResponseStatus status, HttpHeaders headers, String content) {
                errorResponseCalled.set(true);
            }

            @Override
            protected void receive(HttpResponseStatus status, HttpHeaders headers, String obj) {
                receiveCalled.set(true);
            }

        }));
        future.get().cancel();
        latch.await(10, TimeUnit.SECONDS);
        assertFalse(errorResponseCalled.get());
        assertFalse(receiveCalled.get());
        Thread.sleep(200);
        assertNotNull(exception.get());
        assertTrue(exception.get() instanceof CancellationException);
    }

    @Test
    public void testPost() throws Throwable {
        final AM am = new AM();
        final String ur = "http://foo.bar:" + server.httpPort() + "/foo";
        client.addActivityMonitor(am);
        final DeferredAssertions assertions = new DeferredAssertions();
        final Set<StateType> stateTypes = new HashSet<>();
        final String[] xheader = new String[1];
        ResponseFuture f = client.post()
                .setURL(ur)
                .addHeader(Headers.CONNECTION, Connection.close)
                .setBody("This is a test", MediaType.PLAIN_TEXT_UTF_8)
                .onEvent(new Receiver<State<?>>() {
                    public void receive(State<?> state) {
                        stateTypes.add(state.stateType());
                        if (null != state.stateType()) switch (state.stateType()) {
                            case Finished:
                                DefaultFullHttpResponse d = (DefaultFullHttpResponse) state.get();
                                assertions.add(() -> {
                                    assertTrue(am.started.contains(ur));
                        }); break;
                            case Closed:
                                assertions.add(() -> {
                                    assertTrue(am.ended.contains(ur));
                        }); break;
                            case FullContentReceived:
                                ByteBuf buf = (ByteBuf) state.get();
                                byte[] bytes = new byte[buf.readableBytes()];
                                buf.getBytes(0, bytes);
                                final String content = new String(bytes, CharsetUtil.UTF_8);
                                assertions.add(() -> {
                                    assertEquals("Hey you, This is a test", content);
                        }); break;
                            case HeadersReceived:
                                xheader[0] = ((HttpResponse) state.get()).headers().get("X-foo");
                                break;
                            default:
                                break;
                        }
                    }
                }).execute();
        f.await(5, TimeUnit.SECONDS).throwIfError();
        server.throwLast();
        assertions.exec();
        assertTrue(stateTypes.contains(StateType.Connected));
        assertTrue(stateTypes.contains(StateType.SendRequest));
        assertTrue(stateTypes.contains(StateType.Connecting));
        assertTrue(stateTypes.contains(StateType.ContentReceived));
        assertTrue(stateTypes.contains(StateType.FullContentReceived));
        assertTrue(stateTypes.contains(StateType.HeadersReceived));
        assertFalse(stateTypes.contains(StateType.Cancelled));
        assertFalse(stateTypes.contains(StateType.Error));
        assertEquals("bar", xheader[0]);
    }

    private static class AM implements ActivityMonitor {

        final List<String> started = Lists.newCopyOnWriteArrayList();
        final List<String> ended = Lists.newCopyOnWriteArrayList();

        @Override
        public void onStartRequest(URL url) {
            started.add(url.toString());
        }

        @Override
        public void onEndRequest(URL url) {
            ended.add(url.toString());
        }

    }

    @Test
    public void test() throws Throwable {
        final CookieStore store = new CookieStore();
        final String[] contents = new String[1];
        ResponseFuture h = client.get().setCookieStore(store).setURL(URL.parse("https://test.x:" + server.httpsPort()))
                .execute(new ResponseHandler<String>(String.class) {

                    @Override
                    protected void receive(HttpResponseStatus status, HttpHeaders headers, String obj) {
                        contents[0] = obj;
                    }

                });
        final AtomicInteger chunkCount = new AtomicInteger();
        final List<String> chunks = new ArrayList<>();
        h.onAnyEvent(new Receiver<State<?>>() {
            Set<StateType> seen = new HashSet<>();

            @Override
            public void receive(State<?> state) {
                seen.add(state.stateType());
                if (state.get() instanceof HttpContent) {
                    HttpContent content = (HttpContent) state.get();
                    ByteBuf bb = content.content();
                    byte[] bytes = new byte[bb.readableBytes()];
                    bb.getBytes(0, bytes);
                    String s = new String(bytes, CharsetUtil.UTF_8);
                    chunks.add(s);
                    chunkCount.incrementAndGet();
                }
            }
        });
        h.await(5, TimeUnit.SECONDS).throwIfError();
        assertEquals(7, chunkCount.get()); //6 including empty last chunk
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            assertEquals("CHUNK-" + (i + 1) + "\n", chunks.get(i));
            sb.append(chunks.get(i));
        }
        assertEquals("skiddoo", store.get("twentythree"));
        assertEquals(sb.toString(), contents[0]);
    }

    private class ResponderImpl implements Responder {

        @Override
        public Object receive(HttpRequest req, ResponseHead response) throws Exception {
            switch (req.headers().get(HttpHeaderNames.HOST)) {
                case "test.x":
                    return cookieResponse(req, response);
                case "foo.bar":
                    return postResponse(req, response);
                case "cancel.me":
                    return cancelResponse(req, response);
                case "redirect.me":
                    return redirectForever(req, response);
                case "messy.re":
                    return relativeRedirect(req, response);
                case "chunky.thing":
                    return chunkedPut(req, response);
                default:
                    throw new AssertionError("Unknown host header: " + req.headers().get(HttpHeaderNames.HOST));
            }

        }

        private Object cookieResponse(HttpRequest req, ResponseHead response) throws Exception {
            DefaultCookie cookie = new DefaultCookie("twentythree", "skiddoo");
            cookie.setDomain("test.x");
            cookie.setMaxAge(100);
            cookie.setPath("/");
            String cookieValue = ServerCookieEncoder.STRICT.encode(cookie);
            response.header(HttpHeaderNames.SET_COOKIE).set(cookieValue);
            return new ChunkedResponse() {
                @Override
                public Object nextChunk(int callCount) {
                    if (callCount > 5) {
                        return null;
                    }
                    return "CHUNK-" + callCount + "\n";
                }
            };
        }

        private Object postResponse(HttpRequest req, ResponseHead response) throws Exception {
            if (!(req instanceof FullHttpRequest)) {
                throw new IllegalStateException("Wrong type: " + req.getClass().getName() + " - " + req);
            }
            response.header("X-foo").set("bar");
            FullHttpRequest hreq = (FullHttpRequest) req;
            ByteBuf buf = hreq.content();
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(0, bytes);
            String body = new String(bytes, CharsetUtil.UTF_8);
            return "Hey you, " + body;
        }

        private Object chunkedPut(HttpRequest req, ResponseHead response) throws Exception {
            if (!(req instanceof FullHttpRequest)) {
                throw new IllegalStateException("Wrong type: " + req.getClass().getName() + " - " + req);
            }
            FullHttpRequest hreq = (FullHttpRequest) req;
            response.header(CONTENT_TYPE).set("text/plain");
            ByteBuf buf = hreq.content();
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(0, bytes);
            String body = new String(bytes, CharsetUtil.UTF_8);
            response.status(CREATED);
            return "You sent:\n" + body;
        }

        private Object cancelResponse(HttpRequest req, ResponseHead response) throws Exception {
            Thread.sleep(30);
            response.status(HttpResponseStatus.BAD_REQUEST);
            return "Should not be sent";
        }

        private int count = 1;

        private Object redirectForever(HttpRequest req, ResponseHead response) {
            response.status(HttpResponseStatus.MOVED_PERMANENTLY);
            response.header("Location").set("http://redirect.me:" + server.httpPort() + req.uri() + "/" + count++);
            return "Hello!";
        }

        private Object relativeRedirect(HttpRequest req, ResponseHead response) {
            if (req.uri().equals("") || req.uri().equals("/")) {
                response.status(HttpResponseStatus.MOVED_PERMANENTLY);
                response.header("Location").set("/foo/bar");
            }
            return "Boo";
        }
    }
}
