package com.jiawa.lyw.itinerarymap.application;

import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.CachedRoute;

import java.time.Duration;
import java.util.Optional;

/** 派生路线缓存；实现失败必须表现为缓存未命中，不能阻断地图读取。 */
public interface RouteReferenceCache {
    Optional<CachedRoute> find(String key);

    void put(String key, CachedRoute route, Duration ttl);
}
