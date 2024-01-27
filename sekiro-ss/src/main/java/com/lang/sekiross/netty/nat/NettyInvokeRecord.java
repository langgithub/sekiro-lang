package com.lang.sekiross.netty.nat;

import com.lang.sekiro.netty.protocol.SekiroNatMessage;
import lombok.Getter;
import lombok.Setter;

/**
 * 任务实体类，保存回调信息，回调接口实现
 */
public class NettyInvokeRecord {
    @Getter
    private String clientId;

    @Getter
    private String group;

    @Getter
    private long taskId;

    @Getter
    private String paramContent;

    private SekiroNatMessage invokeResult;

    @Getter
    private long taskAddTimestamp = System.currentTimeMillis();

    public NettyInvokeRecord(String clientId, String group, long taskId, String paramContent) {
        this.clientId = clientId;
        this.group = group;
        this.taskId = taskId;
        this.paramContent = paramContent;
    }

    private final Object lock = new Object();

    private boolean callbackCalled = false;

    public NettyInvokeRecord waitCallback(long timeOUt) {
        if (callbackCalled) {
            return this;
        }
        synchronized (lock) {
            if (callbackCalled) {
                return this;
            }

            try {
                lock.wait(timeOUt);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return this;
        }
    }

    /**
     * 异步返回，需要主动调用这个接口通知数据到达，唤醒http request线程，进行数据回传
     * @param sekiroNatMessage 异步返回结果，可以是任何类型
     */
    public void notifyDataArrival(SekiroNatMessage sekiroNatMessage) {
        this.invokeResult = sekiroNatMessage;
        if (sekiroResponseEvent != null) {
            // 触发监听，发送消息到移动端
            sekiroResponseEvent.onSekiroResponse(sekiroNatMessage);
        } else {
            synchronized (lock) {
                callbackCalled = true;
                lock.notify();
            }
        }
    }

    public SekiroNatMessage finalResult() {
        if (!callbackCalled) {
            return null;
        }
        return invokeResult;
    }

    boolean isCallbackCalled() {
        return callbackCalled;
    }

    @Setter
    private SekiroResponseEvent sekiroResponseEvent = null;


    public interface SekiroResponseEvent {
        void onSekiroResponse(SekiroNatMessage responseJson);
    }
}
