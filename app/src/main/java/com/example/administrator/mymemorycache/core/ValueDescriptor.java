package com.example.administrator.mymemorycache.core;

/**
 * Created by heshixiyang on 2017/3/26.
 */
/**
 */
public interface ValueDescriptor<V> {

    int getSizeInBytes(V value);
}

