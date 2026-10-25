package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** Five sample title destinations. Never substitutes a generic search for a title. */
final class FavouriteTitles {
    static final String NETFLIX = "com.netflix.ninja", PRIME = "com.amazon.amazonvideo.livingroom";
    static final String[] KEYS = {"sheldon", "walter", "spider", "jane", "jd"};
    static final String[] NAMES = {"Sheldon", "Walter White", "Spider-Man", "Patrick Jane", "JD"};
    static final String[] TITLES = {"The Big Bang Theory", "Breaking Bad", "Spider-Man: No Way Home", "The Mentalist", "Master"};
    static final String[] PACKAGES = {NETFLIX, NETFLIX, NETFLIX, PRIME, PRIME};
    static final String[] URLS = {
        "https://www.netflix.com/watch/70143830", "https://www.netflix.com/watch/70143836",
        "https://www.netflix.com/watch/81465707",
        "https://app.primevideo.com/detail?gti=amzn1.dv.gti.df50e4a1-2a2f-4886-953e-e9793658df1d",
        "https://app.primevideo.com/detail?gti=amzn1.dv.gti.2cbb8fd7-09ac-ec8f-9bb3-5939da8108cd"
    };
    static final int[] COLORS = {0xFF56B686, 0xFFD8B66A, 0xFFE44B4F, 0xFF679FC9, 0xFFE7A66B};
    // Three back-row figures and two front-row figures. Vertical movement chooses the nearest x.
    static int move(int from, int horizontal, int vertical) {
        if (horizontal != 0) return Math.max(from < 3 ? 0 : 3, Math.min(from < 3 ? 2 : 4, from + horizontal));
        if (vertical > 0 && from < 3) return from == 2 ? 4 : 3;
        if (vertical < 0 && from >= 3) return from == 3 ? 0 : 2;
        return from;
    }
    static void open(Activity activity, int index) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(URLS[index])).setPackage(PACKAGES[index]);
            if (NETFLIX.equals(PACKAGES[index])) {
                // This exact TV route was verified by the owner; plain /title links opened only Home.
                intent.setClassName(NETFLIX, "com.netflix.ninja.MainActivity");
                intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                intent.putExtra("source", "30");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            }
            activity.startActivity(intent);
        }
        catch (ActivityNotFoundException unavailable) {
            Toast.makeText(activity, "The app cannot open this title link.", Toast.LENGTH_LONG).show();
        }
    }
}
