package ru.lisdevs.messenger;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etLogin, etPassword;
    private Button btnLogin;

    // Ваши параметры
    private static final String CLIENT_ID = "2274003"; // замените на ваш client_id
    private static final String CLIENT_SECRET = "lxhD8OD7dMsqtXIm5IUY"; // секретный ключ
    private static final String V = "5.131f";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etLogin = findViewById(R.id.etLogin);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> {
            String login = etLogin.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (login.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Пожалуйста, введите логин и пароль", Toast.LENGTH_SHORT).show();
                return;
            }

            // Выполнить запрос на получение токена по grant_type=password
            fetchAccessToken(login, password);
        });
    }

    private void fetchAccessToken(String username, String password) {
        OkHttpClient client = new OkHttpClient();

        String url = "https://oauth.vk.com/access_token";

        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "password")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("username", username)
                .add("password", password)
                .add("v", V)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Ошибка сети: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String bodyStr = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(bodyStr);
                        if (json.has("access_token")) {
                            String token = json.getString("access_token");
                            String userId = json.optString("user_id");
                            runOnUiThread(() -> {
                                SharedPreferences prefs = getSharedPreferences("VK", MODE_PRIVATE);
                                prefs.edit()
                                        .putString("access_token", token)
                                        .putString("user_id", userId)
                                        .apply();

                                Intent intent = new Intent(LoginActivity.this, BaseActivity.class);
                                startActivity(intent);
                                finish();
                            });
                        } else if (json.has("error")) {
                            String errorMsg = json.optString("error_description", "Ошибка авторизации");
                            runOnUiThread(() -> Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show());
                        }
                    } catch (JSONException e) {
                        runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Ошибка парсинга ответа", Toast.LENGTH_LONG).show());
                    }
                } else {
                    // Логируем тело ответа для диагностики
                    Log.e("VKAuth", "Ошибка ответа: " + bodyStr);
                    runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Ответ сервера не успешен: " + response.code(), Toast.LENGTH_LONG).show());
                }
            }
        });
    }
}
