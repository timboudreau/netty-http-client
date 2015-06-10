package com.mastfrog.netty.pool.wrapper;

import io.netty.channel.Channel;
import io.netty.channel.ChannelProgressivePromise;

/**
 * Wrappers a ChannelProgressivePromise with an implementation which provides
 * an alternate Channel.
 *
 * @author Tim Boudreau
 */
public class WrapChannelProgressivePromise extends WrapChannelPromise<ChannelProgressivePromise> implements ChannelProgressivePromise {
    private final WrapperChannel outer;

    public WrapChannelProgressivePromise(Channel channel, ChannelProgressivePromise realPromise, final WrapperChannel outer) {
        super(channel, realPromise);
        this.outer = outer;
    }

    @Override
    public ChannelProgressivePromise setProgress(long progress, long total) {
        realPromise.setProgress(progress, total);
        return this;
    }

    @Override
    public boolean tryProgress(long progress, long total) {
        return realPromise.tryProgress(progress, total);
    }
}
