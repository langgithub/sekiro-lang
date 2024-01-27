package com.lang.sekiro.api.databind;

import com.lang.sekiro.api.SekiroRequest;
import com.lang.sekiro.api.SekiroRequestHandler;


public class EmptyARCreateGenerator implements ActionRequestHandlerGenerator {
    // extends SekiroRequestHandler 的类的类
    private Class<? extends SekiroRequestHandler> actionRequestHandlerClass;

    public EmptyARCreateGenerator(Class<? extends SekiroRequestHandler> actionRequestHandlerClass) {
        this.actionRequestHandlerClass = actionRequestHandlerClass;
    }

    @Override
    public SekiroRequestHandler gen(SekiroRequest invokeRequest) {
        try {
            return actionRequestHandlerClass.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
