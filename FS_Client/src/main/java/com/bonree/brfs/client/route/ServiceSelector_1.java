package com.bonree.brfs.client.route;

import java.util.List;

public interface ServiceSelector_1 {
    
    /** 概述：选择一个Service
     * @param params
     * @return
     * @user <a href=mailto:weizheng@bonree.com>魏征</a>
     */
    public ServiceMetaInfo selectService(String partPid,List<Integer> excludePot);
}
