package com.gathin.tvhome;

import android.content.Intent;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.tv.TvContract.WatchNextPrograms;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local catalogue shelves and genuine app-published resume entries, refreshed only on Home resume. */
final class HomeTitleRows extends android.widget.FrameLayout {
    private static final class Item {
        final String name,app,url;
        final long position,duration;
        int art=-1;
        Item(String name,String app,String url,long position,long duration) {
            this.name=name;this.app=app;this.url=url;this.position=position;this.duration=duration;
        }
    }
    private final MainActivity home;
    private final MoodDeck deck;
    private final Runnable above,changed;
    private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF posterBounds=new RectF();
    private final List<List<Item>> rows=new ArrayList<>();
    private final int[] selected=new int[3];
    private int row,generation;
    private int oldRow=-1,oldColumn=-1;
    private float focusMotion=1;
    private ValueAnimator focusTween;
    private String resumeMessage="Loading shared resume items…";
    private boolean loading,closed;

    HomeTitleRows(MainActivity home,MoodDeck deck,Runnable above,Runnable changed) {
        super(home);this.home=home;this.deck=deck;this.above=above;this.changed=changed;
        for(int i=0;i<3;i++) rows.add(new ArrayList<>());
        setFocusableInTouchMode(true);setFocusable(false);
        setBackgroundColor(0xEF000000);
        // Separate render nodes let Android cull rows outside the viewport while scrolling.
        for(int i=0;i<4;i++)addView(new RowSurface(i),new LayoutParams(-1,i<3?rowHeight(i):dp(72)));
        layoutSurfaces();
        deck.catalogueReady(this::catalogueChanged);
    }
    int contentHeight() {return top(3)+dp(72);}
    int selectedTop() {return top(row);}
    private int rowHeight(int index) {return dp(index==0 && rows.get(0).isEmpty()?138:260);}
    private int top(int index) {int y=0;for(int i=0;i<index;i++)y+=rowHeight(i);return y;}
    void focusFirst() {row=0;setFocusable(true);requestFocus();describe();redraw();}
    private void catalogueChanged() {
        MoodCatalog catalog=deck.catalogue();rows.get(1).clear();rows.get(2).clear();
        for(int i=0;i<catalog.titles.size();i++) {
            MoodCatalog.Title title=catalog.titles.get(i);
            Item item=new Item(title.name,title.app,title.url,0,0);item.art=i;
            if("movie".equals(title.kind)) rows.get(1).add(item);
            else if(!title.moods.contains("tamil-tv")) rows.get(2).add(item);
        }
        for(int i=0;i<catalog.titles.size();i++) {
            MoodCatalog.Title title=catalog.titles.get(i);
            if(!"movie".equals(title.kind) && title.moods.contains("tamil-tv")) {
                Item item=new Item(title.name,title.app,title.url,0,0);item.art=i;rows.get(2).add(item);
            }
        }
        matchResumeArt();updateLayout();
    }
    void refresh() {
        if(closed || loading || !isAttachedToWindow())return;
        loading=true;int token=++generation;
        ExecutorService worker=Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            List<Item> result=readResume();
            post(() -> {
                if(closed || token!=generation)return;
                loading=false;rows.set(0,result);matchResumeArt();updateLayout();
            });
            worker.shutdown();
        });
    }
    private List<Item> readResume() {
        List<Item> result=new ArrayList<>();
        if(home.checkSelfPermission("android.permission.READ_TV_LISTINGS")!=PackageManager.PERMISSION_GRANTED) {
            resumeMessage="Shared resume entries need TV listings access.";return result;
        }
        resumeMessage="No apps have shared a title to resume yet.";
        String[] columns={WatchNextPrograms.COLUMN_TITLE,WatchNextPrograms.COLUMN_PACKAGE_NAME,
                WatchNextPrograms.COLUMN_INTENT_URI,WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                WatchNextPrograms.COLUMN_DURATION_MILLIS,WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
                WatchNextPrograms.COLUMN_BROWSABLE};
        try(Cursor cursor=home.getContentResolver().query(WatchNextPrograms.CONTENT_URI,columns,null,null,
                "last_engagement_time_utc_millis DESC")) {
            int inspected=0;
            while(cursor!=null && cursor.moveToNext() && inspected++<200 && result.size()<12) {
                String name=cursor.getString(0),app=cursor.getString(1),url=cursor.getString(2);
                int type=cursor.getInt(5);
                if(cursor.getInt(6)==0 || (type!=WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
                        && type!=WatchNextPrograms.WATCH_NEXT_TYPE_NEXT))continue;
                if(name==null || name.isEmpty() || name.length()>256 || app==null || url==null || url.length()>8192)continue;
                String owner=app.toLowerCase(Locale.ROOT);
                if(!app.matches("[A-Za-z0-9_.]+") || owner.contains("factory") || owner.contains("hkcfac")
                        || owner.contains("production") || owner.contains(".fac."))continue;
                try {if(!home.getPackageManager().getApplicationInfo(app,0).enabled)continue;}
                catch(PackageManager.NameNotFoundException absent){continue;}
                if(!safeResumeUri(url))continue;
                result.add(new Item(name,app,url,Math.max(0,cursor.getLong(3)),Math.max(0,cursor.getLong(4))));
            }
        } catch(RuntimeException unavailable) {result.clear();resumeMessage="Shared resume entries aren't available on this TV.";}
        return result;
    }
    private static boolean safeResumeUri(String value) {
        int colon=value.indexOf(':');if(colon<1)return false;
        String scheme=value.substring(0,colon).toLowerCase(Locale.ROOT);
        return !scheme.equals("file") && !scheme.equals("content") && !scheme.equals("javascript") && !scheme.equals("data");
    }
    private void matchResumeArt() {
        MoodCatalog catalog=deck.catalogue();
        for(Item item:rows.get(0))for(int i=0;i<catalog.titles.size();i++) {
            MoodCatalog.Title title=catalog.titles.get(i);
            if(item.app.equals(title.app) && item.name.equalsIgnoreCase(title.name)) {item.art=i;break;}
        }
    }
    private void updateLayout() {
        for(int i=0;i<3;i++)selected[i]=Math.min(selected[i],Math.max(0,rows.get(i).size()-1));
        if(getLayoutParams()!=null && getLayoutParams().height!=contentHeight()) {getLayoutParams().height=contentHeight();requestLayout();}
        layoutSurfaces();
        if(hasFocus())describe();changed.run();redraw();
    }
    boolean navigateKey(KeyEvent event) {
        int key=event.getKeyCode();
        boolean ok=key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER;
        if(!ok && key!=KeyEvent.KEYCODE_DPAD_UP && key!=KeyEvent.KEYCODE_DPAD_DOWN
                && key!=KeyEvent.KEYCODE_DPAD_LEFT && key!=KeyEvent.KEYCODE_DPAD_RIGHT && key!=KeyEvent.KEYCODE_BACK)return false;
        if(event.getAction()!=KeyEvent.ACTION_DOWN || event.getRepeatCount()!=0)return true;
        int beforeRow=row,beforeColumn=row<3?selected[row]:-1;
        if(key==KeyEvent.KEYCODE_BACK){above.run();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_UP){if(row==0)above.run();else {row--;describe();changed.run();redraw();}}
        else if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(row<3){row++;describe();changed.run();redraw();}}
        else if(ok){if(row==3)home.onLibraryRequested();else if(!rows.get(row).isEmpty())open(rows.get(row).get(selected[row]));}
        else if(row<3 && !rows.get(row).isEmpty()) {
            int delta=key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1;
            selected[row]=Math.max(0,Math.min(rows.get(row).size()-1,selected[row]+delta));describe();redraw();
        }
        if(row!=beforeRow || (row<3 && selected[row]!=beforeColumn))animateFocus(beforeRow,beforeColumn);
        return true;
    }
    private void open(Item item) {
        if(row!=0){TitleLink.open(home,item.app,item.url);return;}
        try {
            Intent intent=Intent.parseUri(item.url,Intent.URI_INTENT_SCHEME);
            if(intent.getComponent()!=null && !item.app.equals(intent.getComponent().getPackageName()))throw new IllegalArgumentException();
            intent.setSelector(null);intent.setClipData(null);intent.setPackage(item.app);
            intent.setAction(Intent.ACTION_VIEW);intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            android.content.pm.ResolveInfo target=home.getPackageManager().resolveActivity(intent,0);
            if(target==null || AppLibraryDialog.unsafeEntry(target))throw new IllegalArgumentException();
            home.startActivity(intent);
        } catch(Exception unavailable){Toast.makeText(home,"Open this title inside its streaming app to resume.",Toast.LENGTH_SHORT).show();}
    }
    private String heading(int index){return index==0?"Continue watching":index==1?"Movie night":"TV shows";}
    private void describe() {
        if(row==3){setContentDescription("More apps");return;}
        List<Item> items=rows.get(row);
        setContentDescription(heading(row)+". "+items.size()+" titles. "+(items.isEmpty()?"No shared resume items yet.":items.get(selected[row]).name));
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();refresh();}
    @Override protected void onDetachedFromWindow(){closed=true;generation++;if(focusTween!=null)focusTween.cancel();super.onDetachedFromWindow();}
    @Override protected void onFocusChanged(boolean gain,int direction,android.graphics.Rect old) {
        super.onFocusChanged(gain,direction,old);
        animateFocus(gain?-1:row,row<3?selected[row]:-1);
    }
    private void animateFocus(int previousRow,int previousColumn) {
        if(focusTween!=null)focusTween.cancel();oldRow=previousRow;oldColumn=previousColumn;
        focusTween=ValueAnimator.ofFloat(0,1);focusTween.setDuration(140);focusTween.setInterpolator(new DecelerateInterpolator(1.5f));
        focusTween.addUpdateListener(a -> {focusMotion=(float)a.getAnimatedValue();redraw();});focusTween.start();
    }
    private float growth(int r,int column) {
        if(hasFocus() && row==r && (r==3 || selected[r]==column))return 1+.15f*focusMotion;
        if(oldRow==r && (r==3 || oldColumn==column))return 1+.15f*(1-focusMotion);
        return 1;
    }
    private void layoutSurfaces() {
        for(int i=0;i<getChildCount();i++) {
            LayoutParams params=(LayoutParams)getChildAt(i).getLayoutParams();
            int height=i<3?rowHeight(i):dp(72),y=top(i);
            if(params.height!=height || params.topMargin!=y) {
                params.height=height;params.topMargin=y;getChildAt(i).setLayoutParams(params);
            }
        }
    }
    private void redraw() {
        for(int i=0;i<getChildCount();i++)getChildAt(i).invalidate();
    }
    private final class RowSurface extends View {
        private final int index;
        RowSurface(int index) {super(home);this.index=index;setFocusable(false);}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int clip=canvas.save();
            canvas.clipRect(0,0,getWidth(),getHeight());
            canvas.translate(0,-top(index));
            if(index<3)drawRow(canvas,index);
            else {
                float scale=growth(3,-1);
                canvas.scale(scale,scale,getWidth()/2f,top(3)+dp(31));
                text(canvas,"More apps",getWidth()/2f,top(3)+dp(38),16,Color.WHITE,true);
            }
            canvas.restoreToCount(clip);
        }
    }
    private void drawRow(Canvas canvas,int index) {
        float top=top(index);
        if(canvas.quickReject(0,top,getWidth(),top+rowHeight(index),Canvas.EdgeType.AA))return;
        List<Item> items=rows.get(index);
        canvas.save();
        if(items.isEmpty()){float scale=growth(index,0);canvas.scale(scale,scale,dp(56),top+dp(33));}
        text(canvas,heading(index),dp(56),top+dp(33),20,Color.WHITE,false);canvas.restore();
        if(items.isEmpty()) {
            text(canvas,index==0?resumeMessage:"Your catalogue is loading…",dp(56),top+dp(79),14,0xFFB9B9BE,false);
            if(index==0)text(canvas,"You can still resume inside each streaming app.",dp(56),top+dp(105),12,0xFF8B8B93,false);
            return;
        }
        float step=dp(135),width=dp(108),height=dp(162);
        int visible=Math.max(1,(getWidth()-dp(112))/dp(135));
        int first=Math.min(Math.max(0,selected[index]-2),Math.max(0,items.size()-visible));
        float start=dp(56)-first*step;
        for(int i=0;i<items.size();i++) {
            float x=start+i*step;if(x+width<0 || x>getWidth())continue;
            Item item=items.get(i);boolean focused=hasFocus() && row==index && selected[index]==i;
            RectF target=posterBounds;target.set(x,top+dp(53),x+width,top+dp(53)+height);
            canvas.save();float scale=growth(index,i);canvas.scale(scale,scale,target.centerX(),target.centerY());
            if(item.art>=0)deck.drawPoster(canvas,item.art,target,1);
            else {paint.setColor(0xFF27272E);canvas.drawRoundRect(target,dp(5),dp(5),paint);
                paint.setTextSize(dp(11));
                String caption=TextUtils.ellipsize(item.name,paint,width-dp(12),TextUtils.TruncateAt.END).toString();
                text(canvas,caption,target.centerX(),target.centerY(),11,Color.WHITE,true);}
            canvas.restore();
            paint.setTextSize(dp(11));paint.setTypeface(android.graphics.Typeface.DEFAULT);
            String label=TextUtils.ellipsize(item.name,paint,width,TextUtils.TruncateAt.END).toString();
            text(canvas,label,x,top+dp(238),11,0xFFB9B9BE,false);
            if(index==0 && item.duration>0 && item.position>0) {
                paint.setColor(0xFF36363E);canvas.drawRect(x,top+dp(222),x+width,top+dp(224),paint);
                paint.setColor(Color.WHITE);canvas.drawRect(x,top+dp(222),x+width*Math.min(1,item.position/(float)item.duration),top+dp(224),paint);
            }
        }
    }
    private void text(Canvas canvas,String value,float x,float y,float size,int color,boolean centered) {
        paint.setColor(color);paint.setTextSize(dp(size));paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextAlign(centered?Paint.Align.CENTER:Paint.Align.LEFT);canvas.drawText(value,x,y,paint);
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
