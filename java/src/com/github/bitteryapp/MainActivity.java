package com.github.bitteryapp;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Button;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import android.app.AlertDialog;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.AdSize;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.util.DisplayMetrics;
import android.util.Log;
import java.util.ArrayList;
import java.util.Map;
import android.view.Display;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Vibrator;

import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;
import org.chromium.base.ContextUtils;


public class MainActivity extends AppCompatActivity implements SensorEventListener {
    //private static final String AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111";
    private static final String AD_UNIT_ID = "ca-app-pub-8407308256565742/8471101289";
    private AdView mAdView;
    private FrameLayout adContainerView;
    private FirebaseAnalytics mFirebaseAnalytics;
    private ConsentInformation consentInformation;
    private ConsentForm consentForm;
    public final int MSG_BTC_ADDR = 0x01;
    private BitteryCore mBitteryCore;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Vibrator mVibrator;
    private boolean mLuckyStatus;
    private ArrayList<BitteryHit> mHits = new ArrayList<BitteryHit>();
    private BitteryHitAdapter mAdapter;
    private RecyclerView mBTCHits ;
    private ProgressBar mProgressBar;
    private boolean[] mSelection = new boolean[500];
    private boolean   mSelectionUpdate;
    private String[] mRichlist = new String[500];
    private float mAX;
    private float mAY;
    private float mAZ;

    public static final int EVENT_SHOW_RICHDLG = 0x0000;
    public static final int EVENT_LUCKY_START = 0x00001;
    public static final int EVENT_LUCKY_END   = 0x00002;
    public static final int EVENT_LUCKY_PROGRESS = 0x00003;
    public static final int EVENT_DATA_CHANGED = 0x00004;

    private void loadBanner() {
        // Create an ad request.
        mAdView = new AdView(this);
        mAdView.setAdUnitId(AD_UNIT_ID);
        adContainerView.addView(mAdView);

        AdSize adSize = getAdSize();
        mAdView.setAdSize(adSize);

        AdRequest adRequest = new AdRequest.Builder().build();

        mAdView.loadAd(adRequest);
    }

    private AdSize getAdSize() {
        // Determine the screen width (less decorations) to use for the ad width.
        Display display = getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        float density = outMetrics.density;

        float adWidthPixels = adContainerView.getWidth();

        // If the ad hasn't been laid out, default to the full screen width.
        if (adWidthPixels == 0) {
            adWidthPixels = outMetrics.widthPixels;
        }

        int adWidth = (int) (adWidthPixels / density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth);
    }

