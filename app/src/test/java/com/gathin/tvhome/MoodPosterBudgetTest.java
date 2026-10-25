package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class MoodPosterBudgetTest {
    @Test public void expandedMixedSnapshotFitsWithoutEvictingItsFirstCover() {
        int cache=8*1024*1024;
        for(int count:new int[]{54,100,500}) {
            long bytes=0;
            for(int i=0;i<count;i++) {
                int width=i%3==0 ? 600 : 400, height=600;
                boolean jpeg=i%5!=0;
                int limit=MoodPosterBudget.limit(width,height,jpeg ? "image/jpeg" : "image/png",count,cache);
                double scale=Math.min(1,limit/(double)Math.max(width,height));
                int scaledWidth=(int)Math.round(width*scale),scaledHeight=(int)Math.round(height*scale);
                // Include conservative row alignment; alpha covers require four bytes per pixel.
                bytes+=((scaledWidth*(jpeg ? 2:4)+7)/8*8L)*scaledHeight;
            }
            assertTrue("The whole snapshot must fit",bytes<cache);
        }
    }
    @Test public void existingSnapshotKeepsItsResolutionAndMissingArtIsSafe() {
        assertEquals(360,MoodPosterBudget.limit(400,600,"image/jpeg",26,8*1024*1024));
        assertEquals(1,MoodPosterBudget.limit(-1,-1,null,54,8*1024*1024));
    }
}
