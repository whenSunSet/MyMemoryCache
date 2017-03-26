package com.example.administrator.mymemorycache.reference;

/**
 * Created by heshixiyang on 2017/3/26.
 */

import android.support.annotation.VisibleForTesting;

import com.example.administrator.mymemorycache.util.Closeables;
import com.example.administrator.mymemorycache.util.Preconditions;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * 我的理解：基于SharedReference的智能指针，在调用of()的时候创建一个对象并创建一个SharedReference。在clone()和cloneOrNull()的时候
 * 创建一个对象然后共享被克隆的CloseableReference的SharedReference。这样一来基于同一个SharedReference的CloseableReference们
 * 就表示一个Value有多少个引用被指向。当调用CloseableReference的close()的时候表示一个指向Value的引用关闭了，一旦所有指向该Value
 * 的CloseableReference都关闭，就表示这个Value资源应该被释放了。
 */
public abstract class CloseableReference<T> implements Cloneable, Closeable {

    private static Class<CloseableReference> TAG = CloseableReference.class;

    //默认的ResourceReleaser
    private static final ResourceReleaser<Closeable> DEFAULT_CLOSEABLE_RELEASER =
            new ResourceReleaser<Closeable>() {
                @Override
                public void release(Closeable value) {
                    try {
                        Closeables.close(value, true);
                    } catch (IOException ioe) {
                        // This will not happen, Closeable.close swallows and logs IOExceptions
                    }
                }
            };

    private static volatile @Nullable
    UnclosedReferenceListener sUnclosedReferenceListener;

    protected @Nullable Throwable mRelevantTrace;

    @GuardedBy("this")
    protected boolean mIsClosed = false;

    protected final SharedReference<T> mSharedReference;

    private static volatile boolean sUseFinalizers = true;

    /**
     * 调用者必须保证sharedReference的引用数量不会降到0以下，这样该引用在运行期才是有效的。
     * 这个构造器只由两个of()方法调用
     */
    private CloseableReference(SharedReference<T> sharedReference) {
        mSharedReference = Preconditions.checkNotNull(sharedReference);
        sharedReference.addReference();
        mRelevantTrace = getTraceOrNull();
    }
    //这个构造器只由两个of()方法调用
    private CloseableReference(T t, ResourceReleaser<T> resourceReleaser) {
        mSharedReference = new SharedReference<T>(t, resourceReleaser);
        mRelevantTrace = getTraceOrNull();
    }

    /**
     * 创建一个CloseableReference
     */
    public static @Nullable <T extends Closeable> CloseableReference<T> of(@Nullable T t) {
        if (t == null) {
            return null;
        } else {
            return makeCloseableReference(t, (ResourceReleaser<T>) DEFAULT_CLOSEABLE_RELEASER);
        }
    }

    /**
     * 同上
     */
    public static @Nullable <T> CloseableReference<T> of(
            @Nullable T t,
            ResourceReleaser<T> resourceReleaser) {
        if (t == null) {
            return null;
        } else {
            return makeCloseableReference(t, resourceReleaser);
        }
    }

    //of()的实现方法
    private static <T> CloseableReference<T> makeCloseableReference(
            @Nullable T t,
            ResourceReleaser<T> resourceReleaser) {
        if (sUseFinalizers) {
            return new CloseableReferenceWithFinalizer<T>(t, resourceReleaser);
        } else {
            return new CloseableReferenceWithoutFinalizer<T>(t, resourceReleaser);
        }
    }

    /**
     * 关闭这个CloseableReference，将SharedReference中的引用计数减一
     */
    @Override
    public void close() {
        synchronized (this) {
            if (mIsClosed) {
                return;
            }
            mIsClosed = true;
        }

        mSharedReference.deleteReference();
    }

    /**
     * 如果没有关闭，返回Value
     */
    public synchronized T get() {
        Preconditions.checkState(!mIsClosed);
        return mSharedReference.get();
    }

    /**
     * 基于相同的SharedReference返回一个新的CloseableReference，SharedReference中的引用计数加一
     */
    @Override
    public synchronized CloseableReference<T> clone() {
        mRelevantTrace = getTraceOrNull();
        Preconditions.checkState(isValid());
        return makeCloseableReference();
    }

    //同上不过可能会返回null
    public synchronized CloseableReference<T> cloneOrNull() {
        mRelevantTrace = getTraceOrNull();
        if (isValid()) {
            return makeCloseableReference();
        }
        return null;
    }

    //clone()和cloneOrNull()的具体实现
    private CloseableReference<T> makeCloseableReference() {
        if (sUseFinalizers) {
            return new CloseableReferenceWithFinalizer<T>(mSharedReference);
        }
        return new CloseableReferenceWithoutFinalizer<T>(mSharedReference);
    }

    /**
     * 判断该closable-reference 是否已经关闭
     */
    public synchronized boolean isValid() {
        return !mIsClosed;
    }

    public static boolean isUnclosedTrackingEnabled() {
        return sUnclosedReferenceListener != null;
    }

    public void setUnclosedRelevantTrance(Throwable relevantTrance) {
        mRelevantTrace = relevantTrance;
    }

    /**
     * 返回SharedReference，只有在测试的时候用
     */
    @VisibleForTesting
    public synchronized SharedReference<T> getUnderlyingReferenceTestOnly() {
        return mSharedReference;
    }

    /**
     * debug的时候使用
     */
    public synchronized int getValueHash() {
        return isValid() ? System.identityHashCode(mSharedReference.get()) : 0;
    }

    /**
     * 判断某个closable-reference是否已经关闭
     */
    public static boolean isValid(@Nullable CloseableReference<?> ref) {
        return ref != null && ref.isValid();
    }

