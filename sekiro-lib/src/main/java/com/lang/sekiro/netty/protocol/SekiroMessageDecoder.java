package com.lang.sekiro.netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.List;

public class SekiroMessageDecoder extends ReplayingDecoder<Void> {

    // 消息类型大小
    private static final int TYPE_SIZE = 1;
    // 流水号大小
    private static final int SERIAL_NUMBER_SIZE = 8;
    // uri大小
    private static final int URI_LENGTH_SIZE = 1;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int frameLength=in.readInt();
        SekiroNatMessage sekiroNatMessage = new SekiroNatMessage();
        // 消息类型
        byte type = in.readByte();
        sekiroNatMessage.setType(type);
        // 消息流水号
        long sn = in.readLong();
        sekiroNatMessage.setSerialNumber(sn);
        // 消息命令请求信息
        byte uriLength = in.readByte();
        byte[] uriBytes = new byte[uriLength];
        in.readBytes(uriBytes);
        sekiroNatMessage.setExtra(new String(uriBytes));
        // 消息传输数据
        byte[] data = new byte[frameLength - TYPE_SIZE - SERIAL_NUMBER_SIZE - URI_LENGTH_SIZE - uriLength];
        in.readBytes(data);
        sekiroNatMessage.setData(data);
        out.add(sekiroNatMessage);
    }
}
