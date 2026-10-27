package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

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
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    /**
     * 保存探店博文
     * @param blog
     * @return
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        log.info("保存探店博文：{}", blog.getId());
        return blogService.saveBlog(blog);
    }

    /**
     * 点赞博客--传进去的id是博客的id
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        log.info("点赞博客：id {}", id);
        return blogService.likeBlog(id);
    }
    /**
     * 查询点赞排行榜（前5名）
     * 前端请求：GET /blog/likes/1
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }


    /**
     * 分页查询当前用户博客列表 ----- current是当前页
     * @param current
     * @return
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        log.info("查询我的博客：current {}", current);
        return blogService.queryMyBlog(current);
    }

    /**
     * 分页查询热门博客列表 ----- current是当前页
     * @param current
     * @return
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        log.info("查询热门博客：current {}", current);
        return blogService.queryHotBlog(current);
    }
}
