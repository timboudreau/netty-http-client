package com.mastfrog.netty.http.client;

import com.google.common.collect.Sets;
import com.mastfrog.tiny.http.server.Responder;
import com.mastfrog.tiny.http.server.ResponseHead;
import com.mastfrog.tiny.http.server.TinyHttpServer;
import com.mastfrog.url.URL;
import com.mastfrog.util.thread.Receiver;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.security.cert.CertificateException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLException;
import org.junit.After;
import static org.junit.Assert.assertEquals;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;

/**
 * Created by vitaliy.kuzmich on 5/16/17.
 */
public class RedirectLoopTest {

    TinyHttpServer server;
    HttpClient client;
    ResponderImpl responder;

    @Before
    public void setup() throws CertificateException, SSLException, InterruptedException {
        server = new TinyHttpServer(responder = new ResponderImpl());
        client = HttpClient.builder().resolver(new LocalhostOnlyAddressResolverGroup()).followRedirects().build();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
        client.shutdown();
    }

    @Test
    public void testRedirect() throws Exception {
        final AtomicReference<String> content = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final Set<URL> redirects = Sets.newConcurrentHashSet();
        URL first = URL.parse("http://bar.com:" + server.httpPort());
        URL second = URL.parse("https://baz.com:" + server.httpsPort() + "/foo/bar/baz");
        client.get().setURL("http://foo.com:" + server.httpPort())
                .on(State.Redirect.class, new Receiver<URL>() {
                    @Override
                    public void receive(URL url) {
                        redirects.add(url);
                    }
                })
                .execute(new ResponseHandler<String>(String.class) {
                    @Override
                    protected void receive(String obj) {
                        content.set(obj);
                        latch.countDown();
                    }
                }).await(10, TimeUnit.SECONDS);
        latch.await(10, TimeUnit.SECONDS);
        server.throwLast();
        assertNotNull(content.get());
        assertEquals("Woo hoo", content.get());
        assertTrue(redirects + " does not contain " + second, redirects.contains(second));
        assertTrue(redirects + "  does not contain " + first, redirects.contains(first));
    }

    private final class ResponderImpl implements Responder {

        @Override
        public Object receive(HttpRequest req, ResponseHead response) throws Exception {
            String hostHeader = req.headers().get("Host");
            assertNotNull("Host is null for " + req.uri() + " with " + req.headers(), hostHeader);
            switch (hostHeader) {
                case "foo.com":
                    response.header("Location").set("http://bar.com:" + server.httpPort());
                    response.status(HttpResponseStatus.FOUND);
                    return "Redirect to bar.com\n";
                case "bar.com":
                    response.header("Location").set("https://baz.com:" + server.httpsPort() + "/foo/bar/baz");
                    response.status(HttpResponseStatus.TEMPORARY_REDIRECT);
                    return "Redirect with https to baz.com";
                case "baz.com":
                    response.header("Content-Type").set("text/plain;charset=UTF-8");
                    return "Woo hoo";
                default:
                    throw new AssertionError(req + " " + req.headers().get("Host"));
            }
        }
    }
}
