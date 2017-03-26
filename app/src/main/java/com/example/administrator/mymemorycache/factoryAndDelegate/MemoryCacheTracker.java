package com.example.administrator.mymemorycache.factoryAndDelegate;

/**
 * Created by Administrator on 2017/3/25 0025.
 */
//用于让使用者监听内存缓存事件
public interface MemoryCacheTracker<K> {
    void onCacheHit(K cacheKey);
    void onCacheMiss();
    void onCachePut();
}

