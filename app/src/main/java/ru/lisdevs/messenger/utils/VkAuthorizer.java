package ru.lisdevs.messenger.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

import okhttp3.HttpUrl;

import android.util.Log;


import org.json.JSONException;
import org.json.JSONObject;

public class VkAuthorizer {
    public static final float KATE_API_VERSION = 5.131f;
    public static final int KATE_CLIENT_ID = 2274003;
    public static final String KATE_CLIENT_SECRET = "hHbZxrka2uZ6jB1inYsH";

    // Константы для хранения данных пользователя
    private static final String PREF_USER_ID = "user_id";
    private static final String PREF_USER_NAME = "user_name";
    private static final String PREF_USER_FULL_NAME = "user_full_name";
    private static final String PREF_USER_AVATAR_URL = "user_avatar_url";

    public static String getDirectUrl(String username, String password, String code,
                                      String captcha_sid, String captcha_key, String remixstlid) {
        try {
            String oauthDomain = VKApi.oauthDomain;
            if (TextUtils.isEmpty(oauthDomain)) {
                oauthDomain = "oauth.vk.com";
                Log.w("VkAuthorizer", "Using default oauth domain");
            }

            HttpUrl baseUrl = HttpUrl.parse("https://" + oauthDomain + "/token");
            if (baseUrl == null) {
                throw new IllegalArgumentException("Invalid domain: " + oauthDomain);
            }

            HttpUrl.Builder builder = baseUrl.newBuilder()
                    .addQueryParameter("grant_type", "password")
                    .addQueryParameter("client_id", String.valueOf(KATE_CLIENT_ID))
                    .addQueryParameter("client_secret", KATE_CLIENT_SECRET)
                    .addQueryParameter("username", username)
                    .addQueryParameter("password", password)
                    .addQueryParameter("scope", Scopes.all())
                    .addQueryParameter("v", String.valueOf(KATE_API_VERSION))
                    .addQueryParameter("2fa_supported", "1");

            if (!TextUtils.isEmpty(code)) {
                builder.addQueryParameter("code", code);
            }
            if (!TextUtils.isEmpty(captcha_key)) {
                builder.addQueryParameter("captcha_key", captcha_key);
            }
            if (!TextUtils.isEmpty(captcha_sid)) {
                builder.addQueryParameter("captcha_sid", captcha_sid);
            }
            if (!TextUtils.isEmpty(remixstlid)) {
                builder.addQueryParameter("remixstlid", remixstlid);
            }

            return builder.toString();

        } catch (Exception e) {
            Log.e("VkAuthorizer", "Error building URL: " + e.getMessage());
            return buildFallbackUrl(username, password, code, captcha_sid, captcha_key, remixstlid);
        }
    }

    private static String buildFallbackUrl(String username, String password, String code,
                                           String captcha_sid, String captcha_key, String remixstlid) {
        try {
            StringBuilder url = new StringBuilder("https://oauth.vk.com/token?")
                    .append("grant_type=password")
                    .append("&client_id=").append(KATE_CLIENT_ID)
                    .append("&client_secret=").append(KATE_CLIENT_SECRET)
                    .append("&username=").append(URLEncoder.encode(username, "UTF-8"))
                    .append("&password=").append(URLEncoder.encode(password, "UTF-8"))
                    .append("&scope=").append(Scopes.all())
                    .append("&v=").append(KATE_API_VERSION)
                    .append("&2fa_supported=1");

            if (!TextUtils.isEmpty(code)) {
                url.append("&code=").append(URLEncoder.encode(code, "UTF-8"));
            }
            if (!TextUtils.isEmpty(captcha_key)) {
                url.append("&captcha_key=").append(URLEncoder.encode(captcha_key, "UTF-8"));
            }
            if (!TextUtils.isEmpty(captcha_sid)) {
                url.append("&captcha_sid=").append(URLEncoder.encode(captcha_sid, "UTF-8"));
            }
            if (!TextUtils.isEmpty(remixstlid)) {
                url.append("&remixstlid=").append(URLEncoder.encode(remixstlid, "UTF-8"));
            }

            return url.toString();
        } catch (UnsupportedEncodingException e) {
            Log.e("VkAuthorizer", "Encoding error: " + e.getMessage());
            // Без кодирования как запасной вариант
            StringBuilder url = new StringBuilder("https://oauth.vk.com/token?")
                    .append("grant_type=password")
                    .append("&client_id=").append(KATE_CLIENT_ID)
                    .append("&client_secret=").append(KATE_CLIENT_SECRET)
                    .append("&username=").append(username)
                    .append("&password=").append(password)
                    .append("&scope=").append(Scopes.all())
                    .append("&v=").append(KATE_API_VERSION)
                    .append("&2fa_supported=1");

            if (!TextUtils.isEmpty(code)) url.append("&code=").append(code);
            if (!TextUtils.isEmpty(captcha_key)) url.append("&captcha_key=").append(captcha_key);
            if (!TextUtils.isEmpty(captcha_sid)) url.append("&captcha_sid=").append(captcha_sid);
            if (!TextUtils.isEmpty(remixstlid)) url.append("&remixstlid=").append(remixstlid);

            return url.toString();
        }
    }

