package com.gathin.tvhome;

import android.service.dreams.DreamService;
import android.widget.FrameLayout;

/**
 * The existing system screensaver, showing Home's static local wallpaper and no UI.
 * The personal theme keeps wallpaper_rotation_minutes=0 and wallpaper_captions=false.
 * DreamService consumes the normal wake interaction with interactive mode off.
 */
public class ClockDream extends DreamService {
    private WallpaperPlayer wallpaper;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);

        Config config = Config.load(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(config.backgroundColor);
        setContentView(root);
        // Same crop, dimming, local image choice and bitmap path as Home. The constructor decodes
        // the first image once; with a single static wallpaper, start schedules no decoding work.
        wallpaper = new WallpaperPlayer(this, root, config);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (wallpaper != null) wallpaper.start();
    }

    @Override
    public void onDreamingStopped() {
        if (wallpaper != null) wallpaper.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (wallpaper != null) {
            wallpaper.close();
            wallpaper = null;
        }
        super.onDetachedFromWindow();
    }
}
