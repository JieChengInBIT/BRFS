package com.bonree.brfs.client.impl;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bonree.brfs.client.BRFileSystem;
import com.bonree.brfs.client.StorageNameStick;
import com.bonree.brfs.client.route.DiskServiceSelectorCache;
import com.bonree.brfs.client.route.ServiceSelectorManager;
import com.bonree.brfs.common.ReturnCode;
import com.bonree.brfs.common.ZookeeperPaths;
import com.bonree.brfs.common.exception.BRFSException;
import com.bonree.brfs.common.http.client.HttpClient;
import com.bonree.brfs.common.http.client.HttpResponse;
import com.bonree.brfs.common.http.client.URIBuilder;
import com.bonree.brfs.common.service.Service;
import com.bonree.brfs.common.service.ServiceManager;
import com.bonree.brfs.common.service.impl.DefaultServiceManager;
import com.bonree.brfs.common.utils.BrStringUtils;
import com.bonree.brfs.common.utils.CloseUtils;

public class DefaultBRFileSystem implements BRFileSystem {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultBRFileSystem.class);
    private static final String URI_STORAGE_NAME_ROOT = "/storageName/";

    private static final String DEFAULT_SCHEME = "http";

    private HttpClient client = new HttpClient();
    private CuratorFramework zkClient;
    private ServiceManager serviceManager;

    private ServiceSelectorManager serviceSelectorManager;
    
    private Map<String, StorageNameStick> stickContainer = new HashMap<String, StorageNameStick>();

    public DefaultBRFileSystem(String zkAddresses, String cluster) throws Exception {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        zkClient = CuratorFrameworkFactory.newClient(zkAddresses, 3000, 15000, retryPolicy);
        zkClient.start();
        zkClient.blockUntilConnected();
        ZookeeperPaths zkPaths = ZookeeperPaths.getBasePath(cluster, zkAddresses);
        if(zkClient.checkExists().forPath(zkPaths.getBaseClusterName()) == null) {
            throw new BRFSException("cluster is not exist!!!");
        }
        serviceManager = new DefaultServiceManager(zkClient.usingNamespace(zkPaths.getBaseClusterName().substring(1)));
        serviceManager.start();
        this.serviceSelectorManager = new ServiceSelectorManager(serviceManager, zkClient, zkPaths.getBaseServerIdPath(), zkPaths.getBaseRoutePath());
    }

    @Override
    public boolean createStorageName(String storageName, Map<String, Object> attrs) {
        Service service;
        try {
            service = serviceSelectorManager.useDuplicaSelector().randomService();
            LOG.info("select server:" + service);
        } catch (Exception e1) {
            return false;
        }
        if (service == null) {
            throw new BRFSException("none aliver server!!!");
        }

        URIBuilder uriBuilder = new URIBuilder().setScheme(DEFAULT_SCHEME).setHost(service.getHost()).setPort(service.getPort()).setPath(URI_STORAGE_NAME_ROOT + storageName);

        for (Entry<String, Object> attr : attrs.entrySet()) {
            uriBuilder.addParameter(attr.getKey(), String.valueOf(attr.getValue()));
        }

        try {
            HttpResponse response = client.executePut(uriBuilder.build());
            String code = new String(response.getResponseBody());
            System.out.println(code);
            ReturnCode returnCode = ReturnCode.valueOf(code);
            returnCode = ReturnCode.checkCode(storageName, returnCode);
            return response.isReponseOK();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean updateStorageName(String storageName, Map<String, Object> attrs) {
        Service service;
        try {
            service = serviceSelectorManager.useDuplicaSelector().randomService();
            LOG.info("select server:" + service);
        } catch (Exception e1) {
            return false;
        }
        
        if (service == null) {
            throw new BRFSException("none aliver server!!!");
        }

        URIBuilder uriBuilder = new URIBuilder().setScheme(DEFAULT_SCHEME).setHost(service.getHost()).setPort(service.getPort()).setPath(URI_STORAGE_NAME_ROOT + storageName);

        for (Entry<String, Object> attr : attrs.entrySet()) {
            uriBuilder.addParameter(attr.getKey(), String.valueOf(attr.getValue()));
        }

        try {
            HttpResponse response = client.executePost(uriBuilder.build());
            ReturnCode returnCode = ReturnCode.valueOf(new String(response.getResponseBody()));
            ReturnCode.checkCode(storageName, returnCode);
            return response.isReponseOK();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean deleteStorageName(String storageName) {
        Service service;
        try {
            service = serviceSelectorManager.useDuplicaSelector().randomService();
            LOG.info("select server:" + service);
        } catch (Exception e1) {
            return false;
        }
        if (service == null) {
            throw new BRFSException("none aliver server!!!");
        }

        URI uri = new URIBuilder().setScheme(DEFAULT_SCHEME).setHost(service.getHost()).setPort(service.getPort()).setPath(URI_STORAGE_NAME_ROOT + storageName).build();

        try {
            HttpResponse response = client.executeDelete(uri);
            ReturnCode returnCode = ReturnCode.valueOf(new String(response.getResponseBody()));
            ReturnCode.checkCode(storageName, returnCode);
            return response.isReponseOK();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public StorageNameStick openStorageName(String storageName) {
    	StorageNameStick stick = stickContainer.get(storageName);
    	if(stick == null) {
    		synchronized (stickContainer) {
    			stick = stickContainer.get(storageName);
    			if(stick == null) {
    				Service service;
    		        try {
    		            service = serviceSelectorManager.useDuplicaSelector().randomService();
    		            LOG.info("select server:" + service);
    		        } catch (Exception e1) {
    		            return null;
    		        }
    		        
    		        if (service == null) {
    		            throw new BRFSException("none aliver server!!!");
    		        }

    		        URI uri = new URIBuilder().setScheme(DEFAULT_SCHEME).setHost(service.getHost()).setPort(service.getPort()).setPath(URI_STORAGE_NAME_ROOT + storageName).build();
    		        boolean existFalg = true;
    		        try {
    		            HttpResponse response = client.executeGet(uri);
    		            String code = BrStringUtils.fromUtf8Bytes(response.getResponseBody());
    		            int storageId = -1;
    		            try {
    		                storageId = Integer.parseInt(code);
    		            } catch (NumberFormatException e) {
    		                ReturnCode returnCode = ReturnCode.valueOf(code);
    		                ReturnCode.checkCode(storageName, returnCode);
    		                existFalg = false;
    		            }
    		            if (!existFalg) {
    		                return null;
    		            }
    		            System.out.println("get id---" + storageId);
    		            DiskServiceSelectorCache cache = serviceSelectorManager.useDiskSelector(storageId);
    		            stick = new DefaultStorageNameStick(storageName, storageId, client, cache, serviceSelectorManager.useDuplicaSelector());
    		            stickContainer.put(storageName, stick);
    		        } catch (Exception e) {
    		            e.printStackTrace();
    		        }
    			}
			}
    	}

        return stick;
    }

    @Override
    public void close() throws IOException {
        CloseUtils.closeQuietly(client);
        CloseUtils.closeQuietly(zkClient);
        CloseUtils.closeQuietly(serviceSelectorManager);
        
        for(StorageNameStick stick : stickContainer.values()) {
        	CloseUtils.closeQuietly(stick);
        }
        
        try {
            if (serviceManager != null) {
                serviceManager.stop();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
