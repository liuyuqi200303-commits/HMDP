package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import java.util.UUID;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1、判断手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        // 2、手机号合法，生成验证码，并保存到Redis中
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 3、发送验证码
        log.info("验证码:{}", code);
        return Result.ok();
    }

    /**
     * 用户登录
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        // 1、判断手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        // 2、判断验证码是否正确
        String redisCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (code == null || !code.equals(redisCode)) {
            return Result.fail("验证码不正确");
        }
        // 3、判断手机号是否是已存在的用户
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));
        if (Objects.isNull(user)) {
            // 用户不存在，需要注册
            user = createUserWithPhone(phone);
        }
        // 4、保存用户信息到Redis中，便于后面逻辑的判断（比如登录判断、随时取用户信息，减少对数据库的查询）
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 将对象中字段全部转成string类型，StringRedisTemplate只能存字符串类型的数据
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true). // 忽略空值
                        setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));//StringRedisTemplate，它只能存字符串,这里转换一下类型
//        String token = UUID.randomUUID().toString(true);
        String token = UUID.randomUUID().toString().replace("-", "");
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    /**
     * 根据手机号创建用户并保存
     *
     * @param phone
     * @return
     */
    public User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        this.save(user);
        return user;
    }

    /**
     * 用户注销
     * @param token
     * @return
     */
    @Override
    public Result logout(String token) {
        // 1. 判断token是否为空
        if(StrUtil.isBlank(token)){
            return Result.fail("token为空");
        }

        // 2. 拼出Redis中的Key，并删除
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(key);

        // 3. 清理 ThreadLocal（虽然拦截器最后也会清，但这里手动清一下更保险）
        // 注意：这一步不是必须的，但为了代码严谨可以加上
        UserHolder.removeUser();
        log.info("用户已退出登录");
        return Result.ok();
    }

    //    @Override
//    public Result sendCode(String phone, HttpSession session) {
//        // 1.判断手机号是否合法
//        if (RegexUtils.isPhoneInvalid(phone)) {
//            return Result.fail("手机号格式不正确");
//        }
//        // 2.生成验证码
//        String code = RandomUtil.randomNumbers(6);
//        // 3.保存验证码到Session（【修改】统一用 "code"）
//        session.setAttribute("code", code);
//        // 4.发送验证码
//        log.info("发送验证码：{}", code);
//        return Result.ok();
//    }
//
//    @Override
//    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        String phone = loginForm.getPhone();
//        String code = loginForm.getCode();
//
//        // 1.判断手机号是否合法
//        if (RegexUtils.isPhoneInvalid(phone)) {
//            return Result.fail("手机号格式不正确");
//        }
//
//        // 2.判断验证码是否正确（【修改】统一从 "code" 取）
//        String sessionCode = (String) session.getAttribute("code");
//        if (code == null || !code.equals(sessionCode)) {
//            return Result.fail("验证码不正确");
//        }
//
//        // 3.判断用户是否存在（【重点修复】去数据库查询！）
//        User user = this.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
//
//        // 4.如果不存在则创建用户
//        if (Objects.isNull(user)) {
//            user = createUserWithPhone(phone);
//        }
//
//        // 5.保存用户信息到Session
//        session.setAttribute("user", user);
//        return Result.ok();
//    }
//
//    public User createUserWithPhone(String phone) {
//        User user = new User();
//        user.setPhone(phone);
//        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
//        this.save(user);
//        return user;
//    }
}