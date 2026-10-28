package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
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
import com.hmdp.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Override
    public void loadShopData() {
        // 1. 查询所有店铺信息
        List<Shop> list = this.list();

        // 2. 按照 typeId 进行分组（因为 Redis 的 GEO 是按类型分类的，比如美食一个圈，KTV一个圈）
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        // 3. 分批写入 Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> shopList = entry.getValue();

            String key = "shop:geo:" + typeId;

            // 用 List 把一个个 Point 对象装起来，然后批量存入
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shopList.size());
            for (Shop shop : shopList) {
                // 这里的 id 要转成 String，作为 GEO 集合里的 member
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY()) // 经度 x, 纬度 y
                ));
            }
            // 批量存入 Redis：GEADD key 经度 纬度 member
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    /**
     * 缓存重建线程池---线程池，用于后台重建缓存
     */
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1. 判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 没有坐标，走普通数据库分页
            Page<Shop> page = this.query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        // 2. 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3. 去 Redis 查 GEO
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(
                key,
                new Circle(new Point(x, y), 5000), // 半径 5000 米
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
//                        默认情况下，Redis 只返回店铺的 ID。加上这个参数，Redis 就会额外返回一个值：这个店距离圆心（用户）有多少米。
//                        这也就是为什么你在 Java 里能通过 result.getDistance() 拿到距离。
                        .includeDistance()
                        .sortAscending()
                        .limit(end)
        );

        // 4. 解析结果  ----  防呆设计
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        // 5. 截取 from ~ end 的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        // 6. 根据 id 去数据库查详细信息（保持距离顺序）
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = this.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();

        // 7. 把距离塞进 Shop 对象里
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        return Result.ok(shops);
    }
//    /**
//     * 根据类型查询商铺数据
//     * @param typeId
//     * @param current
//     * @return
//     */
//    @Override
//    public Result queryShopByType(Integer typeId, Integer current) {
//        // 1. 构建分页对象
//        Page<Shop> page = new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE);
//
//        // 2. 构建查询条件
//        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
//        queryWrapper.eq(Shop::getTypeId, typeId);
//
//        // 3. 执行分页查询（用 this.page，而不是 this.lambdaQuery）
//        this.page(page, queryWrapper);
//
//        // 4. 返回分页后的数据
//        return Result.ok(page);
//    }

    @Override
    public Result updateShopById(Shop shop) {
        if (shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        this.updateById(shop);
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }

    @Override
    public Result queryShopByName(String name, Integer current) {
        // 1. 构建分页对象，
        Page<Shop> page = new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE);
        // 2. MP的链式查询
        this.lambdaQuery()
                .like(StrUtil.isNotBlank(name), Shop::getName, name)
                .page(page);
        return Result.ok(page);
    }

//    /**
//     * 根据id查询商铺数据 -- 解决缓存击穿-爆款商品过期时间到了，或者redis重启了，上千请求同时查询mysql
//     * @param id
//     * @return
//     */
//    @Override
//    public Result queryById(Long id) {
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        // 1. 从 Redis 查数据
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        // 2. 如果连空数据都没有，说明这个店压根没做过逻辑过期预热，直接返回错误
//        if (StrUtil.isBlank(shopJson)) {
//            return Result.fail("店铺不存在！");
//        }
//
//        // 3. 反序列化成 RedisData 对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        // 因为 JSON 反序列化时，data 默认是 JSONObject，需要二次转换
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//
//        // 4. 判断是否逻辑过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 未过期，直接返回数据
//            return Result.ok(shop);
//        }
//
//        // 5. 已过期，尝试获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        ILock lock = new SimpleRedisLock(lockKey, stringRedisTemplate);
//        boolean isLock = lock.tryLock(10);
//
//        if (isLock) {
//            // 6. 获取锁成功，开启独立线程去重建缓存
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    // 模拟重建缓存的耗时（测试用）
//                    // Thread.sleep(200);
//                    this.saveShopToCache(id, 30L); // 30秒后逻辑过期
//                } catch (Exception e) {
//                    e.printStackTrace();
//                } finally {
//                    lock.unlock();
//                }
//            });
//        }
//
//        // 7. 无论是否获取到锁，都把旧数据直接返回给用户（实现高可用）
//        return Result.ok(shop);
//    }
//
//    /**
//     * 保存数据到 Redis（不设置物理 TTL）--- 用于逻辑过期解决缓存击穿
//     * @param id
//     * @param expireSeconds
//     */
//    public void saveShopToCache(Long id, Long expireSeconds) {
//        Shop shop = this.getById(id);
//        if (shop == null) {
//            return;
//        }
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        // 设置逻辑过期时间：当前时间 + 30 秒
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        // 存入 Redis，注意：这里不要传 TimeUnit 参数，让它永不过期
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

//          /**
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

}
