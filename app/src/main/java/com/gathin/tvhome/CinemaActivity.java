package com.gathin.tvhome;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Random;

/** Native, offline favourites and mood picker on the owner's static wallpaper. */
public final class CinemaActivity extends Activity {
    private FrameLayout root;
    private LinearLayout content;
    private WallpaperPlayer wallpaper;
    private TextView eyebrow, title, detail, hint, primary, reroll;
    private boolean picker, result;
    private int selected, mood, chosen = -1;
    private final Random random = new Random();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        picker = "picker".equals(getIntent().getStringExtra("page"));
        if (state != null) {
            selected = state.getInt("selected"); mood = state.getInt("mood");
            chosen = state.getInt("chosen", -1); result = state.getBoolean("result");
        }
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        wallpaper = new WallpaperPlayer(this, root, Config.load(this));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(56), dp(34), dp(56), dp(28));
        content.setClipChildren(false);
        content.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xD9000000, 0x55000000}));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = button("‹  Home", this::finish);
        nav.addView(back);
        TextView section = text(picker ? "WHAT TO WATCH" : "MY FAVOURITES", 13, 0xFFD4D4D4);
        section.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        nav.addView(section, new LinearLayout.LayoutParams(0, -1, 1));
        content.addView(nav, new LinearLayout.LayoutParams(-1, dp(42)));
        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        eyebrow = text("", 14, 0xFFE0E0E0);
        eyebrow.setLetterSpacing(.14f);
        content.addView(eyebrow);
        title = text("", 48, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(dp(680), -2);
        titleParams.topMargin = dp(12);
        content.addView(title, titleParams);
        detail = text("", 18, 0xFFE0E0E0);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(dp(650), -2);
        detailParams.topMargin = dp(15);
        content.addView(detail, detailParams);
        LinearLayout actions = new LinearLayout(this);
        actions.setClipChildren(false);
        primary = button(picker ? "Choose for me" : "Find where to watch", () -> activate());
        primary.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (picker && result && key == KeyEvent.KEYCODE_DPAD_RIGHT) { reroll.requestFocus(); return true; }
            if (picker && result) return false;
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                step(key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1); return true;
            }
            return false;
        });
        actions.addView(primary);
        if (picker) {
            reroll = button("Another pick", () -> choose());
            reroll.setOnKeyListener((v, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) { primary.requestFocus(); return true; }
                return key == KeyEvent.KEYCODE_DPAD_RIGHT;
            });
            actions.addView(reroll);
        }
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(54));
        actionParams.topMargin = dp(25);
        content.addView(actions, actionParams);
        hint = text("", 12, 0xFFD0D0D0);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(12);
        content.addView(hint, hintParams);
        content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(12)));
        refresh();
        primary.requestFocus();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setShadowLayer(dp(3), 0, dp(1), 0xE6000000);
        return view;
    }
    private TextView button(String value, Runnable action) {
        TextView view = text(value, 16, Color.WHITE);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(10), dp(8), dp(28), dp(8));
        view.setFocusable(true); view.setClickable(true); view.setBackground(null);
        view.setPivotX(0f);
        view.setOnClickListener(v -> action.run());
        view.setOnFocusChangeListener((v, focused) -> {
            v.animate().cancel();
            v.animate().scaleX(focused ? 1.15f : 1f).scaleY(focused ? 1.15f : 1f)
                    .setDuration(140).setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();
        });
        return view;
    }
    private void step(int amount) {
        if (picker) { mood = Math.floorMod(mood + amount, CinemaData.MOODS.length); result = false; }
        else selected = Math.floorMod(selected + amount, CinemaData.TITLES.length);
        refresh();
    }
    private void choose() { chosen = CinemaData.pick(mood, chosen, random); result = chosen >= 0; refresh(); }
    private void activate() { if (picker && !result) choose(); else openWatchOptions(picker ? chosen : selected); }
    private void refresh() {
        primary.setText(picker && !result ? "Choose for me" : "Find where to watch");
        if (reroll != null) reroll.setVisibility(result ? View.VISIBLE : View.GONE);
        if (picker && !result) {
            eyebrow.setText("DON’T HAVE TIME TO BROWSE?");
            title.setText(CinemaData.MOODS[mood]);
            detail.setText(CinemaData.matching(mood).size() + " favourites · Choose a mood, then let tonight surprise you.");
            hint.setText("← / →  Choose mood     OK  Pick a title     Back  Home");
        } else {
            CinemaData.Title item = CinemaData.TITLES[picker ? chosen : selected];
            eyebrow.setText(picker ? "YOUR PICK · " + CinemaData.MOODS[mood].toUpperCase(java.util.Locale.ROOT) : item.genre);
            title.setText(item.name); detail.setText(item.line);
            hint.setText(picker ? "OK  Find where to watch     →  Another pick     Back  Choose mood"
                    : "← / →  Browse favourites     " + (selected + 1) + " / " + CinemaData.TITLES.length + "     OK  Find where to watch");
        }
    }
    private void openWatchOptions(int index) {
        if (index < 0) return;
        CinemaData.Title item = CinemaData.TITLES[index];
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setClipChildren(false);
        options.setClipToPadding(false);
        options.setPadding(dp(56), dp(34), dp(56), dp(32));
        options.setBackgroundColor(0xF5000000);
        options.addView(text(item.name, 30, Color.WHITE));
        TextView explanation = text("Choose an app to search. Availability depends on your region and subscription.", 14, 0xFFD0D0D0);
        explanation.setPadding(0, dp(12), 0, dp(18)); options.addView(explanation);
        String[][] apps = {{"Netflix", "com.netflix.ninja"}, {"Prime Video", "com.amazon.amazonvideo.livingroom"},
                {"JioHotstar", "in.startv.hotstar"}, {"Apple TV", "com.apple.atve.androidtv.appletv"},
                {"Sun NXT", "com.suntv.sunnxt"}, {"YouTube", "com.google.android.youtube.tv"}};
        android.widget.PopupWindow popup = new android.widget.PopupWindow(options, -1, -1, true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(0); popup.setAnimationStyle(0);
        TextView first = null;
        for (String[] app : apps) {
            if (getPackageManager().getLeanbackLaunchIntentForPackage(app[1]) == null) continue;
            TextView choice = button(app[0], () -> { popup.dismiss(); searchIn(app[1], item.name); });
            options.addView(choice, new LinearLayout.LayoutParams(-1, dp(43)));
            if (first == null) first = choice;
        }
        options.addView(button("Back", popup::dismiss));
        popup.showAtLocation(root, Gravity.CENTER, 0, 0);
        if (first != null) first.requestFocus();
    }
    private void searchIn(String pkg, String query) {
        Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, query);
        try {
            if (search.resolveActivity(getPackageManager()) != null) { startActivity(search); return; }
            Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (launch != null) {
                startActivity(launch);
                Toast.makeText(this, "Search for “" + query + "” in the app", Toast.LENGTH_LONG).show();
            }
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "That app could not open. Choose another app.", Toast.LENGTH_LONG).show();
        }
    }
    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (!result && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT)) { step(key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1); return true; }
        return super.onKeyDown(key, event);
    }
    @Override public void onBackPressed() {
        if (picker && result) { result = false; refresh(); primary.requestFocus(); }
        else finish();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("selected", selected); state.putInt("mood", mood);
        state.putInt("chosen", chosen); state.putBoolean("result", result);
    }
    @Override protected void onResume() { super.onResume(); if (wallpaper != null) wallpaper.start(); }
    @Override protected void onPause() { if (wallpaper != null) wallpaper.stop(); super.onPause(); }
    @Override protected void onDestroy() { if (wallpaper != null) wallpaper.close(); super.onDestroy(); }
}
