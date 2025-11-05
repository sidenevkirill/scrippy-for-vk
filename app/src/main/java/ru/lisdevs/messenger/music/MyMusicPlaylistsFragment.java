package ru.lisdevs.messenger.music;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.BaseActivity;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.local.SaveMusicFragment;
import ru.lisdevs.messenger.lyrics.LyricsBottomSheet;
import ru.lisdevs.messenger.model.Audio;
import ru.lisdevs.messenger.player.PlayerBottomSheetFragment;
import ru.lisdevs.messenger.playlists.VkPlaylistsFragment;
import ru.lisdevs.messenger.search.MusicSearchFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;


public class MyMusicPlaylistsFragment extends Fragment {

    private RecyclerView recyclerView;
    private AudioAdapter adapter;
    private List<Audio> audioList = new ArrayList<>();
    private List<Audio> fullAudioList = new ArrayList<>();
    private List<Friend> availableFriends = new ArrayList<>();

    // Пагинация
    private int currentOffset = 0;
    private static final int PAGE_SIZE = 200;
    private static final int VISIBLE_THRESHOLD = 5;
    private boolean isLoading = false;
    private boolean hasMoreItems = true;
    private int totalCount = 0;

    private TextView textViewResult;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Toolbar toolbar;
    private ProgressBar progressBar;
    private OkHttpClient okHttpClient;
    private static final String MOOSIC_FOLDER = "moosic";

    // Поиск
    private MenuItem searchMenuItem;
    private SearchView searchView;
    private boolean isSearchMode = false;

    // Spinner
    private Spinner toolbarSpinner;
    private ArrayAdapter<String> spinnerAdapter;
    private final String[] spinnerItems = {"Мои треки", "Рекомендации"};
    private boolean isRecommendationsMode = false;

    // Блокировка рекламы
    private Set<Long> blockedUserIds = new HashSet<>();
    private static final String BLOCKED_USERS_BIN_ID = "68998155ae596e708fc73def";
    private static final String JSONBIN_API_KEY = "$2a$10$47Va7lQp9sRxQH9c0Z6Hou3Zc7wZ57pDwaOXsWmCXOAmeIzIJDdf2";
    private static final long SYNC_INTERVAL = TimeUnit.HOURS.toMillis(24);
    private static final String PREFS_NAME = "MyPrefs";

    // Жанры
    private final Map<String, Integer> genres = new LinkedHashMap<String, Integer>() {{
        put("Все жанры", 0);
        put("Рок", 1);
        put("Поп", 2);
        put("Рэп & хип-хоп", 3);
        put("Легкое прослушивание", 4);
        put("Танец & хаус", 5);
        put("Инструментальная", 6);
        put("Металл", 7);
        put("Дабстеп", 8);
        put("Джаз & блюз", 9);
        put("Драм & бас", 10);
        put("Транс", 11);
        put("Шансон", 12);
        put("Этническая", 13);
        put("Акустика & вокал", 14);
        put("Регги", 15);
        put("Классическая", 16);
        put("Инди-поп", 17);
        put("Другая", 18);
        put("Разговорная", 19);
        put("Альтернативная", 21);
        put("Электропop & дискотека", 22);
    }};

    private int currentGenreId = 0;

    // Плейлисты
    private RecyclerView playlistsRecyclerView;
    private PlaylistsAdapter playlistsAdapter;
    private List<Playlist> playlistList = new ArrayList<>();
    private ProgressBar playlistsProgressBar;
    private TextView playlistsTitle;
    private SwipeRefreshLayout refreshPlaylistsButton;
    private Context context;

    public static MyMusicPlaylistsFragment newInstance() {
        MyMusicPlaylistsFragment fragment = new MyMusicPlaylistsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    // Класс для простого представления друга
    public static class Friend {
        private long id;
        private String name;
        private String photoUrl;

        public Friend(long id, String name, String photoUrl) {
            this.id = id;
            this.name = name;
            this.photoUrl = photoUrl;
        }

        public long getId() { return id; }
        public String getName() { return name; }
        public String getPhotoUrl() { return photoUrl; }
    }

    // Чаты для отправки аудио
    private List<SimpleChat> availableChats = new ArrayList<>();

    // Класс для простого представления чата
    public static class SimpleChat {
        private long peerId;
        private String title;
        private String photoUrl;
        private String type;

        public SimpleChat(long peerId, String title, String photoUrl, String type) {
            this.peerId = peerId;
            this.title = title;
            this.photoUrl = photoUrl;
            this.type = type;
        }

        public long getPeerId() { return peerId; }
        public String getTitle() { return title; }
        public String getPhotoUrl() { return photoUrl; }
        public String getType() { return type; }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        okHttpClient = new OkHttpClient();

        // Загружаем чаты и друзей с небольшой задержкой чтобы UI успел инициализироваться
        new Handler().postDelayed(() -> {
            loadAvailableChats();
            loadAvailableFriends();
        }, 1000);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (okHttpClient != null) {
            okHttpClient.dispatcher().cancelAll();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends_music, container, false);

        initViews(view);
        setupRecyclerView();
        setupToolbar();

        refreshAudioList();
        getAudioCount();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Дополнительная загрузка чатов и друзей при создании view
        loadAvailableChats();
        loadAvailableFriends();
    }

    private void initViews(View view) {
        textViewResult = view.findViewById(R.id.count);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        toolbar = view.findViewById(R.id.toolbar);
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);

        playlistsProgressBar = view.findViewById(R.id.playlistsProgressBar);
        refreshPlaylistsButton = view.findViewById(R.id.swipeRefreshLayout);

        swipeRefreshLayout.setOnRefreshListener(this::refreshAudioList);

        ImageView shuffleButton = view.findViewById(R.id.shuffle);
        shuffleButton.setOnClickListener(v -> shuffleAudioList());

        ImageView sortButton = view.findViewById(R.id.sort);
        sortButton.setOnClickListener(v -> showSortDialog());

        ImageView shufflelPlayButton = view.findViewById(R.id.add_to_playlist);
        shufflelPlayButton.setOnClickListener(v -> shufflePlayAudioList());
    }

