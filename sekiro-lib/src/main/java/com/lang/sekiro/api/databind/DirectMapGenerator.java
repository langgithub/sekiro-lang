package com.lang.sekiro.api.databind;

import com.lang.sekiro.api.SekiroRequest;
import com.lang.sekiro.api.SekiroRequestHandler;
import com.lang.sekiro.log.SekiroLogger;


/**
 * ActionRequestHandler 中没有其他额外的属性
 */
public class DirectMapGenerator implements ActionRequestHandlerGenerator {
    private SekiroRequestHandler delegate;

    public DirectMapGenerator(SekiroRequestHandler delegate) {
        SekiroLogger.info("DirectMapGenerator 初始化。ActionRequestHandler 中没有其他额外的属性");
        this.delegate = delegate;
    }

    @Override
    public SekiroRequestHandler gen(SekiroRequest invokeReques) {
        return delegate;
    }
}