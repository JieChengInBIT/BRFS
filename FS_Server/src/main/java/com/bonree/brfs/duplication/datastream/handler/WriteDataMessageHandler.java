package com.bonree.brfs.duplication.datastream.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bonree.brfs.common.net.http.HandleResult;
import com.bonree.brfs.common.net.http.HandleResultCallback;
import com.bonree.brfs.common.net.http.HttpMessage;
import com.bonree.brfs.common.net.http.MessageHandler;
import com.bonree.brfs.common.proto.FileDataProtos.FileContent;
import com.bonree.brfs.common.utils.JsonUtils;
import com.bonree.brfs.common.utils.ProtoStuffUtils;
import com.bonree.brfs.common.write.data.DataItem;
import com.bonree.brfs.common.write.data.FileEncoder;
import com.bonree.brfs.common.write.data.WriteDataMessage;
import com.bonree.brfs.duplication.datastream.DataHandleCallback;
import com.bonree.brfs.duplication.datastream.DataWriteResult;
import com.bonree.brfs.duplication.datastream.DuplicateWriter;
import com.bonree.brfs.duplication.datastream.tasks.ResultItem;
import com.bonree.brfs.duplication.storagename.StorageNameManager;
import com.bonree.brfs.duplication.storagename.StorageNameNode;
import com.google.protobuf.ByteString;

public class WriteDataMessageHandler implements MessageHandler {
	private static final Logger LOG = LoggerFactory.getLogger(WriteDataMessageHandler.class);
	
	private DuplicateWriter duplicateWriter;
	private StorageNameManager storageNameManager;
	
	public WriteDataMessageHandler(DuplicateWriter duplicateWriter,StorageNameManager snManager) {
		this.duplicateWriter = duplicateWriter;
		this.storageNameManager = snManager;
	}

	@Override
	public void handle(HttpMessage msg, HandleResultCallback callback) {
		WriteDataMessage writeMsg = ProtoStuffUtils.deserialize(msg.getContent(), WriteDataMessage.class);
		StorageNameNode node = storageNameManager.findStorageName(writeMsg.getStorageNameId());
		
		if(node == null || !node.isEnable()) {
            callback.completed(new HandleResult(false));
            return;
		}
		
		DataItem[] items = writeMsg.getItems();
		LOG.debug("Writing DataItem[{}]", items.length);
		
		if(items == null || items.length == 0) {
			callback.completed(new HandleResult(true));
			return;
		}
		
		for(DataItem item : items) {
			FileContent content = FileContent.newBuilder()
					.setCompress(0)
					.setDescription("")
					.setData(ByteString.copyFrom(item.getBytes()))
					.setCrcFlag(false)
					.setCrcCheckCode(0)
					.build();
			
			try {
				byte[] bytes = FileEncoder.contents(content);
				item.setBytes(bytes);
			} catch (Exception e) {
				LOG.error("encode file content error", e);
			}
		}
		
		duplicateWriter.write(writeMsg.getStorageNameId(), items, new DataWriteCallback(callback));
	}

	private class DataWriteCallback implements DataHandleCallback<DataWriteResult> {
		
		private HandleResultCallback callback;
		
		public DataWriteCallback(HandleResultCallback callback) {
			this.callback = callback;
		}

		@Override
		public void completed(DataWriteResult writeResult) {
			HandleResult result = new HandleResult();
			result.setSuccess(true);
			
			ResultItem[] resultItems = writeResult.getItems();
			result.setData(JsonUtils.toJsonBytes(resultItems));
			
			callback.completed(result);
		}

		@Override
		public void error(Throwable t) {
			HandleResult result = new HandleResult();
			result.setSuccess(false);
			result.setCause(t);
			
			callback.completed(result);
		}
		
	}

	@Override
	public boolean isValidRequest(HttpMessage message) {
		return true;
	}
}
