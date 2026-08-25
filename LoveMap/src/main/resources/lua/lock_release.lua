-- 分布式锁 - 释放锁
-- KEYS[1]: lock key (upload:lock:{albumId}:{userId})
-- 返回值: 1=释放成功, 0=锁不存在

if redis.call("EXISTS", KEYS[1]) == 1 then
    redis.call("DEL", KEYS[1])
    return 1
else
    return 0
end