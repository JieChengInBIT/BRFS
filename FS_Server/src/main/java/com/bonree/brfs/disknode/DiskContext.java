package com.bonree.brfs.disknode;

import java.io.File;

public class DiskContext {
	public static final String URI_PING_PONG_ROOT = "/ping";
	public static final String URI_DISK_NODE_ROOT = "/disk";
	public static final String URI_FLUSH_NODE_ROOT = "/flush";
	public static final String URI_SEQUENCE_NODE_ROOT = "sequence";
	public static final String URI_SEQ_BYTE_NODE_ROOT = "/bytes";
	public static final String URI_COPY_NODE_ROOT = "/copy";
	public static final String URI_LIST_NODE_ROOT = "/list";
	public static final String URI_META_NODE_ROOT = "/metadata";
	public static final String URI_RECOVER_NODE_ROOT = "/recover";
	
	private String workDirectory;
	
	public DiskContext(String workDir) {
		this.workDirectory = new File(workDir).getAbsolutePath();
	}
	
	public String getRootDir() {
		return this.workDirectory;
	}
	
	/**
	 * 从用户使用的逻辑路径转换为实际磁盘中的真实路径
	 * 
	 * @param logicPath
	 * @return
	 */
	public String getConcreteFilePath(String logicPath) {
		return new File(workDirectory, logicPath).getAbsolutePath();
	}
	
	/**
	 * 从磁盘上的真实路径转换为用户使用的逻辑路径
	 * 
	 * @param path
	 * @return
	 */
	public String getLogicFilePath(String path) {
		if(!path.startsWith(workDirectory)) {
			throw new IllegalArgumentException("path[" + path + "] isn't illegal real path");
		}
		
		return path.substring(workDirectory.length());
	}
}
