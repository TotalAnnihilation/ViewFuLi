package com.example.viewfuli;



import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PictureDetail extends Activity implements View.OnTouchListener {
    Matrix matrix = new Matrix();
    Matrix savedMatrix = new Matrix();
    DisplayMetrics displayMetrics;
    ImageView picture;
    ImageView imageSave;
    Bitmap bitmap;
    String url;
    String urlMD5;
    Boolean saveState=false;
    Boolean saveResult;
    private LruCache<String, Bitmap> mPhotoLruCache;

    float defaultScale ;// 最小缩放比例
    static final int NONE = 0;// 初始状态
    static final int DRAG = 1;// 拖动状态
    static final int ZOOM = 2;// 缩放状态
    int mode = NONE;
    PointF pointF = new PointF();
    PointF mid = new PointF();
    float scaleX = 0;
    float scaleY = 0;
    float dist = 1f;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture_detail);
        picture = (ImageView) findViewById(R.id.picture);// 获取控件
        imageSave= (ImageView) findViewById(R.id.image_save);
        url=getIntent().getStringExtra("url");
        urlMD5=getIntent().getStringExtra("urlMD5");
        try{
            File bitmapFile=new File(getCacheDir(),"tempBitmap.png");
            System.out.println(bitmapFile.getPath());
            if(bitmapFile.exists()){
                bitmap=BitmapFactory.decodeFile(bitmapFile.getPath());
                System.out.println("bitmap"+bitmap);
            }
        }catch(Exception e){
            e.printStackTrace();
            System.out.println("读取图片出错"+e.getMessage());
        }
        if(picture!=null) {
            picture.setImageBitmap(bitmap);// 填充控件
        }
        picture.setOnTouchListener(this);// 设置触屏监听
        displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);// 获取分辨率

        defaulZoom();
        center(true,true);
        picture.setImageMatrix(matrix);

        imageSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(saveState)
                    return;
                saveState=true;
                String[] PERMISSIONS = {
                        "android.permission.READ_EXTERNAL_STORAGE",
                        "android.permission.WRITE_EXTERNAL_STORAGE" };
                //检测是否有写的权限
                int permission = ContextCompat.checkSelfPermission(PictureDetail.this,
                        "android.permission.WRITE_EXTERNAL_STORAGE");
                if (permission != PackageManager.PERMISSION_GRANTED) {
                    // 没有写的权限，去申请写的权限，会弹出对话框
                    ActivityCompat.requestPermissions(PictureDetail.this, PERMISSIONS,1);
                }
                saveResult=saveToSystemGallery(getBaseContext(),bitmap,urlMD5);
                if(saveResult){

                    Toast toast=Toast.makeText(PictureDetail.this,"已将图片保存至相册",Toast.LENGTH_SHORT    );
                                   toast.show();
                }else{
                    Toast toast=Toast.makeText(PictureDetail.this,"图片保存失败",Toast.LENGTH_SHORT    );
                                  toast.show();
                }
                saveState=false;

            }
        });
    }
    /**
     * 触屏监听
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            // 单指按下
            case MotionEvent.ACTION_DOWN:
                System.out.println("单指按下");
                savedMatrix.set(matrix);
                pointF.set(event.getX(), event.getY());
                mode = DRAG;
                break;
            //另外的手指按下
            case MotionEvent.ACTION_POINTER_DOWN:
                System.out.println("另外的手指按下");
                dist = space2Point(event);
                // 如果连续获取的两点之间的距离大于10f说明是多点触摸模式
                if (space2Point(event) > 10f) {
                    savedMatrix.set(matrix);
                    midPoint(mid, event);
                    mode = ZOOM;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    matrix.set(savedMatrix);
                    matrix.postTranslate(event.getX() - pointF.x, event.getY()
                            - pointF.y);
                } else if (mode == ZOOM) {
                    float moveDistance = space2Point(event);
                    if (moveDistance > 10f) {
                        picture.post(new Runnable(){
                            @Override
                            public void run() {
                                //获得图片的变换矩阵
                                Matrix m = picture.getImageMatrix();
                                float[] values = new float[9];
                                m.getValues(values);
                                //获取变化矩阵中的MSCALE_X和MSCALE_Y的值
                                scaleX = values[0];
                                scaleY = values[4];
                                if (scaleX<0.8f){
                                    mode = NONE;
                                    float minScale =0.81f/scaleX;
                                    matrix.postScale(minScale, minScale, mid.x, mid.y);
                                }else if(scaleX>2.5f){
                                    mode = NONE;
                                    float maxScale =2.5f/scaleX;
                                    matrix.postScale(maxScale, maxScale, mid.x, mid.y);
                                }
                            }});
                        matrix.set(savedMatrix);
                        float tScale = moveDistance / dist;
                        matrix.postScale(tScale, tScale, mid.x, mid.y);
                    }
                }
                break;
        }
        picture.setImageMatrix(matrix);
        center(true,true);
        return true;
    }


    /**
     * 为了适应屏幕的宽或高，需要做适度的缩放
     */
    private void defaulZoom() {
        defaultScale = Math.min(
                ((float) displayMetrics.widthPixels / (float) bitmap.getWidth()),
                ((float) displayMetrics.heightPixels / (float) bitmap.getHeight()));
        matrix.postScale(defaultScale, defaultScale);
    }


    /**
     * 让图片居中显示
     */
    protected void center(boolean isHorizontal, boolean isVertical) {

        Matrix m = new Matrix();
        m.set(matrix);
        //设置的图片显示的位置在左上角
        RectF rect = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        m.mapRect(rect);

        float height = rect.height();
        float width = rect.width();

        float deltaX = 0, deltaY = 0;

        if (isVertical) {
            int screenHeight = displayMetrics.heightPixels;
            if (height < screenHeight) {
                //如果图片的高度小于屏幕的高度，那么就要向下移动图片，因为上面的rect设置的图片默认显示在左上角
                deltaY = (screenHeight - height) / 2 - rect.top;
            } else if (rect.top > 0) {
                //说明图片的高度大于屏幕的高度并且上面留有空白需要上移
                deltaY = -rect.top;
            } else if (rect.bottom < screenHeight) {
                //说明图片的高度大于屏幕的高度并且下面留有空白需要下移
                deltaY = picture.getHeight() - rect.bottom;
            }
        }

        if (isHorizontal) {
            int screenWidth = displayMetrics.widthPixels;
            if (width < screenWidth) {
                //如果图片的宽度小于屏幕的宽度，那么就要右移动图片，因为上面的rect设置的图片默认显示在左上角
                deltaX = (screenWidth - width) / 2 - rect.left;
            } else if (rect.left > 0) {
                //说明图片的宽度大于屏幕的宽度并且左边留有空白需要右移
                deltaX = -rect.left;
            } else if (rect.right < screenWidth) {
                //说明图片的宽度大于屏幕的宽度并且右边留有空白需要左移
                deltaX = screenWidth - rect.right;
            }
        }
        matrix.postTranslate(deltaX, deltaY);
    }

    /**
     * 两点的距离
     */
    float distanceX;
    float distanceY;
    private float space2Point(MotionEvent event) {
        try {
            distanceX = event.getX(0) - event.getX(1);
            distanceY = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(distanceX * distanceX + distanceY * distanceY);
        }catch (Exception e){
            e.printStackTrace();
            System.out.println("space2Point"+e.getMessage());
        }
        return (float) Math.sqrt(distanceX * distanceX+ distanceY * distanceY);
    }
    /**
     * 两点的中点
     */
    private void midPoint(PointF point, MotionEvent event) {
        float midX = event.getX(0) + event.getX(1);
        float midY = event.getY(0) + event.getY(1);
        point.set(midX / 2, midY / 2);
    }



    public static boolean saveToSystemGallery(Context context, Bitmap bmp,String fileName ) {
        // 首先保存图片
        File appDir = new File(Environment.getExternalStorageDirectory(), "girls");
        if (!appDir.exists()) {
            appDir.mkdir();
        }
        File file = new File(appDir, fileName+".png");
        if(file.exists()){
            System.out.println(file.exists());
            file.delete();
        }
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        // 其次把文件插入到系统图库
        try {
            MediaStore.Images.Media.insertImage(context.getContentResolver(),
                    file.getAbsolutePath(), fileName, null);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        // 最后通知图库更新
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse(file.getAbsolutePath())));
        return true;
    }

}
