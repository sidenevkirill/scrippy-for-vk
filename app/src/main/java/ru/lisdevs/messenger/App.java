package ru.lisdevs.messenger;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

import javax.net.ssl.HttpsURLConnection;

import ru.lisdevs.messenger.utils.ModernTLSSocketFactory;

public class App extends Application {

    public static Context applicationContext;
    private static final String PREF_NAME = "VK_PREFS";
    private static final String PREF_DARK_THEME = "dark_theme_enabled";

    @Override
    public void onCreate() {
        super.onCreate();
        applicationContext = this;

        // Применяем сохраненную тему перед установкой контента
        applySavedTheme();

        // Применяем динамические цвета Material You
        DynamicColors.applyToActivitiesIfAvailable(this);

        // Настройка TLS перед инициализацией рекламы
        configureTls();

        // Инициализация Yandex Mobile Ads
        // initializeYandexAds();
    }

    private void applySavedTheme() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(PREF_DARK_THEME, false);

        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void configureTls() {
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(new ModernTLSSocketFactory());
            System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeYandexAds() {
        try {
            // MobileAds.initialize(this, () -> {
            //     MobileAds.setUserConsent(true);
            //     MobileAds.setLocationConsent(true);
            //
            //     if (BuildConfig.DEBUG) {
            //         MobileAds.enableLogging(true);
            //     }
            // });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Context getAppContext() {
        return applicationContext;
    }
}