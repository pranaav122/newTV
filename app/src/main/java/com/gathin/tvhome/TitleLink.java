package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

final class TitleLink {
    static void open(Activity activity, String app, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(app)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (FavouriteTitles.NETFLIX.equals(app)) {
                intent.setClassName(app, "com.netflix.ninja.MainActivity");
                intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                intent.putExtra("source", "30");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            }
            activity.startActivity(intent);
        } catch (ActivityNotFoundException unavailable) {
            Toast.makeText(activity, "The app cannot open this title link.", Toast.LENGTH_LONG).show();
        }
    }
}
