package ru.lisdevs.messenger.auth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.BuildConfig;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.AuthRefreshToken;
import ru.lisdevs.messenger.BaseActivity;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.utils.TokenManager;

public class KateAuthActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etCode, etCaptchaKey;
    private LinearLayout llCode, llCaptcha;
    private MaterialCardView cardNewCaptcha;
    private ImageView ivCaptcha;
    private WebView webViewCaptcha;
    private MaterialButton btnLogin, btnRetryCaptcha, btnFillTest, btnDemoMode;
    private LinearProgressIndicator progressBar;
    private MaterialCardView cardTestMode;
    private OkHttpClient client;

    private String currentUsername;
    private String currentPassword;
    private String currentCaptchaSid;
    private TokenManager tokenManager;

    // Константы для Kate Mobile
    private static final int KATE_CLIENT_ID = 2685278;
    private static final String KATE_CLIENT_SECRET = "lxhD8OD7dMsqtXIm5IUY";
    private static final float KATE_API_VERSION = 5.199f;
    private static final String KATE_USER_AGENT = "KateMobileAndroid/52.2 lite-445 (Android 8.1.0; SDK 27; arm64-v8a; Xiaomi Redmi Note 5; ru)";

    // Тестовые учетные данные
    private static final String[] TEST_USERS = {
            "testuser", "demo_user", "vk_test", "android_test"
    };

    private static final String[] TEST_PASSWORDS = {
            "test123", "demo_password", "test_vk_2024", "android123"
    };

    // Тестовый токен для демо-режима
    private static final String DEMO_ACCESS_TOKEN = "vk1.a.demo_token_1234567890abcdef";
    private static final String DEMO_USER_ID = "123456789";
    private static final String DEMO_USER_NAME = "Демо Пользователь";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth); // Используем ваш существующий layout

        initViews();
        initHttpClient();
        initTokenManager();
        setupListeners();
        setupTestMode();

        // Изменяем заголовки для Kate авторизации
        updateUIForKateAuth();
    }

    @SuppressLint("WrongViewCast")
    private void initViews() {
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etCode = findViewById(R.id.et_code);
        etCaptchaKey = findViewById(R.id.et_captcha_key);
        llCode = findViewById(R.id.ll_code);
        llCaptcha = findViewById(R.id.ll_captcha);
        cardNewCaptcha = findViewById(R.id.ll_new_captcha);
        ivCaptcha = findViewById(R.id.iv_captcha);
        webViewCaptcha = findViewById(R.id.webViewCaptcha);
        btnLogin = findViewById(R.id.btn_login);
        btnRetryCaptcha = findViewById(R.id.btn_retry_captcha);
        btnFillTest = findViewById(R.id.btn_fill_test);
        btnDemoMode = findViewById(R.id.btn_demo_mode);
        progressBar = findViewById(R.id.progress_bar);
        cardTestMode = findViewById(R.id.card_test_mode);

        // Скрываем дополнительные поля по умолчанию
        llCode.setVisibility(View.GONE);
        llCaptcha.setVisibility(View.GONE);
        cardNewCaptcha.setVisibility(View.GONE);

        // Настраиваем WebView для новой капчи
        setupWebViewCaptcha();
    }

    private void updateUIForKateAuth() {
        // Обновляем заголовки для Kate авторизации
        TextView title = findViewById(R.id.textView); // Замените на ваш ID заголовка
        if (title != null) {
            title.setText("Авторизация через Kate Mobile");
        }

        // Обновляем текст кнопки
        btnLogin.setText("Войти через Kate");

        // Показываем тестовый режим в DEBUG
        if (BuildConfig.DEBUG && cardTestMode != null) {
            cardTestMode.setVisibility(View.VISIBLE);
        }
    }

    private void setupWebViewCaptcha() {
        WebSettings webSettings = webViewCaptcha.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webViewCaptcha.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d("KateAuth", "Captcha page loaded: " + url);
            }
        });
    }

    private void initHttpClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private void initTokenManager() {
        tokenManager = TokenManager.getInstance(this);
    }

    private void setupTestMode() {
        if (BuildConfig.DEBUG) {
            // Автозаполнение тестовыми данными в debug режиме
            new Handler().postDelayed(() -> {
                if (etUsername.getText().toString().isEmpty() &&
                        etPassword.getText().toString().isEmpty()) {
                    fillTestCredentials();
                }
            }, 500);
        }
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());

        // Длинное нажатие для тестового режима
        btnLogin.setOnLongClickListener(v -> {
            if (BuildConfig.DEBUG) {
                showTestModeDialog();
                return true;
            }
            return false;
        });

        // Кнопки тестового режима
        if (btnFillTest != null) {
            btnFillTest.setOnClickListener(v -> fillTestCredentials());
        }

        if (btnDemoMode != null) {
            btnDemoMode.setOnClickListener(v -> activateDemoMode());
        }

        // Кнопка повтора капчи
        if (btnRetryCaptcha != null) {
            btnRetryCaptcha.setOnClickListener(v -> retryCaptcha());
        }

        // Обработка нажатия Enter в полях
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });

        // Обновление капчи по клику
        ivCaptcha.setOnClickListener(v -> {
            if (currentCaptchaSid != null) {
                loadCaptchaImage(currentCaptchaSid);
            }
        });

        // Навигация
        Button btnNext = findViewById(R.id.next);
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                Intent intent = new Intent(KateAuthActivity.this, AuthRefreshToken.class);
                startActivity(intent);
            });
        }
    }

    private void fillTestCredentials() {
        Random random = new Random();
        int index = random.nextInt(TEST_USERS.length);

        etUsername.setText(TEST_USERS[index]);
        etPassword.setText(TEST_PASSWORDS[index]);

        Toast.makeText(this, "Заполнены тестовые данные", Toast.LENGTH_SHORT).show();
    }

    private void activateDemoMode() {
        setLoadingState(true);

        // Сохраняем демо-данные в TokenManager
        tokenManager.saveUserData(
                DEMO_ACCESS_TOKEN,
                DEMO_USER_ID,
                DEMO_USER_NAME,
                "https://sun9-71.userapi.com/impg/6ggylt_E97y2vqB32Vv-6s0R6QH8Vv6Q5s2w3A/7b0KJ7q3Z4A.jpg?size=200x200&quality=96&sign=1234567890abcdef"
        );

        new Handler().postDelayed(() -> {
            setLoadingState(false);
            Toast.makeText(this, "Активирован демо-режим", Toast.LENGTH_LONG).show();
            navigateToMain();
        }, 1500);
    }

    private void showTestModeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Тестовый режим Kate");
        builder.setMessage("Выберите тип тестирования:");

        builder.setPositiveButton("Быстрый тест", (dialog, which) -> {
            fillTestCredentials();
            performQuickTest();
        });

        builder.setNegativeButton("Демо-режим", (dialog, which) -> {
            activateDemoMode();
        });

        builder.setNeutralButton("Отмена", null);
        builder.show();
    }

    private void performQuickTest() {
        new Handler().postDelayed(() -> {
            String username = etUsername.getText().toString();
            String password = etPassword.getText().toString();

            if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
                btnLogin.performClick();
            }
        }, 500);
    }

    private void attemptLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String code = etCode.getText().toString().trim();
        String captchaKey = etCaptchaKey.getText().toString().trim();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            showError("Введите логин и пароль");
            return;
        }

        // Проверка на тестовые данные
        if (isTestCredentials(username, password)) {
            handleTestLogin(username);
            return;
        }

        currentUsername = username;
        currentPassword = password;

        performKateAuth(username, password, code, currentCaptchaSid, captchaKey);
    }

    private boolean isTestCredentials(String username, String password) {
        for (int i = 0; i < TEST_USERS.length; i++) {
            if (TEST_USERS[i].equals(username) && TEST_PASSWORDS[i].equals(password)) {
                return true;
            }
        }
        return false;
    }

    private void handleTestLogin(String username) {
        setLoadingState(true);

        new Handler().postDelayed(() -> {
            String testToken = generateTestToken(username);
            String testUserId = String.valueOf(100000000 + new Random().nextInt(900000000));
            String testUserName = "Тестовый Пользователь " + username.substring(username.length() - 1);

            tokenManager.saveUserData(
                    testToken,
                    testUserId,
                    testUserName,
                    getTestAvatarUrl(username)
            );

            setLoadingState(false);
            Toast.makeText(this, "Тестовый вход выполнен успешно!", Toast.LENGTH_LONG).show();
            navigateToMain();
        }, 2000);
    }

    private String generateTestToken(String username) {
        return "vk1.a.test_" + username + "_" +
                System.currentTimeMillis() +
                "_" + new Random().nextInt(1000000);
    }

    private String getTestAvatarUrl(String username) {
        return "https://via.placeholder.com/200x200/4CAF50/FFFFFF?text=" +
                URLEncoder.encode(username.substring(0, 1).toUpperCase());
    }

    private void performKateAuth(String username, String password, String code, String captchaSid, String captchaKey) {
        setLoadingState(true);

        new Thread(() -> {
            try {
                String authUrl = buildAuthUrl(username, password, code, captchaSid, captchaKey);
                Log.d("KateAuth", "Auth URL: " + authUrl);

                Request request = new Request.Builder()
                        .url(authUrl)
                        .header("User-Agent", KATE_USER_AGENT)
                        .header("Accept", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : null;

                    if (response.isSuccessful() && responseBody != null) {
                        Log.d("KateAuth", "Response: " + responseBody);
                        handleAuthResponse(responseBody);
                    } else {
                        handleAuthError(response.code(), responseBody);
                    }
                }

            } catch (IOException e) {
                runOnUiThread(() -> {
                    showError("Ошибка сети: " + e.getMessage());
                    setLoadingState(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showError("Неожиданная ошибка");
                    setLoadingState(false);
                    Log.e("KateAuth", "Unexpected error", e);
                });
            }
        }).start();
    }

    private String buildAuthUrl(String username, String password, String code, String captchaSid, String captchaKey) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse("https://oauth.vk.com/token").newBuilder()
                .addQueryParameter("grant_type", "password")
                .addQueryParameter("client_id", String.valueOf(KATE_CLIENT_ID))
                .addQueryParameter("client_secret", KATE_CLIENT_SECRET)
                .addQueryParameter("username", username)
                .addQueryParameter("password", password)
                .addQueryParameter("scope", "all")
                .addQueryParameter("v", String.valueOf(KATE_API_VERSION))
                .addQueryParameter("2fa_supported", "1");

        if (!TextUtils.isEmpty(code)) {
            urlBuilder.addQueryParameter("code", code);
        }

        if (!TextUtils.isEmpty(captchaSid) && !TextUtils.isEmpty(captchaKey)) {
            urlBuilder.addQueryParameter("captcha_sid", captchaSid);
            urlBuilder.addQueryParameter("captcha_key", captchaKey);
        }

        return urlBuilder.build().toString();
    }

    private void handleAuthResponse(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);

            if (json.has("access_token")) {
                // Успешная авторизация
                String accessToken = json.getString("access_token");
                String userId = json.getString("user_id");
                String email = json.optString("email", "");

                // Получаем информацию о пользователе
                fetchUserProfile(accessToken, userId);

            } else if (json.has("error")) {
                handleAuthError(json);
            } else {
                runOnUiThread(() -> {
                    showError("Неизвестный ответ от сервера");
                    setLoadingState(false);
                });
            }

        } catch (JSONException e) {
            runOnUiThread(() -> {
                showError("Ошибка обработки ответа");
                setLoadingState(false);
                Log.e("KateAuth", "JSON parsing error", e);
            });
        }
    }

    private void handleAuthError(JSONObject errorJson) {
        try {
            String errorType = errorJson.getString("error");
            String errorDescription = errorJson.optString("error_description", "");

            switch (errorType) {
                case "need_validation":
                    handleTwoFactorAuth(errorJson);
                    break;

                case "need_captcha":
                    handleCaptcha(errorJson);
                    break;

                case "invalid_client":
                case "invalid_request":
                    runOnUiThread(() -> {
                        showError("Неверный логин или пароль");
                        setLoadingState(false);
                    });
                    break;

                default:
                    runOnUiThread(() -> {
                        showError("Ошибка: " + errorDescription);
                        setLoadingState(false);
                    });
            }

        } catch (JSONException e) {
            runOnUiThread(() -> {
                showError("Ошибка авторизации");
                setLoadingState(false);
            });
        }
    }

    private void handleAuthError(int statusCode, String responseBody) {
        runOnUiThread(() -> {
            switch (statusCode) {
                case 400:
                    showError("Неверный запрос");
                    break;
                case 401:
                    showError("Ошибка авторизации");
                    break;
                case 403:
                    showError("Доступ запрещен");
                    break;
                case 429:
                    showError("Слишком много запросов");
                    break;
                default:
                    showError("Ошибка сервера: " + statusCode);
            }
            setLoadingState(false);
        });
    }

    private void handleTwoFactorAuth(JSONObject errorJson) throws JSONException {
        String validationType = errorJson.optString("validation_type", "2fa");

        runOnUiThread(() -> {
            llCode.setVisibility(View.VISIBLE);
            etCode.requestFocus();

            if ("2fa_app".equals(validationType)) {
                showMessage("Введите код из приложения");
            } else if ("2fa_sms".equals(validationType)) {
                showMessage("Код отправлен в SMS");
            } else {
                showMessage("Введите код двухфакторной аутентификации");
            }

            setLoadingState(false);
        });
    }

    private void handleCaptcha(JSONObject errorJson) throws JSONException {
        currentCaptchaSid = errorJson.getString("captcha_sid");
        String captchaImg = errorJson.getString("captcha_img");

        runOnUiThread(() -> {
            llCaptcha.setVisibility(View.VISIBLE);
            loadCaptchaImage(captchaImg);
            etCaptchaKey.requestFocus();
            showMessage("Введите текст с картинки");
            setLoadingState(false);
        });
    }

    private void loadCaptchaImage(String captchaImg) {
        Picasso.get()
                .load(captchaImg)
                .placeholder(R.drawable.refresh)
                .error(R.drawable.ic_photo_error)
                .into(ivCaptcha);
    }

    private void retryCaptcha() {
        // Реализация повторной загрузки капчи
        if (currentCaptchaSid != null) {
            loadCaptchaImage(currentCaptchaSid);
        }
    }

    private void fetchUserProfile(String accessToken, String userId) {
        String url = "https://api.vk.com/method/users.get" +
                "?user_ids=" + userId +
                "&fields=photo_200,photo_100,first_name,last_name" +
                "&access_token=" + accessToken +
                "&v=" + KATE_API_VERSION;

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", KATE_USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    showError("Ошибка получения профиля");
                    setLoadingState(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        JSONArray users = json.getJSONArray("response");

                        if (users.length() > 0) {
                            JSONObject user = users.getJSONObject(0);
                            String firstName = user.getString("first_name");
                            String lastName = user.getString("last_name");
                            String fullName = firstName + " " + lastName;
                            String photoUrl = user.optString("photo_200",
                                    user.optString("photo_100", ""));

                            saveUserData(accessToken, userId, fullName, photoUrl);

                            runOnUiThread(() -> {
                                showSuccess("Успешная авторизация через Kate!");
                                navigateToMain();
                            });
                            return;
                        }
                    } catch (JSONException e) {
                        Log.e("KateAuth", "Error parsing profile", e);
                    }
                }

                // Если не удалось получить профиль, сохраняем базовые данные
                saveUserData(accessToken, userId, "Пользователь VK", "");
                runOnUiThread(() -> {
                    showSuccess("Успешная авторизация через Kate!");
                    navigateToMain();
                });
            }
        });
    }

    private void saveUserData(String token, String userId, String fullName, String photoUrl) {
        tokenManager.saveUserData(token, userId, fullName, photoUrl);

        SharedPreferences prefs = getSharedPreferences("vk_auth", MODE_PRIVATE);
        prefs.edit()
                .putString("access_token", token)
                .putString("user_id", userId)
                .putString("full_name", fullName)
                .putString("photo_url", photoUrl)
                .putLong("login_time", System.currentTimeMillis())
                .apply();
    }

    private void setLoadingState(boolean loading) {
        runOnUiThread(() -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            btnLogin.setEnabled(!loading);
            btnLogin.setText(loading ? "Авторизация Kate..." : "Войти через Kate");

            // Блокируем поля ввода во время загрузки
            etUsername.setEnabled(!loading);
            etPassword.setEnabled(!loading);
            etCode.setEnabled(!loading);
            etCaptchaKey.setEnabled(!loading);
        });
    }

    private void showError(String message) {
        runOnUiThread(() ->
                Toast.makeText(KateAuthActivity.this, message, Toast.LENGTH_LONG).show()
        );
    }

    private void showMessage(String message) {
        runOnUiThread(() ->
                Toast.makeText(KateAuthActivity.this, message, Toast.LENGTH_SHORT).show()
        );
    }

    private void showSuccess(String message) {
        runOnUiThread(() -> {
            Toast.makeText(KateAuthActivity.this, message, Toast.LENGTH_SHORT).show();
            setLoadingState(false);
        });
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, BaseActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
    }

    // Метод для выхода
    public void logout() {
        tokenManager.clearAuthData();

        SharedPreferences prefs = getSharedPreferences("vk_auth", MODE_PRIVATE);
        prefs.edit().clear().apply();

        runOnUiThread(() -> {
            etUsername.setText("");
            etPassword.setText("");
            etCode.setText("");
            etCaptchaKey.setText("");
            llCode.setVisibility(View.GONE);
            llCaptcha.setVisibility(View.GONE);
            cardNewCaptcha.setVisibility(View.GONE);
        });
    }
}