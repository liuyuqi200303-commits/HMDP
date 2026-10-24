package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 这个类没有注册成 Spring 的 Bean，所以不能被 Spring 管理
 * 为什么不注册呢？1.非单例 2.有动态参数，需要new的时候传参
 * 因为这个类是用于实现锁的，如果注册成 Spring 的 Bean，默认是单例模式，那么所有的锁都会用同一个 RedisTemplate，
 * 这样就失去了锁的隔离性，因为不同的业务可能需要不同的 RedisTemplate 配置。
 */
public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = "thread:";

    // 🚨 初始化 Lua 脚本对象（静态常量，只加载一次）
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        // 指定脚本所在的位置
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        // 指定返回类型为 Long
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识（加上前缀防冲突）
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 尝试加锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 🚨 调用 Lua 脚本释放锁
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }
}