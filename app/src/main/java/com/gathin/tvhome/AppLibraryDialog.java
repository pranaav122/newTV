package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Personal-fork More apps grid. Its transparent window leaves Home's wallpaper visible. */
final class AppLibraryDialog {
    private static final int COLUMNS = 6;
    private static final float FOCUS_SCALE = 1.15f;
    private static final int FOCUS_MS = 140;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();
    // Resource loading is confined to LOADER. Retain only a small label cache between openings.
    private static final Map<String, Known> KNOWN = new LinkedHashMap<String, Known>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Known> eldest) {
            return size() > 128;
        }
    };
    private static final Pattern TEST_SEGMENT = Pattern.compile("(^|[._])(?:test|testmenu|testmode|at)(?:[._]|$)");

    private static final class Known {
        final long updated;
        final String label;
        Known(long updated, String label) { this.updated = updated; this.label = label; }
    }

    private static final class Entry {
        final ResolveInfo app;
        final String pkg, label, key;
        final boolean television;
        Entry(ResolveInfo app, String label, long updated, boolean television) {
            this.app = app;
            pkg = app.activityInfo.packageName;
            this.label = label;
            this.television = television;
            key = pkg + '/' + app.activityInfo.name + ':' + updated;
        }
    }

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LibraryLoads loads = new LibraryLoads();
    private final Predicate<String> onHome;
    private final Runnable dismissed;
    private final boolean animations;
    private final float density;
    private final LinearLayout panel;
    private final RecyclerView grid;
    private final TextView empty;
    private final GridAdapter adapter = new GridAdapter();
    private final List<Entry> apps = new ArrayList<>();
    private final Map<String, Integer> priority = new HashMap<>();
    // All artwork-cache operations occur on main. Rasterized images are at most one cell's size.
    private final LruCache<String, Drawable.ConstantState> artwork = new LruCache<>(24);
    private final Set<String> loadingArtwork = new HashSet<>();
    private final Set<String> failedArtwork = new HashSet<>();
    private final Set<String> boundArtwork = ConcurrentHashMap.newKeySet();
    private final int artWidth, artHeight;
    private String selectedPackage;
    private int selectedPosition;
    private int focusGeneration;
    private volatile boolean closed;
    final PopupWindow popup;

    // Keep the original constructor API. This picker opens apps directly; selection/edit modes are unused.
    AppLibraryDialog(Activity activity, boolean animations, boolean reducedMotion, boolean selectionMode,
                     Predicate<String> onHome, Runnable changed, Runnable dismissed) {
        this.activity = activity;
        this.animations = animations;
        this.onHome = onHome;
        this.dismissed = dismissed;
        density = activity.getResources().getDisplayMetrics().density;
        String[] first = activity.getResources().getStringArray(R.array.more_apps_first);
        for (int i = 0; i < first.length; i++) if (!priority.containsKey(first[i])) priority.put(first[i], i);
        int width = activity.getWindow().getDecorView().getWidth();
        if (width <= 0) width = activity.getResources().getDisplayMetrics().widthPixels;
        artWidth = Math.max(dp(32), (width - dp(112)) / COLUMNS - dp(24));
        artHeight = Math.max(dp(18), artWidth * 9 / 16);

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(56), dp(32), dp(56), dp(24));
        panel.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x88000000, 0xBB000000}));
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setFocusableInTouchMode(true);
        TextView heading = text("More apps", 26, Color.WHITE);
        panel.addView(heading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView hint = text("Back returns to Home", 12, 0xB3FFFFFF);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(6);
        hintParams.bottomMargin = dp(20);
        panel.addView(hint, hintParams);

        FrameLayout body = new FrameLayout(activity);
        body.setClipChildren(true);
        body.setClipToPadding(true);
        panel.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        grid = new RecyclerView(activity);
        grid.setLayoutManager(new GridLayoutManager(activity, COLUMNS));
        grid.setAdapter(adapter);
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.setItemViewCacheSize(COLUMNS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(4), 0, dp(8));
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        body.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        empty = text("No other apps", 16, 0xB3FFFFFF);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        body.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        popup = new PopupWindow(panel, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(false);
        popup.setClippingEnabled(true);
        popup.setElevation(0f);
        popup.setAnimationStyle(0);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        // No FLAG_DIM_BEHIND: the unchanged wallpaper is the grid's background.
        popup.setOnDismissListener(this::didClose);
        panel.setOnKeyListener((v, key, event) -> back(key, event));
    }

    private int dp(float value) { return Math.round(value * density); }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setFocusable(false);
        return view;
    }

    void show() {
        if (closed) return;
        if (activity.isFinishing() || activity.isDestroyed()) { didClose(); return; }
        if (!popup.isShowing()) {
            empty.setText("Loading apps…");
            empty.setVisibility(View.VISIBLE);
            grid.setVisibility(View.GONE);
            try {
                // Own Back immediately, including while package discovery is still queued.
                popup.showAtLocation(activity.getWindow().getDecorView(), Gravity.TOP | Gravity.START, 0, 0);
                panel.requestFocus();
            } catch (RuntimeException unavailable) {
                didClose();
                return;
            }
        }
        load(true);
    }
    void refresh() { if (!closed) load(false); }

    void close() {
        if (popup.isShowing()) popup.dismiss();
        else didClose();
    }

    private void didClose() {
        if (closed) return;
        closed = true;
        focusGeneration++;
        grid.setAdapter(null); // release bound artwork along with this opening's small cache
        artwork.evictAll();
        loadingArtwork.clear();
        failedArtwork.clear();
        boundArtwork.clear();
        dismissed.run();
    }

    private boolean back(int key, KeyEvent event) {
        if (key != KeyEvent.KEYCODE_BACK) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) close();
        return true;
    }

    /** Exclude service/factory entry points rather than exposing a destructive test menu as an app. */
    static boolean unsafeEntry(ResolveInfo app) {
        String identity = (app.activityInfo.packageName + '.' + app.activityInfo.name).toLowerCase(Locale.ROOT);
        return identity.contains("factory") || identity.contains("production")
                || identity.contains("facmenu") || identity.contains("fac_menu")
                || identity.contains("agingtest") || identity.contains("burnin")
                || identity.contains("autotest") || TEST_SEGMENT.matcher(identity).find();
    }

    private void load(boolean reveal) {
        final int generation = loads.start(reveal);
        PackageManager pm = activity.getPackageManager();
        String self = activity.getPackageName();
        LOADER.execute(() -> {
            if (closed) return;
            try {
            Map<String, ResolveInfo> unique = new LinkedHashMap<>();
            Set<String> television = new HashSet<>();
            // A package with both entry categories uses its TV entry point.
            for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                for (ResolveInfo app : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                    if (app.activityInfo == null || !app.activityInfo.exported || unsafeEntry(app)) continue;
                    String pkg = app.activityInfo.packageName;
                    if (pkg.equals(self)) continue;
                    if (Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category)) television.add(pkg);
                    if (!unique.containsKey(pkg)) unique.put(pkg, app);
                }
            }
            List<Entry> found = new ArrayList<>();
            for (ResolveInfo app : unique.values()) {
                if (closed) return;
                String pkg = app.activityInfo.packageName;
                long updated = 0;
                try { PackageInfo info = pm.getPackageInfo(pkg, 0); updated = info.lastUpdateTime; }
                catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { /* stale package */ }
                Known known = KNOWN.get(pkg);
                if (known == null || known.updated != updated) {
                    String label;
                    try { label = Cards.cleanLabel(String.valueOf(app.loadLabel(pm)), pkg); }
                    catch (RuntimeException unavailable) { label = pkg; }
                    known = new Known(updated, label);
                    KNOWN.put(pkg, known);
                }
                found.add(new Entry(app, known.label, updated, television.contains(pkg)));
            }
            main.post(() -> {
                if (closed || !loads.isLatest(generation) || activity.isFinishing() || activity.isDestroyed()) return;
                fill(found); // onHome belongs to MainActivity's adapter, so call it only on main.
                loads.takeReveal();
                focusPosition(selectedPosition);
            });
            } catch (RuntimeException unavailable) {
                main.post(() -> showLoadError(generation));
            }
        });
    }

    private void showLoadError(int generation) {
        if (closed || !loads.isLatest(generation) || activity.isFinishing() || activity.isDestroyed()) return;
        loads.takeReveal();
        if (apps.isEmpty()) {
            empty.setText("Could not load apps. Press Back to return.");
            empty.setVisibility(View.VISIBLE);
            grid.setVisibility(View.GONE);
            panel.requestFocus();
        } else {
            // A failed refresh does not erase an already usable catalogue.
            Toast.makeText(activity, "Could not refresh apps", Toast.LENGTH_SHORT).show();
        }
    }

    private void fill(List<Entry> found) {
        apps.clear();
        for (Entry entry : found) if (!onHome.test(entry.pkg)) apps.add(entry);
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> {
            int rank = Integer.compare(priority.getOrDefault(a.pkg, Integer.MAX_VALUE),
                    priority.getOrDefault(b.pkg, Integer.MAX_VALUE));
            if (rank != 0) return rank;
            int name = collator.compare(a.label, b.label);
            return name != 0 ? name : a.pkg.compareTo(b.pkg);
        });
        int previous = selectedPosition;
        selectedPosition = 0;
        boolean retained = false;
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).pkg.equals(selectedPackage)) { selectedPosition = i; retained = true; break; }
        }
        if (!retained && !apps.isEmpty()) selectedPosition = Math.min(previous, apps.size() - 1);
        adapter.notifyDataSetChanged();
        empty.setText("No other apps");
        empty.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
        grid.setVisibility(apps.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void focusPosition(int requested) {
        final int generation = ++focusGeneration;
        if (apps.isEmpty()) { panel.requestFocus(); return; }
        final int target = Math.max(0, Math.min(requested, apps.size() - 1));
        selectedPosition = target;
        selectedPackage = apps.get(target).pkg;
        grid.scrollToPosition(target);
        grid.postOnAnimation(new Runnable() {
            int frames;
            @Override public void run() {
                if (closed || generation != focusGeneration) return;
                RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(target);
                if (holder != null) { holder.itemView.requestFocus(); return; }
                if (++frames < 10) grid.postOnAnimation(this);
            }
        });
    }

    private boolean navigate(Holder holder, int key, KeyEvent event) {
        if (back(key, event)) return true;
        if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT
                && key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION || apps.isEmpty()) return true;
        int target = position;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT && position % COLUMNS > 0) target--;
        if (key == KeyEvent.KEYCODE_DPAD_RIGHT && position % COLUMNS < COLUMNS - 1 && position + 1 < apps.size()) target++;
        if (key == KeyEvent.KEYCODE_DPAD_UP && position >= COLUMNS) target -= COLUMNS;
        if (key == KeyEvent.KEYCODE_DPAD_DOWN && position / COLUMNS < (apps.size() - 1) / COLUMNS)
            target = Math.min(position + COLUMNS, apps.size() - 1);
        if (target != position) focusPosition(target);
        return true; // edges stay on their row instead of wrapping or escaping into hidden Home.
    }

    private void open(Entry entry) {
        if (unsafeEntry(entry.app)) return;
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(
                new ComponentName(entry.pkg, entry.app.activityInfo.name));
        if (entry.television) intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            activity.startActivity(intent);
            close(); // returning from the app returns to Home's More apps button via dismissed.
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, entry.label), Toast.LENGTH_SHORT).show();
        }
    }

    private void requestArtwork(Entry entry) {
        if (closed || failedArtwork.contains(entry.key) || !loadingArtwork.add(entry.key)) return;
        LOADER.execute(() -> {
            if (closed) return;
            if (!boundArtwork.contains(entry.key)) {
                // A fast scroll can recycle a bound cell before its queued decode starts.
                main.post(() -> {
                    loadingArtwork.remove(entry.key);
                    if (!closed && boundArtwork.contains(entry.key)) requestArtwork(entry);
                });
                return;
            }
            Drawable.ConstantState state = null;
            try {
                Drawable picture = loadArtwork(entry.app);
                if (picture != null) state = picture.getConstantState();
            } catch (RuntimeException | OutOfMemoryError unavailable) { /* keep the app's visible name */ }
            final Drawable.ConstantState loaded = state;
            main.post(() -> {
                if (closed) return;
                loadingArtwork.remove(entry.key);
                if (loaded == null) failedArtwork.add(entry.key);
                else artwork.put(entry.key, loaded);
                for (int i = 0; i < grid.getChildCount(); i++) {
                    RecyclerView.ViewHolder bound = grid.getChildViewHolder(grid.getChildAt(i));
                    if (bound instanceof Holder && entry.key.equals(((Holder) bound).boundKey)) {
                        ((Holder) bound).image.setImageDrawable(loaded == null ? null
                                : loaded.newDrawable(activity.getResources()));
                    }
                }
            });
        });
    }

    private Drawable loadArtwork(ResolveInfo app) {
        PackageManager pm = activity.getPackageManager();
        Drawable source = null;
        int banner = app.activityInfo.banner;
        if (banner == 0) banner = app.activityInfo.applicationInfo.banner;
        if (banner != 0) {
            try {
                Resources res = pm.getResourcesForApplication(app.activityInfo.applicationInfo);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeResource(res, banner, bounds);
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inScaled = false;
                    options.inSampleSize = 1;
                    while (bounds.outWidth / (options.inSampleSize * 2) >= artWidth
                            && bounds.outHeight / (options.inSampleSize * 2) >= artHeight) options.inSampleSize *= 2;
                    Bitmap decoded = BitmapFactory.decodeResource(res, banner, options);
                    if (decoded != null) source = new BitmapDrawable(res, decoded);
                } else source = res.getDrawable(banner, null);
            } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { /* icon fallback */ }
        }
        if (source == null) source = app.loadIcon(pm);
        if (source == null) return null;
        source = source.mutate();
        int w = source.getIntrinsicWidth(), h = source.getIntrinsicHeight();
        if (w <= 0 || h <= 0) { w = artHeight; h = artHeight; }
        float scale = Math.min((float) artWidth / w, (float) artHeight / h);
        int fittedW = Math.max(1, Math.round(w * scale)), fittedH = Math.max(1, Math.round(h * scale));
        Bitmap image = Bitmap.createBitmap(artWidth, artHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        int left = (artWidth - fittedW) / 2, top = (artHeight - fittedH) / 2;
        source.setBounds(left, top, left + fittedW, top + fittedH);
        source.draw(canvas);
        return new BitmapDrawable(activity.getResources(), image);
    }

    private final class GridAdapter extends RecyclerView.Adapter<Holder> {
        GridAdapter() { setHasStableIds(true); }
        @Override public int getItemCount() { return apps.size(); }
        @Override public long getItemId(int position) {
            // Component identity remains stable across APK updates and catalogue refreshes.
            String identity = apps.get(position).pkg + '/' + apps.get(position).app.activityInfo.name;
            long hash = 0xcbf29ce484222325L;
            for (int i = 0; i < identity.length(); i++) { hash ^= identity.charAt(i); hash *= 0x100000001b3L; }
            return hash;
        }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder();
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            Entry entry = apps.get(position);
            if (holder.boundKey != null) boundArtwork.remove(holder.boundKey);
            holder.boundKey = entry.key;
            boundArtwork.add(entry.key);
            holder.label.setText(entry.label);
            holder.itemView.setContentDescription(entry.label);
            holder.itemView.animate().cancel();
            float scale = holder.itemView.hasFocus() ? FOCUS_SCALE : 1f;
            holder.itemView.setScaleX(scale);
            holder.itemView.setScaleY(scale);
            Drawable.ConstantState state = artwork.get(entry.key);
            holder.image.setImageDrawable(state == null ? null : state.newDrawable(activity.getResources()));
            if (state == null) requestArtwork(entry);
        }
        @Override public void onViewRecycled(@NonNull Holder holder) {
            holder.itemView.animate().cancel();
            holder.itemView.setScaleX(1f);
            holder.itemView.setScaleY(1f);
            holder.image.setImageDrawable(null);
            if (holder.boundKey != null) boundArtwork.remove(holder.boundKey);
            holder.boundKey = null;
        }
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView label;
        String boundKey;
        Holder() {
            super(new LinearLayout(activity));
            LinearLayout cell = (LinearLayout) itemView;
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(12), dp(10), dp(12), dp(14));
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    artHeight + dp(52)));
            cell.setClipChildren(false);
            cell.setClipToPadding(false);
            cell.setFocusable(true);
            cell.setClickable(true);
            cell.setBackground(null);
            image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, artHeight));
            label = text("", 12, Color.WHITE);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            nameParams.topMargin = dp(9);
            cell.addView(label, nameParams);
            cell.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) open(apps.get(position));
            });
            cell.setOnKeyListener((v, key, event) -> navigate(this, key, event));
            cell.setOnFocusChangeListener((v, focused) -> {
                v.animate().cancel();
                float scale = focused ? FOCUS_SCALE : 1f;
                if (animations) v.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_MS)
                        .setInterpolator(new DecelerateInterpolator(1.5f)).start();
                else { v.setScaleX(scale); v.setScaleY(scale); }
                int position = getBindingAdapterPosition();
                if (focused && position != RecyclerView.NO_POSITION) {
                    selectedPosition = position;
                    selectedPackage = apps.get(position).pkg;
                }
            });
        }
    }
}
