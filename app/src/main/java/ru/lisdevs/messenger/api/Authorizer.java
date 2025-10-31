package ru.lisdevs.messenger.api;

import android.os.Build;

import java.util.Locale;

public class Authorizer {

    public static String getKateUserAgent() {
        return String.format(Locale.getDefault(),
                "KateMobileAndroid/%s-%d (Android %s; SDK %d; %s; %s %s; %s)",
                "51.1", 442, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.CPU_ABI, Build.MANUFACTURER,
                Build.MODEL, Locale.getDefault().getLanguage());
    }

    public static String getVkUserAgent() {
        return String.format(Locale.getDefault(),
                "VKAndroidApp/8.85-20792 (Android %s; SDK %d; %s; %s %s; %s; 2100x1200)",
                "51.1", 20792, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.CPU_ABI, Build.MANUFACTURER,
                Build.MODEL, Locale.getDefault().getLanguage());
    }

    public static String getNewVkUserAgent() {
        try {
            return String.format(Locale.getDefault(),
                    "VKAndroidApp/%s-%d (Android %s; SDK %d; %s; %s %s; %s; 2100x1200)",
                    "8.85",                 // Версия приложения
                    20792,                  // Номер сборки
                    Build.VERSION.RELEASE,  // Версия Android (например, "7.1.1")
                    Build.VERSION.SDK_INT,  // Уровень API (например, 25)
                    Build.SUPPORTED_ABIS[0], // Архитектура процессора (например, "arm64-v8a")
                    Build.MANUFACTURER,     // Производитель устройства (например, "OnePlus")
                    Build.MODEL,            // Модель устройства (например, "ONEPLUS A5000")
                    Locale.getDefault().getLanguage() // Язык системы (например, "ru")
            );
        } catch (Exception e) {
            // Возвращаем fallback строку в случае ошибки
            return "VKAndroidApp/8.85-20792 (Android 7.1.1; SDK 25; arm64-v8a; OnePlus ONEPLUS A5000; ru; 2100x1200)";
        }
    }
}
