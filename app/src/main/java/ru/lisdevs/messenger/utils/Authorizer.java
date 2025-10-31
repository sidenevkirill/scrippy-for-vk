package ru.lisdevs.messenger.utils;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

import okhttp3.HttpUrl;

public class Authorizer {
    public static final float KATE_API_VERSION = 5.131f;
    public static final int KATE_CLIENT_ID = 2685278;
    public static final String KATE_CLIENT_SECRET = "lxhD8OD7dMsqtXIm5IUY";

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
                "KateMobileAndroid/%s-%d (Android %s; SDK %d; %s; %s %s; %s)",
                "51.1", 442, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.CPU_ABI, Build.MANUFACTURER,
                Build.MODEL, Locale.getDefault().getLanguage());
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

    // Класс для хранения данных капчи
    public static class CaptchaData {
        public boolean isNewCaptcha = false;
        public String redirectUri;
        public String remixstlid;
        public String captchaSid;
        public String captchaImg;
    }
}