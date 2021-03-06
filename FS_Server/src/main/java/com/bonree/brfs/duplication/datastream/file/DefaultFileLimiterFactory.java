package com.bonree.brfs.duplication.datastream.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bonree.brfs.common.service.Service;
import com.bonree.brfs.configuration.Configs;
import com.bonree.brfs.configuration.units.DuplicateNodeConfigs;
import com.bonree.brfs.duplication.DuplicationEnvironment;
import com.bonree.brfs.duplication.DuplicationNodeSelector;
import com.bonree.brfs.duplication.coordinator.DuplicateNode;
import com.bonree.brfs.duplication.coordinator.FileCoordinator;
import com.bonree.brfs.duplication.coordinator.FileNameBuilder;
import com.bonree.brfs.duplication.coordinator.FileNode;
import com.bonree.brfs.duplication.coordinator.FilePathBuilder;
import com.bonree.brfs.duplication.datastream.connection.DiskNodeConnection;
import com.bonree.brfs.duplication.datastream.connection.DiskNodeConnectionPool;
import com.bonree.brfs.duplication.storagename.StorageNameManager;
import com.bonree.brfs.duplication.storagename.StorageNameNode;
import com.bonree.brfs.server.identification.ServerIDManager;

public class DefaultFileLimiterFactory implements FileLimiterFactory {
	private static final Logger LOG = LoggerFactory.getLogger(DefaultFileLimiterFactory.class); 
	
	private FileCoordinator coordinator;
	private DuplicationNodeSelector duplicationNodeSelector;
	private StorageNameManager storageNameManager;
	private Service service;
	private ServerIDManager idManager;
	private DiskNodeConnectionPool connectionPool;
	
	private static final int FILE_CAPACITY = Configs.getConfiguration().GetConfig(DuplicateNodeConfigs.CONFIG_FILE_CAPACITY);
	
	public DefaultFileLimiterFactory(FileCoordinator coordinator,
			DuplicationNodeSelector duplicationNodeSelector,
			StorageNameManager storageNameManager,
			Service service,
			ServerIDManager idManager,
			DiskNodeConnectionPool connectionPool) {
		this.coordinator = coordinator;
		this.duplicationNodeSelector = duplicationNodeSelector;
		this.storageNameManager = storageNameManager;
		this.service = service;
		this.idManager = idManager;
		this.connectionPool = connectionPool;
	}

	@Override
	public FileLimiter create(long time, int storageId) {
		StorageNameNode storageNameNode = storageNameManager.findStorageName(storageId);
		LOG.info("get storageNameNode-->{}, {}", storageId, storageNameNode);
		if(storageNameNode == null) {
			return null;
		}
		
		DuplicateNode[] nodes = duplicationNodeSelector.getDuplicationNodes(storageId, storageNameNode.getReplicateCount());
		if(nodes.length == 0) {
			LOG.error("No available duplication node to build FileNode");
			//没有磁盘节点可用
			return null;
		}
		
		FileNode fileNode = new FileNode(time);
		fileNode.setName(FileNameBuilder.createFile(idManager, storageId, nodes));
		fileNode.setStorageName(storageNameNode.getName());
		fileNode.setStorageId(storageNameNode.getId());
		fileNode.setServiceId(service.getServiceId());
		fileNode.setServiceGroup(service.getServiceGroup());
		fileNode.setDuplicateNodes(nodes);
		
		int capacity = 0;
		boolean allNodeOpened = true;
		for(DuplicateNode node : nodes) {
			LOG.info("start init node[{}] for file[{}]", node, fileNode.getName());
			if(node.getGroup().equals(DuplicationEnvironment.VIRTUAL_SERVICE_GROUP)) {
				LOG.info("Ingore virtual service[{}] to write head for file[{}]", node, fileNode.getName());
				continue;
			}
			
			DiskNodeConnection connection = connectionPool.getConnection(node);
			if(connection == null || connection.getClient() == null) {
				LOG.info("can not write header for file[{}] because [{}] is disconnected", fileNode.getName(), node);
				allNodeOpened = false;
				continue;
			}
			
			String serverId = idManager.getOtherSecondID(node.getId(), storageId);
			String filePath = FilePathBuilder.buildFilePath(fileNode.getStorageName(), serverId, fileNode.getCreateTime(), fileNode.getName());
			
			int result = connection.getClient().openFile(filePath, FILE_CAPACITY);
			LOG.info("open file[{}] at node[{}] get capacity[{}]", filePath, node, result);
			
			if(result < 0) {
				allNodeOpened = false;
			} else {
				capacity = result;
			}
		}
		
		//如果没有一个磁盘节点写入头数据成功，则放弃使用此文件节点
		if(capacity == 0) {
			LOG.error("can not open file at any duplicate node for file[{}]", fileNode.getName());
			return null;
		}
		
		FileLimiter fileLimiter = new FileLimiter(fileNode, capacity);
		fileLimiter.incrementSequenceBy(1);
		fileLimiter.setLength(2);
		
		if(!allNodeOpened) {
			//因为有部分磁盘节点的打开操作失败，所有文件需要设置为同步状态，这样在
			//写完第一批数据的时候，文件会进入同步状态
			fileLimiter.setSync(true);
		}
		
		try {
			coordinator.store(fileNode);
			//只有把文件信息成功存入文件库中才能使用此文件节点
			return fileLimiter;
		} catch (Exception e) {
			LOG.error("store file node[{}] error!", fileNode.getName(), e);
		}
		
		return null;
	}
}
