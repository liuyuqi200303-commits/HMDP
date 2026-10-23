package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.ILock;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据id查询商铺数据 -- 解决缓存击穿-爆款商品过期时间到了，或者redis重启了，上千请求同时查询mysql
     * 解决：互斥锁
     * 先根据id查缓存，能查到数据就直接返回，查不到判断是否为空，不为空（“”）则店铺不存在，为null就去查数据库，
     * 创建锁对象（这个锁对象是java对象，存在JVM堆内存里），尝试获取锁，一个信息只能创建一个锁，
     * 比如说花店的信息只能创建一个锁，这个锁里还要有创建人的id，因为多个信息不会只创建一个锁，
     * 这样不会误删，如果获取锁失败说明有别人拿到了锁，我需要等一段时间后从查缓存开始执行，直到拿到数据，
     * 如果我拿到锁，就去查数据库，数据库不存在就写“”到redis，防止穿透，有数据就写到redis并返回，最后释放锁
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 1. 先查缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 命中真实数据，直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        // 3. 命中空缓存（防穿透逻辑）
        if (shopJson != null) {
            return Result.fail("店铺不存在！");
        }

        // ================= 下面是缓存未命中的情况，开始加互斥锁 =================
        // 4. 创建锁对象 (锁的 key 一般带上业务前缀和 id)
        ILock lock = new SimpleRedisLock("shop:" + id, stringRedisTemplate);
        boolean isLocked = lock.tryLock(10); // 尝试获取锁，10秒过期

        // 5. 如果获取锁失败，说明别人正在查数据库，我们睡一会然后重试
        if (!isLocked) {
            try {
                Thread.sleep(50); // 睡 50 毫秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 递归调用自己，重新查缓存
            return queryById(id);
        }

        // 6. 获取锁成功，去查数据库
        try {
            Shop shop = this.getById(id);

            // 6.1 数据库也不存在，写入空缓存防穿透，并返回
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("店铺不存在！");
            }

            // 6.2 数据库存在，写入真实缓存，并返回
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(shop);

        } finally {
            // 7. 无论如何，最后必须释放锁
            lock.unlock();
        }
    }

    //    /**
//     * 根据id查询商铺数据 -- 防止缓存穿透：有人恶意请求数据库里根本不存在的数据（比如 id = -1）。
//     * 解决方法：即使数据库里没有，也往 Redis 里存一个空字符串（""），并设置一个较短的过期时间（比如 2 分钟）。这样下次再来查 -1，直接在 Redis 拿到空字符串就返回了，保护了数据库。
//     * @param id
//     * @return
//     */
//    @Override
//    public Result queryById(Long id) {
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//
//        // 1. 从 Redis 查询缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        // 2. 判断缓存是否命中
//        // 🚨 注意：这里不能用 StrUtil.isNotBlank！
//        // 因为 isNotBlank 遇到空字符串("") 会返回 false，我们需要拦截空字符串
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 2.1 命中真实数据，直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return Result.ok(shop);
//        }
//
//        // 🚨 核心防御：判断命中的是否是空值（也就是咱们之前为了防穿透存的空字符串）
//        if (shopJson != null) {
//            // 不是 null，但也不是真实数据，说明命中了空缓存
//            return Result.fail("店铺不存在！");
//        }
//
//        // 3. 缓存未命中，查数据库
//        Shop shop = this.getById(id);
//
//        // 4. 数据库也不存在
//        if (shop == null) {
//            // 🚨 核心：数据库没有，往 Redis 存一个空字符串，设置 2 分钟过期（防穿透）
//            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return Result.fail("店铺不存在！");
//        }
//
//        // 5. 数据库存在，写入 Redis，设置 30 分钟（+随机值防雪崩，原版这里就写死30）
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        return Result.ok(shop);
//    }
//    /**
//     * 根据id查询商铺数据--原版
//     *
//     * @param id
//     * @return
//     */
//    public Result queryById(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        // 1、从Redis中查询店铺数据
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        Shop shop = null;
//        // 2、判断缓存是否命中
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 2.1 缓存命中，直接返回店铺数据
//            shop = JSONUtil.toBean(shopJson, Shop.class);
//            return Result.ok(shop);
//        }
//        // 2.2 缓存未命中，从数据库中查询店铺数据--mybatis-plus查询数据库
//        shop = this.getById(id);
//
//        // 4、判断数据库是否存在店铺数据
//        if (Objects.isNull(shop)) {
//            // 4.1 数据库中不存在，返回失败信息
//            return Result.fail("店铺不存在");
//        }
//        // 4.2 数据库中存在，写入Redis，并返回店铺数据
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
//        return Result.ok(shop);
//    }

    /**
     * 根据类型查询商铺数据
     * @param typeId
     * @param current
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current) {
        // 1. 构建分页对象
        Page<Shop> page = new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE);

        // 2. 构建查询条件
        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Shop::getTypeId, typeId);

        // 3. 执行分页查询（用 this.page，而不是 this.lambdaQuery）
        this.page(page, queryWrapper);

        // 4. 返回分页后的数据
        return Result.ok(page.getRecords());
    }

}
