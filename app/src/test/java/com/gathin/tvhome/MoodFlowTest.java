package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import static org.junit.Assert.*;

public class MoodFlowTest {
    private MoodFlow flow() {
        return new MoodFlow(Arrays.asList(Arrays.asList(1,2,3), Collections.singletonList(4)));
    }
    @Test public void skipsAdvanceOnlyAfterAnimationAndWrap() {
        MoodFlow f=flow(); assertTrue(f.swipe()); assertEquals(0,f.mood);
        assertFalse(f.swipe()); assertFalse(f.spin(new Random()));
        f.finishSwipe(); assertEquals(1,f.mood);
        assertTrue(f.swipe()); f.finishSwipe(); assertEquals(0,f.mood);
    }
    @Test public void noTitleCanLaunchBeforeLandingOrAfterCancellation() {
        MoodFlow f=flow(); assertEquals(-1,f.takeLaunch());
        assertTrue(f.spin(new Random(7))); assertEquals(-1,f.takeLaunch());
        f.land(); f.cancel(); assertEquals(-1,f.takeLaunch());
        f.land(); assertEquals(MoodFlow.Phase.DECK,f.phase);
    }
    @Test public void countdownRerollAvoidsImmediateRepeatAndOnlyLaunchesOnce() {
        MoodFlow f=flow(); Random r=new Random(2);
        for(int i=0;i<100;i++) {
            int previous=f.previous;
            assertTrue(f.spin(r)); assertNotEquals(previous,f.chosen);
            assertTrue(f.pool().contains(f.chosen)); f.land();
        }
        int picked=f.chosen; assertEquals(picked,f.takeLaunch()); assertEquals(-1,f.takeLaunch());
    }
    @Test public void oneTitleStillWorksAndEmptyCatalogueCannotSpin() {
        MoodFlow f=flow(); f.swipe();f.finishSwipe(); assertTrue(f.spin(new Random()));
        f.land();assertEquals(4,f.takeLaunch());assertTrue(f.spin(new Random()));
        MoodFlow empty=new MoodFlow(Collections.emptyList());
        assertFalse(empty.spin(new Random()));assertFalse(empty.swipe());
    }
    @Test public void manualBrowsingVisitsEveryTitleAndWrapsWithoutLaunching() {
        MoodFlow f=flow();assertTrue(f.browse());assertEquals(1,f.chosen);
        assertTrue(f.step(-1));assertEquals(3,f.chosen);
        assertTrue(f.step(1));assertEquals(1,f.chosen);
        assertTrue(f.step(1));assertEquals(2,f.chosen);
        assertEquals(MoodFlow.Phase.BROWSE,f.phase);
        int picked=f.chosen;assertEquals(picked,f.takeLaunch());assertEquals(-1,f.takeLaunch());
    }
    @Test public void landedChoiceCanBecomeManualAndCancelCannotLaunchIt() {
        MoodFlow f=flow();assertTrue(f.spin(new Random(4)));
        assertFalse(f.browse());assertFalse(f.step(1));
        int picked=f.chosen;f.land();assertTrue(f.browse());assertEquals(picked,f.chosen);
        f.cancel();assertFalse(f.step(1));assertEquals(-1,f.takeLaunch());
    }
}
