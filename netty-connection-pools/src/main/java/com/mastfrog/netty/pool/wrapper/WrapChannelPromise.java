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
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Wraps a ChannelPromise and provides an alternate Channel.
 *
 * @author Tim Boudreau
 */
public class WrapChannelPromise<T extends ChannelPromise> implements ChannelPromise, GenericFutureListener<Future<? super Void>> {
    private final Channel channel;
    protected final T realPromise;
    private final ListenerSupport<Void> listeners = new ListenerSupport<>();

    public WrapChannelPromise(Channel channel, T realPromise) {
        this.channel = channel;
        this.realPromise = realPromise;
        realPromise.addListener(this);
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public T setSuccess(Void result) {
        realPromise.setSuccess(result);
        return (T) this;
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public T setSuccess() {
        realPromise.setSuccess();
        return (T) this;
    }

    @Override
    public boolean trySuccess() {
        return realPromise.trySuccess();
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public T setFailure(Throwable cause) {
        realPromise.setFailure(cause);
        return (T) this;
    }

    public void operationComplete(Future<? super Void> future) {
        listeners.operationComplete(this);
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public T addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        this.listeners.addListeners(listeners);
        return (T) this;
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public T removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        this.listeners.removeListeners(listener);
        return (T) this;
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public T removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        this.listeners.removeListeners(listeners);
        return (T) this;
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public T addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        this.listeners.addListeners(listener);
        return (T) this;
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public T sync() throws InterruptedException {
        realPromise.sync();
        return (T) this;
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public T syncUninterruptibly() {
        realPromise.syncUninterruptibly();
        return (T) this;
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public T await() throws InterruptedException {
        realPromise.await();
        return (T) this;
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public T awaitUninterruptibly() {
        realPromise.awaitUninterruptibly();
        return (T) this;
    }

    @Override
    public boolean trySuccess(Void result) {
        return realPromise.trySuccess(result);
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return realPromise.tryFailure(cause);
    }

    @Override
    public boolean setUncancellable() {
        return realPromise.setUncancellable();
    }

    @Override
    public boolean isSuccess() {
        return realPromise.isSuccess();
    }

    @Override
    public boolean isCancellable() {
        return realPromise.isCancellable();
    }

    @Override
    public Throwable cause() {
        return realPromise.cause();
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return realPromise.await(timeout, unit);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return realPromise.await(timeoutMillis);
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return realPromise.awaitUninterruptibly(timeout, unit);
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return realPromise.awaitUninterruptibly(timeoutMillis);
    }

    @Override
    public Void getNow() {
        return realPromise.getNow();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return realPromise.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return realPromise.isCancelled();
    }

    @Override
    public boolean isDone() {
        return realPromise.isDone();
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
        return realPromise.get();
    }

    @Override
    public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return realPromise.get(timeout, unit);
    }
    
}
