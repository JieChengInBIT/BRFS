package com.bonree.brfs.duplication.storagename.handler;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bonree.brfs.common.ReturnCode;
import com.bonree.brfs.common.net.http.HandleResult;
import com.bonree.brfs.common.net.http.HandleResultCallback;
import com.bonree.brfs.common.utils.Attributes;
import com.bonree.brfs.common.utils.BrStringUtils;
import com.bonree.brfs.duplication.storagename.StorageNameManager;
import com.bonree.brfs.duplication.storagename.StorageNameNode;
import com.bonree.brfs.duplication.storagename.exception.StorageNameNonexistentException;

public class UpdateStorageNameMessageHandler extends StorageNameMessageHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateStorageNameMessageHandler.class);

    private StorageNameManager storageNameManager;

    public UpdateStorageNameMessageHandler(StorageNameManager storageNameManager) {
        this.storageNameManager = storageNameManager;
    }

    @Override
    public void handleMessage(StorageNameMessage msg, HandleResultCallback callback) {
        LOG.info("update storageName[{}], {}", msg.getName(), msg.getAttributes());
        HandleResult result = new HandleResult();
        boolean success = false;

        Attributes atts = msg.getAttributes();
        Set<String> attNames = atts.getAttributeNames();
        for (String name : attNames) {
            if (StorageNameNode.ATTR_TTL.equals(name)) {
                if (atts.getInt(name) == 0) {
                    result.setSuccess(false);
                    result.setData(BrStringUtils.toUtf8Bytes(ReturnCode.STORAGE_TTL_ERROR.name()));
                    callback.completed(result);
                    return;
                }
            }
        }

        try {
            success = storageNameManager.updateStorageName(msg.getName(), msg.getAttributes());
            result.setSuccess(success);
            result.setData(BrStringUtils.toUtf8Bytes(ReturnCode.SUCCESS.name()));
            if (!success) {
                result.setSuccess(false);
                result.setData(BrStringUtils.toUtf8Bytes(ReturnCode.STORAGE_OPT_ERROR.name()));
            }
        } catch (StorageNameNonexistentException e) {
            result.setSuccess(success);
            result.setData(BrStringUtils.toUtf8Bytes(ReturnCode.STORAGE_NONEXIST_ERROR.name()));
        }

        callback.completed(result);
    }

}
