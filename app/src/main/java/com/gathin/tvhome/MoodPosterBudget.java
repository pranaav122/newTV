package com.gathin.tvhome;

/** Share the existing cache across the entire snapshot without evicting its first posters. */
final class MoodPosterBudget {
    private MoodPosterBudget() {}

    static int limit(int width, int height, String mime, int count, int cacheBytes) {
        if (width<=0 || height<=0) return 1;
        double ratio=Math.min(width,height)/(double)Math.max(width,height);
        int bytesPerPixel="image/jpeg".equals(mime) ? 2 : 4;
        // Leave room for rounded dimensions, row alignment and decoder allocation overhead.
        double allowance=cacheBytes*.85/Math.max(1,count);
        return Math.max(1,Math.min(360,(int)Math.sqrt(allowance/(ratio*bytesPerPixel))));
    }
}
