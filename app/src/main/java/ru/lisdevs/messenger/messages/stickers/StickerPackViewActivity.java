package ru.lisdevs.messenger.messages.stickers;


import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Message;
import ru.lisdevs.messenger.model.Sticker;
import ru.lisdevs.messenger.model.StickerPack;
import ru.lisdevs.messenger.utils.GridSpacingItemDecoration;
import ru.lisdevs.messenger.utils.TokenManager;

public class StickerPackViewActivity extends AppCompatActivity implements StickerPackViewAdapter.OnStickerClickListener {

    private static final String EXTRA_STICKER_PACK = "sticker_pack";
    private static final int REQUEST_CODE_STICKER_SHARE = 1001;

    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyText;
    private StickerPackViewAdapter adapter;

    private StickerPack stickerPack;
    private List<Sticker> stickers = new ArrayList<>();
    private OkHttpClient httpClient = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_view);

        // Получаем переданный стикерпак
        stickerPack = getIntent().getParcelableExtra(EXTRA_STICKER_PACK);
        if (stickerPack == null) {
            finish();
            return;
        }

        initViews();
        setupToolbar();
        loadStickers();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerViewStickers);
        progressBar = findViewById(R.id.progressBar);
        emptyText = findViewById(R.id.emptyText);

        // Настройка RecyclerView
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        recyclerView.setLayoutManager(layoutManager);

        // Добавляем отступы между элементами
        int spacing = getResources().getDimensionPixelSize(R.dimen.sticker_spacing);
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(3, spacing, true));

        adapter = new StickerPackViewAdapter(stickers, this);
        recyclerView.setAdapter(adapter);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(stickerPack.getTitle());
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Показываем количество стикеров в подзаголовке
        TextView subtitle = findViewById(R.id.toolbar_subtitle);
        if (subtitle != null) {
            int stickerCount = stickerPack.getStickers() != null ? stickerPack.getStickers().size() : 0;
            subtitle.setText(getString(R.string.tracks_count, stickerCount));
        }

        // Настройка кнопки "Назад"
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void loadStickers() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        // Загружаем стикеры в фоновом потоке
        new Thread(() -> {
            if (stickerPack != null && stickerPack.getStickers() != null) {
                stickers.clear();
                stickers.addAll(stickerPack.getStickers());
            }

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);

                if (stickers.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                    emptyText.setText("В этом пакете нет стикеров");
                } else {
                    adapter.setStickers(stickers);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    @Override
    public void onStickerClick(Sticker sticker) {
        // Показываем BottomSheet для выбора диалога
        showStickerShareBottomSheet(sticker);
    }

    private void showStickerShareBottomSheet(Sticker sticker) {
        StickerShareBottomSheet bottomSheet = StickerShareBottomSheet.newInstance(sticker);
        bottomSheet.setStickerShareListener((dialog, stickerToShare) -> {
            sendStickerAsImage(dialog, stickerToShare);
        });
        bottomSheet.show(getSupportFragmentManager(), "sticker_share_bottom_sheet");
    }

    private void sendStickerAsImage(Message dialog, Sticker sticker) {
        String accessToken = TokenManager.getInstance(this).getToken();
        if (accessToken == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            return;
        }

        // Показываем прогресс
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Отправка стикера...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Загружаем изображение стикера и отправляем как фото
        loadAndSendStickerAsImage(sticker, dialog, progressDialog);
    }

    private void loadAndSendStickerAsImage(Sticker sticker, Message dialog, ProgressDialog progressDialog) {
        if (sticker == null || sticker.getImageUrl() == null) {
            progressDialog.dismiss();
            Toast.makeText(this, "Ошибка: неверный стикер", Toast.LENGTH_SHORT).show();
            return;
        }

        String imageUrl = sticker.getImageUrl();

        // Исправляем URL если нужно
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            imageUrl = "https://" + imageUrl;
        }

        // Загружаем изображение стикера
        Request request = new Request.Builder()
                .url(imageUrl)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(StickerPackViewActivity.this, "Ошибка загрузки стикера", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Получаем байты изображения
                    byte[] imageBytes = response.body().bytes();

                    // Отправляем как фото через VK API
                    uploadStickerAsPhoto(imageBytes, dialog, sticker, progressDialog);
                } else {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(StickerPackViewActivity.this,
                                "Ошибка загрузки изображения: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void uploadStickerAsPhoto(byte[] imageBytes, Message dialog, Sticker sticker, ProgressDialog progressDialog) {
        String accessToken = TokenManager.getInstance(this).getToken();

        // Сначала получаем URL для загрузки
        String getUploadUrl = "https://api.vk.com/method/photos.getMessagesUploadServer" +
                "?access_token=" + accessToken +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(getUploadUrl)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(StickerPackViewActivity.this, "Ошибка получения сервера загрузки", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONObject uploadServer = json.getJSONObject("response");
                            String uploadUrl = uploadServer.getString("upload_url");

                            // Загружаем изображение на сервер
                            uploadImageToServer(imageBytes, uploadUrl, dialog, sticker, progressDialog);
                        } else {
                            throw new JSONException("No upload server in response");
                        }
                    } catch (JSONException e) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(StickerPackViewActivity.this, "Ошибка обработки ответа сервера", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(StickerPackViewActivity.this,
                                "Ошибка получения сервера загрузки: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void uploadImageToServer(byte[] imageBytes, String uploadUrl, Message dialog, Sticker sticker, ProgressDialog progressDialog) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("photo", "sticker.png",
                        RequestBody.create(imageBytes, MediaType.parse("image/png")))
                .build();

        Request request = new Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(StickerPackViewActivity.this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        // Сохраняем фото в VK
                        saveMessagesPhoto(responseBody, dialog, sticker, progressDialog);
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(StickerPackViewActivity.this, "Ошибка обработки ответа", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(StickerPackViewActivity.this,
                                "Ошибка загрузки изображения: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void saveMessagesPhoto(String uploadResponse, Message dialog, Sticker sticker, ProgressDialog progressDialog) {
        String accessToken = TokenManager.getInstance(this).getToken();

        try {
            JSONObject uploadJson = new JSONObject(uploadResponse);
            String server = uploadJson.getString("server");
            String photo = uploadJson.getString("photo");
            String hash = uploadJson.getString("hash");

            String saveUrl = "https://api.vk.com/method/photos.saveMessagesPhoto" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&server=" + server +
                    "&photo=" + URLEncoder.encode(photo, "UTF-8") +
                    "&hash=" + URLEncoder.encode(hash, "UTF-8");

            Request request = new Request.Builder()
                    .url(saveUrl)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(StickerPackViewActivity.this, "Ошибка сохранения фото", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("response")) {
                                JSONArray photos = json.getJSONArray("response");
                                JSONObject photo = photos.getJSONObject(0);

                                // Отправляем сообщение с фото
                                sendPhotoMessage(photo, dialog, sticker, progressDialog);
                            } else {
                                throw new JSONException("No response in save photo");
                            }
                        } catch (JSONException e) {
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(StickerPackViewActivity.this, "Ошибка сохранения фото", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(StickerPackViewActivity.this,
                                    "Ошибка сохранения фото: " + response.code(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                progressDialog.dismiss();
                Toast.makeText(StickerPackViewActivity.this, "Ошибка обработки фото", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void sendPhotoMessage(JSONObject photo, Message dialog, Sticker sticker, ProgressDialog progressDialog) {
        String accessToken = TokenManager.getInstance(this).getToken();

        try {
            int ownerId = photo.getInt("owner_id");
            int photoId = photo.getInt("id");
            String attachment = "photo" + ownerId + "_" + photoId;

            String url = "https://api.vk.com/method/messages.send" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&peer_id=" + dialog.getPeerId() +
                    "&attachment=" + URLEncoder.encode(attachment, "UTF-8") +
                    "&random_id=" + System.currentTimeMillis();

            Request request = new Request.Builder().url(url).build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(StickerPackViewActivity.this, "Ошибка отправки стикера", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();

                        if (response.isSuccessful()) {
                            Toast.makeText(StickerPackViewActivity.this,
                                    "Стикер отправлен в диалог с " + dialog.getSenderName(),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(StickerPackViewActivity.this,
                                    "Ошибка отправки: " + response.code(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                progressDialog.dismiss();
                Toast.makeText(StickerPackViewActivity.this, "Ошибка отправки фото", Toast.LENGTH_SHORT).show();
            });
        }
    }

    // Остальные методы остаются без изменений
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_sticker_pack, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_share) {
            shareStickerPack();
            return true;
        } else if (id == R.id.action_info) {
            showPackInfo();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void shareStickerPack() {
        if (stickerPack == null) return;

        String shareText = "Посмотрите на стикерпак: " + stickerPack.getTitle();
        if (stickerPack.getStickers() != null) {
            shareText += " (" + stickerPack.getStickers().size() + " стикеров)";
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Поделиться стикерпаком"));
    }

    private void showPackInfo() {
        if (stickerPack == null) return;

        int stickerCount = stickerPack.getStickers() != null ? stickerPack.getStickers().size() : 0;
        String info = "Название: " + stickerPack.getTitle() +
                "\nКоличество стикеров: " + stickerCount +
                "\nID пакета: " + stickerPack.getId();

        new AlertDialog.Builder(this)
                .setTitle("Информация о стикерпаке")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_STICKER_SHARE && resultCode == RESULT_OK) {
            if (data != null && data.hasExtra("selected_sticker")) {
                Sticker sticker = data.getParcelableExtra("selected_sticker");
                if (sticker != null) {
                    showStickerShareBottomSheet(sticker);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Добавляем анимацию перехода
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // Метод для запуска активности
    public static void start(Context context, StickerPack stickerPack) {
        Intent intent = new Intent(context, StickerPackViewActivity.class);
        intent.putExtra(EXTRA_STICKER_PACK, stickerPack);
        context.startActivity(intent);
    }

    // Метод для запуска активности с ожиданием результата
    public static void startForResult(androidx.fragment.app.Fragment fragment, StickerPack stickerPack, int requestCode) {
        Intent intent = new Intent(fragment.getContext(), StickerPackViewActivity.class);
        intent.putExtra(EXTRA_STICKER_PACK, stickerPack);
        fragment.startActivityForResult(intent, requestCode);
    }

    // Метод для запуска активности с анимацией
    public static void startWithAnimation(Activity activity, StickerPack stickerPack) {
        Intent intent = new Intent(activity, StickerPackViewActivity.class);
        intent.putExtra(EXTRA_STICKER_PACK, stickerPack);
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}