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

import com.mastfrog.util.Exceptions;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

/**
 *
 * @author Tim Boudreau
 */
final class NioChannelFactory implements ChannelFactory<NioSocketChannel> {

    private final boolean debug;
    NioChannelFactory(boolean debug) {
        this.debug = debug;
    }

    @Override
    public NioSocketChannel newChannel() {
        try {
            return debug ? new DebugNioSocketChannel(SocketChannel.open()) : new NioSocketChannel(SocketChannel.open());
        } catch (IOException ioe) {
            return Exceptions.chuck(ioe);
        }
    }

    public Channel newChannel(EventLoop eventLoop) {
        return newChannel();
    }

    private static final class DebugNioSocketChannel extends NioSocketChannel {

        public DebugNioSocketChannel() {
        }

        public DebugNioSocketChannel(SelectorProvider provider) {
            super(provider);
        }

        public DebugNioSocketChannel(SocketChannel socket) {
            super(socket);
        }

        public DebugNioSocketChannel(Channel parent, SocketChannel socket) {
            super(parent, socket);
        }

        @Override
        protected void doClose() throws Exception {
            new Exception("client doClose").printStackTrace();
            super.doClose(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected void doDeregister() throws Exception {
            new Exception("client doDeregister").printStackTrace();
            super.doDeregister(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            new Exception("client Explicit close w/ promise").printStackTrace();
            return super.close(promise); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public ChannelFuture close() {
            new Exception("client Explicit close").printStackTrace();
            return super.close(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public ChannelFuture disconnect() {
            new Exception("client Explicit disconnect").printStackTrace();
            return super.disconnect(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected void doDisconnect() throws Exception {
            new Exception("client doDisconnect").printStackTrace();
            super.doDisconnect(); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
