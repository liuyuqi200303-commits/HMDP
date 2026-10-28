package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;

import javax.management.Query;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    void loadShopData();

    Result queryById(Long id);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);

    Result updateShopById(Shop shop);

    Result queryShopByName(String name, Integer current);

//    void saveShopToCache(Long id, Long expireSeconds);
}
