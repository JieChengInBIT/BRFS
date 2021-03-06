package com.bonree.brfs.disknode.server.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bonree.brfs.common.net.http.HandleResult;
import com.bonree.brfs.common.net.http.HandleResultCallback;
import com.bonree.brfs.common.net.http.HttpMessage;
import com.bonree.brfs.common.net.http.MessageHandler;
import com.bonree.brfs.common.write.data.FileEncoder;
import com.bonree.brfs.disknode.DiskContext;
import com.bonree.brfs.disknode.data.write.FileWriterManager;
import com.bonree.brfs.disknode.data.write.RecordFileWriter;
import com.bonree.brfs.disknode.data.write.worker.WriteWorker;
import com.bonree.brfs.disknode.utils.Pair;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

public class OpenMessageHandler implements MessageHandler {
	private static final Logger LOG = LoggerFactory.getLogger(OpenMessageHandler.class);
	
	private static final int DEFAULT_HEADER_VERSION = 0;
	private static final int DEFAULT_HEADER_TYPE = 0;
	
	private static final int DEFAULT_HEADER_SIZE = 2;
	private static final int DEFAULT_TAILER_SIZE = 9;
	
	private DiskContext diskContext;
	private FileWriterManager writerManager;
	
	public OpenMessageHandler(DiskContext diskContext, FileWriterManager writerManager) {
		this.diskContext = diskContext;
		this.writerManager = writerManager;
	}

	@Override
	public boolean isValidRequest(HttpMessage message) {
		return true;
	}

	@Override
	public void handle(HttpMessage msg, HandleResultCallback callback) {
		HandleResult result = new HandleResult();
		String realPath = null;
		try {
			realPath = diskContext.getConcreteFilePath(msg.getPath());
			int capacity = Integer.parseInt(msg.getParams().get("capacity"));
			LOG.info("open file [{}] with capacity[{}]", realPath, capacity);
			
			Pair<RecordFileWriter, WriteWorker> binding = writerManager.getBinding(realPath, true);
			if(binding == null) {
				LOG.error("get file writer for file[{}] error!", realPath);
				result.setSuccess(false);
				return;
			}
			
			byte[] header = Bytes.concat(FileEncoder.start(), FileEncoder.header(DEFAULT_HEADER_VERSION, DEFAULT_HEADER_TYPE));
			binding.first().updateSequence(0);
			binding.first().write(header);
			binding.first().flush();
			
			result.setSuccess(true);
			result.setData(Ints.toByteArray(capacity - DEFAULT_HEADER_SIZE - DEFAULT_TAILER_SIZE));
		} catch (Exception e) {
			LOG.error("write header to file[{}] error!", realPath);
			result.setSuccess(false);
		} finally {
			callback.completed(result);
		}
	}

}
