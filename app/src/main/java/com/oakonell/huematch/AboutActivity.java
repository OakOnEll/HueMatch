package com.oakonell.huematch;

import android.content.Context;
import android.content.Intent;
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

    @Override
    protected CharSequence getActivityTitle() {
        return getString(R.string.mal_title_about);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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
            appCardBuilder.addItem(ConvenienceBuilder.createVersionActionItem(context, context.getDrawable(R.drawable.ic_information_black_24dp),
                    getString(R.string.about_version),
                    false));
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
                                        // TODO test this
                                        Intent intent = new Intent(AboutActivity.this, AboutActivity.class);
                                        AboutActivity.this.startActivity(intent);
                                        finish();
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

    private void addConsumeItem(MaterialAboutCard.Builder appCardBuilder) {
        if (!HueMatcherActivity.DEBUG) return;

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
                                        Intent intent = new Intent(AboutActivity.this, AboutActivity.class);
                                        AboutActivity.this.startActivity(intent);
                                        finish();
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

}
