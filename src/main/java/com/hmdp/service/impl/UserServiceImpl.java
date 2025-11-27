package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 登出功能
     * @return
     */
    @Override
    public Result loginOut(HttpServletRequest request) {
        //1.获取redis中的token
        String token=request.getHeader("authorization");
        //2.如果为空就返回未登录
        if(StrUtil.isBlank(token)){
            return Result.fail("未登录");
        }
        //3.不为空就清空redis，然后返回退出成功
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(tokenKey);

        return Result.ok();
    }

    /**
     * 登录
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号不合法");
        }

        // 直接允许登录，无需验证码
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createNewUser(phone);
        }

        // 生成 token...
        String token = UUID.randomUUID().toString();
        UserDTO dto = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(dto, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((f, v) -> v.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, map);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_CODE_TTL, TimeUnit.MINUTES);

//
//        //4.判断用户是否存在
//        if (user == null) {
//            //5.如果不存在就创建用户
//            user = createNewUser(phone);
//        }
//        //6.保存用户信息到redis中
//        //6.1生成token令牌
//        String token = UUID.randomUUID().toString();
//        //6.2将对象转成map，并且设置不存null值和所有类型为String
//        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
//                CopyOptions.create()
//                        .setIgnoreNullValue(true)
//                        .setFieldValueEditor((fieldName, fieldValue) ->
//                                fieldValue.toString()));
//        //6.3存储
//        String tokenKey = LOGIN_USER_KEY + token;
//        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
//        //6.4设置token有效时间
//        stringRedisTemplate.expire(tokenKey,LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        //7.返回token
        return Result.ok(token);


    }

    private User createNewUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(RandomUtil.randomString(10));
        save(user);
        return user;
    }

    /**
     * 短信验证
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {

        // 1. 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        // 2. 直接写死验证码（压测必需）
        String code = "123456";

        // 3. 保存验证码到 Redis
        stringRedisTemplate.opsForValue().set(
                LOGIN_CODE_KEY + phone,
                code,
                LOGIN_CODE_TTL, // 一般是 2 分钟
                TimeUnit.MINUTES
        );

        // 4. 打印日志（模拟短信）
        log.debug("发送短信验证码成功，验证码: {}", code);

        return Result.ok();
    }

}
