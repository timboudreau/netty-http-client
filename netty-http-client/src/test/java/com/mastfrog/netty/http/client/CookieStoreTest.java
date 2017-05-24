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
package com.mastfrog.netty.http.client;

import com.mastfrog.acteur.headers.Headers;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author tim
 */
public class CookieStoreTest {

    @Test
    public void test() throws IOException {
        assertTrue(true);
        CookieStore store = new CookieStore();
        DefaultCookie ck1 = new DefaultCookie("foo", "bar");
        DefaultCookie ck2 = new DefaultCookie("one", "two");
        ck1.setPath("/foo");
        ck1.setDomain("foo.com");
        ck1.setMaxAge(10000);

        ck2.setPath("/foo");
        ck2.setDomain("foo.com");
        ck2.setMaxAge(10000);

        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        resp.headers().add(Headers.SET_COOKIE_B.name(), Headers.SET_COOKIE_B.toString(ck1));
        resp.headers().add(Headers.SET_COOKIE_B.name(), Headers.SET_COOKIE_B.toString(ck2));

        store.extract(resp.headers());
        Iterator<Cookie> iter = store.iterator();
        assertTrue(iter.hasNext());
        assertTrue(iter.hasNext());

        HttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo/bar");
        req.headers().add(Headers.HOST.name(), "foo.com");
        store.decorate(req);

        List<String> cookieHeaders = req.headers().getAll(Headers.COOKIE_B.name());
        assertEquals(2, cookieHeaders.size());

        List<String> find = new LinkedList<>(Arrays.asList("foo", "one"));
        for (String hdr : cookieHeaders) {
            Cookie cookie = Headers.SET_COOKIE_B.toValue(hdr);
            find.remove(cookie.name());
        }
        assertTrue("Not found: " + find, find.isEmpty());

        CookieStore nue = new CookieStore(store.cookies, true, true);
        assertEquals(store, nue);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        store.store(out);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        nue = new CookieStore();
        nue.read(in);
        assertEquals(store, nue);

        DefaultCookie ck3 = new DefaultCookie("fuz", "bang");
        ck3.setMaxAge(20000);
        ck3.setPath("/moo/wuzz");
        ck3.setDomain("foo.com");
        nue.add(ck3);
        assertNotEquals(store, nue);
    }
}
