/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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

import com.google.common.collect.Sets;
import com.mastfrog.util.thread.Receiver;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joda.time.Duration;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class ConnectionRefusedTest {

    @Test
    public void testTimeout() throws Throwable {
        final CountDownLatch latch = new CountDownLatch(2);
        HttpClient client = HttpClient.builder().setTimeout(Duration.standardSeconds(3)).build();
        final AtomicBoolean notified = new AtomicBoolean();
        final Set<StateType> states = Sets.newConcurrentHashSet();
        client.get().setTimeout(new Duration(2)).setURL("http://10.0.0.254:3720/abcd")
                .onEvent(new Receiver<State<?>>() {

                    @Override
                    public void receive(State<?> object) {
                        states.add(object.stateType());
                        if (object.stateType() == StateType.Timeout) {
                            latch.countDown();
                        }
//                        System.out.println("1STATE " + object);
                        
                    }
                }).execute(new ResponseHandler<Object>(Object.class) {

            @Override
            protected void receive(Object obj) {
                notified.set(true);
                latch.countDown();
            }

            @Override
            protected void onError(Throwable err) {
                notified.set(true);
                latch.countDown();
            }

        }).await(10, TimeUnit.SECONDS);
        latch.await(5, TimeUnit.SECONDS);
        assertTrue(notified.get());
        assertTrue(states.contains(StateType.Timeout));
    }

    @Test
    public void test() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.builder().setTimeout(Duration.standardSeconds(3)).build();
        final AtomicBoolean notified = new AtomicBoolean();
        client.get().setURL("http://192.168.1.254:10001/abcd")
                .onEvent(new Receiver<State<?>>() {

                    @Override
                    public void receive(State<?> object) {
//                        System.out.println("2STATE " + object);
                    }
                })
                .execute(new ResponseHandler<Object>(Object.class) {

                    @Override
                    protected void onError(Throwable err) {
//                        err.printStackTrace();
                        notified.set(true);
                        latch.countDown();
                    }

                    @Override
                    protected void onErrorResponse(HttpResponseStatus status, HttpHeaders headers, String content) {
                        System.out.println("onErrorResponse " + status + " - " + content);
                    }

                    @Override
                    protected void receive(Object obj) {
                        System.out.println("RECEIVE " + obj);
                    }

                    @Override
                    protected void receive(HttpResponseStatus status, HttpHeaders headers, Object obj) {
                        System.out.println("Receive " + status + " " + obj);
                    }

                    @Override
                    protected void receive(HttpResponseStatus status, Object obj) {
                    }
                }).await(1, TimeUnit.SECONDS);
        latch.await(20, TimeUnit.SECONDS);
        if (!notified.get()) {
            Thread.sleep(5000);
        }
        assertTrue(notified.get());
    }
}
