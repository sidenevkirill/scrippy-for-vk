package ru.lisdevs.messenger;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.server.Evatas;
import ru.lisdevs.messenger.server.EvatasConfig;
import ru.lisdevs.messenger.utils.HttpClientManager;
import ru.lisdevs.messenger.utils.HttpClientSingleton;
import ru.lisdevs.messenger.utils.TokenManager;

import okhttp3.MediaType;
import okhttp3.RequestBody;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AuthRefreshToken extends AppCompatActivity {

    private EditText tokenEditText;
    private Button useTokenButton;
    private TokenManager tokenManager;
    private ProgressBar progressBar;
    private OkHttpClient httpClient;

    // Для внутренней коммуникации
    private final BroadcastReceiver authReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("AUTH_TOKEN_REFRESHED".equals(intent.getAction())) {
                boolean success = intent.getBooleanExtra("success", false);
                if (success) {
                    navigateToMainActivity();
                } else {
                    showToast("Не удалось обновить токен");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vk_auth_without_webview);

        // Регистрируем локальный receiver
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(authReceiver,
                        new IntentFilter("AUTH_TOKEN_REFRESHED"));

        tokenManager = TokenManager.getInstance(this);

        // Инициализируем HTTP клиент
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        initViews();
        checkExistingToken();
        setupTokenValidation();
    }

    private void initViews() {
        tokenEditText = findViewById(R.id.tokenEditText);
        useTokenButton = findViewById(R.id.useTokenButton);
        progressBar = findViewById(R.id.progressBar);

        // Начальное состояние кнопки
        useTokenButton.setEnabled(false);
        useTokenButton.setAlpha(0.5f);

        useTokenButton.setOnClickListener(v -> {
            String token = tokenEditText.getText().toString().trim();
            if (!token.isEmpty()) {
                authenticateWithToken(token);
            } else {
                showToast("Пожалуйста, введите access_token");
            }
        });

        // Кнопка "Как получить токен?"
        Button helpButton = findViewById(R.id.helpButton);
        if (helpButton != null) {
            helpButton.setOnClickListener(v -> showTokenHelpBottomSheet());
        }

        // Кнопка очистки
        Button clearButton = findViewById(R.id.clearButton);
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> tokenEditText.setText(""));
        }
    }

    private void setupTokenValidation() {
        tokenEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String token = s.toString().trim();
                boolean isValidFormat = isValidTokenFormat(token);

                useTokenButton.setEnabled(!token.isEmpty() && isValidFormat);
                useTokenButton.setAlpha(!token.isEmpty() && isValidFormat ? 1.0f : 0.5f);

                if (!token.isEmpty() && !isValidFormat) {
                    tokenEditText.setError("Неверный формат токена");
                } else {
                    tokenEditText.setError(null);
                }
            }
        });
    }

    private boolean isValidTokenFormat(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        String trimmedToken = token.trim();

        // Проверка длины токена
        if (trimmedToken.length() < 60 || trimmedToken.length() > 300) {
            return false;
        }

        // Проверка на минимальное количество буквенно-цифровых символов
        int alphanumericCount = 0;
        for (char c : trimmedToken.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                alphanumericCount++;
            }
        }

        if (alphanumericCount < 10) {
            return false;
        }

        // Проверка на спецсимволы
        if (trimmedToken.contains("\n") || trimmedToken.contains("\r")) {
            return false;
        }

        return true;
    }

    private void checkExistingToken() {
        if (tokenManager.isTokenValid()) {
            // Если токен валидный, сразу переходим в приложение
            navigateToMainActivity();
        } else if (tokenManager.getToken() != null) {
            // Если токен есть, но невалидный, предлагаем обновить
            showTokenExpiredDialog();
        }
    }

    private void showTokenExpiredDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_token_expired, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        view.findViewById(R.id.buttonReauth).setOnClickListener(v -> {
            dialog.dismiss();
            // Очищаем старый токен
            tokenManager.clearAuthData();
        });

        view.findViewById(R.id.buttonTryAgain).setOnClickListener(v -> {
            dialog.dismiss();
            // Пробуем принудительно обновить токен в фоне
            String refreshToken = tokenManager.getRefreshToken();
            if (refreshToken != null) {
                refreshTokenInBackground(refreshToken);
            }
        });

        dialog.show();
    }

    private void authenticateWithToken(String token) {
        if (!isValidTokenFormat(token)) {
            showToast("Неверный формат токена. Пожалуйста, проверьте правильность ввода.");
            return;
        }

        showProgress(true);

        // Проверяем токен через API users.get
        String url = "https://api.vk.com/method/users.get" +
                "?access_token=" + token +
                "&v=5.199" +
                "&fields=first_name,last_name,photo_200,photo_100,domain" +
                "&lang=ru";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "VKAndroidApp/5.52-4543")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showProgress(false);
                    showToast("Ошибка сети: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        // Проверяем на ошибку API
                        if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            int errorCode = error.getInt("error_code");
                            String errorMsg = error.getString("error_msg");

                            runOnUiThread(() -> {
                                showProgress(false);
                                handleApiError(errorCode, errorMsg);
                            });
                            return;
                        }

                        // Проверяем успешный ответ
                        if (json.has("response")) {
                            JSONArray responseArray = json.getJSONArray("response");

                            if (responseArray.length() > 0) {
                                JSONObject userObject = responseArray.getJSONObject(0);
                                String userId = userObject.optString("id", "");
                                String firstName = userObject.optString("first_name", "");
                                String lastName = userObject.optString("last_name", "");
                                String photoUrl = userObject.optString("photo_200",
                                        userObject.optString("photo_100", ""));
                                String domain = userObject.optString("domain", "");

                                runOnUiThread(() -> {
                                    showProgress(false);
                                    fetchTokenPermissions(token, userId, firstName, lastName, photoUrl);
                                });
                            } else {
                                runOnUiThread(() -> {
                                    showProgress(false);
                                    showToast("Ошибка: пустой ответ от сервера");
                                });
                            }
                        } else {
                            runOnUiThread(() -> {
                                showProgress(false);
                                showToast("Ошибка: неожиданный формат ответа");
                            });
                        }

                    } catch (JSONException e) {
                        runOnUiThread(() -> {
                            showProgress(false);
                            showToast("Ошибка обработки данных: " + e.getMessage());
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        showProgress(false);
                        showToast("Ошибка сервера: " + response.code());
                    });
                }
            }
        });
    }

    private void fetchTokenPermissions(String token, String userId,
                                       String firstName, String lastName, String photoUrl) {
        showProgress(true);

        // Проверяем, какие права есть у токена
        String url = "https://api.vk.com/method/account.getInfo" +
                "?access_token=" + token +
                "&v=5.199";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "VKAndroidApp/5.52-4543")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Если не удалось проверить права, все равно сохраняем токен
                runOnUiThread(() -> {
                    showProgress(false);
                    saveTokenAndNavigate(token, userId, firstName, lastName, photoUrl);
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                boolean hasAudioPermission = true;

                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONObject info = json.getJSONObject("response");
                            // Можно добавить проверку конкретных прав
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки при проверке прав
                    }
                }

                runOnUiThread(() -> {
                    showProgress(false);
                    saveTokenAndNavigate(token, userId, firstName, lastName, photoUrl);

                    if (!hasAudioPermission) {
                        showToast("Внимание: токен может не иметь прав доступа к музыке");
                    }
                });
            }
        });
    }

    private void saveTokenAndNavigate(String vkToken, String vkUserId,
                                      String firstName, String lastName, String photoUrl) {
        String fullName = firstName + " " + lastName;

        // Сохраняем данные пользователя
        tokenManager.saveUserData(vkToken, vkUserId, fullName, photoUrl);

        // Сохраняем дополнительную информацию
        SharedPreferences prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("auth_type", "ManualToken");
        editor.putString("token_source", "manual_token");
        editor.putString("user_agent", "VKAndroidApp/5.52-4543");
        editor.putLong("auth_time", System.currentTimeMillis());
        editor.apply();

        // Показываем успешное сообщение и переходим
        showToast("Добро пожаловать, " + firstName + "!");
        navigateToMainActivity();
    }

    private void handleApiError(int errorCode, String errorMsg) {
        switch (errorCode) {
            case 5:
                showToast("Неверный токен. Пожалуйста, проверьте правильность токена.");
                break;
            case 6:
                showToast("Слишком много запросов. Попробуйте позже.");
                break;
            case 10:
                showToast("Внутренняя ошибка сервера. Попробуйте позже.");
                break;
            case 15:
                showToast("Доступ запрещен. Токен недействителен.");
                break;
            case 17:
                showToast("Требуется проверка пользователя.");
                break;
            default:
                showToast("Ошибка API (" + errorCode + "): " + errorMsg);
        }

        // Очищаем поле ввода при критических ошибках
        if (errorCode == 5 || errorCode == 15) {
            tokenEditText.setText("");
            tokenEditText.requestFocus();
        }
    }

    private void refreshTokenInBackground(String refreshToken) {
        showProgress(true);

        // Используем фоновый поток для обновления токена
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Имитация обновления токена (замените на реальную логику)
                Thread.sleep(2000);

                // Отправляем результат через локальный broadcast
                Intent intent = new Intent("AUTH_TOKEN_REFRESHED");
                intent.putExtra("success", false);
                LocalBroadcastManager.getInstance(AuthRefreshToken.this).sendBroadcast(intent);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                runOnUiThread(() -> showProgress(false));
            }
        });
    }

    private void showTokenHelpBottomSheet() {
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_token_help, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(view);
        dialog.show();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        useTokenButton.setEnabled(!show);
        tokenEditText.setEnabled(!show);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(this, BaseActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Отменяем регистрацию receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(authReceiver);

        // Отменяем все активные запросы этой активности
        if (httpClient != null) {
            httpClient.dispatcher().cancelAll();
        }
    }
}