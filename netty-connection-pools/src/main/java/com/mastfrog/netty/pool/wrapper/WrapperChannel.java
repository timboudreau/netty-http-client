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

import com.mastfrog.netty.pool.CloseAction;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.net.SocketAddress;

/**
 * A channel which wrappers another channel and alters what happens when close()
 * is called.
 *
 * @author Tim Boudreau
 */
public final class WrapperChannel extends DelegatingChannel {

    private final Channel channel;
    private final CloseAction onClose;
    private final ChannelPromise closeFuture;

    public WrapperChannel(Channel channel, CloseAction onClose) {
        assert !(channel instanceof WrapperChannel);
        this.channel = channel;
        this.onClose = onClose;
        closeFuture = this.newPromise();
        channel.closeFuture().addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                WrapperChannel.this.onClose.onUnderlyingChannelClosed(future.channel());
                future.channel().closeFuture().removeListener(this);
            }
        });
    }

    @Override
    public Channel getDelegate() {
        return channel;
    }

    @Override
    public ChannelFuture close() {
        return close(null);
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        boolean closeUnderlyingChannel = onClose.close(channel, promise);
        if (closeUnderlyingChannel) {
            if (promise == null) {
                channel.close();
            } else {
                channel.close(promise);
            }
        } else if (promise != null) {
            promise.trySuccess();
        }
        closeFuture.trySuccess();
        return closeFuture;
    }

    @Override
    public ChannelFuture closeFuture() {
        return closeFuture;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public boolean isActive() {
        return channel.isActive();
    }

    @Override
    public ChannelFuture disconnect() {
        return wrapFuture(channel.disconnect());
    }

    @Override
    public ChannelFuture deregister() {
        return wrapFuture(channel.deregister());
    }

    @Override
    public EventLoop eventLoop() {
        return channel.eventLoop();
    }

    @Override
    public Channel parent() {
        // XXX should wrap?
        return channel.parent();
    }

    @Override
    public ChannelConfig config() {
        return channel.config();
    }

    @Override
    public boolean isRegistered() {
        return channel.isRegistered();
    }

    @Override
    public ChannelMetadata metadata() {
        return channel.metadata();
    }

    @Override
    public SocketAddress localAddress() {
        return channel.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public boolean isWritable() {
        return channel.isWritable();
    }

    @Override
    public Unsafe unsafe() {
        return channel.unsafe();
    }

    @Override
    public ChannelPipeline pipeline() {
        return channel.pipeline();
    }

    @Override
    public ByteBufAllocator alloc() {
        return channel.alloc();
    }

    @Override
    public ChannelPromise newPromise() {
        return wrapPromise(channel.newPromise());
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return wrapProgressivePromise(channel.newProgressivePromise());
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return wrapFuture(channel.newSucceededFuture());
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return wrapFuture(channel.newFailedFuture(cause));
    }

    @Override
    public ChannelPromise voidPromise() {
        return wrapPromise(channel.voidPromise());
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return wrapFuture(channel.bind(localAddress));
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return wrapFuture(channel.connect(remoteAddress));
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return wrapFuture(channel.connect(remoteAddress, localAddress));
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return wrapFuture(channel.bind(localAddress, promise));
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return wrapFuture(channel.connect(remoteAddress, promise));
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return wrapFuture(channel.connect(remoteAddress, localAddress, promise));
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return wrapFuture(channel.disconnect(promise));
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return wrapFuture(channel.deregister(promise));
    }

    @Override
    public Channel read() {
        channel.read();
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return wrapFuture(channel.write(msg));
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return wrapFuture(channel.write(msg, promise));
    }

    @Override
    public Channel flush() {
        return channel.flush();
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return wrapFuture(channel.writeAndFlush(msg, promise));
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return wrapFuture(channel.writeAndFlush(msg));
    }

    @Override
    public int compareTo(Channel o) {
        return channel.compareTo(o);
    }

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return channel.attr(key);
    }

    ChannelFuture wrapFuture(ChannelFuture fut) {
        return new WrapChannelFuture(this, fut);
    }

    ChannelPromise wrapPromise(ChannelPromise promise) {
        return new WrapChannelPromise<>(this, promise);
    }

    ChannelProgressivePromise wrapProgressivePromise(ChannelProgressivePromise promise) {
        return new WrapChannelProgressivePromise(this, promise, this);
    }
}
