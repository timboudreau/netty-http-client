/*
 * The MIT License
 *
 * Copyright 2015 tim.
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

import com.mastfrog.url.Host;
import com.mastfrog.url.HostAndPort;
import com.mastfrog.url.Port;

/**
 * Map key which combines host+port and whether or not it is an ssl
 * connection.
 *
 * @author Tim Boudreau
 */
public final class HostPortSsl {

    final HostAndPort hostPort;
    final boolean ssl;

    public HostPortSsl(HostAndPort hostPort, boolean ssl) {
        this.hostPort = hostPort;
        this.ssl = ssl;
    }

    public Host host() {
        return hostPort.host;
    }

    public Port port() {
        return hostPort.port;
    }

    public boolean ssl() {
        return ssl;
    }

    @Override
    public String toString() {
        return hostPort.toString() + (ssl ? " (ssl)" : " (plain)");
    }

    @Override
    public int hashCode() {
        return ssl ? 104651 * hostPort.hashCode() : hostPort.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else if (o == this) {
            return true;
        } else if (o instanceof HostPortSsl) {
            return ((HostPortSsl) o).ssl == ssl && ((HostPortSsl) o).hostPort.equals(hostPort);
        } else {
            return false;
        }
    }
}
