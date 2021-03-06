package com.bonree.brfs.duplication.storagename.handler;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bonree.brfs.common.net.http.HandleResultCallback;
import com.bonree.brfs.common.net.http.HttpMessage;
import com.bonree.brfs.common.net.http.MessageHandler;
import com.bonree.brfs.duplication.storagename.StorageNameNode;
import com.google.common.base.Splitter;

public abstract class StorageNameMessageHandler implements MessageHandler {
	private static final Logger LOG = LoggerFactory.getLogger(StorageNameMessageHandler.class);
	
	private static final String PARAM_REPLICATION = "replicas";
	private static final String PARAM_TTL = "ttl";
	private static final String PARAM_ENABLE = "enable";

	@Override
	public void handle(HttpMessage msg, HandleResultCallback callback) {
		StorageNameMessage message = new StorageNameMessage();
		message.setName(parseName(msg.getPath()));
		
		LOG.info("handle StorageName[{}]", message.getName());
		
		Map<String, String> params = msg.getParams();
		LOG.info("params = {}", params);
		if(params.containsKey(PARAM_REPLICATION)) {
			message.addAttribute(StorageNameNode.ATTR_REPLICATION, Integer.parseInt(params.get(PARAM_REPLICATION)));
		}
		
		if(params.containsKey(PARAM_TTL)) {
			message.addAttribute(StorageNameNode.ATTR_TTL, Integer.parseInt(params.get(PARAM_TTL)));
		}
		
		if(params.containsKey(PARAM_ENABLE)) {
			message.addAttribute(StorageNameNode.ATTR_ENABLE, Boolean.parseBoolean(params.get(PARAM_ENABLE)));
		}
		
		handleMessage(message, callback);
	}
	
	protected abstract void handleMessage(StorageNameMessage msg, HandleResultCallback callback);
	
	private String parseName(String uri) {
		return Splitter.on('/').omitEmptyStrings().trimResults().splitToList(uri).get(0);
	}

	@Override
	public boolean isValidRequest(HttpMessage message) {
		return !parseName(message.getPath()).isEmpty();
	}

}
