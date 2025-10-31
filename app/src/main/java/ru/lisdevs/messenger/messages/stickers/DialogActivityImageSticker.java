package ru.lisdevs.messenger.messages.stickers;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.dialog.DialogAdapter;
import ru.lisdevs.messenger.model.Attachment;
import ru.lisdevs.messenger.model.Message;
import ru.lisdevs.messenger.model.Sticker;
import ru.lisdevs.messenger.model.StickerPack;
import ru.lisdevs.messenger.utils.StickerManager;
import ru.lisdevs.messenger.utils.TokenManager;

public class DialogActivityImageSticker extends AppCompatActivity implements StickerGridFragment.OnStickerClickListener {

    private static final String TAG = "DialogActivity";
    private static final int REQUEST_CODE_STICKER_STORE = 1001;
    private static final int MESSAGES_PER_PAGE = 30;

    // Основные элементы UI
    private RecyclerView recyclerView;
    private DialogAdapter adapter;
    private List<Message> messageList = new ArrayList<>();
    private String userId;
    private String userName;
    private String peerId;
    private boolean isSpecialUser;
    private EditText editTextMessage;
    private ImageButton buttonSend;
    private Toolbar toolbar;
    private TextView avatarTextView;
    private ImageView verifiedIcon;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Состояние загрузки
    private boolean isLoading = false;
    private int currentOffset = 0;

