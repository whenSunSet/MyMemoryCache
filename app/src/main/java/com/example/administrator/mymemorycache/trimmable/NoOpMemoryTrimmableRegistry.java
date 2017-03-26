package com.example.administrator.mymemorycache.trimmable;

/**
 * Created by Administrator on 2017/3/25 0025.
 */
/**
 * 一个实现了{@link MemoryTrimmableRegistry}但是什么都没做的class
 * 具体的监听需要使用者去做
 */
public class NoOpMemoryTrimmableRegistry implements MemoryTrimmableRegistry {
    private static NoOpMemoryTrimmableRegistry sInstance = null;

    public NoOpMemoryTrimmableRegistry() {
    }

    public static synchronized NoOpMemoryTrimmableRegistry getInstance() {
        if (sInstance == null) {
            sInstance = new NoOpMemoryTrimmableRegistry();
        }
        return sInstance;
    }

    /** Register an object. */
    public void registerMemoryTrimmable(MemoryTrimmable trimmable) {
    }

    /** Unregister an object. */
    public void unregisterMemoryTrimmable(MemoryTrimmable trimmable) {
    }
}


