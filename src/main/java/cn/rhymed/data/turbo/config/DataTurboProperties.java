package cn.rhymed.data.turbo.config;

import lombok.Data;

/**
 * Data Turbo 配置属性
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10
 **/
@Data
public class DataTurboProperties {

    /**
     * 批量删除默认配置
     */
    private BatchDelete batchDelete = new BatchDelete();

    /**
     * 批量更新默认配置
     */
    private BatchUpdate batchUpdate = new BatchUpdate();

    @Data
    public static class BatchDelete {
        /**
         * 默认主键字段名
         */
        private String primaryId = null;

        /**
         * 每批次查询/删除大小，默认 5000
         */
        private int fetchSize = 5000;

        /**
         * 每批次提交删除大小，默认 50000
         */
        private int batchSize = 50000;

        /**
         * 最大线程数，默认 3
         */
        private int maxThreadCount = 3;
    }

    @Data
    public static class BatchUpdate {
        /**
         * 默认主键字段名
         */
        private String primaryId = null;

        /**
         * 每批次查询/更新大小，默认 5000
         */
        private int fetchSize = 5000;

        /**
         * 每批次提交更新大小，默认 50000
         */
        private int batchSize = 50000;

        /**
         * 最大线程数，默认 3
         */
        private int maxThreadCount = 3;
    }
}
