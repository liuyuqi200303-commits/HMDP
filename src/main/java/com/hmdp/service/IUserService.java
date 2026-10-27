package com.hmdp.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.utils.RegexUtils;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类 继承了mp的通用Service接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    User createUserWithPhone(String phone);

    Result logout(String token);

    Result sign();

    Result signCount();
}
