package com.hmdp;

import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class ShopGepTest {

    @Resource
    private IShopService shopService;

    @Test
    public void testLoadShopData() {
        shopService.loadShopData();
        System.out.println("店铺坐标加载完毕！");
    }
}