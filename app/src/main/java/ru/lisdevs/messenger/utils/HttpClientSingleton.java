package ru.lisdevs.messenger.utils;

import android.util.Log;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

public class HttpClientSingleton {
    private static volatile OkHttpClient instance;
    private static final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor();

    public static OkHttpClient getInstance() {
        if (instance == null) {
            synchronized (HttpClientSingleton.class) {
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

    public static void cleanup() {
        cleanupExecutor.execute(() -> {
            try {
                if (instance != null) {
                    // Отменяем все активные запросы
                    instance.dispatcher().cancelAll();

                    // Закрываем все соединения
                    instance.connectionPool().evictAll();

                    // Закрываем кэш если есть
                    Cache cache = instance.cache();
                    if (cache != null) {
                        try {
                            cache.close();
                        } catch (IOException e) {
                            // Игнорируем ошибки при закрытии кэша
                        }
                    }
                }
            } catch (Exception e) {
                // Логируем ошибку
                Log.e("HttpClientSingleton", "Error during cleanup", e);
            }
        });
    }

    public static void shutdown() {
        cleanup();
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}