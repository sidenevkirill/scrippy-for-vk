package ru.lisdevs.messenger.utils;

import static android.support.v4.media.session.MediaSessionCompat.KEY_TOKEN;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TokenManager {
    private static TokenManager instance;
    private final SharedPreferences prefs;
    private Context context;
    private OkHttpClient httpClient;

    private static final String PREF_NAME = "VKAuthPrefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_PHOTO_URL = "photo_url";
    private static final String KEY_TOKEN_TIME = "token_time";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    // Константы для времени жизни токена
    private static final long TOKEN_LIFETIME = 7 * 24 * 60 * 60 * 1000L; // 7 дней
    private static final long REFRESH_THRESHOLD = 12 * 60 * 60 * 1000L; // 12 часов до истечения
    private static final long TOKEN_EXPIRY_CHECK_INTERVAL = 30 * 60 * 1000L; // 30 минут

    private long lastTokenCheckTime = 0;

    private TokenManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
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

    // Новый метод для сохранения данных с рефреш токеном
    public void saveAuthData(String accessToken, String refreshToken, String userId,
                             String fullName, String photoUrl) {
        long currentTime = System.currentTimeMillis();
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_FULL_NAME, fullName)
                .putString(KEY_PHOTO_URL, photoUrl)
                .putLong(KEY_TOKEN_TIME, currentTime)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
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

    public long getTokenTime() {
        return prefs.getLong(KEY_TOKEN_TIME, 0);
    }

    public boolean isTokenValid() {
        String token = getToken();
        long tokenTime = getTokenTime();

        if (token != null && !token.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            long tokenAge = currentTime - tokenTime;

            // Проверяем не слишком ли часто проверяем токен
            if (currentTime - lastTokenCheckTime < TOKEN_EXPIRY_CHECK_INTERVAL) {
                // Если недавно проверяли, возвращаем последний результат
                return tokenAge < TOKEN_LIFETIME;
            }

            lastTokenCheckTime = currentTime;

            boolean isValid = tokenAge < TOKEN_LIFETIME;

            // Если токен скоро истечет, запускаем рефреш в фоне
            long timeUntilExpiry = TOKEN_LIFETIME - tokenAge;
            if (timeUntilExpiry < REFRESH_THRESHOLD && timeUntilExpiry > 0) {
                refreshTokenInBackground();
            }

            return isValid;
        }
        return false;
    }

    public void refreshTokenInBackground() {
        String refreshToken = getRefreshToken();
        String currentToken = getToken();

        if (refreshToken == null || currentToken == null) {
            Log.d("TokenManager", "No refresh token available");
            return;
        }

        // Проверяем, не обновляем ли мы уже токен
        long lastRefreshTime = prefs.getLong("last_refresh_attempt", 0);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastRefreshTime < 5 * 60 * 1000L) { // 5 минут
            Log.d("TokenManager", "Refresh attempt too soon, skipping");
            return;
        }

        prefs.edit().putLong("last_refresh_attempt", currentTime).apply();

        new Thread(() -> {
            try {
                refreshTokenFromServer(refreshToken);
            } catch (Exception e) {
                Log.e("TokenManager", "Failed to refresh token in background", e);
            }
        }).start();
    }

    private void refreshTokenFromServer(String refreshToken) {

        String refreshUrl = "https://api.vk.com/method/auth.refreshToken";

        RequestBody formBody = new FormBody.Builder()
                .add("refresh_token", refreshToken)
                .add("client_id", "2274003")
                .add("client_secret", "hHbZxrka2uZ6jB1inYsH")
                .add("grant_type", "refresh_token")
                .build();

        Request request = new Request.Builder()
                .url(refreshUrl)
                .post(formBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("TokenManager", "Token refresh failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "{}";
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("access_token")) {
                            String newAccessToken = json.getString("access_token");
                            String newRefreshToken = json.optString("refresh_token", refreshToken);
                            long expiresIn = json.optLong("expires_in", 604800) * 1000; // 7 дней по умолчанию

                            // Обновляем токен
                            long currentTime = System.currentTimeMillis();
                            prefs.edit()
                                    .putString(KEY_ACCESS_TOKEN, newAccessToken)
                                    .putString(KEY_REFRESH_TOKEN, newRefreshToken)
                                    .putLong(KEY_TOKEN_TIME, currentTime)
                                    .apply();

                            Log.d("TokenManager", "Token refreshed successfully");

                            // Можно также обновить информацию о пользователе
                            updateUserInfo(newAccessToken);

                        } else {
                            Log.e("TokenManager", "No access token in refresh response");
                        }
                    } catch (Exception e) {
                        Log.e("TokenManager", "Error parsing refresh response", e);
                    }
                } else {
                    Log.e("TokenManager", "Token refresh failed with code: " + response.code());

                    // Если рефреш токен недействителен, разлогиниваем пользователя
                    if (response.code() == 401) {
                        clearAuthData();
                    }
                }
            }
        });
    }

    private void updateUserInfo(String accessToken) {
        // Пример обновления информации о пользователе
        String userInfoUrl = "https://api.vk.com/method/users.get?fields=photo_200,first_name,last_name&access_token=" + accessToken + "&v=5.199";

        Request request = new Request.Builder()
                .url(userInfoUrl)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("TokenManager", "Failed to update user info", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "{}";
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONObject user = json.getJSONArray("response").getJSONObject(0);
                            String userId = user.getString("id");
                            String firstName = user.getString("first_name");
                            String lastName = user.getString("last_name");
                            String photoUrl = user.optString("photo_200", "");

                            String fullName = firstName + " " + lastName;

                            prefs.edit()
                                    .putString(KEY_USER_ID, userId)
                                    .putString(KEY_FULL_NAME, fullName)
                                    .putString(KEY_PHOTO_URL, photoUrl)
                                    .apply();

                            Log.d("TokenManager", "User info updated");
                        }
                    } catch (Exception e) {
                        Log.e("TokenManager", "Error parsing user info", e);
                    }
                }
            }
        });
    }

    // Метод для принудительного рефреша токена (можно вызывать из UI)
    public void forceRefreshToken() {
        String refreshToken = getRefreshToken();
        if (refreshToken != null) {
            new Thread(() -> {
                try {
                    refreshTokenFromServer(refreshToken);
                } catch (Exception e) {
                    Log.e("TokenManager", "Force refresh failed", e);
                }
            }).start();
        }
    }

    // Метод для проверки, нужно ли обновлять токен (можно использовать для показа уведомления)
    public boolean shouldRefreshToken() {
        long tokenTime = getTokenTime();
        long currentTime = System.currentTimeMillis();
        long tokenAge = currentTime - tokenTime;
        long timeUntilExpiry = TOKEN_LIFETIME - tokenAge;

        return timeUntilExpiry < REFRESH_THRESHOLD && timeUntilExpiry > 0;
    }

    // Метод для получения оставшегося времени жизни токена в миллисекундах
    public long getRemainingTokenLifetime() {
        long tokenTime = getTokenTime();
        long currentTime = System.currentTimeMillis();
        long tokenAge = currentTime - tokenTime;

        return TOKEN_LIFETIME - tokenAge;
    }

    public void clearAuthData() {
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_FULL_NAME)
                .remove(KEY_PHOTO_URL)
                .remove(KEY_TOKEN_TIME)
                .remove(KEY_REFRESH_TOKEN)
                .remove("last_refresh_attempt")
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
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_USER_ID)
                .apply();
    }

    // Метод для инициализации периодической проверки токена
    public void startTokenMonitoring() {
        // Можно реализовать периодическую проверку через WorkManager или AlarmManager
        // Например, проверять каждые 6 часов
        Log.d("TokenManager", "Token monitoring started");
    }

    // Метод для остановки мониторинга токена
    public void stopTokenMonitoring() {
        Log.d("TokenManager", "Token monitoring stopped");
    }
}