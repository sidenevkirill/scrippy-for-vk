package ru.lisdevs.messenger.dialog;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.friends.PhotoViewerFragment;
import ru.lisdevs.messenger.messages.AttachmentsFragment;
import ru.lisdevs.messenger.messages.stickers.StickerGridFragment;
import ru.lisdevs.messenger.messages.stickers.StickerPackManagerActivity;
import ru.lisdevs.messenger.messages.stickers.StickerStoreActivity;
import ru.lisdevs.messenger.messages.stickers.StickersAdapter;
import ru.lisdevs.messenger.messages.stickers.StickersPagerAdapter;
import ru.lisdevs.messenger.model.Attachment;
import ru.lisdevs.messenger.model.Message;
import ru.lisdevs.messenger.model.Sticker;
import ru.lisdevs.messenger.model.StickerPack;
import ru.lisdevs.messenger.official.audios.Audio;
import ru.lisdevs.messenger.official.audios.AudioListFragment;
import ru.lisdevs.messenger.official.audios.ShareToFriendsBottomSheet;
import ru.lisdevs.messenger.settings.SettingsFragment;
import ru.lisdevs.messenger.utils.StickerManager;
import ru.lisdevs.messenger.utils.TokenManager;

import android.os.Build;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;

import androidx.recyclerview.widget.GridLayoutManager;


import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;


import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;


import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import androidx.appcompat.widget.PopupMenu;

public class DialogActivity extends AppCompatActivity implements StickerGridFragment.OnStickerClickListener, ShareToFriendsBottomSheet.ShareAudioListener {

    private static final String TAG = "DialogActivity";
    private static final int REQUEST_CODE_STICKER_STORE = 1001;
    private static final int MESSAGES_PER_PAGE = 30;
    private static final int REQUEST_CODE_SELECT_AUDIO = 1002;
    private static final int REQUEST_CODE_SELECT_PHOTO = 1003;
    private static final int REQUEST_CODE_TAKE_PHOTO = 1004;

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
    private TextView toolbarStatus;
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

    // BroadcastReceiver для обновления стикеров и настроек
    private BroadcastReceiver stickerUpdateReceiver;
    private BroadcastReceiver settingsChangeReceiver;

    private LinearLayout stickerManagementPanel;
    private boolean isStickerManagementPanelVisible = true;

    private TextWatcher textWatcher;
    private boolean hasText = false;

    // Для отправки треков
    private Audio currentAudioToShare;
    private ImageButton btnAttachAudio;
    private ImageButton btnAttachPhoto;
    private View inputContainer;
    private View navigationSpacer;

    // Для работы с фото
    private Uri currentPhotoUri;

    // Флаг тестового режима
    private boolean isTestMode = false;
    private ImageButton stickerButton;

    // BroadcastReceiver для изменения фона
    private BroadcastReceiver backgroundChangeReceiver;

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
            isTestMode = intent.getBooleanExtra("is_test_mode", false);

            Log.d(TAG, "Received arguments - userId: " + userId +
                    ", userName: " + userName +
                    ", peerId: " + peerId +
                    ", isSpecialUser: " + isSpecialUser +
                    ", isTestMode: " + isTestMode);
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
        initInsets();
        setupStickersClickListeners();
        setupTextWatcher();

        checkTestMode();

        // Находим иконку стикеров
        stickerButton = findViewById(R.id.btnAttach);

