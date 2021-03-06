package com.gulimall.order;

import io.seata.spring.annotation.datasource.EnableAutoDataSourceProxy;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@EnableRabbit
@EnableFeignClients(basePackages = "com.gulimall.order.feign")
@EnableRedisHttpSession
@EnableDiscoveryClient
@SpringBootApplication
@EnableAutoDataSourceProxy
public class GulimallOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallOrderApplication.class, args);
    }

}
