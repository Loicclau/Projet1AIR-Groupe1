package com.tonnom.vostit;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "VostItSession";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DARK_MODE = "dark_mode";
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
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
        return pref.getBoolean(KEY_DARK_MODE, false);
    }

    public boolean isLoggedIn() {
        return getUsername() != null;
    }

    public void logout() {
        editor.clear();
        editor.apply();
    }
}
