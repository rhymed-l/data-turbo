package cn.rhymed.data.turbo.context;

import cn.rhymed.data.turbo.config.BatchDeleteConfig;

/**
 * 批量删除上下文
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:09
 **/
public class BatchDeleteContext {

    protected static final ThreadLocal<BatchDeleteConfig> LOCAL_BATCH_DELETE_CONFIG = new ThreadLocal<>();

    public static void setConfig(BatchDeleteConfig batchDeleteConfig) {
        LOCAL_BATCH_DELETE_CONFIG.set(batchDeleteConfig);
    }

    public static BatchDeleteConfig getConfig() {
        return LOCAL_BATCH_DELETE_CONFIG.get();
    }

    public static void clearConfig() {
        LOCAL_BATCH_DELETE_CONFIG.remove();
    }
}
