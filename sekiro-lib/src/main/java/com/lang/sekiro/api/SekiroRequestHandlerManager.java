package com.lang.sekiro.api;

import android.text.TextUtils;


import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.lang.sekiro.api.databind.*;
import com.lang.sekiro.log.SekiroLogger;
import com.lang.sekiro.netty.protocol.SekiroNatMessage;
import com.lang.sekiro.utils.CommonRes;
import com.lang.sekiro.utils.Defaults;
import io.netty.channel.Channel;


public class SekiroRequestHandlerManager {
    private static final String action = "action";
    private static final String actionList = "__actionList";

    // key->action , value->ActionRequestHandlerGenerator
    private Map<String, ActionRequestHandlerGenerator> requestHandlerMap = new HashMap<>();

    // key->SekiroRequestHandler, value->Fileds
    private static final ConcurrentMap<Class, Field[]> fieldCache = new ConcurrentHashMap<>();


    /**
     * 更具sekiroNatMessage 中参数，判断调用具体的一个SekiroRequestHandler实现类
     * @param sekiroNatMessage
     * @param channel
     */
    public void handleSekiroNatMessage(SekiroNatMessage sekiroNatMessage, Channel channel) {
        SekiroRequest sekiroRequest = new SekiroRequest(sekiroNatMessage.getData(), sekiroNatMessage.getSerialNumber());
        SekiroResponse sekiroResponse = new SekiroResponse(sekiroRequest, channel);

        try {
            // 将sekiroNatMessage中的byte数据转化成json
            sekiroRequest.getString("ensure mode parsed");
        } catch (Exception e) {
            sekiroResponse.failed(CommonRes.statusBadRequest, e);
            return;
        }

        // 获取action
        String action = sekiroRequest.getString(SekiroRequestHandlerManager.action);
        if (action == null || "".equals(action.trim())) {
            sekiroResponse.failed("the param:{" + SekiroRequestHandlerManager.action + "} not present");
            return;
        }
        // 更具action 获取ActionRequestHandlerGenerator 继而获取SekiroRequestHandler
        ActionRequestHandlerGenerator actionRequestHandlerGenerator = requestHandlerMap.get(action);
        if (actionRequestHandlerGenerator == null) {
            if (action.equals(actionList)) {
                TreeSet<String> sortedActionSet = new TreeSet<>(requestHandlerMap.keySet());
                sekiroResponse.success(sortedActionSet);
                return;
            } else {
                sekiroResponse.failed("unknown action: " + action);
                return;
            }
        }

        try {
            // 获取SekiroRequestHandler
            actionRequestHandlerGenerator.gen(sekiroRequest).handleRequest(sekiroRequest, sekiroResponse);
        } catch (Throwable throwable) {
            SekiroLogger.error("failed to generate action request handler", throwable);
            sekiroResponse.failed(CommonRes.statusError, throwable);
        }
    }


    /**
     * 注册 requestHandler到 map
     * @param action
     * @param sekiroRequestHandler
     */
    public void registerHandler(String action, SekiroRequestHandler sekiroRequestHandler) {
        if (action == null || "".equals(action.trim())) {
            throw new IllegalArgumentException("action empty!!");
        }
        if (requestHandlerMap.containsKey(action)) {
            throw new IllegalStateException("the request handler: " + sekiroRequestHandler + " for action:" + action + "  registered already!!");
        }
        requestHandlerMap.put(action, toGenerator(sekiroRequestHandler));
    }

