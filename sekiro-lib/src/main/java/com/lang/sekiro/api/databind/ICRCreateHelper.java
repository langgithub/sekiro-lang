package com.lang.sekiro.api.databind;


import com.lang.sekiro.api.SekiroRequest;
import com.lang.sekiro.api.SekiroRequestHandler;

import java.lang.reflect.Constructor;


public class ICRCreateHelper implements ActionRequestHandlerGenerator {
    private Constructor<? extends SekiroRequestHandler> theConstructor;

    public ICRCreateHelper(Constructor<? extends SekiroRequestHandler> theConstructor) {
        this.theConstructor = theConstructor;
    }

    @Override
    public SekiroRequestHandler gen(SekiroRequest invokeRequest) {
        try {
            return theConstructor.newInstance(invokeRequest);
        } catch (Exception e) {
            // not happen
            throw new IllegalStateException(e);
        }
    }
}
