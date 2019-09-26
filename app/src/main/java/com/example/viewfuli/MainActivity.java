package com.example.viewfuli;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.google.android.material.navigation.NavigationView;
import com.jakewharton.disklrucache.DiskLruCache;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {


    private RecyclerView mRecycleViewPhotos;
    private PhotoAdapter mPhotoAdapter;
    private Handler mMainHandler;
    private SwipeRefreshLayout mRefreshLayout;
    private WebRequest gankAPIWebRequest;
    private Handler gankAPIWebRequestHandle;
    private WebRequest aoAPIWebRequest;
    private Handler aoAPIWebRequestHandle;
    private List<WebRequest> mPictureRequests = new ArrayList<WebRequest>();
    private List<Handler> mPictureRequestHandles = new ArrayList<Handler>();
    private int pictureThreadNum = 0;
    private List<WebRequest> mAPIRequests = new ArrayList<WebRequest>();
    private List<Handler> mAPIRequestHandles = new ArrayList<Handler>();
    private LruCache<String, Bitmap> mPhotoLruCache;
    private int pageNo = 1;
    private int[] lastVisibleItems;
    private int lastVisibleItem;
    private int[] firstVisibleItems;
    private int firstVisibleItem;
    private boolean loading = false;
    private boolean loadmore = true;
    private DiskLruCache mDiskLruCache;
    private static final int MAX_SIZE = 100 * 1024 * 1024;
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
//    private Toolbar mToolbar;
    private final int SOURCE_GANK=0;
    private final int SOURCE_OOOPN=1;
    private final int SOURCE_APIOPEN=2;
    private int mSourceType=SOURCE_GANK;
    private String sourceUrl;
    private String sourceURLKey;
    private boolean openPhotoDetail=true;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //隐藏标题栏
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_drawer_layout);
        sourceUrl=getString(R.string.gank_source_url);
        sourceURLKey=getString(R.string.ooopn_source_url_key);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mNavigationView = (NavigationView) findViewById(R.id.navigation_view);
        mRefreshLayout = findViewById(R.id.refresh_layout);
//        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        //mDrawerLayout与mToolbar关联起来
//        ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, mToolbar, R.string.open, R.string.close);
        //初始化状态
 //       actionBarDrawerToggle.syncState();
        //ActionBarDrawerToggle implements DrawerLayout.DrawerListener
//      mDrawerLayout.addDrawerListener(actionBarDrawerToggle);

