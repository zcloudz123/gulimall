package com.gulimall.coupon.dao;

import com.gulimall.coupon.entity.CouponEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券信息
 * 
 * @author zhangyangyang
 * @email sunlightcs@gmail.com
 * @date 2020-06-04 23:04:17
 */
@Mapper
public interface CouponDao extends BaseMapper<CouponEntity> {
	
}
