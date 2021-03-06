package com.bonree.brfs.common.sequencenumber;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;

public class ZkSequenceNumberBuilder implements SequenceNumberBuilder {
	private static final Logger LOG = LoggerFactory.getLogger(ZkSequenceNumberBuilder.class);
	
	private static final String NUMBER_PREFIX = "n_";
	
	private CuratorFramework client;
	private final String numberContainerPath;
	
	public ZkSequenceNumberBuilder(CuratorFramework client, String numberContainerPath) {
		this.client = client;
		this.numberContainerPath = numberContainerPath;
	}

	@Override
	public int nextSequenceNumber() {
		String numberPath = null;
		try {
			numberPath = client.create().creatingParentsIfNeeded()
					.withMode(CreateMode.PERSISTENT_SEQUENTIAL)
					.forPath(ZKPaths.makePath(numberContainerPath, NUMBER_PREFIX));
			
			if(numberPath != null) {
				return Integer.parseInt(Splitter.on("_").splitToList(ZKPaths.getNodeFromPath(numberPath)).get(1));
			}
		} catch (Exception e) {
			LOG.error("get sequence number error!", e);
		} finally {
			if(numberPath != null) {
				try {
					client.delete().quietly().forPath(numberPath);
				} catch (Exception e) {
					LOG.error("delete sequence number node[{}] error!", numberPath, e);
				}
			}
		}
		
		return -1;
	}

}
