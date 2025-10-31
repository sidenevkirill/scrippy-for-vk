package ru.lisdevs.messenger.utils;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.RemoteInput;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MessageReplyReceiver extends BroadcastReceiver {

    private static final String TAG = "MessageReplyReceiver";
    private static final String KEY_REPLY = "key_reply";
    private OkHttpClient httpClient = new OkHttpClient();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        // Получаем текст ответа из уведомления
        CharSequence replyText = getReplyText(intent);
        String userId = intent.getStringExtra("user_id");

        if (replyText != null && userId != null) {
            String message = replyText.toString();
            sendMessage(context, userId, message);

            // Закрываем уведомление после ответа
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(1);
        }
    }

    private CharSequence getReplyText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence(KEY_REPLY);
        }
        return null;
    }

    private void sendMessage(Context context, String userId, String message) {
        String accessToken = TokenManager.getInstance(context).getToken();

        if (accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(context, "Ошибка: токен не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        if (message.trim().isEmpty()) {
            Toast.makeText(context, "Сообщение не может быть пустым", Toast.LENGTH_SHORT).show();
            return;
        }

        // Создаем JSON для отправки сообщения
        String requestBody = createMessageJson(userId, message);
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        RequestBody body = RequestBody.create(requestBody, JSON);

        String url = "https://api.vk.com/method/messages.send" +
                "?access_token=" + accessToken +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", "VKAndroidApp/5.52-4543 (Android 5.1.1; SDK 22; x86_64; unknown Android SDK built for x86_64; en; 320x240)")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to send message: " + e.getMessage());
                showToastOnMainThread(context, "Ошибка отправки: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            String errorMsg = error.optString("error_msg", "Неизвестная ошибка");
                            Log.e(TAG, "API Error: " + errorMsg);
                            showToastOnMainThread(context, "Ошибка: " + errorMsg);
                        } else {
                            Log.d(TAG, "Message sent successfully");
                            showToastOnMainThread(context, "Сообщение отправлено");
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing response: " + e.getMessage());
                        showToastOnMainThread(context, "Ошибка обработки ответа");
                    }
                } else {
                    Log.e(TAG, "Server error: " + response.code());
                    showToastOnMainThread(context, "Ошибка сервера: " + response.code());
                }
            }
        });
    }

    private String createMessageJson(String userId, String message) {
        try {
            JSONObject json = new JSONObject();
            json.put("user_id", userId);
            json.put("message", message);
            json.put("random_id", System.currentTimeMillis());
            return json.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON: " + e.getMessage());
            return "{}";
        }
    }

    private void showToastOnMainThread(final Context context, final String message) {
        // Используем главный поток для показа Toast
        new android.os.Handler(context.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
