package ru.lisdevs.messenger.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import android.content.Context;
import android.content.SharedPreferences;

public class PinManager {
    private static final String PIN_PREFS = "PIN_PREFS";
    private static final String PREF_PIN_CODE = "pin_code";
    private static final String PREF_PIN_ENABLED = "pin_enabled";
    private static final String PREF_FINGERPRINT_ENABLED = "fingerprint_enabled";
    private static final String PREF_ATTEMPTS = "pin_attempts";
    private static final String PREF_LOCKOUT_UNTIL = "lockout_until";
    private static final String PREF_PIN_AUTHENTICATED = "pin_authenticated";
    private static final String PREF_LAST_AUTH_TIME = "last_auth_time";

    // Сохранение PIN-кода
    public static void setPin(Context context, String pin) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_PIN_CODE, pin).apply();
    }

    // Получение PIN-кода
    public static String getPin(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(PREF_PIN_CODE, "");
    }

    // Проверка PIN-кода
    public static boolean verifyPin(Context context, String pin) {
        String savedPin = getPin(context);
        return savedPin.equals(pin);
    }

    // Включение/отключение PIN-кода
    public static void setPinEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_PIN_ENABLED, enabled).apply();

        if (!enabled) {
            // При отключении PIN сбрасываем аутентификацию
            setPinAuthenticated(context, false);
        }
    }

    // Получить время последней аутентификации
    public static long getLastAuthTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        return prefs.getLong(PREF_LAST_AUTH_TIME, 0);
    }

    // Проверить, не истек ли таймаут аутентификации
    public static boolean isAuthValid(Context context) {
        if (!isPinAuthenticated(context)) {
            return false;
        }

        long lastAuthTime = getLastAuthTime(context);
        long currentTime = System.currentTimeMillis();
        long timeout = 30 * 60 * 1000; // 30 минут таймаут (можно настроить)

        return (currentTime - lastAuthTime) <= timeout;
    }

    public static boolean isPinEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_PIN_ENABLED, false);
    }

    // Управление отпечатком пальца
    public static void setFingerprintEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_FINGERPRINT_ENABLED, enabled).apply();
    }

    public static boolean isFingerprintEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_FINGERPRINT_ENABLED, false);
    }

    // Управление попытками ввода
    public static void setAttempts(Context context, int attempts) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putInt(PREF_ATTEMPTS, attempts).apply();
    }

    public static int getAttempts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        return prefs.getInt(PREF_ATTEMPTS, 0);
    }

    public static void resetAttempts(Context context) {
        setAttempts(context, 0);
    }

    // Управление блокировкой
    public static void setLockoutUntil(Context context, long lockoutUntil) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putLong(PREF_LOCKOUT_UNTIL, lockoutUntil).apply();
    }

    public static long getLockoutUntil(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        return prefs.getLong(PREF_LOCKOUT_UNTIL, 0);
    }

    // Управление аутентификацией
    public static void setPinAuthenticated(Context context, boolean authenticated) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_PIN_AUTHENTICATED, authenticated).apply();
    }

    public static boolean isPinAuthenticated(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_PIN_AUTHENTICATED, false);
    }

    public static void resetAuthentication(Context context) {
        setPinAuthenticated(context, false);
    }

    // Полная очистка PIN-данных
    public static void clearAllPinData(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(PREF_PIN_CODE)
                .remove(PREF_PIN_ENABLED)
                .remove(PREF_FINGERPRINT_ENABLED)
                .remove(PREF_ATTEMPTS)
                .remove(PREF_LOCKOUT_UNTIL)
                .remove(PREF_PIN_AUTHENTICATED)
                .apply();
    }
}