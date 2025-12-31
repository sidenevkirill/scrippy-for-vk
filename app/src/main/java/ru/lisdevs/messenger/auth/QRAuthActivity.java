package ru.lisdevs.messenger.auth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.BaseActivity;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.utils.TokenManager;


public class QRAuthActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private Button btnCancel;
    private OkHttpClient client;
    private TokenManager tokenManager;

    private boolean isProcessing = false;
    private BarcodeScanner barcodeScanner;
    private long lastScanTime = 0;
    private static final long SCAN_INTERVAL = 1000; // 1 секунда между сканированиями

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_auth);

        initViews();
        initHttpClient();
        initTokenManager();
        initBarcodeScanner();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private static final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};
    private static final int REQUEST_CODE_PERMISSIONS = 1001;

    private void initViews() {
        previewView = findViewById(R.id.preview_view);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
        btnCancel = findViewById(R.id.btn_cancel);

        btnCancel.setOnClickListener(v -> finish());
        tvStatus.setText("Наведите камеру на QR-код");
    }

    private void initHttpClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private void initTokenManager() {
        tokenManager = TokenManager.getInstance(this);
    }

    private void initBarcodeScanner() {
        // Настраиваем сканер для лучшего распознавания QR-кодов
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAllPotentialBarcodes() // Включаем обнаружение всех потенциальных штрих-кодов
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Разрешение на камеру не предоставлено", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e("QRAuth", "Error starting camera: " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(this, "Ошибка запуска камеры: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        try {
            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            if (!cameraProvider.hasCamera(cameraSelector)) {
                Toast.makeText(this, "Задняя камера не найдена", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            // Упрощаем настройки preview для лучшей производительности
            Preview preview = new Preview.Builder()
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // Упрощаем ImageAnalysis для лучшего сканирования
            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(1920, 1080)) // Увеличиваем разрешение для лучшего качества
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setImageQueueDepth(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888) // Увеличиваем глубину очереди
                    .build();

            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), this::analyzeImage); // Используем отдельный поток

            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            Log.d("QRAuth", "Camera started successfully");

        } catch (Exception e) {
            Log.e("QRAuth", "Use case binding failed: " + e.getMessage(), e);
            runOnUiThread(() ->
                    Toast.makeText(this, "Ошибка инициализации камеры: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void analyzeImage(ImageProxy imageProxy) {
        if (isProcessing) {
            imageProxy.close();
            return;
        }

        // Защита от слишком частого сканирования
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime < SCAN_INTERVAL) {
            imageProxy.close();
            return;
        }

        @SuppressLint("UnsafeOptInUsageError")
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty()) {
                        for (Barcode barcode : barcodes) {
                            String qrContent = barcode.getRawValue();
                            Log.d("QRAuth", "Found barcode: " + qrContent);

                            if (qrContent != null && isVkQrCode(qrContent)) {
                                lastScanTime = currentTime;
                                handleScannedQrCode(qrContent);
                                break;
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("QRAuth", "Barcode scanning failed: " + e.getMessage());
                })
                .addOnCompleteListener(task -> {
                    imageProxy.close();
                });
    }

    private void handleScannedQrCode(String qrContent) {
        if (isProcessing) return;

        Log.d("QRAuth", "QR Code scanned: " + qrContent);
        isProcessing = true;

        runOnUiThread(() -> {
            setLoadingState(true, "Обработка QR-кода...");
            tvStatus.setText("Найден QR-код: " + qrContent.substring(0, Math.min(20, qrContent.length())) + "...");

            new Thread(() -> {
                try {
                    String deviceId = extractDeviceId(qrContent);
                    Log.d("QRAuth", "Extracted device_id: " + deviceId);

                    if (deviceId == null) {
                        runOnUiThread(() -> {
                            showError("Не удалось распознать QR-код VK: " + qrContent);
                            resumeScanning();
                        });
                        return;
                    }

                    startQrAuthProcess(deviceId);

                } catch (Exception e) {
                    Log.e("QRAuth", "Error processing QR: " + e.getMessage());
                    runOnUiThread(() -> {
                        showError("Ошибка обработки: " + e.getMessage());
                        resumeScanning();
                    });
                }
            }).start();
        });
    }

    private boolean isVkQrCode(String qrContent) {
        if (qrContent == null) return false;

        // Расширяем список возможных форматов VK QR-кодов
        return qrContent.startsWith("https://vk.com/") ||
                qrContent.startsWith("vk.com/") ||
                qrContent.contains("device?act=connect") ||
                qrContent.contains("device_id=") ||
                qrContent.contains("oauth.qr") ||
                qrContent.startsWith("https://id.vk.com/") ||
                qrContent.startsWith("id.vk.com/") ||
                qrContent.contains("vk.com/device") ||
                qrContent.contains("vk.com/auth") ||
                qrContent.contains("vk.com/login");
    }

    private String extractDeviceId(String qrContent) {
        try {
            // Нормализуем URL
            if (!qrContent.startsWith("http")) {
                qrContent = "https://" + qrContent;
            }

            Uri uri = Uri.parse(qrContent);

            // Пробуем разные параметры
            String deviceId = uri.getQueryParameter("device_id");
            if (deviceId != null && !deviceId.isEmpty()) {
                return deviceId;
            }

            // Альтернативные способы извлечения
            String[] patterns = {
                    "device_id=([^&]+)",
                    "device=([^&]+)",
                    "id=([^&]+)",
                    "code=([^&]+)"
            };

            for (String patternStr : patterns) {
                Pattern pattern = Pattern.compile(patternStr);
                Matcher matcher = pattern.matcher(qrContent);
                if (matcher.find()) {
                    String foundId = matcher.group(1);
                    if (foundId != null && foundId.length() > 5) { // Минимальная длина device_id
                        return foundId;
                    }
                }
            }

            // Если не нашли в параметрах, пробуем из пути
            List<String> pathSegments = uri.getPathSegments();
            for (String segment : pathSegments) {
                if (segment.length() > 10 && segment.matches("[a-zA-Z0-9]+")) {
                    return segment; // Возможно это device_id в пути
                }
            }

        } catch (Exception e) {
            Log.e("QRAuth", "Error extracting device_id: " + e.getMessage());
        }

        // Если ничего не нашли, возвращаем весь контент для отладки
        Log.d("QRAuth", "Could not extract device_id from: " + qrContent);
        return null;
    }

    private void startQrAuthProcess(String deviceId) {
        runOnUiThread(() -> {
            setLoadingState(true, "Подключение к устройству...");
        });

        checkDeviceAvailability(deviceId);
    }

    private void checkDeviceAvailability(String deviceId) {
        new Thread(() -> {
            try {
                Log.d("QRAuth", "Checking device availability for: " + deviceId);

                JSONObject deviceInfo = getDeviceInfo(deviceId);
                if (deviceInfo == null) {
                    runOnUiThread(() -> {
                        showError("Устройство недоступно или не найдено");
                        resumeScanning();
                    });
                    return;
                }

                // Проверяем ответ
                if (deviceInfo.has("error")) {
                    JSONObject error = deviceInfo.getJSONObject("error");
                    String errorMsg = error.optString("error_msg", "Unknown error");
                    runOnUiThread(() -> {
                        showError("Ошибка устройства: " + errorMsg);
                        resumeScanning();
                    });
                    return;
                }

                // Если устройство доступно, начинаем опрос статуса
                runOnUiThread(() -> {
                    setLoadingState(true, "Ожидание подтверждения на телефоне...");
                });

                pollAuthStatus(deviceId);

            } catch (Exception e) {
                Log.e("QRAuth", "Error checking device: " + e.getMessage());
                runOnUiThread(() -> {
                    showError("Ошибка подключения: " + e.getMessage());
                    resumeScanning();
                });
            }
        }).start();
    }

    private JSONObject getDeviceInfo(String deviceId) {
        try {
            String url = "https://api.vk.com/method/auth.getDeviceInfo" +
                    "?device_id=" + deviceId +
                    "&v=5.199";

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "VKAndroidApp/5.0")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    Log.d("QRAuth", "Device info response: " + responseBody);
                    return new JSONObject(responseBody);
                } else {
                    Log.e("QRAuth", "Device info request failed: " + response.code());
                }
            }
        } catch (Exception e) {
            Log.e("QRAuth", "Error getting device info: " + e.getMessage());
        }
        return null;
    }

    // Остальные методы остаются без изменений...
    private void pollAuthStatus(String deviceId) {
        final int maxAttempts = 120; // 60 секунд (0.5 сек * 120)
        final int interval = 500;

        new Thread(() -> {
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                try {
                    Thread.sleep(interval);

                    JSONObject authStatus = checkAuthStatus(deviceId);
                    if (authStatus != null) {
                        if (authStatus.has("response")) {
                            JSONObject response = authStatus.getJSONObject("response");

                            if (response.has("access_token")) {
                                String accessToken = response.getString("access_token");
                                int userId = response.getInt("user_id");

                                runOnUiThread(() -> {
                                    handleQrAuthSuccess(accessToken, userId);
                                });
                                return;
                            }
                        } else if (authStatus.has("error")) {
                            JSONObject error = authStatus.getJSONObject("error");
                            String errorCode = error.optString("error_code", "");
                            String errorMsg = error.optString("error_msg", "");

                            handleAuthError(errorCode, errorMsg);
                            return;
                        }
                    }

                    if (attempt % 10 == 0) {
                        int finalAttempt = attempt;
                        runOnUiThread(() -> {
                            tvStatus.setText("Ожидание подтверждения... (" + (finalAttempt / 2) + " сек)");
                        });
                    }

                } catch (Exception e) {
                    Log.e("QRAuth", "Error polling auth status: " + e.getMessage());
                }
            }

            runOnUiThread(() -> {
                showError("Время ожидания истекло");
                resumeScanning();
            });

        }).start();
    }

    private JSONObject checkAuthStatus(String deviceId) {
        try {
            String url = "https://api.vk.com/method/auth.poll" +
                    "?device_id=" + deviceId +
                    "&v=5.199";

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "VKAndroidApp/5.0")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    return new JSONObject(responseBody);
                }
            }
        } catch (Exception e) {
            Log.e("QRAuth", "Error checking auth status: " + e.getMessage());
        }
        return null;
    }

    private void handleAuthError(String errorCode, String errorMsg) {
        runOnUiThread(() -> {
            switch (errorCode) {
                case "authorization_declined":
                    showError("Авторизация отклонена");
                    break;
                case "device_expired":
                    showError("Время действия QR-кода истекло");
                    break;
                case "invalid_request":
                    showError("Неверный запрос: " + errorMsg);
                    break;
                default:
                    showError("Ошибка авторизации: " + errorMsg);
            }
            resumeScanning();
        });
    }

    private void handleQrAuthSuccess(String accessToken, int userId) {
        runOnUiThread(() -> {
            setLoadingState(true, "Получение данных профиля...");
        });

        fetchUserProfile(accessToken, userId);
    }

    private void fetchUserProfile(String accessToken, int userId) {
        new Thread(() -> {
            try {
                String url = "https://api.vk.com/method/users.get" +
                        "?user_ids=" + userId +
                        "&fields=photo_200,first_name,last_name" +
                        "&access_token=" + accessToken +
                        "&v=5.199";

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "VKAndroidApp/5.0")
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
                                String photoUrl = user.optString("photo_200", "");

                                tokenManager.saveUserData(accessToken, String.valueOf(userId), fullName, photoUrl);

                                runOnUiThread(() -> {
                                    Toast.makeText(QRAuthActivity.this, "Успешный вход, " + firstName + "!", Toast.LENGTH_SHORT).show();
                                    navigateToMain();
                                });
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("QRAuth", "Error fetching user profile: " + e.getMessage());
            }

            tokenManager.saveUserData(accessToken, String.valueOf(userId), "Пользователь VK", "");

            runOnUiThread(() -> {
                Toast.makeText(QRAuthActivity.this, "Вход выполнен!", Toast.LENGTH_SHORT).show();
                navigateToMain();
            });

        }).start();
    }

    private void setLoadingState(boolean loading, String message) {
        runOnUiThread(() -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            tvStatus.setText(message);
            previewView.setVisibility(loading ? View.GONE : View.VISIBLE);
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            setLoadingState(false, message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            new Handler().postDelayed(this::resumeScanning, 3000);
        });
    }

    private void resumeScanning() {
        isProcessing = false;
        setLoadingState(false, "Наведите камеру на QR-код");
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
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}