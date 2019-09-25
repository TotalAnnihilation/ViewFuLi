package com.example.viewfuli;

import android.os.Looper;

public class WebRequest implements Runnable{
    private Looper mLooper = null;


    @Override
    public void run() {
        System.out.println("Looper Star");
        Looper.prepare();
        synchronized (this) {
            mLooper = Looper.myLooper();
            notifyAll();
        }
        Looper.loop();
        System.out.println("Looper End");
    }
    public Looper getLooper() {
        synchronized (this) {
            while (mLooper == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
        }
        return mLooper;
    }
}
