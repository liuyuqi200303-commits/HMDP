package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private IFollowService followService;

    /**
     * 关注或取关  ==  redis里用户 1 的关注名单key就叫 follows:user:1， 里面存着他关注的所有人的 ID。
     * @param followUserId
     * @param isFollow
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId,
                         @PathVariable("isFollow") Boolean isFollow){
        log.info("用户{}关注/取关用户", followUserId);
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 查看我和某人的共同关注
     * @param id
     * @return
     */
    @GetMapping("/commons/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        log.info("查看我和{}的共同关注", id);
        return followService.followCommons(id);
    }

}
