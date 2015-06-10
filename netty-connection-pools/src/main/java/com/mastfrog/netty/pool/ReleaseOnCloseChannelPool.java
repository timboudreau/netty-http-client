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
package com.mastfrog.netty.pool;

import com.mastfrog.netty.pool.wrapper.ListenerSupport;
import com.mastfrog.netty.pool.wrapper.WrapperChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A wrapper for a ChannelPool, which allows returned Channels' close()
 * method to transparently return the Channel to the pool rather than
 * closing it.
 *
 * @author Tim Boudreau
 */
public class ReleaseOnCloseChannelPool implements ChannelPool {

    private final ChannelPool delegate;
    private final CloseAction onClose = new OnCloseImpl();

    public ReleaseOnCloseChannelPool(ChannelPool delegate) {
        this.delegate = delegate;
    }

    @Override
    public Future<Channel> acquire() {
        System.out.println("ACQUIRE CHANNEL FROM POOL");
        return new WrapChannelFuture(delegate.acquire());
    }

    @Override
    public Future<Channel> acquire(Promise<Channel> promise) {
        System.out.println("ACQUIRE CHANNEL FROM POOL");
        return new WrapChannelFuture(delegate.acquire(), promise);
    }

    @Override
    public Future<Void> release(Channel channel) {
        throw new UnsupportedOperationException("Close the channel instead.");
    }

    @Override
    public Future<Void> release(Channel channel, Promise<Void> promise) {
        throw new UnsupportedOperationException("Close the channel instead.");
    }

    @Override
    public void close() {
        delegate.close();
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + " {" + delegate + "}";
    }

    private class OnCloseImpl implements CloseAction {

        @Override
        public boolean close(Channel underlyingChannel, ChannelPromise promise) {
            delegate.release(underlyingChannel);
            System.out.println("RETURN CHANNEL TO POOL");
            return true;
        }

        @Override
        public void onUnderlyingChannelClosed(Channel underlyingChannel) {
            System.out.println("UNDERLYING CHANNEL CLOSED");
            delegate.release(underlyingChannel);
        }
    }

    private class WrapChannelFuture implements Future<Channel> {

        private final Future<Channel> delegate;
        private final ListenerSupport<? extends Channel> listeners = new ListenerSupport<>();

        public WrapChannelFuture(Future<Channel> delegate) {
            this(delegate, null);
        }

        public WrapChannelFuture(Future<Channel> delegate, final Promise<Channel> promise) {
            this.delegate = delegate;
            delegate.addListener(new GenericFutureListener<Future<? super Channel>>() {

                @Override
                public void operationComplete(Future<? super Channel> future) throws Exception {
                    if (promise != null) {
                        if (future.isSuccess()) {
                            promise.setSuccess(wrapped());
                        } else if (future.cause() != null) {
                            promise.setFailure(future.cause());
                        }
                    }
                    listeners.operationComplete(WrapChannelFuture.this);
                }
            });
        }

        @Override
        public boolean isSuccess() {
            return delegate.isSuccess();
        }

        @Override
        public boolean isCancellable() {
            return delegate.isCancellable();
        }

        @Override
        public Throwable cause() {
            return delegate.cause();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Future<Channel> addListener(GenericFutureListener<? extends Future<? super Channel>> listener) {
            listeners.addListeners(listener);
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Future<Channel> addListeners(GenericFutureListener<? extends Future<? super Channel>>... listeners) {
            this.listeners.addListeners(listeners);
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Future<Channel> removeListener(GenericFutureListener<? extends Future<? super Channel>> listener) {
            this.listeners.removeListeners(listener);
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Future<Channel> removeListeners(GenericFutureListener<? extends Future<? super Channel>>... listeners) {
            this.listeners.removeListeners(listeners);
            return this;
        }

        @Override
        public Future<Channel> sync() throws InterruptedException {
            delegate.sync();
            return this;
        }

        @Override
        public Future<Channel> syncUninterruptibly() {
            delegate.syncUninterruptibly();
            return this;
        }

        @Override
        public Future<Channel> await() throws InterruptedException {
            delegate.await();
            return this;
        }

        @Override
        public Future<Channel> awaitUninterruptibly() {
            delegate.awaitUninterruptibly();
            return this;
        }

        @Override
        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.await(timeout, unit);
        }

        @Override
        public boolean await(long timeoutMillis) throws InterruptedException {
            return delegate.await(timeoutMillis);
        }

        @Override
        public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
            return delegate.awaitUninterruptibly(timeout, unit);
        }

        @Override
        public boolean awaitUninterruptibly(long timeoutMillis) {
            return delegate.awaitUninterruptibly(timeoutMillis);
        }

        private WrapperChannel wrapped;

        private Channel wrapped() {
            if (wrapped != null) {
                return wrapped;
            }
            Channel result = delegate.getNow();
            if (result != null) {
                return wrapped = new WrapperChannel(result, onClose);
            }
            return null;
        }

        @Override
        public Channel getNow() {
            return wrapped();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public Channel get() throws InterruptedException, ExecutionException {
            this.await();
            return wrapped();
        }

        @Override
        public Channel get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }

    }

}