    /**
     *返回某个CloseableReference的克隆
     */
    @Nullable
    public static <T> CloseableReference<T> cloneOrNull(@Nullable CloseableReference<T> ref) {
        return (ref != null) ? ref.cloneOrNull() : null;
    }

    /**
     * 返回一系列CloseableReference<T>的克隆
     */
    public static <T> List<CloseableReference<T>> cloneOrNull(
            Collection<CloseableReference<T>> refs) {
        if (refs == null) {
            return null;
        }
        List<CloseableReference<T>> ret = new ArrayList<>(refs.size());
        for (CloseableReference<T> ref : refs) {
            ret.add(CloseableReference.cloneOrNull(ref));
        }
        return ret;
    }

    /**
     * 安全地关闭某个CloseableReference
     */
    public static void closeSafely(@Nullable CloseableReference<?> ref) {
        if (ref != null) {
            ref.close();
        }
    }

    /**
     * 安全地关闭某一些CloseableReference
     */
    public static void closeSafely(@Nullable Iterable<? extends CloseableReference<?>> references) {
        if (references != null) {
            for (CloseableReference<?> ref : references) {
                closeSafely(ref);
            }
        }
    }

    /**
     * 设置一个监听器，这个监听器在该CloseableReference被GC而没有被显式关闭之前调用
     */
    public static void setUnclosedReferenceListener(
            UnclosedReferenceListener unclosedReferenceListener) {
        sUnclosedReferenceListener = unclosedReferenceListener;
    }

    public static void setUseFinalizers(boolean useFinalizers) {
        sUseFinalizers = useFinalizers;
    }

    private static @Nullable Throwable getTraceOrNull() {
        if (sUnclosedReferenceListener != null) {
            return new Throwable();
        }
        return null;
    }

    public interface UnclosedReferenceListener {
        void onUnclosedReferenceFinalized(CloseableReference<?> ref, Throwable relevantTrace);
    }

    private static class CloseableReferenceWithoutFinalizer<T> extends CloseableReference<T> {

        private static class Destructor extends PhantomReference<CloseableReference> {

            @GuardedBy("Destructor.class")
            private static Destructor sHead;

            private final SharedReference mSharedReference;

            @GuardedBy("Destructor.class")
            private Destructor next;
            @GuardedBy("Destructor.class")
            private Destructor previous;
            @GuardedBy("this")
            private boolean destroyed;

            public Destructor(
                    CloseableReference referent,
                    ReferenceQueue<? super CloseableReference> referenceQueue) {
                super(referent, referenceQueue);
                mSharedReference = referent.mSharedReference;

                synchronized (Destructor.class) {
                    if (sHead != null) {
                        sHead.next = this;
                        previous = sHead;
                    }
                    sHead = this;
                }
            }

            public synchronized boolean isDestroyed() {
                return destroyed;
            }

            public void destroy(boolean correctly) {
                synchronized (this) {
                    if (destroyed) {
                        return;
                    }
                    destroyed = true;
                }

                synchronized (Destructor.class) {
                    if (previous != null) {
                        previous.next = next;
                    }
                    if (next != null) {
                        next.previous = previous;
                    } else {
                        sHead = previous;
                    }
                }

                if (!correctly) {
//                    FLog.w(
//                            TAG,
//                            "GCed without closing: %x %x (type = %s)",
//                            System.identityHashCode(this),
//                            System.identityHashCode(mSharedReference),
//                            mSharedReference.get().getClass().getSimpleName());
                }
                mSharedReference.deleteReference();
            }
        }

        private static final ReferenceQueue<CloseableReference> REF_QUEUE = new ReferenceQueue<>();

        static {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (;;) {
                        try {
                            final Destructor ref = (Destructor) REF_QUEUE.remove();
                            ref.destroy(false);
                        } catch (InterruptedException e) {
                            // Continue. This thread should never be terminated.
                        }
                    }
                }
            }, "CloseableReferenceDestructorThread").start();
        }

        private final Destructor mDestructor;

        private CloseableReferenceWithoutFinalizer(SharedReference<T> sharedReference) {
            super(sharedReference);
            mDestructor = new Destructor(this, REF_QUEUE);
        }

        private CloseableReferenceWithoutFinalizer(T t, ResourceReleaser<T> resourceReleaser) {
            super(t, resourceReleaser);
            mDestructor = new Destructor(this, REF_QUEUE);
        }

        @Override
        public void close() {
            mDestructor.destroy(true);
        }

        @Override
        public boolean isValid() {
            return !mDestructor.isDestroyed();
        }
    }

    private static class CloseableReferenceWithFinalizer<T> extends CloseableReference<T> {

        private CloseableReferenceWithFinalizer(SharedReference<T> sharedReference) {
            super(sharedReference);
        }

        private CloseableReferenceWithFinalizer(T t, ResourceReleaser<T> resourceReleaser) {
            super(t, resourceReleaser);
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                // We put synchronized here so that lint doesn't warn about accessing mIsClosed, which is
                // guarded by this. Lint isn't aware of finalize semantics.
                synchronized (this) {
                    if (mIsClosed) {
                        return;
                    }
                }

                UnclosedReferenceListener listener = sUnclosedReferenceListener;
                if (listener != null) {
                    listener.onUnclosedReferenceFinalized(this, mRelevantTrace);
                } else {
//                    FLog.w(
//                            TAG,
//                            "Finalized without closing: %x %x (type = %s)",
//                            System.identityHashCode(this),
//                            System.identityHashCode(mSharedReference),
//                            mSharedReference.get().getClass().getSimpleName());
                }

                close();
            } finally {
                super.finalize();
            }
        }
    }
}
