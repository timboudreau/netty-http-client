/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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

import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.openide.util.Exceptions;

/**
 * Maps all host name lookups to localhost, so we can use real host names but always
 * call only our local server.
 */
class LocalhostOnlyAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    private final Map<EventExecutor, Res> cache = new WeakHashMap<>();

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor ee) throws Exception {
        Res result = cache.get(ee);
        if (result == null) {
            result = new Res(ee);
            cache.put(ee, result);
        }
        return result;
    }

    static class Res extends AbstractAddressResolver<InetSocketAddress> {

        Res(EventExecutor executor) {
            super(executor, InetSocketAddress.class);
        }

        @Override
        protected boolean doIsResolved(InetSocketAddress t) {
            try {
                InetAddress loc = InetAddress.getLocalHost();
                return loc.equals(t.getAddress());
            } catch (UnknownHostException ex) {
                Exceptions.printStackTrace(ex);
                return false;
            }
        }

        @Override
        protected void doResolve(final InetSocketAddress addr, final Promise<InetSocketAddress> prms) throws Exception {
            super.executor().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        InetAddress loc = InetAddress.getLocalHost();
                        InetSocketAddress sock = new InetSocketAddress(loc, addr.getPort());
                        prms.setSuccess(sock);
                    } catch (UnknownHostException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            });
        }

        @Override
        protected void doResolveAll(final InetSocketAddress addr, final Promise<List<InetSocketAddress>> prms) throws Exception {
            executor().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        InetAddress loc = InetAddress.getLocalHost();
                        InetSocketAddress sock = new InetSocketAddress(loc, addr.getPort());
                        prms.setSuccess(Collections.singletonList(sock));
                    } catch (UnknownHostException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            });
        }
    }

}
