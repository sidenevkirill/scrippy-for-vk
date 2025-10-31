package ru.lisdevs.messenger;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.api.Authorizer;

public class VkTokenAuthActivity extends AppCompatActivity {

    private static final String REDIRECT_URI = "https://oauth.vk.com/blank.html";
    private static final String SCOPE = "friends";
    private static final String TAG = "VkAuthActivity";
    private static final String CLIENT_ID = "2685278";
    private static final String CLIENT_SECRET = "lxhD8OD7dMsqtXIm5IUY";
    private static final String API_VERSION = "5.131";
    private static final int MAX_CAPTCHA_ATTEMPTS = 3;

    private TextInputEditText loginEditText;
    private TextInputEditText passwordEditText;
    private Button loginButton;
    private ProgressBar progressBar;
    private OkHttpClient client;

    private String currentLogin;
    private String currentPassword;
    private String validationSid;
    private String captchaSid;
    private String captchaImg;
    private String captchaKey;
    private int captchaAttempts = 0;

    private Button buttonEnterToken, buttonVKLoginApp, useTokenButton, buttonVKLoginNewApp, buttonFriends;
    private TextView textStatus, buttonVKLogin;
    private EditText tokenEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        buttonEnterToken = findViewById(R.id.buttonEnterToken);
        buttonVKLogin = findViewById(R.id.buttonVKLoginBrowser);
        buttonVKLoginApp = findViewById(R.id.buttonVKLogin);
  //      buttonVKLoginNewApp = findViewById(R.id.buttonVKLoginNewApp);
        //   textStatus = findViewById(R.id.textStatus);

        buttonVKLogin.setOnClickListener(v -> vkFriends());
        buttonVKLoginApp.setOnClickListener(v -> goVkAuth());
        buttonEnterToken.setOnClickListener(v -> showTokenInputBottomSheet());
      //  buttonFriends.setOnClickListener(v -> vkFriends());

