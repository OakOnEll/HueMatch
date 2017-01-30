package com.oakonell.huematch;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import com.danielstone.materialaboutlibrary.ConvenienceBuilder;
import com.danielstone.materialaboutlibrary.MaterialAboutActivity;
import com.danielstone.materialaboutlibrary.model.MaterialAboutActionItem;
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard;
import com.danielstone.materialaboutlibrary.model.MaterialAboutList;

/**
 * Created by Rob on 1/26/2017.
 */

public class AboutActivity
        extends MaterialAboutActivity {
    private static final String TAG = "AboutActivity";

    @Override
    protected CharSequence getActivityTitle() {
        return getString(R.string.mal_title_about);
    }

    @Override
    protected MaterialAboutList getMaterialAboutList(Context context) {
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


}
