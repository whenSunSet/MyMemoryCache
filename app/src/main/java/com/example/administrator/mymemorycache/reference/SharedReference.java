package com.example.administrator.mymemorycache.reference;

/**
 * Created by heshixiyang on 2017/3/26.
 */

import android.support.annotation.VisibleForTesting;

import com.example.administrator.mymemorycache.util.Preconditions;

import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.concurrent.GuardedBy;

/**

 * 我的理解：这就是一个包装Value的类，该类只被CloseableReference创建，当使用CloseableReference#of，
 * 创建一个CloseableReference时，会自动创建一个SharedReference对象，此时会传入一个Value，然后将本对象的mRefCount初始化为1，
 * 因为有一个CloseableReference使用了Value。除此之外还会在 static的IdentityHashMap sLiveObjects设置一个Value-int的键值对，
 * 以表示该Value对象有几个SharedReference对象使用，因为相同的Value可以用多个SharedReference包装。
 *
 * 当使用CloseableReference#clone或cloneOrNull，创建一个CloseableReference时表示CloseableReference指向的是同一个对象
 * 此时mRefCount会加1，每将一个CloseableReference close的时候mRefCount会减1。当mRefCount为0的时候，需要使用mResourceReleaser
 * 将Value资源释放，比如如果Value是Bitmap，那么将Bitmap给recycle()掉。
 *
 * 注意：这样一来就有一个问题，因为一个Value可以用多个SharedReference包装，并且sLiveObjects中会保存每个Value使用了多少个SharedReference
 * 进行包装，如果某个SharedReference的mRefCount归零了，那么该Value对象的资源也就被释放了，此时其他包装该Value对象的SharedReference
 * 同样都失效了，虽然他们的mRefCount没有归零。所以感觉这是一个Fresco中的bug。好在Fresco中并没有对同一个Value使用多个SharedReference包装
 * 不过我会尝试去提一个issue，看看Facebook官方如何解释。
 *
 * 关于上面一个问题的解释：拿CountingMemoryCache来说，其在使用of()创建一个CloseableReference的时候，使用的ResourceReleaser是自定义的
 * 那么此时，也可以选择不释放资源，而是等到所有的SharedReference都失效的时候再释放，所以归根到底在使用的时候何时释放资源由ResourceReleaser
 * 决定。
 */
@VisibleForTesting
public class SharedReference<T> {
    //这个Map保存所有的存活对象的引用，正如上面说的那样，对于一个存活对象，当第一个包装他的SharedReference
    //失效的时候，这个对象的资源就已经被回收了。
    @GuardedBy("itself")
    private static final Map<Object, Integer> sLiveObjects = new IdentityHashMap<>();

    @GuardedBy("this")
    private T mValue;
    @GuardedBy("this")
    private int mRefCount;

    private final ResourceReleaser<T> mResourceReleaser;

    /**
     * 这个构造函数只在CloseableReference#of中调用。调用的时候就表示有一个CloseableReference指向了Value
     * Construct a new shared-reference that will 'own' the supplied {@code value}.
     * The reference count will be set to 1. When the reference count decreases to zero
     * {@code resourceReleaser} will be used to release the {@code value}
     * @param value non-null value to manage
     * @param resourceReleaser non-null ResourceReleaser for the value
     */
    public SharedReference(T value, ResourceReleaser<T> resourceReleaser) {
        mValue = Preconditions.checkNotNull(value);
        mResourceReleaser = Preconditions.checkNotNull(resourceReleaser);
        mRefCount = 1;
        addLiveReference(value);
    }

    /**
     * 只在构造函数中被调用，所以可以用来表示同一个Value被几个SharedReference包装了
     */
    private static void addLiveReference(Object value) {
        synchronized (sLiveObjects) {
            Integer count = sLiveObjects.get(value);
            if (count == null) {
                sLiveObjects.put(value, 1);
            } else {
                sLiveObjects.put(value, count + 1);
            }
        }
    }

    /**
     * 这个方法只在deleteReference()中被调用，表示本SharedReference的mRefCount已经归零，然后对sLiveObjects进行操作。
     */
    private static void removeLiveReference(Object value) {
        synchronized (sLiveObjects) {
            Integer count = sLiveObjects.get(value);
            if (count == null) {
                // Uh oh.
//                FLog.wtf(
//                        "SharedReference",
//                        "No entry in sLiveObjects for value of type %s",
//                        value.getClass());
            } else if (count == 1) {
                sLiveObjects.remove(value);
            } else {
                sLiveObjects.put(value, count - 1);
            }
        }
    }

    public synchronized T get() {
        return mValue;
    }

    /**
     * 判断该SharedReference对象是否可用。只要有一个CloseableReference还存在即为可用
     */
    public synchronized boolean isValid() {
        return mRefCount > 0;
    }

    /**
     * 判断某SharedReference对象是否可用。
     */
    public static boolean isValid(SharedReference<?> ref) {
        return ref != null && ref.isValid();
    }

    /**
     * 又多了一个CloseableReference指向Value，先判断是否可用，然后将引用计数加一
     */
    public synchronized void addReference() {
        ensureValid();
        mRefCount++;
    }

    /**
     * 一个CloseableReference被关闭了，将引用计数减一，如果为0，那么将Value中的资源用mResourceReleaser释放了
     */
    public void deleteReference() {
        if (decreaseRefCount() == 0) {
            T deleted;
            synchronized (this) {
                deleted = mValue;
                mValue = null;
            }
            mResourceReleaser.release(deleted);
            removeLiveReference(deleted);
        }
    }

    /**
     * 将引用计数减一。
     */
    private synchronized int decreaseRefCount() {
        ensureValid();
        Preconditions.checkArgument(mRefCount > 0);

        mRefCount--;
        return mRefCount;
    }

    /**
     * 判断该SharedReference是否可用，不可用就抛出异常
     */
    private void ensureValid() {
        if (!isValid(this)) {
            throw new NullReferenceException();
        }
    }

    public synchronized int getRefCountTestOnly() {
        return mRefCount;
    }

    public static class NullReferenceException extends RuntimeException {
        public NullReferenceException() {
            super("Null shared reference");
        }
    }
}
