package com.lang.sekiross.netty.nat;


import com.lang.sekiro.Constants;
import com.lang.sekiro.netty.protocol.SekiroMessageEncoder;
import com.lang.sekiro.netty.protocol.SekiroMessageDecoder;
import com.lang.sekiro.netty.protocol.SekiroNatMessageDecoderError;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPipeline;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;


/**
 * 手机端通信服务，通过这个和app连接，下发推送调用命令，可实现内网穿透，故命名为NatServer
 */
@Slf4j
@Component
public class SekiroNatServer implements InitializingBean {

    @Value("${natServerPort}")
    private Integer natServerPort;


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
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        pipeline.addLast(new SekiroMessageEncoder());
//                        pipeline.addLast(new SekiroNatMessageDecoderError()); 用于bug演示
                        pipeline.addLast(new SekiroMessageDecoder());
                        pipeline.addLast(new ServerIdleCheckHandler());
                        pipeline.addLast(new NatServerChannelHandler());
                    }
                });


        if (natServerPort == null) {
            natServerPort = Constants.defaultNatServerPort;
        }

        log.info("start netty nat server,port:{}", natServerPort);
        serverBootstrap.bind(natServerPort);
    }

}
