/**
 * Copyright (c) 2017-2018 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.api.http;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.semux.Kernel;
import org.semux.api.ApiHandler;
import org.semux.api.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Semux API launcher
 *
 */
public class SemuxApiService {

    public static final Version DEFAULT_VERSION = Version.v2_1_0;

    private static final Logger logger = LoggerFactory.getLogger(SemuxApiService.class);

    private static final ThreadFactory factory = new ThreadFactory() {
        final AtomicInteger cnt = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "api-" + cnt.getAndIncrement());
        }
    };

    private final Kernel kernel;
    private Channel channel;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private final Map<Version, ApiHandler> apiHandlers;

    String ip;
    int port;

    @SuppressWarnings("deprecation")
    public SemuxApiService(Kernel kernel) {
        this.kernel = kernel;
        this.apiHandlers = Collections
                .unmodifiableMap(Arrays.stream(Version.values())
                        .collect(Collectors.toMap(
                                v -> v,
                                v -> {
                                    switch (v) {
                                    case v1_0_1:
                                        return new org.semux.api.v1_0_1.ApiHandlerImpl(kernel);
                                    case v2_0_0:
                                        return new org.semux.api.ApiHandlerImpl(
                                                new org.semux.api.v2_0_0.impl.SemuxApiServiceImpl(kernel),
                                                org.semux.api.v2_0_0.api.SemuxApi.class);
                                    case v2_1_0:
                                        return new org.semux.api.ApiHandlerImpl(
                                                new org.semux.api.v2_1_0.impl.SemuxApiServiceImpl(kernel),
                                                org.semux.api.v2_1_0.api.SemuxApi.class);
                                    default:
                                        return null;
                                    }
                                })));
    }

    /**
     * Starts API server with configured binding address.
     */
    public void start() {
        start(kernel.getConfig().apiListenIp(), kernel.getConfig().apiListenPort());
    }

    /**
     * Starts API server at the given binding IP and port.
     *
     * @param ip
     * @param port
     */
    public void start(String ip, int port) {
        start(ip, port, new SemuxAPIHttpChannelInitializer());
    }

    /**
     * Starts API server at the given binding IP and port, with the specified
     * channel initializer.
     * 
     * @param ip
     * @param port
     * @param httpChannelInitializer
     */
    public void start(String ip, int port, HttpChannelInitializer httpChannelInitializer) {
        try {
            this.ip = ip;
            this.port = port;
            bossGroup = new NioEventLoopGroup(1, factory);
            workerGroup = new NioEventLoopGroup(0, factory);

            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO)).childHandler(httpChannelInitializer);

            logger.info("Starting API server: address = {}:{}", ip, port);
            channel = b.bind(ip, port).sync().channel();
            logger.info(
                    "API server started. API: {}, Swagger UI: {}",
                    getAPIUrl(),
                    getSwaggerUrl());
        } catch (Exception e) {
            logger.error("Failed to start API server", e);
        }
    }

    /**
     * Stops the API server if started.
     */
    public void stop() {
        if (isRunning() && channel.isOpen()) {
            try {
                channel.close().sync();

                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();

                // workerGroup.terminationFuture().sync();
                // bossGroup.terminationFuture().sync();

                channel = null;
            } catch (Exception e) {
                logger.error("Failed to close channel", e);
            }
            logger.info("API server shut down");
        }
    }

    /**
     * Returns whether the API server is running or not.
     * 
     * @return
     */
    public boolean isRunning() {
        return channel != null;
    }

    public String getAPIUrl() {
        return String.format("http://%s:%d/%s/", ip, port, DEFAULT_VERSION.prefix);
    }

    public String getSwaggerUrl() {
        return String.format("http://%s:%d/%s/swagger.html", ip, port, DEFAULT_VERSION.prefix);
    }

    /**
     * The default channel initializer.
     */
    private class SemuxAPIHttpChannelInitializer extends HttpChannelInitializer {

        @Override
        public HttpHandler initHandler() {
            return new HttpHandler(kernel, apiHandlers);
        }
    }
}
