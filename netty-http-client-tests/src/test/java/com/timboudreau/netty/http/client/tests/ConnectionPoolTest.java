/*
 * The MIT License
 *
 * Copyright 2015 tim.
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
import com.mastfrog.acteur.util.Connection;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.netty.http.client.State;
import com.mastfrog.netty.http.test.harness.TestHarness;
import com.mastfrog.netty.http.test.harness.TestHarnessModule;
import com.mastfrog.util.thread.Receiver;
import com.timboudreau.netty.http.client.tests.ConnectionPoolTest.*;
import io.netty.channel.Channel;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that things work right with Netty's new connection pools in 4.0.26.
 *
 * @author Tim Boudreau
 */
@TestWith(value = {TestModule.class}, iterate = {ThreePoolModule.class /*, UnlimitedPoolModule.class, NoPoolModule.class */})
@RunWith(GuiceRunner.class)
public class ConnectionPoolTest {

    @Test(timeout = 15000)
    public void test(TestHarness harn) throws Throwable {
        harn.get("redirDone").addHeader(Headers.CONNECTION, Connection.keep_alive).log().go().assertStatus(OK).assertContent("Got it\n");
        Thread.sleep(200);
        harn.get("redirDone").addHeader(Headers.CONNECTION, Connection.keep_alive).on(State.Connected.class, new Receiver<Channel>() {

            @Override
            public void receive(Channel object) {
                System.out.println("CHANNEL IS A " + object.getClass().getName());
            }

        }).log().go().assertStatus(OK).assertContent("Got it\n");
    }

    static class UnlimitedPoolModule extends TestHarnessModule {

        public UnlimitedPoolModule() {
            super(HttpClient.builder().useUnlimitedConnectionPool());
        }
    }

    static class ThreePoolModule extends TestHarnessModule {

        public ThreePoolModule() {
            super(HttpClient.builder().setConnectionPoolSize(3));
        }
    }

    static class NoPoolModule extends TestHarnessModule {

        public NoPoolModule() {
            super(HttpClient.builder().dontUseConnectionPool());
        }
    }
}
