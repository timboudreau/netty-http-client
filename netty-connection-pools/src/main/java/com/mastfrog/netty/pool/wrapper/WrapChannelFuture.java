/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.netty.pool.wrapper;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Wraps a ChannelFuture to provide a WrapperChannel instead of the underlying
 * channel.
 *
 * @author Tim Boudreau
 */
public class WrapChannelFuture implements ChannelFuture, ChannelFutureListener {
    private final Channel channel;
    private final ChannelFuture real;
    private final ListenerSupport<Void> listeners = new ListenerSupport<>();

    public WrapChannelFuture(Channel channel, ChannelFuture real) {
        this.channel = channel;
        this.real = real;
        real.addListener(this);
    }

    @Override
    public Channel channel() {
        return channel;
    }

    public void operationComplete(ChannelFuture future) {
        listeners.operationComplete(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        this.listeners.addListeners(listeners);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        this.listeners.removeListeners(listener);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        this.listeners.removeListeners(listeners);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        this.listeners.addListeners(listener);
        return this;
    }

    @Override
    public ChannelFuture sync() throws InterruptedException {
        return new WrapChannelFuture(channel, real.sync());
    }

    @Override
    public ChannelFuture syncUninterruptibly() {
        return new WrapChannelFuture(channel, real.syncUninterruptibly());
    }

    @Override
    public ChannelFuture await() throws InterruptedException {
        return new WrapChannelFuture(channel, real.await());
    }

    @Override
    public ChannelFuture awaitUninterruptibly() {
        return new WrapChannelFuture(channel, real.awaitUninterruptibly());
    }

    @Override
    public boolean isSuccess() {
        return real.isSuccess();
    }

    @Override
    public boolean isCancellable() {
        return real.isCancellable();
    }

    @Override
    public Throwable cause() {
        return real.cause();
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return real.await(timeout, unit);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return real.await(timeoutMillis);
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return real.awaitUninterruptibly(timeout, unit);
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return real.awaitUninterruptibly(timeoutMillis);
    }

    @Override
    public Void getNow() {
        return real.getNow();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return real.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return real.isCancelled();
    }

    @Override
    public boolean isDone() {
        return real.isDone();
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
        return real.get();
    }

    @Override
    public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return real.get(timeout, unit);
    }
}
