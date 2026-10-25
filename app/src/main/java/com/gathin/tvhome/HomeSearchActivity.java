package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

/** Searches local installed apps and the owner's five favourites; no online catalog request. */
public final class HomeSearchActivity extends Activity {
    private WallpaperPlayer wallpaper;
    private LinearLayout results;
    private EditText query;
    private final Map<String, ResolveInfo> apps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK); setContentView(root);
        wallpaper = new WallpaperPlayer(this, root, Config.load(this));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(64),dp(30),dp(64),dp(20)); body.setBackgroundColor(0xC9000000); body.setClipChildren(false);
        root.addView(body, new FrameLayout.LayoutParams(-1,-1));
        TextView heading = text("Search apps and favourites", 24); body.addView(heading);
        query = new EditText(this); query.setSingleLine(true); query.setTextColor(Color.WHITE);
        query.setHintTextColor(0xFFB0B0B0); query.setTextSize(20); query.setHint("Type an app, title or character");
        query.setBackground(null); query.setPadding(dp(8),0,dp(8),0);
        body.addView(query,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView hint = text("Local search  ·  Back returns home",12); hint.setTextColor(0xFFBBBBBB); body.addView(hint);
        ScrollView scroll = new ScrollView(this); scroll.setClipChildren(false); scroll.setClipToPadding(false);
        results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); results.setClipChildren(false);
        results.setPadding(dp(12),dp(16),dp(12),dp(12)); scroll.addView(results);
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}) {
            for(ResolveInfo info:getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)) {
                if(info.activityInfo==null || !info.activityInfo.exported || AppLibraryDialog.unsafeEntry(info)
                        || info.activityInfo.packageName.equals(getPackageName())) continue;
                String label=info.loadLabel(getPackageManager()).toString();
                boolean duplicate=false;
                for(ResolveInfo known:apps.values()) if(known.activityInfo.packageName.equals(info.activityInfo.packageName)) duplicate=true;
                if(!duplicate) apps.put(label,info);
            }
        }
        query.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){filter(s.toString());}
            public void afterTextChanged(Editable e){}
        });
        query.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_DPAD_DOWN && event.getAction()==KeyEvent.ACTION_DOWN && results.getChildCount()>0) {
                ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(),0);
                results.getChildAt(0).requestFocus(); return true;
            } return false;
        });
        filter(""); query.requestFocus();
    }
    private TextView text(String value,int size) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);return t;
    }
    private void result(String label,Runnable open) {
        TextView t=text(label,17); t.setFocusable(true);t.setPadding(dp(6),0,dp(6),0);t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPivotX(0);t.setOnClickListener(v->open.run());
        t.setOnFocusChangeListener((v,on)->v.animate().scaleX(on?1.08f:1).scaleY(on?1.08f:1).setDuration(140).start());
        results.addView(t,new LinearLayout.LayoutParams(-1,dp(42)));
    }
    private void filter(String value) {
        String needle=value.trim().toLowerCase(Locale.ROOT); results.removeAllViews();
        for(int i=0;i<FavouriteTitles.TITLES.length;i++) {
            final int index=i;
            if((FavouriteTitles.TITLES[i]+" "+FavouriteTitles.NAMES[i]).toLowerCase(Locale.ROOT).contains(needle))
                result(FavouriteTitles.TITLES[i]+"  ·  "+FavouriteTitles.NAMES[i],()->FavouriteTitles.open(this,index));
        }
        for(Map.Entry<String,ResolveInfo> e:apps.entrySet()) if(e.getKey().toLowerCase(Locale.ROOT).contains(needle)) {
            ResolveInfo info=e.getValue();
            result(e.getKey(),()->{
                Intent launch=getPackageManager().getLeanbackLaunchIntentForPackage(info.activityInfo.packageName);
                if(launch==null) launch=getPackageManager().getLaunchIntentForPackage(info.activityInfo.packageName);
                if(launch!=null) startActivity(launch);
            });
        }
        if(results.getChildCount()==0) results.addView(text("No matching apps or favourites",17));
    }
    @Override protected void onResume(){super.onResume();wallpaper.start();}
    @Override protected void onPause(){wallpaper.stop();super.onPause();}
    @Override protected void onDestroy(){wallpaper.close();super.onDestroy();}
}
