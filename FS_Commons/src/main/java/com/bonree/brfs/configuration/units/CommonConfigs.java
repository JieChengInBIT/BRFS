package com.bonree.brfs.configuration.units;

import com.bonree.brfs.configuration.ConfigUnit;

public final class CommonConfigs {
	/**
	 * 服务所在的集群名
	 */
	public static final ConfigUnit<String> CONFIG_CLUSTER_NAME =
			ConfigUnit.ofString("cluster.name", "brfs");
	/**
	 * Zookeeper集群的地址信息
	 */
	public static final ConfigUnit<String> CONFIG_ZOOKEEPER_ADDRESSES =
			ConfigUnit.ofString("zookeeper.addresses", "localhost:2181");
	
	private CommonConfigs() {}
}
