package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    /**
     * 点赞博客
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        //2.判断当前用户是否已经点过赞
        String key = RedisConstants.BLOG_LIKED_KEY + id; // 记得在常量类里定义 "blog:liked:"
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            // 3. 没点过赞：点赞
            // 3.1 保存点赞记录到 Redis，Score 存当前时间戳（时间越晚，排在越前面）
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            // 3.2 数据库里点赞数 + 1
            update().setSql("liked = liked + 1").eq("id", id).update();
        } else {
            // 4. 已经点过赞：取消点赞
            // 4.1 从 Redis 里移除该用户
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            // 4.2 数据库里点赞数 - 1
            update().setSql("liked = liked - 1").eq("id", id).update();
        }
        return Result.ok();
    }

    /**
     * 点赞排名前5
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id; // "blog:liked:blogId"

        // 1. 从 Redis 的 ZSet 中，按 Score（时间戳）从大到小，取前 5 个用户 ID------逆序查询前五个
        Set<String> top5 = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 4);

        // 如果没有点赞，直接返回空集合
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2. 把 Set<String> 转成 List<Long> (用户 ID 列表)
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());

        // 3. 根据这批用户 ID，去数据库批量查询用户信息 (MyBatis-Plus 的 listByIds)
        List<UserDTO> userDTOS = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 4. 保证返回给前端的顺序，和 Redis 里取出来的顺序一致
        // （因为 listByIds 默认是按数据库里的主键顺序返回，可能会打乱时间顺序）
        // 技巧：利用 Map 把查出来的用户对应上
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, Function.identity()));
        List<UserDTO> resultList = new ArrayList<>();
        for (Long userId : ids) {
            UserDTO dto = userDTOMap.get(userId);
            if (dto != null) {
                resultList.add(dto);
            }
        }

        return Result.ok(resultList);
    }

    /**
     * 保存博客
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());

        // 2. 初始化点赞数和评论数（防前端空指针）
        blog.setLiked(0);
        blog.setComments(0);

        // 3. 保存到数据库
        this.save(blog);

        // ==========================================
        // 🚨 4. (进阶) 推送给粉丝，存入 Redis 的 Feed 流
        // 如果你现在还没学到这里，这部分代码可以先注释掉
        // ==========================================
        /*
        // 4.1 查询我的所有粉丝
        List<Follow> followList = followService.query().eq("follow_user_id", user.getId()).list();
        // 4.2 推送到每个粉丝的收件箱
        for (Follow follow : followList) {
            Long fanId = follow.getUserId();
            String key = "feed:" + fanId;
            // ZADD 存入粉丝的收件箱，score是时间戳，value是笔记ID
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        */

        // 5. 返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查询我的博客
     * @param current
     * @return
     */
    @Override
    public Result queryMyBlog(Integer current) {
//        1. 获取登录用户
        UserDTO  user = UserHolder.getUser();

//        2. 根据用户查询，使用 MyBatis-Plus 的链式查询分页
        Page<Blog> page = this.query()
                .eq("user_id", user.getId())
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

//        3. 获取当前页数据
        List<Blog> records = page.getRecords();

//        4. 加上作者信息和是否点赞过
        for (Blog blog : records) {
            blog.setIcon(user.getIcon());
            blog.setName(user.getNickName());

            isBlogLiked(blog);
        }
        return Result.ok(page);
    }

    private void isBlogLiked(Blog blog) {
        // 1. 判断当前是否有用户登录（有些查询不需要登录也能看）
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return; // 没登录，不需要判断
        }
        // 2. 拼接 Redis 的 Key
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();

        // 3. 去 Redis 的 ZSet 里查当前用户的 ID 存不存在（之前存进去的是 userId）
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());

        // 4. 如果 score 不是 null，说明点过赞
        blog.setIsLike(score != null);
    }

    /**
     * 查询热门博客
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
//        查询热门笔记，按点赞数降序排序
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();

        if(records.isEmpty()){
            return Result.ok(records);
        }
//        提取所有作者的ID，去重
        Set<Long> userIds = records.stream().map(Blog::getUserId).collect(Collectors.toSet());

        List<User> users = userService.listByIds(userIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, Function.identity()));

        for (Blog blog : records) {
            User user = userMap.get(blog.getUserId());
            if (user != null){
                blog.setName(user.getNickName());
                blog.setIcon(user.getIcon());
            }
            isBlogLiked(blog);

        }
        return Result.ok(page);
    }


}
