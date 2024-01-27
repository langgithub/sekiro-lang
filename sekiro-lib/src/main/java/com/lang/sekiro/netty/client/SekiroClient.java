package com.lang.sekiro.netty.client;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.lang.sekiro.Constants;
import com.lang.sekiro.api.ActionHandler;
import com.lang.sekiro.api.SekiroRequestHandler;
import com.lang.sekiro.api.SekiroRequestHandlerManager;
import com.lang.sekiro.log.SekiroLogger;
import com.lang.sekiro.netty.protocol.SekiroMessageDecoder;
import com.lang.sekiro.netty.protocol.SekiroMessageEncoder;
import com.lang.sekiro.netty.protocol.SekiroNatMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * nio服务类，同时也是包含action转发
 */
public class SekiroClient {
    // 主机
    private String serverHost;
    // 端口
    private int serverPort;
    // uuid
    private String clientId;
    // group
    private String group = "default";

    private AtomicBoolean isStartUp = new AtomicBoolean(false);
    // 所有的client
    private static Map<String, SekiroClient> allClient = new ConcurrentHashMap<>();
    // 管理实际的SekiroRequestHandle实现类
    private SekiroRequestHandlerManager sekiroRequestHandlerManager = new SekiroRequestHandlerManager();

    private SekiroClient(String serverHost, int serverPort, String clientId, String group) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        //@ 是一个hermes保留分隔符
        clientId = clientId.replaceAll("@", "_");
        this.clientId = clientId;
        this.group = group;
        if (group == null || "".equals(group.trim())) {
            this.group = "default";
        }
    }

    public static SekiroClient start(String serverHost, final String clientID) {
        return start(serverHost, Constants.defaultNatServerPort, clientID, "default");
    }

    public static SekiroClient start(String serverHost, int serverPort, final String clientID) {
        return start(serverHost, serverPort, clientID, "default");
    }

    public static SekiroClient start(String serverHost, final String clientID, String group) {
        return start(serverHost, Constants.defaultNatServerPort, clientID, group);
    }

    /**
     * 开启一个长链接调用隧道，可以实现在公网服务器调用NAT网络下的手机功能
     *
     * @param serverHost 服务器地址
     * @param serverPort 服务器ip
     * @param clientID   手机id，唯一标记一个手机（请注意，一个手机应该只开启一个隧道）
     * @param group      分组，解决同一个app，在不同团队安装了不同的服务的问题。不同group
     * @return 一个client控制器实例
     */
    public static SekiroClient start(String serverHost, int serverPort, final String clientID, String group) {
        SekiroClient sekiroClient = allClient.get(serverHost + ":" + serverHost);
        if (sekiroClient == null) {
            synchronized (SekiroClient.class) {
                sekiroClient = allClient.get(serverHost + ":" + serverHost);
                if (sekiroClient == null) {
                    sekiroClient = new SekiroClient(serverHost, serverPort, clientID, group);
                    allClient.put(serverHost + ":" + serverHost, sekiroClient);
                }
            }
        }
        sekiroClient.startInternal();
        return sekiroClient;
    }

    private Bootstrap natClientBootstrap;

    /**
     * 启动NIO服务
     */
    private void startInternal() {
        if (isStartUp.compareAndSet(false, true)) {
            natClientBootstrap = new Bootstrap();
            NioEventLoopGroup workerGroup = new NioEventLoopGroup();

            natClientBootstrap.group(workerGroup);
            natClientBootstrap.channel(NioSocketChannel.class);
            natClientBootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) throws Exception {
                    ChannelPipeline pipeline=socketChannel.pipeline();
                    pipeline.addLast(new SekiroMessageDecoder());
                    pipeline.addLast(new SekiroMessageEncoder());
                    pipeline.addLast(new ClientIdleCheckHandler(SekiroClient.this));
                    pipeline.addLast(new ClientChannelHandler(SekiroClient.this));
                }
            });
            SekiroLogger.info("connect to nat server at service startUp");
            connectNatServer();
        }
    }

    /**
     * 可以在运行时切换client group，比如我们把手机根据是否登陆进行分组，一个手机可以从未登录转化为登录。此时对应的group将会跟随这切换
     *
     * @param newGroup 新的groupId
     */
    public synchronized void updateGroup(String newGroup) {
        if (group.equals(newGroup)) {
            return;
        }
        SekiroLogger.info("the group update from :" + group + " to:" + newGroup);
        Channel cmdChannelCopy = cmdChannel;
        if (cmdChannelCopy != null && cmdChannelCopy.isActive()) {
            cmdChannelCopy.close();
            cmdChannel = null;
            isConnecting = false;
        }
        group = newGroup;
        if (group== null || "".equals(group.trim())) {
            this.group = "default";
        }
        connectNatServer();
    }

    //和服务器保持链接的channel
    private Channel cmdChannel = null;
    private volatile boolean isConnecting = false;

    public synchronized void connectNatServer() {
        Channel cmdChannelCopy = cmdChannel;
        if (cmdChannelCopy != null && cmdChannelCopy.isActive()) {
            return;
        }
        if (isConnecting) {
            SekiroLogger.warn("connect event fire already");
            return;
        }
        isConnecting = true;
        natClientBootstrap.group().submit(new Runnable() {
            @Override
            public void run() {
                SekiroLogger.info("connect to nat server...");
                Channel cmdChannelCopy = cmdChannel;
                if (cmdChannelCopy != null && cmdChannelCopy.isActive()) {
                    SekiroLogger.info("cmd channel active, and close channel,heartbeat timeout ?");
                    cmdChannelCopy.close();
                    //TODO clean up all resource
                }
                natClientBootstrap.connect(serverHost, serverPort).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        isConnecting = false;
                        if (!channelFuture.isSuccess()) {
                            SekiroLogger.warn("connect to nat server failed", channelFuture.cause());
                            natClientBootstrap.group().schedule(new Runnable() {
                                @Override
                                public void run() {
                                    SekiroLogger.info("connect to nat server failed, reconnect by scheduler task start");
                                    connectNatServer();
                                }
                            }, reconnectWait(), TimeUnit.MILLISECONDS);

                        } else {
                            sleepTimeMill = 1000;
                            cmdChannel = channelFuture.channel();
                            SekiroLogger.info("connect to nat server success:" + cmdChannel);

                            SekiroNatMessage sekiroNatMessage = new SekiroNatMessage();
                            sekiroNatMessage.setType(SekiroNatMessage.C_TYPE_REGISTER);
                            sekiroNatMessage.setExtra(clientId + "@" + group);
                            cmdChannel.writeAndFlush(sekiroNatMessage);
                        }
                    }
                });
            }
        });

    }

    private static long sleepTimeMill = 1000;

    private static long reconnectWait() {

        if (sleepTimeMill > 120000) {
            sleepTimeMill = 120000;
        }

        synchronized (SekiroClient.class) {
            sleepTimeMill = sleepTimeMill + 1000;
            return sleepTimeMill;
        }

    }


    public Channel getCmdChannel() {
        return cmdChannel;
    }

    /**
     * 移动端调用注册SekiroRequestHandler
     * @param action
     * @param sekiroRequestHandler
     * @return
     */
    public SekiroClient registerHandler(String action, SekiroRequestHandler sekiroRequestHandler) {
        sekiroRequestHandlerManager.registerHandler(action, sekiroRequestHandler);
        return this;
    }

    public SekiroClient registerHandler(ActionHandler actionHandler) {
        sekiroRequestHandlerManager.registerHandler(actionHandler.action(), actionHandler);
        return this;
    }

    public SekiroRequestHandlerManager getSekiroRequestHandlerManager() {
        return sekiroRequestHandlerManager;
    }
}
