package com.lang.sekiro.netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ReplayingDecoder;

/**
 * 编码器：SekiroMessage转换成网络中的字节流
 */
public class SekiroMessageEncoder extends MessageToByteEncoder<SekiroNatMessage> {

    // 消息类型大小
    private static final int TYPE_SIZE = 1;
    // 流水号大小
    private static final int SERIAL_NUMBER_SIZE = 8;
    // uri 长度大小
    private static final int URI_LENGTH_SIZE = 1;

    /**
     * SekiroMessage转换成网络中的字节流
     * @param ctx
     * @param msg
     * @param out
     * @throws Exception
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, SekiroNatMessage msg, ByteBuf out) throws Exception {
        // 总长度
        int bodyLength = TYPE_SIZE + SERIAL_NUMBER_SIZE + URI_LENGTH_SIZE;
        byte[] uriBytes = null;
        if (msg.getExtra() != null) {
            uriBytes = msg.getExtra().getBytes();
            bodyLength += uriBytes.length;
        }

        if (msg.getData() != null) {
            bodyLength += msg.getData().length;
        }

        //out.writeInt(Constants.protocolMagic);
        // write the total packet length but without length field's length.
        out.writeInt(bodyLength);
        out.writeByte(msg.getType());
        out.writeLong(msg.getSerialNumber());

        if (uriBytes != null) {
            out.writeByte((byte) uriBytes.length);
            out.writeBytes(uriBytes);
        } else {
            out.writeByte((byte) 0x00);
        }

        if (msg.getData() != null) {
            out.writeBytes(msg.getData());
        }
//        out.writeBytes(new byte[]{'\r','\n'});
    }
}