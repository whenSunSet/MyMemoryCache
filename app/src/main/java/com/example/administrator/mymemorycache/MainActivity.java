package com.example.administrator.mymemorycache;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.internal.util.Predicate;
import com.example.administrator.mymemorycache.cacheKey.CacheKey;
import com.example.administrator.mymemorycache.cacheKey.SimpleCacheKey;
import com.example.administrator.mymemorycache.core.CountingMemoryCache;
import com.example.administrator.mymemorycache.core.DefaultBitmapMemoryCacheParamsSupplier;
import com.example.administrator.mymemorycache.core.ValueDescriptor;
import com.example.administrator.mymemorycache.factoryAndDelegate.InstrumentedMemoryCache;
import com.example.administrator.mymemorycache.factoryAndDelegate.MemoryCacheTracker;
import com.example.administrator.mymemorycache.reference.CloseableReference;
import com.example.administrator.mymemorycache.reference.ResourceReleaser;
import com.example.administrator.mymemorycache.trimmable.MemoryTrimType;


public class MainActivity extends AppCompatActivity {
    InstrumentedMemoryCache<CacheKey, Bitmap> instrumentedMemoryCache;
    Button cacheButton;
    Button getCacheButton;
    Button removeCacheButton;
    Button containsCacheButton;
    Button clearCacheButton;
    ImageView showCacheImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initMemoryCache();

        cacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                putCache();
            }
        });

        getCacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getCache();
            }
        });

        containsCacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (containsCache(new SimpleCacheKey("1")))Toast.makeText(MainActivity.this, "缓存key 1 存在", Toast.LENGTH_SHORT).show();
                else Toast.makeText(MainActivity.this, "缓存key 1 不存在", Toast.LENGTH_SHORT).show();
            }
        });

        clearCacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearCache();
            }
        });

        removeCacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, ("删除了" + removeCache(new SimpleCacheKey("1")) + "个缓存"), Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void initView(){
        cacheButton=(Button)findViewById(R.id.cache);
        getCacheButton=(Button)findViewById(R.id.getCache);
        removeCacheButton=(Button)findViewById(R.id.removeCache);
        clearCacheButton=(Button)findViewById(R.id.clearCache);
        containsCacheButton=(Button)findViewById(R.id.containsCache);
        showCacheImage=(ImageView)findViewById(R.id.imageView);
    }

    private void initMemoryCache(){
        CountingMemoryCache<CacheKey, Bitmap> countingCache=new CountingMemoryCache<>(new ValueDescriptor<Bitmap>() {
            @Override
            public int getSizeInBytes(Bitmap value) {
                return value.getByteCount();
            }
        }, new CountingMemoryCache.CacheTrimStrategy(){

            @Override
            public double getTrimRatio(MemoryTrimType trimType) {
                return 0;
            }
        },new DefaultBitmapMemoryCacheParamsSupplier((ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE)));

        instrumentedMemoryCache=new InstrumentedMemoryCache<CacheKey, Bitmap>(countingCache, new MemoryCacheTracker() {
            @Override
            public void onCacheHit(Object cacheKey) {
                Toast.makeText(MainActivity.this, "Hit 缓存", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCacheMiss() {
                Toast.makeText(MainActivity.this, "Miss 缓存", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCachePut() {
                Toast.makeText(MainActivity.this, "Put 缓存", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void putCache(){
        final Bitmap bitmap= BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher);
        CloseableReference<Bitmap> bitmapCloseableReference=instrumentedMemoryCache.cache(new SimpleCacheKey("1"), CloseableReference.of(bitmap, new ResourceReleaser<Bitmap>() {
            @Override
            public void release(Bitmap value) {
                bitmap.recycle();
            }
        }));

    }

    private void getCache(){
        CloseableReference<Bitmap> closeableReference=instrumentedMemoryCache.get(new SimpleCacheKey("1"));
        if (closeableReference!=null)showCacheImage.setImageBitmap(closeableReference.get());
        else showCacheImage.setImageBitmap(null);
        closeableReference.close();
    }

    private boolean containsCache(final CacheKey cacheKey){
        return instrumentedMemoryCache.contains(new Predicate<CacheKey>() {
            @Override
            public boolean apply(CacheKey c) {
                return cacheKey.equals(c);
            }
        });
    }

    private int removeCache(final CacheKey cacheKey){
        return instrumentedMemoryCache.removeAll(new Predicate<CacheKey>() {
            @Override
            public boolean apply(CacheKey c) {
                return cacheKey.equals(c);
            }
        });
    }

    private void clearCache(){
        ((CountingMemoryCache)instrumentedMemoryCache.getDelegate()).clear();
    }

}
