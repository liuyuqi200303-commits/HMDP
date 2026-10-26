-- 为了保证“判断锁是否属于自己”和“删除锁”这两个动作的原子性
-- 比较线程标识与锁中的标识是否一致
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 一致，则删除锁
    return redis.call('del', KEYS[1])
end
-- 不一致，则直接返回
return 0