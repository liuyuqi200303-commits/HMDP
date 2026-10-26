package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@RequestMapping("/voucher-order")
//如果你类名上面加了 @RequiredArgsConstructor（Lombok 注解），那么你只要把字段声明为 private final，Lombok 就会在编译时自动帮你生成这个构造方法！
//@RequiredArgsConstructor
public class VoucherOrderController {
    private final IVoucherOrderService iVoucherOrderService;

    public VoucherOrderController(IVoucherOrderService iVoucherOrderService) {
        this.iVoucherOrderService = iVoucherOrderService;
    }

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        log.info("秒杀优惠券：{}", voucherId);
        return iVoucherOrderService.seckillVoucher(voucherId);
    }
}
