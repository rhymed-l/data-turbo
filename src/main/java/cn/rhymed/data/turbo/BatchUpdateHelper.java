package cn.rhymed.data.turbo;

import cn.rhymed.data.turbo.config.BatchUpdateConfig;
import cn.rhymed.data.turbo.config.DataTurboProperties;
import cn.rhymed.data.turbo.context.BatchUpdateContext;

/**
 * 批量更新帮助类
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-01-15
 **/
public class BatchUpdateHelper {

    private static DataTurboProperties properties;

    /**
     * 设置配置属性（由自动配置类调用）
     */
    public static void setProperties(DataTurboProperties dataTurboProperties) {
        properties = dataTurboProperties;
    }

    public static void execute(Update update) {
        execute(null, update);
    }

    public static void execute(BatchUpdateConfig batchUpdateConfig, Update update) {
        startBatchUpdate(batchUpdateConfig);
        update.doUpdate();
    }

    private static void startBatchUpdate(BatchUpdateConfig batchUpdateConfig) {
        if (batchUpdateConfig == null) {
            // 使用配置文件中的默认值
            batchUpdateConfig = getDefaultConfig();
        }
        BatchUpdateContext.setConfig(batchUpdateConfig);
    }

    /**
     * 获取默认配置（从配置文件或使用内置默认值）
     */
    private static BatchUpdateConfig getDefaultConfig() {
        if (properties != null && properties.getBatchUpdate() != null) {
            DataTurboProperties.BatchUpdate bu = properties.getBatchUpdate();
            return BatchUpdateConfig.builder()
                    .primaryId(bu.getPrimaryId())
                    .fetchSize(bu.getFetchSize())
                    .batchSize(bu.getBatchSize())
                    .maxThreadCount(bu.getMaxThreadCount())
                    .build();
        }

        // 如果没有配置（非 Spring Boot 环境），使用内置默认值
        return BatchUpdateConfig.builder()
                .primaryId(null)  // null 表示自动推断
                .fetchSize(5000)
                .batchSize(50000)
                .maxThreadCount(3)
                .build();
    }
}
