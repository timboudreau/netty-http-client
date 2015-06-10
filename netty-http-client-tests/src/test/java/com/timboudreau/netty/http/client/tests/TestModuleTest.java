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

import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.client.CookieStore;
import com.mastfrog.netty.http.client.StateType;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarness.CallResult;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.util.Streams;
import com.mastfrog.util.thread.Receiver;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.handler.codec.http.LastHttpContent;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

/**
 * Tests of basic test harness functionality here, so we can depend on both
 * Acteur and Netty Http Test Harness without creating a circular dependency.
 *
 * @author Tim Boudreau
 */
@TestWith({TestHarnessModule.class, TestModule.class})
@RunWith(GuiceRunner.class)
public class TestModuleTest {

    @Test
    public void test(TestHarness harn) throws Throwable {
        CookieStore store = new CookieStore();
        CallResult res = harn.get("/ok").setCookieStore(store).go().assertStatus(OK);
        res.assertHasHeader(Headers.SET_COOKIE.name());
        assertTrue(store.iterator().hasNext());
        res.assertCookieValue("xid", "1").throwIfError();

        res = harn.get("/ok").setCookieStore(store).go().assertStatus(OK);
        res.assertCookieValue("xid", "2");
        res.throwIfError();
        assertEquals(store + " " + store.size(), 1, store.size());
        Iterator<Cookie> iter = store.iterator();
        assertTrue(store + "", iter.hasNext());
        iter.next();
        assertFalse(store + "", iter.hasNext());

        res = harn.get("/cookie").setCookieStore(store).addQueryPair("key", "foo")
                .addQueryPair("value", "bar").go()
                .assertStatus(OK)
                .assertCookieValue("foo", "bar");

        res = harn.get("/cookie").setCookieStore(store).addQueryPair("key", "wump")
                .addQueryPair("value", "baz").go()
                .assertStatus(OK)
                .assertCookieValue("wump", "baz");

        assertEquals(store + "", "baz", store.get("wump"));
        assertEquals(store + "", "bar", store.get("foo"));

        R r = new R();
        Resp resp = new Resp();
        FullResp full = new FullResp();
        harn.get("incremental")
                .dontAggregateResponse()
                .on(StateType.ContentReceived, r)
                .on(StateType.FullContentReceived, full)
                .on(StateType.HeadersReceived, resp)
                .execute();
        synchronized (r) {
            r.wait(2000);
        }
        assertTrue(resp.called);
        assertEquals(5, r.found.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("This is call " + i, r.found.get(i));
        }
        Thread.sleep(2000);
        assertFalse(full.called);
    }

    static class R extends Receiver<HttpContent> {

        private final List<String> found = Collections.<String>synchronizedList(new LinkedList<String>());

        @Override
        public void receive(HttpContent object) {
            if (object instanceof LastHttpContent) {
                synchronized (this) {
                    notifyAll();
                    return;
                }
            }
            String content;
            try (ByteBufInputStream in = new ByteBufInputStream(object.content())) {
                content = Streams.readString(in);
                found.add(content);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class Resp extends Receiver<HttpResponse> {

        volatile boolean called;

        @Override
        public void receive(HttpResponse object) {
            called = true;
        }
    }

    static class FullResp extends Receiver<FullHttpResponse> {

        volatile boolean called;

        @Override
        public void receive(FullHttpResponse object) {
            called = true;
        }
    }

}
