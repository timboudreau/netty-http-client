/*
 * The MIT License
 *
 * Copyright 2014 tim.
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
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.giulius.DependenciesBuilder;
import com.timboudreau.netty.http.client.tests.TestModule.App;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import javax.inject.Inject;

/**
 * A test application
 *
 * @author Tim Boudreau
 */
public class TestModule extends ServerModule<App> {

    public TestModule() {
        super(App.class);
    }

    static class App extends Application {

        public App() {
            add(OkPage.class);
            add(CookiePage.class);
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
            Cookie[] cookies = evt.getHeader(Headers.COOKIE);
            if (cookies != null) {
                for (Cookie ck : cookies) {
                    if ("xid".equals(ck.getName())) {
                        String val = ck.getValue();
                        int ival = Integer.parseInt(val) + 1;
                        value = Integer.toString(ival);
                    }
                }
            }
            DefaultCookie ck = new DefaultCookie("xid", value);
            ck.setMaxAge(500);
            ck.setPath("/ok");
            add(Headers.SET_COOKIE, ck);
            setState(new ConsumedLockedState());
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
            add(Headers.SET_COOKIE, ck);
            ok(value);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        new DependenciesBuilder().add(new TestModule()).build().getInstance(Server.class).start().await();
    }
}