    // Перегруженный метод для обратной совместимости
    public static String getDirectUrl(String username, String password, String code,
                                      String captcha_sid, String captcha_key) {
        return getDirectUrl(username, password, code, captcha_sid, captcha_key, null);
    }

    public static String getKateUserAgent() {
        return String.format(Locale.getDefault(),
                "VKAndroidApp/5.52-4543 (Android %s; SDK %d; %s; %s %s; %s; 320x240)",
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                Build.CPU_ABI,
                Build.MANUFACTURER,
                Build.MODEL,
                Locale.getDefault().getLanguage());
    }

    public static boolean validateCredentials(String username, String password) {
        return !TextUtils.isEmpty(username) && !TextUtils.isEmpty(password);
    }

    // Новый метод для обработки ошибки капчи
    public static CaptchaData parseCaptchaError(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            CaptchaData data = new CaptchaData();

            if (json.has("error") && "need_captcha".equals(json.getString("error"))) {
                data.isNewCaptcha = true;

                if (json.has("redirect_uri")) {
                    data.redirectUri = json.getString("redirect_uri");
                }
                if (json.has("remixstlid")) {
                    data.remixstlid = json.getString("remixstlid");
                }
                if (json.has("captcha_sid")) {
                    data.captchaSid = json.getString("captcha_sid");
                }
                if (json.has("captcha_img")) {
                    data.captchaImg = json.getString("captcha_img");
                }

                return data;
            }
        } catch (JSONException e) {
            Log.e("VkAuthorizer", "Error parsing captcha response: " + e.getMessage());
        }
        return null;
    }

    // Методы для сохранения и получения данных пользователя
    public static void saveUserData(Context context, String userId, String userName, String fullName, String avatarUrl) {
        SharedPreferences prefs = getSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        if (userId != null) {
            editor.putString(PREF_USER_ID, userId);
        }
        if (userName != null) {
            editor.putString(PREF_USER_NAME, userName);
        }
        if (fullName != null) {
            editor.putString(PREF_USER_FULL_NAME, fullName);
        }
        if (avatarUrl != null) {
            editor.putString(PREF_USER_AVATAR_URL, avatarUrl);
        }

        editor.apply();
        Log.d("VkAuthorizer", "User data saved: ID=" + userId + ", Name=" + userName + ", FullName=" + fullName + ", Avatar=" + avatarUrl);
    }

    // Перегруженный метод для обратной совместимости
    public static void saveUserData(Context context, String userId, String userName, String fullName) {
        saveUserData(context, userId, userName, fullName, null);
    }

    public static String getUserId(Context context) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.getString(PREF_USER_ID, null);
    }

    public static String getUserName(Context context) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.getString(PREF_USER_NAME, null);
    }

    public static String getUserFullName(Context context) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.getString(PREF_USER_FULL_NAME, null);
    }

    public static String getUserAvatarUrl(Context context) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.getString(PREF_USER_AVATAR_URL, null);
    }

    public static void clearUserData(Context context) {
        SharedPreferences prefs = getSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(PREF_USER_ID);
        editor.remove(PREF_USER_NAME);
        editor.remove(PREF_USER_FULL_NAME);
        editor.remove(PREF_USER_AVATAR_URL);
        editor.apply();
        Log.d("VkAuthorizer", "User data cleared");
    }

    public static boolean hasUserData(Context context) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.contains(PREF_USER_ID) && prefs.contains(PREF_USER_NAME);
    }

    // Метод для получения всех данных пользователя
    public static UserData getUserData(Context context) {
        SharedPreferences prefs = getSharedPreferences(context);
        String userId = prefs.getString(PREF_USER_ID, null);
        String userName = prefs.getString(PREF_USER_NAME, null);
        String fullName = prefs.getString(PREF_USER_FULL_NAME, null);
        String avatarUrl = prefs.getString(PREF_USER_AVATAR_URL, null);

        if (userId != null && userName != null) {
            return new UserData(userId, userName, fullName, avatarUrl);
        }
        return null;
    }

    // Метод для сохранения только аватарки
    public static void saveUserAvatar(Context context, String avatarUrl) {
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PREF_USER_AVATAR_URL, avatarUrl);
            editor.apply();
            Log.d("VkAuthorizer", "User avatar saved: " + avatarUrl);
        }
    }

    // Метод для сохранения данных из ответа авторизации
    public static void saveAuthResponseData(Context context, String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);

            if (json.has("user_id") && json.has("email")) {
                String userId = String.valueOf(json.getInt("user_id"));
                String email = json.getString("email");
                String userName = extractUserNameFromEmail(email);

                saveUserData(context, userId, userName, null, null);
                Log.d("VkAuthorizer", "Auth response data saved: " + userId + ", " + userName);
            }
        } catch (JSONException e) {
            Log.e("VkAuthorizer", "Error parsing auth response: " + e.getMessage());
        }
    }

    // Метод для сохранения данных из профиля пользователя
    public static void saveProfileData(Context context, String userId, String firstName, String lastName) {
        String fullName = firstName + " " + lastName;
        String userName = extractUserName(firstName, lastName);

        saveUserData(context, userId, userName, fullName, null);
        Log.d("VkAuthorizer", "Profile data saved: " + userId + ", " + fullName);
    }

    // Метод для сохранения данных из профиля пользователя с аватаркой
    public static void saveProfileData(Context context, String userId, String firstName, String lastName, String avatarUrl) {
        String fullName = firstName + " " + lastName;
        String userName = extractUserName(firstName, lastName);

        saveUserData(context, userId, userName, fullName, avatarUrl);
        Log.d("VkAuthorizer", "Profile data with avatar saved: " + userId + ", " + fullName + ", Avatar: " + avatarUrl);
    }

    // Метод для проверки наличия аватарки
    public static boolean hasUserAvatar(Context context) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.contains(PREF_USER_AVATAR_URL) &&
                !TextUtils.isEmpty(prefs.getString(PREF_USER_AVATAR_URL, ""));
    }

    // Метод для получения базовой информации о пользователе в виде строки
    public static String getUserInfoString(Context context) {
        UserData userData = getUserData(context);
        if (userData != null) {
            return "ID: " + userData.userId +
                    ", Name: " + userData.userName +
                    ", Full Name: " + (userData.fullName != null ? userData.fullName : "N/A") +
                    ", Avatar: " + (userData.avatarUrl != null ? "Yes" : "No");
        }
        return "No user data";
    }

    private static String extractUserNameFromEmail(String email) {
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return "user";
    }

    private static String extractUserName(String firstName, String lastName) {
        if (firstName != null && lastName != null) {
            return (firstName + "." + lastName).toLowerCase();
        } else if (firstName != null) {
            return firstName.toLowerCase();
        } else {
            return "user";
        }
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences("vk_user_data", Context.MODE_PRIVATE);
    }

    // Класс для хранения данных капчи
    public static class CaptchaData {
        public boolean isNewCaptcha = false;
        public String redirectUri;
        public String remixstlid;
        public String captchaSid;
        public String captchaImg;

        @Override
        public String toString() {
            return "CaptchaData{" +
                    "isNewCaptcha=" + isNewCaptcha +
                    ", redirectUri='" + redirectUri + '\'' +
                    ", remixstlid='" + remixstlid + '\'' +
                    ", captchaSid='" + captchaSid + '\'' +
                    ", captchaImg='" + captchaImg + '\'' +
                    '}';
        }
    }

    // Класс для хранения данных пользователя
    public static class UserData {
        public final String userId;
        public final String userName;
        public final String fullName;
        public final String avatarUrl;

        public UserData(String userId, String userName, String fullName, String avatarUrl) {
            this.userId = userId;
            this.userName = userName;
            this.fullName = fullName;
            this.avatarUrl = avatarUrl;
        }

        // Конструктор для обратной совместимости
        public UserData(String userId, String userName, String fullName) {
            this(userId, userName, fullName, null);
        }

        public boolean hasAvatar() {
            return avatarUrl != null && !avatarUrl.isEmpty();
        }

        public boolean hasFullName() {
            return fullName != null && !fullName.isEmpty();
        }

        @Override
        public String toString() {
            return "UserData{" +
                    "userId='" + userId + '\'' +
                    ", userName='" + userName + '\'' +
                    ", fullName='" + fullName + '\'' +
                    ", avatarUrl='" + avatarUrl + '\'' +
                    '}';
        }
    }
}