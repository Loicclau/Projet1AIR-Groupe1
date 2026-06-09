package com.tonnom.vostit;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SessionManager {
    private static final String PREF_NAME = "VostItSession";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_SYNTHESIS_COUNT = "synthesis_count";
    private static final String KEY_LAST_SYNTHESIS_DATE = "last_synthesis_date";
    private static final String KEY_FAV_SPECIALTY = "fav_specialty";
    private static final String KEY_FAV_YEAR = "fav_year";
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void setFavoriteSpecialty(String specialty) {
        editor.putString(KEY_FAV_SPECIALTY, specialty);
        editor.apply();
    }

    public String getFavoriteSpecialty() {
        return pref.getString(KEY_FAV_SPECIALTY, null);
    }

    public void setFavoriteYear(String year) {
        editor.putString(KEY_FAV_YEAR, year);
        editor.apply();
    }

    public String getFavoriteYear() {
        return pref.getString(KEY_FAV_YEAR, null);
    }

    public void createLoginSession(String username) {
        editor.putString(KEY_USERNAME, username);
        editor.apply();
    }

    public String getUsername() {
        return pref.getString(KEY_USERNAME, null);
    }

    public void setDarkMode(boolean isDark) {
        editor.putBoolean(KEY_DARK_MODE, isDark);
        editor.apply();
    }

    public boolean isDarkMode() {
        return pref.getBoolean(KEY_DARK_MODE, true);
    }

    public int getDailySynthesisCount() {
        String username = getUsername();
        if (username == null) return 0;
        checkAndResetDailyQuota(username);
        return pref.getInt(KEY_SYNTHESIS_COUNT + "_" + username, 0);
    }

    public void incrementDailySynthesisCount() {
        String username = getUsername();
        if (username == null) return;
        checkAndResetDailyQuota(username);
        int currentCount = pref.getInt(KEY_SYNTHESIS_COUNT + "_" + username, 0);
        editor.putInt(KEY_SYNTHESIS_COUNT + "_" + username, currentCount + 1);
        editor.apply();
    }

    private void checkAndResetDailyQuota(String username) {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String lastDate = pref.getString(KEY_LAST_SYNTHESIS_DATE + "_" + username, "");

        if (!today.equals(lastDate)) {
            editor.putString(KEY_LAST_SYNTHESIS_DATE + "_" + username, today);
            editor.putInt(KEY_SYNTHESIS_COUNT + "_" + username, 0);
            editor.apply();
        }
    }

    public boolean isLoggedIn() {
        return getUsername() != null;
    }

    public void logout() {
        // On ne supprime que le username pour garder les préférences locales
        // (quota, thème) même après déconnexion.
        editor.remove(KEY_USERNAME);
        editor.apply();
    }
}
