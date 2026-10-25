package com.gathin.tvhome;

import org.junit.Test;
import java.util.HashSet;
import java.util.Random;
import static org.junit.Assert.*;

public class CinemaDataTest {
    @Test public void everyMoodHasTitlesAndNamesAreUnique() {
        HashSet<String> names = new HashSet<>();
        for (CinemaData.Title title : CinemaData.TITLES) assertTrue(names.add(title.name));
        for (int mood = 0; mood < CinemaData.MOODS.length; mood++) assertFalse(CinemaData.matching(mood).isEmpty());
        assertEquals(CinemaData.TITLES.length, CinemaData.matching(0).size());
    }
    @Test public void picksStayInMoodAndDoNotRepeatImmediately() {
        Random random = new Random(4);
        for (int mood = 0; mood < CinemaData.MOODS.length; mood++) {
            int previous = -1;
            for (int n = 0; n < 100; n++) {
                int chosen = CinemaData.pick(mood, previous, random);
                assertTrue(CinemaData.matching(mood).contains(chosen));
                if (CinemaData.matching(mood).size() > 1) assertNotEquals(previous, chosen);
                previous = chosen;
            }
        }
    }
    @Test public void unknownMoodReturnsNoTitle() { assertEquals(-1, CinemaData.pick(99, -1, new Random(0))); }
}
