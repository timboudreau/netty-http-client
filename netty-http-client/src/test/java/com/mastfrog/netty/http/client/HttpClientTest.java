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
import com.google.common.net.MediaType;
import com.mastfrog.url.URL;
import com.mastfrog.util.thread.Receiver;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author tim
 */
public class HttpClientTest {
    
    @Test
    public void testPost() throws Exception {
        if (true) return;
        HttpClient client = HttpClient.builder().followRedirects().build();
        final AM am = new AM();
        client.addActivityMonitor(am);
        ResponseFuture f = client.get()
                .setURL("http://localhost:9333/foo/bar")
                .setBody("This is a test", MediaType.PLAIN_TEXT_UTF_8)
                .onEvent(new Receiver<State<?>>() {
            public void receive(State<?> state) {
                System.out.println("STATE " + state + " " + state.name() + " " + state.get());
                if (state.stateType() == StateType.Finished) {
                    DefaultFullHttpRequest d = (DefaultFullHttpRequest) state.get();
                    System.out.println("REQ HEADERS:");
                    for (Map.Entry<CharSequence,CharSequence> e : d.headers().entries()) {
                        System.out.println(e.getKey() + ": " + e.getValue());
                    }
                    assertTrue(am.started.contains("http://localhost:9333/foo/bar"));
                    assertTrue(am.ended.contains("http://localhost:9333/foo/bar"));
                }
            }
        }).execute();
        f.await(5, TimeUnit.SECONDS);
    }
    
    private static class AM implements ActivityMonitor {
        final List<String> started = Lists.newCopyOnWriteArrayList();
        final List<String> ended = Lists.newCopyOnWriteArrayList();

        @Override
        public void onStartRequest(URL url) {
            System.out.println("AM START: " + url);
            started.add(url.toString());
        }

        @Override
        public void onEndRequest(URL url) {
            System.out.println("AM END: " + url);
            ended.add(url.toString());
        }
        
    }

    @Test
    public void test() throws Exception {
//        if (true) return;
        HttpClient client = HttpClient.builder().build();
        final CookieStore store = new CookieStore();
//        ResponseFuture h = client.get()setCookieStore(store).setURL(URL.parse("https://timboudreau.com/")).execute(new ResponseHandler<String>(String.class) {
//        ResponseFuture h = client.get().setURL(URL.parse("http://timboudreau.com/files/INTRNET2.TXT")).execute(new ResponseHandler<String>(String.class){
//        ResponseFuture h = client.get().setURL(URL.parse("http://mail-vm.timboudreau.org/blog/api-list")).execute(new ResponseHandler<String>(String.class) {
//        ResponseFuture h = client.get().setURL(URL.parse("http://mail-vm.timboudreau.org")).execute(new ResponseHandler<String>(String.class){
//        ResponseFuture h = client.get().setURL(URL.parse("http://www.google.com")).execute(new ResponseHandler<String>(String.class){
//        ResponseFuture h = client.get().setCookieStore(store).setURL(URL.parse("http://hp.timboudreau.org/blog/latest/read")).execute(new ResponseHandler<String>(String.class){
        ResponseFuture h = client.get().setCookieStore(store).setURL(URL.parse("https://timboudreau.com/")).execute(new ResponseHandler<String>(String.class){

            @Override
            protected void receive(HttpResponseStatus status, HttpHeaders headers, String obj) {
                System.out.println("CALLED BACK WITH '" + obj + "'");
            }
            
        });
        h.on(State.HeadersReceived.class, new Receiver<HttpResponse>() {

            @Override
            public void receive(HttpResponse object) {
                for (Map.Entry<CharSequence, CharSequence> e : object.headers().entries()) {
                    System.out.println(e.getKey() + ": " + e.getValue());
                }
                System.out.println("COOKIES: " + store);
            }
        });

        h.onAnyEvent(new Receiver<State<?>>() {
            Set<StateType> seen = new HashSet<>();
            
            @Override
            public void receive(State<?> state) {
                System.out.println("STATE " + state);
                seen.add(state.stateType());
                if (state.get() instanceof HttpContent) {
                    HttpContent content = (HttpContent) state.get();
                    ByteBuf bb = content.copy().content();
                    System.out.println("CHUNK " + bb.readableBytes() + " bytes");
                } else if (state.get() instanceof HttpResponse) {
//                    System.out.println("HEADERS: " + ((HttpResponse) state.get()).headers());
//                    for (Map.Entry<String,String> e : ((HttpResponse) state.get()).headers().entries()) {
//                        System.out.println(e.getKey() + ": " + e.getValue());
//                    }
                } else if (state.get() instanceof State.FullContentReceived) {
                    System.out.println("COOKIES: " + store);
                }
            }
        });

        h.await();
        Thread.sleep(500);
    }
}
