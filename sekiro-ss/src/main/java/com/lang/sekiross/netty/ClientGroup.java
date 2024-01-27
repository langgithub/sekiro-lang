package com.lang.sekiross.netty;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * 客户端分组保存
 */
@Slf4j
class ClientGroup {

    // 组代码
    private String group;
    // 同一组中的连接
    private Map<String, NatClient> natClientMap = Maps.newConcurrentMap();
    // 同一组中的连接对应的名称
    private LinkedList<String> poolQueue = new LinkedList<>();

    public ClientGroup(String group) {
        this.group = group;
    }

    synchronized String disconnect(String clientId) {
        NatClient natClient = natClientMap.get(clientId);
        natClientMap.remove(clientId);
        removeQueue(clientId);
        if (natClient == null) {
            return "no client: " + clientId;
        } else {
            natClient.getCmdChannel().close();
        }
        return null;
    }

    /**
     * 更具clientid 从natClientMap获取NatClient连接
     * @param clientId uuid类型（：f04e6b8d-c12a-4174-88d3-23fb9891521d）
     * @return NatClient
     */
    synchronized NatClient getByClientId(String clientId) {
        NatClient ret = natClientMap.get(clientId);
        if (ret == null) {
            return null;
        }
        if (!ret.getCmdChannel().isActive()) {
            natClientMap.remove(clientId);
            removeQueue(clientId);
        }
        return ret;
    }

    /**
     * 同一个组中的轮询clietid 用于下发任务
     * @return NatClient
     */
    synchronized NatClient allocateOne() {
        while (true) {
            String poll = poolQueue.poll();
            if (poll == null) {
                log.info("pool queue empty for group:{}", group);
                return null;
            }

            NatClient natClient = natClientMap.get(poll);
            if (natClient == null) {
                continue;
            }
            if (natClient.getCmdChannel() == null) {
                natClientMap.remove(poll);
                continue;
            }
            if (!natClient.getCmdChannel().isActive()) {
                natClientMap.remove(poll);
                continue;
            }
            poolQueue.add(poll);
            return natClient;
        }

    }

    /**
     * 注册接口及注册通道 保存NatClient到natClientMap，poolQueue两个容器中
     * @param client uuid
     * @param cmdChannel ServerSocketChannel
     */
    synchronized void registryClient(String client, Channel cmdChannel, NatClient.NatClientType natClientType) {
        NatClient oldNatClient = natClientMap.get(client);
        log.info("register a client :{} with channel:{} ", client, cmdChannel);
        NatClient natClient = new NatClient(client, group, cmdChannel, natClientType);
        if ((oldNatClient != null)) {
            // 另一台设备也使用另相同clientid
            natClient.migrateSeqGenerator(oldNatClient);
        }
        natClientMap.put(client, natClient);
        removeQueue(client);
        poolQueue.add(client);
    }

    private void removeQueue(String clientId) {
        while (poolQueue.remove(clientId)) ;
    }

    //对象操作全部加锁，防止并发紊乱
    synchronized List<NatClient> queue() {
        List<NatClient> ret = Lists.newArrayListWithCapacity(poolQueue.size());
        // java.util.ConcurrentModificationException
        for (String key : Lists.newArrayList(poolQueue)) {
            NatClient natClient = natClientMap.get(key);
            if (natClient == null) {
                natClientMap.remove(key);
                removeQueue(key);
                continue;
            }
            Channel cmdChannel = natClient.getCmdChannel();
            if (cmdChannel == null || !cmdChannel.isActive()) {
                natClientMap.remove(key);
                removeQueue(key);
                continue;
            }
            ret.add(natClient);
        }
        return ret;
    }

}