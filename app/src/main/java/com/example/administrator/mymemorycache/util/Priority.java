package com.example.administrator.mymemorycache.util;

/**
 * Created by Administrator on 2017/3/25 0025.
 */

import android.support.annotation.Nullable;

/**
 * Priority levels recognized by the image pipeline.
 */
public enum Priority {
    /**
     * NOTE: DO NOT CHANGE ORDERING OF THOSE CONSTANTS UNDER ANY CIRCUMSTANCES.
     * Doing so will make ordering incorrect.
     */

    /**
     * Lowest priority level. Used for prefetches of non-visible images.
     */
    LOW,

    /**
     * Medium priority level. Used for warming of images that might soon get visible.
     */
    MEDIUM,

    /**
     * Highest priority level. Used for images that are currently visible on screen.
     */
    HIGH;

    /**
     * Gets the higher priority among the two.
     * @param priority1
     * @param priority2
     * @return higher priority
     */
    public static Priority getHigherPriority(
            @Nullable Priority priority1,
            @Nullable Priority priority2) {
        if (priority1 == null) {
            return priority2;
        }
        if (priority2 == null) {
            return priority1;
        }
        if (priority1.ordinal() > priority2.ordinal()) {
            return priority1;
        } else {
            return priority2;
        }
    }

}
