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
package com.mastfrog.tiny.http.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.security.cert.CertificateException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;

/**
 * A simple, lightweight HTTP server for use in tests and such.
 */
public final class TinyHttpServer {

    private final Channel httpChannel;
    private final Channel httpsChannel;
    private final EventLoopGroup eventHandlers = new NioEventLoopGroup(2);
    private final EventLoopGroup workers = new NioEventLoopGroup(8);
    private final int httpsPort;
    private final int httpPort;
    private final TinyHttpServerHandler handler;
    static int[] temp = new int[1];
    /**
     * Create a new server, using randomly selected ports that are tested for
     * availability.
     *
     * @param responder The thing that will construct responses.
     *
     * @throws CertificateException If something goes wrong
     * @throws SSLException If something goes wrong
     * @throws InterruptedException If something goes wrong
     */
    public TinyHttpServer(Responder responder) throws CertificateException, SSLException, InterruptedException {
        this(responder, new int[1]);
    }

    private TinyHttpServer(Responder responder, int[] temp) throws CertificateException, SSLException, InterruptedException {
        this(responder, temp[0] = findPort(0), findPort(temp[0]));
    }

    public TinyHttpServer(Responder responder, int httpPort, int httpsPort) throws CertificateException, SSLException, InterruptedException {
        this.httpPort = httpPort == -1 ? findPort(0) : httpPort;
        this.httpsPort = httpsPort == -1 ? findPort(httpPort) : httpsPort;
        // Configure SSL.
        final SslContext sslCtx;
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();

        handler = new TinyHttpServerHandler(responder);
        if (httpsPort == 0 && httpPort == 0) {
            throw new IllegalStateException("Neither http nor https ports specified - cannot start.");
        }

        if (httpPort != 0) {
            // Configure the server.
            ServerBootstrap httpBootstrap = new ServerBootstrap();
            httpBootstrap.option(ChannelOption.SO_BACKLOG, 1024);
            httpBootstrap.group(eventHandlers, workers)
                    .channel(NioServerSocketChannel.class)
//                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new TinyHttpServerInitializer(null, handler));

            httpChannel = httpBootstrap.bind(httpPort).sync().channel();
        } else {
            httpChannel = null;
        }

        if (httpsPort != 0) {
            ServerBootstrap httpsBootstrap = new ServerBootstrap();
            httpsBootstrap.option(ChannelOption.SO_BACKLOG, 1024);
            httpsBootstrap.group(eventHandlers, workers)
                    .channel(NioServerSocketChannel.class)
//                    .handler(new  LoggingHandler(LogLevel.INFO))
                    .childHandler(new TinyHttpServerInitializer(sslCtx, handler));
            httpsChannel = httpsBootstrap.bind(httpsPort).sync().channel();
        } else {
            httpsChannel = null;
        }
    }

    /**
     * Rethrow any exception encountered while handling requests (useful to
     * ensure tests fail if they blow up the responder)
     *
     * @return this
     */
    public TinyHttpServer throwLast() {
        handler.throwLast();
        return this;
    }

    /**
     * The http port.
     *
     * @return The port
     */
    public int httpPort() {
        return httpPort;
    }

    /**
     * The https port.
     *
     * @return The port
     */
    public int httpsPort() {
        return httpsPort;
    }

    /**
     * Wait until the server is shutdown.
     *
     * @return this
     * @throws InterruptedException
     */
    public TinyHttpServer await() throws InterruptedException {
        if (httpChannel != null) {
            httpChannel.closeFuture().await();
        }
        if (httpsChannel != null) {
            httpsChannel.closeFuture().await();
        }
        return this;
    }

    /**
     * Shut down this server and its associated thread pools
     * and block until exit.
     *
     * @return this
     * @throws InterruptedException if something goes wrong
     */
    public TinyHttpServer shutdown() throws InterruptedException {
        if (httpChannel != null) {
            httpChannel.close();
        }
        if (httpsChannel != null) {
            httpsChannel.close();
        }
        eventHandlers.shutdownGracefully();
        workers.shutdownGracefully();
        await();
        return this;
    }

    static final Random r = new Random(System.currentTimeMillis());

    private static int findPort(int not) {

        int port = 7000;
        do {
            // Make sure we're out of the way of a running mongo instance,
            // both the mongo port and the http port
            port = r.nextInt(1000) + 1 + port;
        } while (!available(port) || port == not);
        return port;
    }

    private static boolean available(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            try (DatagramSocket ds = new DatagramSocket(port)) {
                ds.setReuseAddress(true);
                return true;
            } catch (IOException e) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
//        Responder r = (HttpRequest req, ResponseHead response) -> {
//            response.header("Content-Type").set("text/plain;charset=UTF-8");
//            return "Hello world\n";
//        };

        Responder r = (HttpRequest req, ResponseHead response) -> {
            response.header("Content-Type").set("text/plain;charset=UTF-8");
            return (ChunkedResponse) (int callCount) -> {
                if (callCount > 10) {
                    return null;
                }
                try {
                    Thread.sleep(500);
                    return "Hello world " + callCount + "\n";
                } catch (InterruptedException ex) {
                    Logger.getLogger(TinyHttpServer.class.getName()).log(Level.SEVERE, null, ex);
                    return null;
                }
            };
        };

        TinyHttpServer server = new TinyHttpServer(r);
//        TinyHttpServer server = new TinyHttpServer(r);
        System.out.println("Created");
        server.await();
        System.out.println("Await exited");
    }

}
