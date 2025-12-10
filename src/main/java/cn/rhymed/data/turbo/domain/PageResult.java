package cn.rhymed.data.turbo.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-09 23:24
 **/
@Getter
@Setter
@ToString
public class PageResult {
    /**
     * 当前分页数
     **/
    private Integer pageNum;
    /**
     * 开始ID
     **/
    private Long startKey;
    /**
     * 结束ID
     **/
    private Long endKey;
    /**
     * 当前分页大小
     **/
    private Integer pageSize;
}
