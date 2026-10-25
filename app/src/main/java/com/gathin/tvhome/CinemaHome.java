package com.gathin.tvhome;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.animation.ValueAnimator;
import android.content.Intent;
import androidx.recyclerview.widget.RecyclerView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground presentation plus a cached weather read from the separate, same-signed weather app. */
final class CinemaHome {
    private final Activity activity;
    private final TextView quote, weather;
    private final TextView search;
    private final SceneBackground background = new SceneBackground(true);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean resumed, loading;
    private static final String[][] LINES = {
        {"com.netflix.ninja", "Aarambikkalaangala?"},
        {"com.google.android.youtube.tv", "What is that maamu?"},
        {"com.amazon.amazonvideo.livingroom", "Anubavikkanum... aaraaya koodadhu!"},
        {"in.startv.hotstar", "Raghavan instinct."},
        {"com.spotify.tv.android", "Kanmani... anbodu..."},
        {"com.suntv.sunnxt", "Naalu perukku nalladhunna, edhuvum thappilla."},
        {"com.apple.atve.androidtv.appletv", "Theriyalaye paa!"}
    };
    CinemaHome(Activity activity, RecyclerView grid) {
        this.activity = activity;
        float density = activity.getResources().getDisplayMetrics().density;
        View foreground = activity.findViewById(R.id.homeForeground);
        foreground.setBackground(background);
        search = text(18, Color.WHITE);
        search.setText("⌕   Search apps and favourites"); search.setContentDescription("Search apps and favourites");
        search.setFocusable(true); search.setGravity(Gravity.CENTER_VERTICAL); search.setPivotX(0);
        search.setOnClickListener(v -> activity.startActivity(new Intent(activity, HomeSearchActivity.class)));
        search.setOnFocusChangeListener((v, focused) -> v.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f).setDuration(140).start());
        search.setOnKeyListener((v,key,event) -> {
            if(event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if(key == android.view.KeyEvent.KEYCODE_DPAD_DOWN) { ((MainActivity)activity).searchDown(); return true; }
            if(key == android.view.KeyEvent.KEYCODE_DPAD_UP) { ((MainActivity)activity).searchUp(); return true; }
            return false;
        });
        LinearLayout clockRow = (LinearLayout) activity.findViewById(R.id.clock).getParent();
        clockRow.setBaselineAligned(false);
        // Replace the hidden date spacer with the header's search control.
        View dateSpacer = (View) activity.findViewById(R.id.date).getParent();
        int searchIndex = clockRow.indexOfChild(dateSpacer);
        clockRow.removeView(dateSpacer);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,Math.round(48*density),1);
        sp.leftMargin = Math.round(24*density); sp.gravity = Gravity.CENTER_VERTICAL;
        search.setTextSize(16); search.setSingleLine(true);
        search.setEllipsize(android.text.TextUtils.TruncateAt.END);
        clockRow.addView(search,searchIndex,sp);
        quote = text(24, 0xFFE0E0E0);
        quote.setSingleLine(true); quote.setEllipsize(android.text.TextUtils.TruncateAt.END);
        quote.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        quote.setText(LINES[0][1]); quote.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        FrameLayout.LayoutParams q = new FrameLayout.LayoutParams(-1,Math.round(48*density),Gravity.TOP|Gravity.START);
        q.leftMargin = Math.round(10 * density);
        ((FrameLayout) grid.getParent()).addView(quote, q);
        grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> grid.post(() ->
                quote.setTranslationY(Math.max(0,grid.getPaddingTop()-68*density))));
        weather = text(13, 0xFFE5E5E5);
        weather.setText("Weather unavailable");
        weather.setPadding(Math.round(18 * density), 0, 0, 0);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        clockRow.addView(weather, new LinearLayout.LayoutParams(-2, -1));
    }
    private TextView text(float size, int color) {
        TextView view = new TextView(activity); view.setTextSize(size); view.setTextColor(color);
        view.setShadowLayer(4, 0, 2, Color.BLACK); view.setFocusable(false); return view;
    }
    void focus(String pkg) {
        for(String[] line : LINES) if(line[0].equals(pkg)) { quote.setText(line[1]); return; }
        quote.setText(LINES[0][1]);
    }
    void searchFocus() { search.requestFocus(); }
    void scene(Bitmap scene) {
        quote.setVisibility(scene == null ? View.VISIBLE : View.INVISIBLE);
        background.show(scene);
    }
    void resume() {
        resumed = true;
        if (loading) return;
        loading = true;
        io.execute(() -> {
            String label = "Weather unavailable";
            try (Cursor cursor = activity.getContentResolver().query(Uri.parse("content://com.newtv.weather/current"), null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int temp = cursor.getInt(cursor.getColumnIndexOrThrow("temperature"));
                    int code = cursor.getInt(cursor.getColumnIndexOrThrow("code"));
                    long updated = cursor.getLong(cursor.getColumnIndexOrThrow("updated"));
                    boolean old = System.currentTimeMillis() - updated > 60 * 60_000L;
                    String sky = code == 0 ? "Clear" : code <= 3 ? "Cloudy" : code < 50 ? "Mist" : code < 70 ? "Rain" : code < 80 ? "Snow" : code < 95 ? "Showers" : "Thunderstorms";
                    int cityColumn = cursor.getColumnIndex("city");
                    String city = cityColumn >= 0 ? cursor.getString(cityColumn) : null;
                    String place = city == null || city.trim().isEmpty() ? "Weather" : city.trim();
                    label = place + "  ·  " + temp + "°C  ·  " + sky + (old ? "  (cached)" : "");
                }
            } catch (Exception unavailable) { /* An offline or absent companion leaves an honest placeholder. */ }
            String result = label;
            activity.runOnUiThread(() -> {
                loading = false;
                if (resumed && !activity.isDestroyed()) weather.setText(result);
            });
        });
    }
    void pause() { resumed = false; }
    void close() { pause(); io.shutdownNow(); background.stop(); }

    static final class SceneBackground extends Drawable {
        private final boolean shadeHeader;
        SceneBackground() { this(false); }
        SceneBackground(boolean shadeHeader) { this.shadeHeader = shadeHeader; }
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Bitmap previous, current;
        private final RectF imageBounds = new RectF();
        private android.graphics.Shader shade;
        @Override protected void onBoundsChange(android.graphics.Rect bounds) {
            shade=new android.graphics.LinearGradient(0,0,0,Math.max(1,bounds.height()),
                    new int[]{shadeHeader?0xB3000000:0x45000000,0x18000000,0xFF000000,0xFF000000},
                    new float[]{0,.45f,.80f,1},android.graphics.Shader.TileMode.CLAMP);
        }
        private float fraction=1;
        private ValueAnimator fade;
        void show(Bitmap bitmap) {
            if(current == bitmap) return;
            if(fade != null) fade.cancel();
            previous=current; current=bitmap; fraction=0;
            fade=ValueAnimator.ofFloat(0,1);fade.setDuration(260);
            fade.addUpdateListener(a->{fraction=(float)a.getAnimatedValue();if(fraction==1)previous=null;invalidateSelf();});fade.start();
        }
        void stop(){if(fade!=null)fade.cancel();previous=null;}
        private void image(Canvas c,Bitmap b,float alpha) {
            if(b==null || alpha<=0)return;
            float w=getBounds().width(),h=getBounds().height(),s=Math.max(w/b.getWidth(),h/b.getHeight());
            float bw=b.getWidth()*s,bh=b.getHeight()*s;
            imageBounds.set((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2);
            paint.setAlpha(Math.round(255*alpha));c.drawBitmap(b,null,imageBounds,paint);
        }
        @Override public void draw(Canvas c){
            image(c,previous,1-fraction);image(c,current,fraction);
            paint.setAlpha(255);paint.setShader(shade);
            c.drawRect(getBounds(),paint);paint.setShader(null);
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(android.graphics.ColorFilter filter){}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
}
