package ru.lisdevs.messenger;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import ru.lisdevs.messenger.utils.TokenManager;

import okhttp3.MediaType;
import okhttp3.RequestBody;

public class AuthRefreshToken extends AppCompatActivity {

    private EditText tokenEditText;
    private Button useTokenButton, buttonEnterToken;
    private TokenManager tokenManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vk_auth_without_webview);

        // Инициализация TokenManager
        tokenManager = TokenManager.getInstance(this);

        // Инициализация UI элементов
        initViews();

        // Проверка существующего токена
        checkExistingToken();
    }

    private void initViews() {
        tokenEditText = findViewById(R.id.tokenEditText);
        useTokenButton = findViewById(R.id.useTokenButton);

        useTokenButton.setOnClickListener(v -> {
            String token = tokenEditText.getText().toString().trim();
            if (!token.isEmpty()) {
                fetchUserData(token);
            } else {
                showToast("Пожалуйста, введите access_token");
            }
        });
    }

    private void checkExistingToken() {
        if (tokenManager.isTokenValid()) {
            navigateToMainActivity();
        }
    }

    private void showTokenInputBottomSheet() {
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_token_input, null);

        EditText bottomSheetTokenEdit = view.findViewById(R.id.tokenEditText);
        Button bottomSheetUseButton = view.findViewById(R.id.useTokenButton);

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.setCancelable(false);

        bottomSheetUseButton.setOnClickListener(v -> {
            String token = bottomSheetTokenEdit.getText().toString().trim();
            if (!token.isEmpty()) {
                fetchUserData(token);
                bottomSheetDialog.dismiss();
            } else {
                showToast("Пожалуйста, введите access_token");
            }
        });

        bottomSheetDialog.show();
    }

    private void fetchUserData(String accessToken) {
        String url = "https://api.vk.com/method/users.get" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&fields=first_name,last_name,photo_100";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        showToast("Ошибка при получении данных: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        JSONArray responseArray = json.getJSONArray("response");

                        if (responseArray.length() > 0) {
                            JSONObject userObject = responseArray.getJSONObject(0);
                            String firstName = userObject.optString("first_name", "");
                            String lastName = userObject.optString("last_name", "");
                            String userId = userObject.optString("id", "");
                            String photoUrl = userObject.optString("photo_100", "");

                            // Сохраняем данные через TokenManager
                            tokenManager.saveUserData(accessToken, userId, firstName + " " + lastName, photoUrl);

                            runOnUiThread(() -> {
                                showToast("Добро пожаловать, " + firstName + " " + lastName);
                                navigateToMainActivity();
                            });
                        }
                    } catch (JSONException e) {
                        runOnUiThread(() ->
                                showToast("Ошибка обработки данных: " + e.getMessage()));
                    }
                } else {
                    runOnUiThread(() ->
                            showToast("Ошибка сервера: " + response.code()));
                }
            }
        });
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(this, BaseActivity.class);
        startActivity(intent);
        finish();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}