    private void showRichListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.rich_list_dialog_title).setIcon(R.drawable.bitcoin_hit_icon);
        builder.setMultiChoiceItems(mRichlist, mSelection, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                mSelection[which] = isChecked;
                mSelectionUpdate = true;
            }
        });
        builder.setNeutralButton(R.string.rich_list_dialog_selectall, null);
        builder.setPositiveButton(R.string.rich_list_dialog_done, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(mSelectionUpdate == true) {
                    String selection = "";
                    for(int i = 0; i < 500; i++) {
                        if(mSelection[i] == true) {
                            selection += '1';
                        } else {
                            selection += '0';
                        }
                    }
                    mBitteryCore.select(selection);
                    SharedPreferences.Editor editor = ContextUtils.getAppSharedPreferences().edit();
                    editor.putString("RICHLIST_SELECTION", selection);
                    editor.commit();
                }
                dialog.dismiss();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for(int i = 0; i < 500; i++) {
                    ((AlertDialog)dialog).getListView().setItemChecked(i, true);
                    mSelection[i] = true;
                }
                mSelectionUpdate = true;
            }
        });
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float[] val = e.values;

        if(mAX + mAY + mAZ != 0) {
            if(mLuckyStatus == false) {
                float delta = mAX + mAY + mAZ - val[0] - val[1] - val[2];
                if(Math.abs(delta) > 8) {
                    Log.i("BitteryApp", "onSensorChanged delta " + Float.toString(delta));
                    mLuckyStatus = true;
                    mBitteryCore.doLuckyShake(10);
                }
            }
        }
        mAX = val[0];
        mAY = val[1];
        mAZ = val[2];
    }

    public void loadConsentForm() {
        UserMessagingPlatform.loadConsentForm(this,
            new UserMessagingPlatform.OnConsentFormLoadSuccessListener() {
                @Override
                public void onConsentFormLoadSuccess(ConsentForm consentForm) {
                    MainActivity.this.consentForm = consentForm;
                    if(consentInformation.getConsentStatus() == ConsentInformation.ConsentStatus.REQUIRED) {
                        consentForm.show(MainActivity.this,
                                new ConsentForm.OnConsentFormDismissedListener() {
                                    @Override
                                    public void onConsentFormDismissed(FormError formError) {
                                        // Handle dismissal by reloading form.
                                        loadConsentForm();
                                    }
                        });
                    }
                }
            },
            new UserMessagingPlatform.OnConsentFormLoadFailureListener() {
                @Override
                public void onConsentFormLoadFailure(FormError formError) {
                    // Handle the error
                }
            }
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.setup, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.setup) {
            showRichListDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContextUtils.initApplicationContext(this);
        boolean isAdmobDisabled = false;

        mLuckyStatus = false;
        mSelectionUpdate = false;
        if(getPackageName().equals("com.github.bitteryapp.pro"))
            isAdmobDisabled = true;

        FirebaseApp.initializeApp(this);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        setContentView(R.layout.activity_main);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);

        ConsentRequestParameters params = new ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build();
        consentInformation = UserMessagingPlatform.getConsentInformation(this);
        consentInformation.requestConsentInfoUpdate(this, params,
            new ConsentInformation.OnConsentInfoUpdateSuccessListener() {
                @Override
                public void onConsentInfoUpdateSuccess() {
                    // The consent information state was updated.
                    // You are now ready to check if a form is available.
                    if (consentInformation.isConsentFormAvailable()) {
                        loadConsentForm();
                    }
                }
            },
            new ConsentInformation.OnConsentInfoUpdateFailureListener() {
                @Override
                public void onConsentInfoUpdateFailure(FormError formError) {
                    // Handle the error.
                }
            }
        );

        ActionBar actionBar = getSupportActionBar();
        ColorDrawable colorDrawable = new ColorDrawable(Color.parseColor("#0F9D58"));
        actionBar.setBackgroundDrawable(colorDrawable);

        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                switch(msg.what) {
                    case EVENT_DATA_CHANGED:
                    mAdapter.notifyDataSetChanged();
                    break;
                    case EVENT_SHOW_RICHDLG:
                    showRichListDialog();
                    break;
                    case EVENT_LUCKY_START:
                    mVibrator.vibrate(88);
                    break;
                    case EVENT_LUCKY_PROGRESS:
                    int npos = (msg.arg1 * 100) / msg.arg2;
                    mProgressBar.setProgress(npos, true);
                    break;
                    case EVENT_LUCKY_END:
                    int match = mBitteryCore.match();
                    String luckyAddr = mBitteryCore.luckyAddr(msg.arg1);
                    String luckyPriv = mBitteryCore.luckyPriv(msg.arg1);
                    String richAddr = mBitteryCore.richAddr(msg.arg2);
                    int balance = mBitteryCore.richBalance(msg.arg2);

                    int score = (match * 100)/richAddr.length();
                    if(score> 15) {
                        SharedPreferences pref = ContextUtils.getAppSharedPreferences();
                        SharedPreferences.Editor editor = pref.edit();
                        String key = "SCORE" + Integer.toString(score);
                        String val = richAddr + ";" + luckyAddr + ";" + luckyPriv + ";" + Integer.toString(balance) + ";" + Integer.toString(match);
                        editor.putString(key, val);
                        editor.commit();
                        Bundle bundle = new Bundle();
                        bundle.putString("score", Integer.toString(score));
                        mFirebaseAnalytics.logEvent("LuckyShake", bundle);
                    }
                    mVibrator.vibrate(188);
                    mProgressBar.setProgress(0, true);

                    mLuckyStatus = false;
                    mHits.add(0, new BitteryHit(luckyPriv, luckyAddr, richAddr, balance, match, mBitteryCore));
                    mAdapter.notifyItemInserted(0);
                    mBTCHits.smoothScrollToPosition(0);
                    break;
                }
            }
        };

        mProgressBar = (ProgressBar) findViewById(R.id.progressbar);
        mBTCHits = (RecyclerView) findViewById(R.id.btc_hits);
        mAdapter = new BitteryHitAdapter(mHits);
        mBTCHits.setAdapter(mAdapter);
        mBTCHits.setLayoutManager(new LinearLayoutManager(this));

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
                Bundle bundle = new Bundle();
                bundle.putString("initialize", "OK");
                mFirebaseAnalytics.logEvent("MobileAds", bundle);
            }
        });

        if(false == isAdmobDisabled) {
            adContainerView = findViewById(R.id.ad_view_container);
            AdRequest adRequest = new AdRequest.Builder().build();
            adContainerView.post(new Runnable() {
                @Override
                public void run() {
                    loadBanner();
                }
            });
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                mBitteryCore = new BitteryCore(handler);

                boolean itemChanged = false;
                Map<String, ?> allEntries = ContextUtils.getAppSharedPreferences().getAll();
                for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                    if(entry.getKey().startsWith("SCORE")) {
                        String[] info = entry.getValue().toString().split(";");
                        if(info.length == 5) {
                            mHits.add(new BitteryHit(info[2], info[1], info[0], Integer.valueOf(info[3]), Integer.valueOf(info[4]), mBitteryCore));
                            itemChanged = true;
                        }
                    }
                }

                for(int i = 0; i < 500; i++) {
                    int balance = mBitteryCore.richBalance(i);
                    mRichlist[i] = Integer.toString(balance) + "BTC, " + mBitteryCore.richAddr(i);
                    mSelection[i] = false;
                }

                String selection = ContextUtils.getAppSharedPreferences().getString("RICHLIST_SELECTION", "");
                if(selection.length() == 0) {
                    if(savedInstanceState != null) {
                        Message message = Message.obtain();
                        message.what = EVENT_SHOW_RICHDLG;
                        handler.sendMessage(message);
                    }
                } else {
                    if(itemChanged) {
                        Message message = Message.obtain();
                        message.what = EVENT_DATA_CHANGED;
                        handler.sendMessage(message);
                    }
                    for(int i = 0; i < selection.length(); i++) {
                        mSelection[i] = (selection.charAt(i) == '1');
                    }
                    mBitteryCore.select(selection);
                }
            }
        }).start();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mLuckyStatus == false) {
                    mLuckyStatus = true;
                    mBitteryCore.doLuckyShake(10);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        if (mAdView != null) {
            mAdView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (mAdView != null) {
            mAdView.resume();
        }
        super.onResume();

        if(mSensorManager != null) {
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onDestroy() {
        if (mAdView != null) {
            mAdView.destroy();
        }
        super.onDestroy();
        mSensorManager.unregisterListener(this);
    }
}
