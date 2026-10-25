package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class MoodCountdownTest {
    @Test public void grantsThreeRealSecondsEvenWhenFramesAreSkipped() {
        MoodCountdown c=new MoodCountdown();c.start(20000);
        assertEquals(3,c.seconds(20000));assertEquals(2,c.seconds(21000));assertEquals(1,c.seconds(22001));
        assertFalse(c.expired(22999));assertTrue(c.expired(23000));assertTrue(c.expired(25000));
        assertEquals(1,c.progress(25000),0);
    }
    @Test public void cancellingAndRerollingDiscardTheOldDeadline() {
        MoodCountdown c=new MoodCountdown();c.start(0);c.cancel();
        assertFalse(c.active());assertFalse(c.expired(60000));
        c.start(60000);assertFalse(c.expired(61000));assertEquals(3,c.seconds(60000));
        assertTrue(c.expired(63000));
    }
}
