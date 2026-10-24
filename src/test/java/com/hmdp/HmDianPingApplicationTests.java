package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    @Test
    void contextLoads() {
        // 启动 Spring 容器，验证整体环境是否正常
    }

    @Test
    void testRedisSet() {
        // 测试 Redis 写入，比如练习商铺缓存时验证 setex
        stringRedisTemplate.opsForValue()
                .set("test:key", "hello", 10, TimeUnit.MINUTES);
        System.out.println(stringRedisTemplate.opsForValue().get("test:key"));
    }

//    /**
//     * 预热店铺数据
//     */
//    @Test
//    public void testSaveShopToCache(){
//        shopService.saveShopToCache(1L, RedisConstants.CACHE_SHOP_LOGICAL_TTL);
//    }

}