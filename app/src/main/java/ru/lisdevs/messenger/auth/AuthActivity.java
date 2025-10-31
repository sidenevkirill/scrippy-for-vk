package ru.lisdevs.messenger.auth;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Random;

import okhttp3.Request;
import ru.lisdevs.messenger.AuthRefreshToken;
import ru.lisdevs.messenger.BaseActivity;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.VKAuthActivity;
import ru.lisdevs.messenger.utils.TokenManager;
import ru.lisdevs.messenger.utils.VkAuthorizer;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Response;

import android.text.TextUtils;

import java.net.UnknownHostException;

import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.squareup.picasso.Picasso;

import org.json.JSONException;

public class AuthActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etCode, etCaptchaKey;
    private LinearLayout llCode, llCaptcha;
    private ImageView ivCaptcha;
    private Button btnLogin;
    private ProgressBar progressBar;
    private OkHttpClient client;

    private String currentUsername;
    private String currentPassword;
    private String currentCaptchaSid;
    private TokenManager tokenManager;

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
        setContentView(R.layout.activity_auth);

        initHttpClient();
        initTokenManager();
        initViews();
        setupListeners();
        setupTestData();
        checkExistingAuth();

        Button bottomBar = findViewById(R.id.next);
        bottomBar.setOnClickListener(v -> {
            Intent intent = new Intent(AuthActivity.this, AuthRefreshToken.class);
            startActivity(intent);
        });

        Button webAuth = findViewById(R.id.btn_web);
        webAuth.setOnClickListener(v -> {
            Intent intent = new Intent(AuthActivity.this, VKAuthActivity.class);
            startActivity(intent);
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

    private void initViews() {
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etCode = findViewById(R.id.et_code);
        etCaptchaKey = findViewById(R.id.et_captcha_key);
        llCode = findViewById(R.id.ll_code);
        llCaptcha = findViewById(R.id.ll_captcha);
        ivCaptcha = findViewById(R.id.iv_captcha);
        btnLogin = findViewById(R.id.btn_login);
        progressBar = findViewById(R.id.progress_bar);

        // Добавляем кнопку для быстрого тестирования (только для debug сборки)
        if (BuildConfig.DEBUG) {
            setupTestButtons();
        }
    }

    private void setupTestButtons() {
        LinearLayout testButtonsLayout = new LinearLayout(this);
        testButtonsLayout.setOrientation(LinearLayout.VERTICAL);
        testButtonsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Кнопка для заполнения тестовыми данными
        Button btnFillTestData = new Button(this);
        btnFillTestData.setText("Заполнить тестовые данные");
        btnFillTestData.setBackgroundColor(Color.parseColor("#4CAF50"));
        btnFillTestData.setTextColor(Color.WHITE);
        btnFillTestData.setOnClickListener(v -> fillTestCredentials());

        // Кнопка для демо-режима
        Button btnDemoMode = new Button(this);
        btnDemoMode.setText("Демо-режим (без авторизации)");
        btnDemoMode.setBackgroundColor(Color.parseColor("#2196F3"));
        btnDemoMode.setTextColor(Color.WHITE);
        btnDemoMode.setOnClickListener(v -> activateDemoMode());

        testButtonsLayout.addView(btnFillTestData);
        testButtonsLayout.addView(btnDemoMode);

        // Добавляем тестовые кнопки перед основной формой
        @SuppressLint("WrongViewCast") LinearLayout mainLayout = findViewById(R.id.main_layout);
        if (mainLayout != null) {
            mainLayout.addView(testButtonsLayout, 0);
        }
    }

    private void setupTestData() {
        // Автозаполнение тестовыми данными в debug режиме
        if (BuildConfig.DEBUG) {
            new Handler().postDelayed(() -> {
                if (etUsername.getText().toString().isEmpty() &&
                        etPassword.getText().toString().isEmpty()) {
                    fillTestCredentials();
                }
            }, 500);
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

        // Сохраняем демо-данные в VkAuthorizer
        VkAuthorizer.saveUserData(this, DEMO_USER_ID, "demo_user", DEMO_USER_NAME);

        new Handler().postDelayed(() -> {
            setLoadingState(false);
            Toast.makeText(this, "Активирован демо-режим", Toast.LENGTH_LONG).show();
            navigateToMain();
        }, 1500);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String code = etCode.getText().toString().trim();
            String captchaKey = etCaptchaKey.getText().toString().trim();

            if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Введите логин и пароль", Toast.LENGTH_SHORT).show();
                return;
            }

            // Проверка на тестовые данные
            if (isTestCredentials(username, password)) {
                handleTestLogin(username);
                return;
            }

            currentUsername = username;
            currentPassword = password;

            performVkAuth(username, password, code, currentCaptchaSid, captchaKey);
        });

        // Длинное нажатие на кнопку входа для тестового режима
        btnLogin.setOnLongClickListener(v -> {
            if (BuildConfig.DEBUG) {
                showTestModeDialog();
                return true;
            }
            return false;
        });
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

        // Имитация процесса авторизации
        new Handler().postDelayed(() -> {
            String testToken = generateTestToken(username);
            String testUserId = String.valueOf(100000000 + new Random().nextInt(900000000));
            String testUserName = "Тестовый Пользователь " + username.substring(username.length() - 1);

            // Сохраняем тестовые данные в TokenManager
            tokenManager.saveUserData(
                    testToken,
                    testUserId,
                    testUserName,
                    getTestAvatarUrl(username)
            );

            // Сохраняем тестовые данные в VkAuthorizer
            VkAuthorizer.saveUserData(this, testUserId, username, testUserName);

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
        // Возвращаем URL тестового аватара
        return "https://via.placeholder.com/200x200/4CAF50/FFFFFF?text=" +
                URLEncoder.encode(username.substring(0, 1).toUpperCase());
    }

    private void showTestModeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Тестовый режим");
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
        // Быстрый тест с автоматическим входом
        new Handler().postDelayed(() -> {
            String username = etUsername.getText().toString();
            String password = etPassword.getText().toString();

            if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
                btnLogin.performClick();
            }
        }, 500);
    }

    private void checkExistingAuth() {
        if (tokenManager.isTokenValid()) {
            // Проверяем, не демо-ли это токен
            String token = tokenManager.getToken();
            if (token != null && (token.contains("demo") || token.contains("test"))) {
                showWelcomeBackMessage();
            }
            navigateToMain();
        } else if (BuildConfig.DEBUG) {
            // В debug режиме показываем подсказку о тестовых данных
            showTestHint();
        }
    }

    private void showWelcomeBackMessage() {
        String userName = tokenManager.getFullName();
        if (!TextUtils.isEmpty(userName)) {
            Toast.makeText(this, "С возвращением, " + userName + "! (тестовый режим)",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showTestHint() {
        new Handler().postDelayed(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Тестирование приложения");
            builder.setMessage("Для тестирования используйте:\n\n" +
                    "Логин: " + TEST_USERS[0] + "\n" +
                    "Пароль: " + TEST_PASSWORDS[0] + "\n\n" +
                    "Или нажмите и удерживайте кнопку 'Войти' для дополнительных опций.");

            builder.setPositiveButton("Заполнить", (dialog, which) -> fillTestCredentials());
            builder.setNegativeButton("Понятно", null);

            builder.show();
        }, 1000);
    }

    private void performVkAuth(String username, String password, String code, String captchaSid, String captchaKey) {
        setLoadingState(true);

        new Thread(() -> {
            String responseBody = null;
            try {
                if (!VkAuthorizer.validateCredentials(username, password)) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Неверные учетные данные", Toast.LENGTH_SHORT).show();
                        setLoadingState(false);
                    });
                    return;
                }

                String apiUrl = VkAuthorizer.getDirectUrl(username, password, code, captchaSid, captchaKey);
                Log.d("AuthActivity", "Calling URL: " + apiUrl);

                Request request = new Request.Builder()
                        .url(apiUrl)
                        .header("User-Agent", VkAuthorizer.getKateUserAgent())
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    responseBody = response.body() != null ? response.body().string() : "Empty response";

                    if (response.isSuccessful()) {
                        Log.d("AuthActivity", "Success response: " + responseBody);

                        // Сохраняем данные авторизации в VkAuthorizer
                        VkAuthorizer.saveAuthResponseData(AuthActivity.this, responseBody);

                        handleAuthSuccess(responseBody);
                    } else {
                        Log.d("AuthActivity", "Error response: " + responseBody);
                        handleAuthError(response.code(), response.message(), responseBody);
                    }
                }

            } catch (UnknownHostException e) {
                handleNetworkError(e, "Проверьте подключение к интернету");
            } catch (IOException e) {
                handleNetworkError(e, "Ошибка сети: " + e.getMessage());
            } catch (Exception e) {
                handleUnexpectedError(e);
            } finally {
                runOnUiThread(() -> setLoadingState(false));
            }
        }).start();
    }

    private void handleAuthSuccess(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);

            if (json.has("access_token") && json.has("user_id")) {
                String accessToken = json.getString("access_token");
                int userId = json.getInt("user_id");

                // Получаем дополнительную информацию о пользователе
                fetchUserProfile(accessToken, userId);

                saveAuthType("AuthActivity");

            } else if (json.has("validation_type")) {
                String validationType = json.getString("validation_type");
                if ("2fa".equals(validationType)) {
                    runOnUiThread(this::show2FADialog);
                }
            }

        } catch (JSONException e) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Ошибка обработки ответа", Toast.LENGTH_SHORT).show();
                Log.e("AuthActivity", "Error parsing response: " + e.getMessage());
            });
        }
    }

    private void fetchUserProfile(String accessToken, int userId) {
        new Thread(() -> {
            try {
                String url = "https://api.vk.com/method/users.get?user_ids=" + userId +
                        "&fields=photo_200,photo_100&access_token=" + accessToken + "&v=5.131";

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", VkAuthorizer.getKateUserAgent())
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONArray users = json.getJSONArray("response");
                            if (users.length() > 0) {
                                JSONObject user = users.getJSONObject(0);
                                String firstName = user.getString("first_name");
                                String lastName = user.getString("last_name");
                                String fullName = firstName + " " + lastName;

                                // Получаем URL аватарки (пробуем разные размеры)
                                String photoUrl = user.optString("photo_200",
                                        user.optString("photo_100", ""));

                                // Сохраняем все данные через TokenManager
                                tokenManager.saveUserData(
                                        accessToken,
                                        String.valueOf(userId),
                                        fullName,
                                        photoUrl
                                );

                                // Сохраняем данные профиля в VkAuthorizer (включая аватарку)
                                VkAuthorizer.saveProfileData(AuthActivity.this,
                                        String.valueOf(userId), firstName, lastName);

                                // Дополнительно сохраняем URL аватарки в VkAuthorizer
                                // Для этого нужно добавить метод в VkAuthorizer или использовать существующий
                                saveAvatarToVkAuthorizer(photoUrl);

                                Log.d("AuthActivity", "User profile saved: " + fullName + ", Avatar: " + photoUrl);

                                runOnUiThread(this::navigateToMain);
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("AuthActivity", "Error fetching user profile: " + e.getMessage());

                // Если не удалось получить профиль, сохраняем базовые данные
                tokenManager.saveUserData(
                        accessToken,
                        String.valueOf(userId),
                        "Пользователь VK",
                        ""
                );

                // Сохраняем базовые данные в VkAuthorizer
                VkAuthorizer.saveUserData(AuthActivity.this,
                        String.valueOf(userId), "vk_user", "Пользователь VK");

                runOnUiThread(this::navigateToMain);
            }
        }).start();
    }

    // Новый метод для сохранения аватарки в VkAuthorizer
    private void saveAvatarToVkAuthorizer(String avatarUrl) {
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            // Получаем текущие данные пользователя из VkAuthorizer
            VkAuthorizer.UserData userData = VkAuthorizer.getUserData(this);

            if (userData != null) {
                // Создаем новый объект с обновленной аватаркой
                // Для этого нужно расширить класс UserData в VkAuthorizer
                // Или сохранить аватарку отдельно

                // Временное решение: сохраняем аватарку в SharedPreferences
                SharedPreferences prefs = getSharedPreferences("vk_user_data", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("user_avatar_url", avatarUrl);
                editor.apply();

                Log.d("AuthActivity", "Avatar URL saved to VkAuthorizer: " + avatarUrl);
            }
        }
    }

    // Остальные методы остаются без изменений...
    private void handleAuthError(int code, String message, String responseBody) {
        runOnUiThread(() -> {
            Log.e("AuthActivity", "Auth error " + code + ": " + message + "\nResponse: " + responseBody);

            try {
                JSONObject json = new JSONObject(responseBody);

                switch (code) {
                    case 400:
                        handleBadRequestError(json);
                        break;

                    case 401:
                        handleTwoFactorAuthError(json);
                        break;

                    case 402:
                        handleCaptchaError(json);
                        break;

                    default:
                        showGenericError(json);
                }
            } catch (JSONException e) {
                Toast.makeText(this, "Ошибка: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleBadRequestError(JSONObject json) throws JSONException {
        if (json.has("error_description")) {
            String errorDesc = json.getString("error_description");
            if (errorDesc.toLowerCase().contains("captcha")) {
                handleCaptchaError(json);
            } else if (errorDesc.toLowerCase().contains("2fa") || errorDesc.toLowerCase().contains("two-factor")) {
                handleTwoFactorAuthError(json);
            } else {
                Toast.makeText(this, "Неверный логин или пароль", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Неверный логин или пароль", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleTwoFactorAuthError(JSONObject json) {
        try {
            show2FADialog();
            if (json.has("error_description")) {
                String errorDesc = json.getString("error_description");
                if (errorDesc.contains("sms")) {
                    Toast.makeText(this, "Код отправлен в SMS", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Введите код из приложения", Toast.LENGTH_LONG).show();
                }
            }
        } catch (JSONException e) {
            Toast.makeText(this, "Требуется двухфакторная аутентификация", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleCaptchaError(JSONObject json) {
        try {
            if (json.has("captcha_sid") && json.has("captcha_img")) {
                String captchaSid = json.getString("captcha_sid");
                String captchaImg = json.getString("captcha_img");
                showCaptchaDialog(captchaSid, captchaImg);
            } else {
                Toast.makeText(this, "Требуется ввод капчи", Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            Toast.makeText(this, "Ошибка капчи", Toast.LENGTH_SHORT).show();
        }
    }

    private void showGenericError(JSONObject json) throws JSONException {
        if (json.has("error_description")) {
            Toast.makeText(this, "Ошибка: " + json.getString("error_description"), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show();
        }
    }

    private void show2FADialog() {
        llCode.setVisibility(View.VISIBLE);
        etCode.requestFocus();
        Toast.makeText(this, "Введите код двухфакторной аутентификации", Toast.LENGTH_LONG).show();
    }

    private void showCaptchaDialog(String captchaSid, String captchaImg) {
        currentCaptchaSid = captchaSid;

        runOnUiThread(() -> {
            llCaptcha.setVisibility(View.VISIBLE);
            Picasso.get().load(captchaImg).into(ivCaptcha);
            etCaptchaKey.setText("");
            etCaptchaKey.requestFocus();
            Toast.makeText(this, "Введите текст с картинки", Toast.LENGTH_LONG).show();
        });
    }

    private void handleNetworkError(Exception e, String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            Log.e("AuthActivity", "Network error: " + e.getMessage());
        });
    }

    private void handleUnexpectedError(Exception e) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Неожиданная ошибка", Toast.LENGTH_SHORT).show();
            Log.e("AuthActivity", "Unexpected error: " + e.getMessage(), e);
        });
    }

    private void setLoadingState(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Авторизация..." : "Войти");

        // В debug режиме показываем дополнительную информацию
        if (BuildConfig.DEBUG && loading) {
            btnLogin.setText("Тестирование...");
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, BaseActivity.class);
        startActivity(intent);
        finish();

        // Анимация перехода
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
    }

    // Метод для выхода (очистки данных авторизации)
    public void logout() {
        tokenManager.clearAuthData();
        VkAuthorizer.clearUserData(this);

        // Очищаем поля формы
        runOnUiThread(() -> {
            etUsername.setText("");
            etPassword.setText("");
            etCode.setText("");
            etCaptchaKey.setText("");
            llCode.setVisibility(View.GONE);
            llCaptcha.setVisibility(View.GONE);
        });
    }

    private void saveAuthType(String authType) {
        SharedPreferences prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE);
        prefs.edit().putString("auth_type", authType).apply();
        Log.d("AuthActivity", "Auth type saved: " + authType);
    }

}