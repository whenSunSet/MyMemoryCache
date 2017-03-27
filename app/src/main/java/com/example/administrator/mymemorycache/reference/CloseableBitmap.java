package com.example.administrator.mymemorycache.reference;

import android.graphics.Bitmap;

import java.io.Closeable;
import java.io.IOException;

/**
 * Created by Administrator on 2017/3/27 0027.
 */
public class CloseableBitmap implements Closeable{
    private Bitmap mBitmap;

    public CloseableBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
    }

    @Override
    public void close() throws IOException {
        mBitmap.recycle();
    }
}
