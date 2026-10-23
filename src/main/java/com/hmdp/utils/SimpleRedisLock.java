package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;

/**
 * 这个类没有注册成 Spring 的 Bean，所以不能被 Spring 管理
 */
public class SimpleRedisLock implements ILock {

    private String name; // 锁的名称（不同业务用不同的锁）
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识（后面防误删会用到，现在先简单的用线程 ID）
        long threadId = Thread.currentThread().getId();
        // 用 SETNX 命令：只有当 key 不存在时才设置成功（也就是抢到锁）
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        // 因为 Boolean 是包装类，如果返回 null 可能会引发空指针，所以用 Boolean.TRUE.equals 判空
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 直接删除 key，释放锁
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}