package cn.rhymed.data.turbo.config;

import lombok.*;

/**
 * 批量查询配置
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2026-06-01
 **/
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class BatchSelectConfig {

    /**
     * 主键ID
     */
    private String primaryId;

    /**
     * 每批次查询大小
     */
    private int fetchSize;
}
