package com.bonree.brfs.disknode.client;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bonree.brfs.common.net.http.client.ClientConfig;
import com.bonree.brfs.common.net.http.client.HttpClient;
import com.bonree.brfs.common.net.http.client.HttpResponse;
import com.bonree.brfs.common.net.http.client.URIBuilder;
import com.bonree.brfs.common.utils.BrStringUtils;
import com.bonree.brfs.common.utils.ProtoStuffUtils;
import com.bonree.brfs.disknode.DiskContext;
import com.bonree.brfs.disknode.server.handler.data.FileCopyMessage;
import com.bonree.brfs.disknode.server.handler.data.FileInfo;
import com.bonree.brfs.disknode.server.handler.data.WriteData;
import com.bonree.brfs.disknode.server.handler.data.WriteResult;
import com.google.common.primitives.Ints;

public class HttpDiskNodeClient implements DiskNodeClient {
	private static final Logger LOG = LoggerFactory.getLogger(HttpDiskNodeClient.class);
	
	private static final String DEFAULT_SCHEME = "http";
	
	private HttpClient client;

	private String host;
	private int port;
	
	public HttpDiskNodeClient(String host, int port) {
		this(host, port, ClientConfig.DEFAULT);
	}
	
	public HttpDiskNodeClient(String host, int port, ClientConfig clientConfig) {
		this.host = host;
		this.port = port;
		this.client = new HttpClient(clientConfig);
	}
	
	@Override
	public boolean ping() {
		URI uri = new URIBuilder()
		.setScheme(DEFAULT_SCHEME)
		.setHost(host)
		.setPort(port)
		.setPath(DiskContext.URI_PING_PONG_ROOT + "/")
		.build();
		
		try {
			HttpResponse response = client.executeGet(uri);
			return response.isReponseOK();
		} catch (Exception e) {
			LOG.error("ping to {}:{} error", host, port, e);
		}
		
		return false;
	}
	
	@Override
	public int openFile(String path, int capacity) {
		URI uri = new URIBuilder()
		.setScheme(DEFAULT_SCHEME)
		.setHost(host)
		.setPort(port)
		.setPath(DiskContext.URI_DISK_NODE_ROOT + path)
		.addParameter("capacity", String.valueOf(capacity))
		.build();
		
		try {
			LOG.info("open file[{}] with capacity[{}] to {}:{}", path, capacity, host, port);
			HttpResponse response = client.executePut(uri);
			LOG.info("open file[{}] response[{}]", path, response.getStatusCode());
			if(response.isReponseOK()) {
				return Ints.fromByteArray(response.getResponseBody());
			}
		} catch (Exception e) {
			LOG.error("open file[{}] at {}:{} error", path, host, port, e);
		}
		
		return -1;
	}

	@Override
	public WriteResult writeData(String path, int sequence, byte[] bytes) throws IOException {
		WriteData writeItem = new WriteData();
		writeItem.setDiskSequence(sequence);
		writeItem.setBytes(bytes);
		
		WriteResult[] results = writeDatas(path, new WriteData[] {writeItem});
		
		return results != null ? results[0] : null;
	}

	@Override
	public WriteResult writeData(String path, int sequence, byte[] bytes, int offset, int size)
			throws IOException {
		int length = Math.min(size, bytes.length - offset);
		byte[] copy = new byte[length];
		System.arraycopy(bytes, offset, copy, 0, length);
		
		return writeData(path, sequence, copy);
	}
	
	@Override
	public WriteResult[] writeDatas(String path, WriteData[] dataList) throws IOException {
		WriteDataList datas = new WriteDataList();
		datas.setDatas(dataList);
		
		URI uri = new URIBuilder()
		.setScheme(DEFAULT_SCHEME)
		.setHost(host)
		.setPort(port)
		.setPath(DiskContext.URI_DISK_NODE_ROOT + path)
		.build();
		
		try {
			LOG.info("write file[{}] with {} datas to {}:{}", path, dataList.length, host, port);
			HttpResponse response = client.executePost(uri, ProtoStuffUtils.serialize(datas));
			LOG.info("write file[{}] response[{}]", path, response.getStatusCode());
			if(response.isReponseOK()) {
				WriteResultList resultList = ProtoStuffUtils.deserialize(response.getResponseBody(), WriteResultList.class);
				return resultList.getWriteResults();
			}
		} catch (Exception e) {
			LOG.error("write file[{}] to {}:{} error", path, host, port, e);
		}
		
		return null;
	}
	