//        //监听
//        mDrawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
//            @Override
//            public void onDrawerSlide(@NonNull View view, float v) {
//                Log.i("---", "滑动中");
//            }
//
//            @Override
//            public void onDrawerOpened(@NonNull View view) {
//                Log.i("---", "打开");
//            }
//
//            @Override
//            public void onDrawerClosed(@NonNull View view) {
//                Log.i("---", "关闭");
//            }
//
//            @Override
//            public void onDrawerStateChanged(int i) {
//                Log.i("---", "状态改变");
//            }
//        });


        //NavigationView 内容点击事件
        mNavigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                String title = (String) menuItem.getTitle();
                if(title.equals(getString(R.string.gank_name))){
                    mSourceType=SOURCE_GANK;
                    sourceUrl=getString(R.string.gank_source_url);
                    gankAPIWebRequestHandle.sendEmptyMessage(3);
                    mRefreshLayout.setRefreshing(true);
                }else if(title.equals(getString(R.string.ooopn_name))){
                    mSourceType=SOURCE_OOOPN;
                    sourceUrl=getString(R.string.ooopn_source_url);
                    sourceURLKey=getString(R.string.ooopn_source_url_key);
                    mAPIRequestHandles.get(0).sendEmptyMessage(3);
                    mRefreshLayout.setRefreshing(true);
                }else if(title.equals(getString(R.string.btstu_name))){
                    mSourceType=SOURCE_OOOPN;
                    sourceUrl=getString(R.string.btstu_source_url);
                    sourceURLKey=getString(R.string.btstu_source_url_key);
                    mAPIRequestHandles.get(0).sendEmptyMessage(3);
                    mRefreshLayout.setRefreshing(true);
                }else if(title.equals(getString(R.string.apiopen_name))){
                    mSourceType=SOURCE_APIOPEN;
                    sourceUrl=getString(R.string.apiopen_source_url);
                    aoAPIWebRequestHandle.sendEmptyMessage(3);
                    mRefreshLayout.setRefreshing(true);
                }
                mDrawerLayout.closeDrawers();
                return false;
            }
        });


        //初始化磁盘缓存
        if (mDiskLruCache == null || mDiskLruCache.isClosed()) {
            try {
                File cacheDir = CacheUtil.getDiskCacheDir(this, "CacheDir");
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }
                //初始化DiskLruCache
                mDiskLruCache = DiskLruCache.open(cacheDir, 1, 1, MAX_SIZE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //初始化内存缓存
        long maxMemory = Runtime.getRuntime().maxMemory();
        int cacheSize = (int) (maxMemory / 4);
        mPhotoLruCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };




        //初始化图片列表界面
        mRecycleViewPhotos = findViewById(R.id.recycler_view_photos);
        mRecycleViewPhotos.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));


        //设置Adapter
        mPhotoAdapter = new PhotoAdapter();
        mRecycleViewPhotos.setAdapter(mPhotoAdapter);
        //增加上拉加载功能及预加载功能
        mRecycleViewPhotos.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                StaggeredGridLayoutManager staggeredGridLayoutManager = (StaggeredGridLayoutManager) recyclerView.getLayoutManager();
                if (newState == RecyclerView.SCROLL_STATE_IDLE &&
                        lastVisibleItem + 1 == mPhotoAdapter.getItemCount() && !loading && loadmore) {
                    loading = true;
                    System.out.println("pageno:" + pageNo);
                    switch (mSourceType){
                        case SOURCE_GANK:{
                            gankAPIWebRequestHandle.sendEmptyMessage(0);
                            break;
                        }
                        case SOURCE_OOOPN:{
                            for(int i=0; i<10;i++){
                                mAPIRequestHandles.get(i).sendEmptyMessage(0);
                            }
                            break;
                        }case SOURCE_APIOPEN:{
                                aoAPIWebRequestHandle.sendEmptyMessage(0);
                            break;
                        }

                    }


                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    List<PhotoItem> photoItemList = mPhotoAdapter.getPhotoItemList();
                    String url = null;
                    //预加载前十个
                    if (firstVisibleItem - 10 > 0) {
                        for (int i = firstVisibleItem - 10; i < firstVisibleItem; i++) {
                            url = photoItemList.get(i).getUrl();
                            if (mPhotoLruCache.get(url) == null) {
                                Message message = mPictureRequestHandles.get(pictureThreadNum % 10).obtainMessage();
                                pictureThreadNum++;
                                message.what = 2;
                                message.obj = url;
                                message.arg2=mSourceType;
                                message.sendToTarget();
                            }
                        }
                    } else {
                        for (int i = 0; i < firstVisibleItem; i++) {
                            url = photoItemList.get(i).getUrl();
                            if (mPhotoLruCache.get(url) == null) {
                                Message message = mPictureRequestHandles.get(pictureThreadNum % 10).obtainMessage();
                                pictureThreadNum++;
                                message.what = 2;
                                message.obj = url;
                                message.arg2=mSourceType;
                                message.sendToTarget();
                            }
                        }
                    }
                    //预加载后十个
                    if (lastVisibleItem + 11 < photoItemList.size()) {
                        for (int i = lastVisibleItem + 1; i < lastVisibleItem + 11; i++) {
                            url = photoItemList.get(i).getUrl();
                            if (mPhotoLruCache.get(url) == null) {
                                Message message = mPictureRequestHandles.get(pictureThreadNum % 10).obtainMessage();
                                pictureThreadNum++;
                                message.what = 2;
                                message.obj = url;
                                message.arg2=mSourceType;
                                message.sendToTarget();
                            }
                        }
                    } else {
                        for (int i = lastVisibleItem + 1; i < photoItemList.size(); i++) {
                            url = photoItemList.get(i).getUrl();
                            if (mPhotoLruCache.get(url) == null) {
                                Message message = mPictureRequestHandles.get(pictureThreadNum % 10).obtainMessage();
                                pictureThreadNum++;
                                message.what = 2;
                                message.obj = url;
                                message.arg2=mSourceType;
                                message.sendToTarget();
                            }
                        }
                    }

                }
            }
            //计算第一个及最后一个item位置
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
//                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
//                lastVisibleItem = linearLayoutManager.findLastCompletelyVisibleItemPosition();
//                firstVisibleItem = linearLayoutManager.findFirstCompletelyVisibleItemPosition();
                StaggeredGridLayoutManager staggeredGridLayoutManager = (StaggeredGridLayoutManager) recyclerView.getLayoutManager();
                lastVisibleItems = staggeredGridLayoutManager.findLastCompletelyVisibleItemPositions(null);
                lastVisibleItem = findMax(lastVisibleItems);
                firstVisibleItems = staggeredGridLayoutManager.findFirstCompletelyVisibleItemPositions(null);
                firstVisibleItem = findMin(firstVisibleItems);
            }
        });

        //主线程Handler
        mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 0) {
                    System.out.println("增量刷新：" + ((List<PhotoItem>) msg.obj).size());
                    //System.out.println("主线程的mPhotoAdapter"+mPhotoAdapter);
                    mPhotoAdapter.getPhotoItemList().addAll((List<PhotoItem>) msg.obj);
                    System.out.println("PhotoItemList的Size" + mPhotoAdapter.getPhotoItemList().size());
                    mPhotoAdapter.notifyDataSetChanged();
                    loading = false;
                } else if (msg.what == 1) {
                    //System.out.println(msg.arg1);
                    mPhotoAdapter.notifyItemChanged(msg.arg1);
                } else if (msg.what == 2) {
                    System.out.println("全量刷新：" + ((List<PhotoItem>) msg.obj).size());
                    //System.out.println("主线程的mPhotoAdapter"+mPhotoAdapter);
                    mPhotoAdapter.getPhotoItemList().clear();
                    mPhotoAdapter.getPhotoItemList().addAll((List<PhotoItem>) msg.obj);
                    System.out.println("PhotoItemList的Size" + mPhotoAdapter.getPhotoItemList().size());
                    mPhotoAdapter.notifyDataSetChanged();
                    loading = false;
                    if (mRefreshLayout.isRefreshing())
                        mRefreshLayout.setRefreshing(false);
                }
            }
        };


        //GANK专用API线程
        gankAPIWebRequest = new WebRequest();
        new Thread(gankAPIWebRequest).start();
        gankAPIWebRequestHandle = new Handler(gankAPIWebRequest.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                //更新一页数据并进行页面全量刷新
                if (msg.what == 0) {
                    loading = true;
//                    System.out.println("加载页数：" + pageNo);
                    PhotoResult photoResult = PhotoFetcher.fetchPhotoItems(sourceUrl + pageNo, "UTF-8");
                    pageNo++;
                    loadmore = !(photoResult.getResults().size() == 0);
                    Message message = mMainHandler.obtainMessage();
                    message.what = 0;
                    message.obj = photoResult.getResults();
                    message.sendToTarget();
                }
                //重新加载第一页数据
                else if (msg.what == 3) {
                    loading = true;
                    pageNo = 1;
//                    System.out.println("加载页数：" + pageNo);
                    PhotoResult photoResult = PhotoFetcher.fetchPhotoItems(sourceUrl + pageNo, "UTF-8");
                    pageNo++;
                    Message message = mMainHandler.obtainMessage();
                    message.what = 2;
                    message.obj = photoResult.getResults();
                    message.sendToTarget();
                }
            }
        };

        //APIOPEN专用API线程
        aoAPIWebRequest = new WebRequest();
        new Thread(aoAPIWebRequest).start();
        aoAPIWebRequestHandle = new Handler(aoAPIWebRequest.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                //更新一页数据并进行页面全量刷新
                if (msg.what == 0) {
                    loading = true;
//                    System.out.println("加载页数：" + pageNo);
                    PhotoResult photoResult = PhotoFetcher.getAPIOpenPhotoItems(sourceUrl , "UTF-8");
                    Message message = mMainHandler.obtainMessage();
                    message.what = 0;
                    message.obj = photoResult.getResults();
                    message.sendToTarget();
                }
                //重新加载第一页数据
                else if (msg.what == 3) {
                    loading = true;
                    PhotoResult photoResult = PhotoFetcher.getAPIOpenPhotoItems(sourceUrl , "UTF-8");
                    Message message = mMainHandler.obtainMessage();
                    message.what = 2;
                    message.obj = photoResult.getResults();
                    message.sendToTarget();
                }
            }
        };


        //API线程Handler
        for(int i=0;i<10;i++){
            WebRequest apiRequest = new WebRequest();
            mAPIRequests.add(apiRequest);
            new Thread(apiRequest).start();
            Handler apiRequestHandle = new Handler(apiRequest.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    loading = true;
                    List<PhotoItem> photoItemList=new ArrayList<PhotoItem>();
                    //更新一页数据并进行页面全量刷新
                    if (msg.what == 0) {
                        for(int j=0;j<10;j++){
                            PhotoItem photoItem=PhotoFetcher.getRandomPhotoItem(sourceUrl,"UTF-8",sourceURLKey);
                            if(photoItem!=null)
                                photoItemList.add(photoItem);
                        }
                        Message message = mMainHandler.obtainMessage();
                        message.what = 0;
                        message.obj = photoItemList;
                        message.sendToTarget();
                    }
                    //重新加载第一页数据
                    else if (msg.what == 3) {
                        for(int j=0;j<20;j++){
                            PhotoItem photoItem=PhotoFetcher.getRandomPhotoItem(sourceUrl,"UTF-8",sourceURLKey);
                            if(photoItem!=null)
                                photoItemList.add(photoItem);
                        }
                        Message message = mMainHandler.obtainMessage();
                        message.what = 2;
                        message.obj = photoItemList;
                        message.sendToTarget();
                    }
                }
            };
            mAPIRequestHandles.add(apiRequestHandle);
        }





        //获取图片线程
        for (int i = 0; i < 10; i++) {
            WebRequest mPictureRequest = new WebRequest();
            mPictureRequests.add(mPictureRequest);
            new Thread(mPictureRequest).start();
//            System.out.println("图片线程ID：" + Thread.currentThread().getId());
            Handler mPictureRequestHandle = new Handler(mPictureRequest.getLooper()) {
                @Override
                public void handleMessage(Message msg) {

                    //加载一个图片并更新到页面上
                    if (msg.what == 1 && msg.arg2==mSourceType) {
                        String url = (String) msg.obj;
                        try {
//                        byte[] photoData = PhotoFetcher.getUrlBytes(url);
//                        Bitmap PhotoBitmap = BitmapFactory.decodeByteArray(photoData, 0, photoData.length);
                            Bitmap PhotoBitmap = getDiskCache(url);
                            if (PhotoBitmap == null) {
//                                System.out.println("网络加载：" + url);
                                PhotoBitmap = PhotoFetcher.getUrlBitMap(url);
                                if (PhotoBitmap != null) {
                                    mPhotoLruCache.put(url, PhotoBitmap);
                                    putDiskCache(url, PhotoBitmap);
                                } else {
                                    mPhotoAdapter.mPhotoItemList.get(msg.arg1).setAvailability(false);
                                }
                                Message message = mMainHandler.obtainMessage();
                                message.what = 1;
                                message.arg1 = msg.arg1;
                                message.sendToTarget();


                            } else {
//                                System.out.println("从磁盘加载至内存" + url);
                                mPhotoLruCache.put(url, PhotoBitmap);
                                Message message = mMainHandler.obtainMessage();
                                message.what = 1;
                                message.arg1 = msg.arg1;
                                message.sendToTarget();
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    //加载一个图片并添加到缓存里
                    else if (msg.what == 2 && msg.arg2==mSourceType) {
                        String url = (String) msg.obj;
                        //System.out.println("缓存加载："+url);
                        try {
                            Bitmap PhotoBitmap = getDiskCache(url);
                            if (PhotoBitmap == null) {
//                                System.out.println("网络加载：" + url);
                                PhotoBitmap = PhotoFetcher.getUrlBitMap(url);
                                if (PhotoBitmap != null) {
                                    mPhotoLruCache.put(url, PhotoBitmap);
                                    putDiskCache(url, PhotoBitmap);
                                }
                            } else {
//                                System.out.println("从磁盘加载至内存" + url);
                                mPhotoLruCache.put(url, PhotoBitmap);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }
            };
            mPictureRequestHandles.add(mPictureRequestHandle);
        }


       //下拉刷新
        mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                switch (mSourceType){
                    case SOURCE_GANK:{
                        gankAPIWebRequestHandle.sendEmptyMessage(3);
                        break;
                    }
                    case SOURCE_OOOPN:{
                            mAPIRequestHandles.get(0).sendEmptyMessage(3);
                            break;

                    }case SOURCE_APIOPEN:{

                            aoAPIWebRequestHandle.sendEmptyMessage(0);

                        break;
                    }

                }
            }
        });

        //初始化数据
        switch (mSourceType){
            case SOURCE_GANK:{
                gankAPIWebRequestHandle.sendEmptyMessage(0);
                break;
            }
            case SOURCE_OOOPN:{
                for(int i=0; i<10;i++){
                    mAPIRequestHandles.get(i).sendEmptyMessage(0);
                }
                break;
            }case SOURCE_APIOPEN:{

                    aoAPIWebRequestHandle.sendEmptyMessage(0);

                break;
            }

        }
    }


//查找最大值
    private int findMax(int[] intArray) {
        int max = intArray[0];
        for (int value : intArray) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }
//查找最小值
    private int findMin(int[] intArray) {
        int min = intArray[0];
        for (int value : intArray) {
            if (value < min) {
                min = value;
            }
        }
        return min;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        gankAPIWebRequest.getLooper().quitSafely();
    }


    //显示图片的ViewHolder
    private class PhotoHolder extends RecyclerView.ViewHolder {
        //        TextView mTextViewPhotoName;
        ImageView mImageViewPhoto;
        PhotoItem mPhotoItem;

        public PhotoHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.list_item_photo, parent, false));
//            mTextViewPhotoName=itemView.findViewById(R.id.text_view_photo_name);
            mImageViewPhoto = itemView.findViewById(R.id.image_view_photo);
        }

        public void bind(PhotoItem photoItem) {
            mPhotoItem = photoItem;
            if (mPhotoItem.isAvailability()) {
                mImageViewPhoto.setImageResource(R.drawable.loading);
                Bitmap bitmap=mPhotoLruCache.get(mPhotoItem.getUrl());
                if ( bitmap!= null) {
                    //自己计算ImageView的大小，因为适配器自己适配的有问题。
                    StaggeredGridLayoutManager.LayoutParams layoutParams = (StaggeredGridLayoutManager.LayoutParams) mImageViewPhoto.getLayoutParams();//获取你要填充图片的布局的layoutParam
                    layoutParams.height = (int) (((float) bitmap.getHeight()) / bitmap.getWidth() * getScreenWidth(MainActivity.this) / 2 );
                    //因为是2列,所以宽度是屏幕的一半,高度是根据bitmap的高/宽*屏幕宽的一半
                    layoutParams.width =  getScreenWidth(MainActivity.this) / 2;//这个是布局的宽度
                    mImageViewPhoto.setLayoutParams(layoutParams);
                    //System.out.println("正在渲染："+this.getAdapterPosition());
                    mImageViewPhoto.setImageBitmap(mPhotoLruCache.get(mPhotoItem.getUrl()));
                    mImageViewPhoto.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            // 给bnt1添加点击响应事件
                            if(openPhotoDetail){
                                openPhotoDetail=false;
                                Bitmap clickBitmap = mPhotoLruCache.get(mPhotoItem.getUrl());
                                if (clickBitmap == null) {
//                            System.out.println("居然是空的！！！！");
                                    clickBitmap = getDiskCache(mPhotoItem.getUrl());
                                }
                                if (clickBitmap != null) {
//                                System.out.println("!!!!!!!!!!!!!!!!clickBitmap:" + clickBitmap);
                                    File avaterFile = new File(getCacheDir(), "tempBitmap.png");//设置文件名称

                                    if (avaterFile.exists()) {
                                        avaterFile.delete();
                                    }
                                    try {
                                        avaterFile.createNewFile();
                                        FileOutputStream fos = new FileOutputStream(avaterFile);
                                        clickBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                        fos.flush();
                                        fos.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }

                                    Intent intent = new Intent(MainActivity.this, PictureDetail.class);
                                    intent.putExtra("url", mPhotoItem.getUrl());
                                    intent.putExtra("urlMD5", hashKeyForDisk(mPhotoItem.getUrl()));
                                    startActivity(intent);
                                    openPhotoDetail=true;
                                }
                            }


                        }
                    });

                } else {
                    Message message = mPictureRequestHandles.get(pictureThreadNum % 10).obtainMessage();
                    pictureThreadNum++;
                    message.what = 1;
                    message.arg1 = this.getAdapterPosition();
                    message.arg2 = mSourceType;
                    //System.out.println("message.arg1=" + this.getAdapterPosition());
                    message.obj = mPhotoItem.getUrl();
                    message.sendToTarget();
                }
            } else {
                StaggeredGridLayoutManager.LayoutParams layoutParams = (StaggeredGridLayoutManager.LayoutParams) mImageViewPhoto.getLayoutParams();//获取你要填充图片的布局的layoutParam
                layoutParams.height = 0;
                layoutParams.width =  0;
                mImageViewPhoto.setLayoutParams(layoutParams);

            }
        }
    }

