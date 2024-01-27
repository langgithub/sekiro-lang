package com.lang.sekiross.netty.websocket;


import com.lang.sekiro.Constants;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SekiroWebSocketServer implements InitializingBean {

    @Value("${webSocketServerPort}")
    private Integer webSocketServerPort;


    private boolean started = false;

    @Override
    public void afterPropertiesSet() {
        if (started) {
            return;
        }
        startUp();
        started = true;
    }


    private void startUp() {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup serverBossGroup = new NioEventLoopGroup();
        NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup();

        serverBootstrap.group(serverBossGroup, serverWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        //websocket协议本身是基于http协议的，所以这边也要使用http解编码器
                        socketChannel.pipeline().addLast(new HttpServerCodec());
                        //socketChannel.pipeline().addLast(new HttpResponseEncoder());
                        socketChannel.pipeline().addLast(new HttpObjectAggregator(1 << 25));
                        //以块的方式来写的处理器
                        socketChannel.pipeline().addLast(new ChunkedWriteHandler());
                        socketChannel.pipeline().addLast(new SekiroWebSocketHandler());
                    }
                });


        if (webSocketServerPort == null) {
            webSocketServerPort = Constants.defaultWebSocketServerPort;
        }

        log.info("start WebSocket server,port:{}", webSocketServerPort);
        serverBootstrap.bind(webSocketServerPort);
    }
}
