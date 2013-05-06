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
import com.mastfrog.url.URL;
import com.mastfrog.util.thread.Receiver;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author tim
 */
public class HttpClientTest {
    
    @Test
    public void testPost() throws Exception {
        HttpClient client = HttpClient.builder().followRedirects().build();
        ResponseFuture f = client.get()
                .setURL("http://localhost:9333/foo/bar")
                .setBody("This is a test", MediaType.PLAIN_TEXT_UTF_8)
                .onEvent(new Receiver<State<?>>() {

            @Override
            public void receive(State<?> object) {
                System.out.println("STATE " + object + " " + object.name() + " " + object.get());
                if (object.get() instanceof DefaultFullHttpRequest) {
                    DefaultFullHttpRequest d = (DefaultFullHttpRequest) object.get();
                    System.out.println("REQ HEADERS:");
                    for (Map.Entry<String,String> e : d.headers().entries()) {
                        System.out.println(e.getKey() + ": " + e.getValue());
                    }
                }
            }
            
        }).execute();
        f.await(5, TimeUnit.SECONDS);
    }

    @Test
    public void test() throws Exception {
        if (true) return;
        HttpClient client = HttpClient.builder().build();
//        ResponseFuture h = client.get().setURL(URL.parse("https://timboudreau.com/")).execute(new ResponseHandler<String>(String.class){
//        ResponseFuture h = client.get().setURL(URL.parse("http://timboudreau.com/files/INTRNET2.TXT")).execute(new ResponseHandler<String>(String.class){
//        ResponseFuture h = client.get().setURL(URL.parse("http://mail-vm.timboudreau.org/blog/api-list")).execute(new ResponseHandler<String>(String.class) {
//        ResponseFuture h = client.get().setURL(URL.parse("http://mail-vm.timboudreau.org")).execute(new ResponseHandler<String>(String.class){
//        ResponseFuture h = client.get().setURL(URL.parse("http://www.google.com")).execute(new ResponseHandler<String>(String.class){
        ResponseFuture h = client.get().setURL(URL.parse("http://mail-vm.timboudreau.org/blog/latest/read")).execute(new ResponseHandler<String>(String.class){

            @Override
            protected void receive(HttpResponseStatus status, HttpHeaders headers, String obj) {
                System.out.println("CALLED BACK WITH '" + obj + "'");
            }

        });
        h.on(State.HeadersReceived.class, new Receiver<HttpResponse>() {

            @Override
            public void receive(HttpResponse object) {
                for (Map.Entry<String, String> e : object.headers().entries()) {
                    System.out.println(e.getKey() + ": " + e.getValue());
                }
            }
        });

        h.onAnyEvent(new Receiver<State<?>>() {

            @Override
            public void receive(State<?> state) {
                if (state.get() instanceof HttpContent) {
                    HttpContent content = (HttpContent) state.get();
                    ByteBuf bb = content.copy().content();
                    System.out.println("CHUNK " + bb.readableBytes() + " bytes");
                } else if (state.get() instanceof HttpResponse) {
//                    System.out.println("HEADERS: " + ((HttpResponse) state.get()).headers());
//                    for (Map.Entry<String,String> e : ((HttpResponse) state.get()).headers().entries()) {
//                        System.out.println(e.getKey() + ": " + e.getValue());
//                    }
                }
            }
        });

        h.await();
        Thread.sleep(500);
    }
}
