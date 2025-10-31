package ru.lisdevs.messenger.utils;

import android.content.Context;
import android.util.Log;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class SpecialUsersManager {
    private static final String SPECIAL_USERS_URL = "https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/master/special_users.json";
    private static final String TAG = "SpecialUsersManager";
    private static Set<Long> specialUserIds = new HashSet<>();
    private static long lastUpdateTime = 0;
    private static final long CACHE_DURATION = 24 * 60 * 60 * 1000; // 24 часа

    public static boolean isSpecialUser(long userId) {
        boolean isSpecial = specialUserIds.contains(userId);
        Log.d(TAG, "Checking user " + userId + ": " + isSpecial);
        Log.d(TAG, "Total special users loaded: " + specialUserIds.size());
        return isSpecial;
    }

    public static void loadSpecialUsers(Context context) {
        // Проверяем, не пора ли обновить кэш
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < CACHE_DURATION && !specialUserIds.isEmpty()) {
            Log.d(TAG, "Using cached special users data");
            return;
        }

        Log.d(TAG, "Loading special users from: " + SPECIAL_USERS_URL);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(SPECIAL_USERS_URL)
                .header("User-Agent", "VKAndroidApp/7.4.1-12345 (Android 11; SDK 30; arm64-v8a; ; ru)")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load special users: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        Set<Long> newSpecialUserIds = new HashSet<>();

                        if (json.has("special_users")) {
                            JSONArray specialUsersArray = json.getJSONArray("special_users");
                            for (int i = 0; i < specialUsersArray.length(); i++) {
                                long userId = specialUsersArray.getLong(i);
                                newSpecialUserIds.add(userId);
                                Log.d(TAG, "Added special user: " + userId);
                            }
                        }

                        if (json.has("vip_users")) {
                            JSONArray vipUsersArray = json.getJSONArray("vip_users");
                            for (int i = 0; i < vipUsersArray.length(); i++) {
                                long userId = vipUsersArray.getLong(i);
                                newSpecialUserIds.add(userId);
                                Log.d(TAG, "Added VIP user: " + userId);
                            }
                        }

                        // Обновляем кэш
                        specialUserIds = newSpecialUserIds;
                        lastUpdateTime = System.currentTimeMillis();

                        Log.d(TAG, "Successfully loaded " + specialUserIds.size() + " special users");

                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing special users JSON: " + e.getMessage());
                    }
                } else {
                    Log.e(TAG, "Failed to load special users: HTTP " + response.code());
                }
            }
        });
    }

    public static Set<Long> getSpecialUserIds() {
        return new HashSet<>(specialUserIds);
    }

    public static void clearCache() {
        specialUserIds.clear();
        lastUpdateTime = 0;
        Log.d(TAG, "Special users cache cleared");
    }
}