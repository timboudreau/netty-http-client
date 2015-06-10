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
package com.mastfrog.netty.pool;

import com.mastfrog.netty.pool.hacks.ChannelPoolChannelInitializer;
import com.mastfrog.netty.pool.multi.ChannelPoolFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

/**
 * A channel pool which does not actually pool channels, but simply closes them
 * on return to pool.
 *
 * @author Tim Boudreau
 */
public final class NullChannelPool implements ChannelPool {

    private final ChannelPoolChannelInitializer initializer;
    private final Bootstrap bootstrap;
    private final ChannelPoolFactory executorProvider;
    private final EventLoop executor;

    public NullChannelPool(ChannelPoolChannelInitializer initializer, Bootstrap bootstrap, ChannelPoolFactory executorProvider) {
        this.initializer = initializer;
        this.bootstrap = bootstrap;
        this.executorProvider = executorProvider;
        this.executor = bootstrap.group().next();
    }

    @Override
    public Future<Channel> acquire() {
        return acquire(null);
    }

    @Override
    public Future<Channel> acquire(final Promise<Channel> promise) {
        System.out.println("DummyChannelPool acquire " + initializer);
        final Promise<Channel> result = executor.newPromise();
        ChannelFuture fut = initializer.connect(bootstrap);
        fut.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                System.out.println("CFL acquire complete: " + future.isSuccess());
                if (future.isSuccess()) {
                    result.trySuccess(future.channel());
                    if (promise != null) {
                        promise.trySuccess(future.channel());
                    }
                } else {
                    if (future.cause() != null) {
                        future.cause().printStackTrace();
                    }
                    result.setFailure(future.cause());
                    if (promise != null) {
                        promise.tryFailure(future.cause());
                    }
                }
            }
        });
        return promise != null ? promise : result;
    }

    @Override
    public Future<Void> release(Channel channel) {
        return channel.close();
    }

    @Override
    public Future<Void> release(Channel channel, final Promise<Void> promise) {
        channel.close().addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                promise.setSuccess(null);
            }
        });
        return promise;
    }

    @Override
    public void close() {
        //do nothing
    }
}
