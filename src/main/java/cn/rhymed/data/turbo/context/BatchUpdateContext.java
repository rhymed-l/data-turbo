package cn.rhymed.data.turbo.context;

import cn.rhymed.data.turbo.config.BatchUpdateConfig;

/**
 * 批量更新上下文
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-01-15
 **/
public class BatchUpdateContext {

    protected static final ThreadLocal<BatchUpdateConfig> LOCAL_BATCH_UPDATE_CONFIG = new ThreadLocal<>();

    public static void setConfig(BatchUpdateConfig batchUpdateConfig) {
        LOCAL_BATCH_UPDATE_CONFIG.set(batchUpdateConfig);
    }

    public static BatchUpdateConfig getConfig() {
        return LOCAL_BATCH_UPDATE_CONFIG.get();
    }

    public static void clearConfig() {
        LOCAL_BATCH_UPDATE_CONFIG.remove();
    }
}
