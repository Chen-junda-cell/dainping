package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

public class LoginInterceptor implements HandlerInterceptor {



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//
//
//        String token = request.getHeader("token");
//
//        if (token == null || token.isEmpty()) {
//            // ----------- 自动构造压测用户（关键）-----------
//            UserDTO user = new UserDTO();
//            long uid = Thread.currentThread().getId();  // 每个线程一个用户
//            user.setId(uid);
//            user.setNickName("LoadTestUser-" + uid);
//            UserHolder.saveUser(user);
//            return true;
//        }
//
//        // ----------- 正常登录流程 -----------
//        // 根据 token 查 Redis → 若找到，UserHolder.saveUser(user)
//        // 若 token 无效，也走上面的模拟用户逻辑（可调）
//
//        return true;

        // 1.判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserHolder.getUser() == null) {
            // 没有，需要拦截，设置状态码
            response.setStatus(401);
            // 拦截
            return false;
        }
        // 有用户，则放行
        return true;
    }
}
