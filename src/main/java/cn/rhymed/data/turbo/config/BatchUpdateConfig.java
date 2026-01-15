package cn.rhymed.data.turbo.config;

import lombok.*;

/**
 * 批量更新配置
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-01-15
 **/
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class BatchUpdateConfig {

    /**
     * 主键ID
     */
    private String primaryId;

    /**
     * 每批次查询大小
     */
    private int fetchSize;

    /**
     * 每批次提交更新大小
     */
    private int batchSize;

    /**
     * 最大线程数
     **/
    private int maxThreadCount;
}
