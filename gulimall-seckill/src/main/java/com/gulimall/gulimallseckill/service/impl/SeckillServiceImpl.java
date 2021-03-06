package com.gulimall.gulimallseckill.service.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.gulimall.common.to.mq.SeckillOrderTo;
import com.gulimall.common.utils.R;
import com.gulimall.common.vo.MemberRespVo;
import com.gulimall.gulimallseckill.feign.CouponFeignService;
import com.gulimall.gulimallseckill.feign.ProductFeignService;
import com.gulimall.gulimallseckill.interceptor.LoginUserInterceptor;
import com.gulimall.gulimallseckill.service.SeckillService;
import com.gulimall.gulimallseckill.to.SeckillSkuRedisTo;
import com.gulimall.gulimallseckill.vo.SeckillSessionWithSkusVo;
import com.gulimall.gulimallseckill.vo.SkuInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @decription:
 * @author: zyy
 * @date 2020-06-29-21:09
 */
@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    CouponFeignService couponFeignService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    RabbitTemplate rabbitTemplate;

    //活动key:前缀 + startTime_endTime  value:skuIds    list结构
    private final String SESSIONS_PREFIX = "seckill:sessions:";

    //秒杀sku的key:前缀  valueKey:skuId  valueVal:skuInfo  Hash结构
    private final String SKUKILL_CACHE_PREFIX = "seckill:skus:";

    //秒杀信号量的key:前缀 + 商品随机码  value:秒杀量    String结构
    private final String SKU_STOCK_SEMAPHORE = "seckill:stock:";

    @Override
    public void uploadSeckillLastest3Days() {
        //扫描需要参与秒杀的商品
        R r = couponFeignService.getLatest3DaysSession();
        if (r.getCode() == 0) {
            //上架商品
            List<SeckillSessionWithSkusVo> sessions = r.getData(new TypeReference<List<SeckillSessionWithSkusVo>>() {
            });
            //缓存到redis
            //活动信息
            saveSessionInfos(sessions);
            //活动的关联商品信息
            saveSessionSkuInfos(sessions);
        }
    }

    @SentinelResource(value = "seckillSkusAnnotation",blockHandler = "blockHandler")
    @Override
    public List<SeckillSkuRedisTo> getCurrentSeckillSkus() {
        //找到属于当前时间的秒杀活动
        try(Entry entry = SphU.entry("currentSeckillSkus")) {
            long now = new Date().getTime();
            Set<String> keys = stringRedisTemplate.keys(SESSIONS_PREFIX + "*");
            for (String key : keys) {
                String replace = key.replace(SESSIONS_PREFIX, "");
                String[] split = replace.split("_");
                Long start = Long.parseLong(split[0]);
                Long end = Long.parseLong(split[1]);
                if (now >= start && now < end) {
                    List<String> skuIds = stringRedisTemplate.opsForList().range(key, -100, 100);
                    BoundHashOperations<String, String, String> ops = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
                    List<String> list = ops.multiGet(skuIds);
                    if (!CollectionUtils.isEmpty(list)) {
                        return list.stream().map(o -> JSON.parseObject(o, SeckillSkuRedisTo.class)).collect(Collectors.toList());
                    }
                    break;
                }
            }
        } catch (NumberFormatException | BlockException e) {
            log.info("查询seckill的商品服务降级");
        }
        //获取当前秒杀活动的秒杀商品信息
        return null;
    }

    public List<SeckillSkuRedisTo> blockHandler(BlockException ex) {
        log.info("原方法被限流了");
        return null;
    }

    @Override
    public SeckillSkuRedisTo getSkukillInfo(Long skuId) {
        //找到对应的Redis缓存信息
        BoundHashOperations<String, String, String> ops = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
        Set<String> keys = ops.keys();
        String reg = "\\d_" + skuId;
        if (!CollectionUtils.isEmpty(keys)) {
            for (String key : keys) {
                if (Pattern.matches(reg, key)) {
                    SeckillSkuRedisTo skuRedisTo = JSON.parseObject(ops.get(key), SeckillSkuRedisTo.class);
                    Long startTime = skuRedisTo.getStartTime();
                    Long endTime = skuRedisTo.getEndTime();
                    long now = new Date().getTime();
                    if (now < startTime && now >= endTime) {
                        skuRedisTo.setRandomCode(null);
                    }
                    return skuRedisTo;
                }
            }
        }
        return null;
    }

    //秒杀服务主处理方法
    @Override
    public String kill(String killId, String code, Integer num) {
        MemberRespVo memberRespVo = LoginUserInterceptor.threadLocal.get();
        //获取秒杀商品的详细信息
        BoundHashOperations<String, String, String> ops = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
        String s = ops.get(killId);
        if (s == null) {
            return null;
        } else {
            SeckillSkuRedisTo skuRedisTo = JSON.parseObject(s, SeckillSkuRedisTo.class);
            //校验合法性
            long now = new Date().getTime();
            Long startTime = skuRedisTo.getStartTime();
            Long endTime = skuRedisTo.getEndTime();
            //校验时间
            if (now >= startTime && now < endTime) {
                //校验code
                if (skuRedisTo.getRandomCode().equals(code)) {
                    //校验数量及是否重复购买
                    if (num <= skuRedisTo.getSeckillLimit().intValue()) {
                        String redisUserKey = memberRespVo.getId() + "_" + killId;
                        //设置自动过期
                        long ttl = endTime - startTime;
                        boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(redisUserKey, num.toString(), ttl, TimeUnit.MILLISECONDS);
                        if(aBoolean){
                            RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + code);
                            try {
                                boolean flag = semaphore.tryAcquire(num, 100, TimeUnit.MILLISECONDS);
                                if(flag){
                                    //秒杀成功，快速下单
                                    String timeId = IdWorker.getTimeId();
                                    SeckillOrderTo seckillOrderTo = new SeckillOrderTo();
                                    BeanUtils.copyProperties(skuRedisTo,seckillOrderTo);
                                    seckillOrderTo.setOrderSn(timeId);
                                    seckillOrderTo.setNum(num);
                                    seckillOrderTo.setMemberId(memberRespVo.getId());
                                    rabbitTemplate.convertAndSend("order-event-exchange","order.seckill.order",seckillOrderTo);
                                    return timeId;
                                }
                            } catch (InterruptedException e) {
                                return null;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private void saveSessionInfos(List<SeckillSessionWithSkusVo> sessions) {
        sessions.forEach(seckillSessionWithSkusVo -> {
            long startTime = seckillSessionWithSkusVo.getStartTime().getTime();
            long endTime = seckillSessionWithSkusVo.getEndTime().getTime();
            String key = SESSIONS_PREFIX + startTime + "_" + endTime;
            List<String> collect = seckillSessionWithSkusVo.getSkuRelationEntities().stream().map(seckillSkuVo -> seckillSkuVo.getPromotionSessionId() + "_" + seckillSkuVo.getSkuId()).collect(Collectors.toList());
            //缓存活动信息 (seckill:sessions:1000_1999   1,2,3) (seckill:sessions:2000_2999   4,6)
            if (!stringRedisTemplate.hasKey(key)) {
                stringRedisTemplate.opsForList().leftPushAll(key, collect);
            }
        });
    }

    private void saveSessionSkuInfos(List<SeckillSessionWithSkusVo> sessions) {
        sessions.forEach(seckillSessionWithSkusVo -> {
            BoundHashOperations<String, Object, Object> ops = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
            seckillSessionWithSkusVo.getSkuRelationEntities().forEach(seckillSkuVo -> {
                if (!ops.hasKey(seckillSkuVo.getPromotionSessionId() + "_" + seckillSkuVo.getSkuId())) {
                    //缓存商品
                    SeckillSkuRedisTo seckillSkuRedisTo = new SeckillSkuRedisTo();
                    //sku的基本数据
                    R r = productFeignService.getSkuInfo(seckillSkuVo.getSkuId());
                    if (r.getCode() == 0) {
                        SkuInfoVo skuInfoVo = r.getData("skuInfo", new TypeReference<SkuInfoVo>() {
                        });
                        seckillSkuRedisTo.setSkuInfoVo(skuInfoVo);
                    }
                    //设置商品To的秒杀区间
                    seckillSkuRedisTo.setStartTime(seckillSessionWithSkusVo.getStartTime().getTime());
                    seckillSkuRedisTo.setEndTime(seckillSessionWithSkusVo.getEndTime().getTime());

                    //随机码
                    String token = UUID.randomUUID().toString().replace("-", "");
                    seckillSkuRedisTo.setRandomCode(token);

                    //使用商品的秒杀量作为分布式的信号量
                    RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + token);
                    semaphore.trySetPermitsAsync(seckillSkuVo.getSeckillCount().intValue());

                    //sku的秒杀信息
                    BeanUtils.copyProperties(seckillSkuVo, seckillSkuRedisTo);
                    String jsonString = JSON.toJSONString(seckillSkuRedisTo);
                    ops.put(seckillSkuVo.getPromotionSessionId() + "_" + seckillSkuVo.getSkuId(), jsonString);
                }
            });
        });
    }
}
