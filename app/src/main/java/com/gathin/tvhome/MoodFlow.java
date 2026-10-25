package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Local selection state. A cancelled or hidden deck can never produce a pending launch. */
final class MoodFlow {
    enum Phase { DECK, SWIPE, REEL, COUNTDOWN, BROWSE }
    final List<List<Integer>> pools;
    int mood, chosen = -1, previous = -1;
    Phase phase = Phase.DECK;

    MoodFlow(List<List<Integer>> pools) { this.pools = pools; }
    List<Integer> pool() { return pools.isEmpty() ? new ArrayList<>() : pools.get(mood); }
    boolean swipe() {
        if (phase != Phase.DECK || pools.size() < 2) return false;
        phase = Phase.SWIPE; return true;
    }
    void finishSwipe() {
        if (phase == Phase.SWIPE) { mood = (mood + 1) % pools.size(); phase = Phase.DECK; }
    }
    boolean spin(Random random) {
        if (pool().isEmpty() || (phase != Phase.DECK && phase != Phase.COUNTDOWN && phase != Phase.BROWSE)) return false;
        List<Integer> candidates = new ArrayList<>(pool());
        if (candidates.size() > 1) candidates.remove(Integer.valueOf(previous));
        chosen = candidates.get(random.nextInt(candidates.size()));
        previous = chosen; phase = Phase.REEL; return true;
    }
    void land() { if (phase == Phase.REEL) phase = Phase.COUNTDOWN; }
    boolean browse() {
        if(pool().isEmpty() || (phase!=Phase.DECK && phase!=Phase.COUNTDOWN)) return false;
        if(!pool().contains(chosen)) chosen=pool().get(0);
        phase=Phase.BROWSE; return true;
    }
    boolean step(int direction) {
        if(phase!=Phase.BROWSE || pool().isEmpty()) return false;
        int index=Math.floorMod(pool().indexOf(chosen)+direction,pool().size());
        chosen=pool().get(index); previous=chosen; return true;
    }
    int takeLaunch() {
        if (phase != Phase.COUNTDOWN && phase != Phase.BROWSE) return -1;
        int result = chosen; cancel(); return result;
    }
    void cancel() { phase = Phase.DECK; chosen = -1; }
}
