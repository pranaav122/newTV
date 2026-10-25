package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded, offline card/reel drawing on the existing Home surface; no timer survives leaving it. */
final class MoodDeck extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path posterClip = new Path();
    private final Matrix posterMatrix = new Matrix();
    private final RectF shaderSource = new RectF();
    private static final class Poster {
        final Bitmap bitmap;
        final BitmapShader shader;
        Poster(Bitmap bitmap) {
            this.bitmap=bitmap;
            shader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        }
    }
    private final Rect posterSource = new Rect();
    private final RectF posterFit = new RectF();
    private final ColorMatrixColorFilter grey;
    private final Random random = new Random();
    private final float density;
    private final LruCache<String,Poster> art = new LruCache<String,Poster>(8*1024*1024) {
        @Override protected int sizeOf(String key, Poster poster) { return poster.bitmap.getAllocationByteCount(); }
    };
    private final LruCache<String,Bitmap> scenes = new LruCache<String,Bitmap>(4*1024*1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private MoodCatalog catalog = new MoodCatalog();
    private MoodFlow flow = new MoodFlow(catalog.pools);
    private ValueAnimator animation, fade;
    private final MoodCountdown timer = new MoodCountdown();
    private Shader vignette, glow;
    private Bitmap background, oldBackground, pickedBackground;
    private float motion, reel, countdown, crossfade = 1;
    private int generation, artGeneration;
    private boolean attached, browseAfterSpin;
    private int reelTarget;
    private Runnable above, below;
    private Runnable catalogueListener;
    void catalogueReady(Runnable listener) { catalogueListener=listener; if(!catalog.titles.isEmpty()) listener.run(); }
    MoodCatalog catalogue() { return catalog; }
    private final Runnable countdownTick = new Runnable() {
        @Override public void run() {
            if(!timer.active() || flow.phase!=MoodFlow.Phase.COUNTDOWN) return;
            long now=android.os.SystemClock.uptimeMillis();
            countdown=timer.progress(now); invalidate();
            if(timer.expired(now)) open(); else postDelayed(this,32);
        }
    };

    MoodDeck(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        ColorMatrix matrix = new ColorMatrix(); matrix.setSaturation(0);
        grey = new ColorMatrixColorFilter(matrix);
        setFocusable(true); setFocusableInTouchMode(true);
        setContentDescription("Don't have time to browse? Left skips a mood. Right or OK picks a title.");
    }
    void navigate(Runnable above, Runnable below) { this.above=above; this.below=below; }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); attached=true;
        ExecutorService loader = Executors.newSingleThreadExecutor();
        loader.execute(() -> {
            MoodCatalog ready = MoodCatalog.load(getContext());
            // Size each poster for its share of the unchanged cache, including larger snapshots.
            for (MoodCatalog.Title title : ready.titles) {
                File file=MoodCatalog.local(getContext(),"posters/"+title.id+".jpg");
                BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(file.getPath(),bounds);
                int limit=MoodPosterBudget.limit(bounds.outWidth,bounds.outHeight,bounds.outMimeType,
                        ready.titles.size(),art.maxSize());
                Bitmap bitmap = decode(file,limit);
                if (bitmap!=null) synchronized(art) { art.put("p/"+title.id,new Poster(bitmap)); }
            }
            post(() -> {
                if (!attached) return;
                catalog=ready; flow=new MoodFlow(ready.pools); showMood(); invalidate();
                if(catalogueListener!=null) catalogueListener.run();
            });
            loader.shutdown();
        });
    }
    private Bitmap decode(File file, int limit) {
        if (!file.isFile()) return null;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getPath(),options);
        options.inSampleSize=1;
        while (Math.max(options.outWidth,options.outHeight)/options.inSampleSize>limit*2) options.inSampleSize*=2;
        options.inJustDecodeBounds=false; options.inPreferredConfig=Bitmap.Config.RGB_565;
        Bitmap raw=BitmapFactory.decodeFile(file.getPath(),options);
        if (raw==null) return null;
        float scale=Math.min(1,limit/(float)Math.max(raw.getWidth(),raw.getHeight()));
        Bitmap small=Bitmap.createScaledBitmap(raw,Math.max(1,Math.round(raw.getWidth()*scale)),
                Math.max(1,Math.round(raw.getHeight()*scale)),true);
        if (small!=raw) raw.recycle();
        return small;
    }
    private Poster poster(int title) {
        synchronized(art) { return art.get("p/"+catalog.titles.get(title).id); }
    }
    private void loadScene(int title, java.util.function.Consumer<Bitmap> ready) {
        int request=++artGeneration;
        if (title<0 || title>=catalog.titles.size()) { ready.accept(null); return; }
        String id=catalog.titles.get(title).id;
        Bitmap cached;
        synchronized(scenes) { cached=scenes.get(id); }
        if(cached!=null) { ready.accept(cached); return; }
        ExecutorService loader=Executors.newSingleThreadExecutor();
        loader.execute(() -> {
            Bitmap decoded=decode(MoodCatalog.local(getContext(),"backdrops/"+id+".jpg"),960);
            if (decoded!=null) synchronized(scenes) { scenes.put(id,decoded); }
            post(() -> { if(attached && request==artGeneration) ready.accept(decoded); });
            loader.shutdown();
        });
    }
    private void showMood() {
        pickedBackground=null;
        updateShaders();
        if(!flow.pool().isEmpty()) loadScene(flow.pool().get(0),this::scene);
        if(!catalog.moods.isEmpty()) setContentDescription(catalog.moods.get(flow.mood).name+", "+flow.pool().size()
                +" titles. Left skips; Right or OK picks; Up returns to My Favourites.");
    }
    private void scene(Bitmap bitmap) {
        if(background==bitmap) return;
        if(fade!=null) fade.cancel();
        oldBackground=background; background=bitmap; crossfade=0;
        fade=ValueAnimator.ofFloat(0,1); fade.setDuration(260);
        fade.addUpdateListener(a -> {crossfade=(float)a.getAnimatedValue(); invalidate();});
        fade.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) {oldBackground=null;} });
        fade.start();
    }
    private void stopAnimation() {
        generation++;
        timer.cancel(); removeCallbacks(countdownTick);
        if(animation!=null) { animation.removeAllListeners(); animation.cancel(); animation=null; }
    }
    void cancel() {
        stopAnimation(); flow.cancel(); browseAfterSpin=false; motion=0; countdown=0; showMood(); invalidate();
    }
    private void halt() {
        stopAnimation(); artGeneration++; flow.cancel(); browseAfterSpin=false; motion=0; countdown=0;
        if(fade!=null) { fade.removeAllListeners(); fade.cancel(); }
        oldBackground=null; crossfade=1; invalidate();
    }
    void pause() { halt(); }
    void close() {
        attached=false; artGeneration++; stopAnimation();
        if(fade!=null) fade.cancel();
        synchronized(art) { art.evictAll(); }
        synchronized(scenes) { scenes.evictAll(); }
        background=null; oldBackground=null; pickedBackground=null;
    }
    @Override protected void onDetachedFromWindow() { close(); super.onDetachedFromWindow(); }
    @Override protected void onFocusChanged(boolean gain,int direction,Rect old) {
        super.onFocusChanged(gain,direction,old);
        if(!gain) halt(); else { showMood(); invalidate(); }
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(!hasFocus) halt();
    }
    boolean navigateKey(KeyEvent event) {
        int key=event.getKeyCode();
        boolean ok=key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER;
        if(!ok && key!=KeyEvent.KEYCODE_MENU && key!=KeyEvent.KEYCODE_DPAD_LEFT && key!=KeyEvent.KEYCODE_DPAD_RIGHT
                && key!=KeyEvent.KEYCODE_DPAD_UP && key!=KeyEvent.KEYCODE_DPAD_DOWN && key!=KeyEvent.KEYCODE_BACK) return false;
        if(event.getAction()!=KeyEvent.ACTION_DOWN || event.getRepeatCount()!=0) return true;
        if(key==KeyEvent.KEYCODE_DPAD_UP) {
            if(flow.phase!=MoodFlow.Phase.DECK) cancel(); else if(above!=null) above.run();
        } else if(key==KeyEvent.KEYCODE_DPAD_DOWN) {
            if(flow.phase==MoodFlow.Phase.REEL || flow.phase==MoodFlow.Phase.COUNTDOWN) beginBrowse();
            else { cancel(); if(below!=null) below.run(); }
        }
        else if(key==KeyEvent.KEYCODE_BACK) {
            if(flow.phase!=MoodFlow.Phase.DECK) cancel(); else if(above!=null) above.run();
        } else if(key==KeyEvent.KEYCODE_MENU) {
            if(flow.phase==MoodFlow.Phase.BROWSE) spin(); else beginBrowse();
        } else if(flow.phase==MoodFlow.Phase.BROWSE) {
            if(ok) open();
            else if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT)
                browseStep(key==KeyEvent.KEYCODE_DPAD_LEFT ? -1:1);
        } else if(key==KeyEvent.KEYCODE_DPAD_LEFT) {
            if(flow.phase==MoodFlow.Phase.DECK) swipe(); else cancel();
        } else if(ok && flow.phase==MoodFlow.Phase.COUNTDOWN) open();
        else if(flow.phase==MoodFlow.Phase.DECK || flow.phase==MoodFlow.Phase.COUNTDOWN) spin();
        return true;
    }
    private void swipe() {
        if(!flow.swipe()) return;
        animate(0,1,350,new DecelerateInterpolator(),value -> motion=value,() -> {
            flow.finishSwipe(); motion=0; showMood(); invalidate();
        });
    }
    private void spin() {
        stopAnimation();
        if(!flow.spin(random)) return;
        browseAfterSpin=false;
        pickedBackground=null;
        int picked=flow.chosen;
        loadScene(picked,bitmap -> {
            if(flow.chosen!=picked) return;
            pickedBackground=bitmap;
            if(flow.phase==MoodFlow.Phase.COUNTDOWN || flow.phase==MoodFlow.Phase.BROWSE) scene(bitmap);
        });
        List<Integer> pool=flow.pool();
        float destination=pool.size()*5+pool.indexOf(flow.chosen); reelTarget=(int)destination;
        animate(0,destination,3600,new DecelerateInterpolator(2.1f),value -> reel=value,() -> {
            flow.land();
            if(pickedBackground!=null) scene(pickedBackground);
            if(browseAfterSpin) beginBrowse(); else countDown();
        });
    }
    private void countDown() {
        setContentDescription(catalog.titles.get(flow.chosen).name+". Opening in three seconds. Down browses titles; OK opens now; Right spins again; Left or Back cancels.");
        stopAnimation(); countdown=0; timer.start(android.os.SystemClock.uptimeMillis()); post(countdownTick);
    }
    private void beginBrowse() {
        if(flow.phase==MoodFlow.Phase.REEL) {
            browseAfterSpin=true; setContentDescription("The reel will stop in manual browsing. Nothing opens automatically.");
            invalidate(); return;
        }
        boolean fromDeck=flow.phase==MoodFlow.Phase.DECK;
        if(!flow.browse()) return;
        stopAnimation(); browseAfterSpin=false;
        if(fromDeck) reel=reelTarget=flow.pool().indexOf(flow.chosen);
        describeBrowse(); invalidate();
    }
    private void browseStep(int direction) {
        if(!flow.step(direction)) return;
        reelTarget+=direction;
        int picked=flow.chosen;
        loadScene(picked,bitmap -> {if(flow.phase==MoodFlow.Phase.BROWSE && flow.chosen==picked) scene(bitmap);});
        describeBrowse();
        animate(reel,reelTarget,180,new DecelerateInterpolator(1.5f),value -> reel=value,() -> {});
    }
    private void describeBrowse() {
        setContentDescription(catalog.titles.get(flow.chosen).name+". "+(flow.pool().indexOf(flow.chosen)+1)
                +" of "+flow.pool().size()+" titles. Left and Right browse, OK opens, Back returns to moods.");
    }
    private void open() {
        stopAnimation();
        if(!isFocused() || !hasWindowFocus()) { flow.cancel(); return; }
        int title=flow.takeLaunch();
        if(title>=0) {
            MoodCatalog.Title picked=catalog.titles.get(title);
            TitleLink.open((Activity)getContext(),picked.app,picked.url);
        }
        invalidate();
    }
    private void animate(float start,float end,long duration,android.animation.TimeInterpolator ease,
            java.util.function.Consumer<Float> frame,Runnable finish) {
        stopAnimation(); int token=generation;
        animation=ValueAnimator.ofFloat(start,end); animation.setDuration(duration); animation.setInterpolator(ease);
        animation.addUpdateListener(a -> {frame.accept((float)a.getAnimatedValue()); invalidate();});
        animation.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) {
            if(token==generation) finish.run();
        }});
        animation.start();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        backdrop(canvas,oldBackground,1-crossfade); backdrop(canvas,background,crossfade);
        paint.setColorFilter(null); paint.setAlpha(255); paint.setColor(0xD9000000);
        canvas.drawRect(0,0,w,h,paint);
        paint.setShader(vignette);
        canvas.drawRect(0,0,w,h,paint); paint.setShader(null);
        text(canvas,"Don't have time to browse?",w/2,45*density,20,0xFFEDEDED,false);
        if(catalog.moods.isEmpty()) return;
        paint.setShader(glow);
        canvas.drawRect(0,0,w,h,paint); paint.setShader(null);
        if(flow.phase==MoodFlow.Phase.REEL || flow.phase==MoodFlow.Phase.COUNTDOWN || flow.phase==MoodFlow.Phase.BROWSE) drawReel(canvas,w,h);
        else drawDeck(canvas,w,h);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh); updateShaders();
    }
    private void updateShaders() {
        float w=getWidth(),h=getHeight(); if(w<=0 || h<=0) return;
        vignette=new RadialGradient(w/2,h*.48f,w*.62f,new int[]{0x00000000,0xCC000000},null,Shader.TileMode.CLAMP);
        int color=catalog.moods.isEmpty()?0xFFB8C0CC:catalog.moods.get(flow.mood).color;
        glow=new RadialGradient(w/2,h*.53f,h*.55f,new int[]{(color&0xFFFFFF)|0x2A000000,0},null,Shader.TileMode.CLAMP);
    }
    private void backdrop(Canvas canvas,Bitmap bitmap,float alpha) {
        if(bitmap==null || alpha<=0) return;
        paint.setColorFilter(grey); paint.setAlpha(Math.round(255*alpha));
        cover(canvas,bitmap,new RectF(0,0,getWidth(),getHeight()));
        paint.setColorFilter(null); paint.setAlpha(255);
    }
    private void drawDeck(Canvas canvas,float w,float h) {
        float cw=226*density,ch=Math.min(307*density,h-146*density),x=(w-cw)/2,y=83*density;
        // Draw the two next moods behind the active card; their names remain legible at the edges.
        for(int depth=Math.min(2,catalog.moods.size()-1);depth>=1;depth--) {
            int next=(flow.mood+depth)%catalog.moods.size();
            canvas.save(); canvas.rotate(depth==1?6: -7,w/2,y+ch*.8f);
            float dx=(depth==1?42:-40)*density;
            card(canvas,next,x+dx,y+depth*10*density,cw,ch,.58f-depth*.08f,false); canvas.restore();
        }
        canvas.save();
        if(flow.phase==MoodFlow.Phase.SWIPE) {
            canvas.translate(-w*.7f*motion,-h*.03f*motion); canvas.rotate(-15*motion,w/2,y+ch/2);
        }
        float scale=isFocused()?1.025f:1;
        canvas.scale(scale,scale,w/2,y+ch/2);
        card(canvas,flow.mood,x,y,cw,ch,1-motion,true); canvas.restore();
        text(canvas,"‹  Next mood       OK / ›  Pick for me       Menu  Browse",w/2,h-26*density,12,0xFFB9B9BE,false);
    }
    private void card(Canvas canvas,int mood,float x,float y,float w,float h,float alpha,boolean fan) {
        MoodCatalog.Mood value=catalog.moods.get(mood);
        paint.setColorFilter(null); paint.setAlpha(255);
        int tint=(value.color&0xFFFFFF)|0xCC000000;
        paint.setShader(new LinearGradient(x,y,x+w*.4f,y+h,new int[]{tint,0xFF141418,0xFF08090B},new float[]{0,.65f,1},Shader.TileMode.CLAMP));
        int save=canvas.saveLayerAlpha(x-50*density,y-20*density,x+w+50*density,y+h+20*density,Math.round(255*Math.max(0,alpha)));
        canvas.drawRoundRect(x,y,x+w,y+h,12*density,12*density,paint); paint.setShader(null);
        List<Integer> pool=catalog.pools.get(mood);
        float ph=h*.54f,pw=ph*2/3;
        for(int n=0;n<Math.min(3,pool.size());n++) {
            int offset=n==2?0:n==0?-1:1;
            float px=x+w/2+offset*40*density,py=y+26*density+Math.abs(offset)*10*density;
            canvas.save(); canvas.rotate(offset*11,px,py+ph/2);
            drawPoster(canvas,pool.get(n),new RectF(px-pw/2,py,px+pw/2,py+ph),1); canvas.restore();
        }
        text(canvas,value.name,x+w/2,y+h-50*density,16,Color.WHITE,true);
        text(canvas,pool.size()+" titles",x+w/2,y+h-27*density,12,value.color,false);
        canvas.restoreToCount(save);
    }
    private void drawReel(Canvas canvas,float w,float h) {
        List<Integer> pool=flow.pool();
        float position=flow.phase==MoodFlow.Phase.COUNTDOWN ? Math.round(reel):reel;
        int center=Math.round(position);
        float baseH=Math.min(246*density,h*.54f),step=baseH*.49f,cy=h*.46f;
        for(int distance=4;distance>=0;distance--) {
            for(int side=-1;side<=1;side+=2) {
                if(distance==0 && side==1) continue;
                int slot=center+distance*side;
                float relative=slot-position;
                float scale=Math.max(.54f,1-Math.abs(relative)*.14f);
                float ph=baseH*scale,pw=ph*2/3,cx=w/2+relative*step;
                int title=pool.get(Math.floorMod(slot,pool.size()));
                drawPoster(canvas,title,new RectF(cx-pw/2,cy-ph/2,cx+pw/2,cy+ph/2),Math.max(.1f,1-Math.abs(relative)*.23f));
            }
        }
        float baseline=cy+baseH/2+30*density;
        if(flow.phase==MoodFlow.Phase.COUNTDOWN) {
            MoodCatalog.Title title=catalog.titles.get(flow.chosen);
            text(canvas,title.name,w/2,baseline,18,Color.WHITE,true);
            text(canvas,"Opening in "+Math.max(1,(int)Math.ceil(3*(1-countdown)))+"…  OK to open · › spin again · Back to cancel",
                    w/2,baseline+25*density,11,0xFFCBCBD0,false);
            float bw=150*density,bx=(w-bw)/2,by=baseline+41*density;
            paint.setColor(0xFF343439); canvas.drawRect(bx,by,bx+bw,by+2*density,paint);
            paint.setColor(catalog.moods.get(flow.mood).color); canvas.drawRect(bx,by,bx+bw*countdown,by+2*density,paint);
            text(canvas,"↓  Browse this mood",w/2,baseline+64*density,13,Color.WHITE,false);
        } else if(flow.phase==MoodFlow.Phase.BROWSE) {
            text(canvas,catalog.titles.get(flow.chosen).name,w/2,baseline,18,Color.WHITE,true);
            text(canvas,(pool.indexOf(flow.chosen)+1)+" of "+pool.size()+" titles  ·  ‹ / › Browse  ·  OK Open  ·  Back Moods",
                    w/2,baseline+25*density,12,0xFFCBCBD0,false);
            text(canvas,"Menu  Spin again",w/2,baseline+52*density,11,0xFFB9B9BE,false);
        } else {
            text(canvas,"Finding your next watch…",w/2,baseline+15*density,13,0xFFCBCBD0,false);
            text(canvas,browseAfterSpin?"Manual browsing after this spin":"↓  Browse when the reel stops",w/2,baseline+45*density,12,Color.WHITE,false);
        }
    }
    void drawPoster(Canvas canvas,int title,RectF destination,float alpha) {
        Poster poster=poster(title);
        Bitmap bitmap=poster==null?null:poster.bitmap;
        paint.setColorFilter(null); paint.setShader(null); paint.setAlpha(Math.round(alpha*255));
        if(bitmap!=null && bitmap.getWidth()/(float)bitmap.getHeight()<=.85f
                && catalog.titles.get(title).posterLabel.isEmpty()) {
            // One rounded textured primitive replaces a stencil clip plus a bitmap draw.
            crop(bitmap,destination);
            shaderSource.set(posterSource);
            posterMatrix.setRectToRect(shaderSource,destination,Matrix.ScaleToFit.FILL);
            poster.shader.setLocalMatrix(posterMatrix);
            paint.setShader(poster.shader);
            canvas.drawRoundRect(destination,5*density,5*density,paint);
            paint.setShader(null);paint.setAlpha(255);
            return;
        }
        canvas.save(); posterClip.rewind(); posterClip.addRoundRect(destination,5*density,5*density,Path.Direction.CW); canvas.clipPath(posterClip);
        if(bitmap!=null) {
            float ratio=bitmap.getWidth()/(float)bitmap.getHeight();
            if(ratio>.85f && catalog.titles.get(title).posterLabel.isEmpty()) {
                // Apple's official season art is square. Keep its complete lettering and faces.
                paint.setColor(Color.BLACK); canvas.drawRect(destination,paint); paint.setAlpha(Math.round(alpha*255));
                float ih=destination.width()/ratio;
                posterFit.set(destination.left,destination.centerY()-ih/2,destination.right,destination.centerY()+ih/2);
                canvas.drawBitmap(bitmap,null,posterFit,paint);
            } else cover(canvas,bitmap,destination);
            String label=catalog.titles.get(title).posterLabel;
            if(!label.isEmpty()) {
                paint.setShader(new LinearGradient(0,destination.centerY(),0,destination.bottom,
                        new int[]{0,0xF5000000},null,Shader.TileMode.CLAMP));
                canvas.drawRect(destination,paint); paint.setShader(null);
                String[] lines=label.split("\n");
                for(int n=0;n<lines.length;n++) text(canvas,lines[n],destination.centerX(),
                        destination.bottom-destination.width()*(.14f+(lines.length-1-n)*.13f),
                        destination.width()*.095f/density,Color.WHITE,true);
            }
        }
        else {
            paint.setColor(0xFF24242B); canvas.drawRect(destination,paint);
            text(canvas,catalog.titles.get(title).name,destination.centerX(),destination.centerY(),10,Color.WHITE,false);
        }
        canvas.restore(); paint.setAlpha(255);
    }
    private void cover(Canvas canvas,Bitmap bitmap,RectF dst) {
        crop(bitmap,dst);
        canvas.drawBitmap(bitmap,posterSource,dst,paint);
    }
    private void crop(Bitmap bitmap,RectF dst) {
        float sourceAspect=bitmap.getWidth()/(float)bitmap.getHeight(),targetAspect=dst.width()/dst.height();
        float sw=bitmap.getWidth(),sh=bitmap.getHeight();
        if(sourceAspect>targetAspect) sw=sh*targetAspect; else sh=sw/targetAspect;
        Rect src=posterSource;
        src.set(Math.round((bitmap.getWidth()-sw)/2),Math.round((bitmap.getHeight()-sh)/2),
                Math.round((bitmap.getWidth()+sw)/2),Math.round((bitmap.getHeight()+sh)/2));
    }
    private void text(Canvas canvas,String text,float x,float y,float size,int color,boolean bold) {
        paint.setShader(null); paint.setColorFilter(null); paint.setColor(color); paint.setAlpha(255);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(size*density);
        paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        canvas.drawText(text,x,y,paint); paint.setTextAlign(Paint.Align.LEFT);
    }
}
