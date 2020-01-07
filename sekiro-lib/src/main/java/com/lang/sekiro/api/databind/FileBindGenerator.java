package com.lang.sekiro.api.databind;


import com.lang.sekiro.utils.CommonRes;
import com.lang.sekiro.api.SekiroRequest;
import com.lang.sekiro.api.SekiroRequestHandler;
import com.lang.sekiro.api.SekiroResponse;
import com.lang.sekiro.utils.Defaults;
import com.lang.sekiro.utils.ReflectUtil;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * ActionRequestHandler 实现类中存在其他file参数
 */
public class FileBindGenerator implements ActionRequestHandlerGenerator {

    // SekiroRequestHandler实现类中的所有属性
    private Map<Field, FileBindHandler> fileBindHandlerMap;
    // SekiroRequestHandler实现类的实例
    private ActionRequestHandlerGenerator instanceCreateHelper;
    // SekiroRequestHandler中非基本类的filed
    private Map<Field, Object> copyFiledMap;

    public FileBindGenerator(List<Field> autoBindFields, ActionRequestHandlerGenerator instanceCreateHelper, Map<Field, Object> copyFiledMap) {
        this.fileBindHandlerMap = toBindHandler(autoBindFields);
        this.instanceCreateHelper = instanceCreateHelper;
        this.copyFiledMap = copyFiledMap;
    }

    /**
     * 内部类绑定相关
     * @param autoBindFields
     * @return
     */
    private Map<Field, FileBindHandler> toBindHandler(List<Field> autoBindFields) {
        Map<Field, FileBindHandler> fileBindHandlerMap = new HashMap<>();
        for (Field field : autoBindFields) {
            AutoBind fieldAnnotation = field.getAnnotation(AutoBind.class);
            Object defaultValue = null;
            if (fieldAnnotation != null) {
                Class<?> wrapperType = ReflectUtil.primitiveToWrapper(field.getType());
                if (wrapperType == String.class) {
                    defaultValue = fieldAnnotation.defaultStringValue();
                } else if (wrapperType == Integer.class) {
                    defaultValue = fieldAnnotation.defaultIntValue();
                } else if (wrapperType == Double.class) {
                    defaultValue = fieldAnnotation.defaultDoubleValue();
                } else if (wrapperType == Boolean.class) {
                    defaultValue = fieldAnnotation.defaultBooleanValue();
                } else if (wrapperType == Long.class) {
                    defaultValue = fieldAnnotation.defaultLongValue();
                }
            }
            fileBindHandlerMap.put(field, new FileBindHandler(defaultValue, field, fieldAnnotation));
        }
        return fileBindHandlerMap;
    }

    /**
     * 将invokeRequest 中的参数绑定到SekiroRequestHandler
     * @param invokeRequest
     * @param actionRequestHandler
     */
    private void bindFiled(SekiroRequest invokeRequest, SekiroRequestHandler actionRequestHandler) {
        for (Map.Entry<Field, FileBindHandler> entry : fileBindHandlerMap.entrySet()) {
            Field key = entry.getKey();
            Object value = entry.getValue().transfer(invokeRequest);
            if (value != null) {
                try {
                    key.set(actionRequestHandler, value);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        // 不是基本数据类型的绑定
        for (Map.Entry<Field, Object> entry : copyFiledMap.entrySet()) {
            Field key = entry.getKey();
            try {
                Object o = key.get(actionRequestHandler);
                if (o == null || isPrimitiveDefault(key.getType(), o)) {
                    key.set(actionRequestHandler, entry.getValue());
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * 判断是否是同一类型
     * @param type
     * @param object
     * @return
     */
    private static boolean isPrimitiveDefault(Class type, Object object) {
        if (!type.isPrimitive()) {
            // 包装类型
            Class<?> theUnwrapType = ReflectUtil.wrapperToPrimitive(type);
            if (theUnwrapType == null) {
                return false;
            }
            type = theUnwrapType;
        }
        return Defaults.defaultValue(type) == object;
    }

    /**
     * new SekiroRequestHandler 实例
     * @param invokeRequest
     * @return
     */
    @Override
    public SekiroRequestHandler gen(SekiroRequest invokeRequest) {
        SekiroRequestHandler gen = instanceCreateHelper.gen(invokeRequest);
        try {
            // 这里如果没有 私有便利其实可以略过
            bindFiled(invokeRequest, gen);
        } catch (final RuntimeException e) {
            if (e instanceof ParamNotPresentException) {
                return new SekiroRequestHandler() {
                    @Override
                    public void handleRequest(SekiroRequest sekiroRequest, SekiroResponse sekiroResponse) {
                        sekiroResponse.failed("the param: {" + ((ParamNotPresentException) e).getAttributeName() + "} not presented");
                    }
                };
            }
            if (e instanceof DataParseFailedException) {
                return new SekiroRequestHandler() {
                    @Override
                    public void handleRequest(SekiroRequest sekiroRequest, SekiroResponse sekiroResponse) {
                        DataParseFailedException dataParseFailedException = (DataParseFailedException) e;
                        sekiroResponse.failed(CommonRes.statusBadRequest, dataParseFailedException);
                    }
                };
            }
            throw e;
        }
        return gen;
    }
}