package com.gulimall.coupon.dao;

import com.gulimall.coupon.entity.MemberPriceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品会员价格
 * 
 * @author zhangyangyang
 * @email sunlightcs@gmail.com
 * @date 2020-06-04 23:04:17
 */
@Mapper
public interface MemberPriceDao extends BaseMapper<MemberPriceEntity> {
	
}
