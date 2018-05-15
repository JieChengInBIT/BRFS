package com.bonree.brfs.disknode.server.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bonree.brfs.common.http.HandleResult;
import com.bonree.brfs.common.http.HandleResultCallback;
import com.bonree.brfs.common.http.HttpMessage;
import com.bonree.brfs.common.http.MessageHandler;
import com.bonree.brfs.disknode.DiskContext;
import com.bonree.brfs.disknode.data.read.DataFileReader;

public class ReadMessageHandler implements MessageHandler {
	private static final Logger LOG = LoggerFactory.getLogger(ReadMessageHandler.class);
	
	public static final String PARAM_READ_OFFSET = "offset";
	public static final String PARAM_READ_LENGTH = "size";
	
	private DiskContext diskContext;
	
	public ReadMessageHandler(DiskContext context) {
		this.diskContext = context;
	}

	@Override
	public void handle(HttpMessage msg, HandleResultCallback callback) {
		HandleResult result = new HandleResult();
		
		String offsetParam = msg.getParams().get(PARAM_READ_OFFSET);
		String lengthParam = msg.getParams().get(PARAM_READ_LENGTH);
		int offset = offsetParam == null ? 0 : Integer.parseInt(offsetParam);
		int length = lengthParam == null ? Integer.MAX_VALUE : Integer.parseInt(lengthParam);
		
		LOG.info("read data offset[{}], size[{}]", offset, length);
		
		byte[] data = DataFileReader.readFile(diskContext.getConcreteFilePath(msg.getPath()), offset, length);
		
		result.setSuccess(data == null ? false : true);
		result.setData(data);
		callback.completed(result);
	}

}
