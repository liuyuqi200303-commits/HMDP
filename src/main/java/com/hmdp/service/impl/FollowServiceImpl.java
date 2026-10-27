package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;


    /**
     * 关注或取关
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
//        1.获取当前用户id
        Long userId = UserHolder.getUser().getId();
//        2.拼接redis的key
        String key = RedisConstants.FOLLOW_KEY_USER + userId;

        if(isFollow){
//            3.关注操作
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean success = this.save(follow);

            if(success){
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }else {
//            4.取关操作
            boolean success = this.remove(new QueryWrapper<Follow>()
                    .eq("user_id",userId)
                    .eq("follow_user_id",followUserId));
            if(success){
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查看我和某人的共同关注
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
//        拼接我自己的key和传过来的人的key
        Long userId = UserHolder.getUser().getId();
        String key1 = RedisConstants.FOLLOW_KEY_USER + userId;
        String key2 = RedisConstants.FOLLOW_KEY_USER + id;

//        redis的set求交集操作
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        // String 列表 -> Long 列表 ids
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 根据ids查数据库，Long 列表 -> UserDTO 列表 users
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);

    }
}
