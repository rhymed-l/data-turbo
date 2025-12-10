package cn.rhymed.data.turbo.config;

import lombok.*;

/**
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:47
 **/
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PageConfig {

    /**
     * 主键ID
     */
    private String primaryId;

    /**
     * 分页大小
     */
    private int pageSize;

}
