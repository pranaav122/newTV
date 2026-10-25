package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URI;

public class FavouriteTitlesTest {
    @Test public void navigationStaysInEachRowAndReachesEveryCharacter() {
        assertEquals(0,FavouriteTitles.move(0,-1,0));
        assertEquals(2,FavouriteTitles.move(2,1,0));
        assertEquals(3,FavouriteTitles.move(3,-1,0));
        assertEquals(4,FavouriteTitles.move(4,1,0));
        assertEquals(1,FavouriteTitles.move(0,1,0));
        assertEquals(2,FavouriteTitles.move(1,1,0));
        assertEquals(3,FavouriteTitles.move(0,0,1));
        assertEquals(4,FavouriteTitles.move(2,0,1));
        assertEquals(0,FavouriteTitles.move(3,0,-1));
        assertEquals(2,FavouriteTitles.move(4,0,-1));
    }
    @Test public void destinationsAreExplicitHttpsTitleLinksForTheMatchingApp() throws Exception {
        assertEquals(5,FavouriteTitles.TITLES.length);
        for(int i=0;i<5;i++) {
            URI uri=new URI(FavouriteTitles.URLS[i]);
            assertEquals("https",uri.getScheme());
            assertFalse(FavouriteTitles.URLS[i].contains("…"));
            assertFalse(FavouriteTitles.URLS[i].contains("search"));
            assertEquals(i<3?"www.netflix.com":"app.primevideo.com",uri.getHost());
            assertEquals(i<3?FavouriteTitles.NETFLIX:FavouriteTitles.PRIME,FavouriteTitles.PACKAGES[i]);
        }
    }
}
