package com.lang.sekiross.netty;

import com.google.common.base.Charsets;

import com.lang.sekiro.Constants;
import com.lang.sekiro.utils.CommonRes;
import com.lang.sekiro.netty.protocol.SekiroNatMessage;
import com.lang.sekiross.netty.http.HeaderNameValue;
import com.lang.sekiross.netty.task.NettyInvokeRecord;
import com.lang.sekiross.netty.task.TaskRegistry;
import com.lang.sekiross.util.ReturnUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class NatClient {

    @Getter
    private String group;

    @Getter
    private String clientId;
    @Getter
    private Channel cmdChannel;

    private AtomicLong invokeSeqGenerator = new AtomicLong(0L);

    public NatClient(String clientId, String group, Channel cmdChannel) {
        this.clientId = clientId;
        this.group = group;
        attachChannel(cmdChannel);
    }

    public void attachChannel(Channel channel) {
        Channel oldChannel = this.cmdChannel;
        if (oldChannel != null && oldChannel != channel) {
            //这里先让他给泄漏了。让客户端来close，服务器不负责close
            //oldChannel.close();
        }
        this.cmdChannel = channel;
        this.cmdChannel.attr(Constants.CLIENT_KEY).set(clientId);
        this.cmdChannel.attr(Constants.GROUP_KEY).set(group);
    }

    /**
     * 将接口封装到SekiroNatMessage，并且发送到移动端，由于是异步所以是不断的发送
     * 注册一个新任务
     * @param paramContent
     * @return
     */
    private NettyInvokeRecord forwardInternal(String paramContent) {
        log.info("request body: {}   clientId:{}", paramContent, clientId);
        long invokeTaskId = invokeSeqGenerator.incrementAndGet();
        NettyInvokeRecord nettyInvokeRecord = new NettyInvokeRecord(clientId, group, invokeTaskId, paramContent);

        SekiroNatMessage proxyMessage = new SekiroNatMessage();
        proxyMessage.setType(SekiroNatMessage.TYPE_INVOKE);
        proxyMessage.setSerialNumber(invokeTaskId);
        proxyMessage.setData(paramContent.getBytes(Charsets.UTF_8));
        // 注册一个新任务
        TaskRegistry.getInstance().registerTask(nettyInvokeRecord);

        cmdChannel.writeAndFlush(proxyMessage);

        return nettyInvokeRecord;
    }

    /**
     * 将接口封装发送到移动端（异步发送），并设置回调响应事件
     * @param paramContent
     * @param channel 接口调用者与服务器5601的channel
     */
    public void forward(String paramContent, final Channel channel) {
        NettyInvokeRecord nettyInvokeRecord = forwardInternal(paramContent);
        // 注册回调函数，返回抓取数据给调用接口者
        nettyInvokeRecord.setSekiroResponseEvent(new NettyInvokeRecord.SekiroResponseEvent() {
            @Override
            public void onSekiroResponse(SekiroNatMessage sekiroNatMessage) {
                if (sekiroNatMessage == null) {
                    ReturnUtil.writeRes(channel, CommonRes.failed("timeout"));
                    return;
                }

                byte[] data = sekiroNatMessage.getData();
                if (data == null) {
                    ReturnUtil.writeRes(channel, CommonRes.success(null));
                    return;
                }

                String responseContentType = sekiroNatMessage.getExtra();
                if (responseContentType == null) {
                    responseContentType = "text/plain;charset=utf8";
                }

                if (StringUtils.containsIgnoreCase(responseContentType, "application/json")) {
                    String responseJson = new String(sekiroNatMessage.getData(), StandardCharsets.UTF_8);
                    log.info("receive json response:{}", responseJson);
                    try {
                        JSONObject jsonObject = JSONObject.parseObject(responseJson);
                        ReturnUtil.writeRes(channel, ReturnUtil.from(jsonObject, clientId));
                        return;
                    } catch (JSONException e) {
                        log.warn("parse response failed", e);
                    }
                }

                DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(data));
                httpResponse.headers().set(HeaderNameValue.CONTENT_TYPE, responseContentType);

                channel.writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
            }
        });
    }

    public void forward(String paramContent, Integer timeOut, HttpServletResponse httpServletResponse) {
        NettyInvokeRecord nettyInvokeRecord = forwardInternal(paramContent);
        nettyInvokeRecord.waitCallback(timeOut);
        SekiroNatMessage sekiroNatMessage = nettyInvokeRecord.finalResult();
        if (sekiroNatMessage == null) {
            ReturnUtil.writeRes(httpServletResponse, CommonRes.failed("timeout"));
            return;
        }

        byte[] data = sekiroNatMessage.getData();
        if (data == null) {
            ReturnUtil.writeRes(httpServletResponse, CommonRes.success(null));
            return;
        }

        String responseContentType = sekiroNatMessage.getExtra();
        if (responseContentType == null) {
            responseContentType = "text/plain;charset=utf8";
        }

        if (StringUtils.containsIgnoreCase(responseContentType, "application/json")) {
            String responseJson = new String(sekiroNatMessage.getData(), StandardCharsets.UTF_8);
            log.info("receive json response:{}", responseJson);
            try {
                JSONObject jsonObject = JSONObject.parseObject(responseJson);
                ReturnUtil.writeRes(httpServletResponse, ReturnUtil.from(jsonObject, clientId));
                return;
            } catch (JSONException e) {
                log.warn("parse response failed", e);
            }
        }

        httpServletResponse.setContentType(responseContentType);
        ServletOutputStream outputStream = null;
        try {
            outputStream = httpServletResponse.getOutputStream();
            outputStream.write(sekiroNatMessage.getData());
            outputStream.close();
        } catch (IOException e) {
            log.warn("write response failed");
        } finally {
            IOUtils.closeQuietly(outputStream);
        }

    }
}
