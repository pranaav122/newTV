package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;

/** On-demand weather for a locally chosen place. No service, job, alarm or polling. */
public final class CurrentWeatherProvider extends ContentProvider {
    @Override public boolean onCreate() {
        return true;
    }
    @Override public synchronized Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if (getContext().getPackageManager().checkSignatures(Binder.getCallingUid(), getContext().getApplicationInfo().uid)
                != PackageManager.SIGNATURE_MATCH) throw new SecurityException("Only the same-signed launcher may read the widget");
        if (!"/current".equals(uri.getPath())) throw new IllegalArgumentException("Unknown weather query");
        MatrixCursor result = new MatrixCursor(new String[]{"temperature", "code", "updated", "city"});
        SharedPreferences place = getContext().getSharedPreferences("place", 0);
        String city = place.getString("name", "").trim();
        if (city.isEmpty() || !place.contains("lat") || !place.contains("lon")) return result;
        double latitude = Double.longBitsToDouble(place.getLong("lat", 0));
        double longitude = Double.longBitsToDouble(place.getLong("lon", 0));
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return result;
        String location = Double.toHexString(latitude) + "," + Double.toHexString(longitude);
        SharedPreferences cache = getContext().getSharedPreferences("current", 0);
        boolean samePlace = location.equals(cache.getString("place", ""));
        long now = System.currentTimeMillis(), updated = samePlace ? cache.getLong("updated", 0) : 0;
        long attempted = samePlace ? cache.getLong("attempted", 0) : 0;
        if (now - updated > Forecast.REFRESH_MS && (now - attempted > Forecast.RETRY_MS || now < attempted)) {
            SharedPreferences.Editor attempt = cache.edit().putString("place", location).putLong("attempted", now);
            if (!samePlace) attempt.remove("updated");
            attempt.apply();
            try {
                Forecast.Report report = Forecast.fetch(latitude, longitude, 3);
                updated = System.currentTimeMillis();
                cache.edit().putInt("temperature", report.tempC).putInt("code", report.code).putLong("updated", updated).apply();
            } catch (Exception offline) { /* Return a labelled cached report, never an invented temperature. */ }
        }
        if (updated > 0) result.addRow(new Object[]{cache.getInt("temperature", 0), cache.getInt("code", 0), updated, city});
        return result;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.newtv.weather"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
