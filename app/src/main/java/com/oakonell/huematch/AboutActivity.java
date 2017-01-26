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
import com.danielstone.materialaboutlibrary.model.MaterialAboutTitleItem;

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
        // TODO get good icons for these
        MaterialAboutCard.Builder appCardBuilder = new MaterialAboutCard.Builder();

        // Add items to card

        appCardBuilder.addItem(new MaterialAboutTitleItem.Builder()
                .text(R.string.app_name)
                .icon(R.mipmap.ic_launcher)
                .build());

        try {
            appCardBuilder.addItem(ConvenienceBuilder.createVersionActionItem(context, context.getDrawable(android.R.drawable.ic_dialog_info),
//                    new IconicsDrawable(c)
//                            .icon(GoogleMaterial.Icon.gmd_info_outline)
//                            .color(ContextCompat.getColor(c, R.color.colorIcon))
//                            .sizeDp(18),
                    "Version",
                    false));

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error adding version card");
        }

//        appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
//                .text("Changelog")
//                .icon(new IconicsDrawable(c)
//                        .icon(CommunityMaterial.Icon.cmd_history)
//                        .color(ContextCompat.getColor(c, R.color.colorIcon))
//                        .sizeDp(18))
//                .setOnClickListener(ConvenienceBuilder.createWebViewDialogOnClickAction(c, "Releases", "https://github.com/daniel-stoneuk/material-about-library/releases", true, false))
//                .build());

//        appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
//                .text("Licenses")
//                .icon(new IconicsDrawable(c)
//                        .icon(GoogleMaterial.Icon.gmd_book)
//                        .color(ContextCompat.getColor(c, R.color.colorIcon))
//                        .sizeDp(18))
//                .setOnClickListener(new MaterialAboutActionItem.OnClickListener() {
//                    @Override
//                    public void onClick() {
//                        Toast.makeText(
//                                c, "Material Design About Library with Mike Penz Android Iconics", Toast.LENGTH_SHORT).show();
//                    }
//                })
//                .build());

        MaterialAboutCard.Builder authorCardBuilder = new MaterialAboutCard.Builder();
        authorCardBuilder.title(R.string.author);

        authorCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Oak on Ell")
                .subText("United States")
                .icon(android.R.drawable.picture_frame)
                .build());

//        authorCardBuilder.addItem(new MaterialAboutActionItem.Builder()
//                .text("Fork on GitHub")
//                .icon(new IconicsDrawable(c)
//                        .icon(CommunityMaterial.Icon.cmd_github_circle)
//                        .color(ContextCompat.getColor(c, R.color.colorIcon))
//                        .sizeDp(18))
//                .setOnClickListener(ConvenienceBuilder.createWebsiteOnClickAction(c, Uri.parse("https://github.com/daniel-stoneuk")))
//                .build());

        MaterialAboutCard.Builder convenienceCardBuilder = new MaterialAboutCard.Builder();

        convenienceCardBuilder.title("Convenience Builder");
//        try {
//            convenienceCardBuilder.addItem(ConvenienceBuilder.createVersionActionItem(c,
//                    new IconicsDrawable(c)
//                            .icon(CommunityMaterial.Icon.cmd_information_outline)
//                            .color(ContextCompat.getColor(c, R.color.colorIcon))
//                            .sizeDp(18),
//                    "Version",
//                    false));
//        } catch (PackageManager.NameNotFoundException e) {
//            e.printStackTrace();
//        }

        convenienceCardBuilder.addItem(ConvenienceBuilder.createWebsiteActionItem(context,
                context.getDrawable(android.R.drawable.ic_menu_view),
                context.getString(R.string.about_visit_web),
                true,
                Uri.parse("http://oakonell.com")));

        convenienceCardBuilder.addItem(ConvenienceBuilder.createRateActionItem(context,
                context.getDrawable(android.R.drawable.star_on),
                context.getString(R.string.about_rate_app),
                null
        ));

//        convenienceCardBuilder.addItem(ConvenienceBuilder.createEmailItem(c,
//                new IconicsDrawable(c)
//                        .icon(CommunityMaterial.Icon.cmd_email)
//                        .color(ContextCompat.getColor(c, R.color.colorIcon))
//                        .sizeDp(18),
//                "Send an email",
//                true,
//                "apps@daniel-stone.uk",
//                "Question concerning MaterialAboutLibrary"));


        MaterialAboutCard.Builder otherCardBuilder = new MaterialAboutCard.Builder();
        otherCardBuilder.title("Other");

//        otherCardBuilder.addItem(new MaterialAboutActionItem.Builder()
//                .icon(new IconicsDrawable(c)
//                        .icon(CommunityMaterial.Icon.cmd_language_html5)
//                        .color(ContextCompat.getColor(c, R.color.colorIcon))
//                        .sizeDp(18))
//                .text("HTML Formatted Sub Text")
//                .subTextHtml("This is <b>HTML</b> formatted <i>text</i> <br /> This is very cool because it allows lines to get very long which can lead to all kinds of possibilities. <br /> And line breaks.")
//                .setIconGravity(MaterialAboutActionItem.GRAVITY_TOP)
//                .build()
//        );

        return new MaterialAboutList(appCardBuilder.build(), authorCardBuilder.build(), convenienceCardBuilder.build(), otherCardBuilder.build());
    }


}
