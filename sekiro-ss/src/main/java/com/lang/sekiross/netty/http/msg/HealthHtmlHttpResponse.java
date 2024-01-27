
package com.lang.sekiross.netty.http.msg;
import com.google.common.base.Charsets;
import com.lang.sekiross.netty.http.HeaderNameValue;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class HealthHtmlHttpResponse extends DefaultFullHttpResponse {
    // public final byte[] contentByteData;

    public HealthHtmlHttpResponse(String content) {
        super(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(content.getBytes(Charsets.UTF_8)));

        //contentByteData = content.getBytes(Charsets.UTF_8);
        headers().set(HeaderNameValue.CONTENT_TYPE, "text/html;charset=utf8;");
        //  headers().set(HeaderNameValue.CONTENT_LENGTH, contentByteData.length);
    }

    public static HealthHtmlHttpResponse health() {
        return new HealthHtmlHttpResponse(" ");
    }



}
