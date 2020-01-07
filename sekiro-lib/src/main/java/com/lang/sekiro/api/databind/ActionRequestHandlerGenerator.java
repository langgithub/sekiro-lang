package com.lang.sekiro.api.databind;


import com.lang.sekiro.api.SekiroRequest;
import com.lang.sekiro.api.SekiroRequestHandler;

/**
 * SekiroRequestHandler 生成器
 */
public interface ActionRequestHandlerGenerator {
    SekiroRequestHandler gen(SekiroRequest invokeRequest);
}
