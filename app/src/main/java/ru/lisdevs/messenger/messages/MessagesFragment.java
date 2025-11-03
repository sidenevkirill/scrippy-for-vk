package ru.lisdevs.messenger.messages;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.BaseActivity;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.about.AboutFragment;
import ru.lisdevs.messenger.account.ProfileFragment;
import ru.lisdevs.messenger.album.PhotoTabsFragment;
import ru.lisdevs.messenger.db.AutoResponseDBHelper;
import ru.lisdevs.messenger.dialog.DialogActivity;
import ru.lisdevs.messenger.documents.DocumentsFragment;
import ru.lisdevs.messenger.groups.GroupsTabsFragment;
import ru.lisdevs.messenger.chat.ChatActivity;
import ru.lisdevs.messenger.messages.stickers.StickerPackManagerActivity;
import ru.lisdevs.messenger.music.RecommendationFragment;
import ru.lisdevs.messenger.newsfeed.NewsFeedFragment;
import ru.lisdevs.messenger.settings.SettingsFragment;
import ru.lisdevs.messenger.utils.CircleTransform;
import ru.lisdevs.messenger.utils.TokenManager;
import ru.lisdevs.messenger.video.VideoFragment;

import android.widget.Button;
import android.widget.CheckBox;


public class MessagesFragment extends Fragment {

    private static final String TAG = "MessagesFragment";
    private RecyclerView recyclerView;
    private MessagesAdapter adapter;
    private List<Dialog> dialogList = new ArrayList<>();
    private Set<String> specialUsers = new HashSet<>();
    private OkHttpClient httpClient = new OkHttpClient();
    private boolean isSpecialUsersLoaded = false;
    private TextView dialogCountText;

    private TextView profileNameTextView;
    private ImageView profileAvatar;
    private String userId;
    private ImageView specialIcon;
    private OkHttpClient client = new OkHttpClient();
    private String userFirstName = "";
    private String userLastName = "";
    private boolean isPremiumUser = false;

    // Для уведомлений
    private NotificationManager notificationManager;
    private static final String CHANNEL_ID = "messages_channel";
    private static final int NOTIFICATION_ID = 1;
    private Handler messageHandler;
    private Runnable messageChecker;
    private static final long CHECK_INTERVAL = 10000; // 10 секунд
    private long lastMessageTime = 0;

    // Для хранения ID уже показанных уведомлений
    private Set<String> shownNotificationIds = new HashSet<>();

    // Для автоответов
    private AutoResponseDBHelper autoResponseDBHelper;
    //private ImageView fabAutoResponse;

    // Для создания группового чата
    private FloatingActionButton btnCreateGroupChat;
    private List<Friend> friendList = new ArrayList<>();

    // ID закрепленных чатов (Поддержка и Авто-бот)
    private Set<String> pinnedChatIds = new HashSet<>(Arrays.asList("-71746274"));

    // Для SwipeRefresh и прогресса
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar horizontalProgressBar;
    private TextView loadingText;

    // BroadcastReceiver для изменений настроек уведомлений
    private BroadcastReceiver notificationsStateReceiver;

    // Флаг для тестового режима
    private boolean isTestMode = false;

    // Для архивации чатов
    private Set<String> archivedChats = new HashSet<>();
    private static final String PREF_ARCHIVED_CHATS = "archived_chats";
    private boolean showArchivedChats = false;
    private ImageView toggleArchiveButton;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Проверяем, используем ли мы тестовые данные
        checkTestMode();

        // Загружаем архивные чаты
        loadArchivedChats();

        // Инициализация базы данных автоответов
        autoResponseDBHelper = new AutoResponseDBHelper(requireContext());

        // Инициализация менеджера уведомлений
        notificationManager = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        // Создаем Handler для периодической проверки сообщений
        messageHandler = new Handler();

