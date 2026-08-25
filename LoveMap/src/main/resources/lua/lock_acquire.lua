-- 分布式锁 - 照片上传
-- KEYS[1]: lock key (upload:lock:{albumId}:{userId})
-- ARGV[1]: 锁超时时间（秒）
-- 返回值: 1=获取成功, 0=获取失败（已有其他线程持有锁）

if redis.call("SET", KEYS[1], "1", "NX", "EX", tonumber(ARGV[1])) then
    return 1
else
    return 0
end