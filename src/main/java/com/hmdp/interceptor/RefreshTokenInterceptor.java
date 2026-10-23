package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import cn.hutool.core.util.StrUtil;
import org.springframework.web.servlet.HandlerInterceptor;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    // new出来的对象是无法直接注入IOC容器的（LoginInterceptor是直接new出来的）
    // 所以这里需要再配置类中注入，然后通过构造器传入到当前类中
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("用户刷新拦截器...");
        // 1、获取token，并判断token是否存在
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)){
            // token不存在，说明当前用户未登录，不需要刷新直接放行
            return true;
        }
        // 2、判断用户是否存在
        String tokenKey = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        if (userMap.isEmpty()){
            // 用户不存在，说明当前用户未登录，不需要刷新直接放行
            return true;
        }
        // 3、用户存在，则将用户信息保存到ThreadLocal中，方便后续逻辑处理，比如：方便获取和使用用户信息，Redis获取用户信息是具有侵入性的
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(BeanUtil.copyProperties(userMap, UserDTO.class));
        // 4、刷新token有效期
        stringRedisTemplate.expire(token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    // 请求处理完毕，视图渲染完毕之后（清理 ThreadLocal）
    // 苍穹外卖没写这个方法，黑马点评必须写！
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        log.info("刷新拦截器清理ThreadLocal...");
        // 🚨 核心：把当前线程里的用户信息删掉，防止内存泄漏！
        UserHolder.removeUser();
    }
}
