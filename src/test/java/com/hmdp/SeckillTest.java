package com.hmdp;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class SeckillTest {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Test
    public void testSeckillConcurrent() throws InterruptedException {
        // 1. 准备并发工具
        // 模拟 200 个并发用户
        int threadCount = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        // CountDownLatch 就像发令枪，让 200 个线程准备好后同时开跑
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);

        // 这里必须换成你数据库里刚刚插入的优惠券 ID (我们插的是 10)
        Long voucherId = 10L;

        // 2. 循环发起请求
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    // 让当前线程阻塞，等待发令枪响
                    countDownLatch.await();

                    // 抢购
                    // 抢购
                    voucherOrderService.seckillVoucher(voucherId);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // 每个线程准备好后，发令枪的倒计时减 1
            countDownLatch.countDown();
        }

        // 主线程睡 3 秒，等待所有 200 个并发请求执行完毕
        Thread.sleep(30000);
        System.out.println("=== 并发测试结束 ===");
    }

    @Test
    public void testSeckillOneByOne() {
        Long voucherId = 10L;
        // 模拟 105 个不同用户，依次下单
        for (long i = 1; i <= 105; i++) {
            // 临时覆盖当前的用户 ID，模拟不同用户
            // 你需要在 VoucherOrderServiceImpl 的 seckillVoucher 里加一句：
            // Long userId = (Long) RequestContextHolder... 太麻烦，我们直接改测试逻辑。

            // 因为你的 Service 里写死了 Thread.currentThread().getId()
            // 在单线程循环里，每次都是同一个线程，会被“一人一单”拦截
            // 所以，你需要把 Service 里的 userId 临时改成从参数传！

            // 🚨 快捷改法：你的 Service 里 `Long userId = Thread.currentThread().getId();`
            // 改成 `Long userId = Long.valueOf(new Random().nextInt(10000));`
        }
    }
}