package com.gulimall.product.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @decription:
 * @author: zyy
 * @date 2020-06-30-11:40
 */
@Data
public class SeckillInfoVo {

    private Long id;
    /**
     * 活动id
     */
    private Long promotionId;
    /**
     * 活动场次id
     */
    private Long promotionSessionId;
    /**
     * 商品id
     */
    private Long skuId;

    //商品秒杀随机码，由于防恶意刷单攻击
    private String randomCode;
    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;
    /**
     * 秒杀总量
     */
    private BigDecimal seckillCount;
    /**
     * 每人限购数量
     */
    private BigDecimal seckillLimit;
    /**
     * 排序
     */
    private Integer seckillSort;

    private Long startTime;

    private Long endTime;
}
