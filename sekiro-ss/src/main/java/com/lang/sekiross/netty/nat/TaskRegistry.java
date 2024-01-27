package com.lang.sekiross.netty.nat;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lang.sekiro.netty.protocol.SekiroNatMessage;

import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

/**
 * 不断发送任务到移动端，同时也会把当前任务存储起来
 */
@Slf4j
public class TaskRegistry {

    private Map<String, NettyInvokeRecord> doingTask = Maps.newConcurrentMap();

    private TaskRegistry() {}

    private static TaskRegistry instance = new TaskRegistry();

    public static TaskRegistry getInstance() {
        return instance;
    }

    /**
     * 任务key合成
     * @param clientId
     * @param group
     * @param seq
     * @return
     */
    private String genTaskItemKey(String clientId, String group, long seq) {
        return clientId + "---" + group + "---" + seq;
    }

    /**
     * 注册任务到map
     * @param nettyInvokeRecord
     */
    public synchronized void registerTask(NettyInvokeRecord nettyInvokeRecord) {
        doingTask.put(genTaskItemKey(nettyInvokeRecord.getClientId(), nettyInvokeRecord.getGroup(), nettyInvokeRecord.getTaskId()), nettyInvokeRecord);
    }

    /**
     * 判断当前任务map 是否包含指定的key的任务
     * @param clientId
     * @param group
     * @param taskId
     * @return
     */
    public boolean hasTaskAttached(String clientId, String group, Long taskId) {
        return doingTask.containsKey(genTaskItemKey(clientId, group, taskId));
    }

    /**
     * 任务回调，获取之前存储的任务，进一步获取channel 返回数据
     * @param clientId
     * @param group
     * @param taskId
     * @param sekiroNatMessage
     */
    public void forwardClientResponse(String clientId, String group, Long taskId, SekiroNatMessage sekiroNatMessage) {
        NettyInvokeRecord nettyInvokeRecord = doingTask.remove(genTaskItemKey(clientId, group, taskId));
        if (nettyInvokeRecord == null) {
            log.error("can not find invoke record for client: {}  taskId:{}", clientId, taskId);
            return;
        }
        nettyInvokeRecord.notifyDataArrival(sekiroNatMessage);
    }

    /**
     * 清空指定时间外的任务
     * @param before
     */
    public void cleanBefore(long before) {
        Set<String> needRemove = Sets.newHashSet();
        for (Map.Entry<String, NettyInvokeRecord> entry : doingTask.entrySet()) {
            NettyInvokeRecord nettyInvokeRecord = entry.getValue();
            if (nettyInvokeRecord.getTaskAddTimestamp() > before) {
                continue;
            }
            needRemove.add(entry.getKey());
        }

        for (String taskItemKey : needRemove) {
            NettyInvokeRecord nettyInvokeRecord = doingTask.remove(taskItemKey);
            if (nettyInvokeRecord == null) {
                continue;
            }
            log.warn("clean timeout task by task clean scheduler:{}", taskItemKey);
            nettyInvokeRecord.notifyDataArrival(null);
        }
    }
}
