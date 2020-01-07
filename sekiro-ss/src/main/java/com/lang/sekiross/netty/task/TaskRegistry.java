package com.lang.sekiross.netty.task;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lang.sekiro.netty.protocol.SekiroNatMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;

@Slf4j
public class TaskRegistry {

    private TaskRegistry() {}

    private static TaskRegistry instance = new TaskRegistry();

    public static TaskRegistry getInstance() {
        return instance;
    }

    private Map<String, NettyInvokeRecord> doingTask = Maps.newConcurrentMap();

    /**
     * 唯一表示当前任务
     * @param clientId uuid
     * @param group group
     * @param seq 原子操作i++：流程号
     * @return
     */
    private String genTaskItemKey(String clientId, String group, long seq) {
        return clientId + "---" + group + "---" + seq;
    }

    public synchronized void registerTask(NettyInvokeRecord nettyInvokeRecord) {
        doingTask.put(genTaskItemKey(nettyInvokeRecord.getClientId(), nettyInvokeRecord.getGroup(), nettyInvokeRecord.getTaskId()), nettyInvokeRecord);
    }

    /**
     * 从任务列表中获取对于的任务，调用响应的回调函数
     * @param clientId
     * @param group
     * @param taskId
     * @param sekiroNatMessage
     */
    public void forwardClientResponse(String clientId,String group, Long taskId, SekiroNatMessage sekiroNatMessage) {
        NettyInvokeRecord nettyInvokeRecord = doingTask.remove(genTaskItemKey(clientId,group, taskId));
        if (nettyInvokeRecord == null) {
            log.error("can not find invoke record for client: {}  taskId:{}", clientId, taskId);
            return;
        }
        nettyInvokeRecord.notifyDataArrival(sekiroNatMessage);
    }

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
