-- 参数：优惠券ID, 用户ID
local voucherId = ARGV[1]
local userId = ARGV[2]

-- key，来自 KEYS
local stockKey = KEYS[1]
local orderKey = KEYS[2]

-- 判断库存
local stock = tonumber(redis.call('get', stockKey))
if stock == nil or stock <= 0 then
    -- 库存不存在 或 <= 0
    return 1
end

-- 判断是否下过单
if redis.call('sismember', orderKey, userId) == 1 then
    return 2
end

-- 扣库存
redis.call('incrby', stockKey, -1)

-- 记录订单（set 防止重复）
redis.call('sadd', orderKey, userId)

return 0