//加载圈圈的ViewHolder
    private class FooterViewHolder extends RecyclerView.ViewHolder {
        private ProgressBar mFootViewProgress;

        public FooterViewHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.foot_view, parent, false));
//            mTextViewPhotoName=itemView.findViewById(R.id.text_view_photo_name);
            mFootViewProgress = itemView.findViewById(R.id.foot_view_progress);
            mFootViewProgress.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    switch (mSourceType){
                        case SOURCE_GANK:{
                            gankAPIWebRequestHandle.sendEmptyMessage(0);
                            break;
                        }
                        case SOURCE_OOOPN:{
                            for(int i=0; i<10;i++){
                                mAPIRequestHandles.get(i).sendEmptyMessage(0);
                            }
                            break;
                        }case SOURCE_APIOPEN:{

                                aoAPIWebRequestHandle.sendEmptyMessage(0);

                            break;
                        }

                    }

                }
            });
        }

        public ProgressBar getmFootViewProgress() {
            return mFootViewProgress;
        }
    }


    //适配器
    private class PhotoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_ITEM = 0;
        private static final int TYPE_FOOTER = 1;


        private List<PhotoItem> mPhotoItemList = new ArrayList<>();


        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(MainActivity.this);
            if (viewType == TYPE_ITEM) {
                return new PhotoHolder(layoutInflater, parent);
            } else if (viewType == TYPE_FOOTER) {
                return new FooterViewHolder(layoutInflater, parent);
            }
            return null;
