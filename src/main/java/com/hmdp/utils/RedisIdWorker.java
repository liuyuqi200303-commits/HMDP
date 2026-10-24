package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 分布式ID生成器
 *  符号位：1bit，永远为0（表示正数）
 *  时间戳：31bit，以秒为单位，可以使用69年
 *  序列号：32bit，秒内的计数器，支持每秒产生2^32个不同ID
 */
@Component
public class RedisIdWorker {

    private static final String ID_PREFIX = "0";
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200;
    /**
     * 序列化位数
     */
    private static final int COUNT_BITS = 32;

    /**
     * 生成分布式ID
     * @param keyPrefix
     * @return
     */
    public long nextId(String keyPrefix){
        // 1、生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        // 2、生成序列号
        // 以当天的时间戳为key，防止一直自增下去导致超时，这样每天的极限都是 2^{31}
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = stringRedisTemplate.opsForValue().increment(ID_PREFIX + keyPrefix + ":" + date);
        // 3、拼接并返回
        return timestamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);
    }
}

