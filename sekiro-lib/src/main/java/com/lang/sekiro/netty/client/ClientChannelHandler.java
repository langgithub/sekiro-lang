package com.lang.sekiro.netty.client;


import com.lang.sekiro.log.SekiroLogger;
import com.lang.sekiro.netty.protocol.SekiroNatMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ClientChannelHandler extends SimpleChannelInboundHandler<SekiroNatMessage> {

    private SekiroClient sekiroClient;

    public ClientChannelHandler(SekiroClient sekiroClient) {
        this.sekiroClient = sekiroClient;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, SekiroNatMessage sekiroNatMessage) throws Exception {
        switch (sekiroNatMessage.getType()) {
            case SekiroNatMessage.TYPE_INVOKE:
                handleInvokeRequest(sekiroNatMessage, channelHandlerContext.channel());
                break;
        }
    }

    /**
     * 调用本地接口
     * @param sekiroNatMessage
     * @param channel
     */
    private void handleInvokeRequest(SekiroNatMessage sekiroNatMessage, Channel channel) {
        long serialNumber = sekiroNatMessage.getSerialNumber();
        if (serialNumber < 0) {
            throw new IllegalStateException("the serial number not set");
        }
        sekiroClient.getSekiroRequestHandlerManager().handleSekiroNatMessage(sekiroNatMessage, channel);
    }


    /**
     * channel未连接到远程服务器，发起reconnect
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel cmdChannel = sekiroClient.getCmdChannel();
        if (cmdChannel == ctx.channel()) {
            SekiroLogger.warn("channel inactive ,reconnect to nat server");
            sekiroClient.connectNatServer();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        SekiroLogger.error("exception caught", cause);
        ctx.close();
    }
}
