package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Sideloaded catalogue snapshot. The launcher has no network permission or catalogue polling. */
final class MoodCatalog {
    static final String FILE = "mood-catalog.json";
    static final class Title {
        final String id, name, app, url, posterLabel, kind;
        final Set<String> moods = new HashSet<>();
        Title(JSONObject value) throws Exception {
            id = value.getString("id"); name = value.getString("title");
            app = value.getString("app"); url = value.getString("url");
            kind=value.optString("kind",url.contains("/movies/") ? "movie":"show");
            posterLabel=value.optString("posterLabel", "");
            if (!id.matches("[a-z0-9-]{1,64}") || name.isEmpty() || !app.matches("[A-Za-z0-9_.]+")
                    || !url.startsWith("https://") || url.contains("…")) throw new IOException("Invalid title");
            JSONArray tags = value.getJSONArray("moods");
            for (int i=0; i<tags.length(); i++) moods.add(tags.getString(i));
        }
    }
    static final class Mood {
        final String name;
        final int color;
        Mood(String name, int color) { this.name = name; this.color = color; }
    }
    final List<Title> titles = new ArrayList<>();
    final List<Mood> moods = new ArrayList<>();
    final List<List<Integer>> pools = new ArrayList<>();

    static MoodCatalog load(Context context) {
        MoodCatalog result = new MoodCatalog();
        try {
            File file = local(context, FILE);
            if (!file.isFile() || file.length() > 512000) return result;
            String text;
            try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192]; int n;
                while ((n=in.read(bytes))!=-1) out.write(bytes,0,n);
                text = out.toString(StandardCharsets.UTF_8.name());
            }
            JSONObject data = new JSONObject(text);
            JSONArray records = data.getJSONArray("titles");
            Set<String> ids = new HashSet<>();
            for (int i=0; i<records.length(); i++) {
                Title title = new Title(records.getJSONObject(i));
                if (!ids.add(title.id)) throw new IOException("Duplicate title");
                try {
                    ApplicationInfo app = context.getPackageManager().getApplicationInfo(title.app, 0);
                    if (app.enabled) result.titles.add(title);
                } catch (android.content.pm.PackageManager.NameNotFoundException absent) { /* Skip absent apps. */ }
            }
            JSONArray moods = data.getJSONArray("moods");
            for (int m=0; m<moods.length(); m++) {
                JSONObject value = moods.getJSONObject(m);
                String name = value.getString("name"), key = value.getString("id");
                List<Integer> pool = new ArrayList<>();
                for (int t=0; t<result.titles.size(); t++)
                    if (key.equals("all") || result.titles.get(t).moods.contains(key)) pool.add(t);
                if (!pool.isEmpty()) {
                    result.moods.add(new Mood(name, Color.parseColor(value.getString("color"))));
                    result.pools.add(pool);
                }
            }
        } catch (Exception invalid) { return new MoodCatalog(); }
        return result;
    }
    // Keep an app-owned internal copy so shared storage mounting cannot blank the deck at boot.
    static File local(Context context, String path) {
        File directory = Config.dir(context);
        File source = directory == null ? null : new File(directory, path);
        File cache = new File(context.getFilesDir(), "mood-art/" + path);
        if (source != null && source.isFile() && (!cache.isFile() || cache.length()!=source.length()
                || source.lastModified()>cache.lastModified())) {
            File parent = cache.getParentFile();
            if (parent != null && (parent.isDirectory() || parent.mkdirs())) {
                try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(cache)) {
                    byte[] bytes = new byte[16384]; int n;
                    while ((n=in.read(bytes))!=-1) out.write(bytes,0,n);
                    cache.setLastModified(source.lastModified());
                } catch (IOException ignored) { return source; }
            }
        }
        return cache;
    }
}
