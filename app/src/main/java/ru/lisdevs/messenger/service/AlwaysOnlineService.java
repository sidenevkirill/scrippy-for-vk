package ru.lisdevs.messenger.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.settings.SettingsFragment;
import ru.lisdevs.messenger.utils.TokenManager;

public class AlwaysOnlineService extends Service {
    private static final String TAG = "AlwaysOnlineService";

    private Handler handler;
    private Runnable onlineRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler();

        onlineRunnable = new Runnable() {
            @Override
            public void run() {
                if (SettingsFragment.isAlwaysOnlineEnabled(AlwaysOnlineService.this)) {
                    setOnlineStatus();
                    // Повторяем каждые 5 минут
                    handler.postDelayed(this, 5 * 60 * 1000);
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AlwaysOnlineService started");

        // Запускаем первый запрос сразу
        handler.post(onlineRunnable);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && onlineRunnable != null) {
            handler.removeCallbacks(onlineRunnable);
        }
        Log.d(TAG, "AlwaysOnlineService stopped");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setOnlineStatus() {
        String token = TokenManager.getInstance(this).getToken();
        if (token == null) {
            Log.w(TAG, "Token is null, cannot set online status");
            return;
        }

        String url = "https://api.vk.com/method/account.setOnline" +
                "?access_token=" + token +
                "&voip=0" +
                "&v=5.131";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "KateMobileAndroid/56 lite-447 (Android 6.0; SDK 23; x86; Google Android SDK built for x86; en)")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to set online status: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        Log.e(TAG, "API error: " + error.getString("error_msg"));
                    } else {
                        Log.d(TAG, "Online status set successfully");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing response: " + e.getMessage());
                }
            }
        });
    }
}