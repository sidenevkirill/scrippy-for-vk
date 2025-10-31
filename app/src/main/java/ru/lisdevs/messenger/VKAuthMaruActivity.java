package ru.lisdevs.messenger;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
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
import java.net.URLEncoder;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.api.Authorizer;

public class VKAuthMaruActivity extends AppCompatActivity {

    private static final String REDIRECT_URI = "https://oauth.vk.com/blank.html";
    private static final String AUTH_URL_NEW = "https://oauth.vk.com/authorize?" +
            "client_id=6463690" +
            "&scope=audio" +
            "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI) +
            "&display=mobile" +
            "&response_type=token" +
            "&revoke=1";

    private WebView webView;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vk_web_auth);

            TextView bottomBar = findViewById(R.id.next);
            TextView topBar = findViewById(R.id.nextLogin);


            bottomBar.setOnClickListener(v -> {
                Intent intent = new Intent(VKAuthMaruActivity.this, AuthRefreshToken.class);
                startActivity(intent);
            });

            topBar.setOnClickListener(v -> {
            Intent intent = new Intent(VKAuthMaruActivity.this, AuthRefreshToken.class);
            startActivity(intent);
        });


        prefs = getSharedPreferences("VK", MODE_PRIVATE);

        // Проверяем, есть ли сохраненный токен
        String savedToken = prefs.getString("access_token", null);
        if (savedToken != null && !savedToken.isEmpty()) {
            // Если токен есть, сразу переходим в основное приложение
            proceedToMainApp();
            return;
        }

        setupWebView();
    }

    private void setupWebView() {
        webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);

        String userAgent = Authorizer.getKateUserAgent();
        webView.getSettings().setUserAgentString(userAgent);

        webView.setWebViewClient(new VKWebViewClient());
        webView.loadUrl(AUTH_URL_NEW);
    }

    private class VKWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
            return handleUrl(request.getUrl().toString());
        }

        @Override
        public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull String url) {
            return handleUrl(url);
        }

        private boolean handleUrl(String url) {
            if (url.startsWith(REDIRECT_URI)) {
                if (url.contains("access_token=")) {
                    String[] parts = url.split("#");
                    if (parts.length > 1) {
                        String params = parts[1];
                        String[] paramPairs = params.split("&");
                        String accessToken = null;
                        String userId = null;
                        for (String pair : paramPairs) {
                            if (pair.startsWith("access_token=")) {
                                accessToken = pair.substring("access_token=".length());
                            } else if (pair.startsWith("user_id=")) {
                                userId = pair.substring("user_id=".length());
                            }
                        }
                        if (accessToken != null && userId != null) {
                            fetchUserData(accessToken, userId);
                        }
                    }
                }
                return true;
            }
            return false;
        }
    }

    private void fetchUserData(String accessToken, String userId) {
        String url = "https://api.vk.com/method/users.get" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&fields=first_name,last_name";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(VKAuthMaruActivity.this,
                        "Ошибка при получении данных: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        JSONArray responseArray = json.getJSONArray("response");
                        if (responseArray.length() > 0) {
                            JSONObject userObject = responseArray.getJSONObject(0);
                            String firstName = userObject.optString("first_name");
                            String lastName = userObject.optString("last_name");
                            String fetchedUserId = userObject.optString("id");

                            runOnUiThread(() -> onTokenReceived(accessToken, fetchedUserId, firstName + " " + lastName));
                        }
                    } catch (JSONException e) {
                        runOnUiThread(() -> Toast.makeText(VKAuthMaruActivity.this,
                                "Ошибка парсинга: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
                    }
                }
            }
        });
    }

    private void onTokenReceived(String token, String userId, String fullName) {
        prefs.edit()
                .putString("access_token", token)
                .putString("user_id", userId)
                .putString("full_name", fullName)
                .apply();

        proceedToMainApp();
    }

    private void proceedToMainApp() {
        Intent intent = new Intent(this, BaseActivity.class);
        startActivity(intent);
        finish();
    }

    // Добавляем метод для выхода (если нужно)
    public static void logout(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("VK", MODE_PRIVATE);
        prefs.edit()
                .remove("access_token")
                .remove("user_id")
                .remove("full_name")
                .apply();
    }
}