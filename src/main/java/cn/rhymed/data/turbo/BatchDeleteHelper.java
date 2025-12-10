package cn.rhymed.data.turbo;

import cn.rhymed.data.turbo.config.BatchDeleteConfig;
import cn.rhymed.data.turbo.config.DataTurboProperties;
import cn.rhymed.data.turbo.context.BatchDeleteContext;

/**
 * 批量删除帮助类
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-09 23:33
 **/
public class BatchDeleteHelper {

    private static DataTurboProperties properties;

    /**
     * 设置配置属性（由自动配置类调用）
     */
    public static void setProperties(DataTurboProperties dataTurboProperties) {
        properties = dataTurboProperties;
    }

    public static void execute(Delete delete) {
        execute(null, delete);
    }

    public static void execute(BatchDeleteConfig batchDeleteConfig, Delete delete) {
        startBatchDelete(batchDeleteConfig);
        delete.doDelete();
    }

    private static void startBatchDelete(BatchDeleteConfig batchDeleteConfig) {
        if (batchDeleteConfig == null) {
            // 使用配置文件中的默认值
            batchDeleteConfig = getDefaultConfig();
        }
        BatchDeleteContext.setConfig(batchDeleteConfig);
    }

    /**
     * 获取默认配置（从配置文件或使用内置默认值）
     */
    private static BatchDeleteConfig getDefaultConfig() {
        if (properties != null) {
            DataTurboProperties.BatchDelete bd = properties.getBatchDelete();
            return BatchDeleteConfig.builder()
                    .primaryId(bd.getPrimaryId())
                    .fetchSize(bd.getFetchSize())
                    .batchSize(bd.getBatchSize())
                    .maxThreadCount(bd.getMaxThreadCount())
                    .build();
        }

        // 如果没有配置（非 Spring Boot 环境），使用内置默认值
        return BatchDeleteConfig.builder()
                .primaryId(null)  // null 表示自动推断
                .fetchSize(5000)
                .batchSize(50000)
                .maxThreadCount(3)
                .build();
    }
}
