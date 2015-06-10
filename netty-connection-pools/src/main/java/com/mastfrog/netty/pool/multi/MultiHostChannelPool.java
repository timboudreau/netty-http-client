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
package com.mastfrog.netty.pool.multi;

import com.mastfrog.netty.pool.ExpirableChannelPool;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implements a similar interface to ChannelPool, and maintains a mapping of
 * host to channel pool.
 *
 * @author Tim Boudreau
 */
public final class MultiHostChannelPool {

    private final Map<HostPortSsl, ExpirableChannelPool> poolForHost = new HashMap<>();
    private final ChannelPoolFactory factory;

    public MultiHostChannelPool(ChannelPoolFactory factory) {
        this.factory = factory;
    }

    synchronized void expireUnusedChannels(long maxAge) {
        Set<HostPortSsl> hosts = new HashSet<>();
        for (Map.Entry<HostPortSsl, ExpirableChannelPool> e : poolForHost.entrySet()) {
            if (e.getValue().isExpired(maxAge)) {
                hosts.add(e.getKey());
                e.getValue().close();
            }
        }
        for (HostPortSsl h : hosts) {
            poolForHost.remove(h);
        }
    }

    public Future<Channel> acquire(HostPortSsl hostPortSsl) {
        ExpirableChannelPool pool;
        synchronized (this) {
            pool = poolForHost.get(hostPortSsl);
            if (pool == null) {
                pool = new ExpirableChannelPool(factory.newPool(hostPortSsl));
                poolForHost.put(hostPortSsl, pool);
            }
        }
        System.out.println("ACQUIRE " + hostPortSsl);
        return pool.acquire();
    }

    public Future<Channel> acquire(HostPortSsl host, Promise<Channel> promise) {
        ExpirableChannelPool pool;
        synchronized (this) {
            pool = poolForHost.get(host);
            System.out.println("GOT A " + pool + " for " + host);
            if (pool == null) {
                pool = new ExpirableChannelPool(factory.newPool(host));
                poolForHost.put(host, pool);
            }
        }
        return pool.acquire(promise);
    }

    public Future<Void> release(HostPortSsl host, Channel channel) {
        ExpirableChannelPool pool;
        synchronized (this) {
            pool = poolForHost.get(host);
        }
        if (pool != null) {
            pool.release(channel);
        }
        ChannelPromise result = channel.newPromise();;
        result.setSuccess(null);
        return result;
    }

    public Future<Void> release(HostPortSsl host, Channel channel, Promise<Void> promise) {
        ExpirableChannelPool pool;
        synchronized (this) {
            pool = poolForHost.get(host);
        }
        if (pool != null) {
            pool.release(channel, promise);
            return promise;
        } else {
            ChannelPromise result = channel.newPromise();
            result.setSuccess(null);
            return result;
        }
    }

    public synchronized void close() {
        for (ExpirableChannelPool e : poolForHost.values()) {
            e.close();
        }
        poolForHost.clear();
    }

}
