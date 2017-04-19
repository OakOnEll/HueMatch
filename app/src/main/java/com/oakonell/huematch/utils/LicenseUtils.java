package com.oakonell.huematch.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.oakonell.huematch.BuildConfig;
import com.oakonell.huematch.R;
import com.oakonell.huematch.iab.IabException;
import com.oakonell.huematch.iab.IabHelper;
import com.oakonell.huematch.iab.IabResult;
import com.oakonell.huematch.iab.Inventory;
import com.oakonell.huematch.iab.Purchase;

import java.util.concurrent.TimeUnit;

/**
 * Created by Rob on 1/31/2017.
 */

public class LicenseUtils {
    private static final String TAG = "LicenseUtils";
    private static final String FULL_APP_SKU = "full_app";

    private static final int PURCHASE_REQUEST_CODE = 100;
    private static final int TRIAL_DAYS = 30;

    private IabHelper helper;
    private Runnable onConnectContinuation;

    enum ConnectState {
        CONNECTED, CONNECTING, ERROR
    }

    private boolean skipResumeCheck = false;

    private ConnectState connectState = ConnectState.CONNECTING;

    public interface LicenseStartupListener {
        void onSuccess();

        void onFailure();
    }

    public void onCreateBind(final Activity activity, final LicenseStartupListener listener) {
        String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAp2d6w5KW" +
                "4LOJBhP0CvNP42oHubOw85PFZOcUlViEP5QVS0z1G1uwPm4CT6gvlmjcNip0KeIqU780xidbilvPxAUIp+vi4oIb6VFq" +
                "/4LNcM1x0/yDxDhb7rFmhsIcpC3DCOij07CbBELDAPGaAcjhfh5p1HjkDp6YThB47+XlXRj+igK164nJxhpgnlv1ceqUE2TqqgrTL8wcti5D" +
                "TlXwrZVaE/UTLqsrcElcoXcZBrm4amR3Fiijo48TZGT1gK+W0ftgC8g6M4otX7wLwMhcuroforAhQHGgjBgSUTWJ5qiIZ6v1Z7XwhpDawn9Ec3" +
                "G3NL/j511yeDRQTeN47HOIIwIDAQAB";

        // compute your public key and store it in base64EncodedPublicKey
        helper = new IabHelper(activity, base64EncodedPublicKey);

        helper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh no, there was a problem.
                    Log.e(TAG, "Problem setting up In-app Billing: " + result);
                    if (listener != null) {
                        listener.onFailure();
                        connectState = ConnectState.ERROR;
                    }
                    return;
                }
                connectState = ConnectState.CONNECTED;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (listener != null) {
                    listener.onSuccess();
                }
                if (onConnectContinuation != null) {
                    onConnectContinuation.run();
                    onConnectContinuation = null;
                }
            }
        });
    }

    public void onDestroyRelease(Activity activity) {
        if (helper != null) {
            try {
                helper.dispose();
            } catch (IabHelper.IabAsyncInProgressException e) {
                Log.e(TAG, "Error disposing in app billing helper", e);
            }
        }
        helper = null;
    }


    public boolean isPurchased(Activity activity) {
        Inventory inventory = null;
        try {
            inventory = helper.queryInventory();
        } catch (IabException e) {
            showMessage(activity, activity.getString(R.string.title_error), activity.getString(R.string.error_checking_license), null);
            Log.e(TAG, "Exception checking license state", e);
            return false;
        }
        return isPurchased(activity, inventory);
    }

    interface PurchasedQueryResultListener {
        void onPurchased();

        void onNotPurchased();

        void onError(IabResult result);
    }

    public void isPurchasedAsync(final Activity activity, final PurchasedQueryResultListener listener) {
        if (connectState == ConnectState.ERROR) {
            listener.onError(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE, "Billing didn't connect"));
        }
        if (connectState == ConnectState.CONNECTING) {
            final Runnable currentContinuation = onConnectContinuation;
            onConnectContinuation = new Runnable() {
                @Override
                public void run() {
                    if (currentContinuation != null) {
                        currentContinuation.run();
                    }
                    isPurchasedAsync(activity, listener);
                }
            };
            return;
        }
        try {
            helper.queryInventoryAsync(new IabHelper.QueryInventoryFinishedListener() {
                @Override
                public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                    if (!result.isSuccess()) {
                        // Oh no, there was a problem.
                        Log.e(TAG, "Problem setting up In-app Billing: " + result);
                        listener.onError(result);
                        return;
                    }
                    if (isPurchased(activity, inv)) {
                        listener.onPurchased();
                    } else {
                        listener.onNotPurchased();
                    }
                }
            });
        } catch (IabHelper.IabAsyncInProgressException e) {
            showMessage(activity, activity.getString(R.string.title_error), activity.getString(R.string.error_checking_license_async), null);
            Log.e(TAG, "Exception checking license state async", e);
        }
    }

    private boolean isPurchased(Activity activity, Inventory inventory) {
        if (BuildConfig.DEBUG) return true;
        final Purchase purchase = inventory.getPurchase(FULL_APP_SKU);
        return purchase != null;
    }

    public void onResumeCheckLicense(final Activity activity) {
        if (skipResumeCheck) {
            Log.i(TAG, "Skipping onResume check.");
            return;
        }
        isPurchasedAsync(activity, new PurchasedQueryResultListener() {
            @Override
            public void onPurchased() {
                Log.i(TAG, "Full app is purchased!");
            }

            @Override
            public void onError(IabResult result) {
                Log.e(TAG, "Error verifying app license: " + result.toString());
                showMessage(activity, activity.getString(R.string.title_error), activity.getString(R.string.error_verifying_license), null);
            }

            @Override
            public void onNotPurchased() {
                Log.i(TAG, "Full App is not purchased.");

                long trialDaysRemaining = getTrialDaysRemaining(activity);
                if (trialDaysRemaining > 0) {
                    Log.i(TAG, "In trial period.");
                    return;
                }

                Log.i(TAG, "Launching purchase flow.");
                // trial is over
                showMessage(activity, activity.getString(R.string.title_trial_expired),
                        activity.getResources().getQuantityString(R.plurals.trial_over, TRIAL_DAYS, TRIAL_DAYS), new Runnable() {
                            @Override
                            public void run() {
                                launchPurchaseFlow(activity, new PurchaseListener() {
                                    @Override
                                    public void onPurchase() {
                                        // thank you dialog
                                        showMessage(activity, activity.getString(R.string.title_thanks), activity.getString(R.string.thanks_for_purchasing), null);
                                    }

                                    @Override
                                    public void onNotPurchased() {
                                        skipResumeCheck = true;
                                        showMessage(activity, activity.getString(R.string.title_trial_expired), activity.getString(R.string.trial_expired_not_purchased_app_close), new Runnable() {
                                            @Override
                                            public void run() {
                                                skipResumeCheck = false;
                                                activity.finish();
                                            }
                                        });
                                    }
                                });
                            }
                        });
            }


        });
    }

    public long getTrialDaysRemaining(Activity activity) {
        PackageInfo packageInfo = null;
        try {
            packageInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting first install time", e);
            return TRIAL_DAYS;
        }
        long installTime = packageInfo.firstInstallTime;
        long now = System.currentTimeMillis();
        long installedMs = now - installTime;
        long installedDays = TimeUnit.MILLISECONDS.toDays(installedMs);
        return TRIAL_DAYS - installedDays;
    }


    public void showMessage(Activity activity, String title, String text, final Runnable onDone) {
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

    public boolean handleActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        return helper.handleActivityResult(requestCode, resultCode, data);
    }

    public interface ConsumeListener {
        void onSuccess();

        void onError(IabResult result);
    }

    public void consume(final Activity activity, final ConsumeListener listener) {
        try {
            helper.queryInventoryAsync(new IabHelper.QueryInventoryFinishedListener() {
                @Override
                public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                    if (!result.isSuccess()) {
                        // Oh no, there was a problem.
                        Log.e(TAG, "Problem consuming purchase: " + result);
                        showMessage(activity, activity.getString(R.string.title_error), activity.getString(R.string.error_consuming_with_reason) + result.toString(), null);
                        return;
                    }
                    final Purchase purchase = inv.getPurchase(FULL_APP_SKU);
                    if (purchase == null) {
                        listener.onError(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED, "Item not owned, to consume!"));
                        return;
                    }
                    try {
                        helper.consumeAsync(purchase, new IabHelper.OnConsumeFinishedListener() {
                            @Override
                            public void onConsumeFinished(Purchase purchase, IabResult result) {
                                if (!result.isSuccess()) {
                                    // Oh no, there was a problem.
                                    Log.e(TAG, "Problem consuming purchase: " + result);
                                    listener.onError(result);
                                    return;
                                }
                                listener.onSuccess();
                            }
                        });
                    } catch (IabHelper.IabAsyncInProgressException e) {
                        showMessage(activity, activity.getString(R.string.title_error), activity.getString(R.string.error_consuming_with_reason) + e.toString(), null);
                        Log.e(TAG, "Exception consuming purchase", e);
                    }
                }
            });
        } catch (IabHelper.IabAsyncInProgressException e) {
            showMessage(activity, activity.getString(R.string.title_error), activity.getString(R.string.error_checking_inventory_for_consumption) + e.toString(), null);
            Log.e(TAG, "Exception checking inventory for consuming purchase", e);
        }
    }

    public interface PurchaseListener {
        void onPurchase();

        void onNotPurchased();
    }

    public void launchPurchaseFlow(final Activity activity, final PurchaseListener listener) {
        try {
            helper.launchPurchaseFlow(activity, FULL_APP_SKU, PURCHASE_REQUEST_CODE, new IabHelper.OnIabPurchaseFinishedListener() {
                @Override
                public void onIabPurchaseFinished(IabResult result, Purchase info) {
                    if (!result.isSuccess()) {
                        // Oh no, there was a problem.
                        listener.onNotPurchased();
                        Log.e(TAG, "Problem launching purchase flow: " + result);
                        return;
                    }
                    listener.onPurchase();
                }
            });
        } catch (IabHelper.IabAsyncInProgressException e) {
            Log.e(TAG, "Exception getting buy intent", e);
            showMessage(activity, activity.getString(R.string.title_error), activity.getString(R.string.error_launching_purchase) + e.toString(), null);
        }
    }

}
