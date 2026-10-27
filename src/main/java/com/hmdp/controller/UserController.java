package com.hmdp.controller;


import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        log.info("获取验证码：phone: {}", phone);
        userService.sendCode(phone, session);
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        log.info("登录：loginForm: {}", loginForm);
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){
        log.info("登出：token: {}", request.getHeader("authorization"));
        String token = request.getHeader("authorization");
        return userService.logout(token);
    }

    /**
     * 获取当前登录用户信息
     * @return
     */
    @GetMapping("/me")
    public Result me(){
        log.info("获取当前用户");
        // 从 ThreadLocal 里拿拦截器存进去的用户
        UserDTO userDTO = UserHolder.getUser();
        return Result.ok(userDTO);
    }

    /**
     * 根据id获取用户信息
     * 这个接口从前端传的id和请求头携带的token不对应也能请求成功，算是-----没用的接口
     * @param userId
     * @return
     */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        log.info("获取用户详情：userId: {}", userId);
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 用户签到
     */
    @PostMapping("/sign")
    public Result sign() {
        log.info("用户签到");
        return userService.sign();
    }

    /**
     * 统计用户最近一次连续签到天数
     */
    @GetMapping("/sign/count")
    public Result signCount() {
        log.info("统计本月截止到今天，用户的连续签到天数");
        return userService.signCount();
    }
}