    // Стикеры и стикерпаки
    private LinearLayout stickersPanel;
    private TabLayout stickersTabLayout;
    private ViewPager2 stickersViewPager;
    private ImageButton btnStickers;
    private ImageButton btnCloseStickers;
    private ImageButton btnInfo;
    private StickersAdapter stickersAdapter;
    private StickersPagerAdapter stickersPagerAdapter;
    private StickerManager stickerManager;
    private List<StickerPack> purchasedStickerPacks = new ArrayList<>();
    private List<Sticker> stickerList = new ArrayList<>();
    private boolean isStickersPanelVisible = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_dialog);

        // Получаем переданные данные из Intent
        Intent intent = getIntent();
        if (intent != null) {
            userId = intent.getStringExtra("userId");
            userName = intent.getStringExtra("userName");
            peerId = intent.getStringExtra("peerId");
            isSpecialUser = intent.getBooleanExtra("isSpecialUser", false);

            Log.d(TAG, "Received arguments - userId: " + userId +
                    ", userName: " + userName +
                    ", peerId: " + peerId +
                    ", isSpecialUser: " + isSpecialUser);
        }

        // Если peerId все еще null, используем userId как peerId
        if (peerId == null && userId != null) {
            peerId = userId;
            Log.d(TAG, "Using userId as peerId: " + peerId);
        }

        initViews();
        setupRecyclerView();
        setupClickListeners();
        setupAvatar();
        setupSwipeRefresh();
        initStickersViews();
        loadStickers();

        // Загружаем историю сообщений
        if (peerId == null) {
            Log.e(TAG, "peerId is still null! Cannot load messages.");
            Toast.makeText(this, "Ошибка: не удалось загрузить диалог", Toast.LENGTH_SHORT).show();
        } else {
            loadDialogHistory(0, true);
        }
    }

    private void initViews() {
        // Настройка тулбара
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Скрываем стандартный заголовок ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        if (toolbarTitle != null) {
            toolbarTitle.setText(userName != null ? userName : "Диалог");
        }

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Находим TextView для аватарку и иконку верификации
        avatarTextView = findViewById(R.id.avatar_text);
        verifiedIcon = findViewById(R.id.verified_icon);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        // Находим кнопку меню
        btnInfo = findViewById(R.id.btnInfo);
        if (btnInfo != null) {
            btnInfo.setOnClickListener(v -> showPopupMenu(v));
        }

        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.btnSend);
        recyclerView = findViewById(R.id.recyclerViewDialog);
    }

    private void initStickersViews() {
        // Инициализация менеджера стикеров
        stickerManager = StickerManager.getInstance(this);

        // Находим новые view для расширенной панели стикеров
        stickersPanel = findViewById(R.id.stickersPanel);
        stickersTabLayout = findViewById(R.id.stickersTabLayout);
        stickersViewPager = findViewById(R.id.stickersViewPager);
        btnStickers = findViewById(R.id.btnAttach);
        btnCloseStickers = findViewById(R.id.btnCloseStickers);

        // Настройка ViewPager2 с табами
        setupStickersViewPager();

        // Настройка RecyclerView для стикеров (резервный вариант)
        GridLayoutManager layoutManager = new GridLayoutManager(this, 4);
        RecyclerView stickersRecyclerView = findViewById(R.id.stickersRecyclerView);
        if (stickersRecyclerView != null) {
            stickersRecyclerView.setLayoutManager(layoutManager);
            stickersAdapter = new StickersAdapter(stickerList, this::onStickerClick);
            stickersRecyclerView.setAdapter(stickersAdapter);
        }

        // Обработчики кликов
        btnStickers.setOnClickListener(v -> toggleStickersPanel());
        btnCloseStickers.setOnClickListener(v -> hideStickersPanel());

        // Кнопка для открытия магазина стикеров
        ImageButton btnStickerStore = findViewById(R.id.btnStickerStore);
        if (btnStickerStore != null) {
            btnStickerStore.setOnClickListener(v -> showStickerStore());
        }
    }

    private void setupStickersViewPager() {
        purchasedStickerPacks = stickerManager.getPurchasedStickerPacks();

        // Добавляем базовые стикеры, если нет купленных
        if (purchasedStickerPacks.isEmpty()) {
            StickerPack basicPack = stickerManager.getStickerPack(1);
            if (basicPack != null) {
                purchasedStickerPacks.add(basicPack);
            }
        }

        stickersPagerAdapter = new StickersPagerAdapter(this, purchasedStickerPacks);
        stickersViewPager.setAdapter(stickersPagerAdapter);

        // Связываем TabLayout с ViewPager2
        new TabLayoutMediator(stickersTabLayout, stickersViewPager,
                (tab, position) -> {
                    if (position == 0) {
                        tab.setText("Все");
                    } else {
                        StickerPack pack = purchasedStickerPacks.get(position - 1);
                        tab.setText(pack.getTitle() != null ? pack.getTitle() : "Стикеры");
                    }
                }).attach();
    }

    private void toggleStickersPanel() {
        if (isStickersPanelVisible) {
            hideStickersPanel();
        } else {
            showStickersPanel();
        }
    }

    private void showStickersPanel() {
        stickersPanel.setVisibility(View.VISIBLE);
        isStickersPanelVisible = true;

        // Скрываем клавиатуру
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && editTextMessage != null) {
            imm.hideSoftInputFromWindow(editTextMessage.getWindowToken(), 0);
        }
    }

    private void hideStickersPanel() {
        stickersPanel.setVisibility(View.GONE);
        isStickersPanelVisible = false;
    }

    private void loadStickers() {
        // Загружаем стикеры через StickerManager
        List<Sticker> allStickers = stickerManager.getAllPurchasedStickers();

        // Если нет купленных стикеров или они не загружаются, используем fallback
        if (allStickers.isEmpty() || stickerManager.areAllStickersBroken(allStickers)) {
            Log.w(TAG, "Using fallback stickers");
            allStickers = stickerManager.getFallbackStickers();
        }

        // Обновляем адаптеры
        if (stickersAdapter != null) {
            stickersAdapter.setStickers(allStickers);
        }

        // Обновляем ViewPager
        purchasedStickerPacks = stickerManager.getPurchasedStickerPacks();

        if (purchasedStickerPacks.isEmpty()) {
            StickerPack basicPack = stickerManager.getStickerPack(1);
            if (basicPack != null) {
                purchasedStickerPacks.add(basicPack);
            }
        }

        if (stickersPagerAdapter != null) {
            stickersPagerAdapter.setStickerPacks(purchasedStickerPacks);
            stickersPagerAdapter.notifyDataSetChanged();
        }

        Log.d(TAG, "Stickers loaded: " + allStickers.size() + " stickers, " + purchasedStickerPacks.size() + " packs");
    }

    @Override
    public void onStickerClick(Sticker sticker) {
        // ВРЕМЕННО: всегда отправляем как изображение для тестирования
        sendStickerAsImage(sticker);

        // ПОЗЖЕ, когда добавите проверку покупки:
        // if (isStickerPurchased(sticker)) {
        //     sendSticker(sticker);
        // } else {
        //     sendStickerAsImage(sticker);
        // }
    }

    // Временная заглушка - всегда false
    private boolean isStickerPurchased(Sticker sticker) {
        return false;
    }

    // ОРИГИНАЛЬНЫЙ метод отправки стикера (для купленных)
    private void sendSticker(Sticker sticker) {
        String accessToken = TokenManager.getInstance(this).getToken();
        if (accessToken != null && peerId != null) {
            // Создаем временное сообщение со стикером
            Message stickerMessage = new Message(userId, userName, "", System.currentTimeMillis(), null);
            stickerMessage.setOutgoing(true);
            stickerMessage.setReadStatus(Message.READ_STATUS_SENT);
            stickerMessage.setPeerId(peerId);

            // Создаем вложение стикера
            Attachment attachment = new Attachment();
            attachment.setType("sticker");

            Attachment.Photo stickerPhoto = new Attachment.Photo();
            List<Attachment.Size> sizes = new ArrayList<>();

            Attachment.Size size = new Attachment.Size();
            size.setUrl(sticker.getImageUrl());
            size.setWidth(sticker.getWidth());
            size.setHeight(sticker.getHeight());
            size.setType("x");

            sizes.add(size);
            stickerPhoto.setSizes(sizes);
            attachment.setPhoto(stickerPhoto);

            stickerMessage.addAttachment(attachment);
            stickerMessage.setPreviewText("😊 Стикер");

            // Добавляем в список
            adapter.addMessage(stickerMessage);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);

            // ПРАВИЛЬНЫЙ формат для отправки стикера
            try {
                String url = "https://api.vk.com/method/messages.send" +
                        "?access_token=" + accessToken +
                        "&v=5.131" +
                        "&peer_id=" + peerId +
                        "&sticker_id=" + sticker.getId() +
                        "&random_id=" + System.currentTimeMillis();

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e(TAG, "Failed to send sticker", e);
                        runOnUiThread(() -> {
                            Toast.makeText(DialogActivityImageSticker.this, "Ошибка отправки стикера", Toast.LENGTH_SHORT).show();
                            messageList.remove(stickerMessage);
                            adapter.notifyDataSetChanged();
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.isSuccessful()) {
                            String responseBody = response.body().string();
                            Log.d(TAG, "Sticker sent successfully: " + responseBody);

                            try {
                                JSONObject json = new JSONObject(responseBody);
                                if (json.has("response")) {
                                    int messageId = json.getInt("response");
                                    stickerMessage.setMessageId(String.valueOf(messageId));
                                }
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing send sticker response", e);
                            }

                            runOnUiThread(() -> {
                                // Обновляем статус отправленного сообщения
                                int messageIndex = messageList.indexOf(stickerMessage);
                                if (messageIndex != -1) {
                                    Message sentMessage = messageList.get(messageIndex);
                                    sentMessage.setReadStatus(Message.READ_STATUS_READ);
                                    adapter.notifyItemChanged(messageIndex);
                                }

                                // Скрываем панель стикеров после отправки
                                hideStickersPanel();

                                // Обновляем историю сообщений для получения реального стикера от API
                                loadDialogHistory(0, true);
                            });
                        } else {
                            Log.e(TAG, "Failed to send sticker, code: " + response.code());
                            runOnUiThread(() -> {
                                Toast.makeText(DialogActivityImageSticker.this, "Ошибка отправки: " + response.code(), Toast.LENGTH_SHORT).show();
                                messageList.remove(stickerMessage);
                                adapter.notifyDataSetChanged();
                            });
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error sending sticker", e);
                Toast.makeText(this, "Ошибка отправки стикера", Toast.LENGTH_SHORT).show();
                messageList.remove(stickerMessage);
                adapter.notifyDataSetChanged();
            }
        } else {
            Toast.makeText(this, "Не удалось отправить стикер", Toast.LENGTH_SHORT).show();
        }
    }

    // НОВЫЙ метод для отправки стикера как изображения (для НЕ купленных)
    private void sendStickerAsImage(Sticker sticker) {
        if (sticker == null || sticker.getImageUrl() == null) {
            Toast.makeText(this, "Ошибка: неверный стикер", Toast.LENGTH_SHORT).show();
            return;
        }

        String accessToken = TokenManager.getInstance(this).getToken();
        if (accessToken != null && peerId != null) {
            // Создаем временное сообщение со стикером как изображением
            Message stickerMessage = new Message(userId, userName, "", System.currentTimeMillis(), null);
            stickerMessage.setOutgoing(true);
            stickerMessage.setReadStatus(Message.READ_STATUS_SENT);
            stickerMessage.setPeerId(peerId);
            stickerMessage.setPreviewText("😊 Стикер");

            // Добавляем в список для мгновенного отображения
            adapter.addMessage(stickerMessage);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);

            // Загружаем изображение стикера и отправляем как фото
            loadAndSendStickerAsImage(sticker, stickerMessage);
        } else {
            Toast.makeText(this, "Не удалось отправить стикер", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadAndSendStickerAsImage(Sticker sticker, Message tempMessage) {
        if (sticker == null || sticker.getImageUrl() == null) {
            runOnUiThread(() -> {
                Toast.makeText(DialogActivityImageSticker.this, "Ошибка: неверный стикер", Toast.LENGTH_SHORT).show();
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
            return;
        }

        // Загружаем изображение стикера
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(sticker.getImageUrl())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to load sticker image", e);
                runOnUiThread(() -> {
                    Toast.makeText(DialogActivityImageSticker.this, "Ошибка загрузки стикера", Toast.LENGTH_SHORT).show();
                    messageList.remove(tempMessage);
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Получаем байты изображения
                    byte[] imageBytes = response.body().bytes();

                    // Отправляем как фото через VK API
                    uploadStickerAsPhoto(imageBytes, tempMessage, sticker);
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivityImageSticker.this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
                        messageList.remove(tempMessage);
                        adapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }

    private void uploadStickerAsPhoto(byte[] imageBytes, Message tempMessage, Sticker originalSticker) {
        String accessToken = TokenManager.getInstance(this).getToken();

        // Сначала получаем URL для загрузки
        String getUploadUrl = "https://api.vk.com/method/photos.getMessagesUploadServer" +
                "?access_token=" + accessToken +
                "&v=5.131";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(getUploadUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to get upload server", e);
                runOnUiThread(() -> {
                    Toast.makeText(DialogActivityImageSticker.this, "Ошибка получения сервера загрузки", Toast.LENGTH_SHORT).show();
                    messageList.remove(tempMessage);
                    adapter.notifyDataSetChanged();
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
                            uploadImageToServer(imageBytes, uploadUrl, tempMessage, originalSticker);
                        } else {
                            throw new JSONException("No upload server in response");
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing upload server response", e);
                        runOnUiThread(() -> {
                            Toast.makeText(DialogActivityImageSticker.this, "Ошибка обработки ответа сервера", Toast.LENGTH_SHORT).show();
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivityImageSticker.this, "Ошибка получения сервера загрузки", Toast.LENGTH_SHORT).show();
                        messageList.remove(tempMessage);
                        adapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }

    private void uploadImageToServer(byte[] imageBytes, String uploadUrl, Message tempMessage, Sticker originalSticker) {
        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("photo", "sticker.png",
                        RequestBody.create(imageBytes, MediaType.parse("image/png")))
                .build();

        Request request = new Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to upload image", e);
                runOnUiThread(() -> {
                    Toast.makeText(DialogActivityImageSticker.this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
                    messageList.remove(tempMessage);
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        // Сохраняем фото в VK
                        saveMessagesPhoto(responseBody, tempMessage, originalSticker);
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing upload response", e);
                        runOnUiThread(() -> {
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivityImageSticker.this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
                        messageList.remove(tempMessage);
                        adapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }

    private void saveMessagesPhoto(String uploadResponse, Message tempMessage, Sticker originalSticker) {
        String accessToken = TokenManager.getInstance(this).getToken();

        try {
            String server = getJsonValue(uploadResponse, "server");
            String photo = getJsonValue(uploadResponse, "photo");
            String hash = getJsonValue(uploadResponse, "hash");

            String saveUrl = "https://api.vk.com/method/photos.saveMessagesPhoto" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&server=" + server +
                    "&photo=" + URLEncoder.encode(photo, "UTF-8") +
                    "&hash=" + URLEncoder.encode(hash, "UTF-8");

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(saveUrl)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to save photo", e);
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivityImageSticker.this, "Ошибка сохранения фото", Toast.LENGTH_SHORT).show();
                        messageList.remove(tempMessage);
                        adapter.notifyDataSetChanged();
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
                                sendPhotoMessage(photo, tempMessage, originalSticker);
                            } else {
                                throw new JSONException("No response in save photo");
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing save photo response", e);
                            runOnUiThread(() -> {
                                messageList.remove(tempMessage);
                                adapter.notifyDataSetChanged();
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(DialogActivityImageSticker.this, "Ошибка сохранения фото", Toast.LENGTH_SHORT).show();
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                        });
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in saveMessagesPhoto", e);
            runOnUiThread(() -> {
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
        }
    }

    private void sendPhotoMessage(JSONObject photo, Message tempMessage, Sticker originalSticker) {
        String accessToken = TokenManager.getInstance(this).getToken();

        try {
            int ownerId = photo.getInt("owner_id");
            int photoId = photo.getInt("id");
            String attachment = "photo" + ownerId + "_" + photoId;

            String url = "https://api.vk.com/method/messages.send" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&peer_id=" + peerId +
                    "&attachment=" + URLEncoder.encode(attachment, "UTF-8") +
                    "&random_id=" + System.currentTimeMillis();

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to send photo message", e);
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivityImageSticker.this, "Ошибка отправки стикера", Toast.LENGTH_SHORT).show();
                        messageList.remove(tempMessage);
                        adapter.notifyDataSetChanged();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            // Обновляем статус сообщения
                            int messageIndex = messageList.indexOf(tempMessage);
                            if (messageIndex != -1) {
                                Message sentMessage = messageList.get(messageIndex);
                                sentMessage.setReadStatus(Message.READ_STATUS_READ);

                                // УПРОЩЕННАЯ версия - без сложной обработки фото
                                try {
                                    List<Attachment> attachments = new ArrayList<>();
                                    Attachment attachmentObj = new Attachment();
                                    attachmentObj.setType("photo");

                                    // Просто создаем базовое вложение
                                    Attachment.Photo stickerPhoto = new Attachment.Photo();
                                    List<Attachment.Size> sizes = new ArrayList<>();
                                    Attachment.Size size = new Attachment.Size();

                                    // Используем оригинальный URL стикера как fallback
                                    size.setUrl(originalSticker.getImageUrl());
                                    size.setWidth(originalSticker.getWidth());
                                    size.setHeight(originalSticker.getHeight());
                                    size.setType("x");
                                    sizes.add(size);

                                    stickerPhoto.setSizes(sizes);
                                    attachmentObj.setPhoto(stickerPhoto);
                                    attachments.add(attachmentObj);

                                    sentMessage.setAttachments(attachments);
                                    adapter.notifyItemChanged(messageIndex);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error updating message with photo", e);
                                }
                            }

                            // Скрываем панель стикеров
                            hideStickersPanel();
                            Toast.makeText(DialogActivityImageSticker.this, "Стикер отправлен как изображение", Toast.LENGTH_SHORT).show();
                        } else {
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(DialogActivityImageSticker.this, "Ошибка отправки", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error sending photo message", e);
            runOnUiThread(() -> {
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
        }
    }

    // Вспомогательный метод для извлечения значений из JSON
    private String getJsonValue(String jsonString, String key) {
        try {
            JSONObject json = new JSONObject(jsonString);
            return json.getString(key);
        } catch (JSONException e) {
            Log.e(TAG, "Error getting JSON value for key: " + key, e);
            return "";
        }
    }

    private void showStickerStore() {
        Intent intent = new Intent(this, StickerStoreActivity.class);
        startActivityForResult(intent, REQUEST_CODE_STICKER_STORE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_STICKER_STORE && resultCode == RESULT_OK) {
            // Обновляем список стикеров после покупки
            purchasedStickerPacks = stickerManager.getPurchasedStickerPacks();
            if (stickersPagerAdapter != null) {
                stickersPagerAdapter.setStickerPacks(purchasedStickerPacks);
                stickersPagerAdapter.notifyDataSetChanged();
            }
            loadStickers();

            Toast.makeText(this, "Стикерпак успешно приобретен!", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                // Загружаем более старые сообщения
                loadOlderMessages();
            });

            // Настраиваем цвета индикатора обновления
            swipeRefreshLayout.setColorSchemeColors(
                    getResources().getColor(R.color.color_primary),
                    getResources().getColor(R.color.black),
                    getResources().getColor(R.color.gray)
            );
        }
    }

    private void setupAvatar() {
        if (avatarTextView != null && userName != null && !userName.isEmpty()) {
            // Получаем первую букву имени
            String firstLetter = getFirstLetter(userName);
            avatarTextView.setText(firstLetter);

            // Устанавливаем случайный цвет фона
            int color = getRandomColor();
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            avatarTextView.setBackground(drawable);
        }

        // Настраиваем иконку верификации
        if (verifiedIcon != null) {
            if (isSpecialUser) {
                verifiedIcon.setVisibility(View.VISIBLE);
                verifiedIcon.setImageResource(R.drawable.check_circle);

                // Добавляем обработчик клика на иконку верификации
                verifiedIcon.setOnClickListener(v -> {
                    showVerificationInfo();
                });

                // Делаем иконку кликабельной
                verifiedIcon.setClickable(true);
                verifiedIcon.setFocusable(true);

                // Добавляем ripple эффект
                try {
                    verifiedIcon.setBackgroundResource(R.drawable.ripple_effect);
                } catch (Exception e) {
                    verifiedIcon.setBackgroundResource(android.R.drawable.btn_default);
                }
            } else {
                verifiedIcon.setVisibility(View.GONE);
            }
        }
    }

    private String getFirstLetter(String name) {
        if (!TextUtils.isEmpty(name)) {
            return name.substring(0, 1).toUpperCase();
        }
        return "?";
    }

    private int getRandomColor() {
        Random random = new Random();
        int[] colors = {
                Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
                Color.parseColor("#45B7D1"), Color.parseColor("#F9A826"),
                Color.parseColor("#6A5ACD"), Color.parseColor("#FFA07A"),
                Color.parseColor("#20B2AA"), Color.parseColor("#9370DB"),
                Color.parseColor("#3CB371"), Color.parseColor("#FF4500")
        };
        return colors[random.nextInt(colors.length)];
    }

    private void showVerificationInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Верифицированный пользователь")
                .setMessage("Этот пользователь прошел проверку подлинности и является официальным представителем.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Начинаем с конца списка
        recyclerView.setLayoutManager(layoutManager);

        adapter = new DialogAdapter(messageList, userId, isSpecialUser);
        recyclerView.setAdapter(adapter);

        // Добавляем обработчик прокрутки для подгрузки старых сообщений
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && !isLoading) {
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    if (firstVisibleItemPosition == 0) {
                        // Пользователь прокрутил к началу списка - загружаем старые сообщения
                        loadOlderMessages();
                    }
                }
            }
        });
    }

    private void setupClickListeners() {
        if (buttonSend != null) {
            buttonSend.setOnClickListener(v -> {
                String messageText = editTextMessage.getText().toString().trim();
                if (!messageText.isEmpty()) {
                    sendMessage(messageText);
                    editTextMessage.setText("");
                }
            });
        }

        // Обработка нажатия Enter в EditText
        if (editTextMessage != null) {
            editTextMessage.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    String messageText = editTextMessage.getText().toString().trim();
                    if (!messageText.isEmpty()) {
                        sendMessage(messageText);
                        editTextMessage.setText("");
                    }
                    return true;
                }
                return false;
            });
        }
    }

    private void loadDialogHistory(int offset, boolean clearExisting) {
        if (isLoading) return;

        isLoading = true;
        if (progressBar != null && clearExisting) {
            progressBar.setVisibility(View.VISIBLE);
        }

        String accessToken = TokenManager.getInstance(this).getToken();
        Log.d(TAG, "Loading dialog history - Token: " + (accessToken != null) + ", PeerId: " + peerId + ", Offset: " + offset);

        if (accessToken != null && peerId != null) {
            String url = "https://api.vk.com/method/messages.getHistory" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&peer_id=" + peerId +
                    "&count=" + MESSAGES_PER_PAGE +
                    "&offset=" + offset +
                    "&extended=1";

            Log.d(TAG, "Request URL: " + url);

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to load dialog history", e);
                    runOnUiThread(() -> {
                        isLoading = false;
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(DialogActivityImageSticker.this, "Ошибка загрузки сообщений", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    isLoading = false;
                    runOnUiThread(() -> {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    });

                    Log.d(TAG, "Response received - Code: " + response.code());

                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        Log.d(TAG, "Response body: " + responseBody);

                        try {
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("error")) {
                                JSONObject error = json.getJSONObject("error");
                                Log.e(TAG, "VK API Error: " + error.toString());
                                runOnUiThread(() -> {
                                    Toast.makeText(DialogActivityImageSticker.this, "Ошибка VK API: " + error.optString("error_msg"), Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }

                            if (json.has("response")) {
                                JSONObject responseObj = json.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");
                                JSONArray profiles = responseObj.optJSONArray("profiles");

                                Log.d(TAG, "Loaded " + items.length() + " messages");

                                Map<String, String> userNames = parseUserNames(profiles);
                                List<Message> messages = new ArrayList<>();

                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject messageObj = items.getJSONObject(i);
                                    Message message = parseMessage(messageObj, userNames);
                                    messages.add(message);
                                }

                                runOnUiThread(() -> {
                                    Log.d(TAG, "Adding " + messages.size() + " messages to adapter");

                                    if (clearExisting) {
                                        messageList.clear();
                                        // Добавляем в обратном порядке, так как API возвращает от новых к старым
                                        for (int i = messages.size() - 1; i >= 0; i--) {
                                            messageList.add(messages.get(i));
                                        }
                                        adapter.notifyDataSetChanged();
                                        recyclerView.scrollToPosition(adapter.getItemCount() - 1);
                                    } else {
                                        // Добавляем старые сообщения в начало
                                        for (int i = messages.size() - 1; i >= 0; i--) {
                                            messageList.add(0, messages.get(i));
                                        }
                                        adapter.notifyItemRangeInserted(0, messages.size());
                                    }

                                    if (messageList.isEmpty()) {
                                        Toast.makeText(DialogActivityImageSticker.this, "Диалог пуст", Toast.LENGTH_SHORT).show();
                                    }

                                    currentOffset = offset + items.length();
                                });
                            } else {
                                Log.e(TAG, "No 'response' field in JSON");
                                runOnUiThread(() -> {
                                    Toast.makeText(DialogActivityImageSticker.this, "Ошибка формата ответа", Toast.LENGTH_SHORT).show();
                                });
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing dialog history", e);
                            runOnUiThread(() -> {
                                Toast.makeText(DialogActivityImageSticker.this, "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } else {
                        Log.e(TAG, "Unsuccessful response: " + response.code());
                        runOnUiThread(() -> {
                            Toast.makeText(DialogActivityImageSticker.this, "Ошибка сети: " + response.code(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        } else {
            Log.e(TAG, "Token or peerId is null - Token: " + accessToken + ", PeerId: " + peerId);
            Toast.makeText(this, "Ошибка: токен или ID диалога не доступны", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadOlderMessages() {
        if (!isLoading) {
            loadDialogHistory(currentOffset, false);
        }
    }

    private Message parseMessage(JSONObject messageObj, Map<String, String> userNames) throws JSONException {
        String text = messageObj.optString("text");
        String senderId = String.valueOf(messageObj.optInt("from_id"));
        long date = messageObj.optLong("date") * 1000;
        boolean isOut = messageObj.optInt("out") == 1;
        int readState = messageObj.optInt("read_state");

        // Определяем статус прочтения
        int readStatus;
        if (isOut) {
            if (readState == 1) {
                readStatus = Message.READ_STATUS_READ;
            } else {
                readStatus = Message.READ_STATUS_SENT;
            }
        } else {
            readStatus = Message.READ_STATUS_INCOMING;
        }

        String senderName = userNames.get(senderId);
        if (senderName == null) {
            senderName = "Пользователь " + senderId;
        }

        // Создаем сообщение
        Message message = new Message(senderId, senderName, text, date, null);
        message.setReadStatus(readStatus);
        message.setOutgoing(isOut);
        message.setPeerId(peerId);

        // Обработка вложений
        if (messageObj.has("attachments")) {
            JSONArray attachments = messageObj.getJSONArray("attachments");
            List<Attachment> attachmentList = parseAttachments(attachments);
            message.setAttachments(attachmentList);

            // Устанавливаем preview текст для сообщений с вложениями
            if (text.isEmpty() && !attachmentList.isEmpty()) {
                message.setPreviewText(generateAttachmentPreview(attachmentList));
            }
        }

        // Обработка пересланных сообщений
        if (messageObj.has("fwd_messages")) {
            JSONArray fwdMessages = messageObj.getJSONArray("fwd_messages");
            message.setPreviewText("📩 Пересланные сообщения (" + fwdMessages.length() + ")");
        }

        return message;
    }

    private List<Attachment> parseAttachments(JSONArray attachmentsArray) throws JSONException {
        List<Attachment> attachments = new ArrayList<>();

        for (int i = 0; i < attachmentsArray.length(); i++) {
            JSONObject attachmentObj = attachmentsArray.getJSONObject(i);
            Attachment attachment = new Attachment();

            String type = attachmentObj.getString("type");
            attachment.setType(type);

            JSONObject attachmentData = attachmentObj.getJSONObject(type);

            switch (type) {
                case "photo":
                    Attachment.Photo photo = parsePhoto(attachmentData);
                    attachment.setPhoto(photo);
                    break;

                case "doc":
                    Attachment.Document doc = parseDocument(attachmentData);
                    attachment.setDoc(doc);
                    break;

                case "audio":
                    Attachment.Audio audio = parseAudio(attachmentData);
                    attachment.setAudio(audio);
                    break;

                case "audio_message":
                    // Обработка голосовых сообщений
                    Attachment.Document audioMessage = parseAudioMessage(attachmentData);
                    attachment.setDoc(audioMessage);
                    attachment.setType("audio_message");
                    break;

                case "sticker":
                    // Обработка стикеров
                    Attachment.Photo sticker = parseSticker(attachmentData);
                    attachment.setPhoto(sticker);
                    break;

                default:
                    // Обработка других типов вложений
                    Attachment.Document otherDoc = parseOtherAttachment(attachmentData, type);
                    attachment.setDoc(otherDoc);
                    break;
            }

            attachments.add(attachment);
        }

        return attachments;
    }

    private Attachment.Photo parsePhoto(JSONObject photoObj) throws JSONException {
        Attachment.Photo photo = new Attachment.Photo();
        photo.setText(photoObj.optString("text", ""));

        JSONArray sizes = photoObj.getJSONArray("sizes");
        List<Attachment.Size> sizeList = new ArrayList<>();

        for (int i = 0; i < sizes.length(); i++) {
            JSONObject sizeObj = sizes.getJSONObject(i);
            Attachment.Size size = new Attachment.Size();
            size.setType(sizeObj.getString("type"));
            size.setUrl(sizeObj.getString("url"));
            size.setWidth(sizeObj.getInt("width"));
            size.setHeight(sizeObj.getInt("height"));
            sizeList.add(size);
        }

        photo.setSizes(sizeList);
        return photo;
    }

    private Attachment.Document parseDocument(JSONObject docObj) throws JSONException {
        Attachment.Document doc = new Attachment.Document();
        doc.setId(String.valueOf(docObj.getInt("id")));
        doc.setTitle(docObj.getString("title"));
        doc.setExt(docObj.getString("ext"));
        doc.setUrl(docObj.getString("url"));
        doc.setSize(docObj.getInt("size"));
        doc.setType(docObj.optString("type", "document"));
        return doc;
    }

    private Attachment.Audio parseAudio(JSONObject audioObj) throws JSONException {
        Attachment.Audio audio = new Attachment.Audio();
        audio.setArtist(audioObj.getString("artist"));
        audio.setTitle(audioObj.getString("title"));
        audio.setDuration(audioObj.getInt("duration"));
        audio.setUrl(audioObj.getString("url"));
        return audio;
    }

    private Attachment.Document parseAudioMessage(JSONObject audioMessageObj) throws JSONException {
        Attachment.Document audioMessage = new Attachment.Document();
        audioMessage.setId(String.valueOf(audioMessageObj.getInt("id")));
        audioMessage.setTitle("Голосовое сообщение");
        audioMessage.setExt("ogg");
        audioMessage.setUrl(audioMessageObj.getString("link_ogg"));
        audioMessage.setSize(audioMessageObj.getInt("duration")); // используем duration как размер
        audioMessage.setType("audio_message");
        return audioMessage;
    }

    private Attachment.Photo parseSticker(JSONObject stickerObj) throws JSONException {
        Attachment.Photo sticker = new Attachment.Photo();

        JSONArray images = stickerObj.getJSONArray("images");
        List<Attachment.Size> sizeList = new ArrayList<>();

        for (int i = 0; i < images.length(); i++) {
            JSONObject imageObj = images.getJSONObject(i);
            Attachment.Size size = new Attachment.Size();
            size.setUrl(imageObj.getString("url"));
            size.setWidth(imageObj.getInt("width"));
            size.setHeight(imageObj.getInt("height"));

            // Определяем тип по размеру
            if (imageObj.getInt("width") >= 512) {
                size.setType("x");
            } else if (imageObj.getInt("width") >= 256) {
                size.setType("m");
            } else {
                size.setType("s");
            }

            sizeList.add(size);
        }

        sticker.setSizes(sizeList);
        return sticker;
    }

    private Attachment.Document parseOtherAttachment(JSONObject attachmentData, String type) throws JSONException {
        Attachment.Document doc = new Attachment.Document();
        doc.setId(String.valueOf(attachmentData.optInt("id", 0)));
        doc.setTitle(attachmentData.optString("title", "Вложение"));
        doc.setExt(attachmentData.optString("ext", "file"));
        doc.setUrl(attachmentData.optString("url", ""));
        doc.setSize(attachmentData.optInt("size", 0));
        doc.setType(type);
        return doc;
    }

    private String generateAttachmentPreview(List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "(вложение)";
        }

        Map<String, Integer> typeCounts = new HashMap<>();
        for (Attachment attachment : attachments) {
            String type = attachment.getType();
            Integer count = typeCounts.get(type);
            if (count == null) {
                typeCounts.put(type, 1);
            } else {
                typeCounts.put(type, count + 1);
            }
        }

        List<String> parts = new ArrayList<>();

        // Фото
        Integer photoCount = typeCounts.get("photo");
        if (photoCount != null && photoCount > 0) {
            parts.add("📷 " + photoCount);
        }

        // Документы
        Integer docCount = typeCounts.get("doc");
        if (docCount != null && docCount > 0) {
            parts.add("📎 " + docCount);
        }

        // Аудио
        Integer audioCount = typeCounts.get("audio");
        if (audioCount != null && audioCount > 0) {
            parts.add("🎵 " + audioCount);
        }

        // Голосовые сообщения
        Integer audioMessageCount = typeCounts.get("audio_message");
        if (audioMessageCount != null && audioMessageCount > 0) {
            parts.add("🎤 " + audioMessageCount);
        }

        // Стикеры
        Integer stickerCount = typeCounts.get("sticker");
        if (stickerCount != null && stickerCount > 0) {
            parts.add("😊 " + stickerCount);
        }

        // Другие типы
        int otherCount = 0;
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            String type = entry.getKey();
            if (!type.equals("photo") && !type.equals("doc") && !type.equals("audio") &&
                    !type.equals("audio_message") && !type.equals("sticker")) {
                otherCount += entry.getValue();
            }
        }
        if (otherCount > 0) {
            parts.add("📁 " + otherCount);
        }

        if (parts.isEmpty()) {
            return "(вложение)";
        }

        return String.join(" • ", parts);
    }

    private void sendMessage(String text) {
        // Скрываем панель стикеров при отправке текста
        hideStickersPanel();

        String accessToken = TokenManager.getInstance(this).getToken();
        if (accessToken != null && peerId != null) {
            // Создаем временное сообщение для отображения
            Message tempMessage = new Message(userId, userName, text, System.currentTimeMillis(), null);
            tempMessage.setOutgoing(true);
            tempMessage.setReadStatus(Message.READ_STATUS_SENT);
            tempMessage.setPeerId(peerId);

            adapter.addMessage(tempMessage);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);

            // Отправляем сообщение через API
            try {
                String url = "https://api.vk.com/method/messages.send" +
                        "?access_token=" + accessToken +
                        "&v=5.131" +
                        "&peer_id=" + peerId +
                        "&message=" + URLEncoder.encode(text, "UTF-8") +
                        "&random_id=" + System.currentTimeMillis();

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e(TAG, "Failed to send message", e);
                        runOnUiThread(() -> {
                            Toast.makeText(DialogActivityImageSticker.this, "Ошибка отправки сообщения", Toast.LENGTH_SHORT).show();

                            // Удаляем временное сообщение при ошибке
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.isSuccessful()) {
                            String responseBody = response.body().string();
                            Log.d(TAG, "Message sent successfully: " + responseBody);

                            try {
                                JSONObject json = new JSONObject(responseBody);
                                if (json.has("response")) {
                                    int messageId = json.getInt("response");
                                    // tempMessage.setMessageId(String.valueOf(messageId));
                                }
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing send response", e);
                            }

                            runOnUiThread(() -> {
                                // Обновляем статус отправленного сообщения
                                int messageIndex = messageList.indexOf(tempMessage);
                                if (messageIndex != -1) {
                                    Message sentMessage = messageList.get(messageIndex);
                                    sentMessage.setReadStatus(Message.READ_STATUS_READ);
                                    adapter.notifyItemChanged(messageIndex);
                                }
                            });
                        } else {
                            Log.e(TAG, "Failed to send message, code: " + response.code());
                            runOnUiThread(() -> {
                                Toast.makeText(DialogActivityImageSticker.this, "Ошибка отправки: " + response.code(), Toast.LENGTH_SHORT).show();

                                // Удаляем временное сообщение при ошибке
                                messageList.remove(tempMessage);
                                adapter.notifyDataSetChanged();
                            });
                        }
                    }
                });
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Encoding error", e);
                Toast.makeText(this, "Ошибка кодирования", Toast.LENGTH_SHORT).show();

                // Удаляем временное сообщение при ошибке
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            }
        } else {
            Toast.makeText(this, "Не удалось отправить сообщение", Toast.LENGTH_SHORT).show();
        }
    }

    private Map<String, String> parseUserNames(JSONArray profiles) {
        Map<String, String> userNames = new HashMap<>();
        if (profiles != null) {
            for (int i = 0; i < profiles.length(); i++) {
                try {
                    JSONObject profile = profiles.getJSONObject(i);
                    String userId = String.valueOf(profile.optInt("id"));
                    String firstName = profile.optString("first_name");
                    String lastName = profile.optString("last_name");
                    userNames.put(userId, firstName + " " + lastName);
                    Log.d(TAG, "Parsed user: " + userId + " - " + firstName + " " + lastName);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing user profile", e);
                }
            }
        }
        return userNames;
    }

    private void showPopupMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        MenuInflater inflater = popupMenu.getMenuInflater();
        inflater.inflate(R.menu.dialog_menu, popupMenu.getMenu());

        // Для API 29+ можно установить силу отображения иконок
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popupMenu.setForceShowIcon(true);
        }

        // Обработчик выбора пунктов меню
        popupMenu.setOnMenuItemClickListener(item -> {
            handleMenuAction(item.getItemId());
            return true;
        });

        // Показываем меню
        popupMenu.show();
    }

    private void handleMenuAction(int menuItemId) {
        switch (menuItemId) {
            case R.id.menu_search:
                showSearchDialog();
                break;
            case R.id.menu_clear:
                showClearHistoryDialog();
                break;
            case R.id.menu_info:
                showDialogInfo();
                break;
            case R.id.menu_sticker_store:
                showStickerStore();
                break;
        }
    }

    // Методы для меню
    private void showUserProfile() {
        if (userId != null) {
            Toast.makeText(this, "Открытие профиля пользователя: " + userName, Toast.LENGTH_SHORT).show();
            // Здесь можно добавить логику открытия профиля
        }
    }

    private void showAttachments() {
        Toast.makeText(this, "Просмотр вложений диалога", Toast.LENGTH_SHORT).show();
        // Логика показа вложений
    }

    private void showDialogInfo() {
        int messageCount = messageList.size();
        String info = "Сообщений в диалоге: " + messageCount +
                "\nСобеседник: " + (userName != null ? userName : "Неизвестно") +
                "\nID: " + (userId != null ? userId : "Неизвестно") +
                "\nСтикерпаков: " + purchasedStickerPacks.size();

        new AlertDialog.Builder(this)
                .setTitle("Информация о диалоге")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Поиск по сообщениям");

        final EditText input = new EditText(this);
        input.setHint("Введите текст для поиска");
        builder.setView(input);

        builder.setPositiveButton("Искать", (dialog, which) -> {
            String searchText = input.getText().toString().trim();
            if (!searchText.isEmpty()) {
                searchMessages(searchText);
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void searchMessages(String searchText) {
        List<Message> searchResults = new ArrayList<>();
        for (Message message : messageList) {
            if (message.getBody().toLowerCase().contains(searchText.toLowerCase())) {
                searchResults.add(message);
            }
        }

        if (searchResults.isEmpty()) {
            Toast.makeText(this, "Сообщения не найдены", Toast.LENGTH_SHORT).show();
        } else {
            showSearchResults(searchResults, searchText);
        }
    }

    private void showSearchResults(List<Message> results, String searchText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Найдено сообщений: " + results.size());

        String[] messages = new String[results.size()];
        for (int i = 0; i < results.size(); i++) {
            Message msg = results.get(i);
            String shortText = msg.getBody().length() > 50 ?
                    msg.getBody().substring(0, 50) + "..." : msg.getBody();
            messages[i] = msg.getSenderName() + ": " + shortText;
        }

        builder.setItems(messages, (dialog, which) -> {
            int position = messageList.indexOf(results.get(which));
            if (position != -1) {
                recyclerView.scrollToPosition(position);
            }
        });

        builder.setPositiveButton("Закрыть", null);
        builder.show();
    }

    private void showClearHistoryDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Очистка истории")
                .setMessage("Вы уверены, что хотите очистить историю переписки? Это действие нельзя отменить.")
                .setPositiveButton("Очистить", (dialog, which) -> {
                    messageList.clear();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // Метод для запуска активности
    public static void start(Context context, String userId, String userName, String peerId, boolean isSpecialUser) {
        Intent intent = new Intent(context, DialogActivityImageSticker.class);
        intent.putExtra("userId", userId);
        intent.putExtra("userName", userName);
        intent.putExtra("peerId", peerId);
        intent.putExtra("isSpecialUser", isSpecialUser);
        context.startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Очистка ресурсов если необходимо
    }
}