package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 逻辑过期数据类
 */
@Data
public class RedisData {
    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
    /**
     * 缓存数据
     */
    private Object data;
}
