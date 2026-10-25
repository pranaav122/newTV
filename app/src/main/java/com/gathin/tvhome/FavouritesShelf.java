package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.io.*;
import java.util.Random;
import java.util.concurrent.Executors;

/** Local character composition. It draws only on selection/animation; there is no render loop. */
public final class FavouritesShelf extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ColorMatrixColorFilter grey;
    private final Bitmap[] figures = new Bitmap[5], scenes = new Bitmap[5];
    private final Rect[] crops = new Rect[5];
    private static final float[] POSITIONS = {.29f,.50f,.71f,.39f,.61f};
    private final Typeface headingFont = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Drawable[] logos = new Drawable[5];
    private final Shader[] glows = new Shader[5];
    private Shader legFade;
    private final RectF figureBounds = new RectF();
    private final PorterDuffXfermode darken = new PorterDuffXfermode(PorterDuff.Mode.DARKEN);
    private final Random random = new Random();
    private final float[] rays = new float[10];
    private int selected;
    private float progress = 1f;
    private ValueAnimator moment;
    private boolean loaded, loading;
    private Runnable above, below;
    private java.util.function.Consumer<Bitmap> scene = bitmap -> {};
    private final float density;

    public FavouritesShelf(Context context) { this(context, null); }
    public FavouritesShelf(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        for (int i=0;i<logos.length;i++) logos[i]=QuietLogo.forPackage(context,FavouriteTitles.PACKAGES[i]);
        ColorMatrix matrix = new ColorMatrix(); matrix.setSaturation(0);
        grey = new ColorMatrixColorFilter(matrix);
        setFocusable(true); setFocusableInTouchMode(true);
        setContentDescription("My Favourites. Left and right choose a character; up and down change rows.");
        setOnClickListener(v -> FavouriteTitles.open((Activity) getContext(), selected));
    }
    void navigate(Runnable above, Runnable below) { this.above = above; this.below = below; }
    void sceneListener(java.util.function.Consumer<Bitmap> listener) { scene = listener; }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (loaded || loading) return;
        loading = true;
        // One bounded preparation job, then the executor exits. No decode on D-pad movement.
        java.util.concurrent.ExecutorService loader = Executors.newSingleThreadExecutor();
        loader.execute(() -> {
            Bitmap[] f = new Bitmap[5], s = new Bitmap[5]; Rect[] bounds = new Rect[5];
            for (int i = 0; i < 5; i++) {
                f[i] = decode(localFile("characters", FavouriteTitles.KEYS[i] + ".png"), 640, false);
                if (f[i] != null) bounds[i] = alphaBounds(f[i]);
                s[i] = decode(localFile("scenes", FavouriteTitles.KEYS[i] + ".jpg"), 960, true);
            }
            post(() -> {
                System.arraycopy(f, 0, figures, 0, 5); System.arraycopy(s, 0, scenes, 0, 5);
                System.arraycopy(bounds, 0, crops, 0, 5); loaded = true; loading = false;
                if (isFocused()) scene.accept(scenes[selected]);
                invalidate();
            });
            loader.shutdown();
        });
    }
    private File localFile(String folder, String name) {
        File external = Config.dir(getContext());
        File source = external == null ? null : new File(external, folder + "/" + name);
        File cache = new File(getContext().getFilesDir(), "character-art/" + folder + "/" + name);
        if (source != null && source.isFile()) {
            if (!cache.isFile() || cache.length() != source.length() || source.lastModified() > cache.lastModified()) {
                File parent = cache.getParentFile();
                if (parent != null && (parent.isDirectory() || parent.mkdirs())) {
                    try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(cache)) {
                        byte[] bytes = new byte[16384]; int n;
                        while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
                        cache.setLastModified(source.lastModified());
                    } catch (IOException ignored) { return source; }
                }
            }
        }
        return cache;
    }
    private Bitmap decode(File file, int limit, boolean scene) {
        if (!file.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), o); o.inSampleSize = 1;
        while (Math.max(o.outWidth, o.outHeight) / o.inSampleSize > limit * 2) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        Bitmap raw = BitmapFactory.decodeFile(file.getPath(), o);
        if (raw == null) return null;
        float scale = Math.min(1f, limit / (float) Math.max(raw.getWidth(), raw.getHeight()));
        int w = Math.max(1, Math.round(raw.getWidth() * scale)), h = Math.max(1, Math.round(raw.getHeight() * scale));
        Bitmap ready = Bitmap.createBitmap(w, h, scene ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(ready); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (scene) p.setColorFilter(grey);
        canvas.drawBitmap(raw, null, new Rect(0, 0, w, h), p);
        if (scene) canvas.drawColor(0xAA000000);
        raw.recycle(); return ready;
    }
    private static Rect alphaBounds(Bitmap bitmap) {
        int w = bitmap.getWidth(), h = bitmap.getHeight(), l = w, t = h, r = 0, b = 0;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) if (Color.alpha(row[x]) > 24) {
                l = Math.min(l, x); r = Math.max(r, x + 1); t = Math.min(t, y); b = Math.max(b, y + 1);
            }
        }
        return r > l ? new Rect(l, t, r, b) : new Rect(0, 0, w, h);
    }
    @Override protected void onFocusChanged(boolean gain, int direction, Rect previous) {
        super.onFocusChanged(gain, direction, previous);
        if (gain) choose(selected); else {
            if (moment != null) moment.cancel();
            scene.accept(null); invalidate();
        }
    }
    private void choose(int index) {
        selected = index;
        setContentDescription(FavouriteTitles.NAMES[index] + ", " + FavouriteTitles.TITLES[index] + ". Press OK to open.");
        scene.accept(scenes[index]);
        if (moment != null) moment.cancel();
        for (int i = 0; i < rays.length; i++) rays[i] = .8f + random.nextFloat() * .4f;
        moment = ValueAnimator.ofFloat(0, 1); moment.setDuration(index == 2 ? 700 : 520);
        moment.setInterpolator(new DecelerateInterpolator());
        moment.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
        moment.start();
    }
    boolean navigateKey(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) performClick();
            return true;
        }
        if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT
                && key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN
                && key != KeyEvent.KEYCODE_BACK) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int next = FavouriteTitles.move(selected, key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1, 0);
            if (next != selected) choose(next); return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            boolean down = key == KeyEvent.KEYCODE_DPAD_DOWN;
            if (!down && selected < 3) { if (above != null) above.run(); }
            else if (down && selected >= 3) { if (below != null) below.run(); }
            else choose(FavouriteTitles.move(selected, 0, down ? 1 : -1));
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK) { if (above != null) above.run(); return true; }
        return false;
    }
    @Override protected void onDetachedFromWindow() {
        if (moment != null) moment.cancel();
        if (isFocused()) scene.accept(null);
        super.onDetachedFromWindow();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        if(w<=0 || h<=0)return;
        for(int i=0;i<glows.length;i++) {
            glows[i]=new RadialGradient(w*POSITIONS[i],h*.62f,h*.50f,
                    new int[]{(FavouriteTitles.COLORS[i]&0xFFFFFF)|0x65000000,0},null,Shader.TileMode.CLAMP);
            if(logos[i]!=null)logos[i].setBounds((int)(w-59*density),0,w,(int)(35*density));
        }
        legFade=new LinearGradient(0,h*.77f,0,h,new int[]{0,Color.BLACK},null,Shader.TileMode.CLAMP);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        paint.setShader(null); paint.setColorFilter(null); paint.setAlpha(255); paint.setColor(Color.WHITE);
        paint.setTypeface(headingFont);
        paint.setTextSize(20 * density);
        canvas.drawText("My Favourites", 14 * density, 25 * density, paint);
        if (!loaded) return;
        boolean active = isFocused();
        if (active) {
            paint.setTextSize(18 * density); paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(FavouriteTitles.TITLES[selected], w - 64*density, 25*density, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            Drawable logo = logos[selected];
            if (logo != null) logo.draw(canvas);
            paint.setShader(glows[selected]);
            canvas.drawRect(0, 0, w, h, paint); paint.setShader(null);
        }
        // Back-row selection never changes the depth order. Only a selected front figure is drawn last.
        for (int i = 0; i < 3; i++) figure(canvas, i, active);
        for (int i = 3; i < 5; i++) if (!(active && selected == i)) figure(canvas, i, active);
        if (active && selected >= 3) figure(canvas, selected, true);
        paint.setColorFilter(null); paint.setColor(Color.BLACK); paint.setAlpha(255);
        paint.setShader(legFade);
        canvas.drawRect(0,h*.77f,w,h,paint); paint.setShader(null);
        if (active) {
            paint.setColor(0xFFE4E4E4); paint.setTextSize(11*density); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(FavouriteTitles.NAMES[selected] + "   ·   OK to watch", w/2, h-4*density,paint); paint.setTextAlign(Paint.Align.LEFT);
        }
    }
    private void figure(Canvas c, int index, boolean active) {
        Bitmap bitmap = figures[index]; if (bitmap == null) return;
        float w=getWidth(), h=getHeight(), room=h-48*density;

        boolean chosen=active && selected==index;
        float growth=chosen ? 1f+.10f*Math.min(1,progress*3) : 1f;
        float fh=room*(index<3?.92f:.72f)*growth;
        Rect source=crops[index]; float fw=fh*source.width()/source.height();
        float bottom=h-(index<3?14:0)*density, x=w*POSITIONS[index], y=bottom-fh;
        float pulse=chosen?(float)Math.sin(Math.PI*Math.min(1,progress*2)):0;
        if(chosen && index==0) x+=2*density*pulse;
        if(chosen && index==1) y-=3*density*pulse;
        if(chosen && index==2) y+=7*density*(float)Math.sin(Math.PI*Math.min(1,progress*3));
        if(chosen && index==4) x+=4*density*pulse;
        if(chosen && index==3) {
            // A code-drawn Red John emblem replaces the absent PSD layer; Darken is preserved.
            paint.setColor(0xFFAD1C23); paint.setAlpha(Math.round(200*Math.min(1,progress*2)));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3*density);
            paint.setXfermode(darken);
            float ex=x-fw*.66f, ey=y+fh*.22f, er=22*density;
            c.drawCircle(ex-er*.38f,ey-er*.25f,2*density,paint); c.drawCircle(ex+er*.38f,ey-er*.25f,2*density,paint);
            c.drawArc(ex-er,ey-er,ex+er,ey+er,15,150,false,paint);
            paint.setXfermode(null); paint.setStyle(Paint.Style.FILL);
        }
        paint.setAlpha(255); paint.setColorFilter(chosen?null:grey);
        figureBounds.set(x-fw/2,y,x+fw/2,bottom);
        c.drawBitmap(bitmap,source,figureBounds,paint); paint.setColorFilter(null);
        if(chosen && index==2 && progress<.95f) {
            paint.setColor(Color.WHITE); paint.setAlpha(Math.round(220*(1-progress))); paint.setStrokeWidth(1.5f*density);
            float hy=y+fh*.065f;
            for(int j=0;j<rays.length;j++) {
                double angle=Math.PI*(1.05f+j*.10f); float r=16*density;
                c.drawLine(x+(float)Math.cos(angle)*r,hy+(float)Math.sin(angle)*r,
                        x+(float)Math.cos(angle)*r*1.75f*rays[j],hy+(float)Math.sin(angle)*r*1.75f*rays[j],paint);
            }
            paint.setAlpha(255);
        }
    }
}
