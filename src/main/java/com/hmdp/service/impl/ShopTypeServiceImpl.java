package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.TYPE_SHOP_KEY;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result typeList() {
        //1.将所有的店铺种类去redis中寻找
        String key = TYPE_SHOP_KEY;
        List<String> typeJsonList = stringRedisTemplate.opsForList().range(key, 0, -1);
        // 2.判断缓存是否存在
        if (typeJsonList != null && !typeJsonList.isEmpty()) {
            // 2.1 缓存命中，把 JSON 转为对象返回
            List<ShopType> typeList = typeJsonList.stream()
                    .map(json -> JSONUtil.toBean(json, ShopType.class))
                    .collect(Collectors.toList());
            return Result.ok(typeList);
        }
        // 3.缓存未命中，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("店铺类型不存在");
        }

        // 4.写入 Redis 缓存（List 结构，每个元素存 JSON）
        List<String> cacheList = typeList.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(key, cacheList);

        // 5.可选：设置过期时间，避免缓存雪崩
        stringRedisTemplate.expire(key, 30, TimeUnit.MINUTES);

        // 6.返回结果
        return Result.ok(typeList);
    }
}
