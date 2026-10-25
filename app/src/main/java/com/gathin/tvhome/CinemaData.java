package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Sample favourites from the reference video. No catalog or viewing-history requests. */
final class CinemaData {
    static final String[] MOODS = {"Anything at all", "Comfort sitcom", "Mind games", "Capes and chaos", "Gotham after dark", "Thalapathy mass"};
    static final class Title {
        final String name, genre, line;
        final int mood;
        Title(String name, String genre, String line, int mood) {
            this.name = name; this.genre = genre; this.line = line; this.mood = mood;
        }
    }
    static final Title[] TITLES = {
        new Title("The Big Bang Theory", "COMEDY · SERIES", "A little science. A lot of friendship.", 1),
        new Title("How I Met Your Mother", "COMEDY · SERIES", "Make a little time for the long story.", 1),
        new Title("Friends", "COMEDY · SERIES", "The familiar faces are waiting.", 1),
        new Title("Breaking Bad", "CRIME · SERIES", "One decision changes everything.", 2),
        new Title("The Mentalist", "MYSTERY · SERIES", "Look a little closer.", 2),
        new Title("The Flash", "SUPERHERO · SERIES", "The next adventure can wait for no one.", 3),
        new Title("Spider-Man: No Way Home", "SUPERHERO · FILM", "Some nights need a friendly neighbourhood hero.", 3),
        new Title("Batman Begins", "GOTHAM · FILM", "Every story has a beginning.", 4),
        new Title("The Dark Knight", "GOTHAM · FILM", "Tonight, Gotham can wait.", 4),
        new Title("The Dark Knight Rises", "GOTHAM · FILM", "Time to rise to the occasion.", 4),
        new Title("Master", "TAMIL ACTION · FILM", "Settle in for the big entrance.", 5),
        new Title("Leo", "TAMIL ACTION · FILM", "A quiet evening with a little chaos.", 5),
        new Title("The Greatest of All Time", "TAMIL ACTION · FILM", "Make tonight a blockbuster.", 5)
    };

    static List<Integer> matching(int mood) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < TITLES.length; i++) if (mood == 0 || TITLES[i].mood == mood) result.add(i);
        return result;
    }

    static int pick(int mood, int previous, Random random) {
        List<Integer> pool = matching(mood);
        if (pool.isEmpty()) return -1;
        if (pool.size() > 1) pool.remove(Integer.valueOf(previous));
        return pool.get(random.nextInt(pool.size()));
    }
}
