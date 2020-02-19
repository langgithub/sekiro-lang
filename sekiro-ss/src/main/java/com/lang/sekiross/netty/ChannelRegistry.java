package com.lang.sekiross.netty;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.commons.lang3.StringUtils;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChannelRegistry {

    private ChannelRegistry() {}

    private static ChannelRegistry instance = new ChannelRegistry();

    public static ChannelRegistry getInstance() {
        return instance;
    }

    private Map<String, ClientGroup> clientGroupMap = Maps.newConcurrentMap();

    // 处理下限容器
    private Map<SocketAddress, String> other = Maps.newConcurrentMap();

    public Map<SocketAddress, String> getOther() {
        return other;
    }

    /**
     * 根据group查询ClientGroup，没有就创建
     * @param group
     * @return ClientGroup
     */
    private ClientGroup createOrGet(String group) {
        ClientGroup clientGroup = clientGroupMap.get(group);
        if (clientGroup != null) {
            return clientGroup;
        }
        synchronized (ChannelRegistry.class) {
            clientGroup = clientGroupMap.get(group);
            if (clientGroup != null) {
                return clientGroup;
            }
            clientGroup = new ClientGroup(group);
            clientGroupMap.put(group, clientGroup);
            return clientGroup;
        }
    }

    /**
     * 客户端分组保存
     */
    private static class ClientGroup {

        // 组代码
        private String group;
        // 组容器 map
        private Map<String, NatClient> natClientMap = Maps.newConcurrentMap();
        // 组容器 阻塞队列
        private BlockingDeque<NatClient> poolQueue = new LinkedBlockingDeque<>();

        public ClientGroup(String group) {
            this.group = group;
        }

        /**
         * 更具clientid 从natClientMap获取NatClient连接
         * @param clientId uuid类型（：f04e6b8d-c12a-4174-88d3-23fb9891521d）
         * @return NatClient
         */
        public NatClient getByClientId(String clientId) {
            NatClient ret = natClientMap.get(clientId);
            if (ret != null && !ret.getCmdChannel().isActive()) {
                natClientMap.remove(clientId);
            }
            return ret;
        }

        /**
         * 从阻塞队列中获取一个NatClient 连接
         * @return NatClient
         */
        public NatClient allocateOne() {
            while (true) {
                NatClient poll = poolQueue.poll();
                if (poll == null) {
                    log.info("poolQueue(NatClient) is empty");
                    return null;
                }
                if (!poll.getCmdChannel().isActive()) {
                    //TODO queue 的数据结构不合理，需要支持线性remove
                    NatClient realNatClient = natClientMap.get(poll.getClientId());
                    if (realNatClient == poll) {
                        log.info("remove channel for client:{}", poll.getClientId());
                        natClientMap.remove(poll.getClientId());
                    }
                    continue;
                }

                poolQueue.add(poll);
                return poll;
            }

        }

        /**
         * 注册接口及注册通道 保存NatClient到natClientMap，poolQueue两个容器中
         * @param client uuid
         * @param cmdChannel ServerSocketChannel
         */
        public synchronized void registryClient(String client, Channel cmdChannel) {
            NatClient natClient = natClientMap.get(client);
            if (natClient != null) {
                Channel cmdChannelOld = natClient.getCmdChannel();
                if (cmdChannelOld != cmdChannel) {
                    log.info("old channel exist,attach again，oldChannel:{}  now channel:{} client:{}", cmdChannelOld, cmdChannel, client);
                    natClient.attachChannel(cmdChannel);
                }
                return;
            }
            log.info("register a client :{} with channel:{} ", client, cmdChannel);
            natClient = new NatClient(client, group, cmdChannel);
            natClientMap.put(client, natClient);
            poolQueue.add(natClient);
            ChannelRegistry.getInstance().getOther().put(cmdChannel.remoteAddress(),group);
        }


    }

    /**
     * 注册远程调用
     * @param client
     * @param cmdChannel
     */
    public synchronized void registryClient(String client, Channel cmdChannel) {
        log.info("register for client:{}", client);
        int index = client.indexOf("@");
        if (index < 0) {
            return;
        }
        String[] clientAndGroup = client.split("@");
        String group = clientAndGroup[1];
        String clientId = clientAndGroup[0];
        createOrGet(group).registryClient(clientId, cmdChannel);
    }

    /**
     * 更具group->ClientGroup->NatClient
     * @param group
     * @return NatClient
     */
    public NatClient allocateOne(String group) {
        if (StringUtils.isBlank(group)) {
            group = "default";
        }
        return createOrGet(group).allocateOne();
    }

    public NatClient queryByClient(String group, String clientId) {
        if (StringUtils.isBlank(group)) {
            return queryByClient(clientId);
        }
        return createOrGet(group).getByClientId(clientId);
    }

    private NatClient queryByClient(String clientId) {
        for (ClientGroup clientGroup : clientGroupMap.values()) {
            NatClient natClient = clientGroup.getByClientId(clientId);
            if (natClient != null) {
                Channel cmdChannel = natClient.getCmdChannel();
                if (cmdChannel != null && cmdChannel.isActive()) {
                    return natClient;
                }
            }
        }
        return null;
    }

    public List<String> channelStatus(String group) {
        if (group == null) {
            return Collections.emptyList();
        }
        ClientGroup clientGroup = clientGroupMap.get(group);
        if (clientGroup == null) {
            return Collections.emptyList();
        }
        Collection<NatClient> natClients = clientGroup.natClientMap.values();
        List<String> clientVo = Lists.newArrayList();
        for (NatClient natClient : natClients) {
            if (natClient.getCmdChannel() != null && natClient.getCmdChannel().isActive()) {
                clientVo.add(natClient.getClientId());
            }
        }
        return clientVo;
    }


    public List<String> channelList() {
        return Lists.newArrayList(clientGroupMap.keySet());
    }

}