    private void setupToolbar() {
        // Добавляем проверку на null
        if (toolbar == null) {
            return;
        }

        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        toolbar.inflateMenu(R.menu.main_menu);

        searchMenuItem = toolbar.getMenu().findItem(R.id.action_search);
        searchView = (SearchView) searchMenuItem.getActionView();
        setupSearchView();

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_search) {
                return true;
            } else if (item.getItemId() == R.id.action_show_friends) {
                navigateToFriends();
                return true;
            } else if (item.getItemId() == R.id.action_show_save) {
                navigateToSave();
                return true;
            }
            return false;
        });
    }

    // Метод для определения User-Agent
    private String getUserAgent() {
        if (isAuthViaAuthActivity()) {
            return "VKAndroidApp/1.0";
        } else {
            try {
                return Authorizer.getKateUserAgent();
            } catch (Exception e) {
                // Fallback на стандартный User-Agent
                return "VKAndroidApp/1.0";
            }
        }
    }

    // Метод для определения типа авторизации
    private boolean isAuthViaAuthActivity() {
        // Проверка через SharedPreferences
        SharedPreferences prefs = requireContext().getSharedPreferences("auth_prefs", Context.MODE_PRIVATE);
        String authType = prefs.getString("auth_type", null);

        if (authType != null) {
            return "AuthActivity".equals(authType);
        }

        // По умолчанию возвращаем true для совместимости
        return true;
    }

    // Метод для загрузки списка друзей
    private void loadAvailableFriends() {
        if (!isAdded() || getContext() == null) {
            return;
        }

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            return;
        }

        String url = "https://api.vk.com/method/friends.get" +
                "?access_token=" + accessToken +
                "&fields=first_name,last_name,photo_100" +
                "&count=100" +
                "&v=5.199";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Ошибка загрузки друзей", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        String errorMsg = error.optString("error_msg", "Unknown error");
                        return;
                    }

                    if (json.has("response")) {
                        JSONObject responseObj = json.getJSONObject("response");
                        JSONArray items = responseObj.getJSONArray("items");
                        List<Friend> friends = new ArrayList<>();

                        for (int i = 0; i < items.length(); i++) {
                            JSONObject friendJson = items.getJSONObject(i);
                            long id = friendJson.getLong("id");
                            String firstName = friendJson.optString("first_name", "");
                            String lastName = friendJson.optString("last_name", "");
                            String photoUrl = friendJson.optString("photo_100", "");

                            String fullName = firstName + " " + lastName;

                            Friend friend = new Friend(id, fullName, photoUrl);
                            friends.add(friend);
                        }

                        // Обновляем список в UI потоке
                        requireActivity().runOnUiThread(() -> {
                            if (isAdded()) {
                                availableFriends.clear();
                                availableFriends.addAll(friends);
                            }
                        });
                    }
                } catch (Exception e) {
                    // Логируем ошибку если нужно
                }
            }
        });
    }

    private void shareAudioToFriends(ru.lisdevs.messenger.official.audios.Audio audio) {
        if (availableFriends.isEmpty()) {
            Toast.makeText(context, "Загрузка списка друзей...", Toast.LENGTH_SHORT).show();
            loadAvailableFriends();

            // Показываем диалог после небольшой задержки
            new Handler().postDelayed(() -> {
                if (!availableFriends.isEmpty()) {
                    showFriendsSelectionDialog(audio);
                } else {
                    Toast.makeText(context, "Не удалось загрузить список друзей", Toast.LENGTH_SHORT).show();
                }
            }, 1500);
            return;
        }

        showFriendsSelectionDialog(audio);
    }

    // Метод для показа диалога выбора друга
    private void showFriendsSelectionDialog(ru.lisdevs.messenger.official.audios.Audio audio) {
        if (availableFriends.isEmpty()) {
            Toast.makeText(context, "Список друзей пуст", Toast.LENGTH_SHORT).show();
            return;
        }

        // Создаем массив имен друзей
        String[] friendNames = new String[availableFriends.size()];
        for (int i = 0; i < availableFriends.size(); i++) {
            friendNames[i] = availableFriends.get(i).getName();
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Отправить аудио другу")
                .setItems(friendNames, (dialog, which) -> {
                    Friend selectedFriend = availableFriends.get(which);
                    sendAudioToFriend(selectedFriend, audio);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void sendAudioToFriend(Friend friend, ru.lisdevs.messenger.official.audios.Audio audio) {
        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null) {
            Toast.makeText(context, "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            return;
        }

        String messageText = "Аудиозапись: " + audio.getArtist() + " - " + audio.getTitle() +
                "\nСсылка: " + audio.getUrl();

        try {
            String url = "https://api.vk.com/method/messages.send" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&user_id=" + friend.getId() +
                    "&message=" + URLEncoder.encode(messageText, "UTF-8") +
                    "&random_id=" + System.currentTimeMillis();

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", getUserAgent())
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (context != null) {
                        ((Activity) context).runOnUiThread(() ->
                                Toast.makeText(context, "Ошибка отправки: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show());
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (context != null) {
                        ((Activity) context).runOnUiThread(() -> {
                            try {
                                String responseBody = response.body().string();
                                JSONObject json = new JSONObject(responseBody);

                                if (json.has("response")) {
                                    Toast.makeText(context, "✅ Аудио отправлено другу: " + friend.getName(),
                                            Toast.LENGTH_SHORT).show();
                                } else if (json.has("error")) {
                                    JSONObject error = json.getJSONObject("error");
                                    String errorMsg = error.optString("error_msg", "Неизвестная ошибка");
                                    Toast.makeText(context, "❌ Ошибка: " + errorMsg,
                                            Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Toast.makeText(context, "✅ Аудио отправлено другу: " + friend.getName(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(context, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Метод для загрузки доступных чатов
    private void loadAvailableChats() {
        if (!isAdded() || getContext() == null) {
            return;
        }

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            return;
        }

        String url = "https://api.vk.com/method/messages.getConversations" +
                "?access_token=" + accessToken +
                "&extended=1" +
                "&fields=first_name,last_name,photo_100,name" +
                "&count=50" +
                "&v=5.199";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Ошибка загрузки диалогов", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        String errorMsg = error.optString("error_msg", "Unknown error");
                        return;
                    }

                    if (json.has("response")) {
                        JSONObject responseObj = json.getJSONObject("response");
                        JSONArray items = responseObj.getJSONArray("items");
                        List<SimpleChat> chats = new ArrayList<>();

                        // Получаем профили и группы для информации о собеседниках
                        JSONArray profiles = responseObj.optJSONArray("profiles");
                        JSONArray groups = responseObj.optJSONArray("groups");

                        for (int i = 0; i < items.length(); i++) {
                            JSONObject conversation = items.getJSONObject(i);
                            JSONObject convObj = conversation.getJSONObject("conversation");
                            JSONObject peerObj = convObj.getJSONObject("peer");

                            long peerId = peerObj.getLong("id");
                            String type = peerObj.getString("type");
                            String title = "";
                            String photoUrl = "";

                            // Получаем информацию о собеседнике
                            if ("user".equals(type)) {
                                // Личная переписка
                                if (profiles != null) {
                                    for (int j = 0; j < profiles.length(); j++) {
                                        JSONObject profile = profiles.getJSONObject(j);
                                        if (profile.getLong("id") == peerId) {
                                            String firstName = profile.optString("first_name", "");
                                            String lastName = profile.optString("last_name", "");
                                            title = firstName + " " + lastName;
                                            photoUrl = profile.optString("photo_100", "");
                                            break;
                                        }
                                    }
                                }
                                // Если не нашли в profiles, используем ID как fallback
                                if (title.isEmpty()) {
                                    title = "Пользователь " + peerId;
                                }
                            } else if ("chat".equals(type)) {
                                // Групповой чат
                                title = convObj.getJSONObject("chat_settings").optString("title", "Беседа");
                                photoUrl = convObj.getJSONObject("chat_settings").optString("photo_100", "");
                            } else if ("group".equals(type)) {
                                // Сообщество
                                if (groups != null) {
                                    for (int j = 0; j < groups.length(); j++) {
                                        JSONObject group = groups.getJSONObject(j);
                                        if (group.getLong("id") == Math.abs(peerId)) {
                                            title = group.optString("name", "Сообщество");
                                            photoUrl = group.optString("photo_100", "");
                                            break;
                                        }
                                    }
                                }
                                // Если не нашли в groups, используем fallback
                                if (title.isEmpty()) {
                                    title = "Сообщество " + Math.abs(peerId);
                                }
                            } else {
                                // Для неизвестных типов
                                title = "Чат " + peerId;
                            }

                            SimpleChat chat = new SimpleChat(peerId, title, photoUrl, type);
                            chats.add(chat);
                        }

                        // Обновляем список в UI потоке
                        requireActivity().runOnUiThread(() -> {
                            if (isAdded()) {
                                availableChats.clear();
                                availableChats.addAll(chats);
                            }
                        });
                    }
                } catch (Exception e) {
                    // Логируем ошибку если нужно
                }
            }
        });
    }

    private void setupSpinner() {
        toolbarSpinner = toolbar.findViewById(R.id.toolbar_spinner);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                requireContext(),
                R.layout.simple_spinner_item,
                new String[]{"Аудиозаписи", "Рекомендации"}
        ) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) {
                    TextView textView = (TextView) view;
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
                    textView.setTextSize(18);
                    textView.setTypeface(textView.getTypeface(), Typeface.NORMAL);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        textView.setPaddingRelative(0, 0, 0, 0);
                    } else {
                        textView.setPadding(30, 5, 30, 5);
                    }
                }
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                if (view instanceof TextView) {
                    TextView textView = (TextView) view;
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
                    textView.setTextSize(18);
                    textView.setPadding(30, 0, 30, 0);
                }
                return view;
            }
        };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        toolbarSpinner.setAdapter(adapter);
        toolbarSpinner.setMinimumWidth(180);

        toolbarSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    showMyTracks();
                } else {
                    showRecommendations();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void onSpinnerItemSelected(String item, int position) {
        switch (position) {
            case 0:
                showMyTracks();
                break;
            case 1:
                showRecommendations();
                break;
        }
    }

    private void showMyTracks() {
        isRecommendationsMode = false;
        refreshAudioList();
    }

    private void showRecommendations() {
        isRecommendationsMode = true;
        loadRecommendations();
    }

    private void loadRecommendations() {
        swipeRefreshLayout.setRefreshing(true);
        String accessToken = TokenManager.getInstance(getContext()).getToken();

        if (accessToken != null) {
            fetchRecAudio(accessToken);
        } else {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void fetchRecAudio(String accessToken) {
        swipeRefreshLayout.setRefreshing(true);

        String url = "https://api.vk.com/method/audio.getRecommendations?access_token=" + accessToken + "&v=5.131";

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", getUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            swipeRefreshLayout.setRefreshing(false);
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            String body = response.body().string();
                            JSONObject json = new JSONObject(body);
                            if (json.has("response")) {
                                JSONArray items = json.getJSONObject("response").getJSONArray("items");
                                List<Audio> newList = parseAudioItems(items);
                                requireActivity().runOnUiThread(() -> {
                                    updateAudioList(newList, true);
                                    swipeRefreshLayout.setRefreshing(false);
                                });
                            } else if (json.has("error")) {
                                showApiError(json.getJSONObject("error"));
                                requireActivity().runOnUiThread(() -> {
                                    swipeRefreshLayout.setRefreshing(false);
                                });
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_LONG).show();
                                swipeRefreshLayout.setRefreshing(false);
                            });
                        }
                    }
                });
    }

    private void updateAudioList(List<Audio> newList, boolean isRecommendations) {
        fullAudioList.clear();
        audioList.clear();

        fullAudioList.addAll(newList);
        audioList.addAll(newList);

        adapter.notifyDataSetChanged();

        if (isRecommendations) {
            updateCountText("Треки: " + newList.size());
        } else {
            updateCountText("Треки: " + newList.size());
        }
    }

    private void setupSearchView() {
        if (searchView == null) return;

        searchView.setQueryHint("Поиск по трекам");
        searchView.setIconifiedByDefault(true);
        searchView.setIconified(true);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterTracks(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    resetSearch();
                } else {
                    filterTracks(newText);
                }
                return true;
            }
        });

        searchView.setOnCloseListener(() -> {
            resetSearch();
            isSearchMode = false;
            return false;
        });

        searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                isSearchMode = true;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                resetSearch();
                isSearchMode = false;
                return true;
            }
        });

        searchView.setMaxWidth(Integer.MAX_VALUE);

        ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_button);
        if (searchIcon != null) {
            searchIcon.setImageResource(R.drawable.ic_search);
        }

        EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (searchEditText != null) {
            searchEditText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
            searchEditText.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
        }
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AudioAdapter(audioList);
        recyclerView.setAdapter(adapter);

        // Добавляем слушатель прокрутки для пагинации
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;

                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                // Проверяем, нужно ли загружать еще данные
                if (!isLoading && hasMoreItems && !isSearchMode && !isRecommendationsMode &&
                        (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - VISIBLE_THRESHOLD &&
                        firstVisibleItemPosition >= 0) {
                    loadMoreAudio();
                }
            }
        });

        adapter.setOnItemClickListener(position -> {
            if (position >= 0 && position < audioList.size()) {
                playTrack(position);
            }
        });

        adapter.setOnMenuClickListener(this::showBottomSheet);
    }

    private void loadMoreAudio() {
        if (isLoading || !hasMoreItems || isSearchMode || isRecommendationsMode) {
            return;
        }

        isLoading = true;

        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken != null) {
            fetchAudio(accessToken, currentOffset, true);
        } else {
            isLoading = false;
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshAudioList() {
        if (isRecommendationsMode) {
            loadRecommendations();
            return;
        }

        currentOffset = 0;
        hasMoreItems = true;
        isLoading = true;
        swipeRefreshLayout.setRefreshing(true);

        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken != null) {
            fetchAudio(accessToken, 0, false);
        } else {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
            isLoading = false;
        }
    }

    private void fetchAudio(String accessToken, int offset, boolean isLoadMore) {
        String url = "https://api.vk.com/method/audio.get" +
                "?access_token=" + accessToken +
                "&offset=" + offset +
                "&count=" + PAGE_SIZE +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    isLoading = false;
                    swipeRefreshLayout.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
//                    Toast.makeText(getContext(), "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);

                    if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            swipeRefreshLayout.setRefreshing(false);
                            progressBar.setVisibility(View.GONE);
                            showApiError(error);
                        });
                        return;
                    }

                    if (json.has("response")) {
                        JSONObject responseObj = json.getJSONObject("response");
                        JSONArray items = responseObj.getJSONArray("items");
                        List<Audio> newList = parseAudioItems(items);

                        totalCount = responseObj.optInt("count", 0);
                        hasMoreItems = (offset + items.length()) < totalCount;
                        currentOffset = offset + items.length();

                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            swipeRefreshLayout.setRefreshing(false);
                            progressBar.setVisibility(View.GONE);

                            if (!isLoadMore) {
                                fullAudioList.clear();
                                audioList.clear();
                            }

                            fullAudioList.addAll(newList);
                            audioList.addAll(newList);

                            adapter.notifyDataSetChanged();

                            if (isLoadMore) {
                                updateCountText("Треки: " + audioList.size() + " из " + totalCount);
                            } else {
                                updateCountText("Треки: " + totalCount);
                            }
                        });
                    }
                } catch (JSONException e) {
                    requireActivity().runOnUiThread(() -> {
                        isLoading = false;
                        swipeRefreshLayout.setRefreshing(false);
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private List<Audio> parseAudioItems(JSONArray items) throws JSONException {
        List<Audio> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject track = items.getJSONObject(i);
            String artist = track.optString("artist", "Unknown Artist");
            String title = track.optString("title", "Unknown Title");
            String url = track.optString("url");
            int genreId = track.optInt("genre_id", 0);
            long ownerId = track.optLong("owner_id", 0);
            long audioId = track.optLong("id", 0);
            int lyricsId = track.optInt("lyrics_id", 0);
            int duration = track.optInt("duration", 0);
            int albumId = track.optInt("album_id", 0);

            if (url != null && !url.isEmpty()) {
                Audio audio = new Audio(artist, title, url);
                audio.setGenreId(genreId);
                audio.setOwnerId(ownerId);
                audio.setAudioId(audioId);
                audio.setLyricsId(lyricsId);
                audio.setDuration(duration);
                audio.setAlbumId(albumId);
                result.add(audio);
            }
        }
        return result;
    }

    public void filterTracks(String query) {
        List<Audio> filteredList = new ArrayList<>();
        String lowerCaseQuery = query.toLowerCase();

        for (Audio audio : fullAudioList) {
            if (audio.getTitle().toLowerCase().contains(lowerCaseQuery) ||
                    audio.getArtist().toLowerCase().contains(lowerCaseQuery)) {
                filteredList.add(audio);
            }
        }

        audioList.clear();
        audioList.addAll(filteredList);
        adapter.notifyDataSetChanged();

        if (isRecommendationsMode) {
            updateCountText("Найдено: " + audioList.size());
        } else {
            updateCountText("Найдено: " + audioList.size() + " из " + totalCount);
        }

        if (filteredList.isEmpty() && !query.isEmpty()) {
            Toast.makeText(getContext(), "Ничего не найдено", Toast.LENGTH_SHORT).show();
        }
    }

    public void resetSearch() {
        audioList.clear();
        audioList.addAll(fullAudioList);
        adapter.notifyDataSetChanged();

        if (isRecommendationsMode) {
            updateCountText("Рекомендации: " + audioList.size());
        } else {
            updateCountText("Треки: " + audioList.size() + " из " + totalCount);
        }
    }

    private void playTrack(int position) {
        Audio audio = audioList.get(position);
        if (audio.getUrl() == null || audio.getUrl().isEmpty()) {
            Toast.makeText(getContext(), "Трек недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(getContext(), MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_PLAY);
        intent.putExtra("URL", audio.getUrl());
        intent.putExtra("TITLE", audio.getTitle());
        intent.putExtra("ARTIST", audio.getArtist());
        intent.putExtra("DURATION", audio.getDuration());

        ArrayList<Audio> playlist = new ArrayList<>(audioList);
        intent.putParcelableArrayListExtra("PLAYLIST", playlist);
        intent.putExtra("POSITION", position);

        ContextCompat.startForegroundService(requireContext(), intent);

        showPlayerBottomSheet();
    }

    private void showPlayerBottomSheet() {
        PlayerBottomSheetFragment playerFragment = new PlayerBottomSheetFragment();
        playerFragment.show(getParentFragmentManager(), "player_bottom_sheet");
    }

    private void shufflePlayAudioList() {
        if (audioList.isEmpty()) {
            Toast.makeText(getContext(), "Нет треков для воспроизведения", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Audio> shuffledList = new ArrayList<>(audioList);
        Collections.shuffle(shuffledList);

        Audio randomAudio = shuffledList.get(0);
        playTrackDirectly(randomAudio);

        Toast.makeText(getContext(), "Воспроизведение случайного трека", Toast.LENGTH_SHORT).show();
    }

    private void playTrackDirectly(Audio audio) {
        if (audio.getUrl() == null || audio.getUrl().isEmpty()) {
            Toast.makeText(getContext(), "Трек недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(getContext(), MusicPlayerService.class);
        intent.setAction("PLAY");
        intent.putExtra("URL", audio.getUrl());
        intent.putExtra("TITLE", audio.getTitle());
        intent.putExtra("ARTIST", audio.getArtist());
        intent.putExtra("DURATION", audio.getDuration());
        ContextCompat.startForegroundService(requireContext(), intent);
    }

    private void shuffleAudioList() {
        if (!audioList.isEmpty()) {
            Collections.shuffle(audioList);
            adapter.notifyDataSetChanged();
            Toast.makeText(getContext(), "Треки перемешаны", Toast.LENGTH_SHORT).show();

            if (isRecommendationsMode) {
                updateCountText("Рекомендации: " + audioList.size());
            }
        }
    }

    private void showSortDialog() {
        CharSequence[] items = {
                createItemWithIcon("По названию (А-Я)", R.drawable.sort_alphabetical_ascending),
                createItemWithIcon("По исполнителю", R.drawable.sort_variant),
                createItemWithIcon("По длительности", R.drawable.sort_clock_ascending_outline)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Сортировка треков")
                .setItems(items, (dialog, which) -> {
                    handleSortSelection(which);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private SpannableString createItemWithIcon(String text, @DrawableRes int iconRes) {
        SpannableString spannableString = new SpannableString("   " + text);
        Drawable icon = ContextCompat.getDrawable(requireContext(), iconRes);
        if (icon != null) {
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            ImageSpan imageSpan = new ImageSpan(icon, ImageSpan.ALIGN_BASELINE);
            spannableString.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannableString;
    }

    private void handleSortSelection(int which) {
        switch (which) {
            case 0:
                sortByTitle();
                break;
            case 1:
                sortByArtist();
                break;
            case 2:
                sortByDuration();
                break;
        }
    }

    private void sortByTitle() {
        Collections.sort(audioList, (a1, a2) ->
                a1.getTitle().compareToIgnoreCase(a2.getTitle()));
        adapter.notifyDataSetChanged();
    }

    private void sortByArtist() {
        Collections.sort(audioList, (a1, a2) ->
                a1.getArtist().compareToIgnoreCase(a2.getArtist()));
        adapter.notifyDataSetChanged();
    }

    private void sortByDuration() {
        Collections.sort(audioList, (a1, a2) ->
                Long.compare(a1.getDuration(), a2.getDuration()));
        adapter.notifyDataSetChanged();
    }

    private void showBottomSheet(Audio audio) {
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_audio, null);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(view);

        view.findViewById(R.id.buttonSave).setOnClickListener(v -> {
            saveTrack(audio);
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonDownload).setOnClickListener(v -> {
            downloadTrack(audio);
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonAddToPlaylist).setOnClickListener(v -> {
            showAddToPlaylistDialog(audio);
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonEdit).setOnClickListener(v -> {
            showEditBottomSheet(audio);
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonSearch).setOnClickListener(v -> {
            searchArtist(audio.getArtist());
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonAlbum).setOnClickListener(v -> {
            deleteTrack(audio);
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonCopy).setOnClickListener(v -> {
            copyAudioLink(audio);
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonShare).setOnClickListener(v -> {
            shareAudioToChat(audio);
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonShareFriend).setOnClickListener(v -> {
            shareAudioToFriends(convertToOfficialAudio(audio));
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonOpenLyrics).setOnClickListener(v -> {
            if (audio.getLyricsId() > 0) {
                showLyrics(audio);
            } else {
                Toast.makeText(getContext(), "Текст песни недоступен", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    // Основной метод для отправки аудио в чат
    private void shareAudioToChat(Audio audio) {
        if (!isAdded() || getContext() == null) {
            return;
        }

        Context context = requireContext();

        if (availableChats.isEmpty()) {
            Toast.makeText(context, "Загрузка списка диалогов...", Toast.LENGTH_SHORT).show();
            loadAvailableChats();

            // Показываем сообщение и предлагаем повторить попытку
            new Handler().postDelayed(() -> {
                if (!availableChats.isEmpty()) {
                    shareAudioToChat(audio);
                } else {
                    Toast.makeText(context, "Не удалось загрузить диалоги", Toast.LENGTH_SHORT).show();
                }
            }, 1500);
            return;
        }

        try {
            showChatSelectionDialog(audio);
        } catch (Exception e) {
            Toast.makeText(context, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Диалог выбора чата
    private void showChatSelectionDialog(Audio audio) {
        Context context = requireContext();

        // Создаем массив названий чатов
        String[] chatNames = new String[availableChats.size()];
        for (int i = 0; i < availableChats.size(); i++) {
            chatNames[i] = availableChats.get(i).getTitle();
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle("Отправить аудио в чат")
                .setItems(chatNames, (dialog, which) -> {
                    SimpleChat selectedChat = availableChats.get(which);
                    sendAudioToChat(selectedChat, convertToOfficialAudio(audio));
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // Конвертация Audio в official.audios.Audio
    private ru.lisdevs.messenger.official.audios.Audio convertToOfficialAudio(Audio audio) {
        ru.lisdevs.messenger.official.audios.Audio officialAudio =
                new ru.lisdevs.messenger.official.audios.Audio();
        officialAudio.setArtist(audio.getArtist());
        officialAudio.setTitle(audio.getTitle());
        officialAudio.setUrl(audio.getUrl());
        officialAudio.setDuration(audio.getDuration());

        // Пробуем установить дополнительные поля через reflection
        try {
            java.lang.reflect.Method setOwnerIdMethod = officialAudio.getClass().getMethod("setOwnerId", long.class);
            setOwnerIdMethod.invoke(officialAudio, audio.getOwnerId());
        } catch (Exception e) {
            // Игнорируем если метод не существует
        }

        try {
            java.lang.reflect.Method setAudioIdMethod = officialAudio.getClass().getMethod("setId", long.class);
            setAudioIdMethod.invoke(officialAudio, audio.getAudioId());
        } catch (Exception e) {
            // Игнорируем если метод не существует
        }

        return officialAudio;
    }

    // Упрощенный метод отправки аудио в чат
    private void sendAudioToChat(SimpleChat chat, ru.lisdevs.messenger.official.audios.Audio audio) {
        if (!isAdded() || getContext() == null) {
            return;
        }

        Context context = requireContext();
        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null) {
            Toast.makeText(context, "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            return;
        }

        String messageText = "Аудиозапись: " + audio.getArtist() + " - " + audio.getTitle() +
                "\nСсылка: " + audio.getUrl();

        try {
            String url = "https://api.vk.com/method/messages.send" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&peer_id=" + chat.getPeerId() +
                    "&message=" + URLEncoder.encode(messageText, "UTF-8") +
                    "&random_id=" + System.currentTimeMillis();

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", getUserAgent())
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    FragmentActivity activity = getActivity();
                    if (activity != null && !activity.isFinishing()) {
                        activity.runOnUiThread(() ->
                                Toast.makeText(activity, "Ошибка отправки: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show());
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    FragmentActivity activity = getActivity();
                    if (activity != null && !activity.isFinishing()) {
                        activity.runOnUiThread(() -> {
                            try {
                                String responseBody = response.body().string();
                                JSONObject json = new JSONObject(responseBody);

                                if (json.has("response")) {
                                    Toast.makeText(activity, "✅ Аудио отправлено в " + chat.getTitle(),
                                            Toast.LENGTH_SHORT).show();
                                } else if (json.has("error")) {
                                    JSONObject error = json.getJSONObject("error");
                                    String errorMsg = error.optString("error_msg", "Неизвестная ошибка");
                                    Toast.makeText(activity, "❌ Ошибка: " + errorMsg,
                                            Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Toast.makeText(activity, "✅ Аудио отправлено в " + chat.getTitle(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            FragmentActivity activity = getActivity();
            if (activity != null && !activity.isFinishing()) {
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }
    }

    private void openAlbum(long ownerId, long albumId, String albumTitle) {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(getContext());
        progressDialog.setMessage("Загрузка альбома...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Запрос для получения всех треков пользователя
        String url = "https://api.vk.com/method/audio.get" +
                "?access_token=" + accessToken +
                "&owner_id=" + ownerId +
                "&count=6000" + // Увеличиваем количество для получения всех треков
                "&v=5.131";

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", getUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(getContext(), "Ошибка загрузки альбома: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("response")) {
                                JSONObject responseObj = json.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");

                                List<Audio> allTracks = parseAudioItems(items);

                                // Фильтруем треки по album_id
                                List<Audio> albumTracks = new ArrayList<>();
                                for (Audio track : allTracks) {
                                    if (track.getAlbumId() == albumId) {
                                        albumTracks.add(track);
                                    }
                                }

                                requireActivity().runOnUiThread(() -> {
                                    progressDialog.dismiss();

                                    if (albumTracks.isEmpty()) {
                                        Toast.makeText(getContext(), "Альбом пуст или недоступен", Toast.LENGTH_SHORT).show();
                                    } else {
                                        showAlbumFragment(ownerId, albumId, albumTitle, albumTracks);
                                    }
                                });
                            } else if (json.has("error")) {
                                JSONObject error = json.getJSONObject("error");
                                int errorCode = error.getInt("error_code");
                                String errorMsg = error.getString("error_msg");

                                requireActivity().runOnUiThread(() -> {
                                    progressDialog.dismiss();
                                    Toast.makeText(getContext(),
                                            "Ошибка: " + errorMsg + " (" + errorCode + ")",
                                            Toast.LENGTH_SHORT).show();
                                });
                            }
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(getContext(), "Ошибка обработки данных: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                });
    }

    private void showAlbumFragment(long ownerId, long albumId, String albumTitle, List<Audio> tracks) {
        OpenAlbumFragment fragment = OpenAlbumFragment.newInstance(ownerId, albumId, albumTitle, tracks);

        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack("album")
                .commit();
    }

    private void searchArtist(String artistName) {
        Intent searchIntent = new Intent(getActivity(), BaseActivity.class);
        searchIntent.putExtra("search_query", artistName);
        searchIntent.putExtra("fragment_to_load", "music_search");
        searchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(searchIntent);
        openMusicSearchFragment(artistName);
    }

    private void openMusicSearchFragment(String query) {
        MusicSearchFragment searchFragment = new MusicSearchFragment();
        Bundle args = new Bundle();
        args.putString("search_query", query);
        searchFragment.setArguments(args);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, searchFragment)
                .addToBackStack("my_music")
                .commit();
    }

    private void shareAudioLink(Audio audio) {
        String shareText = createShareText(audio);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Послушай этот трек");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

        try {
            startActivity(Intent.createChooser(shareIntent, "Поделиться треком"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Ошибка при открытии sharing", Toast.LENGTH_SHORT).show();
        }
    }

    private String createShareText(Audio audio) {
        return audio.getArtist() + " - " + audio.getTitle() + "\n\n" +
                "Ссылка: " + audio.getUrl() + "\n\n" +
                "Поделено через Моё Приложение";
    }

    private void saveTrack(Audio audio) {
        String fileName = audio.getArtist() + " - " + audio.getTitle() + ".mp3";
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "");

        String subPath = MOOSIC_FOLDER + File.separator + fileName;

        DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm != null) {
            try {
                File moosicDir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        MOOSIC_FOLDER
                );
                if (!moosicDir.exists()) {
                    moosicDir.mkdirs();
                }

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(audio.getUrl()))
                        .setTitle(fileName)
                        .setDescription("Скачивание " + fileName)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, subPath)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true);

                dm.enqueue(request);
                Toast.makeText(getContext(), "Скачивание начато: " + fileName, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(getContext(), "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getContext(), "Сервис скачивания недоступен", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadTrack(Audio audio) {
        String fileName = audio.getArtist() + " - " + audio.getTitle() + ".mp3";
        DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm != null) {
            dm.enqueue(new DownloadManager.Request(Uri.parse(audio.getUrl()))
                    .setTitle(fileName)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED));
            Toast.makeText(getContext(), "Скачивание начато", Toast.LENGTH_SHORT).show();
        }
    }

    private void editTrack(Audio audio, String newArtist, String newTitle) {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken == null) {
            showToast("Токен не найден");
            return;
        }

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/audio.edit")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("owner_id", String.valueOf(audio.getOwnerId()))
                .addQueryParameter("audio_id", String.valueOf(audio.getAudioId()))
                .addQueryParameter("artist", newArtist)
                .addQueryParameter("title", newTitle)
                .addQueryParameter("v", "5.131")
                .build();

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", getUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        showToast("Ошибка сети");
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.has("response")) {
                                requireActivity().runOnUiThread(() -> {
                                    showToast("Трек успешно изменен");
                                    refreshAudioList();
                                });
                            } else if (json.has("error")) {
                                JSONObject error = json.getJSONObject("error");
                                showToast("Ошибка: " + error.optString("error_msg"));
                            }
                        } catch (JSONException e) {
                            showToast("Ошибка обработки ответа");
                        }
                    }
                });
    }

    private void showToast(String message) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
        }
    }

    private void showEditBottomSheet(Audio audio) {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_track, null);
        BottomSheetDialog editDialog = new BottomSheetDialog(requireContext());
        editDialog.setContentView(view);

        EditText editArtist = view.findViewById(R.id.editArtist);
        EditText editTitle = view.findViewById(R.id.editTitle);
        Button saveButton = view.findViewById(R.id.buttonSave);

        editArtist.setText(audio.getArtist());
        editTitle.setText(audio.getTitle());

        saveButton.setOnClickListener(v -> {
            String newArtist = editArtist.getText().toString().trim();
            String newTitle = editTitle.getText().toString().trim();

            if (!newArtist.isEmpty() && !newTitle.isEmpty()) {
                editTrack(audio, newArtist, newTitle);
                editDialog.dismiss();
            } else {
                Toast.makeText(getContext(), "Заполните все поля", Toast.LENGTH_SHORT).show();
            }
        });

        editDialog.show();
    }

    private void showAddToPlaylistDialog(Audio audio) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Добавить в плейлист")
                .setMessage("Выберите плейлист для добавления трека")
                .setPositiveButton("Продолжить", (dialog, which) -> {
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void navigateToFriends() {
        VkPlaylistsFragment vkPlaylistsFragment = new VkPlaylistsFragment();
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, vkPlaylistsFragment)
                .addToBackStack("my_music")
                .commit();
    }

    private void navigateToSave() {
        SaveMusicFragment saveMusicFragment = new SaveMusicFragment();
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, saveMusicFragment)
                .addToBackStack("my_music")
                .commit();
    }

    private void getAudioCount() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken == null) {
            updateCountText("Токен не найден");
            return;
        }

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/audio.getCount")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("v", "5.131")
                .build();

        okHttpClient.newCall(new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                updateCountText("Ошибка сети");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    if (json.has("response")) {
                        int count = json.getInt("response");
                        updateCountText("Всего треков: " + count);
                    } else if (json.has("error")) {
                        // Обработка ошибки
                    }
                } catch (JSONException e) {
                    updateCountText("Ошибка обработки");
                }
            }
        });
    }

    private void updateCountText(String text) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> textViewResult.setText(text));
        }
    }

    private void showApiError(JSONObject error) {
        if (!isAdded() || getActivity() == null) return;

        int code = error.optInt("error_code");
        String msg = error.optString("error_msg");
        getActivity().runOnUiThread(() ->
                Toast.makeText(getContext(), "Ошибка API (" + code + "): " + msg, Toast.LENGTH_LONG).show()
        );
    }

    private void showLyrics(Audio audio) {
        if (audio.getLyricsId() > 0) {
            LyricsBottomSheet bottomSheet = LyricsBottomSheet.newInstance(
                    audio.getLyricsId(),
                    audio.getTitle(),
                    audio.getArtist()
            );
            bottomSheet.show(getParentFragmentManager(), "lyrics_bottom_sheet");
        } else {
            showToast("Текст песни недоступен для этого трека");
        }
    }

    private void deleteTrack(Audio audio) {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        String url = "https://api.vk.com/method/audio.delete" +
                "?access_token=" + accessToken +
                "&owner_id=" + audio.getOwnerId() +
                "&audio_id=" + audio.getAudioId() +
                "&v=5.131";

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", getUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        requireActivity().runOnUiThread(() ->
                                showToast("Ошибка сети: " + e.getMessage()));
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("response")) {
                                int result = json.getInt("response");
                                if (result == 1) {
                                    requireActivity().runOnUiThread(() ->
                                            showToast("Трек удален"));
                                    refreshTrackList();
                                } else {
                                    requireActivity().runOnUiThread(() ->
                                            showToast("Не удалось удалить трек"));
                                }
                            } else if (json.has("error")) {
                                JSONObject error = json.getJSONObject("error");
                                String errorMsg = error.optString("error_msg", "Неизвестная ошибка");
                                int errorCode = error.optInt("error_code", 0);

                                requireActivity().runOnUiThread(() ->
                                        showToast("Ошибка (" + errorCode + "): " + errorMsg));
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() ->
                                    showToast("Ошибка обработки ответа"));
                        } finally {
                            response.close();
                        }
                    }
                });
    }

    private void refreshTrackList() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        }
    }

    private void copyAudioLink(Audio audio) {
        String link = audio.getUrl();
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Playlist Link", link);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), "Ссылка скопирована", Toast.LENGTH_SHORT).show();
    }

    // Классы для плейлистов
    public static class Playlist {
        public int id;
        public String title;
        public long ownerId;
        public String ownerName;
        public int count;
        public Photo photo;

        public String getPhotoUrl() {
            return photo != null ? photo.photo_300 : "";
        }
    }

    public static class Photo {
        public String photo_300;
    }

    // Адаптер для плейлистов
    private static class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.ViewHolder> {
        private final List<Playlist> playlists;
        private final OnPlaylistClickListener listener;
        private final OnMenuButtonClickListener menuListener;

        interface OnPlaylistClickListener {
            void onClick(Playlist playlist);
        }

        interface OnMenuButtonClickListener {
            void onMenuClick(Playlist playlist);
        }

        PlaylistsAdapter(List<Playlist> playlists,
                         OnPlaylistClickListener listener,
                         OnMenuButtonClickListener menuListener) {
            this.playlists = playlists;
            this.listener = listener;
            this.menuListener = menuListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist_horizontal, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Playlist playlist = playlists.get(position);

            holder.title.setText(playlist.title);
            holder.count.setText(playlist.count + " треков");

            if (playlist.photo != null && !playlist.photo.photo_300.isEmpty()) {
                Glide.with(holder.itemView)
                        .load(playlist.photo.photo_300)
                        .placeholder(R.drawable.circle_playlist)
                        .into(holder.image);
            } else {
                holder.image.setImageResource(R.drawable.circle_playlist);
            }

            holder.itemView.setOnClickListener(v -> listener.onClick(playlist));
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ShapeableImageView image;
            TextView title;
            TextView count;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.coverImage);
                title = itemView.findViewById(R.id.titleText);
                count = itemView.findViewById(R.id.countText);
            }
        }
    }

    private static class GenreAdapter extends RecyclerView.Adapter<GenreAdapter.ViewHolder> {
        private final List<String> genreNames;
        private OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(String genreName);
        }

        GenreAdapter(List<String> genreNames) {
            this.genreNames = genreNames;
        }

        void setOnItemClickListener(OnItemClickListener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_genre, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.genreName.setText(genreNames.get(position));
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(genreNames.get(position));
                }
            });
        }

        @Override
        public int getItemCount() {
            return genreNames.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView genreName;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                genreName = itemView.findViewById(R.id.genreName);
            }
        }
    }
}