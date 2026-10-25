package com.gathin.tvhome;

/** Real elapsed time, independent of Android's animation speed or dropped frames. */
final class MoodCountdown {
    private long deadline=-1;
    void start(long now) { deadline=now+3000; }
    void cancel() { deadline=-1; }
    boolean active() { return deadline>=0; }
    boolean expired(long now) { return active() && now>=deadline; }
    float progress(long now) { return active() ? Math.max(0,Math.min(1,1-(deadline-now)/3000f)):0; }
    int seconds(long now) { return active() ? Math.max(0,(int)Math.ceil((deadline-now)/1000.0)):0; }
}
