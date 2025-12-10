package cn.rhymed.data.turbo.config;

import lombok.*;

/**
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:10
 **/
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeleteConfig {

    /**
     * 主键ID
     */
    private String primaryId;

    /**
     * 每批次查询大小
     */
    private int fetchSize;

    /**
     * 每批次提交删除大小
     */
    private int batchSize;

    /**
     * 最大线程数
     **/
    private int maxThreadCount;
}
