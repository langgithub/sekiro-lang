package com.lang.sekiross.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lang.sekiro.utils.CommonRes;
import com.lang.sekiross.netty.http.HeaderNameValue;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class ReturnUtil {
    public static <T> CommonRes<T> failed(String message) {
        return failed(message, status_other);
    }

    public static CommonRes<Object> from(JSONObject jsonObject) {
        return from(jsonObject, null);
    }

    public static CommonRes<Object> from(JSONObject jsonObject, String clientId) {
        CommonRes<Object> ret = new CommonRes<>();
        ret.setStatus(jsonObject.getInteger("status"));
        ret.setMessage(jsonObject.getString("message"));
        ret.setData(jsonObject.get("data"));
        if (clientId != null) {
            ret.setClientId(clientId);
        }
        return ret;
    }

    public static <T> CommonRes<T> failed(Exception exception) {
        return failed(CommonUtil.translateSimpleExceptionMessage(exception), status_other);
    }

    public static <T> CommonRes<T> failed(String message, int status) {
        return new CommonRes<T>(status, message, null);
    }

    public static void writeRes(HttpServletResponse httpServletResponse, CommonRes<?> commonRes) {
        httpServletResponse.setContentType("application/json;charset=utf8");

        try {
            ServletOutputStream outputStream = httpServletResponse.getOutputStream();
            outputStream.write(JSON.toJSONBytes(commonRes));
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void writeRes(Channel channel, CommonRes<?> commonRes) {
        byte[] bytes = JSON.toJSONString(commonRes).getBytes(StandardCharsets.UTF_8);
        DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes));
        httpResponse.headers().set(HeaderNameValue.CONTENT_TYPE, "application/json;charset=utf8;");
        channel.writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
    }


    public static final int status_other = -1;
    public static final int status_success = 0;
    public static final int status_timeout = 1;
}