//            return new PhotoHolder(layoutInflater, parent);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof PhotoHolder) {
//                System.out.println("PhotoHolder:"+position);
                PhotoItem photoItem = mPhotoItemList.get(position);
                ((PhotoHolder) holder).bind(photoItem);
                holder.itemView.setTag(position);
            } else if (holder instanceof FooterViewHolder) {
//               System.out.println("FooterViewHolder:"+position);
                FooterViewHolder footerViewHolder = (FooterViewHolder) holder;

                if (loadmore)
                    footerViewHolder.getmFootViewProgress().setVisibility(View.VISIBLE);
                else
                    footerViewHolder.getmFootViewProgress().setVisibility(View.GONE);

            }

        }

        @Override
        public int getItemCount() {
            return mPhotoItemList.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            if (position + 1 == getItemCount()) {
                //最后一个item设置为footerView
//                System.out.println("TYPE_FOOTER");
                return TYPE_FOOTER;
            } else {
//                System.out.println("TYPE_ITEM");
                return TYPE_ITEM;
            }
        }


        public List<PhotoItem> getPhotoItemList() {
            return mPhotoItemList;
        }

    }

    //旋转屏幕之后重新适配
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mPhotoAdapter.notifyDataSetChanged();
    }

//String转MD5
    public static String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    //字节转String，协助上面的MD5方法
    private static String bytesToHexString(byte[] bytes) {

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    //获取磁盘缓存的图片
    private Bitmap getDiskCache(String url) {
        try {
            String key = hashKeyForDisk(url);
            DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
            if (snapshot != null) {
                InputStream in = snapshot.getInputStream(0);
//                System.out.println("磁盘读取："+url);
                return BitmapFactory.decodeStream(in);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    //向磁盘缓存中添加图片
    private void putDiskCache(String url, Bitmap bitmap) {
        try {
            String key = hashKeyForDisk(url);
            DiskLruCache.Editor editor = mDiskLruCache.edit(key);
            if (editor != null) {
                OutputStream outputStream = editor.newOutputStream(0);
                if (writeToStream(outputStream, bitmap)) {
                    editor.commit();
//                    System.out.println("磁盘存储："+url);
                } else {
                    editor.abort();
                }
            }
            mDiskLruCache.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    ///图片写到输出流的方法，辅助上面存储图片到磁盘的方法
    private boolean writeToStream(OutputStream fos, Bitmap bitmap) {

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            baos.writeTo(fos);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }


    }



    //获取屏幕宽度的方法
    public static int getScreenWidth(Context context)
    {
        WindowManager wm = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(outMetrics);
        return outMetrics.widthPixels;
    }

}

