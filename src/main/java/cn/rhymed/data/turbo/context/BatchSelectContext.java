package cn.rhymed.data.turbo.context;

import cn.rhymed.data.turbo.config.BatchSelectConfig;

import java.util.List;
import java.util.function.Consumer;

/**
 * 批量查询上下文
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2026-06-01
 **/
public class BatchSelectContext {

    protected static final ThreadLocal<BatchSelectConfig> LOCAL_BATCH_SELECT_CONFIG = new ThreadLocal<>();
    protected static final ThreadLocal<Consumer<List<?>>> LOCAL_BATCH_CONSUMER = new ThreadLocal<>();

    public static void setConfig(BatchSelectConfig config) {
        LOCAL_BATCH_SELECT_CONFIG.set(config);
    }

    public static BatchSelectConfig getConfig() {
        return LOCAL_BATCH_SELECT_CONFIG.get();
    }

    public static void setBatchConsumer(Consumer<List<?>> consumer) {
        LOCAL_BATCH_CONSUMER.set(consumer);
    }

    @SuppressWarnings("unchecked")
    public static Consumer<List<?>> getBatchConsumer() {
        return LOCAL_BATCH_CONSUMER.get();
    }

    public static void clearConfig() {
        LOCAL_BATCH_SELECT_CONFIG.remove();
        LOCAL_BATCH_CONSUMER.remove();
    }
}
