package com.bonree.brfs.discover;

import com.bonree.brfs.common.ZookeeperPaths;
import com.bonree.brfs.common.service.Service;
import com.bonree.brfs.common.service.ServiceManager;
import com.bonree.brfs.common.service.impl.DefaultServiceManager;
import com.bonree.brfs.common.zookeeper.curator.CuratorClient;
import com.bonree.brfs.common.zookeeper.curator.cache.CuratorCacheFactory;
import com.bonree.brfs.configuration.Configuration;
import com.bonree.brfs.configuration.ServerConfig;
import com.bonree.brfs.rebalance.RebalanceManager;
import com.bonree.brfs.rebalance.task.ServerChangeTaskGenetor;
import com.bonree.brfs.configuration.Configuration.ConfigException;
import com.bonree.brfs.server.identification.ServerIDManager;

public class DiscoverTest2 {

    public static final String CONFIG_NAME2 = "E:/BRFS2/config/server.properties";
    public static final String HOME2 = "E:/BRFS2";

    public static void main(String[] args) throws InterruptedException {
        try {
            Configuration conf = Configuration.getInstance();
            conf.parse(CONFIG_NAME2);
            conf.printConfigDetail();
            ServerConfig serverConfig = ServerConfig.parse(conf, HOME2);
            CuratorCacheFactory.init(serverConfig.getZkHosts());
            ZookeeperPaths zookeeperPaths = ZookeeperPaths.create(serverConfig.getClusterName(), serverConfig.getZkHosts());
            ServerIDManager idManager = new ServerIDManager(serverConfig, zookeeperPaths);
            idManager.getSecondServerID(1); // TODO 模拟存储数据

            CuratorClient leaderClient = CuratorClient.getClientInstance(serverConfig.getZkHosts(), 1000, 1000);
            CuratorClient client = CuratorClient.getClientInstance(serverConfig.getZkHosts());

            ServiceManager sm = new DefaultServiceManager(client.getInnerClient().usingNamespace(zookeeperPaths.getBaseServersPath().substring(1, zookeeperPaths.getBaseServersPath().length())));
            sm.start();

            RebalanceManager rebalanceServer = new RebalanceManager(serverConfig.getZkHosts(), zookeeperPaths, idManager, sm);
            rebalanceServer.start();

            Service selfService = new Service();
            selfService.setHost(serverConfig.getHost());
            selfService.setPort(serverConfig.getPort());
            selfService.setServiceGroup("discover");
            selfService.setServiceId(idManager.getFirstServerID());
            sm.registerService(selfService);
            System.out.println(selfService);
            sm.addServiceStateListener("discover", new ServerChangeTaskGenetor(leaderClient, client, sm, idManager, zookeeperPaths.getBaseRebalancePath(), 3000));
            System.out.println("launch Server 2");
        } catch (ConfigException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
