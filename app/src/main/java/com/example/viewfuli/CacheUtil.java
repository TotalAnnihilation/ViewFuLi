package com.example.viewfuli;

import android.content.Context;

import java.io.File;

public class CacheUtil {
    public static File getDiskCacheDir(Context context, String uniqueName) {
        final String cachePath = context.getCacheDir().getPath();
        return new File(cachePath + File.separator + uniqueName);
    }
}
