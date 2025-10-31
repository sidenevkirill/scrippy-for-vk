package ru.lisdevs.messenger.utils;

import static android.support.v4.media.session.MediaSessionCompat.KEY_TOKEN;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;

public class TokenManager {
    private static TokenManager instance;
    private final SharedPreferences prefs;
    private Context context;

    private static final String PREF_NAME = "VKAuthPrefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_PHOTO_URL = "photo_url";
    private static final String KEY_TOKEN_TIME = "token_time";

    private TokenManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized TokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new TokenManager(context);
        }
        return instance;
    }

    public void saveToken(String token) {
        long currentTime = System.currentTimeMillis();
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, token)
                .putLong(KEY_TOKEN_TIME, currentTime)
                .apply();
    }

    public void saveUserId(String userId) {
        prefs.edit().putString(KEY_USER_ID, userId).apply();
    }

    public void saveUserData(String token, String userId, String fullName, String photoUrl) {
        long currentTime = System.currentTimeMillis();
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, token)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_FULL_NAME, fullName)
                .putString(KEY_PHOTO_URL, photoUrl)
                .putLong(KEY_TOKEN_TIME, currentTime)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getFullName() {
        return prefs.getString(KEY_FULL_NAME, null);
    }

    public String getPhotoUrl() {
        return prefs.getString(KEY_PHOTO_URL, null);
    }

    public boolean isTokenValid() {
        String token = getToken();
        long tokenTime = prefs.getLong(KEY_TOKEN_TIME, 0);

        if (token != null && !token.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            long tokenAge = currentTime - tokenTime;

            // Токен действителен 24 часа (86400000 мс)
            return tokenAge < 86400000;
        }
        return false;
    }

    public void clearAuthData() {
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_FULL_NAME)
                .remove(KEY_PHOTO_URL)
                .remove(KEY_TOKEN_TIME)
                .apply();
    }

    public static void logout(Context context) {
        getInstance(context).clearAuthData();
        CookieManager.getInstance().removeAllCookies(null);
    }

    public void refreshToken(String newToken) {
        long currentTime = System.currentTimeMillis();
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, newToken)
                .putLong(KEY_TOKEN_TIME, currentTime)
                .apply();
    }

    @SuppressLint("RestrictedApi")
    public void clearToken() {
        // Fixed: using correct key names
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_USER_ID)
                .apply();
    }
}