package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.ILock;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisIdWorker redisIdWorker;

    // 1. 加载 Lua 脚本 --- 把 seckill.lua 文件读进内存，打包成一个 DefaultRedisScript 对象
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 2. 创建阻塞队列（存储抢购成功的订单，等待异步写入数据库）
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    // 3. 创建单个后台线程，专门去处理队列里的订单
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 4. 项目一启动，这个后台线程就开始死循环监听队列
//    当 Spring 把这个类的对象创建好，并且把里面的 @Resource（比如 stringRedisTemplate）都注入完毕后，Spring 会立刻自动调用带有 @PostConstruct 注解的 init() 方法。
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(() -> {
            while (true) {
                try {
                    // 从队列里拿订单，如果队列为空，它会在这里阻塞等待
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 拿到订单，去数据库里保存（调用下面的方法）
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    System.out.println("处理订单异常");
                }
            }
        });
    }

    /**
     * 秒杀优惠券
     * 1.synchronized异步下单--一个一个排队下单
     * 2.基于Redis实现分布式锁
     * 3.基于lua脚本把判断锁的主人并删除锁的操作原子化，防止误删其他用户锁，保证锁的释放
     * 4.把所有的“判断和扣减”全部塞进 Redis 的 Lua 脚本 里，然后利用 阻塞队列 进行异步下单。
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取当前用户（测试时如果没有登录，去 Controller 里或者这里临时写死 1L）
//        Long userId = Thread.currentThread().getId();// 删掉这行测试代码
        Long userId = UserHolder.getUser().getId(); // 换成真实登录用户

        log.info("当前用户id：{}", userId);

        // 5. 执行 Lua 脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.singletonList("seckill:stock:" + voucherId),
                userId.toString(),
                voucherId.toString()
        );

        // 6. 判断结果
        // r=0，下单成功；r=1，库存不足；r=2，不能重复下单
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足！" : "不能重复下单！");
        }

        // 7. Lua 脚本通过，立刻生成订单 ID，扔进队列里，然后直接返回成功给前端！
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        // 扔进阻塞队列
        orderTasks.add(voucherOrder);

        // 8. 秒回前端
        return Result.ok(orderId);
    }

    // 这个方法在后台线程里执行，去写 MySQL
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        log.info("处理订单：{}", voucherOrder.getId());

        // 🚨 必须加这两句，去扣 MySQL 的库存！
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        this.save(voucherOrder);
        System.out.println("后台成功插入订单：" + voucherOrder.getId());
    }
}