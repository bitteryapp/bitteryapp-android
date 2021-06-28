package com.github.bitteryapp;

import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;
import android.content.Context;
import android.graphics.Bitmap;
import java.util.Properties;
import java.util.Locale;
import java.util.ArrayList;
import android.os.Handler;
import android.os.Message;
import android.os.IBinder;
import android.content.SharedPreferences.Editor;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.ThreadUtils;
import org.chromium.base.BuildInfo;

public class BitteryCore {
    private static Handler mHandler;

    static {
        System.loadLibrary("bitterycore");
    }

    public BitteryCore(Handler handler) {
        if(mHandler == null) {
            initBitteryCore();
        }
        mHandler = handler;
    }

    public void doLuckyShake(int sec) {
        luckyShake(sec);
    }

    public String luckyAddr(int idx) {
        return getLuckyAddr(idx);
    }

    public String luckyPriv(int idx) {
        return getLuckyPriv(idx);
    }

    public Bitmap qrBitmap(String str) {
        Bitmap qr = getQRBitmap(str);
        return qr;
    }

    public int match() {
        return getMatchNum();
    }

    public void select(String sel) {
        setSelection(sel);
    }

    public String richAddr(int idx) {
        return getRichAddr(idx);
    }

    public int richBalance(int idx) {
        return getRichBalance(idx);
    }

    @CalledByNative
    private static void pushMessage(int what, int arg1, int arg2, String msg) {
        Message message = Message.obtain();
        message.what = what;
        message.arg1 = arg1;
        message.arg2 = arg2;
        message.obj  = msg;
        mHandler.sendMessage(message);
    }

    private static native void luckyShake(int sec);
    private static native void initBitteryCore();
    private static native void publishMessage(String message);
    private static native Bitmap getQRBitmap(String str);
    private static native String getLuckyAddr(int idx);
    private static native String getLuckyPriv(int idx);
    private static native String getRichAddr(int idx);
    private static native int getRichBalance(int idx);
    private static native int getMatchNum();
    private static native void setSelection(String sel);
}
