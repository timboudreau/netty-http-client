/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau
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
package com.timboudreau.netty.http.client.tests;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.giulius.DependenciesBuilder;
import com.mastfrog.util.Strings;
import com.timboudreau.netty.http.client.tests.TestModule.App;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.FOUND;
import io.netty.handler.codec.http.LastHttpContent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;
import org.joda.time.Duration;

/**
 * A test application
 *
 * @author Tim Boudreau
 */
public class TestModule extends ServerModule<App> {

    static {
        System.setProperty("acteur.debug", "true");
        System.setProperty("internal.gzip", "true");
    }

    public TestModule() {
        super(App.class);
    }

    static class App extends Application {

        public App() {
            add(OkPage.class);
            add(CompressedPage.class);
            add(CookiePage.class);
            add(IncrementalPage.class);
            add(RedirectPage.class);
            add(RedirectPage2.class);
            add(RedirectDest.class);
        }
    }

    @Path("/ok")
    @Methods(Method.GET)
    static class OkPage extends Page {

        @Inject
        OkPage(ActeurFactory af) {
            add(IncrementalCookie.class);
            add(af.respondWith(HttpResponseStatus.OK, "Okey dokey"));
        }
    }

    static class IncrementalCookie extends Acteur {

        @Inject
        IncrementalCookie(HttpEvent evt) {
            String value = "1";
            Cookie cookie = null;
            Cookie[] cookies = evt.getHeader(Headers.COOKIE_B);
            if (cookies != null) {
                for (Cookie ck : cookies) {
                    if ("xid".equals(ck.name())) {
                        String val = ck.value();
                        int ival = Integer.parseInt(val) + 1;
                        value = Integer.toString(ival);
                    }
                }
            }
            DefaultCookie ck = new DefaultCookie("xid", value);
            ck.setMaxAge(500);
            ck.setPath("/ok");
            add(Headers.SET_COOKIE_B, ck);
            next();
        }
    }

    @Methods(Method.GET)
    @Path("/cookie")
    static class CookiePage extends Page {

        CookiePage() {
            add(ParamsCookie.class);
        }
    }

    static class ParamsCookie extends Acteur {

        @Inject
        ParamsCookie(HttpEvent evt) {
            String key = evt.getParameter("key");
            String value = evt.getParameter("value");
            if (key == null || value == null) {
                setState(new RespondWith(Err.badRequest("Missing params")));
                return;
            }
            DefaultCookie ck = new DefaultCookie(key, value);
            ck.setMaxAge(500);
            ck.setPath(evt.getPath().toStringWithLeadingSlash());
            add(Headers.SET_COOKIE_B, ck);
            ok(value);
        }
    }

    @Methods(Method.GET)
    @Path("/incremental")
    static class IncrementalPage extends Page {

        IncrementalPage() {
            add(IncrementalActeur.class);
        }
    }

    static class IncrementalActeur extends Acteur implements ChannelFutureListener {

        private volatile int callCount;

        IncrementalActeur() {
            ok();
            setChunked(true);
            setResponseBodyWriter(this);
        }

        @Override
        public void operationComplete(ChannelFuture f) throws Exception {
            int count = callCount++;
            if (count == 5) {
                f.channel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                return;
            }
            ByteBuf buf = f.channel().alloc().buffer(50);
            ByteBufUtil.writeAscii(buf, "This is call " + count);
            f.channel().writeAndFlush(new DefaultHttpContent(buf)).addListener(this);
        }
    }

    @Methods(Method.GET)
    @Path("/redir")
    static class RedirectPage extends Page {

        RedirectPage() {
            add(DelayActeur.class);
            add(RedirActeur.class);
        }
    }

    @Methods(Method.GET)
    @Path("/redir2")
    static class RedirectPage2 extends Page {

        RedirectPage2() {
            add(DelayActeur.class);
            add(RedirActeur2.class);
        }
    }

    @Methods(Method.GET)
    @Path("/redirDone")
    static class RedirectDest extends Page {

        RedirectDest() {
            add(FinalResponseActeur.class);
        }
    }

    static class DelayActeur extends Acteur {

        DelayActeur() {
            response().setDelay(Duration.standardSeconds(1));
            next();
        }
    }

    static class RedirActeur extends Acteur {

        @Inject
        RedirActeur(PathFactory pf) throws URISyntaxException {
            add(Headers.LOCATION, new URI("/redir2"));
            reply(FOUND);
        }
    }

    static class RedirActeur2 extends Acteur {

        @Inject
        RedirActeur2(PathFactory pf) throws URISyntaxException {
            add(Headers.LOCATION, new URI("/redirDone"));
            reply(FOUND);
        }
    }

    static class FinalResponseActeur extends Acteur {

        FinalResponseActeur() {
            ok("Got it\n");
        }
    }

    @Methods(GET)
    @Path("/comp")
    static class CompressedPage extends Page {

        CompressedPage() {
            add(CompressedActeur.class);
        }
    }

    static class CompressedActeur extends Acteur {

        private byte[] bytes;

        @Inject
        public CompressedActeur(HttpEvent evt) throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 100; i++) {
                sb.append("Test-").append(i).append('\n');
            }
            CharSequence accepted = evt.getHeader(Headers.ACCEPT_ENCODING);
            if (accepted == null || !Strings.charSequenceContains(accepted, HttpHeaderValues.GZIP, true)) {
                reply(HttpResponseStatus.BAD_REQUEST, "No Accept-Encoding header - client not "
                        + "configured for compression");
                return;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                try (PrintStream ps = new PrintStream(gzip)) {
                    ps.println(sb);
                }
            }
            add(Headers.header("X-Internal-Compress"), "true");
            add(Headers.CONTENT_ENCODING, "gzip");
            bytes = baos.toByteArray();
            ok(Unpooled.wrappedBuffer(bytes));
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        new DependenciesBuilder().add(new TestModule()).build().getInstance(Server.class).start().await();
    }
}
