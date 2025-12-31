package ru.lisdevs.messenger.utils;

import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

public class HttpClientManager {
    private static volatile OkHttpClient instance;

    public static OkHttpClient getInstance() {
        if (instance == null) {
            synchronized (HttpClientManager.class) {
                if (instance == null) {
                    instance = createHttpClient();
                }
            }
        }
        return instance;
    }

    private static OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    // Метод для очистки в фоне (вызывать из Application.onTerminate())
    public static void cleanup() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (instance != null) {
                    instance.dispatcher().cancelAll();
                    instance.connectionPool().evictAll();
                }
            } catch (Exception e) {
                // Игнорируем ошибки при очистке
            }
        });
    }
}