package com.lang.sekiross.netty.nat;


import com.lang.sekiro.Constants;
import com.lang.sekiro.netty.protocol.SekiroNatMessage;
import com.lang.sekiross.netty.ChannelRegistry;

import com.lang.sekiross.netty.NatClient;
import io.netty.channel.Channel;
import org.apache.commons.lang3.StringUtils;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;

@ChannelHandler.Sharable
@Slf4j
public class NatServerChannelHandler extends SimpleChannelInboundHandler<SekiroNatMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, SekiroNatMessage proxyMessage) throws Exception {
        log.info("recieved proxy message, type is:{} from channel:{}", proxyMessage.getTypeReadable(), channelHandlerContext.channel());
        switch (proxyMessage.getType()) {
            case SekiroNatMessage.TYPE_HEARTBEAT:
                handleHeartbeatMessage(channelHandlerContext, proxyMessage);
                break;
            case SekiroNatMessage.C_TYPE_REGISTER:
                handleRegisterMessage(channelHandlerContext, proxyMessage);
                break;
            case SekiroNatMessage.TYPE_INVOKE:
                handleInvokeResponseMessage(channelHandlerContext, proxyMessage);
                break;
        }
    }

    /**
     * 接收到移动端发来的TYPE_INVOKE请求，表示hook成功，返回进入回调
     * @param ctx
     * @param proxyMessage
     */
    private void handleInvokeResponseMessage(ChannelHandlerContext ctx, SekiroNatMessage proxyMessage) {
        String clientId = ctx.channel().attr(Constants.CLIENT_KEY).get();
        if (StringUtils.isBlank(clientId)) {
            log.error("client id is lost");
            return;
        }
        long serialNumber = proxyMessage.getSerialNumber();
        if (serialNumber < 0) {
            log.error("serial number not set for client!!");
            return;
        }
        String group = ctx.channel().attr(Constants.GROUP_KEY).get();
        TaskRegistry.getInstance().forwardClientResponse(clientId, group, serialNumber, proxyMessage);
    }


    private void handleHeartbeatMessage(ChannelHandlerContext ctx, SekiroNatMessage proxyMessage) {
        SekiroNatMessage heartbeatMessage = new SekiroNatMessage();
        heartbeatMessage.setSerialNumber(proxyMessage.getSerialNumber());
        heartbeatMessage.setType(SekiroNatMessage.TYPE_HEARTBEAT);
        log.info("response heartbeat message {}", ctx.channel());
        ctx.channel().writeAndFlush(heartbeatMessage);
        // 心跳回调
//        handler(ctx);

//        String clientId = ctx.channel().attr(Constants.CLIENT_KEY).get();
//        if (StringUtils.isBlank(clientId)) {
//            ctx.channel().close();
//        } else {
//            ChannelRegistry.getInstance().registryClient(clientId, ctx.channel());
//        }
    }

    private void handleRegisterMessage(ChannelHandlerContext ctx, SekiroNatMessage proxyMessage) {
        String clientIdAndGroup = proxyMessage.getExtra();
        if (StringUtils.isBlank(clientIdAndGroup)) {
            log.error("clientId can not empty");
            return;
        }
        ChannelRegistry.getInstance().registryClient(clientIdAndGroup, ctx.channel(), NatClient.NatClientType.NORMAL);
        // 上线回调
//        handler(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("error", cause);
        ctx.close();
    }
}
