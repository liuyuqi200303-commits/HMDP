//package com.hmdp.service.impl;
//
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import com.hmdp.dto.Result;
//import com.hmdp.entity.SeckillVoucher;
//import com.hmdp.entity.VoucherOrder;
//import com.hmdp.mapper.VoucherOrderMapper;
//import com.hmdp.service.ISeckillVoucherService;
//import com.hmdp.service.IVoucherOrderService;
//import com.hmdp.utils.ILock;
//import com.hmdp.utils.SimpleRedisLock;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.Resource;
//import java.time.LocalDateTime;
//
///**
// * <p>
// *  服务实现类
// * </p>
// *
// * @author 虎哥
// * @since 2021-12-22
// */
//@Service
//public class VoucherOrderServiceImpl_unlocklua extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
//
//    @Resource
//    private ISeckillVoucherService seckillVoucherService;
//
//    @Resource
//    private StringRedisTemplate stringRedisTemplate;
//
//    /**
//     * 秒杀优惠券
//     * 1.synchronized异步下单--一个一个排队下单
//     * 2.基于Redis实现分布式锁
//     * 3.基于lua脚本把判断锁的主人并删除锁的操作原子化，防止误删其他用户锁，保证锁的释放
//     * 4.把所有的“判断和扣减”全部塞进 Redis 的 Lua 脚本 里，然后利用 阻塞队列 进行异步下单。
//     * @param voucherId
//     * @return
//     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1. 创建锁对象（锁的名字叫 "order:" + voucherId）
//        ILock lock = new SimpleRedisLock("order:" + voucherId, stringRedisTemplate);
//        // 2. 尝试获取锁（给个 10 秒超时）
//        boolean isLock = lock.tryLock(10);
//
//        // 3. 如果获取锁失败，直接返回错误（我们这里不排队，直接拒绝，防止用户等太久）
//        if (!isLock) {
//            return Result.fail("服务器繁忙，请稍后再试！");
//        }
//
//        try {
//            // ================= 下面是你原来的业务逻辑 =================
//            SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//            if (voucher == null) {
//                return Result.fail("优惠券不存在");
//            }
//            if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//                return Result.fail("秒杀尚未开始！");
//            }
//            if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//                return Result.fail("秒杀已经结束！");
//            }
//            if (voucher.getStock() < 1) {
//                return Result.fail("库存不足！");
//            }
//
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock = stock - 1")
//                    .eq("voucher_id", voucherId).update();
//
//            if (!success) {
//                return Result.fail("库存不足！");
//            }
//
//            VoucherOrder voucherOrder = new VoucherOrder();
//            long orderId = System.currentTimeMillis();
//            voucherOrder.setId(orderId);
//            voucherOrder.setUserId(1L); // 测试用
//            voucherOrder.setVoucherId(voucherId);
//            this.save(voucherOrder);
//            return Result.ok(voucherOrder);
////            return Result.ok(orderId);
//            // ================= 业务逻辑结束 =================
//        } finally {
//            // 🚨 无论如何，必须在 finally 里释放锁！
//            lock.unlock();
//        }
//
//    }
//}