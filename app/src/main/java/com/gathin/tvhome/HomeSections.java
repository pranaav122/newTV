package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The dock, favourites and mood deck share one already-attached, continuous Home surface. */
final class HomeSections {
    private final MainActivity home;
    private final SectionScroll scroll;
    private final ViewGroup apps;
    private final LinearLayout favourites;
    private final FavouritesShelf shelf;
    private final LinearLayout moods;
    private final MoodDeck deck;
    private final HomeTitleRows titles;
    private final CinemaHome.SceneBackground scene = new CinemaHome.SceneBackground();
    private int section;
    private ValueAnimator travel;

    HomeSections(MainActivity home, FrameLayout root, ViewGroup apps) {
        this.home = home;
        this.apps = apps;
        int index = root.indexOfChild(apps);
        root.removeView(apps);
        scroll = new SectionScroll(home);
        scroll.setFocusable(false);
        LinearLayout sections = new LinearLayout(home);
        int height = home.getResources().getDisplayMetrics().heightPixels;
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setClipChildren(false);
        scroll.addView(sections, new FrameLayout.LayoutParams(-1, height*3));
        sections.addView(apps, new LinearLayout.LayoutParams(-1, height));
        favourites = new LinearLayout(home);
        favourites.setOrientation(LinearLayout.VERTICAL);
        favourites.setPadding(dp(56), dp(28), dp(56), dp(20));
        favourites.setClipChildren(false);
        favourites.setBackground(scene);
        favourites.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        sections.addView(favourites, new LinearLayout.LayoutParams(-1, height));
        shelf = new FavouritesShelf(home);
        shelf.sceneListener(scene::show);
        favourites.addView(shelf, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = new TextView(home);
        hint.setText("↓   Don't have time to browse?"); hint.setTextColor(0xFFB9B9BE);
        hint.setTextSize(12); hint.setGravity(Gravity.CENTER);
        favourites.addView(hint,new LinearLayout.LayoutParams(-1,dp(48)));
        moods = new LinearLayout(home);
        moods.setOrientation(LinearLayout.VERTICAL);
        moods.setBackgroundColor(android.graphics.Color.BLACK);
        moods.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        sections.addView(moods,new LinearLayout.LayoutParams(-1,height));
        deck = new MoodDeck(home);
        moods.addView(deck,new LinearLayout.LayoutParams(-1,0,1));
        TextView next = new TextView(home);
        next.setText("↓  Continue watching");next.setTextColor(0xFFB9B9BE);next.setTextSize(13);next.setGravity(Gravity.CENTER);
        moods.addView(next,new LinearLayout.LayoutParams(-1,dp(48)));
        titles=new HomeTitleRows(home,deck,this::moods,() -> {
            resizeContent();if(section==3)move(true);
        });
        sections.addView(titles,new LinearLayout.LayoutParams(-1,titles.contentHeight()));
        sections.getLayoutParams().height=height*3+titles.contentHeight();
        shelf.navigate(() -> home(true), this::moods);
        deck.navigate(this::favourites,this::lower);
        root.addView(scroll, index, new FrameLayout.LayoutParams(-1, -1));
    }
    View foreground() { return scroll; }
    boolean isBelow() { return section>0; }
    void down() {
        if(section!=0) return;
        favourites();
    }
    private void favourites() {
        section=1;
        titles.setFocusable(false);
        favourites.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        shelf.requestFocus();
        apps.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        moods.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        move(true);
    }
    void moods() {
        section=2;
        titles.setFocusable(false);
        moods.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        deck.requestFocus();
        apps.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        favourites.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        move(true);
    }
    void home(boolean animate) {
        if(section==0) return;
        section=0;
        titles.setFocusable(false);
        apps.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        home.onLibraryUp();
        favourites.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        moods.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        move(animate);
    }
    private void lower() {
        section=3;titles.focusFirst();
        apps.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        favourites.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        moods.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        move(true);
    }
    void resume() {titles.refresh();}
    void back() { if(section==3)moods();else if(section==2) favourites(); else home(true); }
    void restoreFocus() {
        if(section==1 && !favourites.hasFocus()) shelf.requestFocus();
        if(section==2 && !moods.hasFocus()) deck.requestFocus();
        if(section==3 && !titles.hasFocus())titles.requestFocus();
    }
    void pause() { deck.pause(); move(false); }
    void close() { if(travel!=null) travel.cancel(); scene.stop(); deck.close(); }
    private void move(boolean animate) {
        if(travel!=null) travel.cancel();
        int height=scroll.getHeight();
        int destination=section*height;
        if(section==3)destination=Math.min(height*2+titles.contentHeight(),height*3+titles.selectedTop());
        if(!animate) { scroll.position(destination); return; }
        travel = ValueAnimator.ofInt(scroll.getScrollY(), destination);
        travel.setDuration(300);
        travel.setInterpolator(new DecelerateInterpolator(1.5f));
        travel.addUpdateListener(a -> scroll.position((int)a.getAnimatedValue()));
        travel.start();
    }
    private TextView button(String label, Runnable action) {
        TextView view = new TextView(home);
        view.setText(label); view.setTextColor(android.graphics.Color.WHITE); view.setTextSize(15);
        view.setGravity(Gravity.CENTER); view.setFocusable(true); view.setPadding(dp(12),0,dp(12),0);
        view.setOnClickListener(v -> action.run());
        view.setOnFocusChangeListener((v,on) -> v.animate().scaleX(on?1.15f:1).scaleY(on?1.15f:1)
                .setDuration(140).setInterpolator(new DecelerateInterpolator(1.5f)).start());
        return view;
    }
    private int dp(int value) { return Math.round(value*home.getResources().getDisplayMetrics().density); }
    private void resizeContent() {
        if(titles==null || scroll.getChildCount()==0)return;
        int height=scroll.getHeight();if(height<=0)height=home.getResources().getDisplayMetrics().heightPixels;
        View content=scroll.getChildAt(0);
        int wanted=height*3+titles.contentHeight();
        if(content.getLayoutParams().height!=wanted) {
            content.getLayoutParams().height=wanted;content.requestLayout();
        }
    }

    private final class SectionScroll extends FrameLayout {
        SectionScroll(MainActivity context) { super(context); }
        // D-pad focus is assigned first; only our bounded scroll animation moves the viewport.
        @Override public void scrollTo(int x, int y) { }
        void position(int y) { super.scrollTo(0,y); }
        @Override public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) { return false; }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
            super.onSizeChanged(w,h,oldw,oldh);
            if(h<=0 || h==oldh) return;
            if(apps.getLayoutParams().height==h && favourites.getLayoutParams().height==h && moods.getLayoutParams().height==h) return;
            apps.getLayoutParams().height=h;
            favourites.getLayoutParams().height=h;
            moods.getLayoutParams().height=h;
            getChildAt(0).getLayoutParams().height=h*3+titles.contentHeight();
            getChildAt(0).requestLayout();
            apps.requestLayout(); favourites.requestLayout(); moods.requestLayout();
            post(() -> position(section*h));
        }
    }
}
