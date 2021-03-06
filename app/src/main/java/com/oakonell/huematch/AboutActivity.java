package com.oakonell.huematch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.danielstone.materialaboutlibrary.ConvenienceBuilder;
import com.danielstone.materialaboutlibrary.MaterialAboutActivity;
import com.danielstone.materialaboutlibrary.model.MaterialAboutActionItem;
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard;
import com.danielstone.materialaboutlibrary.model.MaterialAboutList;
import com.oakonell.huematch.iab.IabResult;
import com.oakonell.huematch.utils.LicenseUtils;

import java.util.concurrent.CountDownLatch;

import hugo.weaving.DebugLog;

/**
 * Created by Rob on 1/26/2017.
 */

public class AboutActivity
        extends MaterialAboutActivity {
    private static final String TAG = "AboutActivity";
    private final LicenseUtils licenseUtils = new LicenseUtils();
    private final CountDownLatch licenseServiceLatch = new CountDownLatch(1);
    private HueSharedPreferences prefs;
    private int clickCount;

    @Override
    protected CharSequence getActivityTitle() {
        return getString(R.string.mal_title_about);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        prefs = HueSharedPreferences.getInstance(this);
        super.onCreate(savedInstanceState);
        licenseUtils.onCreateBind(this, new LicenseUtils.LicenseStartupListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "license connection success");
                licenseServiceLatch.countDown();
            }

            @Override
            public void onFailure() {
                Log.i(TAG, "license connection failure");
                licenseServiceLatch.countDown();
            }
        });
    }

    @Override
    @DebugLog
    protected void onDestroy() {
        licenseUtils.onDestroyRelease(this);
        super.onDestroy();
    }

    @Override
    protected MaterialAboutList getMaterialAboutList(Context context) {
        // This is called from under an ASyncTask, so can block, awaiting the license service response
        MaterialAboutCard.Builder appCardBuilder = new MaterialAboutCard.Builder();

        appCardBuilder.addItem(ConvenienceBuilder.createAppTitleItem(context));
        try {
            int text = R.string.about_version;
            if (prefs.getDebuggable()) {
                text = R.string.about_version_debug;
            }

            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = pInfo.versionName;
            int versionCode = pInfo.versionCode;
            boolean includeVersionCode = false;
            MaterialAboutActionItem item = (new MaterialAboutActionItem.Builder())
                    .text(text)
                    .subText(versionName + (includeVersionCode ? " (" + versionCode + ")" : ""))
                    .icon(context.getDrawable(R.drawable.ic_information_black_24dp))
                    .setOnClickListener(new MaterialAboutActionItem.OnClickListener() {
                        @Override
                        public void onClick() {
                            clickCount++;
                            if (clickCount > 6) {
                                prefs.setDebuggable(!prefs.getDebuggable());
                            } else {
                                return;
                            }
                            Runnable continuation = new Runnable() {
                                @Override
                                public void run() {
                                    reloadActivity();
                                }
                            };
                            if (prefs.getDebuggable()) {
                                showMessage(AboutActivity.this, "Debug", "Debug features turned on.", continuation);
                            } else {
                                prefs.setViewFPS(false);
                                showMessage(AboutActivity.this, "Debug", "Debug features turned off.", continuation);
                            }
                        }
                    }).
                            build();

            appCardBuilder.addItem(item);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error adding version card");
        }

        appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text(R.string.changelog)
                .icon(R.drawable.ic_book_open_black_24dp)
                .setOnClickListener(ConvenienceBuilder.createWebViewDialogOnClickAction(context, getString(R.string.changelog), "file:///android_asset/changelog.txt", true, true))
                .build());
        appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text(R.string.privacy_policy)
                .icon(R.drawable.ic_lock_black_24dp)
                .setOnClickListener(ConvenienceBuilder.createWebViewDialogOnClickAction(context, getString(R.string.privacy_policy), "file:///android_asset/privacy_policy.html", true, true))
                .build());

        try {
            // make sure the license service is available, via the licenseServiceLatch
            //   we are in an ASyncTask thread, so can wait and execute synchronous license checks.
            licenseServiceLatch.await();

            if (!licenseUtils.isPurchased(this)) {
                int trialDaysRemaining = (int) licenseUtils.getTrialDaysRemaining(this);
                MaterialAboutActionItem purchase = new MaterialAboutActionItem.Builder()
                        .text(R.string.purchase_full_app)
                        .subText(getResources().getQuantityString(R.plurals.trial_days_remaining, trialDaysRemaining, trialDaysRemaining))
                        .icon(R.drawable.ic_cash_usd_black_24dp)
                        .setOnClickListener(new MaterialAboutActionItem.OnClickListener() {
                            @Override
                            public void onClick() {
                                licenseUtils.launchPurchaseFlow(AboutActivity.this, new LicenseUtils.PurchaseListener() {
                                    @Override
                                    public void onPurchase() {
                                        // redisplay the about activity to change this action item
                                        reloadActivity();
                                    }

                                    @Override
                                    public void onNotPurchased() {
                                        // do nothing
                                    }
                                });
                            }
                        })
                        .build();

                appCardBuilder.addItem(purchase);
            } else {
                appCardBuilder.addItem(new MaterialAboutActionItem.Builder().text(R.string.full_app_about_item)
                        .icon(R.drawable.ic_checkbox_marked_circle_outline_black_24dp)
                        .build());
            }
        } catch (InterruptedException e) {
            appCardBuilder.addItem(new MaterialAboutActionItem.Builder().text(R.string.unknown_app_license_status_about_item)
//                        .icon(R.drawable.ic_lock_black_24dp)
                    .build());
            Log.e(TAG, "License Countdown licenseServiceLatch interrupted", e);
        }

        addConsumeItem(appCardBuilder);


        MaterialAboutCard.Builder authorCardBuilder = new MaterialAboutCard.Builder();
        authorCardBuilder.title(R.string.author);

        authorCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Oak on Ell")
                .icon(R.drawable.ic_account_black_24dp)
                .build());

        authorCardBuilder.addItem(ConvenienceBuilder.createWebsiteActionItem(context,
                context.getDrawable(R.drawable.ic_web_black_24dp),
                context.getString(R.string.about_visit_web),
                true,
                Uri.parse("http://oakonell.com")));

        authorCardBuilder.addItem(ConvenienceBuilder.createRateActionItem(context,
                context.getDrawable(R.drawable.ic_star_black_24dp),
                context.getString(R.string.about_rate_app),
                null
        ));


        MaterialAboutCard.Builder convenienceCardBuilder = new MaterialAboutCard.Builder();

        convenienceCardBuilder.title(R.string.about_libraries);
        convenienceCardBuilder.addItem(ConvenienceBuilder.createWebsiteActionItem(context, getDrawable(R.drawable.ic_information_black_24dp),
                "MaterialAboutLibrary", false, Uri.parse("https://github.com/daniel-stoneuk/material-about-library")));

        convenienceCardBuilder.addItem(ConvenienceBuilder.createWebsiteActionItem(context, getDrawable(R.drawable.fabric),
                "Fabric.io - Crashlytics", false, Uri.parse("https://fabric.io/home")));

        convenienceCardBuilder.addItem(ConvenienceBuilder.createWebsiteActionItem(context, getDrawable(R.drawable.hue),
                "Philip Hue SDK", false, Uri.parse("https://developers.meethue.com/")));


        return new MaterialAboutList(appCardBuilder.build(), authorCardBuilder.build(), convenienceCardBuilder.build());
    }

    private void reloadActivity() {
        finish();

        Intent intent = new Intent(AboutActivity.this, AboutActivity.class);
        AboutActivity.this.startActivity(intent);
    }

    private void addConsumeItem(MaterialAboutCard.Builder appCardBuilder) {
        if (!HueMatcherActivity.DEBUG && !prefs.getDebuggable()) return;

        MaterialAboutActionItem consume = new MaterialAboutActionItem.Builder()
                .text("Debug/Test - consume full-app purchase")
                .setOnClickListener(new MaterialAboutActionItem.OnClickListener() {
                    @Override
                    public void onClick() {
                        licenseUtils.consume(AboutActivity.this, new LicenseUtils.ConsumeListener() {
                            @Override
                            public void onSuccess() {
                                licenseUtils.showMessage(AboutActivity.this, "Consumed", "Purchase was consumed", new Runnable() {
                                    @Override
                                    public void run() {
                                        reloadActivity();
                                    }
                                });
                            }

                            @Override
                            public void onError(IabResult result) {
                                licenseUtils.showMessage(AboutActivity.this, "Consumption Error", "Purchase was not consumed: " + result.toString(), null);
                            }
                        });
                    }
                })
                .build();

        appCardBuilder.addItem(consume);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        // Pass on the activity result to the helper for handling
        if (licenseUtils.handleActivityResult(this, requestCode, resultCode, data)) {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
            return;
        }

        // not handled, so handle it ourselves (here's where you'd
        // perform any handling of activity results not related to in-app
        // billing...
        Log.d(TAG, "onActivityResult not handled by IABUtil.");
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showMessage(Activity activity, String title, String text, final Runnable onDone) {
        // 1. Instantiate an AlertDialog.Builder with its constructor
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

// 2. Chain together various setter methods to set the dialog characteristics
        builder.setMessage(text)
                .setTitle(title)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                        if (onDone != null) {
                            onDone.run();
                        }
                    }
                })
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (onDone != null) {
                            onDone.run();
                        }
                    }
                })
        ;

// 3. Get the AlertDialog from create()
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