	@Override
	public boolean flush(String path) {
		URI uri = new URIBuilder()
		.setScheme(DEFAULT_SCHEME)
		.setHost(host)
		.setPort(port)
		.setPath(DiskContext.URI_FLUSH_NODE_ROOT + path)
		.build();
		
		try {
			LOG.info("flush file[{}] to {}:{}", path, host, port);
			HttpResponse response = client.executePost(uri);
			LOG.info("flush file[{}] response[{}]", path, response.getStatusCode());
			return response.isReponseOK();
		} catch (Exception e) {
			LOG.error("write file[{}] to {}:{} error", path, host, port, e);
		}
		
		return false;
	}

	@Override
	public byte[] readData(String path, int offset, int size)
			throws IOException {
		URI uri = new URIBuilder()
	    .setScheme(DEFAULT_SCHEME)
	    .setHost(host)
	    .setPort(port)
	    .setPath(DiskContext.URI_DISK_NODE_ROOT + path)
	    .addParameter("offset", String.valueOf(offset))
	    .addParameter("size", String.valueOf(size))
	    .build();

		byte[] result = null;
		try {
			LOG.info("read file[{}] with offset[{}], size[{}] to {}:{}", path, offset, size, host, port);
			HttpResponse response = client.executeGet(uri);
			LOG.info("read file[{}] response[{}]", path, response.getStatusCode());
			if(response.isReponseOK()) {
				result = response.getResponseBody();
			}
		} catch (Exception e) {
			LOG.error("read file[{}] with[offset={},size={}] at {}:{} error", path, offset, size, host, port, e);
		}
		
		return result;
	}

	@Override
	public boolean closeFile(String path) {
		URI uri = new URIBuilder()
	    .setScheme(DEFAULT_SCHEME)
	    .setHost(host)
	    .setPort(port)
	    .setPath(DiskContext.URI_DISK_NODE_ROOT + path)
	    .build();
		
		try {
			LOG.info("close file[{}] to {}:{}", path, host, port);
			HttpResponse response = client.executeClose(uri);
			LOG.info("close file[{}] response[{}]", path, response.getStatusCode());
			return response.isReponseOK();
		} catch (Exception e) {
			LOG.error("close file[{}] at {}:{} error", path, host, port, e);
		}
		
		return false;
	}

	@Override
	public boolean deleteFile(String path, boolean force) {
		URIBuilder builder = new URIBuilder();
		builder.setScheme(DEFAULT_SCHEME)
	    .setHost(host)
	    .setPort(port)
	    .setPath(DiskContext.URI_DISK_NODE_ROOT + path);
		
		if(force) {
			builder.addParameter("force");
		}

		try {
			LOG.info("delete file[{}] force[{}] to {}:{}", path, force, host, port);
			HttpResponse response = client.executeDelete(builder.build());
			LOG.info("delete file[{}] response[{}]", path, response.getStatusCode());
			return response.isReponseOK();
		} catch (Exception e) {
			LOG.error("delete file[{}] at {}:{} error", path, host, port, e);
		}

		return false;
	}

	@Override
	public boolean deleteDir(String path, boolean force, boolean recursive) {
		URIBuilder builder = new URIBuilder();
		builder.setScheme(DEFAULT_SCHEME)
	    .setHost(host)
	    .setPort(port)
	    .setPath(DiskContext.URI_DISK_NODE_ROOT + path);
		
		if(force) {
			builder.addParameter("force");
		}
		
		if(recursive) {
			builder.addParameter("recursive");
		}

		try {
			LOG.info("delete dir[{}] force[{}] recursive[{}] to {}:{}", path, force, recursive, host, port);
			HttpResponse response = client.executeDelete(builder.build());
			LOG.info("delete dir[{}] response[{}]", path, response.getStatusCode());
			return response.isReponseOK();
		} catch (Exception e) {
			LOG.error("delete dir[{}] at {}:{} error", path, host, port, e);
		}

		return false;
	}

	@Override
	public BitSet getWritingSequence(String path) {
		URI uri = new URIBuilder()
	    .setScheme(DEFAULT_SCHEME)
	    .setHost(host)
	    .setPort(port)
	    .setPath(DiskContext.URI_SEQUENCE_NODE_ROOT + path)
	    .build();
		
		try {
			LOG.info("get sequences from file[{}] to {}:{}", path, host, port);
			HttpResponse response = client.executeGet(uri);
			LOG.info("get sequences from file[{}] response[{}]", path, response.getStatusCode());
			if(response.isReponseOK()) {
				return BitSet.valueOf(response.getResponseBody());
			}
		} catch (Exception e) {
			LOG.error("get sequences of file[{}] at {}:{} error", path, host, port, e);
		}
		
		return null;
	}

	@Override
	public void copyFrom(String host, int port, String remotePath, String localPath) throws Exception {
		copyInner(FileCopyMessage.DIRECT_FROM_REMOTE, host, port, remotePath, localPath);
	}
	
