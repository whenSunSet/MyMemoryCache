package com.example.administrator.mymemorycache.factoryAndDelegate;

import com.android.internal.util.Predicate;
import com.example.administrator.mymemorycache.core.MemoryCache;
import com.example.administrator.mymemorycache.reference.CloseableReference;

/**
 * Created by heshixiyang on 2017/3/26.
 */
//CountingMemoryCache的代理
public class InstrumentedMemoryCache<K, V> implements MemoryCache<K, V> {

    private final MemoryCache<K, V> mDelegate;
    private final MemoryCacheTracker mTracker;

    public InstrumentedMemoryCache(MemoryCache<K, V> delegate, MemoryCacheTracker tracker) {
        mDelegate = delegate;
        mTracker = tracker;
    }

    @Override
    public CloseableReference<V> get(K key) {
        CloseableReference<V> result = mDelegate.get(key);
        if (result == null) {
            mTracker.onCacheMiss();
        } else {
            mTracker.onCacheHit(key);
        }
        return result;
    }

    @Override
    public CloseableReference<V> cache(K key, CloseableReference<V> value) {
        mTracker.onCachePut();
        return mDelegate.cache(key, value);
    }

    @Override
    public int removeAll(Predicate<K> predicate) {
        return mDelegate.removeAll(predicate);
    }

    @Override
    public boolean contains(Predicate<K> predicate) {
        return mDelegate.contains(predicate);
    }

    public MemoryCache<K, V> getDelegate() {
        return mDelegate;
    }
}