    /**
     * 类的自动绑定 ，也就是new 一个SekiroRequestHandler 的实例
     * 因为要绑定其他参数，所有这里重新new 了一个新对象
     * @param actionRequestHandler
     * @return
     */
    @SuppressWarnings("unchecked")
    private ActionRequestHandlerGenerator toGenerator(SekiroRequestHandler actionRequestHandler) {
        Constructor<? extends SekiroRequestHandler>[] constructors = (Constructor<? extends SekiroRequestHandler>[]) actionRequestHandler.getClass().getDeclaredConstructors();
        boolean canAutoCreateInstance = false;
        ActionRequestHandlerGenerator instanceCreateHelper = null;
        // 根据构造参数选择绑定方法
        for (Constructor<? extends SekiroRequestHandler> constructor : constructors) {
            // SekiroRequestHandler的实现类是一个无参构造函数
            if (constructor.getParameterTypes().length == 0) {
                canAutoCreateInstance = true;
                // 走这里，new SekiroRequestHandler
                instanceCreateHelper = new EmptyARCreateGenerator(actionRequestHandler.getClass());
                break;
            }
            // SekiroRequestHandler的实现类包含一个构造函数
            if (constructor.getParameterTypes().length == 1) {
                // 如果这个参数与SekiroRequest是父子关系
                if (SekiroRequest.class.isAssignableFrom(constructor.getParameterTypes()[0])) {
                    canAutoCreateInstance = true;
                    instanceCreateHelper = new ICRCreateHelper(constructor);
                    break;
                } else if (actionRequestHandler.getClass().getName().startsWith(constructor.getParameterTypes()[0].getName())) {
                    // 可能是匿名内部类，这个时候也需要支持注入（内部类绑定 忽略）
                    //com.virjar.sekiro.demo.MainActivity$1$1
                    //com.virjar.sekiro.demo.MainActivity$1
                    String simpleInnerClassName = actionRequestHandler.getClass().getName().substring(constructor.getParameterTypes()[0].getName().length());
                    if (simpleInnerClassName.startsWith("$")) {
                        //确定是匿名内部类
                        //find out class object instance
                        Object outClassObjectInstance = null;
                        boolean hasAutoBindAnnotation = false;
                        for (Field field : actionRequestHandler.getClass().getDeclaredFields()) {
                            if (!field.isSynthetic()) {
                                continue;
                            }

                            AutoBind fieldAnnotation = field.getAnnotation(AutoBind.class);
                            if (fieldAnnotation != null) {
                                hasAutoBindAnnotation = true;
                            }

                            if (!field.getType().equals(constructor.getParameterTypes()[0])) {
                                continue;
                            }

                            field.setAccessible(true);
                            try {
                                outClassObjectInstance = field.get(actionRequestHandler);
                                break;
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        // TODO 不支持匿名内部类的自动绑定
                        if (outClassObjectInstance != null && hasAutoBindAnnotation) {
//                            canAutoCreateInstance = true;
//                            Class<? extends SekiroRequestHandler> aClass = actionRequestHandler.getClass();
//                            instanceCreateHelper = new InnerClassCreateHelper(constructor, outClassObjectInstance);
//                            break;
                            throw new IllegalStateException("can not bind attribute for InnerClass object");
                        }
                    }
                }
            }
        }
        if (!canAutoCreateInstance) {
            return new DirectMapGenerator(actionRequestHandler);
        }

        // 获取对于的fields属性
        Field[] fields = classFileds(actionRequestHandler.getClass());
        // 数组转化成list
        List<Field> autoBindFields = new ArrayList<>();
        // 不是原始类型存放容器
        Map<Field, Object> copyFiledMap = new HashMap<>();
        for (Field field : fields) {
            // 这里其实做过判断了，可以忽略
            if (Modifier.isStatic(field.getModifiers())|| field.isSynthetic()) {
                continue;
            }
            try {
                Object o = field.get(actionRequestHandler);
                if (o != null) {
                    // 判断是否是原始类型和object类型
                    if (field.getType().isPrimitive() && Defaults.defaultValue(o.getClass()) == o) {
                        continue;
                    }
                    copyFiledMap.put(field, o);
                }
            } catch (Exception e) {
                SekiroLogger.error(e.getMessage(),e);
            }
            autoBindFields.add(field);
        }
        if (autoBindFields.size() == 0) {
            return new DirectMapGenerator(actionRequestHandler);
        }
        return new FileBindGenerator(autoBindFields, instanceCreateHelper, copyFiledMap);
    }


    /**
     * 获取clazz 类类中的属性，改方法线程安全
     * @param clazz
     * @return
     */
    private static Field[] classFileds(Class clazz) {
        // 判断对象类型
        if (clazz == Object.class) return new Field[0];
        // 获取缓存
        Field[] fields = fieldCache.get(clazz);
        // 缓存不为空直接返回
        if (fields != null) return fields;
        synchronized (clazz) {
            // 确保是原来的clazz
            fields = fieldCache.get(clazz);
            while (fields == null) {
                ArrayList<Field> ret = new ArrayList<>();
                ret.addAll(Arrays.asList(clazz.getDeclaredFields()));
                // 递归获取当前实体的父类信息
                ret.addAll(Arrays.asList(classFileds(clazz.getSuperclass())));
                Iterator<Field> iterator = ret.iterator();
                while (iterator.hasNext()) {
                    Field next = iterator.next();
                    if (Modifier.isStatic(next.getModifiers())) {
                        iterator.remove();
                        continue;
                    }
                    if (next.isSynthetic()) {
                        iterator.remove();
                        continue;
                    }
                    if (!next.isAccessible()) {
                        next.setAccessible(true);
                    }
                }
                // 自动分配一个大小合适的Filed 数组
                fields = ret.toArray(new Field[0]);
                fieldCache.put(clazz, fields);
            }
        }
        return fields;
    }


}