        // Можно раскомментировать, чтобы автоматически проверять сохранённый токен при запуске
        checkStoredToken();
        initializeViews();
        setupHttpClient();
        setupLoginButton();
    }

    private void showTokenInputBottomSheet() {
        // Создаем View из layout
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_token_input, null);

        // Находим элементы
        tokenEditText = view.findViewById(R.id.tokenEditText);
        useTokenButton = view.findViewById(R.id.useTokenButton);

        // Создаем BottomSheetDialog
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.setCancelable(true); // Запрещаем закрытие по клику вне диалога

        // Обработчик кнопки
        useTokenButton.setOnClickListener(v -> {
            String token = tokenEditText.getText().toString().trim();
            if (!token.isEmpty()) {
                fetchUserToken(token);
            } else {
                Toast.makeText(this, "Пожалуйста, введите access_token", Toast.LENGTH_SHORT).show();
            }
        });

        // Показываем BottomSheet
        bottomSheetDialog.show();
    }

    private void fetchUserToken(String accessToken) {
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
                runOnUiThread(() -> Toast.makeText(VkTokenAuthActivity.this, "Ошибка при получении данных: " + e.getMessage(), Toast.LENGTH_LONG).show());
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
                            String userId = userObject.optString("id");

                            runOnUiThread(() -> onTokenReceived(accessToken, userId, firstName + " " + lastName));
                        }
                    } catch (JSONException e) {
                        runOnUiThread(() -> Toast.makeText(VkTokenAuthActivity.this, "Ошибка парсинга: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(VkTokenAuthActivity.this, "Не удалось получить данные пользователя", Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    private void checkStoredToken() {
        SharedPreferences prefs = getSharedPreferences("VK", MODE_PRIVATE);
        String token = prefs.getString("access_token", null);

        if (token != null) {
            // Проверяем валидность токена
            checkTokenValidity(token);
            // В случае успешной проверки внутри handleApiResponse произойдет переход
            // Если токен недействителен, он будет удален
        } else {
            // Токен не найден, пользователь вводит вручную или авторизация
            // Можно оставить как есть или показать диалог
            // displayMessage("Введите токен или авторизуйтесь");
        }
    }

    private void showTokenInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Введите токен VK");

        final EditText input = new EditText(this);
        input.setHint("Токен");
        builder.setView(input);

        builder.setPositiveButton("ОК", (dialog, which) -> {
            String token = input.getText().toString().trim();
            if (!token.isEmpty()) {
                saveToken(token);
                //displayToken(token); // отображаем токен
                // Можно проверить валидность или сразу отправить
                checkTokenValidity(token);
                // sendTokenToMain(token);
            } else {
                Toast.makeText(this, "Пожалуйста, введите токен", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveToken(String token) {
        getSharedPreferences("VK", MODE_PRIVATE).edit()
                .putString("access_token", token)
                .apply();
    }

    // Метод для отображения токена в TextView
    private void displayToken(String token) {
        textStatus.setText("Полученный токен:\n" + token);
    }

    private void checkTokenValidity(String token) {
        new Thread(() -> {
            try {
                String urlStr = "https://api.vk.com/method/users.get?access_token="
                        + token + "&v=5.131";

                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                int responseCode = connection.getResponseCode();

                InputStream inputStream;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.getInputStream();
                    String response = convertStreamToString(inputStream);
                    runOnUiThread(() -> handleApiResponse(response));
                } else {
                    runOnUiThread(() -> {
                        textStatus.setText("Токен недействителен или истёк");
                        Toast.makeText(this, "Пожалуйста, авторизуйтесь заново", Toast.LENGTH_LONG).show();
                        getSharedPreferences("VK", MODE_PRIVATE).edit().remove("access_token").apply();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> textStatus.setText("Ошибка проверки токена"));
            }
        }).start();
    }

    private String convertStreamToString(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    private void handleApiResponse(String response) {
        if (response.contains("\"error\"")) {
            // Токен недействителен
            getSharedPreferences("VK", MODE_PRIVATE).edit().remove("access_token").apply();
            Toast.makeText(this, "Токен недействителен, пожалуйста, авторизуйтесь заново.", Toast.LENGTH_LONG).show();
            // Можно оставить активити открытым для повторного ввода
        } else {
            // Токен валиден - получаем данные пользователя
            String token = getSharedPreferences("VK", MODE_PRIVATE).getString("access_token", "");
            fetchUserData(token);
        }
    }

    private void fetchUserData(String token) {
        new Thread(() -> {
            try {
                String urlStr = "https://api.vk.com/method/users.get?access_token="
                        + token + "&v=5.131";

                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                int responseCode = connection.getResponseCode();

                InputStream inputStream;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.getInputStream();
                    String response = convertStreamToString(inputStream);
                    runOnUiThread(() -> handleUserApiResponse(response));
                } else {
                    runOnUiThread(() -> {
                        textStatus.setText("Ошибка получения данных пользователя");
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> textStatus.setText("Ошибка сети"));
            }
        }).start();
    }

    private void handleUserApiResponse(String response) {
        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONArray responseArray = jsonObject.getJSONArray("response");
            if (responseArray.length() > 0) {
                JSONObject userObject = responseArray.getJSONObject(0);
                String userId = userObject.getString("id");
                String firstName = userObject.optString("first_name");
                String lastName = userObject.optString("last_name");
                String fullName = firstName + " " + lastName;

                String photoUrl = userObject.optString("photo_100");
                String city = "";
                if (userObject.has("city")) {
                    city = userObject.getJSONObject("city").optString("title");
                }
                String country = "";
                if (userObject.has("country")) {
                    country = userObject.getJSONObject("country").optString("title");
                }
                int onlineStatus = userObject.optInt("online", 0);
                String statusText = userObject.optString("status", "");

                // Передача данных в другое активити
                sendUserDataToMain(userId, fullName, photoUrl, city, country, onlineStatus, statusText);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendUserDataToMain(String userId, String fullName, String photoUrl,
                                    String city, String country, int onlineStatus, String statusText) {
        Intent intent = new Intent(this, BaseActivity.class);
        intent.putExtra("USER_ID", userId);
        intent.putExtra("FULL_NAME", fullName);
        intent.putExtra("PHOTO_URL", photoUrl);
        intent.putExtra("CITY", city);
        intent.putExtra("COUNTRY", country);
        intent.putExtra("ONLINE_STATUS", onlineStatus);
        intent.putExtra("STATUS_TEXT", statusText);
        startActivity(intent);
    }

    private void startVKAuth() {
     /*   String authUrl = "https://oauth.vk.com/authorize?" +
                "client_id=" + CLIENT_ID +
                "&display=page" +
                "&redirect_uri=" + REDIRECT_URI +
                "&scope=" + SCOPE +
                "&response_type=token" +
                "&v=5.131";*/

        String authUrl = "https://oauth.vk.com/authorize?client_id=6287487&scope=69662&redirect_uri=https://oauth.vk.com/blank.html&display=mobile&response_type=token&revoke=1";

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
        startActivity(intent);

        // Для обработки редиректа потребуется WebView или deep link.
    }

    private void goVkAuth() {
        Intent intent = new Intent(this, VKAuthActivity.class);
        startActivity(intent);
    }

    private void goVkTokenAuth() {
        Intent intent = new Intent(this, AuthRefreshToken.class);
        startActivity(intent);
    }

    private void vkFriends() {
        Intent intent = new Intent(this, RegistarationActivity.class);
        startActivity(intent);
    }
    private void initializeViews() {
        loginEditText = findViewById(R.id.loginEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupHttpClient() {
        client = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build();
    }

    private void setupLoginButton() {
        loginButton.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        currentLogin = loginEditText.getText().toString().trim();
        currentPassword = passwordEditText.getText().toString().trim();

        if (currentLogin.isEmpty() || currentPassword.isEmpty()) {
            showToast("Введите логин и пароль");
            return;
        }

        showProgress(true);
        resetCaptchaState();

        Request request = buildAuthRequest(currentLogin, currentPassword);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handleNetworkError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                processAuthResponse(response);
            }
        });
    }

    private void resetCaptchaState() {
        captchaSid = null;
        captchaImg = null;
        captchaKey = null;
        captchaAttempts = 0;
    }

    private Request buildAuthRequest(String login, String password) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse("https://oauth.vk.com/token").newBuilder()
                .addQueryParameter("grant_type", "password")
                .addQueryParameter("client_id", CLIENT_ID)
                .addQueryParameter("client_secret", CLIENT_SECRET)
                .addQueryParameter("username", login)
                .addQueryParameter("password", password)
                .addQueryParameter("v", API_VERSION)
                .addQueryParameter("2fa_supported", "1");

        if (captchaSid != null && captchaKey != null) {
            urlBuilder.addQueryParameter("captcha_sid", captchaSid)
                    .addQueryParameter("captcha_key", captchaKey);
        }

        return new Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", Authorizer.getKateUserAgent())
                // .header("User-Agent", "VkAuthApp/1.0")
                .build();
    }

    private void processAuthResponse(Response response) throws IOException {
        String responseBody = response.body().string();
        Log.d(TAG, "Auth response: " + responseBody);

        try {
            JSONObject json = new JSONObject(responseBody);

            if (!response.isSuccessful()) {
                handleApiError(json);
                return;
            }

            if (json.has("access_token")) {
                handleSuccessfulAuth(json);
            } else {
                handleUnexpectedResponse(responseBody);
            }
        } catch (JSONException e) {
            handleJsonParsingError(responseBody, e);
        }
    }

    private void handleSuccessfulAuth(JSONObject authData) throws JSONException {
        String token = authData.getString("access_token");
        String userId = authData.getString("user_id");

        fetchUserInfo(token, userId);
    }

    private void fetchUserInfo(String token, String userId) {
        Request request = new Request.Builder()
                .url("https://api.vk.com/method/users.get?" +
                        "user_ids=" + userId +
                        "&fields=first_name,last_name" +
                        "&access_token=" + token +
                        "&v=" + API_VERSION)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showProgress(false);
                    showToast("Не удалось получить информацию о пользователе");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    JSONArray users = json.getJSONArray("response");
                    JSONObject user = users.getJSONObject(0);

                    String firstName = user.getString("first_name");
                    String lastName = user.getString("last_name");
                    String fullName = firstName + " " + lastName;

                    onTokenReceived(token, userId, fullName);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing user info", e);
                    onTokenReceived(token, userId, "Пользователь VK");
                }
            }
        });
    }

    private void handleApiError(JSONObject error) throws JSONException {
        String errorType = error.getString("error");
        String errorMsg = error.optString("error_description", "Неизвестная ошибка");
        Log.e(TAG, "API error: " + errorType + " - " + errorMsg);

        runOnUiThread(() -> {
            showProgress(false);

            if ("need_validation".equals(errorType)) {
                try {
                    validationSid = error.getString("validation_sid");
                    show2FADialog(true);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing 2FA data", e);
                    showToast("Ошибка при обработке двухфакторной аутентификации");
                }
            } else if ("need_captcha".equals(errorType)) {
                try {
                    captchaSid = error.getString("captcha_sid");
                    captchaImg = error.getString("captcha_img");
                    showCaptchaDialog();
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing captcha data", e);
                    showToast("Ошибка при обработке капчи");
                }
            } else {
                showToast("Ошибка: " + errorMsg);
                resetCaptchaState();
            }
        });
    }

    private void showCaptchaDialog() {
        runOnUiThread(() -> {
            if (captchaAttempts >= MAX_CAPTCHA_ATTEMPTS) {
                showToast("Превышено количество попыток ввода капчи");
                resetCaptchaState();
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Введите капчу");

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(32, 32, 32, 32);

            ImageView captchaImageView = new ImageView(this);
            new LoadCaptchaImageTask(captchaImageView).execute(captchaImg);

            EditText captchaInput = new EditText(this);
            captchaInput.setHint("Введите текст с картинки");
            captchaInput.setInputType(InputType.TYPE_CLASS_TEXT);

            layout.addView(captchaImageView);
            layout.addView(captchaInput);
            builder.setView(layout);

            builder.setPositiveButton("Подтвердить", (dialog, which) -> {
                String enteredCaptcha = captchaInput.getText().toString().trim();
                if (!enteredCaptcha.isEmpty()) {
                    captchaKey = enteredCaptcha;
                    captchaAttempts++;
                    attemptLoginWithCaptcha();
                } else {
                    showToast("Введите текст с картинки");
                }
            });

            builder.setNegativeButton("Отмена", (dialog, which) -> {
                resetCaptchaState();
                dialog.cancel();
            });

            builder.setCancelable(false);
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    private void attemptLoginWithCaptcha() {
        showProgress(true);
        Request request = buildAuthRequest(currentLogin, currentPassword);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handleNetworkError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                processAuthResponse(response);
            }
        });
    }

    private static class LoadCaptchaImageTask extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;

        LoadCaptchaImageTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            try {
                URL url = new URL(params[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                return BitmapFactory.decodeStream(input);
            } catch (Exception e) {
                Log.e("VkAuthActivity", "Error loading captcha image", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (imageViewReference.get() != null && bitmap != null) {
                imageViewReference.get().setImageBitmap(bitmap);
            }
        }
    }

    private void show2FADialog(boolean isAppCode) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Двухфакторная аутентификация");
            builder.setMessage("Введите код из приложения аутентификатора");

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(32, 32, 32, 32);

            EditText codeInput = new EditText(this);
            codeInput.setHint("6-значный код");
            codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            codeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});

            Button pasteButton = new Button(this);
            pasteButton.setText("Вставить из буфера");
            pasteButton.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                    String pasteData = item.getText().toString();
                    if (pasteData != null && pasteData.matches("\\d{6}")) {
                        codeInput.setText(pasteData);
                    } else {
                        showToast("В буфере нет 6-значного кода");
                    }
                }
            });

            layout.addView(codeInput);
            layout.addView(pasteButton);
            builder.setView(layout);

            builder.setPositiveButton("Подтвердить", (dialog, which) -> {
                String code = codeInput.getText().toString().trim();
                if (code.length() == 6) {
                    validate2FACode(code);
                } else {
                    showToast("Введите 6-значный код");
                }
            });

            builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

            AlertDialog dialog = builder.create();
            dialog.show();

            codeInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(codeInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void validate2FACode(String code) {
        showProgress(true);

        Request request = new Request.Builder()
                .url("https://oauth.vk.com/token?" +
                        "grant_type=password" +
                        "&client_id=" + CLIENT_ID +
                        "&client_secret=" + CLIENT_SECRET +
                        "&username=" + currentLogin +
                        "&password=" + currentPassword +
                        "&v=" + API_VERSION +
                        "&code=" + code +
                        "&validation_sid=" + validationSid)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handleNetworkError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                processAuthResponse(response);
            }
        });
    }

    private void onTokenReceived(String token, String userId, String fullName) {
        SharedPreferences prefs = getSharedPreferences("VK_PREFS", MODE_PRIVATE);
        prefs.edit()
                .putString("access_token", token)
                .putString("user_id", userId)
                .putString("full_name", fullName)
                .putString("refresh_token", "фиктивный_refresh_token")
                .apply();

        runOnUiThread(() -> {
            showProgress(false);
            showToast("Успешная авторизация");
            startMainActivity(fullName, userId, token);  // Передаем все данные
        });
    }

    private void startMainActivity(String userName, String userId, String token) {
        Intent intent = new Intent(this, BaseActivity.class);
        // Упаковываем все данные в Intent
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("USER_ID", userId);
        intent.putExtra("ACCESS_TOKEN", token);
        startActivity(intent);
        finish();

        // Добавляем анимацию перехода
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void handleNetworkError(IOException e) {
        runOnUiThread(() -> {
            showProgress(false);
            showToast("Ошибка сети: " + e.getMessage());
            Log.e(TAG, "Network error", e);
        });
    }

    private void handleJsonParsingError(String responseBody, JSONException e) {
        runOnUiThread(() -> {
            showProgress(false);
            showToast("Ошибка обработки данных");
            Log.e(TAG, "JSON parsing error. Response: " + responseBody, e);
        });
    }

    private void handleUnexpectedResponse(String responseBody) {
        runOnUiThread(() -> {
            showProgress(false);
            showToast("Неизвестный формат ответа");
            Log.e(TAG, "Unexpected response: " + responseBody);
        });
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!show);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}