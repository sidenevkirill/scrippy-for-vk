package ru.lisdevs.messenger;


import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import android.graphics.Bitmap;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;

import java.util.Locale;

import ru.lisdevs.messenger.utils.TokenManager;

public class VKAuthActivity extends AppCompatActivity {

    private static final String REDIRECT_URI = "https://oauth.vk.com/blank.html";
    private static final String AUTH_URL_NEW = "https://oauth.vk.com/authorize?" +
            "client_id=2685278" +
            "&scope=notify,friends,audio,offline" +
            "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI) +
            "&display=mobile" +
            "&response_type=token" +
            "&v=5.199" +
            "&revoke=0";

    private WebView webView;
    private TokenManager tokenManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vk_web_auth);

        TextView bottomBar = findViewById(R.id.next);
        TextView topBar = findViewById(R.id.nextLogin);

        bottomBar.setOnClickListener(v -> {
            Intent intent = new Intent(VKAuthActivity.this, AuthRefreshToken.class);
            startActivity(intent);
        });

        topBar.setOnClickListener(v -> {
            Intent intent = new Intent(VKAuthActivity.this, VKAuthMaruActivity.class);
            startActivity(intent);
        });

        tokenManager = TokenManager.getInstance(this);

        // Очищаем cookies для принудительного нового входа
        clearCookies();

        // Проверяем валидность токена
        if (tokenManager.isTokenValid()) {
            proceedToMainApp();
            return;
        }

        setupWebView();
    }

    private void clearCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(null);
        } else {
            cookieManager.removeAllCookie();
        }
    }

    private void setupWebView() {
        webView = findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();

        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.clearCache(true);
        webView.clearHistory();

        // Устанавливаем User-Agent
        String userAgent = getNewVkUserAgent();
        webSettings.setUserAgentString(userAgent);

        webView.setWebViewClient(new VKWebViewClient());
        webView.loadUrl(AUTH_URL_NEW);
    }

    public static String getNewVkUserAgent() {
        try {
            return String.format(Locale.getDefault(),
                    "VKAndroidApp/8.85-20792 (Android %s; SDK %d; %s; %s %s; %s; %s; 2100x1200)",
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT,
                    getCPUArchitecture(),
                    Build.MANUFACTURER,
                    Build.MODEL,
                    Locale.getDefault().getLanguage(),
                    "official"
            );
        } catch (Exception e) {
            return "VKAndroidApp/8.85-20792 (Android 7.1.1; SDK 25; arm64-v8a; OnePlus ONEPLUS A5000; ru; official; 2100x1200)";
        }
    }

    private static String getCPUArchitecture() {
        try {
            if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
                return Build.SUPPORTED_ABIS[0];
            }
        } catch (Exception e) {
            // Ignore
        }
        return "arm64-v8a";
    }

    private class VKWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            Log.d("VKAuth", "Page started: " + url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Log.d("VKAuth", "Page finished: " + url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            return handleUrl(url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(url);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            Log.e("VKAuth", "Error: " + errorCode + " - " + description + " URL: " + failingUrl);
            runOnUiThread(() -> Toast.makeText(VKAuthActivity.this,
                    "Ошибка загрузки: " + description, Toast.LENGTH_LONG).show());
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (errorResponse != null) {
                Log.e("VKAuth", "HTTP Error: " + errorResponse.getStatusCode());
            }
        }

        private boolean handleUrl(String url) {
            Log.d("VKAuth", "Handling URL: " + url);

            if (url.startsWith(REDIRECT_URI)) {
                if (url.contains("access_token=")) {
                    handleAuthSuccess(url);
                    return true;
                } else if (url.contains("error=")) {
                    handleAuthError(url);
                    return true;
                } else if (url.contains("success=1") || url.contains("success=true")) {
                    Log.d("VKAuth", "Auth success without token");
                    return true;
                }
            }
            return false;
        }

        private void handleAuthSuccess(String url) {
            try {
                Uri uri = Uri.parse(url);
                String fragment = uri.getFragment();

                if (fragment != null) {
                    String[] params = fragment.split("&");
                    String accessToken = null;
                    String userId = null;
                    long expiresIn = 86400; // 24 часа в секундах

                    for (String param : params) {
                        if (param.startsWith("access_token=")) {
                            accessToken = param.substring("access_token=".length());
                        } else if (param.startsWith("user_id=")) {
                            userId = param.substring("user_id=".length());
                        } else if (param.startsWith("expires_in=")) {
                            expiresIn = Long.parseLong(param.substring("expires_in=".length()));
                        }
                    }

                    if (accessToken != null && userId != null) {
                        Log.d("VKAuth", "Token received: " + accessToken.substring(0, 10) + "...");
                        fetchUserData(accessToken, userId);
                    } else {
                        Log.e("VKAuth", "Missing token or user_id in URL");
                    }
                }
            } catch (Exception e) {
                Log.e("VKAuth", "Error parsing auth URL", e);
                runOnUiThread(() -> Toast.makeText(VKAuthActivity.this,
                        "Ошибка обработки авторизации", Toast.LENGTH_LONG).show());
            }
        }

        private void handleAuthError(String url) {
            try {
                Uri uri = Uri.parse(url);
                String error = uri.getQueryParameter("error");
                String errorDescription = uri.getQueryParameter("error_description");

                String errorMessage = "Ошибка авторизации";
                if (errorDescription != null) {
                    errorMessage += ": " + errorDescription;
                }

                Log.e("VKAuth", "Auth error: " + error + " - " + errorDescription);
                String finalErrorMessage = errorMessage;
                runOnUiThread(() -> Toast.makeText(VKAuthActivity.this, finalErrorMessage, Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                Log.e("VKAuth", "Error parsing error URL", e);
            }
        }
    }

    private void fetchUserData(String accessToken, String userId) {
        String url = "https://api.vk.com/method/users.get" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&fields=first_name,last_name,photo_100";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(VKAuthActivity.this,
                            "Ошибка при получении данных: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    Log.e("VKAuth", "Network error", e);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            String errorMsg = error.optString("error_msg", "Unknown error");
                            runOnUiThread(() -> Toast.makeText(VKAuthActivity.this,
                                    "Ошибка VK: " + errorMsg, Toast.LENGTH_LONG).show());
                            return;
                        }

                        JSONArray responseArray = json.getJSONArray("response");
                        if (responseArray.length() > 0) {
                            JSONObject userObject = responseArray.getJSONObject(0);
                            String firstName = userObject.optString("first_name", "");
                            String lastName = userObject.optString("last_name", "");
                            String fetchedUserId = userObject.optString("id", userId);
                            String photoUrl = userObject.optString("photo_100", "");

                            runOnUiThread(() -> {
                                onTokenReceived(accessToken, fetchedUserId, firstName + " " + lastName, photoUrl);
                            });
                        }
                    } catch (JSONException e) {
                        runOnUiThread(() -> {
                            Toast.makeText(VKAuthActivity.this,
                                    "Ошибка обработки данных", Toast.LENGTH_LONG).show();
                            Log.e("VKAuth", "JSON parsing error", e);
                        });
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(VKAuthActivity.this,
                            "Ошибка сервера: " + response.code(), Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    private void onTokenReceived(String token, String userId, String fullName, String photoUrl) {
        // Сохраняем данные через TokenManager
        tokenManager.saveUserData(token, userId, fullName, photoUrl);

        saveAuthType("VKAuthActivity");

        Log.d("VKAuth", "Token saved successfully");
        proceedToMainApp();
    }

    private void saveAuthType(String authType) {
        SharedPreferences prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE);
        prefs.edit().putString("auth_type", authType).apply();
        Log.d("VKAuth", "Auth type saved: " + authType);
    }

    private void proceedToMainApp() {
        Intent intent = new Intent(this, BaseActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}