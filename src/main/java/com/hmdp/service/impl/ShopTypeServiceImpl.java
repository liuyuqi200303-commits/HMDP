package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    /**
     * 查询店铺的类型
     *
     * @return
     */
    @Override
    public Result queryTypeList() {
        // 1. 直接使用常量作为 Key，不要拼 UUID，也不要拼 ID
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY; // 确保你的常量类里定义了这个值，比如 "cache:shopType:list"

        // 2. 从 Redis 中查询
        String shopTypeJSON = stringRedisTemplate.opsForValue().get(key);

        // 3. 判断缓存是否命中
        if (StrUtil.isNotBlank(shopTypeJSON)) {
            // 3.1 缓存命中，反序列化成 List 并返回
            List<ShopType> typeList = JSONUtil.toList(shopTypeJSON, ShopType.class);
            return Result.ok(typeList);
        }

        // 4. 缓存未命中，查询数据库（按 sort 排序）
        List<ShopType> typeList = this.lambdaQuery()
                .orderByAsc(ShopType::getSort)
                .list();

        // 5. 判断数据库中是否存在数据
        // 【注意】这里不能用 Objects.isNull(typeList)，因为 list() 查不到数据时返回的是空集合 []，不是 null
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("店铺类型不存在");
        }

        // 6. 写入 Redis，设置过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList),
                RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

        return Result.ok(typeList);
    }

}