        // Инициализация BroadcastReceiver для уведомлений
        initNotificationsReceiver();
    }

    // Метод для проверки тестового режима
    private void checkTestMode() {
        Context context = getSafeContext();
        if (context == null) return;

        String token = TokenManager.getInstance(context).getToken();
        // Проверяем, является ли токен тестовым
        if (token == null || token.contains("test") || token.equals("demo") ||
                token.length() < 10 || token.equals("000000")) {
            isTestMode = true;
            Log.d(TAG, "Тестовый режим активирован");
        } else {
            isTestMode = false;
        }
    }

    @SuppressLint("NewApi")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_messages, container, false);

        // Инициализация SwipeRefreshLayout и прогресса
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        horizontalProgressBar = view.findViewById(R.id.horizontalProgressBar);
        loadingText = view.findViewById(R.id.loadingText);
        btnCreateGroupChat = view.findViewById(R.id.btnCreateGroupChat);

        recyclerView = view.findViewById(R.id.recyclerViewMessages);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        dialogCountText = view.findViewById(R.id.count);
        updateDialogCountText(0, 0);

        profileNameTextView = view.findViewById(R.id.toolbar_title);
        profileAvatar = view.findViewById(R.id.profile_avatar);
        specialIcon = view.findViewById(R.id.special_icon);
        profileAvatar.setOnClickListener(v -> showProfileBottomSheet());

        // Кнопка для быстрого доступа к автоответам
        //fabAutoResponse = view.findViewById(R.id.fabAutoResponse);
        //fabAutoResponse.setOnClickListener(v -> openFriendsBottomSheet());

        checkTestChatMode();

        if (isTestMode) {
            btnCreateGroupChat.setVisibility(View.GONE);
        } else {
            btnCreateGroupChat.setOnClickListener(v -> openFriendsBottomSheet());
        }

        // Кнопка создания группового чата
        //btnCreateGroupChat = view.findViewById(R.id.btnCreateGroupChat);
        //btnCreateGroupChat.setOnClickListener(v -> openFriendsBottomSheet());

        // Кнопка переключения между активными и архивными чатами
        toggleArchiveButton = view.findViewById(R.id.toggleArchiveButton);
        toggleArchiveButton.setOnClickListener(v -> toggleArchivedChats());

        // Скрываем FAB по умолчанию, показываем только когда есть автоответы
        //fabAutoResponse.setVisibility(View.GONE);

        // ВАЖНО: Получаем userId из TokenManager
        Context context = getSafeContext();
        if (context != null) {
            userId = TokenManager.getInstance(context).getUserId();
        }

        if (userId != null) {
            loadUserProfile(userId);
            checkSpecialUser(userId);
        } else {
            profileNameTextView.setText("Гость");
        }

        // Настройка SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Показываем горизонтальный прогресс вместо круглого
            showHorizontalProgress();
            refreshData();
        });

        // Скрываем крутящийся индикатор SwipeRefreshLayout - используем свой прогресс
        swipeRefreshLayout.setColorSchemeColors(Color.TRANSPARENT);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.TRANSPARENT);

        // ИНИЦИАЛИЗАЦИЯ АДАПТЕРА
        adapter = new MessagesAdapter(dialogList, new MessagesAdapter.SpecialUserChecker() {
            @Override
            public boolean isSpecialUser(String userId) {
                return specialUsers.contains(userId);
            }

            @Override
            public boolean isArchived(String userId) {
                return archivedChats.contains(userId);
            }
        }, requireContext(), userId);

        adapter.setOnItemClickListener(new MessagesAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Dialog dialog) {
                openDialogActivity(dialog);
            }
        });

        // Добавляем обработчик долгого нажатия для архивации/удаления чатов
        adapter.setOnItemLongClickListener(new MessagesAdapter.OnItemLongClickListener() {
            @Override
            public void onItemLongClick(Dialog dialog) {
                showArchiveDeleteDialog(dialog);
            }
        });

        recyclerView.setAdapter(adapter);

        loadSpecialUsers();

        // Если тестовый режим - показываем тестовые данные
        if (isTestMode) {
            showTestData();
        } else {
            // Иначе загружаем реальные данные
            Context safeContext = getSafeContext();
            if (safeContext != null) {
                String accessToken = TokenManager.getInstance(safeContext).getToken();
                if (accessToken != null) {
                    fetchDialogs(accessToken);
                    // Запускаем периодическую проверку новых сообщений только если уведомления включены
                    if (areNotificationsEnabled()) {
                        startMessageChecking();
                    }
                } else {
                    Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_SHORT).show();
                }
            }
        }

        return view;
    }

    /**
     * Метод для открытия чата с авто-ботом
     */
    private void openAutoBotChat() {
        if (!isAdded()) return;

        String botUserId = "-999999999"; // Специальный ID для бота
        String botUserName = "Авто-бот";
        String botPeerId = "-999999999";

        Log.d(TAG, "Opening auto-bot chat - UserId: " + botUserId +
                ", UserName: " + botUserName +
                ", PeerId: " + botPeerId);

        // Создаем интент для открытия ChatActivity
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra("chatName", botUserName);
        intent.putExtra("avatarRes", R.drawable.accoun_oval); // Аватарка бота
        intent.putExtra("is_auto_bot", true); // Помечаем как чат с авто-ботом

        startActivity(intent);
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    /**
     * Метод для создания искусственного диалога с авто-ботом
     */
    private Dialog createAutoBotDialog() {
        Dialog autoBotDialog = new Dialog(
                "-999999999", // Специальный ID для бота
                "Авто-бот",
                "Привет! Я бот с готовыми ответами. Задайте мне вопрос!",
                System.currentTimeMillis(), // Текущее время
                "-999999999", // peer_id
                "https://via.placeholder.com/100/4CAF50/FFFFFF?text=AI" // Аватарка бота
        );
        autoBotDialog.setReadStatus(Dialog.READ_STATUS_INCOMING);
        autoBotDialog.setOutgoing(false);
        autoBotDialog.setGroupChat(false);
        autoBotDialog.setChatTitle("Авто-бот");
        autoBotDialog.setUnreadCount(0);

        return autoBotDialog;
    }

    // Метод для переключения между активными и архивными чатами
    private void toggleArchivedChats() {
        if (!isAdded()) return;

        showArchivedChats = !showArchivedChats;

        // Обновляем иконку кнопки
        if (showArchivedChats) {
            toggleArchiveButton.setImageResource(R.drawable.archive_arrow_up_outline); // Иконка "вернуть из архива"
            // Показываем прогресс и обновляем данные при входе в архив
            showHorizontalProgress();
            loadArchivedChatsWithRefresh();
        } else {
            toggleArchiveButton.setImageResource(R.drawable.ic_archive); // Иконка архива
            refreshData();
        }
    }

    private void checkTestChatMode() {
        String token = TokenManager.getInstance(requireContext()).getToken();
        // Проверяем, является ли токен тестовым
        if (token == null || token.contains("test") || token.equals("demo") ||
                token.length() < 10 || token.equals("000000") || token.contains("demo_token")) {
            isTestMode = true;
            Log.d("MessagesFragment", "Тестовый режим активирован");
        } else {
            isTestMode = false;
        }
    }

    // Метод для загрузки архивных чатов с обновлением данных
    private void loadArchivedChatsWithRefresh() {
        if (!isAdded()) return;

        if (isTestMode) {
            // В тестовом режиме показываем тестовые архивные чаты
            List<Dialog> archivedDialogs = createTestArchivedDialogs();
            adapter.setDialogs(archivedDialogs);
            updateDialogCountText(archivedDialogs.size(), 0);
            hideHorizontalProgress();
            return;
        }

        // В реальном режиме сначала обновляем данные, потом показываем архивные
        Context context = getSafeContext();
        if (context != null) {
            String accessToken = TokenManager.getInstance(context).getToken();
            if (accessToken != null) {
                fetchDialogsForArchive(accessToken);
            } else {
                hideHorizontalProgress();
                Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Метод для загрузки диалогов специально для архивного режима
    private void fetchDialogsForArchive(String accessToken) {
        String url = "https://api.vk.com/method/messages.getConversations" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&count=200" + // Увеличиваем количество для получения всех чатов
                "&extended=1" +
                "&fields=photo_100";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isAdded()) return;

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(),
                                "Ошибка при получении диалогов: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        updateDialogCountText(0, 0);
                        hideHorizontalProgress();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");

                            int totalCount = responseObj.optInt("count", items.length());
                            int unreadCount = responseObj.optInt("unread_count", 0);

                            JSONArray profiles = responseObj.optJSONArray("profiles");
                            Map<String, String> userNames = parseUserNames(profiles);
                            Map<String, String> userAvatars = parseUserAvatars(profiles);
                            Map<String, String> chatSettings = parseChatSettings(items);

                            String currentUserId = MessagesFragment.this.userId;

                            List<Dialog> allDialogs = new ArrayList<>();
                            List<Dialog> pinnedDialogs = new ArrayList<>();

                            boolean hasSupportChat = false;
                            boolean hasAutoBot = false;

                            for (int i = 0; i < items.length(); i++) {
                                JSONObject conversationObj = items.getJSONObject(i);
                                JSONObject lastMessage = conversationObj.getJSONObject("last_message");

                                JSONObject conversation = conversationObj.getJSONObject("conversation");
                                JSONObject peer = conversation.getJSONObject("peer");
                                String peerId = peer.optString("id");
                                String peerType = peer.optString("type");

                                String text = lastMessage.optString("text");
                                long date = lastMessage.optLong("date") * 1000;

                                boolean isOut = lastMessage.optInt("out") == 1;
                                int readState = lastMessage.optInt("read_state");

                                int readStatus;
                                if (isOut) {
                                    if (readState == 1) {
                                        readStatus = Dialog.READ_STATUS_READ;
                                    } else {
                                        readStatus = Dialog.READ_STATUS_SENT;
                                    }
                                } else {
                                    readStatus = Dialog.READ_STATUS_INCOMING;
                                }

                                DialogInfo dialogInfo = determineDialogUser(peerId, peerType, currentUserId, userNames, conversation, chatSettings);
                                String avatarUrl = userAvatars.get(dialogInfo.userId);

                                Dialog dialog = new Dialog(
                                        dialogInfo.userId,
                                        dialogInfo.userName,
                                        text,
                                        date,
                                        peerId,
                                        avatarUrl
                                );
                                dialog.setReadStatus(readStatus);
                                dialog.setOutgoing(isOut);
                                dialog.setGroupChat("chat".equals(peerType));
                                dialog.setChatTitle(dialogInfo.chatTitle);

                                if (conversation.has("unread_count")) {
                                    dialog.setUnreadCount(conversation.optInt("unread_count"));
                                }

                                // Проверяем, является ли чат архивным
                                if (archivedChats.contains(dialogInfo.userId)) {
                                    dialog.setArchived(true);
                                }

                                // Проверяем, является ли это закрепленный чат (Поддержка или Авто-бот)
                                if (pinnedChatIds.contains(dialogInfo.userId)) {
                                    if ("-71746274".equals(dialogInfo.userId)) {
                                        hasSupportChat = true;
                                        Dialog supportDialog = new Dialog(
                                                dialogInfo.userId,
                                                "Поддержка",
                                                text.isEmpty() ? "Здравствуйте! Чем могу помочь?" : text,
                                                date,
                                                peerId,
                                                avatarUrl
                                        );
                                        supportDialog.setReadStatus(readStatus);
                                        supportDialog.setOutgoing(isOut);
                                        supportDialog.setGroupChat("chat".equals(peerType));
                                        supportDialog.setChatTitle("Поддержка");
                                        if (conversation.has("unread_count")) {
                                            supportDialog.setUnreadCount(conversation.optInt("unread_count"));
                                        }
                                        pinnedDialogs.add(supportDialog);
                                    } else if ("-999999999".equals(dialogInfo.userId)) {
                                        hasAutoBot = true;
                                        Dialog autoBotDialog = new Dialog(
                                                dialogInfo.userId,
                                                "Авто-бот",
                                                "Привет! Я бот с готовыми ответами. Задайте мне вопрос!",
                                                date,
                                                peerId,
                                                "https://via.placeholder.com/100/4CAF50/FFFFFF?text=AI"
                                        );
                                        autoBotDialog.setReadStatus(readStatus);
                                        autoBotDialog.setOutgoing(isOut);
                                        autoBotDialog.setGroupChat("chat".equals(peerType));
                                        autoBotDialog.setChatTitle("Авто-бот");
                                        if (conversation.has("unread_count")) {
                                            autoBotDialog.setUnreadCount(conversation.optInt("unread_count"));
                                        }
                                        pinnedDialogs.add(autoBotDialog);
                                    }
                                } else {
                                    allDialogs.add(dialog);
                                }
                            }

                            // Сохраняем все диалоги для последующей фильтрации
                            dialogList.clear();
                            dialogList.addAll(allDialogs);

                            // Фильтруем диалоги - показываем только архивные чаты
                            List<Dialog> archivedDialogs = new ArrayList<>();
                            for (Dialog dialog : allDialogs) {
                                if (dialog.isArchived()) {
                                    archivedDialogs.add(dialog);
                                }
                            }

                            // Добавляем закрепленные чаты в начало
                            archivedDialogs.addAll(0, pinnedDialogs);

                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    adapter.setDialogs(archivedDialogs);
                                    updateDialogCountText(archivedDialogs.size(), 0);
                                    hideHorizontalProgress();

                                    if (!isSpecialUsersLoaded) {
                                        new Handler().postDelayed(() -> {
                                            if (isAdded()) {
                                                adapter.notifyDataSetChanged();
                                            }
                                        }, 1000);
                                    }

                                    // Показываем сообщение о количестве архивных чатов
                                    if (archivedDialogs.size() > 0) {
                                        // Можно добавить сообщение если нужно
                                    } else {
                                        Toast.makeText(getContext(),
                                                "Архивные чаты не найдены",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    } catch (JSONException e) {
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(),
                                        "Ошибка парсинга: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                                updateDialogCountText(0, 0);
                                hideHorizontalProgress();
                            });
                        }
                    }
                }
            }
        });
    }

    // Метод для показа архивных чатов (старая версия - оставляем для обратной совместимости)
    private void showArchivedChatsList() {
        if (!isAdded()) return;

        if (isTestMode) {
            // В тестовом режиме показываем тестовые архивные чаты
            List<Dialog> archivedDialogs = createTestArchivedDialogs();
            adapter.setDialogs(archivedDialogs);
            updateDialogCountText(archivedDialogs.size(), 0);
            hideHorizontalProgress();
            return;
        }

        // В реальном режиме используем обновленный метод с загрузкой данных
        loadArchivedChatsWithRefresh();
    }

    // Метод для создания тестовых архивных чатов
    private List<Dialog> createTestArchivedDialogs() {
        List<Dialog> archivedDialogs = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        // Архивный чат 1
        Dialog archived1 = new Dialog(
                "111111111",
                "Архивный друг",
                "Давно не общались",
                currentTime - 2592000000L, // 30 дней назад
                "111111111",
                "https://via.placeholder.com/100/808080/FFFFFF?text=A"
        );
        archived1.setReadStatus(Dialog.READ_STATUS_INCOMING);
        archived1.setOutgoing(false);
        archived1.setUnreadCount(0);
        archived1.setArchived(true);
        archivedDialogs.add(archived1);

        // Архивный чат 2
        Dialog archived2 = new Dialog(
                "222222222",
                "Старая группа",
                "Последнее сообщение было давно",
                currentTime - 1728000000L, // 20 дней назад
                "2000000004",
                "https://via.placeholder.com/100/696969/FFFFFF?text=AG"
        );
        archived2.setReadStatus(Dialog.READ_STATUS_INCOMING);
        archived2.setOutgoing(false);
        archived2.setGroupChat(true);
        archived2.setChatTitle("Старая группа");
        archived2.setUnreadCount(0);
        archived2.setArchived(true);
        archivedDialogs.add(archived2);

        return archivedDialogs;
    }

    // Метод для загрузки архивных чатов из SharedPreferences
    private void loadArchivedChats() {
        Context context = getSafeContext();
        if (context == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREF_ARCHIVED_CHATS, Context.MODE_PRIVATE);
        archivedChats = prefs.getStringSet(PREF_ARCHIVED_CHATS, new HashSet<>());
    }

    // Метод для сохранения архивных чатов в SharedPreferences
    private void saveArchivedChats() {
        Context context = getSafeContext();
        if (context == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREF_ARCHIVED_CHATS, Context.MODE_PRIVATE);
        prefs.edit().putStringSet(PREF_ARCHIVED_CHATS, archivedChats).apply();
    }

    // Метод для архивации чата
    private void archiveChat(Dialog dialog) {
        if (!isAdded()) return;

        if (isTestMode) {
            Toast.makeText(getContext(), "Демо-режим: чат перемещен в архив", Toast.LENGTH_SHORT).show();
            dialog.setArchived(true);
            adapter.notifyDataSetChanged();
            return;
        }

        String chatId = dialog.getUserId();
        archivedChats.add(chatId);
        saveArchivedChats();

        Toast.makeText(getContext(), "Чат перемещен в архив", Toast.LENGTH_SHORT).show();

        // Обновляем список диалогов
        if (showArchivedChats) {
            // Если мы в режиме архива, обновляем данные
            loadArchivedChatsWithRefresh();
        } else {
            refreshData();
        }
    }

    // Метод для разархивации чата
    private void unarchiveChat(Dialog dialog) {
        if (!isAdded()) return;

        String chatId = dialog.getUserId();
        archivedChats.remove(chatId);
        saveArchivedChats();

        Toast.makeText(getContext(), "Чат восстановлен из архива", Toast.LENGTH_SHORT).show();

        // Обновляем список диалогов
        if (showArchivedChats) {
            // Если мы в режиме архива, обновляем данные
            loadArchivedChatsWithRefresh();
        } else {
            refreshData();
        }
    }

    // Метод для показа диалога архивации/удаления
    private void showArchiveDeleteDialog(Dialog dialog) {
        if (!isAdded()) return;

        String dialogName = dialog.isGroupChat() ? dialog.getChatTitle() : dialog.getUserName();
        boolean isArchived = archivedChats.contains(dialog.getUserId());

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Действия с чатом");

        // Создаем массив действий
        String[] actions;
        if (isArchived) {
            actions = new String[]{"Восстановить из архива", "Удалить"};
        } else {
            actions = new String[]{"Архивировать", "Удалить"};
        }

        builder.setItems(actions, (dialogInterface, which) -> {
            switch (which) {
                case 0: // Архивация/Восстановление
                    if (isArchived) {
                        unarchiveChat(dialog);
                    } else {
                        archiveChat(dialog);
                    }
                    break;
                case 1: // Удаление
                    showDeleteDialog(dialog);
                    break;
            }
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    // Метод для показа диалога удаления
    private void showDeleteDialog(Dialog dialog) {
        if (!isAdded()) return;

        String dialogName = dialog.isGroupChat() ? dialog.getChatTitle() : dialog.getUserName();

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Удаление диалога")
                .setMessage("Вы уверены, что хотите удалить диалог с \"" + dialogName + "\"?")
                .setPositiveButton("Удалить", (dialogInterface, which) -> {
                    deleteDialog(dialog);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // Метод для удаления диалога
    private void deleteDialog(Dialog dialog) {
        if (!isAdded()) return;

        if (isTestMode) {
            Toast.makeText(getContext(), "Демо-режим: удаление недоступно", Toast.LENGTH_SHORT).show();
            return;
        }

        Context context = getSafeContext();
        if (context == null) return;

        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null) {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        // Не удаляем чат поддержки и авто-бота
        if ("-71746274".equals(dialog.getUserId()) || "-999999999".equals(dialog.getUserId())) {
            Toast.makeText(getContext(), "Этот чат нельзя удалить", Toast.LENGTH_SHORT).show();
            return;
        }

        // Не удаляем чат "Избранное" (чат с самим собой)
        if (userId != null && userId.equals(dialog.getUserId()) || "Избранное".equals(dialog.getUserName())) {
            Toast.makeText(getContext(), "Чат 'Избранное' нельзя удалить", Toast.LENGTH_SHORT).show();
            return;
        }

        // Удаляем из архива если он там был
        archivedChats.remove(dialog.getUserId());
        saveArchivedChats();

        // Для групповых чатов используем peer_id, для личных - user_id
        String peerId = dialog.getPeerId();
        boolean isChat = dialog.isGroupChat();

        String url;
        if (isChat) {
            // Для групповых чатов
            url = "https://api.vk.com/method/messages.deleteConversation" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&peer_id=" + peerId;
        } else {
            // Для личных диалогов
            url = "https://api.vk.com/method/messages.deleteConversation" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&user_id=" + dialog.getUserId();
        }

        Request request = new Request.Builder()
                .url(url)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), "Ошибка удаления диалога", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response") && json.getInt("response") == 1) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    Toast.makeText(getContext(), "Диалог удален", Toast.LENGTH_SHORT).show();
                                    // Обновляем список диалогов
                                    Context context = getSafeContext();
                                    if (context != null) {
                                        String token = TokenManager.getInstance(context).getToken();
                                        if (token != null) {
                                            if (showArchivedChats) {
                                                loadArchivedChatsWithRefresh();
                                            } else {
                                                fetchDialogs(token);
                                            }
                                        }
                                    }
                                });
                            }
                        } else if (json.has("error")) {
                            String errorMsg = json.getJSONObject("error").getString("error_msg");
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    Toast.makeText(getContext(), "Ошибка: " + errorMsg, Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    } catch (Exception e) {
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), "Ошибка удаления диалога", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                }
            }
        });
    }

    // Метод для показа тестовых данных
    private void showTestData() {
        if (!isAdded()) return;

        // Создаем тестовые диалоги
        List<Dialog> testDialogs = createTestDialogs();

        // Фильтруем по архиву если нужно
        if (!showArchivedChats) {
            List<Dialog> activeDialogs = new ArrayList<>();
            for (Dialog dialog : testDialogs) {
                if (!dialog.isArchived()) {
                    activeDialogs.add(dialog);
                }
            }
            testDialogs = activeDialogs;
        }

        // Обновляем UI
        if (getActivity() != null && isAdded()) {
            List<Dialog> finalTestDialogs = testDialogs;
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                adapter.setDialogs(finalTestDialogs);
                updateDialogCountText(finalTestDialogs.size(), 3); // 3 непрочитанных
                hideHorizontalProgress();

                // Обновляем имя пользователя для тестового режима
                profileNameTextView.setText("Тестовый Пользователь");
                userFirstName = "Тестовый";
                userLastName = "Пользователь";
            });
        }
    }

    // Метод для создания тестовых диалогов (включая архивные)
    private List<Dialog> createTestDialogs() {
        List<Dialog> testDialogs = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        // 1. Чат поддержки (всегда первый)
        Dialog supportDialog = new Dialog(
                "-71746274",
                "Поддержка",
                "Здравствуйте! Чем могу помочь?",
                currentTime - 3600000, // 1 час назад
                "-71746274",
                "https://via.placeholder.com/100/0077FF/FFFFFF?text=S"
        );
        supportDialog.setReadStatus(Dialog.READ_STATUS_INCOMING);
        supportDialog.setOutgoing(false);
        supportDialog.setUnreadCount(0);
        testDialogs.add(supportDialog);

        // 2. Авто-бот (второй)
        Dialog autoBotDialog = new Dialog(
                "-999999999",
                "Авто-бот",
                "Привет! Я бот с готовыми ответами. Задайте мне вопрос!",
                currentTime - 1800000, // 30 минут назад
                "-999999999",
                "https://via.placeholder.com/100/4CAF50/FFFFFF?text=AI"
        );
        autoBotDialog.setReadStatus(Dialog.READ_STATUS_INCOMING);
        autoBotDialog.setOutgoing(false);
        autoBotDialog.setUnreadCount(0);
        testDialogs.add(autoBotDialog);

        // 3. Избранное (третий)
        Dialog favoriteDialog = new Dialog(
                userId != null ? userId : "12345",
                "Избранное",
                "Сохраняйте здесь важные сообщения",
                currentTime - 7200000, // 2 часа назад
                userId != null ? userId : "12345",
                "https://via.placeholder.com/100/FFA500/FFFFFF?text=F"
        );
        favoriteDialog.setReadStatus(Dialog.READ_STATUS_READ);
        favoriteDialog.setOutgoing(true);
        favoriteDialog.setUnreadCount(0);
        testDialogs.add(favoriteDialog);

        // 4. Популярный блогер (непрочитанный)
        Dialog bloggerDialog = new Dialog(
                "123456789",
                "Иван Иванов",
                "Привет! Посмотри мое новое видео 😊",
                currentTime - 300000, // 5 минут назад
                "123456789",
                "https://via.placeholder.com/100/4CAF50/FFFFFF?text=I"
        );
        bloggerDialog.setReadStatus(Dialog.READ_STATUS_INCOMING);
        bloggerDialog.setOutgoing(false);
        bloggerDialog.setUnreadCount(1);
        testDialogs.add(bloggerDialog);

        // 5. Групповой чат
        Dialog groupDialog = new Dialog(
                "2000000001",
                "Лучшие друзья",
                "Мария: Кто сегодня вечером свободен?",
                currentTime - 1800000, // 30 минут назад
                "2000000001",
                "https://via.placeholder.com/100/9C27B0/FFFFFF?text=G"
        );
        groupDialog.setReadStatus(Dialog.READ_STATUS_INCOMING);
        groupDialog.setOutgoing(false);
        groupDialog.setGroupChat(true);
        groupDialog.setChatTitle("Лучшие друзья");
        groupDialog.setUnreadCount(2);
        testDialogs.add(groupDialog);

        // 6. Коллега по работе
        Dialog colleagueDialog = new Dialog(
                "987654321",
                "Анна Петрова",
                "Вы: Добрый день! Отправил вам файлы",
                currentTime - 86400000, // 1 день назад
                "987654321",
                "https://via.placeholder.com/100/FF5722/FFFFFF?text=A"
        );
        colleagueDialog.setReadStatus(Dialog.READ_STATUS_SENT);
        colleagueDialog.setOutgoing(true);
        colleagueDialog.setUnreadCount(0);
        testDialogs.add(colleagueDialog);

        // 7. Семейный чат (непрочитанный)
        Dialog familyDialog = new Dialog(
                "2000000002",
                "Семья",
                "Мама: Не забудьте зайти в воскресенье!",
                currentTime - 60000, // 1 минуту назад
                "2000000002",
                "https://via.placeholder.com/100/3F51B5/FFFFFF?text=F"
        );
        familyDialog.setReadStatus(Dialog.READ_STATUS_INCOMING);
        familyDialog.setOutgoing(false);
        familyDialog.setGroupChat(true);
        familyDialog.setChatTitle("Семья");
        familyDialog.setUnreadCount(1);
        testDialogs.add(familyDialog);

        // 8. Старый друг (архивный)
        Dialog oldFriendDialog = new Dialog(
                "555555555",
                "Сергей Сидоров",
                "Давно не виделись! Как дела?",
                currentTime - 259200000, // 3 дня назад
                "555555555",
                "https://via.placeholder.com/100/607D8B/FFFFFF?text=S"
        );
        oldFriendDialog.setReadStatus(Dialog.READ_STATUS_INCOMING);
        oldFriendDialog.setOutgoing(false);
        oldFriendDialog.setUnreadCount(0);
        oldFriendDialog.setArchived(true);
        testDialogs.add(oldFriendDialog);

        // 9. Учеба/Курсы
        Dialog studyDialog = new Dialog(
                "2000000003",
                "Программирование Android",
                "Преподаватель: Не забудьте сделать домашнее задание",
                currentTime - 172800000, // 2 дня назад
                "2000000003",
                "https://via.placeholder.com/100/009688/FFFFFF?text=P"
        );
        studyDialog.setReadStatus(Dialog.READ_STATUS_INCOMING);
        studyDialog.setOutgoing(false);
        studyDialog.setGroupChat(true);
        studyDialog.setChatTitle("Программирование Android");
        studyDialog.setUnreadCount(0);
        testDialogs.add(studyDialog);

        // 10. Сообщение с вложением
        Dialog attachmentDialog = new Dialog(
                "777777777",
                "Екатерина Волкова",
                "📎 Фотография",
                currentTime - 43200000, // 12 часов назад
                "777777777",
                "https://via.placeholder.com/100/E91E63/FFFFFF?text=E"
        );
        attachmentDialog.setReadStatus(Dialog.READ_STATUS_INCOMING);
        attachmentDialog.setOutgoing(false);
        attachmentDialog.setUnreadCount(0);
        testDialogs.add(attachmentDialog);

        // 11. Важное уведомление
        Dialog importantDialog = new Dialog(
                "888888888",
                "Важные уведомления",
                "Напоминание: завтра встреча в 15:00",
                currentTime - 14400000, // 4 часа назад
                "888888888",
                "https://via.placeholder.com/100/FFC107/000000?text=!"
        );
        importantDialog.setReadStatus(Dialog.READ_STATUS_INCOMING);
        importantDialog.setOutgoing(false);
        importantDialog.setUnreadCount(0);
        testDialogs.add(importantDialog);

        return testDialogs;
    }

    // Метод для открытия диалога
    private void openDialogActivity(Dialog dialog) {
        if (!isAdded()) return;

        if (isTestMode) {
            // В тестовом режиме открываем тестовые диалоги
            openTestDialog(dialog);
            return;
        }

        String userId = dialog.getUserId();
        String userName = dialog.getUserName();
        String peerId = dialog.getPeerId();
        boolean isSpecialUser = specialUsers.contains(userId);

        Log.d(TAG, "Opening dialog activity - UserId: " + userId +
                ", UserName: " + userName +
                ", PeerId: " + peerId +
                ", IsSpecial: " + isSpecialUser +
                ", CurrentUserId: " + this.userId);

        // Если это чат поддержки, используем специальную логику
        if ("-71746274".equals(userId)) {
            openSupportDialog();
        }
        // Если это чат "Избранное" (проверяем по имени ИЛИ по ID)
        else if ("Избранное".equals(userName) || (this.userId != null && this.userId.equals(userId))) {
            openFavoriteDialog();
        }
        // Если это авто-бот
        else if ("-999999999".equals(userId) || "Авто-бот".equals(userName)) {
            openAutoBotChat();
        } else {
            DialogActivity.start(requireContext(), userId, userName, peerId, isSpecialUser);
        }
    }

    // Метод для открытия тестовых диалогов
    private void openTestDialog(Dialog dialog) {
        if (!isAdded()) return;

        String userId = dialog.getUserId();
        String userName = dialog.getUserName();
        String peerId = dialog.getPeerId();

        Log.d(TAG, "Opening test dialog - UserId: " + userId +
                ", UserName: " + userName +
                ", PeerId: " + peerId);

        // Если это авто-бот в тестовом режиме
        if ("-999999999".equals(userId) || "Авто-бот".equals(userName)) {
            openAutoBotChat();
            return;
        }

        // Оригинальная логика для других диалогов
        Intent intent = new Intent(requireContext(), DialogActivity.class);
        intent.putExtra("user_id", userId);
        intent.putExtra("user_name", userName);
        intent.putExtra("peer_id", peerId);
        intent.putExtra("is_special_user", specialUsers.contains(userId));
        intent.putExtra("is_test_mode", true);

        startActivity(intent);
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        Toast.makeText(requireContext(),
                "Демо-режим: тестовый диалог\n" + dialog.getUserName(),
                Toast.LENGTH_SHORT).show();
    }

    // Метод для инициализации BroadcastReceiver для уведомлений
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void initNotificationsReceiver() {
        notificationsStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("NOTIFICATIONS_STATE_CHANGED".equals(intent.getAction())) {
                    boolean notificationsEnabled = intent.getBooleanExtra("notifications_enabled", true);
                    handleNotificationsStateChange(notificationsEnabled);
                }
            }
        };

        // Регистрируем BroadcastReceiver
        Context context = getSafeContext();
        if (context != null) {
//            IntentFilter filter = new IntentFilter("NOTIFICATIONS_STATE_CHANGED");
//            context.registerReceiver(notificationsStateReceiver, filter);
        }
    }

    // Метод для обработки изменения состояния уведомлений
    private void handleNotificationsStateChange(boolean notificationsEnabled) {
        if (notificationsEnabled) {
            // Включаем проверку сообщений
            startMessageChecking();
            Log.d(TAG, "Уведомления включены, запускаем проверку сообщений");
        } else {
            // Отключаем проверку сообщений и очищаем уведомления
            stopMessageChecking();
            notificationManager.cancelAll();
            Log.d(TAG, "Уведомления отключены, останавливаем проверку сообщений");
        }
    }

    // Метод для проверки включены ли уведомления
    private boolean areNotificationsEnabled() {
        Context context = getSafeContext();
        if (context == null) return false;

        SharedPreferences prefs = context.getSharedPreferences("VK_PREFsS", Context.MODE_PRIVATE);
        return prefs.getBoolean("notifications_enabled", true); // По умолчанию включены
    }

    // Метод для показа горизонтального прогресса
    private void showHorizontalProgress() {
        if (!isAdded()) return;

        if (horizontalProgressBar != null) {
            horizontalProgressBar.setVisibility(View.VISIBLE);
        }
        if (loadingText != null) {
            loadingText.setVisibility(View.VISIBLE);
            loadingText.setText(showArchivedChats ? "Загрузка архивных чатов..." : "Загрузка диалогов...");
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false); // Отключаем крутящийся индикатор
        }
        // НЕ меняем имя пользователя - оно должно оставаться прежним
    }

    // Метод для скрытия горизонтального прогресса
    private void hideHorizontalProgress() {
        if (!isAdded()) return;

        if (horizontalProgressBar != null) {
            horizontalProgressBar.setVisibility(View.GONE);
        }
        if (loadingText != null) {
            loadingText.setVisibility(View.GONE);
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
        // Имя пользователя остается без изменений
    }

    // Метод для обновления данных
    private void refreshData() {
        if (!isAdded()) return;

        if (isTestMode) {
            // В тестовом режиме просто обновляем тестовые данные
            showTestData();
            return;
        }

        Context context = getSafeContext();
        if (context != null) {
            String accessToken = TokenManager.getInstance(context).getToken();
            if (accessToken != null) {
                if (showArchivedChats) {
                    // Если в режиме архива, используем специальный метод
                    loadArchivedChatsWithRefresh();
                } else {
                    fetchDialogs(accessToken);
                }

                // Также обновляем профиль пользователя (но имя уже загружено)
                if (userId != null) {
                    loadUserProfile(userId); // Это обновит имя если оно изменилось
                }
            } else {
                hideHorizontalProgress();
                Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Перезагружаем архивные чаты при возвращении на фрагмент
        loadArchivedChats();

        if (isTestMode) {
            // В тестовом режиме просто обновляем данные
            showTestData();
        } else {
            // При возвращении на фрагмент обновляем диалоги и скрываем уведомления
            Context context = getSafeContext();
            if (context != null) {
                String accessToken = TokenManager.getInstance(context).getToken();
                if (accessToken != null) {
                    if (showArchivedChats) {
                        // Если мы в режиме архива, обновляем архивные чаты
                        loadArchivedChatsWithRefresh();
                    } else {
                        fetchDialogs(accessToken);
                    }
                }
            }
            // Скрываем уведомления когда пользователь в приложении
            hideNotifications();
        }

        // Обновляем состояние автоответов
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //updateAutoResponseFab();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // При уходе с фрагмента останавливаем проверку сообщений
        stopMessageChecking();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Останавливаем проверку сообщений при уничтожении фрагмента
        stopMessageChecking();

        // Отменяем регистрацию BroadcastReceiver
        if (notificationsStateReceiver != null) {
            try {
                Context context = getSafeContext();
                if (context != null) {
                    context.unregisterReceiver(notificationsStateReceiver);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering notifications receiver", e);
            }
        }

        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.dispatcher().cancelAll();
        }
        if (autoResponseDBHelper != null) {
            autoResponseDBHelper.close();
        }
    }

    // Метод для открытия BottomSheet со списком друзей
    private void openFriendsBottomSheet() {
        if (!isAdded()) return;

        if (isTestMode) {
            Toast.makeText(getContext(), "Демо-режим: создание чата недоступно", Toast.LENGTH_SHORT).show();
            return;
        }
        // Сначала загружаем список друзей
        loadFriendsList();
    }

    // Загрузка списка друзей из VK API
    private void loadFriendsList() {
        if (!isAdded()) return;

        Context context = getSafeContext();
        if (context == null) return;

        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null) {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = "https://api.vk.com/method/friends.get" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&fields=photo_100,online" +
                "&count=100";

        Request request = new Request.Builder()
                .url(url)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), "Ошибка загрузки друзей", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");

                            friendList.clear();
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject friend = items.getJSONObject(i);
                                String id = friend.getString("id");
                                String firstName = friend.getString("first_name");
                                String lastName = friend.getString("last_name");
                                String photoUrl = friend.optString("photo_100", "");
                                boolean isOnline = friend.optInt("online") == 1;

                                friendList.add(new Friend(id, firstName + " " + lastName, photoUrl, isOnline));
                            }

                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    showFriendsBottomSheet();
                                });
                            }
                        } else if (json.has("error")) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    Toast.makeText(getContext(), "Ошибка API", Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    } catch (Exception e) {
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), "Ошибка парсинга", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                }
            }
        });
    }

    // Показать BottomSheet со списком друзей
    private void showFriendsBottomSheet() {
        if (!isAdded()) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_friends, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        // Настройка RecyclerView для списка друзей
        RecyclerView friendsRecyclerView = bottomSheetView.findViewById(R.id.friendsRecyclerView);
        friendsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        FriendsAdapter adapter = new FriendsAdapter(friendList);
        friendsRecyclerView.setAdapter(adapter);

        // Кнопка создания чата
        Button createChatButton = bottomSheetView.findViewById(R.id.btnCreateChat);
        createChatButton.setOnClickListener(v -> {
            List<String> selectedFriends = adapter.getSelectedFriends();
            if (selectedFriends.isEmpty()) {
                Toast.makeText(getContext(), "Выберите хотя бы одного друга", Toast.LENGTH_SHORT).show();
                return;
            }

            createGroupChat(selectedFriends);
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();

        // Настройка высоты BottomSheet
        if (bottomSheetDialog.getWindow() != null) {
            bottomSheetDialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }

    // Создание группового чата
    private void createGroupChat(List<String> selectedFriendIds) {
        if (!isAdded()) return;

        Context context = getSafeContext();
        if (context == null) return;

        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null) {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        // Формируем список ID пользователей для чата
        StringBuilder userIds = new StringBuilder();
        for (String friendId : selectedFriendIds) {
            if (userIds.length() > 0) {
                userIds.append(",");
            }
            userIds.append(friendId);
        }

        String url = "https://api.vk.com/method/messages.createChat" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&user_ids=" + userIds.toString() +
                "&title=Групповой%20чат";

        Request request = new Request.Builder()
                .url(url)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), "Ошибка создания чата", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            int chatId = json.getJSONObject("response").getInt("chat_id");
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    Toast.makeText(getContext(), "Чат создан успешно", Toast.LENGTH_SHORT).show();
                                    // Обновляем список диалогов
                                    Context context = getSafeContext();
                                    if (context != null) {
                                        String token = TokenManager.getInstance(context).getToken();
                                        if (token != null) {
                                            if (showArchivedChats) {
                                                loadArchivedChatsWithRefresh();
                                            } else {
                                                fetchDialogs(token);
                                            }
                                        }
                                    }
                                });
                            }
                        } else if (json.has("error")) {
                            String errorMsg = json.getJSONObject("error").getString("error_msg");
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    Toast.makeText(getContext(), "Ошибка: " + errorMsg, Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    } catch (Exception e) {
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), "Ошибка создания чата", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                }
            }
        });
    }

  /*  @RequiresApi(api = Build.VERSION_CODES.N)
    private void updateAutoResponseFab() {
        if (!isAdded()) return;

        List<AutoResponse> responses = autoResponseDBHelper.getAllAutoResponses();
        if (fabAutoResponse != null) {
            if (responses.isEmpty()) {
                fabAutoResponse.setVisibility(View.GONE);
            } else {
                fabAutoResponse.setVisibility(View.VISIBLE);
                // Показываем количество активных автоответов
                long activeCount = responses.stream().filter(AutoResponse::isActive).count();
                if (activeCount > 0) {
                    fabAutoResponse.setContentDescription("Автоответы (" + activeCount + " активных)");
                }
            }
        }
    }*/

    // Создание канала уведомлений (для Android 8.0+)
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Сообщения ВК",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Уведомления о новых сообщениях ВКонтакте");
            channel.enableLights(true);
            channel.setLightColor(Color.BLUE);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // Запуск периодической проверки сообщений
    private void startMessageChecking() {
        // Проверяем, включены ли уведомления и прикреплен ли фрагмент
        if (!isAdded() || !areNotificationsEnabled() || isTestMode) {
            Log.d(TAG, "Fragment not attached or notifications disabled, not starting message check");
            return;
        }

        stopMessageChecking(); // Останавливаем предыдущую проверку

        messageChecker = new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) {
                    Log.d(TAG, "Fragment not attached, stopping message checking");
                    stopMessageChecking();
                    return;
                }
               // checkNewMessages();
                if (messageHandler != null && isAdded()) {
                    messageHandler.postDelayed(this, CHECK_INTERVAL);
                }
            }
        };
        if (messageHandler != null && isAdded()) {
            messageHandler.postDelayed(messageChecker, CHECK_INTERVAL);
        }
        Log.d(TAG, "Запущена проверка новых сообщений");
    }

    // Остановка проверки сообщений
    private void stopMessageChecking() {
        if (messageHandler != null && messageChecker != null) {
            messageHandler.removeCallbacks(messageChecker);
            Log.d(TAG, "Проверка сообщений остановлена");
        }
    }

    // Проверка новых сообщений
    private void checkNewMessages() {
        // Проверяем, включены ли уведомления и прикреплен ли фрагмент
        if (!isAdded() || !areNotificationsEnabled() || isTestMode) {
            Log.d(TAG, "Fragment not attached or notifications disabled, skipping message check");
            return;
        }

        Context context = getSafeContext();
        if (context == null) return;

        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null) return;

        String url = "https://api.vk.com/method/messages.getConversations" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&count=10" + // Проверяем несколько последних диалогов
                "&extended=1";

        Request request = new Request.Builder()
                .url(url)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to check new messages: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // Проверяем, прикреплен ли еще фрагмент
                if (!isAdded()) {
                    Log.d(TAG, "Fragment not attached, ignoring message check response");
                    return;
                }

                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");

                            for (int i = 0; i < items.length(); i++) {
                                JSONObject conversation = items.getJSONObject(i);
                                JSONObject lastMessage = conversation.getJSONObject("last_message");

                                // Проверяем, является ли сообщение новым входящим
                                boolean isOut = lastMessage.optInt("out") == 0; // 0 - входящее
                                int readState = lastMessage.optInt("read_state");
                                long messageTime = lastMessage.optLong("date") * 1000;
                                int messageId = lastMessage.optInt("id", 0);

                                // Получаем информацию об отправителе
                                JSONArray profiles = responseObj.optJSONArray("profiles");
                                String senderName = "Неизвестный";
                                String senderId = lastMessage.optString("from_id");
                                String senderAvatar = null;

                                if (profiles != null) {
                                    for (int j = 0; j < profiles.length(); j++) {
                                        JSONObject profile = profiles.getJSONObject(j);
                                        if (profile.optString("id").equals(senderId)) {
                                            senderName = profile.optString("first_name", "Неизвестный");
                                            senderAvatar = profile.optString("photo_100", null);
                                            break;
                                        }
                                    }
                                }

                                // Создаем уникальный ID для уведомления
                                String notificationId = senderId + "_" + messageId;

                                // Сообщение считается новым если оно входящее и непрочитанное
                                // и пришло после последнего проверенного сообщения
                                // И мы еще не показывали уведомление для этого сообщения
                                if (isOut && readState == 0 && messageTime > lastMessageTime &&
                                        !shownNotificationIds.contains(notificationId)) {

                                    lastMessageTime = messageTime;
                                    shownNotificationIds.add(notificationId); // Запоминаем ID уведомления

                                    String messageText = lastMessage.optString("text", "(вложение)");

                                    // Проверяем и отправляем автоответ
                                    checkAndSendAutoResponse(senderId, messageText);

                                    String shortMessage = messageText;
                                    if (shortMessage.length() > 50) {
                                        shortMessage = shortMessage.substring(0, 47) + "...";
                                    }

                                    // Показываем уведомление с уникальным ID
                                    showNewMessageNotification(senderName, shortMessage, senderId, senderAvatar, messageId);
                                    break; // Показываем уведомление только для самого нового сообщения
                                }
                            }

                            // Очищаем старые ID уведомлений (чтобы не накапливались)
                            cleanupOldNotificationIds();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing new messages: " + e.getMessage());
                    }
                }
            }
        });
    }

    // Очистка старых ID уведомлений (сохраняем только последние 100)
    private void cleanupOldNotificationIds() {
        if (shownNotificationIds.size() > 100) {
            // Создаем новый Set с последними 50 элементами
            List<String> idsList = new ArrayList<>(shownNotificationIds);
            shownNotificationIds = new HashSet<>(idsList.subList(
                    Math.max(0, idsList.size() - 50), idsList.size()));
        }
    }

    // Метод для проверки и отправки автоответа
    private void checkAndSendAutoResponse(String peerId, String messageText) {
        if (messageText == null || messageText.equals("(вложение)")) {
            return;
        }

        String response = autoResponseDBHelper.findMatchingResponse(messageText);
        if (response != null) {
            Log.d(TAG, "Found auto-response for message: " + messageText);
            sendAutoResponse(peerId, response);
        }
    }

    // Метод для отправки автоответа
    private void sendAutoResponse(String peerId, String message) {
        // Проверяем, прикреплен ли фрагмент
        if (!isAdded()) {
            Log.e(TAG, "Fragment not attached, cannot send auto-response");
            return;
        }

        Context context = getSafeContext();
        if (context == null) {
            Log.e(TAG, "Context is null, cannot send auto-response");
            return;
        }

        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null) {
            Log.e(TAG, "Access token is null, cannot send auto-response");
            return;
        }

        try {
            String url = "https://api.vk.com/method/messages.send" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&peer_id=" + peerId +
                    "&message=" + URLEncoder.encode(message, "UTF-8") +
                    "&random_id=" + new Random().nextInt();

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to send auto-response: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    // Проверяем, прикреплен ли еще фрагмент
                    if (!isAdded()) {
                        Log.d(TAG, "Fragment not attached, ignoring auto-response result");
                        return;
                    }

                    if (response.isSuccessful()) {
                        Log.d(TAG, "Auto-response sent successfully to peer: " + peerId);
                        // Обновляем диалоги только если фрагмент все еще прикреплен
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Context context = getSafeContext();
                                if (context != null) {
                                    String token = TokenManager.getInstance(context).getToken();
                                    if (token != null) {
                                        if (showArchivedChats) {
                                            loadArchivedChatsWithRefresh();
                                        } else {
                                            fetchDialogs(token);
                                        }
                                    }
                                }
                            });
                        }
                    } else {
                        Log.e(TAG, "Failed to send auto-response, code: " + response.code());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error sending auto-response: " + e.getMessage());
        }
    }

    // Показать уведомление о новом сообщении с уникальным ID
    private void showNewMessageNotification(String senderName, String messageText, String senderId, String senderAvatar, int messageId) {
        // Проверяем, включены ли уведомления и прикреплен ли фрагмент
        if (!isAdded() || !areNotificationsEnabled()) {
            Log.d(TAG, "Fragment not attached or notifications disabled, not showing notification");
            return;
        }

        // Создаем уникальный ID для уведомления
        int notificationId = (senderId + "_" + messageId).hashCode() & 0x7fffffff;

        // Создаем интент для открытия диалога
        Intent intent = new Intent(requireContext(), BaseActivity.class);
        intent.putExtra("open_dialog", true);
        intent.putExtra("user_id", senderId);
        intent.putExtra("user_name", senderName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                requireContext(),
                notificationId, // Уникальный ID для каждого уведомления
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Создаем уведомление
        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_message_notification)
                .setContentTitle(senderName)
                .setContentText(messageText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(messageText))
                .setGroup("vk_messages")
                .setGroupSummary(true);

        // Загружаем аватарку для уведомления (если есть)
        if (senderAvatar != null && !senderAvatar.isEmpty()) {
            try {
                // Создаем Bitmap из URL аватарки
                Bitmap avatarBitmap = loadAvatarBitmap(senderAvatar);
                if (avatarBitmap != null) {
                    builder.setLargeIcon(avatarBitmap);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading avatar for notification", e);
            }
        }

        // Показываем уведомление с уникальным ID
        notificationManager.notify(notificationId, builder.build());

        // Вибрация
        vibrate();
    }

    // Метод для загрузки аватарки как Bitmap
    private Bitmap loadAvatarBitmap(String avatarUrl) {
        try {
            Request request = new Request.Builder().url(avatarUrl).build();
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                byte[] bytes = response.body().bytes();
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading avatar bitmap", e);
        }
        return null;
    }

    // Вибрация при новом сообщении
    private void vibrate() {
        // Проверяем, включены ли уведомления и прикреплен ли фрагмент
        if (!isAdded() || !areNotificationsEnabled()) {
            return;
        }

        Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
        }
    }

    // Скрыть уведомления
    private void hideNotifications() {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void showProfileBottomSheet() {
        if (!isAdded()) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_profile, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        // Находим элементы BottomSheet
        ImageView userAvatar = bottomSheetView.findViewById(R.id.profile_image);
        TextView openProfile = bottomSheetView.findViewById(R.id.profile_open);
        TextView userNameTextView = bottomSheetView.findViewById(R.id.user_name);
        TextView userStatusTextView = bottomSheetView.findViewById(R.id.user_status);
        ImageView premiumBadge = bottomSheetView.findViewById(R.id.premium_badge);
        TextView copyProfileLink = bottomSheetView.findViewById(R.id.copy_profile_link);
        TextView settingsFragment = bottomSheetView.findViewById(R.id.settingsFragment);
        TextView autoResponsesFragment = bottomSheetView.findViewById(R.id.autoResponsesFragment);
        TextView stikersFragment = bottomSheetView.findViewById(R.id.stikersFragment);
        TextView musicFragment = bottomSheetView.findViewById(R.id.my_music);
        TextView aboutFragment = bottomSheetView.findViewById(R.id.about);
        TextView groupsFragment = bottomSheetView.findViewById(R.id.groups);
        TextView albumFragment = bottomSheetView.findViewById(R.id.albums);
        TextView docFragment = bottomSheetView.findViewById(R.id.doc);
        TextView exitButton = bottomSheetView.findViewById(R.id.exit);

        // СКРЫВАЕМ ПУНКТ "СТИКЕРПАКИ" ДЛЯ ТЕСТОВОГО АККАУНТА
        if (isTestMode) {
            stikersFragment.setVisibility(View.GONE);
        } else {
            stikersFragment.setVisibility(View.VISIBLE);
            stikersFragment.setOnClickListener(v -> {
                openStickers();
                bottomSheetDialog.dismiss();
            });
        }

        // Устанавливаем аватарку пользователя
        if (profileAvatar.getDrawable() != null) {
            userAvatar.setImageDrawable(profileAvatar.getDrawable());
        } else {
            userAvatar.setImageResource(R.drawable.default_avatar);
        }

        // Устанавливаем имя пользователя
        String fullName = userFirstName + " " + userLastName;
        userNameTextView.setText(fullName);

        // Устанавливаем статус премиум
        if (isPremiumUser) {
            userStatusTextView.setText("У вас премиум");
            premiumBadge.setVisibility(View.VISIBLE);
            userStatusTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_background));
        } else {
            userStatusTextView.setText("Премиум отсутствует");
            premiumBadge.setVisibility(View.GONE);
            userStatusTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
        }

        // Устанавливаем обработчики нажатий
        copyProfileLink.setOnClickListener(v -> {
            openFeed();
        });

        settingsFragment.setOnClickListener(v -> {
            openSettings();
            bottomSheetDialog.dismiss();
        });

        autoResponsesFragment.setOnClickListener(v -> {
            openAutoResponses();
            bottomSheetDialog.dismiss();
        });

        userAvatar.setOnClickListener(v -> {
            openProfile();
            bottomSheetDialog.dismiss();
        });

        openProfile.setOnClickListener(v -> {
            openProfile();
            bottomSheetDialog.dismiss();
        });

        musicFragment.setOnClickListener(v -> {
            musicFragment();
            bottomSheetDialog.dismiss();
        });

        groupsFragment.setOnClickListener(v -> {
            groupsFragment();
            bottomSheetDialog.dismiss();
        });

        aboutFragment.setOnClickListener(v -> {
            aboutFragment();
            bottomSheetDialog.dismiss();
        });;

        albumFragment.setOnClickListener(v -> {
            openAlbum();
            bottomSheetDialog.dismiss();
        });;

        docFragment.setOnClickListener(v -> {
            openDoc();
            bottomSheetDialog.dismiss();
        });

        exitButton.setOnClickListener(v -> {
            showExitConfirmationDialog();
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void showExitConfirmationDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Выход из приложения")
                .setMessage("Вы действительно хотите выйти из приложения?")
                .setPositiveButton("Выйти", (dialog, which) -> {
                    exitApp();
                })
                .setNegativeButton("Отмена", null)
                .setCancelable(true)
                .show();
    }

    // Метод для выхода из приложения
    private void exitApp() {
        // Вариант 1: Завершение всех активностей
        if (getActivity() != null) {
            getActivity().finishAffinity();
        }

        // Вариант 2: Завершение процесса (более жесткий способ)
        // android.os.Process.killProcess(android.os.Process.myPid());

        // Вариант 3: Системный выход
        // System.exit(0);
    }

    private void openAutoResponses() {
        if (!isAdded()) return;
        Intent intent = new Intent(requireContext(), AutoResponseActivity.class);
        startActivity(intent);
    }

    private void openProfile() {
        if (!isAdded()) return;
        ProfileFragment settingsFragment = new ProfileFragment();
        Bundle args = new Bundle();
        settingsFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, settingsFragment)
                .addToBackStack("user_profile")
                .commit();
    }

    private void openAlbum() {
        if (!isAdded()) return;
        PhotoTabsFragment settingsFragment = new PhotoTabsFragment();
        Bundle args = new Bundle();
        settingsFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, settingsFragment)
                .addToBackStack("user_profile")
                .commit();
    }

    private void openDoc() {
        if (!isAdded()) return;
        DocumentsFragment documentsFragment = new DocumentsFragment();
        Bundle args = new Bundle();
        documentsFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, documentsFragment)
                .addToBackStack("user_profile")
                .commit();
    }

    private void groupsFragment() {
        if (!isAdded()) return;
        GroupsTabsFragment groupsTabsFragment = new GroupsTabsFragment();
        Bundle args = new Bundle();
        groupsTabsFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, groupsTabsFragment)
                .addToBackStack("user_profile")
                .commit();
    }

    private void videosFragment() {
        if (!isAdded()) return;
        VideoFragment groupsTabsFragment = new VideoFragment();
        Bundle args = new Bundle();
        groupsTabsFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, groupsTabsFragment)
                .addToBackStack("user_profile")
                .commit();
    }

    private void openFeed() {
        if (!isAdded()) return;
        NewsFeedFragment newsFeedFragment = new NewsFeedFragment();
        Bundle args = new Bundle();
        newsFeedFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, newsFeedFragment)
                .addToBackStack("user_profile")
                .commit();
    }

    private void musicFragment() {
        if (!isAdded()) return;
        RecommendationFragment settingsFragment = new RecommendationFragment();
        Bundle args = new Bundle();
        settingsFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, settingsFragment)
                .addToBackStack("user_profile")
                .commit();
    }

    private void aboutFragment() {
        if (!isAdded()) return;
        AboutFragment aboutFragment = new AboutFragment();
        Bundle args = new Bundle();
        aboutFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, aboutFragment)
                .addToBackStack("user_profile")
                .commit();
    }

    private void openSettings() {
        if (!isAdded()) return;
        SettingsFragment settingsFragment = new SettingsFragment();
        Bundle args = new Bundle();
        settingsFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, settingsFragment)
                .addToBackStack("user_profile")
                .commit();
    }

    private void openStickers() {
        if (!isAdded()) return;
        Intent intent = new Intent(requireActivity(), StickerPackManagerActivity.class);
        startActivity(intent);
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void copyToClipboard(String label, String text) {
        if (!isAdded()) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
    }

    private void showToast(String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void loadUserProfile(String userId) {
        if (isTestMode) {
            // В тестовом режиме устанавливаем тестовые данные
            if (getActivity() != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    userFirstName = "Тестовый";
                    userLastName = "Пользователь";
                    String fullName = userFirstName + " " + userLastName;
                    profileNameTextView.setText(fullName);

                    // Устанавливаем тестовую аватарку
                    Glide.with(requireContext())
                            .load("https://via.placeholder.com/100/0077FF/FFFFFF?text=TU")
                            .placeholder(R.drawable.default_avatar)
                            .error(R.drawable.default_avatar)
                            .circleCrop()
                            .into(profileAvatar);
                });
            }
            return;
        }

        // Оригинальный код для реального режима
        Context context = getSafeContext();
        if (context == null) return;

        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null) {
            if (getActivity() != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    profileNameTextView.setText("Не авторизован");
                });
            }
            return;
        }

        String url = "https://api.vk.com/method/users.get" +
                "?user_ids=" + userId +
                "&access_token=" + accessToken +
                "&fields=photo_100" +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "KateMobileAndroid/56 lite-447 (Android 6.0; SDK 23; x86; Google Android SDK built for x86; en)")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        profileNameTextView.setText("Ошибка соединения");
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // Проверяем, прикреплен ли еще фрагмент
                if (!isAdded()) return;

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        String errorMsg = error.getString("error_msg");
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                profileNameTextView.setText("Ошибка: " + errorMsg);
                            });
                        }
                        return;
                    }

                    JSONArray users = json.getJSONArray("response");
                    if (users.length() == 0) {
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                profileNameTextView.setText("Пользователь не найден");
                            });
                        }
                        return;
                    }

                    JSONObject user = users.getJSONObject(0);
                    userFirstName = user.getString("first_name");
                    userLastName = user.getString("last_name");
                    String fullName = userFirstName + " " + userLastName;
                    String photoUrl = user.optString("photo_100", null);

                    if (getActivity() != null && isAdded()) {
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            // ВСЕГДА обновляем имя пользователя при успешной загрузке
                            profileNameTextView.setText(fullName);
                            if (photoUrl != null && !photoUrl.isEmpty()) {
                                Glide.with(requireContext())
                                        .load(photoUrl)
                                        .placeholder(R.drawable.default_avatar)
                                        .error(R.drawable.default_avatar)
                                        .circleCrop()
                                        .into(profileAvatar);
                            }
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null && isAdded()) {
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            profileNameTextView.setText("Ошибка загрузки");
                        });
                    }
                }
            }
        });
    }

    // Метод для открытия диалога с поддержкой
    private void openSupportDialog() {
        if (!isAdded()) return;

        String supportUserId = "-71746274";
        String supportUserName = "Поддержка";
        String supportPeerId = "-71746274";
        boolean isSpecialUser = false;

        Log.d(TAG, "Opening support dialog - UserId: " + supportUserId +
                ", UserName: " + supportUserName +
                ", PeerId: " + supportPeerId);

        DialogActivity.start(requireContext(), supportUserId, supportUserName, supportPeerId, isSpecialUser);
    }

    // Метод для открытия диалога "Избранное"
    private void openFavoriteDialog() {
        if (!isAdded()) return;

        String favoriteUserId = userId;
        String favoriteUserName = "Избранное";
        String favoritePeerId = userId;
        boolean isSpecialUser = false;

        Log.d(TAG, "Opening favorite dialog - UserId: " + favoriteUserId +
                ", UserName: " + favoriteUserName +
                ", PeerId: " + favoritePeerId);

        DialogActivity.start(requireContext(), favoriteUserId, favoriteUserName, favoritePeerId, isSpecialUser);
    }

    private void updateDialogCountText(int totalCount, int unreadCount) {
        if (dialogCountText != null && isAdded()) {
            String countText;
            if (totalCount == 0) {
                countText = showArchivedChats ? "Архивные чаты не найдены" : "Диалоги не загружены";
            } else {
                if (unreadCount > 0 && !showArchivedChats) {
                    countText = formatDialogCount(totalCount) + " (" + unreadCount + " непрочитанных)";
                    dialogCountText.setTextColor(Color.RED);
                } else {
                    countText = formatDialogCount(totalCount);
                    dialogCountText.setTextColor(Color.GRAY);
                }
            }
            dialogCountText.setText(countText);
        }
    }

    private String formatDialogCount(int count) {
        if (count == 0) {
            return showArchivedChats ? "Нет архивных чатов" : "Нет диалогов";
        } else if (count == 1) {
            return showArchivedChats ? "1 архивный чат" : "1 диалог";
        } else if (count >= 2 && count <= 4) {
            return showArchivedChats ? count + " архивных чата" : count + " диалога";
        } else {
            return showArchivedChats ? count + " архивных чатов" : count + " диалогов";
        }
    }

    private void checkSpecialUser(String userId) {
        if (isTestMode) {
            // В тестовом режиме показываем премиум значок
            if (getActivity() != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    isPremiumUser = true;
                    specialIcon.setVisibility(View.VISIBLE);
                    animateSpecialIcon();
                });
            }
            return;
        }

        String specialUsersUrl = "https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/special_users.json";

        Request request = new Request.Builder()
                .url(specialUsersUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        specialIcon.setVisibility(View.GONE);
                        isPremiumUser = false;
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // Проверяем, прикреплен ли еще фрагмент
                if (!isAdded()) return;

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("special_users")) {
                        JSONArray specialUsers = json.getJSONArray("special_users");
                        boolean isSpecialUser = false;

                        for (int i = 0; i < specialUsers.length(); i++) {
                            String specialUserId = specialUsers.getString(i);
                            if (specialUserId.equals(userId)) {
                                isSpecialUser = true;
                                break;
                            }
                        }

                        boolean finalIsSpecialUser = isSpecialUser;
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                isPremiumUser = finalIsSpecialUser;
                                if (finalIsSpecialUser) {
                                    specialIcon.setVisibility(View.VISIBLE);
                                    animateSpecialIcon();
                                } else {
                                    specialIcon.setVisibility(View.GONE);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    if (getActivity() != null && isAdded()) {
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            specialIcon.setVisibility(View.GONE);
                            isPremiumUser = false;
                        });
                    }
                }
            }
        });
    }

    private void animateSpecialIcon() {
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                0.8f, 1.2f, 0.8f, 1.2f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleAnimation.setDuration(500);
        scaleAnimation.setRepeatCount(1);
        scaleAnimation.setRepeatMode(Animation.REVERSE);
        specialIcon.startAnimation(scaleAnimation);
    }

    private void loadSpecialUsers() {
        if (isTestMode) {
            // В тестовом режиме добавляем тестовых специальных пользователей
            specialUsers.add("123456789"); // Тестовый блогер
            specialUsers.add("888888888"); // Важные уведомления
            isSpecialUsersLoaded = true;
            return;
        }

        Request request = new Request.Builder()
                .url("https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/special_users.json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("MessagesFragment", "Failed to load special users", e);
                isSpecialUsersLoaded = true;
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // Проверяем, прикреплен ли еще фрагмент
                if (!isAdded()) {
                    isSpecialUsersLoaded = true;
                    return;
                }

                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        parseSpecialUsers(json);
                    }
                } catch (Exception e) {
                    Log.e("MessagesFragment", "Error parsing special users", e);
                } finally {
                    isSpecialUsersLoaded = true;
                    if (getActivity() != null && isAdded()) {
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                        });
                    }
                }
            }
        });
    }

    private void parseSpecialUsers(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray usersArray = jsonObject.getJSONArray("special_users");

            specialUsers.clear();
            for (int i = 0; i < usersArray.length(); i++) {
                long userId = usersArray.getLong(i);
                specialUsers.add(String.valueOf(userId));
            }

            Log.d("MessagesFragment", "Loaded " + specialUsers.size() + " special users");
        } catch (JSONException e) {
            Log.e("MessagesFragment", "Error parsing special users JSON", e);
        }
    }

    // ИСПРАВЛЕННЫЙ метод fetchDialogs - используем только userId из TokenManager
    private void fetchDialogs(String accessToken) {
        String url = "https://api.vk.com/method/messages.getConversations" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&count=20" +
                "&extended=1" +
                "&fields=photo_100";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isAdded()) return;

                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(),
                                "Ошибка при получении диалогов: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        updateDialogCountText(0, 0);
                        hideHorizontalProgress();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // Проверяем, прикреплен ли еще фрагмент
                if (!isAdded()) return;

                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");

                            int totalCount = responseObj.optInt("count", items.length());
                            int unreadCount = responseObj.optInt("unread_count", 0);

                            JSONArray profiles = responseObj.optJSONArray("profiles");
                            Map<String, String> userNames = parseUserNames(profiles);
                            Map<String, String> userAvatars = parseUserAvatars(profiles);
                            Map<String, String> chatSettings = parseChatSettings(items);

                            // ВАЖНО: Используем ТОЛЬКО userId из TokenManager
                            String currentUserId = MessagesFragment.this.userId;

                            List<Dialog> dialogs = new ArrayList<>();
                            List<Dialog> pinnedDialogs = new ArrayList<>(); // Для закрепленных чатов

                            boolean hasSupportChat = false;
                            boolean hasAutoBot = false;

                            for (int i = 0; i < items.length(); i++) {
                                JSONObject conversationObj = items.getJSONObject(i);
                                JSONObject lastMessage = conversationObj.getJSONObject("last_message");

                                JSONObject conversation = conversationObj.getJSONObject("conversation");
                                JSONObject peer = conversation.getJSONObject("peer");
                                String peerId = peer.optString("id");
                                String peerType = peer.optString("type");

                                String text = lastMessage.optString("text");
                                long date = lastMessage.optLong("date") * 1000;

                                boolean isOut = lastMessage.optInt("out") == 1;
                                int readState = lastMessage.optInt("read_state");

                                int readStatus;
                                if (isOut) {
                                    if (readState == 1) {
                                        readStatus = Dialog.READ_STATUS_READ;
                                    } else {
                                        readStatus = Dialog.READ_STATUS_SENT;
                                    }
                                } else {
                                    readStatus = Dialog.READ_STATUS_INCOMING;
                                }

                                DialogInfo dialogInfo = determineDialogUser(peerId, peerType, currentUserId, userNames, conversation, chatSettings);
                                String avatarUrl = userAvatars.get(dialogInfo.userId);

                                Dialog dialog = new Dialog(
                                        dialogInfo.userId,
                                        dialogInfo.userName,
                                        text,
                                        date,
                                        peerId,
                                        avatarUrl
                                );
                                dialog.setReadStatus(readStatus);
                                dialog.setOutgoing(isOut);
                                dialog.setGroupChat("chat".equals(peerType));
                                dialog.setChatTitle(dialogInfo.chatTitle);

                                // Добавляем информацию о непрочитанных сообщениях в диалоге
                                if (conversation.has("unread_count")) {
                                    dialog.setUnreadCount(conversation.optInt("unread_count"));
                                }

                                // Проверяем, является ли чат архивным
                                if (archivedChats.contains(dialogInfo.userId)) {
                                    dialog.setArchived(true);
                                }

                                // Проверяем, является ли это закрепленный чат (Поддержка или Авто-бот)
                                if (pinnedChatIds.contains(dialogInfo.userId)) {
                                    if ("-71746274".equals(dialogInfo.userId)) {
                                        hasSupportChat = true;
                                        // Для чата поддержки меняем имя на "Поддержка"
                                        dialog = new Dialog(
                                                dialogInfo.userId,
                                                "Поддержка",
                                                text.isEmpty() ? "Здравствуйте! Чем могу помочь?" : text,
                                                date,
                                                peerId,
                                                avatarUrl
                                        );
                                        dialog.setReadStatus(readStatus);
                                        dialog.setOutgoing(isOut);
                                        dialog.setGroupChat("chat".equals(peerType));
                                        dialog.setChatTitle("Поддержка");
                                        if (conversation.has("unread_count")) {
                                            dialog.setUnreadCount(conversation.optInt("unread_count"));
                                        }
                                        pinnedDialogs.add(dialog);
                                    }
                                } else {
                                    // Фильтруем архивные чаты если не показываем их
                                    if (!showArchivedChats && !dialog.isArchived()) {
                                        dialogs.add(dialog);
                                    } else if (showArchivedChats && dialog.isArchived()) {
                                        dialogs.add(dialog);
                                    }
                                }
                            }

                            // Если чата поддержки нет в списке, создаем искусственный
                            if (!hasSupportChat && !showArchivedChats) {
                                Dialog supportDialog = createSupportDialog();
                                pinnedDialogs.add(supportDialog);
                            }

                            // Сохраняем все диалоги для последующей фильтрации
                            dialogList.clear();
                            dialogList.addAll(dialogs);

                            // Сначала добавляем закрепленные чаты, затем обычные
                            List<Dialog> finalDialogs = new ArrayList<>();
                            finalDialogs.addAll(pinnedDialogs);
                            finalDialogs.addAll(dialogs);

                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    adapter.setDialogs(finalDialogs);
                                    updateDialogCountText(totalCount, unreadCount);
                                    hideHorizontalProgress();

                                    if (!isSpecialUsersLoaded) {
                                        new Handler().postDelayed(() -> {
                                            if (isAdded()) {
                                                adapter.notifyDataSetChanged();
                                            }
                                        }, 1000);
                                    }
                                });
                            }
                        } else if (json.has("error")) {
                            String errorMsg = json.getJSONObject("error").optString("error_msg");
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    Toast.makeText(getContext(),
                                            "Ошибка API: " + errorMsg,
                                            Toast.LENGTH_LONG).show();
                                    updateDialogCountText(0, 0);
                                    hideHorizontalProgress();
                                });
                            }
                        }
                    } catch (JSONException e) {
                        if (getActivity() != null && isAdded()) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(),
                                        "Ошибка парсинга: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                                updateDialogCountText(0, 0);
                                hideHorizontalProgress();
                            });
                        }
                    }
                }
            }
        });
    }

    // Метод для создания искусственного чата поддержки
    private Dialog createSupportDialog() {
        Dialog supportDialog = new Dialog(
                "-71746274", // ID поддержки
                "Поддержка",
                "Здравствуйте! Чем могу помочь?", // Стандартное приветственное сообщение
                System.currentTimeMillis(), // Текущее время
                "-71746274" // peer_id
        );
        supportDialog.setReadStatus(Dialog.READ_STATUS_INCOMING);
        supportDialog.setOutgoing(false);
        supportDialog.setGroupChat(false);
        supportDialog.setChatTitle("Поддержка");
        supportDialog.setUnreadCount(0);

        return supportDialog;
    }

    // Новый метод для получения настроек чатов
    private Map<String, String> parseChatSettings(JSONArray items) {
        Map<String, String> chatSettings = new HashMap<>();
        try {
            for (int i = 0; i < items.length(); i++) {
                JSONObject conversationObj = items.getJSONObject(i);
                JSONObject conversation = conversationObj.getJSONObject("conversation");
                JSONObject peer = conversation.getJSONObject("peer");

                if ("chat".equals(peer.optString("type")) && conversation.has("chat_settings")) {
                    JSONObject chatSettingsObj = conversation.getJSONObject("chat_settings");
                    String peerId = peer.optString("id");
                    String title = chatSettingsObj.optString("title", "Безымянный чат");
                    chatSettings.put(peerId, title);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing chat settings", e);
        }
        return chatSettings;
    }

    // ИСПРАВЛЕННЫЙ метод determineDialogUser для корректного определения "Избранного"
    private DialogInfo determineDialogUser(String peerId, String peerType, String currentUserId,
                                           Map<String, String> userNames, JSONObject conversation,
                                           Map<String, String> chatSettings) {
        if ("user".equals(peerType)) {
            // Личный диалог - отображаем собеседника
            String userName = userNames.get(peerId);
            if (userName == null) {
                userName = "Пользователь " + peerId;
            }

            // ВАЖНО: Сравниваем с currentUserId (который берется из TokenManager)
            if (currentUserId != null && currentUserId.equals(peerId)) {
                return new DialogInfo(peerId, "Избранное", "Избранное");
            }

            return new DialogInfo(peerId, userName, null);
        } else if ("chat".equals(peerType)) {
            // Групповой чат - отображаем название чата
            String chatTitle = chatSettings.get(peerId);
            if (chatTitle == null && conversation.has("chat_settings")) {
                try {
                    JSONObject chatSettingsObj = conversation.getJSONObject("chat_settings");
                    chatTitle = chatSettingsObj.optString("title", "Безымянный чат");
                } catch (JSONException e) {
                    chatTitle = "Чат " + peerId;
                }
            }
            if (chatTitle == null) {
                chatTitle = "Чат " + peerId;
            }
            return new DialogInfo(peerId, chatTitle, chatTitle);
        } else {
            // Другие типы диалогов
            return new DialogInfo(peerId, "Диалог " + peerId, null);
        }
    }

    private Map<String, String> parseUserNames(JSONArray profiles) {
        Map<String, String> userNames = new HashMap<>();
        if (profiles != null) {
            for (int i = 0; i < profiles.length(); i++) {
                try {
                    JSONObject profile = profiles.getJSONObject(i);
                    String userId = profile.optString("id");
                    String firstName = profile.optString("first_name");
                    String lastName = profile.optString("last_name");
                    userNames.put(userId, firstName + " " + lastName);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return userNames;
    }

    private Map<String, String> parseUserAvatars(JSONArray profiles) {
        Map<String, String> userAvatars = new HashMap<>();
        if (profiles != null) {
            for (int i = 0; i < profiles.length(); i++) {
                try {
                    JSONObject profile = profiles.getJSONObject(i);
                    String userId = profile.optString("id");
                    String avatarUrl = profile.optString("photo_100", "");
                    userAvatars.put(userId, avatarUrl);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return userAvatars;
    }

    // Добавляем метод для переключения между тестовым и реальным режимом (для отладки)
    public void toggleTestMode() {
        if (!isAdded()) return;

        isTestMode = !isTestMode;
        if (isTestMode) {
            showTestData();
            Toast.makeText(getContext(), "Включен демо-режим", Toast.LENGTH_SHORT).show();
        } else {
            Context context = getSafeContext();
            if (context != null) {
                String accessToken = TokenManager.getInstance(context).getToken();
                if (accessToken != null) {
                    if (showArchivedChats) {
                        loadArchivedChatsWithRefresh();
                    } else {
                        fetchDialogs(accessToken);
                    }
                }
            }
            Toast.makeText(getContext(), "Включен обычный режим", Toast.LENGTH_SHORT).show();
        }
    }

    // Вспомогательный метод для безопасного получения контекста
    @Nullable
    private Context getSafeContext() {
        if (isAdded()) {
            return getContext();
        }
        return null;
    }

    // Класс для представления диалога
    public static class Dialog {
        public static final int READ_STATUS_SENT = 0;
        public static final int READ_STATUS_READ = 1;
        public static final int READ_STATUS_INCOMING = 2;

        private String userId;
        private String userName;
        private String lastMessage;
        private long date;
        private String peerId;
        private int readStatus;
        private boolean isOutgoing;
        private int unreadCount;
        private String avatarUrl;
        private boolean isGroupChat;
        private String chatTitle;
        private boolean isArchived; // Новое поле для архивации

        public Dialog(String userId, String userName, String lastMessage, long date, String peerId) {
            this.userId = userId;
            this.userName = userName;
            this.lastMessage = lastMessage;
            this.date = date;
            this.peerId = peerId;
            this.unreadCount = 0;
            this.isGroupChat = false;
            this.isArchived = false;
        }

        // Конструктор с аватаркой
        public Dialog(String userId, String userName, String lastMessage, long date, String peerId, String avatarUrl) {
            this(userId, userName, lastMessage, date, peerId);
            this.avatarUrl = avatarUrl;
        }

        // Геттеры
        public String getUserId() { return userId; }
        public String getUserName() { return userName; }
        public String getLastMessage() { return lastMessage; }
        public long getDate() { return date; }
        public String getPeerId() { return peerId; }
        public int getReadStatus() { return readStatus; }
        public boolean isOutgoing() { return isOutgoing; }
        public int getUnreadCount() { return unreadCount; }
        public String getAvatarUrl() { return avatarUrl; }
        public boolean isGroupChat() { return isGroupChat; }
        public String getChatTitle() { return chatTitle; }
        public boolean isArchived() { return isArchived; } // Новый геттер

        // Сеттеры
        public void setReadStatus(int readStatus) { this.readStatus = readStatus; }
        public void setOutgoing(boolean outgoing) { isOutgoing = outgoing; }
        public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public void setGroupChat(boolean groupChat) { isGroupChat = groupChat; }
        public void setChatTitle(String chatTitle) { this.chatTitle = chatTitle; }
        public void setArchived(boolean archived) { isArchived = archived; } // Новый сеттер
    }

    // Обновленный класс DialogInfo
    private static class DialogInfo {
        String userId;
        String userName;
        String chatTitle;

        DialogInfo(String userId, String userName, String chatTitle) {
            this.userId = userId;
            this.userName = userName;
            this.chatTitle = chatTitle;
        }
    }

    // Класс для представления друга
    public static class Friend {
        private String id;
        private String name;
        private String photoUrl;
        private boolean isOnline;
        private boolean isSelected;

        public Friend(String id, String name, String photoUrl, boolean isOnline) {
            this.id = id;
            this.name = name;
            this.photoUrl = photoUrl;
            this.isOnline = isOnline;
            this.isSelected = false;
        }

        // Геттеры и сеттеры
        public String getId() { return id; }
        public String getName() { return name; }
        public String getPhotoUrl() { return photoUrl; }
        public boolean isOnline() { return isOnline; }
        public boolean isSelected() { return isSelected; }
        public void setSelected(boolean selected) { isSelected = selected; }
    }

    // Адаптер для списка друзей
    public static class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.FriendViewHolder> {

        private List<Friend> friends;

        public FriendsAdapter(List<Friend> friends) {
            this.friends = friends;
        }

        @NonNull
        @Override
        public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friend_selected, parent, false);
            return new FriendViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
            holder.bind(friends.get(position));
        }

        @Override
        public int getItemCount() {
            return friends.size();
        }

        // Получить список выбранных друзей
        public List<String> getSelectedFriends() {
            List<String> selected = new ArrayList<>();
            for (Friend friend : friends) {
                if (friend.isSelected()) {
                    selected.add(friend.getId());
                }
            }
            return selected;
        }

        class FriendViewHolder extends RecyclerView.ViewHolder {
            private CheckBox checkBox;
            private TextView textName;
            private ImageView avatar;
            private View onlineIndicator;
            private Random random = new Random();

            public FriendViewHolder(@NonNull View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.checkBoxFriend);
                textName = itemView.findViewById(R.id.textFriendName);
                avatar = itemView.findViewById(R.id.imageFriendAvatar);
                onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
            }

            void bind(Friend friend) {
                textName.setText(friend.getName());
                checkBox.setChecked(friend.isSelected());

                // Загрузка аватарки с круглой обрезкой
                if (friend.getPhotoUrl() != null && !friend.getPhotoUrl().isEmpty()) {
                    Picasso.get()
                            .load(friend.getPhotoUrl())
                            .placeholder(createPlaceholder(friend.getName()))
                            .error(createPlaceholder(friend.getName()))
                            .resize(100, 100)
                            .centerCrop()
                            .transform(new CircleTransform())
                            .into(avatar);
                } else {
                    // Если URL нет, показываем текстовый аватар круглой формы
                    showTextAvatar(friend.getName());
                }

                // Показать индикатор онлайн статуса
                onlineIndicator.setVisibility(friend.isOnline() ? View.VISIBLE : View.GONE);

                // Обработчик выбора
                itemView.setOnClickListener(v -> {
                    friend.setSelected(!friend.isSelected());
                    checkBox.setChecked(friend.isSelected());
                });

                checkBox.setOnClickListener(v -> {
                    friend.setSelected(checkBox.isChecked());
                });
            }

            private Drawable createPlaceholder(String userName) {
                String firstLetter = getFirstLetter(userName);
                int color = getRandomColor();

                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(color);

                return drawable;
            }

            private void showTextAvatar(String userName) {
                String firstLetter = getFirstLetter(userName);

                // Создаем текстовый аватар круглой формы
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(getRandomColor());
                avatar.setBackground(drawable);

                // Для ImageView устанавливаем масштабирование
                avatar.setScaleType(ImageView.ScaleType.CENTER);

                // Устанавливаем первую букву имени как contentDescription
                avatar.setContentDescription(firstLetter);

                // Очищаем предыдущее изображение
                avatar.setImageDrawable(null);
            }

            private String getFirstLetter(String name) {
                if (!TextUtils.isEmpty(name)) {
                    String[] nameParts = name.split(" ");
                    if (nameParts.length > 0) {
                        return nameParts[0].substring(0, 1).toUpperCase();
                    }
                    return name.substring(0, 1).toUpperCase();
                }
                return "?";
            }

            private int getRandomColor() {
                int[] colors = {
                        Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
                        Color.parseColor("#45B7D1"), Color.parseColor("#F9A826"),
                        Color.parseColor("#6A5ACD"), Color.parseColor("#FFA07A"),
                        Color.parseColor("#20B2AA"), Color.parseColor("#9370DB"),
                        Color.parseColor("#3CB371"), Color.parseColor("#FF4500")
                };
                return colors[random.nextInt(colors.length)];
            }
        }
    }

    // Обновленный класс MessagesAdapter
    public static class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.DialogViewHolder> {

        private List<Dialog> dialogs;
        private Random random = new Random();
        private SpecialUserChecker specialUserChecker;
        private OnItemClickListener onItemClickListener;
        private OnItemLongClickListener onItemLongClickListener;
        private Context context;
        private String currentUserId;

        public interface SpecialUserChecker {
            boolean isSpecialUser(String userId);
            boolean isArchived(String userId);
        }

        public interface OnItemClickListener {
            void onItemClick(Dialog dialog);
        }

        public interface OnItemLongClickListener {
            void onItemLongClick(Dialog dialog);
        }

        // Конструктор с Context и currentUserId
        public MessagesAdapter(List<Dialog> dialogs, SpecialUserChecker specialUserChecker, Context context, String currentUserId) {
            this.dialogs = dialogs;
            this.specialUserChecker = specialUserChecker;
            this.context = context;
            this.currentUserId = currentUserId;
        }

        public void setDialogs(List<Dialog> dialogs) {
            this.dialogs = dialogs;
            notifyDataSetChanged();
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            this.onItemClickListener = listener;
        }

        public void setOnItemLongClickListener(OnItemLongClickListener listener) {
            this.onItemLongClickListener = listener;
        }

        @NonNull
        @Override
        public DialogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_new, parent, false);
            return new DialogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DialogViewHolder holder, int position) {
            Dialog dialog = dialogs.get(position);
            holder.bind(dialog, specialUserChecker, onItemClickListener, onItemLongClickListener, context, currentUserId);
        }

        @Override
        public int getItemCount() {
            return dialogs.size();
        }

        class DialogViewHolder extends RecyclerView.ViewHolder {
            TextView textSender;
            TextView textBody;
            TextView textDate;
            ImageView avatarImageView;
            TextView avatarTextView;
            ImageView verifiedIcon;
            ImageView readStatusIcon;
            ImageView importantIcon;
            ImageView messageTypeIcon;
            ImageView groupChatIcon;
            ImageView archiveIcon; // Новая иконка архива
            TextView unreadBadge;
            ProgressBar loadingProgressBar;
            private Random random = new Random();

            public DialogViewHolder(@NonNull View itemView) {
                super(itemView);

                textSender = itemView.findViewById(R.id.textSender);
                textBody = itemView.findViewById(R.id.textBody);
                textDate = itemView.findViewById(R.id.textDate);
                avatarImageView = itemView.findViewById(R.id.avatarImageView);
                avatarTextView = itemView.findViewById(R.id.avatarTextView);
                verifiedIcon = itemView.findViewById(R.id.verified_icon);
                readStatusIcon = itemView.findViewById(R.id.readStatusIcon);
                importantIcon = itemView.findViewById(R.id.importantIcon);
                messageTypeIcon = itemView.findViewById(R.id.messageTypeIcon);
                groupChatIcon = itemView.findViewById(R.id.groupChatIcon);
                archiveIcon = itemView.findViewById(R.id.archiveIcon); // Новая иконка
                unreadBadge = itemView.findViewById(R.id.unreadBadge);
                loadingProgressBar = itemView.findViewById(R.id.loadingProgressBar);
            }

            void bind(Dialog dialog, SpecialUserChecker specialUserChecker,
                      OnItemClickListener listener, OnItemLongClickListener longClickListener,
                      Context context, String currentUserId) {
                // Сбрасываем состояние ProgressBar
                loadingProgressBar.setVisibility(View.GONE);
                readStatusIcon.setVisibility(View.VISIBLE);

                // Устанавливаем данные
                textSender.setText(dialog.getUserName());
                textBody.setText(getDisplayText(dialog, currentUserId));
                textDate.setText(formatDate(dialog.getDate()));

                // Загружаем аватарку с помощью Picasso
                loadAvatar(dialog, context);

                // Настраиваем бейдж непрочитанных
                setupUnreadBadge(dialog);

                // Настраиваем статус прочтения
                setupReadStatus(dialog);

                // Настраиваем иконки
                setupIcons(dialog, specialUserChecker, currentUserId);

                // Обработчик клика с индикатором загрузки
                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        showLoadingIndicator();
                        new Handler().postDelayed(() -> {
                            listener.onItemClick(dialog);
                            hideLoadingIndicator();
                        }, 300);
                    }
                });

                // Обработчик долгого нажатия для архивации/удаления
                itemView.setOnLongClickListener(v -> {
                    if (longClickListener != null) {
                        longClickListener.onItemLongClick(dialog);
                        return true;
                    }
                    return false;
                });
            }

            private void showLoadingIndicator() {
                loadingProgressBar.setVisibility(View.VISIBLE);
                readStatusIcon.setVisibility(View.GONE);
                itemView.setEnabled(false);
            }

            private void hideLoadingIndicator() {
                loadingProgressBar.setVisibility(View.GONE);
                readStatusIcon.setVisibility(View.VISIBLE);
                itemView.setEnabled(true);
            }

            private String getDisplayText(Dialog dialog, String currentUserId) {
                String lastMessage = dialog.getLastMessage();

                if (lastMessage == null || lastMessage.isEmpty()) {
                    return checkForAttachments(dialog);
                }

                if (dialog.isOutgoing()) {
                    return "Вы: " + lastMessage;
                }

                return lastMessage;
            }

            private String checkForAttachments(Dialog dialog) {
                String attachmentText = "📎 Вложение";
                if (dialog.isOutgoing()) {
                    return "Вы: " + attachmentText;
                }
                return attachmentText;
            }

            private void loadAvatar(Dialog dialog, Context context) {
                String avatarUrl = dialog.getAvatarUrl();

                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    avatarImageView.setVisibility(View.VISIBLE);
                    avatarTextView.setVisibility(View.GONE);

                    Picasso.get()
                            .load(avatarUrl)
                            .placeholder(createPlaceholder(dialog.getUserName()))
                            .error(createPlaceholder(dialog.getUserName()))
                            .resize(100, 100)
                            .centerCrop()
                            .transform(new CircleTransform())
                            .into(avatarImageView);
                } else {
                    showTextAvatar(dialog.getUserName());
                }
            }

            private Drawable createPlaceholder(String userName) {
                String firstLetter = getFirstLetter(userName);
                int color = getRandomColor();

                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(color);

                return drawable;
            }

            private void showTextAvatar(String userName) {
                avatarImageView.setVisibility(View.GONE);
                avatarTextView.setVisibility(View.VISIBLE);

                String firstLetter = getFirstLetter(userName);
                avatarTextView.setText(firstLetter);

                int color = getRandomColor();
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(color);
                avatarTextView.setBackground(drawable);
            }

            private void setupUnreadBadge(Dialog dialog) {
                if (unreadBadge != null) {
                    if (dialog.getUnreadCount() > 0) {
                        unreadBadge.setVisibility(View.VISIBLE);
                        unreadBadge.setText(String.valueOf(dialog.getUnreadCount()));
                    } else {
                        unreadBadge.setVisibility(View.GONE);
                    }
                }
            }

            private void setupReadStatus(Dialog dialog) {
                if (readStatusIcon != null) {
                    switch (dialog.getReadStatus()) {
                        case Dialog.READ_STATUS_SENT:
                            readStatusIcon.setImageResource(R.drawable.ic_check_read);
                            readStatusIcon.setVisibility(View.VISIBLE);
                            readStatusIcon.setColorFilter(Color.BLUE);
                            break;
                        case Dialog.READ_STATUS_READ:
                            readStatusIcon.setImageResource(R.drawable.ic_check_sent);
                            readStatusIcon.setVisibility(View.VISIBLE);
                            readStatusIcon.setColorFilter(Color.parseColor("#4CAF50"));
                            break;
                        case Dialog.READ_STATUS_INCOMING:
                        default:
                            readStatusIcon.setVisibility(View.GONE);
                            break;
                    }
                }
            }

            private void setupIcons(Dialog dialog, SpecialUserChecker specialUserChecker, String currentUserId) {
                // Иконка архивного чата
                if (archiveIcon != null) {
                    if (dialog.isArchived()) {
                        archiveIcon.setVisibility(View.VISIBLE);
                        archiveIcon.setImageResource(R.drawable.ic_archive);
                        archiveIcon.setColorFilter(Color.GRAY);
                        archiveIcon.setContentDescription("Архивный чат");
                    } else {
                        archiveIcon.setVisibility(View.GONE);
                    }
                }

                // Иконка группового чата
                if (groupChatIcon != null) {
                    if (dialog.isGroupChat()) {
                        groupChatIcon.setVisibility(View.GONE);
                        groupChatIcon.setImageResource(R.drawable.circle_chat);
                        groupChatIcon.setContentDescription("Групповой чат");
                    } else {
                        groupChatIcon.setVisibility(View.GONE);
                    }
                }

                // Иконка поддержки, избранного и авто-бота
                if (importantIcon != null) {
                    boolean isSupportChat = "-71746274".equals(dialog.getUserId()) ||
                            "Поддержка".equals(dialog.getUserName());

                    boolean isFavoriteChat = "Избранное".equals(dialog.getUserName()) ||
                            (currentUserId != null && currentUserId.equals(dialog.getUserId()));

                    boolean isAutoBot = "-999999999".equals(dialog.getUserId()) ||
                            "Авто-бот".equals(dialog.getUserName());

                    if (isSupportChat) {
                        importantIcon.setVisibility(View.VISIBLE);
                        importantIcon.setImageResource(R.drawable.circle_help);
                        importantIcon.setContentDescription("Поддержка");
                    } else if (isAutoBot) {
                        importantIcon.setVisibility(View.VISIBLE);
                        importantIcon.setImageResource(R.drawable.circle_robot); // Добавьте эту иконку в ресурсы
                        importantIcon.setContentDescription("Чат-бот");
                    } else {
                        importantIcon.setVisibility(View.GONE);
                    }
                }

                // Тип сообщения
                if (messageTypeIcon != null) {
                    messageTypeIcon.setVisibility(View.GONE);
                }

                // Галочка verified
                if (verifiedIcon != null) {
                    if (specialUserChecker != null && specialUserChecker.isSpecialUser(dialog.getUserId())) {
                        verifiedIcon.setVisibility(View.VISIBLE);
                        verifiedIcon.setImageResource(R.drawable.circle_shufle);
                    } else {
                        verifiedIcon.setVisibility(View.GONE);
                    }
                }
            }

            private String getFirstLetter(String name) {
                if (!TextUtils.isEmpty(name)) {
                    String[] nameParts = name.split(" ");
                    if (nameParts.length > 0) {
                        return nameParts[0].substring(0, 1).toUpperCase();
                    }
                    return name.substring(0, 1).toUpperCase();
                }
                return "?";
            }

            private int getRandomColor() {
                int[] colors = {
                        Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
                        Color.parseColor("#45B7D1"), Color.parseColor("#F9A826"),
                        Color.parseColor("#6A5ACD"), Color.parseColor("#FFA07A"),
                        Color.parseColor("#20B2AA"), Color.parseColor("#9370DB"),
                        Color.parseColor("#3CB371"), Color.parseColor("#FF4500")
                };
                return colors[random.nextInt(colors.length)];
            }

            private String formatDate(long timestamp) {
                Date date = new Date(timestamp);
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return sdf.format(date);
            }
        }
    }
}