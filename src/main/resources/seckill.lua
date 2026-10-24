-- 1. 参数列表
-- KEYS[1] 代表优惠券库存的 key (seckill:stock:10)
-- ARGV[1] 代表当前用户 id (1)
-- ARGV[2] 代表当前优惠券 id (10)

-- 2. 判断库存是否充足
local stock = tonumber(redis.call('get', KEYS[1]))
if (stock <= 0) then
    return 1; -- 1 代表库存不足
end

-- 3. 判断一人一单（用 Set 结构存用户，防止重复购买）
-- 这里的 key 叫 seckill:order:优惠券ID
local isBought = redis.call('sismember', 'seckill:order:' .. ARGV[2], ARGV[1])
if (isBought == 1) then
    return 2; -- 2 代表重复下单
end

-- 4. 扣减库存
redis.call('incrby', KEYS[1], -1)

-- 5. 把当前用户加入已购集合
redis.call('sadd', 'seckill:order:' .. ARGV[2], ARGV[1])

-- 6. 返回 0 代表抢购成功
return 0;