        // Скрываем иконку стикеров для тестового аккаунта
        if (isTestMode && stickerButton != null) {
            stickerButton.setVisibility(View.GONE);
        }
    }

    private void checkTestMode() {
        String token = TokenManager.getInstance(this).getToken();
        // Та же логика проверки тестового режима
        if (token == null || token.contains("test") || token.equals("demo") ||
                token.length() < 10 || token.equals("000000")) {
            isTestMode = true;
            Log.d("ChatActivity", "Тестовый режим активирован");
        } else {
            isTestMode = false;
        }

        // Инициализация BroadcastReceiver для стикеров
        stickerUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("STICKER_PACKS_UPDATED".equals(intent.getAction())) {
                    Log.d(TAG, "Received sticker packs update broadcast");
                    loadStickers();
                }
            }
        };

        // Инициализация BroadcastReceiver для изменений настроек
        settingsChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("SETTINGS_CHANGED".equals(intent.getAction())) {
                    Log.d(TAG, "Settings changed, stickers behavior might be affected");
                    // Можно добавить дополнительную логику при изменении настроек
                }
            }
        };

        // Инициализация BroadcastReceiver для изменения фона
        backgroundChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("CHAT_BACKGROUND_CHANGED".equals(intent.getAction())) {
                    int backgroundId = intent.getIntExtra("background_id", 0);
                    applyChatBackground(backgroundId);
                    Log.d(TAG, "Chat background changed to: " + backgroundId);
                }
            }
        };

        // Применяем текущий фон при создании активности
        applyChatBackground(SettingsFragment.getCurrentChatBackground(this));

        // Загружаем историю сообщений
        if (isTestMode) {
            // В тестовом режиме показываем тестовые сообщения
            showTestMessages();
        } else if (peerId == null) {
            Log.e(TAG, "peerId is still null! Cannot load messages.");
            Toast.makeText(this, "Ошибка: не удалось загрузить диалог", Toast.LENGTH_SHORT).show();
        } else {
            loadDialogHistory(0, true);
        }
    }

    // Метод для применения фона чата
    private void applyChatBackground(int backgroundId) {
        // Применяем фон к основному layout
        View mainLayout = findViewById(R.id.main_layout);
        if (mainLayout != null) {
            SettingsFragment.applyBackgroundToView(this, mainLayout, backgroundId);
        }

        // Применяем фон к RecyclerView
        if (recyclerView != null) {
            SettingsFragment.applyBackgroundToView(this, recyclerView, backgroundId);
        }

        // Применяем фон к SwipeRefreshLayout
        if (swipeRefreshLayout != null) {
            SettingsFragment.applyBackgroundToView(this, swipeRefreshLayout, backgroundId);
        }
    }

    // Метод для показа тестовых сообщений
    private void showTestMessages() {
        Log.d(TAG, "Показываем тестовые сообщения для диалога: " + userName);

        List<Message> testMessages = createTestMessages();
        messageList.clear();
        messageList.addAll(testMessages);

        if (adapter != null) {
            adapter.notifyDataSetChanged();
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }

        // Обновляем заголовок с пометкой "Демо"
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(userName + " (Демо)");
        }

        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        if (toolbarTitle != null) {
            toolbarTitle.setText(userName + " (Демо)");
        }

        Toast.makeText(this, "Демо-режим: тестовый диалог", Toast.LENGTH_SHORT).show();
    }

    // Метод для создания тестовых сообщений
    private List<Message> createTestMessages() {
        List<Message> messages = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        // Сообщения от собеседника
        messages.add(new Message(
                userId != null ? userId : "123456789",
                userName != null ? userName : "Собеседник",
                "Привет! Это тестовый диалог в демо-режиме.",
                currentTime - 3600000, // 1 час назад
                null
        ));

        // Сообщения от текущего пользователя
        String currentUserId = "current_user";
        messages.add(new Message(
                currentUserId,
                "Вы",
                "Здравствуйте! Да, я вижу что это демо-версия чата.",
                currentTime - 1800000, // 30 минут назад
                null
        ));
        messages.get(1).setOutgoing(true);
        messages.get(1).setReadStatus(Message.READ_STATUS_READ);

        // Сообщение со стикером (имитация)
        messages.add(new Message(
                userId != null ? userId : "123456789",
                userName != null ? userName : "Собеседник",
                "",
                currentTime - 1200000, // 20 минут назад
                null
        ));
        messages.get(2).setPreviewText("😊 Стикер");

        // Сообщение с аудио
        messages.add(new Message(
                userId != null ? userId : "123456789",
                userName != null ? userName : "Собеседник",
                "Послушай этот трек!",
                currentTime - 900000, // 15 минут назад
                null
        ));
        messages.get(3).setPreviewText("🎵 Исполнитель - Название трека");

        // Ответное сообщение
        messages.add(new Message(
                currentUserId,
                "Вы",
                "Крутой трек! Спасибо за рекомендацию 👍",
                currentTime - 600000, // 10 минут назад
                null
        ));
        messages.get(4).setOutgoing(true);
        messages.get(4).setReadStatus(Message.READ_STATUS_READ);

        // Сообщение с вложением
        messages.add(new Message(
                userId != null ? userId : "123456789",
                userName != null ? userName : "Собеседник",
                "Посмотри это фото",
                currentTime - 300000, // 5 минут назад
                null
        ));
        messages.get(5).setPreviewText("📷 Фотография");

        // Последнее сообщение
        messages.add(new Message(
                currentUserId,
                "Вы",
                "Отлично! Буду тестировать дальше.",
                currentTime - 60000, // 1 минуту назад
                null
        ));
        messages.get(6).setOutgoing(true);
        messages.get(6).setReadStatus(Message.READ_STATUS_SENT);

        return messages;
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
        toolbarStatus = findViewById(R.id.toolbarStatus);

        if (toolbarTitle != null) {
            // Добавляем пометку о демо-режиме если включен
            String title = userName != null ? userName : "Диалог";
            if (isTestMode) {
                title += " (Демо)";
            }
            toolbarTitle.setText(title);
        }

        // Инициализируем статус пользователя
        setupUserStatus();

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

        // Находим кнопку для прикрепления аудио
        btnAttachAudio = findViewById(R.id.btnAttachAudio);

        // Находим кнопку для прикрепления фото
        btnAttachPhoto = findViewById(R.id.btnAttachPhoto);

        // В тестовом режиме скрываем прогресс-бар
        if (isTestMode && progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    // Новый метод для настройки статуса пользователя
    private void setupUserStatus() {
        if (toolbarStatus != null) {
            if (isTestMode) {
                // В тестовом режиме показываем случайный статус
                String[] demoStatuses = {"в сети", "был(а) недавно", "был(а) только что"};
                String randomStatus = demoStatuses[new Random().nextInt(demoStatuses.length)];
                toolbarStatus.setText(randomStatus);

                // Устанавливаем цвет в зависимости от статуса
                if (randomStatus.equals("в сети")) {
                    toolbarStatus.setTextColor(getResources().getColor(R.color.green_500));
                } else {
                    toolbarStatus.setTextColor(getResources().getColor(R.color.gray));
                }
            } else {
                // В реальном режиме загружаем статус пользователя
                loadUserStatus();
            }
        }
    }

    // Новый метод для загрузки реального статуса пользователя
    private void loadUserStatus() {
        if (userId == null || isTestMode) return;

        String accessToken = TokenManager.getInstance(this).getToken();
        if (accessToken != null) {
            String url = "https://api.vk.com/method/users.get" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&user_ids=" + userId +
                    "&fields=online,last_seen";

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to load user status", e);
                    runOnUiThread(() -> {
                        toolbarStatus.setText("статус неизвестен");
                        toolbarStatus.setTextColor(getResources().getColor(R.color.gray));
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("response")) {
                                JSONArray users = json.getJSONArray("response");
                                if (users.length() > 0) {
                                    JSONObject user = users.getJSONObject(0);
                                    boolean isOnline = user.optBoolean("online", false);

                                    runOnUiThread(() -> {
                                        if (isOnline) {
                                            toolbarStatus.setText("в сети");
                                            toolbarStatus.setTextColor(getResources().getColor(R.color.green_500));
                                        } else {
                                            // Показываем время последнего посещения
                                            if (user.has("last_seen")) {
                                                JSONObject lastSeen = null;
                                                try {
                                                    lastSeen = user.getJSONObject("last_seen");
                                                } catch (JSONException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                long lastSeenTime = 0;
                                                try {
                                                    lastSeenTime = lastSeen.getLong("time") * 1000;
                                                } catch (JSONException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                String lastSeenText = formatLastSeen(lastSeenTime);
                                                toolbarStatus.setText(lastSeenText);
                                            } else {
                                                toolbarStatus.setText("не в сети");
                                            }
                                            toolbarStatus.setTextColor(getResources().getColor(R.color.gray));
                                        }
                                    });
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing user status", e);
                            runOnUiThread(() -> {
                                toolbarStatus.setText("статус неизвестен");
                                toolbarStatus.setTextColor(getResources().getColor(R.color.gray));
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            toolbarStatus.setText("статус неизвестен");
                            toolbarStatus.setTextColor(getResources().getColor(R.color.gray));
                        });
                    }
                }
            });
        }
    }

    // Новый метод для форматирования времени последнего посещения
    private String formatLastSeen(long lastSeenTime) {
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - lastSeenTime;
        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);
        long days = diff / (24 * 60 * 60 * 1000);

        if (minutes < 1) {
            return "был(а) только что";
        } else if (minutes < 60) {
            return "был(а) " + minutes + " " + getMinutesText((int) minutes) + " назад";
        } else if (hours < 24) {
            return "был(а) " + hours + " " + getHoursText((int) hours) + " назад";
        } else {
            return "был(а) " + days + " " + getDaysText((int) days) + " назад";
        }
    }

    private String getMinutesText(int minutes) {
        if (minutes % 10 == 1 && minutes % 100 != 11) return "минуту";
        if (minutes % 10 >= 2 && minutes % 10 <= 4 && (minutes % 100 < 10 || minutes % 100 >= 20)) return "минуты";
        return "минут";
    }

    private String getHoursText(int hours) {
        if (hours % 10 == 1 && hours % 100 != 11) return "час";
        if (hours % 10 >= 2 && hours % 10 <= 4 && (hours % 100 < 10 || hours % 100 >= 20)) return "часа";
        return "часов";
    }

    private String getDaysText(int days) {
        if (days % 10 == 1 && days % 100 != 11) return "день";
        if (days % 10 >= 2 && days % 10 <= 4 && (days % 100 < 10 || days % 100 >= 20)) return "дня";
        return "дней";
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
        setupStickersViewPagerScrollListener();

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

        // Кнопка для управления стикерпаками
        ImageButton btnManageStickers = findViewById(R.id.btnManageStickers);
        if (btnManageStickers != null) {
            btnManageStickers.setOnClickListener(v -> openStickerPackManager());
        }
    }

    private void setupStickersViewPager() {
        purchasedStickerPacks = stickerManager.getEnabledStickerPacks(this);

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

    private void setupStickersViewPagerScrollListener() {
        if (stickersViewPager != null) {
            stickersViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                    super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                    // Можно добавить анимацию скрытия/показа при прокрутке
                }

                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    // При смене страницы скрываем панель управления
                    hideStickerManagementPanel();
                }

                @Override
                public void onPageScrollStateChanged(int state) {
                    super.onPageScrollStateChanged(state);
                    if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                        // Начали прокрутку - скрываем панель
                        hideStickerManagementPanel();
                    }
                }
            });
        }

        // Также настраиваем для RecyclerView (фолбэк вариант)
        RecyclerView stickersRecyclerView = findViewById(R.id.stickersRecyclerView);
        if (stickersRecyclerView != null) {
            stickersRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (Math.abs(dy) > 2) { // Если прокрутка достаточно значительная
                        hideStickerManagementPanel();
                    }
                }

                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        hideStickerManagementPanel();
                    }
                }
            });
        }
    }

    // Методы для управления видимостью панели управления
    private void hideStickerManagementPanel() {
        if (stickerManagementPanel != null && isStickerManagementPanelVisible) {
            stickerManagementPanel.animate()
                    .translationY(stickerManagementPanel.getHeight())
                    .setDuration(200)
                    .withEndAction(() -> {
                        stickerManagementPanel.setVisibility(View.GONE);
                        isStickerManagementPanelVisible = false;
                    })
                    .start();
        }
    }

    private void showStickerManagementPanel() {
        if (stickerManagementPanel != null && !isStickerManagementPanelVisible) {
            stickerManagementPanel.setVisibility(View.VISIBLE);
            stickerManagementPanel.animate()
                    .translationY(0)
                    .setDuration(200)
                    .withEndAction(() -> isStickerManagementPanelVisible = true)
                    .start();
        }
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

        // Показываем панель управления при открытии панели стикеров
        showStickerManagementPanel();

        // Скрываем клавиатуру
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && editTextMessage != null) {
            imm.hideSoftInputFromWindow(editTextMessage.getWindowToken(), 0);
        }
    }

    private void hideStickersPanel() {
        stickersPanel.setVisibility(View.GONE);
        isStickersPanelVisible = false;

        // Показываем панель управления при закрытии всей панели стикеров
        showStickerManagementPanel();
    }

    private void setupStickersClickListeners() {
        // При клике на область стикеров показываем панель управления
        View stickersContent = findViewById(R.id.stickersViewPager);
        if (stickersContent != null) {
            stickersContent.setOnClickListener(v -> {
                if (!isStickerManagementPanelVisible) {
                    showStickerManagementPanel();
                }
            });
        }

        // То же для RecyclerView
        RecyclerView stickersRecyclerView = findViewById(R.id.stickersRecyclerView);
        if (stickersRecyclerView != null) {
            stickersRecyclerView.setOnClickListener(v -> {
                if (!isStickerManagementPanelVisible) {
                    showStickerManagementPanel();
                }
            });
        }
    }

    private void loadStickers() {
        // Используем только включенные стикеры
        List<Sticker> enabledStickers = stickerManager.getEnabledStickers(this);

        // Fallback если все стикеры сломаны
        if (enabledStickers.isEmpty() || stickerManager.areAllStickersBroken(enabledStickers)) {
            Log.w(TAG, "Using fallback stickers");
            enabledStickers = stickerManager.getFallbackStickers();
        }

        // Обновляем адаптеры
        if (stickersAdapter != null) {
            stickersAdapter.setStickers(enabledStickers);
        }

        // Обновляем ViewPager с включенными пакетами
        purchasedStickerPacks = stickerManager.getEnabledStickerPacks(this);

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

        Log.d(TAG, "Enabled stickers loaded: " + enabledStickers.size() + " stickers, " + purchasedStickerPacks.size() + " packs");
    }

    @Override
    public void onStickerClick(Sticker sticker) {
        if (isTestMode) {
            // В тестовом режиме показываем сообщение о демо
            Toast.makeText(this, "Демо-режим: стикер отправлен", Toast.LENGTH_SHORT).show();

            // Создаем тестовое сообщение со стикером
            Message stickerMessage = new Message(
                    "current_user",
                    "Вы",
                    "",
                    System.currentTimeMillis(),
                    null
            );
            stickerMessage.setOutgoing(true);
            stickerMessage.setReadStatus(Message.READ_STATUS_SENT);
            stickerMessage.setPreviewText("😊 Стикер");

            adapter.addMessage(stickerMessage);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            hideStickersPanel();
        } else {
            // Проверяем настройку и отправляем соответствующим способом
            if (SettingsFragment.isSendStickersAsStickersEnabled(this)) {
                sendStickerAsSticker(sticker);
            } else {
                sendStickerAsGraffiti(sticker); // Изменено: отправляем как граффити вместо изображения
            }
        }
    }

    // Метод для отправки стикера как граффити
    private void sendStickerAsGraffiti(Sticker sticker) {
        if (isTestMode) {
            // В тестовом режиме показываем сообщение о демо
            Toast.makeText(this, "Демо-режим: стикер отправлен как граффити", Toast.LENGTH_SHORT).show();
            return;
        }

        if (sticker == null || sticker.getImageUrl() == null) {
            Toast.makeText(this, "Ошибка: неверный стикер", Toast.LENGTH_SHORT).show();
            return;
        }

        String accessToken = TokenManager.getInstance(this).getToken();
        if (accessToken != null && peerId != null) {
            // Создаем временное сообщение со стикером как граффити
            Message stickerMessage = new Message(userId, userName, "", System.currentTimeMillis(), null);
            stickerMessage.setOutgoing(true);
            stickerMessage.setReadStatus(Message.READ_STATUS_SENT);
            stickerMessage.setPeerId(peerId);
            stickerMessage.setPreviewText("🎨 Граффити");

            // Добавляем в список для мгновенного отображения
            adapter.addMessage(stickerMessage);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);

            // Загружаем изображение стикера и отправляем как граффити
            loadAndSendStickerAsGraffiti(sticker, stickerMessage);
        } else {
            Toast.makeText(this, "Не удалось отправить стикер", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadAndSendStickerAsGraffiti(Sticker sticker, Message tempMessage) {
        if (sticker == null) {
            runOnUiThread(() -> {
                Toast.makeText(DialogActivity.this, "Ошибка: неверный стикер", Toast.LENGTH_SHORT).show();
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
            return;
        }

        // Проверяем валидность URL
        String imageUrl = sticker.getImageUrl();
        if (!isValidUrl(imageUrl)) {
            Log.e(TAG, "Invalid sticker URL: " + imageUrl);
            runOnUiThread(() -> {
                Toast.makeText(DialogActivity.this, "Ошибка: неверный URL стикера", Toast.LENGTH_SHORT).show();
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
            return;
        }

        // Исправление: добавляем схему к URL если её нет
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            imageUrl = "https://" + imageUrl;
            Log.d(TAG, "Fixed URL scheme: " + imageUrl);
        }

        // Загружаем изображение стикера
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(imageUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to load sticker image", e);
                runOnUiThread(() -> {
                    Toast.makeText(DialogActivity.this, "Ошибка загрузки стикера", Toast.LENGTH_SHORT).show();
                    messageList.remove(tempMessage);
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Получаем байты изображения
                    byte[] imageBytes = response.body().bytes();

                    // Отправляем как граффити через VK API
                    uploadStickerAsGraffiti(imageBytes, tempMessage, sticker);
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivity.this, "Ошибка загрузки изображения: " + response.code(), Toast.LENGTH_SHORT).show();
                        messageList.remove(tempMessage);
                        adapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }

    private void uploadStickerAsGraffiti(byte[] imageBytes, Message tempMessage, Sticker originalSticker) {
        String accessToken = TokenManager.getInstance(this).getToken();

        // Получаем URL для загрузки граффити
        String getUploadUrl = "https://api.vk.com/method/docs.getMessagesUploadServer" +
                "?access_token=" + accessToken +
                "&type=graffiti" +
                "&peer_id=" + peerId +
                "&v=5.131";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(getUploadUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to get graffiti upload server", e);
                runOnUiThread(() -> {
                    Toast.makeText(DialogActivity.this, "Ошибка получения сервера загрузки граффити", Toast.LENGTH_SHORT).show();
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

                            // Загружаем изображение на сервер как граффити
                            uploadGraffitiToServer(imageBytes, uploadUrl, tempMessage, originalSticker);
                        } else {
                            handleGraffitiUploadError(json, tempMessage);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing graffiti upload server response", e);
                        runOnUiThread(() -> {
                            Toast.makeText(DialogActivity.this, "Ошибка обработки ответа сервера", Toast.LENGTH_SHORT).show();
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivity.this, "Ошибка получения сервера загрузки: " + response.code(), Toast.LENGTH_SHORT).show();
                        messageList.remove(tempMessage);
                        adapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }

    private void uploadGraffitiToServer(byte[] imageBytes, String uploadUrl, Message tempMessage, Sticker originalSticker) {
        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "graffiti.png",
                        RequestBody.create(imageBytes, MediaType.parse("image/png")))
                .build();

        Request request = new Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to upload graffiti", e);
                runOnUiThread(() -> {
                    Toast.makeText(DialogActivity.this, "Ошибка загрузки граффити", Toast.LENGTH_SHORT).show();
                    messageList.remove(tempMessage);
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        // Сохраняем граффити в VK
                        saveGraffiti(responseBody, tempMessage, originalSticker);
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing graffiti upload response", e);
                        runOnUiThread(() -> {
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivity.this, "Ошибка загрузки граффити: " + response.code(), Toast.LENGTH_SHORT).show();
                        messageList.remove(tempMessage);
                        adapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }

    private void saveGraffiti(String uploadResponse, Message tempMessage, Sticker originalSticker) {
        String accessToken = TokenManager.getInstance(this).getToken();

        try {
            JSONObject uploadJson = new JSONObject(uploadResponse);
            String file = uploadJson.getString("file");

            String saveUrl = "https://api.vk.com/method/docs.save" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&file=" + URLEncoder.encode(file, "UTF-8");

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(saveUrl)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to save graffiti", e);
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivity.this, "Ошибка сохранения граффити", Toast.LENGTH_SHORT).show();
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
                                JSONObject graffiti = json.getJSONObject("response");

                                // Получаем данные граффити
                                JSONObject doc = graffiti.getJSONObject("graffiti");
                                int ownerId = doc.getInt("owner_id");
                                int docId = doc.getInt("id");

                                // Отправляем сообщение с граффити
                                sendGraffitiMessage(ownerId, docId, tempMessage, originalSticker);
                            } else {
                                handleGraffitiSaveError(json, tempMessage);
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing save graffiti response", e);
                            runOnUiThread(() -> {
                                messageList.remove(tempMessage);
                                adapter.notifyDataSetChanged();
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(DialogActivity.this, "Ошибка сохранения граффити: " + response.code(), Toast.LENGTH_SHORT).show();
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                        });
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in saveGraffiti", e);
            runOnUiThread(() -> {
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
        }
    }

    private void sendGraffitiMessage(int ownerId, int docId, Message tempMessage, Sticker originalSticker) {
        String accessToken = TokenManager.getInstance(this).getToken();

        try {
            // Формируем attachment для граффити в формате doc{owner_id}_{doc_id}
            String attachment = "doc" + ownerId + "_" + docId;

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
                    Log.e(TAG, "Failed to send graffiti message", e);
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivity.this, "Ошибка отправки граффити", Toast.LENGTH_SHORT).show();
                        messageList.remove(tempMessage);
                        adapter.notifyDataSetChanged();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            // ВАЖНОЕ ИСПРАВЛЕНИЕ: Полностью перезагружаем историю для получения правильных данных
                            loadDialogHistory(0, true);

                            Toast.makeText(DialogActivity.this, "Граффити отправлено", Toast.LENGTH_SHORT).show();
                            hideStickersPanel();
                        } else {
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(DialogActivity.this, "Ошибка отправки: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error sending graffiti message", e);
            runOnUiThread(() -> {
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
        }
    }

    private void handleGraffitiUploadError(JSONObject json, Message tempMessage) {
        try {
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                String errorMsg = error.optString("error_msg", "Неизвестная ошибка");
                runOnUiThread(() -> {
                    Toast.makeText(DialogActivity.this,
                            "Ошибка загрузки граффити: " + errorMsg, Toast.LENGTH_LONG).show();
                    messageList.remove(tempMessage);
                    adapter.notifyDataSetChanged();
                });
            }
        } catch (JSONException e) {
            runOnUiThread(() -> {
                Toast.makeText(DialogActivity.this, "Ошибка загрузки граффити", Toast.LENGTH_SHORT).show();
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
        }
    }

    private void handleGraffitiSaveError(JSONObject json, Message tempMessage) {
        try {
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                String errorMsg = error.optString("error_msg", "Неизвестная ошибка");
                runOnUiThread(() -> {
                    Toast.makeText(DialogActivity.this,
                            "Ошибка сохранения граффити: " + errorMsg, Toast.LENGTH_LONG).show();
                    messageList.remove(tempMessage);
                    adapter.notifyDataSetChanged();
                });
            }
        } catch (JSONException e) {
            runOnUiThread(() -> {
                Toast.makeText(DialogActivity.this, "Ошибка сохранения граффити", Toast.LENGTH_SHORT).show();
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
        }
    }

    // Оригинальный метод отправки стикера (как стикер)
    private void sendStickerAsSticker(Sticker sticker) {
        if (isTestMode) {
            // В тестовом режиме показываем сообщение о демо
            Toast.makeText(this, "Демо-режим: стикер отправлен", Toast.LENGTH_SHORT).show();
            return;
        }

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
                            Toast.makeText(DialogActivity.this, "Ошибка отправки стикера", Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(DialogActivity.this, "Ошибка отправки: " + response.code(), Toast.LENGTH_SHORT).show();
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

    // Метод для отправки стикера как изображения
    private void sendStickerAsImage(Sticker sticker) {
        if (isTestMode) {
            // В тестовом режиме показываем сообщение о демо
            Toast.makeText(this, "Демо-режим: стикер отправлен", Toast.LENGTH_SHORT).show();
            return;
        }

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
        if (sticker == null) {
            runOnUiThread(() -> {
                Toast.makeText(DialogActivity.this, "Ошибка: неверный стикер", Toast.LENGTH_SHORT).show();
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
            return;
        }

        // Проверяем валидность URL
        String imageUrl = sticker.getImageUrl();
        if (!isValidUrl(imageUrl)) {
            Log.e(TAG, "Invalid sticker URL: " + imageUrl);
            runOnUiThread(() -> {
                Toast.makeText(DialogActivity.this, "Ошибка: неверный URL стикера", Toast.LENGTH_SHORT).show();
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
            return;
        }

        // Исправление: добавляем схему к URL если её нет
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            imageUrl = "https://" + imageUrl;
            Log.d(TAG, "Fixed URL scheme: " + imageUrl);
        }

        // Дополнительная проверка после добавления схемы
        if (!isValidUrl(imageUrl)) {
            Log.e(TAG, "Still invalid URL after fixing scheme: " + imageUrl);
            runOnUiThread(() -> {
                Toast.makeText(DialogActivity.this, "Ошибка: неверный URL стикера", Toast.LENGTH_SHORT).show();
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
            return;
        }

        // Загружаем изображение стикера
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(imageUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to load sticker image", e);
                runOnUiThread(() -> {
                    Toast.makeText(DialogActivity.this, "Ошибка загрузки стикера", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(DialogActivity.this, "Ошибка загрузки изображения: " + response.code(), Toast.LENGTH_SHORT).show();
                        messageList.remove(tempMessage);
                        adapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }

    // Метод для проверки валидности URL
    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        // Проверяем наличие хоста (домена)
        if (url.startsWith("http://") || url.startsWith("https://")) {
            // После схемы должен быть хотя бы один символ
            String withoutScheme = url.substring(url.indexOf("://") + 3);
            if (withoutScheme.isEmpty() || withoutScheme.startsWith("/")) {
                return false;
            }

            // Проверяем наличие точки в домене (минимальная проверка)
            int slashIndex = withoutScheme.indexOf('/');
            String host = slashIndex == -1 ? withoutScheme : withoutScheme.substring(0, slashIndex);
            return host.contains(".") && host.length() > 2;
        } else {
            // Для URL без схемы проверяем наличие точки
            return url.contains(".") && url.length() > 2;
        }
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
                    Toast.makeText(DialogActivity.this, "Ошибка получения сервера загрузки", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(DialogActivity.this, "Ошибка обработки ответа сервера", Toast.LENGTH_SHORT).show();
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivity.this, "Ошибка получения сервера загрузки: " + response.code(), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(DialogActivity.this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(DialogActivity.this, "Ошибка загрузки изображения: " + response.code(), Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(DialogActivity.this, "Ошибка сохранения фото", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(DialogActivity.this, "Ошибка сохранения фото: " + response.code(), Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(DialogActivity.this, "Ошибка отправки стикера", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(DialogActivity.this, "Стикер отправлен как изображение", Toast.LENGTH_SHORT).show();
                        } else {
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(DialogActivity.this, "Ошибка отправки: " + response.code(), Toast.LENGTH_SHORT).show();
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
        if (isTestMode) {
            Toast.makeText(this, "Демо-режим: магазин стикеров недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, StickerStoreActivity.class);
        startActivityForResult(intent, REQUEST_CODE_STICKER_STORE);
    }

    private void openStickerPackManager() {
        if (isTestMode) {
            Toast.makeText(this, "Демо-режим: управление стикерами недоступно", Toast.LENGTH_SHORT).show();
            return;
        }

        StickerPackManagerActivity.start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) return;

        switch (requestCode) {
            case REQUEST_CODE_STICKER_STORE:
                // Обновляем список стикеров после покупки
                purchasedStickerPacks = stickerManager.getEnabledStickerPacks(this);
                if (stickersPagerAdapter != null) {
                    stickersPagerAdapter.setStickerPacks(purchasedStickerPacks);
                    stickersPagerAdapter.notifyDataSetChanged();
                }
                loadStickers();
                Toast.makeText(this, "Стикерпак успешно приобретен!", Toast.LENGTH_SHORT).show();
                break;

            case REQUEST_CODE_SELECT_AUDIO:
                if (data != null) {
                    Audio selectedAudio = data.getParcelableExtra("selected_audio");
                    if (selectedAudio != null) {
                        shareAudioToDialog(selectedAudio);
                    }
                }
                break;

            case REQUEST_CODE_SELECT_PHOTO:
                if (data != null && data.getData() != null) {
                    handleSelectedPhoto(data.getData());
                }
                break;

            case REQUEST_CODE_TAKE_PHOTO:
                if (currentPhotoUri != null) {
                    handleSelectedPhoto(currentPhotoUri);
                }
                break;
        }
    }

    private void setupSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (isTestMode) {
                    // В тестовом режиме просто скрываем индикатор
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(this, "Демо-режим: обновление недоступно", Toast.LENGTH_SHORT).show();
                } else {
                    // Загружаем более старые сообщения
                    loadOlderMessages();
                }
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

            // Обработчик клика на аватарку для перехода к вложениям
            avatarTextView.setOnClickListener(v -> {
                showAttachmentsFragment();
            });

            // Делаем аватарку кликабельной
            avatarTextView.setClickable(true);
            avatarTextView.setFocusable(true);

            // Добавляем ripple эффект
            try {
                avatarTextView.setBackgroundResource(R.drawable.ripple_effect);
                // Восстанавливаем цвет поверх ripple
                GradientDrawable rippleDrawable = new GradientDrawable();
                rippleDrawable.setShape(GradientDrawable.OVAL);
                rippleDrawable.setColor(color);
                avatarTextView.setBackground(rippleDrawable);
            } catch (Exception e) {
                Log.w(TAG, "Ripple effect not available, using default background");
            }
        }

        // Настраиваем иконку верификации
        if (verifiedIcon != null) {
            if (isSpecialUser) {
                verifiedIcon.setVisibility(View.VISIBLE);
                verifiedIcon.setImageResource(R.drawable.circle_shufle);

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

    // НОВЫЙ МЕТОД: Показать фрагмент с вложениями
    private void showAttachmentsFragment() {
        if (isTestMode) {
            Toast.makeText(this, "Демо-режим: просмотр вложений", Toast.LENGTH_SHORT).show();

            // В демо-режиме показываем тестовые вложения
            List<Attachment> demoAttachments = createDemoAttachments();
            AttachmentsFragment fragment = AttachmentsFragment.newInstance(demoAttachments, userName != null ? userName : "Собеседник");

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(android.R.id.content, fragment);
            transaction.addToBackStack("attachments");
            transaction.commit();
        } else {
            // Загружаем реальные вложения из истории сообщений
            List<Attachment> allAttachments = getAllAttachmentsFromMessages();

            if (allAttachments.isEmpty()) {
                Toast.makeText(this, "В этом диалоге нет вложений", Toast.LENGTH_SHORT).show();
            } else {
                AttachmentsFragment fragment = AttachmentsFragment.newInstance(allAttachments, userName != null ? userName : "Собеседник");

                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(android.R.id.content, fragment);
                transaction.addToBackStack("attachments");
                transaction.commit();
            }
        }
    }

    // НОВЫЙ МЕТОД: Собрать все вложения из сообщений
    private List<Attachment> getAllAttachmentsFromMessages() {
        List<Attachment> allAttachments = new ArrayList<>();

        for (Message message : messageList) {
            if (message.hasAttachments()) {
                allAttachments.addAll(message.getAttachments());
            }
        }

        return allAttachments;
    }

    // НОВЫЙ МЕТОД: Создать демо-вложения для тестового режима
    private List<Attachment> createDemoAttachments() {
        List<Attachment> demoAttachments = new ArrayList<>();

        // Демо-фото
        Attachment photoAttachment = new Attachment();
        photoAttachment.setType("photo");
        Attachment.Photo demoPhoto = new Attachment.Photo();
        List<Attachment.Size> sizes = new ArrayList<>();
        Attachment.Size size = new Attachment.Size();
        size.setUrl("https://static.rustore.ru/2025/10/16/e7/apk/2063663821/content/SCREENSHOT/9adc8296-a5d3-4cf2-864b-3bd61a65080f.jpg");
        size.setType("x");
        size.setWidth(800);
        size.setHeight(600);
        sizes.add(size);
        demoPhoto.setSizes(sizes);
        photoAttachment.setPhoto(demoPhoto);
        demoAttachments.add(photoAttachment);

        // Демо-аудио
        Attachment audioAttachment = new Attachment();
        audioAttachment.setType("audio");
        Attachment.Audio demoAudio = new Attachment.Audio();
        demoAudio.setArtist("Demo Artist");
        demoAudio.setTitle("Demo Track");
        demoAudio.setDuration(180);
        demoAudio.setUrl("https://example.com/demo-audio.mp3");
        audioAttachment.setAudio(demoAudio);
        demoAttachments.add(audioAttachment);

        // Демо-документ
        Attachment docAttachment = new Attachment();
        docAttachment.setType("doc");
        Attachment.Document demoDoc = new Attachment.Document();
        demoDoc.setTitle("Demo Document.pdf");
        demoDoc.setExt("pdf");
        demoDoc.setSize(1024 * 1024); // 1MB
        demoDoc.setUrl("https://example.com/demo-document.pdf");
        docAttachment.setDoc(demoDoc);
        demoAttachments.add(docAttachment);

        return demoAttachments;
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

        // Устанавливаем обработчик кликов на фото
        adapter.setOnPhotoClickListener(new DialogAdapter.OnPhotoClickListener() {
            @Override
            public void onPhotoClick(Message message, Attachment.Photo photo) {
                // Собираем все фото из этого сообщения
                List<String> photoUrls = extractPhotoUrlsFromMessage(message);

                if (!photoUrls.isEmpty()) {
                    // Находим позицию текущего фото
                    int currentPosition = findPhotoPosition(photoUrls, photo);

                    // Открываем просмотрщик фото
                    showPhotoViewer(photoUrls, currentPosition);
                }
            }
        });

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

    // МЕТОДЫ ДЛЯ ПРОСМОТРА ФОТОГРАФИЙ
    public void showPhotoViewer(List<String> photoUrls, int currentPosition) {
        PhotoViewerFragment fragment = PhotoViewerFragment.newInstance(
                new ArrayList<>(photoUrls), currentPosition);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(android.R.id.content, fragment);
        transaction.addToBackStack("photo_viewer");
        transaction.commit();
    }

    private List<String> extractPhotoUrlsFromMessage(Message message) {
        List<String> photoUrls = new ArrayList<>();
        if (message != null && message.hasAttachments()) {
            for (Attachment attachment : message.getAttachments()) {
                if ("photo".equals(attachment.getType()) && attachment.getPhoto() != null) {
                    String bestUrl = attachment.getPhoto().getBestQualityUrl();
                    if (bestUrl != null && !bestUrl.isEmpty()) {
                        photoUrls.add(bestUrl);
                    }
                }
            }
        }
        return photoUrls;
    }

    private int findPhotoPosition(List<String> photoUrls, Attachment.Photo targetPhoto) {
        String targetUrl = targetPhoto.getBestQualityUrl();
        for (int i = 0; i < photoUrls.size(); i++) {
            if (photoUrls.get(i).equals(targetUrl)) {
                return i;
            }
        }
        return 0;
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
                        Toast.makeText(DialogActivity.this, "Ошибка загрузки сообщений", Toast.LENGTH_SHORT).show();
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
                                    Toast.makeText(DialogActivity.this, "Ошибка VK API: " + error.optString("error_msg"), Toast.LENGTH_SHORT).show();
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
                                        Toast.makeText(DialogActivity.this, "Диалог пуст", Toast.LENGTH_SHORT).show();
                                    }

                                    currentOffset = offset + items.length();
                                });
                            } else {
                                Log.e(TAG, "No 'response' field in JSON");
                                runOnUiThread(() -> {
                                    Toast.makeText(DialogActivity.this, "Ошибка формата ответа", Toast.LENGTH_SHORT).show();
                                });
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing dialog history", e);
                            runOnUiThread(() -> {
                                Toast.makeText(DialogActivity.this, "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } else {
                        Log.e(TAG, "Unsuccessful response: " + response.code());
                        runOnUiThread(() -> {
                            Toast.makeText(DialogActivity.this, "Ошибка сети: " + response.code(), Toast.LENGTH_SHORT).show();
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

                    // ВАЖНОЕ ИСПРАВЛЕНИЕ: Правильно определяем граффити
                    if ("graffiti".equals(doc.getType())) {
                        attachment.setType("graffiti"); // Устанавливаем правильный тип

                        // Создаем фото для отображения граффити из превью документа
                        Attachment.Photo graffitiPhoto = createGraffitiPhotoFromDocument(doc, attachmentData);
                        if (graffitiPhoto != null) {
                            attachment.setPhoto(graffitiPhoto);
                        }
                    }
                    break;

                case "audio":
                    Attachment.Audio audio = parseAudio(attachmentData);
                    attachment.setAudio(audio);
                    break;

                case "audio_message":
                    Attachment.Document audioMessage = parseAudioMessage(attachmentData);
                    attachment.setDoc(audioMessage);
                    attachment.setType("audio_message");
                    break;

                case "sticker":
                    Attachment.Photo sticker = parseSticker(attachmentData);
                    attachment.setPhoto(sticker);
                    break;

                default:
                    Attachment.Document otherDoc = parseOtherAttachment(attachmentData, type);
                    attachment.setDoc(otherDoc);
                    break;
            }

            attachments.add(attachment);
        }

        return attachments;
    }

    private Attachment.Photo createGraffitiPhotoFromDocument(Attachment.Document graffitiDoc, JSONObject attachmentData) {
        if (graffitiDoc == null) return null;

        Attachment.Photo photo = new Attachment.Photo();
        List<Attachment.Size> sizes = new ArrayList<>();

        try {
            // Пытаемся получить превью граффити из документа
            if (attachmentData.has("preview")) {
                JSONObject preview = attachmentData.getJSONObject("preview");
                if (preview.has("photo")) {
                    JSONObject photoPreview = preview.getJSONObject("photo");
                    JSONArray sizesArray = photoPreview.getJSONArray("sizes");

                    for (int i = 0; i < sizesArray.length(); i++) {
                        JSONObject sizeObj = sizesArray.getJSONObject(i);
                        Attachment.Size size = new Attachment.Size();
                        size.setUrl(sizeObj.getString("src"));
                        size.setWidth(sizeObj.getInt("width"));
                        size.setHeight(sizeObj.getInt("height"));
                        size.setType(getSizeType(sizeObj.getInt("width")));
                        sizes.add(size);
                    }
                }
            }

            // Если превью не найдено, но есть URL документа, используем его
            if (sizes.isEmpty() && graffitiDoc.getUrl() != null && !graffitiDoc.getUrl().isEmpty()) {
                Attachment.Size size = new Attachment.Size();
                size.setUrl(graffitiDoc.getUrl());
                size.setWidth(256); // стандартный размер для граффити
                size.setHeight(256);
                size.setType("x");
                sizes.add(size);
            }

            // Если все еще нет URL, создаем placeholder
            if (sizes.isEmpty()) {
                Attachment.Size size = new Attachment.Size();
                size.setUrl(""); // пустой URL для placeholder
                size.setWidth(256);
                size.setHeight(256);
                size.setType("x");
                sizes.add(size);
            }

            photo.setSizes(sizes);
            return photo;

        } catch (JSONException e) {
            Log.e(TAG, "Error creating graffiti photo from document", e);

            // Fallback: создаем базовое фото для граффити
            Attachment.Size size = new Attachment.Size();
            size.setUrl(graffitiDoc != null ? graffitiDoc.getUrl() : "");
            size.setWidth(256);
            size.setHeight(256);
            size.setType("x");
            sizes.add(size);
            photo.setSizes(sizes);
            return photo;
        }
    }

    // Вспомогательный метод для определения типа размера
    private String getSizeType(int width) {
        if (width >= 1280) return "w";
        if (width >= 807) return "z";
        if (width >= 604) return "y";
        if (width >= 510) return "x";
        if (width >= 320) return "m";
        if (width >= 160) return "s";
        return "o";
    }

    private Attachment.Photo createGraffitiPreview(Attachment.Document graffitiDoc) {
        if (graffitiDoc == null) {
            return null;
        }

        Attachment.Photo photo = new Attachment.Photo();
        List<Attachment.Size> sizes = new ArrayList<>();
        Attachment.Size size = new Attachment.Size();

        // Для граффити используем превью из документа
        // VK API обычно предоставляет превью для граффити в поле "preview"
        try {
            // Пытаемся получить URL превью граффити
            // В реальном API граффити может иметь превью в разных размерах
            size.setUrl(graffitiDoc.getUrl()); // Используем основной URL документа как fallback
            size.setWidth(200); // Стандартная ширина для граффити
            size.setHeight(200); // Стандартная высота для граффити
            size.setType("x");

            sizes.add(size);
            photo.setSizes(sizes);

            return photo;
        } catch (Exception e) {
            Log.e(TAG, "Error creating graffiti preview", e);
            return null;
        }
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
        doc.setOwnerId(String.valueOf(docObj.getInt("owner_id")));
        doc.setTitle(docObj.getString("title"));
        doc.setExt(docObj.getString("ext"));
        doc.setUrl(docObj.getString("url"));
        doc.setSize(docObj.getInt("size"));
        doc.setType(docObj.optString("type", "document"));

        // Сохраняем превью для граффити
        if ("graffiti".equals(doc.getType()) && docObj.has("preview")) {
            try {
                JSONObject preview = docObj.getJSONObject("preview");
                // Информация о превью будет использована в createGraffitiPhotoFromDocument
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing graffiti preview", e);
            }
        }

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

            // Учитываем граффити как отдельный тип
            if ("doc".equals(type) && attachment.getDoc() != null && "graffiti".equals(attachment.getDoc().getType())) {
                type = "graffiti";
            }

            Integer count = typeCounts.get(type);
            if (count == null) {
                typeCounts.put(type, 1);
            } else {
                typeCounts.put(type, count + 1);
            }
        }

        List<String> parts = new ArrayList<>();

        // Граффити (добавляем в начало)
        Integer graffitiCount = typeCounts.get("graffiti");
        if (graffitiCount != null && graffitiCount > 0) {
            parts.add("🎨 Граффити");
        }

        // Фото (исключаем граффити)
        Integer photoCount = typeCounts.get("photo");
        if (photoCount != null && photoCount > 0) {
            parts.add("📷 " + photoCount);
        }

        // Документы (исключаем граффити)
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
                    !type.equals("audio_message") && !type.equals("sticker") && !type.equals("graffiti")) {
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

        if (isTestMode) {
            // В тестовом режиме создаем тестовое сообщение
            Message testMessage = new Message(
                    "current_user",
                    "Вы",
                    text,
                    System.currentTimeMillis(),
                    null
            );
            testMessage.setOutgoing(true);
            testMessage.setReadStatus(Message.READ_STATUS_SENT);

            adapter.addMessage(testMessage);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            Toast.makeText(this, "Демо-режим: сообщение отправлен", Toast.LENGTH_SHORT).show();
            return;
        }

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
                            Toast.makeText(DialogActivity.this, "Ошибка отправки сообщения", Toast.LENGTH_SHORT).show();

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
                                Toast.makeText(DialogActivity.this, "Ошибка отправки: " + response.code(), Toast.LENGTH_SHORT).show();

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
            return handleMenuAction(item.getItemId());
        });

        // Показываем меню
        popupMenu.show();
    }

    private boolean handleMenuAction(int menuItemId) {
        switch (menuItemId) {
            case R.id.menu_search:
                showSearchDialog();
                return true;
            case R.id.menu_clear:
                showClearHistoryDialog();
                return true;
            case R.id.menu_info:
                showDialogInfo();
                return true;
            case R.id.menu_manage_stickers:
                openStickerPackManager();
                return true;
            default:
                return false;
        }
    }

    private void showDialogInfo() {
        int messageCount = messageList.size();
        String info = "Сообщений в диалоге: " + messageCount +
                "\nСобеседник: " + (userName != null ? userName : "Неизвестно") +
                "\nID: " + (userId != null ? userId : "Неизвестно") +
                "\nСтикерпаков: " + purchasedStickerPacks.size() +
                (isTestMode ? "\n\n⚠️ Демо-режим" : "");

        new AlertDialog.Builder(this)
                .setTitle("Информация о диалоге" + (isTestMode ? " (Демо)" : ""))
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showSearchDialog() {
        if (isTestMode) {
            Toast.makeText(this, "Демо-режим: поиск недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

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
                .setTitle("Очистка истории" + (isTestMode ? " (Демо)" : ""))
                .setMessage("Вы уверены, что хотите очистить историю переписки? Это действие нельзя отменить." +
                        (isTestMode ? "\n\nВ демо-режиме это очистит только локальные тестовые сообщения." : ""))
                .setPositiveButton("Очистить", (dialog, which) -> {
                    messageList.clear();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "История очищена" + (isTestMode ? " (демо)" : ""), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onResume() {
        super.onResume();

        // Обновляем статус при возвращении в активность
        setupUserStatus();

        // Регистрируем receiver для обновления стикеров и настроек
        IntentFilter stickerFilter = new IntentFilter("STICKER_PACKS_UPDATED");
        IntentFilter settingsFilter = new IntentFilter("SETTINGS_CHANGED");

        if (!isTestMode && peerId != null) {
            markMessagesAsRead();
        }

        // ИСПРАВЛЕНИЕ: Правильная регистрация BroadcastReceiver для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stickerUpdateReceiver, stickerFilter, RECEIVER_NOT_EXPORTED);
            registerReceiver(settingsChangeReceiver, settingsFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stickerUpdateReceiver, stickerFilter);
            registerReceiver(settingsChangeReceiver, settingsFilter);
        }

        // Регистрируем receiver для изменения фона
        IntentFilter backgroundFilter = new IntentFilter("CHAT_BACKGROUND_CHANGED");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(backgroundChangeReceiver, backgroundFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(backgroundChangeReceiver, backgroundFilter);
        }

        // Если есть трек для отправки, отправляем его
        if (currentAudioToShare != null) {
            sendAudioMessage(currentAudioToShare);
            currentAudioToShare = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Отписываемся от receiver
        try {
            unregisterReceiver(stickerUpdateReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering sticker receiver", e);
        }

        try {
            unregisterReceiver(backgroundChangeReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering background receiver", e);
        }

        try {
            unregisterReceiver(settingsChangeReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering settings receiver", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Очистка ресурсов если необходимо
    }

    // Метод для запуска активности
    public static void start(Context context, String userId, String userName, String peerId, boolean isSpecialUser) {
        Intent intent = new Intent(context, DialogActivity.class);
        intent.putExtra("userId", userId);
        intent.putExtra("userName", userName);
        intent.putExtra("peerId", peerId);
        intent.putExtra("isSpecialUser", isSpecialUser);
        context.startActivity(intent);
    }

    // Вложенный класс AudioSelectionBottomSheet
    public static class AudioSelectionBottomSheet extends BottomSheetDialogFragment {

        private AudioSelectionListener listener;

        public interface AudioSelectionListener {
            void onAudioSelected(Audio audio);
            void onShareToFriends(Audio audio);
        }

        public void setAudioSelectionListener(AudioSelectionListener listener) {
            this.listener = listener;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.bottom_sheet_audio_selection, container, false);
            setupViews(view);
            return view;
        }

        private void setupViews(View view) {
            // Кнопка "Мои аудиозаписи"
            View btnMyAudio = view.findViewById(R.id.btnMyAudio);
            if (btnMyAudio != null) {
                btnMyAudio.setOnClickListener(v -> {
                    // Запускаем активность с моими аудиозаписями
                    Intent intent = new Intent(getActivity(), AudioListFragment.class);
                    intent.putExtra("selection_mode", true);
                    getActivity().startActivityForResult(intent, REQUEST_CODE_SELECT_AUDIO);
                    dismiss();
                });
            }

            // Кнопка "Рекомендации"
            View btnRecommendations = view.findViewById(R.id.btnRecommendations);
            if (btnRecommendations != null) {
                btnRecommendations.setOnClickListener(v -> {
                    // Запускаем активность с рекомендациями
                    Intent intent = new Intent(getActivity(), AudioListFragment.class);
                    intent.putExtra("selection_mode", true);
                    getActivity().startActivityForResult(intent, REQUEST_CODE_SELECT_AUDIO);
                    dismiss();
                });
            }

            // Кнопка "Популярное"
            View btnPopular = view.findViewById(R.id.btnPopular);
            if (btnPopular != null) {
                btnPopular.setOnClickListener(v -> {
                    // Запускаем активность с популярной музыкой
                    Intent intent = new Intent(getActivity(), AudioListFragment.class);
                    intent.putExtra("selection_mode", true);
                    getActivity().startActivityForResult(intent, REQUEST_CODE_SELECT_AUDIO);
                    dismiss();
                });
            }

            // Кнопка "Поделиться с друзьями"
            View btnShareWithFriends = view.findViewById(R.id.btnShareWithFriends);
            if (btnShareWithFriends != null) {
                btnShareWithFriends.setOnClickListener(v -> {
                    if (listener != null) {
                        // Здесь можно передать какой-то дефолтный трек или показать выбор
                        listener.onShareToFriends(null);
                    }
                    dismiss();
                });
            }
        }
    }

    private void initInsets() {
        inputContainer = findViewById(R.id.inputContainer);
        navigationSpacer = findViewById(R.id.navigationSpacer);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Для Android 11+ используем WindowInsets API
            setupEdgeToEdge();
        } else {
            // Для старых версий используем стандартные отступы
            setupLegacyInsets();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void setupEdgeToEdge() {
        // Включаем отрисовку за системными барами
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Настраиваем прозрачные системные бары
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        // Устанавливаем цвета системных баров
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        // Обработка инсетов
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            // Получаем системные инсеты
            int systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int tappableElement = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom;

            // Используем максимальное значение для безопасного отступа
            int bottomInset = Math.max(systemBars, Math.max(ime, Math.max(navigationBars, tappableElement)));

            // Устанавливаем отступ для навигационной панели
            if (navigationSpacer != null) {
                ViewGroup.LayoutParams params = navigationSpacer.getLayoutParams();
                params.height = bottomInset;
                navigationSpacer.setLayoutParams(params);
            }

            // Настраиваем отступы для RecyclerView
            if (recyclerView != null) {
                recyclerView.setPadding(
                        recyclerView.getPaddingLeft(),
                        recyclerView.getPaddingTop(),
                        recyclerView.getPaddingRight(),
                        bottomInset + 16 // Добавляем дополнительный отступ
                );
            }

            // Настраиваем отступы для SwipeRefreshLayout
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setProgressViewOffset(
                        false,
                        swipeRefreshLayout.getProgressViewStartOffset(),
                        swipeRefreshLayout.getProgressViewEndOffset() + bottomInset
                );
            }

            return insets;
        });

        // Принудительно применяем инсеты
        ViewCompat.requestApplyInsets(findViewById(R.id.main_layout));
    }

    private void setupLegacyInsets() {
        // Для старых версий Android используем простой подход
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.color_send));
        }

        // Устанавливаем фиксированный отступ для навигационной панели
        if (navigationSpacer != null) {
            ViewGroup.LayoutParams params = navigationSpacer.getLayoutParams();
            params.height = getNavigationBarHeight();
            navigationSpacer.setLayoutParams(params);
        }
    }

    /**
     * Получаем высоту навигационной панели
     */
    private int getNavigationBarHeight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Resources resources = getResources();
            int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
            if (resourceId > 0) {
                return resources.getDimensionPixelSize(resourceId);
            }
        }
        return 0;
    }

    /**
     * Проверяем, есть ли навигационная панель
     */
    private boolean hasNavigationBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Resources resources = getResources();
            int resourceId = resources.getIdentifier("config_showNavigationBar", "bool", "android");
            if (resourceId > 0) {
                return resources.getBoolean(resourceId);
            }
        }
        return false;
    }

    // Метод для отметки сообщений как прочитанных
    private void markMessagesAsRead() {
        if (!SettingsFragment.isMarkAsReadEnabled(this)) {
            return;
        }

        String accessToken = TokenManager.getInstance(this).getToken();
        if (accessToken == null || peerId == null) {
            return;
        }

        String url = "https://api.vk.com/method/messages.markAsRead" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&peer_id=" + peerId;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to mark messages as read", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            Log.d(TAG, "Messages marked as read successfully");

                            // Обновляем статус сообщений в UI
                            runOnUiThread(() -> {
                                updateMessagesReadStatus();
                            });
                        } else if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            Log.e(TAG, "Error marking messages as read: " + error.toString());
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing mark as read response", e);
                    }
                } else {
                    Log.e(TAG, "Failed to mark messages as read, code: " + response.code());
                }
            }
        });
    }

    // Метод для обновления статуса прочтения в UI
    private void updateMessagesReadStatus() {
        for (Message message : messageList) {
            if (!message.isOutgoing() && message.getReadStatus() != Message.READ_STATUS_READ) {
                message.setReadStatus(Message.READ_STATUS_READ);
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    // НОВЫЙ МЕТОД: Отправка трека в текущий диалог
    private void sendAudioMessage(Audio audio) {
        if (isTestMode) {
            // В тестовом режиме создаем тестовое сообщение с аудио
            Message audioMessage = new Message(
                    "current_user",
                    "Вы",
                    "",
                    System.currentTimeMillis(),
                    null
            );
            audioMessage.setOutgoing(true);
            audioMessage.setReadStatus(Message.READ_STATUS_SENT);
            audioMessage.setPreviewText("🎵 " + (audio != null ? audio.getArtist() + " - " + audio.getTitle() : "Тестовый трек"));

            adapter.addMessage(audioMessage);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            Toast.makeText(this, "Демо-режим: трек отправлен", Toast.LENGTH_SHORT).show();
            return;
        }

        String accessToken = TokenManager.getInstance(this).getToken();
        if (accessToken != null && peerId != null) {
            // Создаем временное сообщение с треком
            Message audioMessage = new Message(userId, userName, "", System.currentTimeMillis(), null);
            audioMessage.setOutgoing(true);
            audioMessage.setReadStatus(Message.READ_STATUS_SENT);
            audioMessage.setPeerId(peerId);
            audioMessage.setPreviewText("🎵 " + audio.getArtist() + " - " + audio.getTitle());

            // Добавляем в список для мгновенного отображения
            adapter.addMessage(audioMessage);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);

            // Отправляем трек через API
            try {
                String url = "https://api.vk.com/method/messages.send" +
                        "?access_token=" + accessToken +
                        "&v=5.131" +
                        "&peer_id=" + peerId +
                        "&attachment=" + URLEncoder.encode("audio" + audio.getOwnerId() + "_" + audio.getAudioId(), "UTF-8") +
                        "&random_id=" + System.currentTimeMillis();

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e(TAG, "Failed to send audio", e);
                        runOnUiThread(() -> {
                            Toast.makeText(DialogActivity.this, "Ошибка отправки трека", Toast.LENGTH_SHORT).show();
                            messageList.remove(audioMessage);
                            adapter.notifyDataSetChanged();
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.isSuccessful()) {
                            String responseBody = response.body().string();
                            Log.d(TAG, "Audio sent successfully: " + responseBody);

                            runOnUiThread(() -> {
                                // Обновляем статус отправленного сообщения
                                int messageIndex = messageList.indexOf(audioMessage);
                                if (messageIndex != -1) {
                                    Message sentMessage = messageList.get(messageIndex);
                                    sentMessage.setReadStatus(Message.READ_STATUS_READ);

                                    // Добавляем вложение с аудио
                                    try {
                                        List<Attachment> attachments = new ArrayList<>();
                                        Attachment attachment = new Attachment();
                                        attachment.setType("audio");

                                        Attachment.Audio audioAttachment = new Attachment.Audio();
                                        audioAttachment.setArtist(audio.getArtist());
                                        audioAttachment.setTitle(audio.getTitle());
                                        audioAttachment.setDuration(audio.getDuration());
                                        audioAttachment.setUrl(audio.getUrl());

                                        attachment.setAudio(audioAttachment);
                                        attachments.add(attachment);

                                        sentMessage.setAttachments(attachments);
                                        adapter.notifyItemChanged(messageIndex);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error updating message with audio", e);
                                    }
                                }

                                Toast.makeText(DialogActivity.this, "Трек отправлен", Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            Log.e(TAG, "Failed to send audio, code: " + response.code());
                            runOnUiThread(() -> {
                                Toast.makeText(DialogActivity.this, "Ошибка отправки: " + response.code(), Toast.LENGTH_SHORT).show();
                                messageList.remove(audioMessage);
                                adapter.notifyDataSetChanged();
                            });
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error sending audio", e);
                Toast.makeText(this, "Ошибка отправки трека", Toast.LENGTH_SHORT).show();
                messageList.remove(audioMessage);
                adapter.notifyDataSetChanged();
            }
        } else {
            Toast.makeText(this, "Не удалось отправить трек", Toast.LENGTH_SHORT).show();
        }
    }

    // Реализация интерфейса ShareAudioListener
    @Override
    public void onShareToFriend(AudioListFragment.Friend friend, Audio audio) {
        if (friend != null && audio != null) {
            // Открываем диалог с выбранным другом и отправляем туда трек
            DialogActivity.start(this, friend.getId(), friend.getName(), friend.getId(), false);

            // Сохраняем трек для отправки после открытия диалога
            this.currentAudioToShare = audio;

            Toast.makeText(this, "Открывается диалог с " + friend.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    // Метод для показа BottomSheet с друзьями для отправки трека
    public void showShareAudioBottomSheet(Audio audio, List<AudioListFragment.Friend> friends) {
        if (audio == null || friends == null || friends.isEmpty()) {
            Toast.makeText(this, "Нет друзей для отправки", Toast.LENGTH_SHORT).show();
            return;
        }

        ShareToFriendsBottomSheet bottomSheet = ShareToFriendsBottomSheet.newInstance(audio, friends);
        bottomSheet.setShareAudioListener(this);
        bottomSheet.show(getSupportFragmentManager(), "ShareAudioBottomSheet");
    }

    // Метод для показа BottomSheet с выбором трека
    private void showAudioSelectionBottomSheet() {
        AudioSelectionBottomSheet bottomSheet = new AudioSelectionBottomSheet();
        bottomSheet.setAudioSelectionListener(new AudioSelectionBottomSheet.AudioSelectionListener() {
            @Override
            public void onAudioSelected(Audio audio) {
                if (audio != null) {
                    // Отправляем выбранный трек в текущий диалог
                    shareAudioToDialog(audio);
                }
            }

            @Override
            public void onShareToFriends(Audio audio) {
                if (audio != null) {
                    // Показываем BottomSheet с друзьями для отправки
                    loadFriendsAndShowShareDialog(audio);
                }
            }
        });
        bottomSheet.show(getSupportFragmentManager(), "AudioSelectionBottomSheet");
    }

    // Метод для загрузки списка друзей и показа диалога отправки
    private void loadFriendsAndShowShareDialog(Audio audio) {
        if (isTestMode) {
            Toast.makeText(this, "Демо-режим: отправка друзьям недоступна", Toast.LENGTH_SHORT).show();
            return;
        }

        // Показываем индикатор загрузки
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Загрузка списка друзей...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String accessToken = TokenManager.getInstance(this).getToken();
        if (accessToken != null) {
            String url = "https://api.vk.com/method/friends.get" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&fields=photo_100,first_name,last_name" +
                    "&count=100";

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(DialogActivity.this, "Ошибка загрузки друзей", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    runOnUiThread(() -> progressDialog.dismiss());

                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("response")) {
                                JSONObject responseObj = json.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");
                                List<AudioListFragment.Friend> friends = new ArrayList<>();

                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject friendObj = items.getJSONObject(i);
                                    String id = String.valueOf(friendObj.getInt("id"));
                                    String firstName = friendObj.getString("first_name");
                                    String lastName = friendObj.getString("last_name");
                                    String photoUrl = friendObj.optString("photo_100", "");

                                    AudioListFragment.Friend friend = new AudioListFragment.Friend(id, firstName + " " + lastName, photoUrl);
                                    friends.add(friend);
                                }

                                runOnUiThread(() -> {
                                    showShareAudioBottomSheet(audio, friends);
                                });
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing friends", e);
                            runOnUiThread(() -> {
                                Toast.makeText(DialogActivity.this, "Ошибка обработки списка друзей", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(DialogActivity.this, "Ошибка загрузки друзей", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        } else {
            progressDialog.dismiss();
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupTextWatcher() {
        textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                boolean newHasText = s.toString().trim().length() > 0;
                if (newHasText != hasText) {
                    hasText = newHasText;
                    updateSendButtonIcon();
                }
            }
        };

        editTextMessage.addTextChangedListener(textWatcher);

        // Инициализируем иконку при старте
        updateSendButtonIcon();
    }

    // Метод для обновления иконки кнопки отправки
    private void updateSendButtonIcon() {
        if (buttonSend == null) return;

        if (hasText) {
            // Если есть текст - показываем иконку отправки сообщения
            buttonSend.setImageResource(R.drawable.ic_send_tap);
        } else {
            // Если текста нет - показываем иконку записи голосового сообщения
            buttonSend.setImageResource(R.drawable.ic_send);
        }

        // Добавляем анимацию при смене иконки
        buttonSend.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(100)
                .withEndAction(() -> buttonSend.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start())
                .start();
    }

    private void setupClickListeners() {
        if (buttonSend != null) {
            buttonSend.setOnClickListener(v -> {
                if (hasText) {
                    // Если есть текст - отправляем сообщение
                    String messageText = editTextMessage.getText().toString().trim();
                    if (!messageText.isEmpty()) {
                        sendMessage(messageText);
                        editTextMessage.setText("");
                    }
                } else {
                    // Если текста нет - запускаем запись голосового сообщения
                    startVoiceRecording();
                }
            });
        }

        // Длинное нажатие на кнопку отправки (даже когда есть текст) для записи голосового
        if (buttonSend != null) {
            buttonSend.setOnLongClickListener(v -> {
                startVoiceRecording();
                return true;
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

        // Обработчик для кнопки прикрепления аудио
        if (btnAttachAudio != null) {
            btnAttachAudio.setOnClickListener(v -> {
                if (isTestMode) {
                    Toast.makeText(this, "Демо-режим: отправка аудио", Toast.LENGTH_SHORT).show();

                    // Создаем тестовое сообщение с аудио
                    Message audioMessage = new Message(
                            "current_user",
                            "Вы",
                            "",
                            System.currentTimeMillis(),
                            null
                    );
                    audioMessage.setOutgoing(true);
                    audioMessage.setReadStatus(Message.READ_STATUS_SENT);
                    audioMessage.setPreviewText("🎵 Тестовый трек - Демо версия");

                    adapter.addMessage(audioMessage);
                    recyclerView.scrollToPosition(adapter.getItemCount() - 1);
                } else {
                    showAudioSelectionBottomSheet();
                }
            });
        }

        // Обработчик для кнопки прикрепления фото
        if (btnAttachPhoto != null) {
            btnAttachPhoto.setOnClickListener(v -> {
                showPhotoSelectionDialog();
            });
        }
    }

    private void startVoiceRecording() {
        if (isTestMode) {
            Toast.makeText(this, "Демо-режим: запись голосового сообщения", Toast.LENGTH_SHORT).show();

            // Создаем тестовое голосовое сообщение
            Message voiceMessage = new Message(
                    "current_user",
                    "Вы",
                    "",
                    System.currentTimeMillis(),
                    null
            );
            voiceMessage.setOutgoing(true);
            voiceMessage.setReadStatus(Message.READ_STATUS_SENT);
            voiceMessage.setPreviewText("🎤 Голосовое сообщение");

            adapter.addMessage(voiceMessage);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
        } else {
            // TODO: Реализовать запись голосового сообщения
            //Toast.makeText(this, "Запись голосового сообщения...", Toast.LENGTH_SHORT).show();

            // Временная заглушка - можно интегрировать с AudioRecord или MediaRecorder
            showVoiceRecordingDialog();
        }
    }

    // Диалог записи голосового сообщения (временная реализация)
    private void showVoiceRecordingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Упс, ошибка")
                .setMessage("Сначала введите текст")
                .setPositiveButton("OK", null)
                .show();
    }


    // МЕТОД: Показать диалог выбора фото
    private void showPhotoSelectionDialog() {
        if (isTestMode) {
            Toast.makeText(this, "Демо-режим: отправка фото", Toast.LENGTH_SHORT).show();

            // Создаем тестовое сообщение с фото
            Message photoMessage = new Message(
                    "current_user",
                    "Вы",
                    "",
                    System.currentTimeMillis(),
                    null
            );
            photoMessage.setOutgoing(true);
            photoMessage.setReadStatus(Message.READ_STATUS_SENT);
            photoMessage.setPreviewText("📷 Тестовое фото");

            adapter.addMessage(photoMessage);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выберите фото");
        builder.setItems(new String[]{"Сделать фото", "Выбрать из галереи"}, (dialog, which) -> {
            switch (which) {
                case 0:
                    takePhoto();
                    break;
                case 1:
                    selectPhotoFromGallery();
                    break;
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    // МЕТОД: Сделать фото
    private void takePhoto() {
        try {
            // Создаем файл для сохранения фото
            File photoFile = createImageFile();
            if (photoFile != null) {
                Uri photoUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".provider",
                        photoFile);
                currentPhotoUri = photoUri;

                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

                // Проверяем, есть ли приложение камеры
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(takePictureIntent, REQUEST_CODE_TAKE_PHOTO);
                } else {
                    Toast.makeText(this, "Приложение камеры не найдено", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error taking photo", e);
            Toast.makeText(this, "Ошибка при создании фото", Toast.LENGTH_SHORT).show();
        }
    }

    // МЕТОД: Создать файл для фото
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        return image;
    }

    // МЕТОД: Выбрать фото из галереи
    private void selectPhotoFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Выберите фото"), REQUEST_CODE_SELECT_PHOTO);
    }

    // МЕТОД: Обработка выбранного фото
    private void handleSelectedPhoto(Uri photoUri) {
        if (photoUri == null) {
            Toast.makeText(this, "Не удалось выбрать фото", Toast.LENGTH_SHORT).show();
            return;
        }

        // Создаем временное сообщение с фото
        Message photoMessage = new Message(userId, userName, "", System.currentTimeMillis(), null);
        photoMessage.setOutgoing(true);
        photoMessage.setReadStatus(Message.READ_STATUS_SENT);
        photoMessage.setPeerId(peerId);
        photoMessage.setPreviewText("📷 Фото");

        // Добавляем в список для мгновенного отображения
        adapter.addMessage(photoMessage);
        recyclerView.scrollToPosition(adapter.getItemCount() - 1);

        // Загружаем и отправляем фото
        uploadAndSendPhoto(photoUri, photoMessage);
    }

    // МЕТОД: Загрузка и отправка фото
    private void uploadAndSendPhoto(Uri photoUri, Message tempMessage) {
        String accessToken = TokenManager.getInstance(this).getToken();
        if (accessToken == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            messageList.remove(tempMessage);
            adapter.notifyDataSetChanged();
            return;
        }

        // Сначала получаем URL для загрузки фото
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
                    Toast.makeText(DialogActivity.this, "Ошибка получения сервера загрузки", Toast.LENGTH_SHORT).show();
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

                            // Загружаем фото на сервер
                            uploadPhotoToServer(photoUri, uploadUrl, tempMessage);
                        } else {
                            throw new JSONException("No upload server in response");
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing upload server response", e);
                        runOnUiThread(() -> {
                            Toast.makeText(DialogActivity.this, "Ошибка обработки ответа сервера", Toast.LENGTH_SHORT).show();
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivity.this, "Ошибка получения сервера загрузки: " + response.code(), Toast.LENGTH_SHORT).show();
                        messageList.remove(tempMessage);
                        adapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }

    // МЕТОД: Загрузка фото на сервер
    private void uploadPhotoToServer(Uri photoUri, String uploadUrl, Message tempMessage) {
        OkHttpClient client = new OkHttpClient();

        try {
            InputStream inputStream = getContentResolver().openInputStream(photoUri);
            if (inputStream == null) {
                throw new IOException("Cannot open input stream from photo URI");
            }

            byte[] imageBytes = readInputStream(inputStream);

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("photo", "photo.jpg",
                            RequestBody.create(imageBytes, MediaType.parse("image/jpeg")))
                    .build();

            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to upload photo", e);
                    runOnUiThread(() -> {
                        Toast.makeText(DialogActivity.this, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show();
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
                            saveMessagesPhoto(responseBody, tempMessage);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing upload response", e);
                            runOnUiThread(() -> {
                                messageList.remove(tempMessage);
                                adapter.notifyDataSetChanged();
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(DialogActivity.this, "Ошибка загрузки фото: " + response.code(), Toast.LENGTH_SHORT).show();
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                        });
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error reading photo file", e);
            runOnUiThread(() -> {
                Toast.makeText(DialogActivity.this, "Ошибка чтения фото", Toast.LENGTH_SHORT).show();
                messageList.remove(tempMessage);
                adapter.notifyDataSetChanged();
            });
        }
    }

    // МЕТОД: Чтение InputStream в byte array
    private byte[] readInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    // МЕТОД: Сохранение фото в VK
    private void saveMessagesPhoto(String uploadResponse, Message tempMessage) {
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
                        Toast.makeText(DialogActivity.this, "Ошибка сохранения фото", Toast.LENGTH_SHORT).show();
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
                                sendPhotoMessage(photo, tempMessage);
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
                            Toast.makeText(DialogActivity.this, "Ошибка сохранения фото: " + response.code(), Toast.LENGTH_SHORT).show();
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

    // МЕТОД: Отправка сообщения с фото
    private void sendPhotoMessage(JSONObject photo, Message tempMessage) {
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
                        Toast.makeText(DialogActivity.this, "Ошибка отправки фото", Toast.LENGTH_SHORT).show();
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

                                // Добавляем информацию о фото в сообщение
                                try {
                                    List<Attachment> attachments = new ArrayList<>();
                                    Attachment attachmentObj = new Attachment();
                                    attachmentObj.setType("photo");

                                    Attachment.Photo messagePhoto = new Attachment.Photo();
                                    List<Attachment.Size> sizes = new ArrayList<>();
                                    Attachment.Size size = new Attachment.Size();

                                    // Получаем URL фото из ответа
                                    JSONArray sizesArray = photo.getJSONArray("sizes");
                                    for (int i = 0; i < sizesArray.length(); i++) {
                                        JSONObject sizeObj = sizesArray.getJSONObject(i);
                                        if ("x".equals(sizeObj.getString("type"))) {
                                            size.setUrl(sizeObj.getString("url"));
                                            size.setWidth(sizeObj.getInt("width"));
                                            size.setHeight(sizeObj.getInt("height"));
                                            size.setType("x");
                                            break;
                                        }
                                    }

                                    sizes.add(size);
                                    messagePhoto.setSizes(sizes);
                                    attachmentObj.setPhoto(messagePhoto);
                                    attachments.add(attachmentObj);

                                    sentMessage.setAttachments(attachments);
                                    adapter.notifyItemChanged(messageIndex);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error updating message with photo", e);
                                }
                            }

                            Toast.makeText(DialogActivity.this, "Фото отправлено", Toast.LENGTH_SHORT).show();
                        } else {
                            messageList.remove(tempMessage);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(DialogActivity.this, "Ошибка отправки: " + response.code(), Toast.LENGTH_SHORT).show();
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

    // НОВЫЙ метод для отправки трека
    public void shareAudioToDialog(Audio audio) {
        if (audio == null) {
            Toast.makeText(this, "Ошибка: неверный трек", Toast.LENGTH_SHORT).show();
            return;
        }

        currentAudioToShare = audio;
        sendAudioMessage(audio);
    }

    private boolean isStickerPurchased(Sticker sticker) {
        return false;
    }
}