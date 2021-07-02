package com.github.bitteryapp;

import android.graphics.Bitmap;

public class BitteryHit {
    private String mPrivKey;
    private String mBTCAddr;
    private String mRichAddr;
    private int mBalance;
    private int mMatch;
    private BitteryCore mBitteryCore;

    public BitteryHit(String privKey, String btcAddr, String richAddr, int balance, int match, BitteryCore core) {
        mPrivKey = privKey;
        mBTCAddr = btcAddr;
        mBalance = balance;
        mRichAddr = richAddr;
        mMatch = match;
        mBitteryCore = core;
    }

    public String getPrivKey() { return mPrivKey; }
    public String getBTCAddr() { return mBTCAddr; }
    public String getRichAddr() { return mRichAddr; }
    public int getBalance() { return mBalance; }
    public int getMatchNum () { return mMatch; }
    public int getScore() {
        return (mMatch * 100)/mRichAddr.length();
    }

    public Bitmap getKeyQR() {
        return mBitteryCore.qrBitmap(mPrivKey);
    }
    public Bitmap getScoreBitmap() {
        return mBitteryCore.scoreBitmap((mMatch * 100)/mRichAddr.length());
    }
}
