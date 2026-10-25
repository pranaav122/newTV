package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Small local vector marks, drawn without a card or an app-banner background. */
final class QuietLogo extends Drawable {
    private final Drawable vector;
    private final int special;
    private final int focusColor;
    private boolean focused;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    static Drawable forPackage(Context context, String pkg) {
        int resource, color;
        switch (pkg) {
            case "com.netflix.ninja": resource = R.drawable.logo_netflix; color = 0xFFE50914; break;
            case "com.google.android.youtube.tv": resource = R.drawable.logo_youtube; color = 0xFFFF0033; break;
            case "com.amazon.amazonvideo.livingroom": return new QuietLogo(null, 3, 0xFF00A8E1);
            case "com.spotify.tv.android": resource = R.drawable.logo_spotify; color = 0xFF1ED760; break;
            // A blue selection accent keeps the normally monochrome Apple mark distinguishable too.
            case "com.apple.atve.androidtv.appletv": resource = R.drawable.logo_appletv; color = 0xFF55B9FF; break;
            case "in.startv.hotstar": return new QuietLogo(null, 1, 0xFFE600D6);
            case "com.suntv.sunnxt": return new QuietLogo(null, 2, 0xFFF234AD);
            default: return null;
        }
        return new QuietLogo(context.getDrawable(resource), 0, color);
    }

    private QuietLogo(Drawable vector, int special, int focusColor) {
        this.vector = vector == null ? null : vector.mutate();
        this.special = special;
        this.focusColor = focusColor;
        paint.setColor(Color.WHITE);
    }

    void focus(boolean selected) {
        if (focused == selected) return;
        focused = selected;
        int color = selected ? focusColor : Color.WHITE;
        paint.setColor(color);
        if (vector != null) vector.setTint(color);
        invalidateSelf();
    }

    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        float width = getBounds().width(), height = getBounds().height();
        float side = Math.min(width * .48f, height * .72f);
        float x = (width - side) / 2, y = (height - side) / 2;
        if (vector != null) {
            vector.setBounds(Math.round(x), Math.round(y), Math.round(x + side), Math.round(y + side));
            vector.draw(canvas);
        } else if (special == 1) {
            Path star = new Path();
            for (int i = 0; i < 16; i++) {
                double angle = Math.PI * i / 8 - Math.PI / 2;
                float radius = side * (i % 2 == 0 ? .5f : .19f);
                float px = width / 2 + (float) Math.cos(angle) * radius;
                float py = height / 2 + (float) Math.sin(angle) * radius;
                if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
            }
            star.close();
            canvas.drawPath(star, paint);
        } else if (special == 3) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(side * .075f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            Path smile = new Path();
            smile.moveTo(width / 2 - side * .49f, height / 2);
            smile.quadTo(width / 2, height / 2 + side * .43f, width / 2 + side * .48f, height / 2 - side * .05f);
            canvas.drawPath(smile, paint);
            paint.setStyle(Paint.Style.FILL);
            Path arrow = new Path();
            arrow.moveTo(width / 2 + side * .23f, height / 2 - side * .12f);
            arrow.lineTo(width / 2 + side * .54f, height / 2 - side * .13f);
            arrow.lineTo(width / 2 + side * .46f, height / 2 + side * .16f);
            arrow.close(); canvas.drawPath(arrow, paint);
        } else {
            paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(side * .40f);
            canvas.drawText("SUN", width / 2, height / 2 - side * .03f, paint);
            paint.setTextSize(side * .53f);
            canvas.drawText("NXT", width / 2, height / 2 + side * .45f, paint);
        }
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); if (vector != null) vector.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
