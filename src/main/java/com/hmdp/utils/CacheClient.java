package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Component
@Slf4j
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    //方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }


    // 方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓
    //存击穿问题
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(JSONUtil.toJsonStr(value));
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    //方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {

        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //1.判断redis中是否存在
        if (StrUtil.isNotBlank(json)) {
            //2.如果存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        //3.如果得到null，返回
        if (json == null) {
            return null;
        }
        //4.如果不存在，就去数据库中寻找
        R value = dbFallback.apply(id);
        //5.如果数据库不存在，就将空key放进redis
        if (value == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.如果数据库存在，就set进redis中，返回
        this.set(key, value, time, timeUnit);
        return value;
    }


    //方法4：根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit
    ) {
        String key = keyPrefix + id;
        //1.查询redis里是否存在
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.如果不存在，就返回null
        if (StrUtil.isBlank(json)) {
            return null;
        }
        //3.如果存在，就将json数据转成对象，设置好数据和逻辑过期时间
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //4.如果没过期，就直接返回对象
        if (LocalDateTime.now().isAfter(expireTime)) {
            return r;
        }
        //5.如果过期了就尝试缓存重建
        //6.缓存重建
        //6.1获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2如果获取成功，就开辟新线程
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(
                    () -> {
                        //6.3然后访问数据库，获得新的信息
                        R newR = dbFallback.apply(id);
                        //6.4更新redis中的缓存
                        this.set(key, newR, time, timeUnit);
                        //6.5释放锁
                        unLuck(lockKey);
                    });
        }
        //7.提交过期信息
        return r;
    }

    //方法5：根据指定的key查询缓存，并反序列化为指定类型，需要利用上锁等待线程的方法解决缓存击穿问题
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit
    ) throws InterruptedException {
        String key = keyPrefix + id;
        //1.获取redis中的缓存获得
        String json = stringRedisTemplate.opsForValue().get(key);
        R r = JSONUtil.toBean(json, type);
        //2.获得的json看看是否命中，如果命中直接返回
        if (StrUtil.isNotBlank(json)) {
            return r;
        }
        //3.如果命中后但无值，看看获得的值是否是null
        if (json == null) {
            return null;
        }
        //4.如果没命中，就要去数据库中寻找，这是就要开启一个锁
        R value = dbFallback.apply(id);
        boolean isLock = tryLock(LOCK_SHOP_KEY + id);
        if (!isLock) {
            Thread.sleep(50);
            return queryWithMutex(keyPrefix, id, type, dbFallback, time, timeUnit);
        }
        //5.从数据库中获得的数据后，如果有值，就将缓存写入redis中
        if (value == null) {
            //6.如果没有值，就将null也写入redis中，然后返回null
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        set(key, value, time, timeUnit);
        //7.释放锁
        unLuck(LOCK_SHOP_KEY + id);
        //8.返回
        return r;
    }


    //上锁方法
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //解锁方法
    private void unLuck(String key) {
        stringRedisTemplate.delete(key);
    }


}
