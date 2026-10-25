package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Foreground-only idle fade. The wallpaper must be outside the supplied foreground view.
 * All calls belong on the main thread. No work is scheduled while inactive.
 *
 * Integration:
 * - Construct with homeForeground and the configured inactivity timeout in milliseconds.
 * - onResume: setActive(true); onPause/onDestroy: setActive(false).
 * - Also setActive(false) while More apps or another launcher popup is open, then reactivate only
 *   if the activity is resumed when the popup closes.
 * - At the very start of dispatchKeyEvent, call consumeWakeKey and return true if it consumes.
 *   Do this BEFORE super.dispatchKeyEvent, which can call onUserInteraction.
 * - onUserInteraction calls onInteraction. It reschedules an awake screen; it never wakes one.
 * - onNewIntent calls wake, covering a Home press handled by the system rather than this activity.
 * - Optional touch support: consumeWakeTouch before super.dispatchTouchEvent.
 *
 * This only receives events delivered to the activity; it does not filter global remote keys.
 */
final class QuietIdle {
    private static final long FADE_OUT_MS = 350;
    private static final long FADE_IN_MS = 180;

    private final View foreground;
    private final long timeoutMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable idle = this::fadeAway;
    private final DecelerateInterpolator ease = new DecelerateInterpolator(1.5f);
    private boolean active;
    private boolean asleep;
    private boolean consumingKey;
    private int wakeKey;
    private long wakeDownTime;
    private boolean wakeTouch;

    QuietIdle(View foreground, long timeoutMs) {
        if (foreground == null) throw new IllegalArgumentException("foreground is required");
        this.foreground = foreground;
        this.timeoutMs = Math.max(0L, timeoutMs);
    }

    /** Inactive means no pending idle callback, no animation, and fully visible foreground. */
    void setActive(boolean value) {
        if (active == value) return;
        active = value;
        handler.removeCallbacks(idle);
        foreground.animate().cancel();
        foreground.setAlpha(1f);
        asleep = false;
        consumingKey = false;
        wakeTouch = false;
        if (active) schedule();
    }

    /** Ordinary activity interaction must not reveal a sleeping screen before key dispatch decides. */
    void onInteraction() {
        if (active && !asleep) schedule();
    }

    /** Reveal without launching an app; safe both for Home intents and an intercepted wake gesture. */
    void wake() {
        handler.removeCallbacks(idle);
        foreground.animate().cancel();
        asleep = false;
        if (active && foreground.getAlpha() < 1f) {
            foreground.animate().alpha(1f).setDuration(FADE_IN_MS).setInterpolator(ease).start();
        } else {
            foreground.setAlpha(1f);
        }
        if (active) schedule();
    }

    /** Consume the wake press, repeats and release, so its release cannot activate the focused app. */
    boolean consumeWakeKey(KeyEvent event) {
        if (!active) return false;
        int action = event.getAction();
        int code = event.getKeyCode();
        if (consumingKey && code == wakeKey) {
            // A lost key-up must not swallow a later independent press of the same button.
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                    && event.getDownTime() != wakeDownTime) {
                consumingKey = false;
            } else {
                if (action == KeyEvent.ACTION_UP) consumingKey = false;
                schedule();
                return true;
            }
        }
        if (!asleep) return false;
        if (action == KeyEvent.ACTION_DOWN) {
            consumingKey = true;
            wakeKey = code;
            wakeDownTime = event.getDownTime();
        }
        wake();
        return true;
    }

    /** Optional: call only from dispatchTouchEvent, not hover/generic-motion dispatch. */
    boolean consumeWakeTouch(MotionEvent event) {
        if (!active) return false;
        int action = event.getActionMasked();
        if (wakeTouch) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) wakeTouch = false;
            schedule();
            return true;
        }
        if (!asleep) return false;
        wakeTouch = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL;
        wake();
        return true;
    }

    private void schedule() {
        handler.removeCallbacks(idle);
        if (active && timeoutMs > 0) handler.postDelayed(idle, timeoutMs);
    }

    private void fadeAway() {
        if (!active || timeoutMs == 0) return;
        asleep = true;
        foreground.animate().cancel();
        foreground.animate().alpha(0f).setDuration(FADE_OUT_MS).setInterpolator(ease).start();
    }
}