	@Override
	public void copyTo(String host, int port, String localPath, String remotePath) throws Exception {
		copyInner(FileCopyMessage.DIRECT_TO_REMOTE, host, port, remotePath, localPath);
	}
	
	private void copyInner(int direct, String host, int port, String remotePath, String localPath) throws Exception {
		FileCopyMessage msg = new FileCopyMessage();
		msg.setDirect(direct);
		msg.setRemoteHost(host);
		msg.setRemotePort(port);
		msg.setRemotePath(remotePath);
		msg.setLocalPath(localPath);
		
		URI uri = new URIBuilder()
	    .setScheme(DEFAULT_SCHEME)
	    .setHost(host)
	    .setPort(port)
	    .setPath(DiskContext.URI_COPY_NODE_ROOT + "/")
	    .build();
		
		client.executePost(uri, ProtoStuffUtils.serialize(msg));
	}

	@Override
	public boolean recover(String path, RecoverInfo infos) {
		URI uri = new URIBuilder()
	    .setScheme(DEFAULT_SCHEME)
	    .setHost(host)
	    .setPort(port)
	    .setPath(DiskContext.URI_RECOVER_NODE_ROOT + path)
	    .build();
		
		try {
			LOG.info("recover file[{}] response[{}] to {}:{}", path, host, port);
			HttpResponse response = client.executePost(uri, ProtoStuffUtils.serialize(infos));
			LOG.info("recover file[{}] response[{}]", path, response.getStatusCode());
			
			return response.isReponseOK();
		} catch (Exception e) {
			LOG.error("recover file[{}] at {}:{} error", path, host, port, e);
		}
		
		return false;
	}

	@Override
	public byte[] getBytesBySequence(String path, int sequence) {
		URI uri = new URIBuilder()
	    .setScheme(DEFAULT_SCHEME)
	    .setHost(host)
	    .setPort(port)
	    .setPath(DiskContext.URI_SEQ_BYTE_NODE_ROOT + path)
	    .addParameter("seq", String.valueOf(sequence))
	    .build();
		
		try {
			LOG.info("get bytes from file[{}] by sequence[{}] to {}:{}", path, sequence, host, port);
			HttpResponse response = client.executeGet(uri);
			LOG.info("get bytes from file[{}] response[{}]", path, response.getStatusCode());
			if(response.isReponseOK()) {
				return response.getResponseBody();
			}
		} catch (Exception e) {
			LOG.error("get bytes of file[{}] with seq[{}] at {}:{} error", path, sequence, host, port, e);
		}
		
		return null;
	}

	@Override
	public List<FileInfo> listFiles(String path, int level) {
		URI uri = new URIBuilder()
	    .setScheme(DEFAULT_SCHEME)
	    .setHost(host)
	    .setPort(port)
	    .setPath(DiskContext.URI_LIST_NODE_ROOT + path)
	    .addParameter("level", String.valueOf(level))
	    .build();
		
		try {
			HttpResponse response = client.executeGet(uri);
			
			if(response.isReponseOK()) {
				JSONArray array = JSONArray.parseArray(BrStringUtils.fromUtf8Bytes(response.getResponseBody()));
				ArrayList<FileInfo> result = new ArrayList<FileInfo>();
				for(int i = 0; i < array.size(); i++) {
					JSONObject object = array.getJSONObject(i);
					FileInfo info = new FileInfo();
					info.setType(object.getIntValue("type"));
					info.setLevel(object.getIntValue("level"));
					info.setPath(object.getString("path"));
					result.add(info);
				}
				
				return result;
			}
		} catch (Exception e) {
			LOG.error("list files of dir[{}] with level[{}] at {}:{} error", path, level, host, port, e);
		}
		
		return null;
	}

	@Override
	public int[] getWritingFileMetaInfo(String path) {
		URI uri = new URIBuilder()
	    .setScheme(DEFAULT_SCHEME)
	    .setHost(host)
	    .setPort(port)
	    .setPath(DiskContext.URI_META_NODE_ROOT + path)
	    .build();
		
		try {
			LOG.info("get meta info from file[{}] to {}:{}", path, host, port);
			HttpResponse response = client.executeGet(uri);
			LOG.info("get meta info from file[{}] response[{}]", path, response.getStatusCode());
			
			if(response.isReponseOK()) {
				JSONObject json = JSONObject.parseObject(BrStringUtils.fromUtf8Bytes(response.getResponseBody()));
				int[] result = new int[2];
				result[0] = json.getIntValue("seq");
				result[1] = json.getIntValue("length");
				
				return result;
			}
		} catch (Exception e) {
			LOG.error("get meta info of file[{}] at {}:{} error", path, host, port, e);
		}
		
		return null;
	}
	
	@Override
	public void close() throws IOException {
		client.close();
	}

}
