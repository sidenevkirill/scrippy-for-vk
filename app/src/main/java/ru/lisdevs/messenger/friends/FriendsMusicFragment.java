package ru.lisdevs.messenger.friends;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.playlists.PlaylistDetailsFragment;
import ru.lisdevs.messenger.posts.GroupPostsFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.SpecialUsersManager;
import ru.lisdevs.messenger.utils.TokenManager;

public class FriendsMusicFragment extends Fragment {

    private RecyclerView recyclerViewFriends;
    private FriendsAdapter adapter;
    private List<VKFriend> friendsList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView statusTextView;
    private OkHttpClient httpClient;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_friends, container, false);

        initViews(view);
        setupRecyclerView();
        fetchVKFriends();

        return view;
    }

    private void initViews(View view) {
        recyclerViewFriends = view.findViewById(R.id.friendsRecyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        statusTextView = view.findViewById(R.id.statusTextView);

        swipeRefreshLayout.setOnRefreshListener(this::fetchVKFriends);
    }

    private void setupRecyclerView() {
        adapter = new FriendsAdapter(friendsList, friend -> {
            openFriendDetails(friend);
        });
        recyclerViewFriends.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewFriends.setAdapter(adapter);
    }

    private void fetchVKFriends() {
        statusTextView.setText("Загрузка друзей...");
        swipeRefreshLayout.setRefreshing(true);

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null || accessToken.isEmpty()) {
            showError("Ошибка авторизации");
            return;
        }

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/friends.get")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("fields", "first_name,last_name,screen_name")
                .addQueryParameter("v", "5.131")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "VKAndroidApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    showError("Ошибка сети: " + e.getMessage());
                    swipeRefreshLayout.setRefreshing(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        requireActivity().runOnUiThread(() -> {
                            showError("Ошибка сервера: " + response.code());
                            swipeRefreshLayout.setRefreshing(false);
                        });
                        return;
                    }

                    String json = body.string();
                    JSONObject jsonResponse = new JSONObject(json);

                    if (jsonResponse.has("error")) {
                        handleApiError(jsonResponse.getJSONObject("error"));
                        return;
                    }

                    List<VKFriend> friends = parseFriendsResponse(jsonResponse);
                    requireActivity().runOnUiThread(() -> {
                        updateFriendsList(friends);
                        swipeRefreshLayout.setRefreshing(false);
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        showError("Ошибка обработки данных");
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }
        });
    }

    private List<VKFriend> parseFriendsResponse(JSONObject jsonResponse) throws JSONException {
        List<VKFriend> friends = new ArrayList<>();
        JSONArray items = jsonResponse.getJSONObject("response").getJSONArray("items");

        for (int i = 0; i < items.length(); i++) {
            JSONObject friendJson = items.getJSONObject(i);
            long id = friendJson.getLong("id");
            String firstName = friendJson.getString("first_name");
            String lastName = friendJson.getString("last_name");
            String screenName = friendJson.optString("screen_name", "");

            friends.add(new VKFriend(id, firstName, lastName, screenName));
        }
        return friends;
    }

    private void updateFriendsList(List<VKFriend> friends) {
        friendsList.clear();
        friendsList.addAll(friends);

        if (friends.isEmpty()) {
            statusTextView.setText("Друзья не найдены");
            statusTextView.setVisibility(View.VISIBLE);
        } else {
            statusTextView.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
        }
    }

    private void handleApiError(JSONObject errorObj) {
        String errorMsg = errorObj.optString("error_msg", "Неизвестная ошибка API");
        requireActivity().runOnUiThread(() -> {
            showError(errorMsg);
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void showError(String message) {
        statusTextView.setText(message);
        statusTextView.setVisibility(View.VISIBLE);
    }

    private void openFriendDetails(VKFriend friend) {
        FriendDetailsFragment detailsFragment = new FriendDetailsFragment();
        Bundle args = new Bundle();
        args.putLong("friend_id", friend.id);
        args.putString("friend_name", friend.firstName + " " + friend.lastName);
        detailsFragment.setArguments(args);

        getParentFragmentManager().beginTransaction()
                .replace(R.id.container, detailsFragment)
                .addToBackStack(null)
                .commit();
    }

    static class VKFriend {
        long id;
        String firstName;
        String lastName;
        String screenName;

        VKFriend(long id, String firstName, String lastName, String screenName) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.screenName = screenName;
        }
    }

    static class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {
        private List<VKFriend> friends;
        private OnItemClickListener listener;
        private OkHttpClient countClient = new OkHttpClient();

        interface OnItemClickListener {
            void onItemClick(VKFriend friend);
        }

        FriendsAdapter(List<VKFriend> friends, OnItemClickListener listener) {
            this.friends = friends;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friend, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VKFriend friend = friends.get(position);
            holder.nameTextView.setText(friend.firstName + " " + friend.lastName);
            holder.audioCountTextView.setText("Загрузка...");

            loadAudioCount(friend.id, holder.audioCountTextView);

            holder.itemView.setOnClickListener(v -> listener.onItemClick(friend));
        }

        private void loadAudioCount(long userId, TextView countView) {
            String accessToken = TokenManager.getInstance(countView.getContext()).getToken();
            if (accessToken == null) return;

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/audio.getCount")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("owner_id", String.valueOf(userId))
                    .addQueryParameter("v", "5.131")
                    .build();

            countClient.newCall(new Request.Builder()
                            .url(url)
                            .build())
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            updateCountText(countView, "Ошибка");
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            try {
                                String json = response.body().string();
                                JSONObject jsonObject = new JSONObject(json);
                                Object responseObj = jsonObject.get("response");

                                int count = 0;
                                if (responseObj instanceof JSONObject) {
                                    count = ((JSONObject) responseObj).getInt("count");
                                } else if (responseObj instanceof Integer) {
                                    count = (Integer) responseObj;
                                }

                                updateCountText(countView, "Треков: " + count);
                            } catch (Exception e) {
                                updateCountText(countView, "Ошибка");
                            }
                        }
                    });
        }

        private void updateCountText(TextView view, String text) {
            if (view.getContext() instanceof Activity) {
                ((Activity) view.getContext()).runOnUiThread(() -> view.setText(text));
            }
        }

        @Override
        public int getItemCount() {
            return friends.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView;
            TextView audioCountTextView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.user);
                audioCountTextView = itemView.findViewById(R.id.audio_count);
            }
        }
    }

    public static class FriendDetailsFragment extends Fragment {
        private TextView textViewDetails;
        private TextView textViewGroups;
        private TextView textViewId;
        private RecyclerView recyclerViewAudio;
        private RecyclerView recyclerViewPlaylists;
        private AudioAdapter audioAdapter;
        private PlaylistAdapter playlistAdapter;
        private List<VKAudio> audioList = new ArrayList<>();
        private List<PlaylistItem> playlistList = new ArrayList<>();
        private int currentTrackIndex = -1;
        private Toolbar toolbar;
        private SwipeRefreshLayout swipeRefreshLayout;
        private long friendId;
        private String ACCESS_TOKEN;
        private static final String API_VERSION = "5.131";
        private TextView textViewResult;
        private int playlistId;
        private LinearLayout playlistsSection;
        private OkHttpClient httpClient;
        private ImageView specialUserBadge;
        private boolean isSpecialUser = false;
        private Handler handler = new Handler(Looper.getMainLooper());
        private TextView textViewPlaylistsCount;
        private int totalPlaylistsCount = 0;
        private int audioCount = 0;
        private ImageView addFriendIcon;
        private boolean isFriend = false;
        private long currentUserId = 0;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            // Загружаем список специальных пользователей при создании фрагмента
            SpecialUsersManager.loadSpecialUsers(getContext());
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.activity_groups_music, container, false);

            textViewDetails = view.findViewById(R.id.statusTextView);
            textViewGroups = view.findViewById(R.id.group_name);
            textViewId = view.findViewById(R.id.group_members);
            recyclerViewAudio = view.findViewById(R.id.friendsRecyclerView);
            recyclerViewPlaylists = view.findViewById(R.id.recyclerViewPlaylists);
            playlistsSection = view.findViewById(R.id.playlistsSection);
            textViewResult = view.findViewById(R.id.count);
            textViewPlaylistsCount = view.findViewById(R.id.count_playlists);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            toolbar = view.findViewById(R.id.toolbar);
            specialUserBadge = view.findViewById(R.id.verified_icon);
            addFriendIcon = view.findViewById(R.id.add_friend_icon);

            ImageView shuffleButton = view.findViewById(R.id.sort);
            shuffleButton.setOnClickListener(v -> openPlaylistsFragment());

            setupUI();
            setupToolbar();
            setupSwipeRefresh();
            setupFriendIcon();

            // Получение аргументов
            Bundle args = getArguments();
            if (args != null) {
                friendId = args.getLong("friend_id");
                String friendName = args.getString("friend_name");

                textViewDetails.setText("ID: " + friendId + "\nИмя: " + friendName);
                textViewGroups.setText(friendName);
                textViewId.setText("@id" + Math.toIntExact(friendId));

                // Установка имени в Toolbar
                if (getActivity() instanceof AppCompatActivity && friendName != null) {
                    ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("Аудиозаписи");
                }

                // Проверяем, является ли пользователь специальным ПОСЛЕ установки friendId
                checkIfSpecialUser(friendId);

                // Загрузка данных
                fetchFriendAudio(friendId);
                fetchUserPlaylists();
                getAudioCount();
            }

            return view;
        }

        private void setupUI() {
            // Настройка RecyclerView для треков (вертикальный)
            recyclerViewAudio.setLayoutManager(new LinearLayoutManager(getContext()));
            audioAdapter = new AudioAdapter(audioList);
            recyclerViewAudio.setAdapter(audioAdapter);

            // Настройка RecyclerView для плейлистов (горизонтальный)
            LinearLayoutManager playlistsLayoutManager = new LinearLayoutManager(
                    getContext(), LinearLayoutManager.HORIZONTAL, false);
            recyclerViewPlaylists.setLayoutManager(playlistsLayoutManager);
            playlistAdapter = new PlaylistAdapter(playlistList, this::openPlaylist);
            recyclerViewPlaylists.setAdapter(playlistAdapter);
        }

        private void setupToolbar() {
            if (toolbar != null && getActivity() instanceof AppCompatActivity) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                activity.setSupportActionBar(toolbar);
                if (activity.getSupportActionBar() != null) {
                    activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                }

                toolbar.setNavigationOnClickListener(v -> {
                    if (getActivity() != null) {
                        getActivity().onBackPressed();
                    }
                });
            }

            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_show_friends_posts) {
                    GroupPostsFragment groupPostsFragment = new GroupPostsFragment();
                    Bundle args = new Bundle();
                    args.putString("GROUP_ID", String.valueOf(friendId));
                    groupPostsFragment.setArguments(args);

                    FragmentManager fragmentManager = getParentFragmentManager();
                    FragmentTransaction transaction = fragmentManager.beginTransaction();
                    transaction.replace(R.id.container, groupPostsFragment);
                    transaction.addToBackStack(null);
                    transaction.commit();
                    return true;
                }
                return false;
            });
        }

        private void setupSwipeRefresh() {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                // Сбрасываем счетчики
                totalPlaylistsCount = 0;
                audioCount = 0;
                updateCombinedStatsText();

                fetchFriendAudio(friendId);
                fetchUserPlaylists();
                getAudioCount();
                checkIfSpecialUser(friendId);
            });

            // Обработка клика по элементу списка треков
            audioAdapter.setOnItemClickListener(audio -> {
                currentTrackIndex = audioList.indexOf(audio);
                String url = audio.url;
                if (url != null && !url.isEmpty()) {
                    // Запускаем сервис для воспроизведения
                    Intent intent = new Intent(getContext(), MusicPlayerService.class);
                    intent.setAction("PLAY");
                    intent.putExtra("URL", url);
                    intent.putExtra("TITLE", audio.artist + " - " + audio.title);
                    ContextCompat.startForegroundService(getContext(), intent);
                } else {
                    Toast.makeText(getContext(), "Нет доступного URL для этого трека", Toast.LENGTH_SHORT).show();
                }
            });

            audioAdapter.setOnMenuClickListener(this::showBottomSheet);
        }

        private void checkIfSpecialUser(long userId) {
            if (userId == 0) {
                Log.d("SpecialUser", "User ID is 0, skipping check");
                return;
            }

            Log.d("SpecialUser", "Checking if user " + userId + " is special");

            // Проверяем сразу
            isSpecialUser = SpecialUsersManager.isSpecialUser(userId);
            updateSpecialUserBadge();

            // Если список пустой, ждем загрузки и проверяем снова
            if (SpecialUsersManager.getSpecialUserIds().isEmpty()) {
                Log.d("SpecialUser", "Special users list is empty, loading...");
                SpecialUsersManager.loadSpecialUsers(getContext());

                handler.postDelayed(() -> {
                    isSpecialUser = SpecialUsersManager.isSpecialUser(userId);
                    Log.d("SpecialUser", "Delayed check - User " + userId + " is special: " + isSpecialUser);
                    updateSpecialUserBadge();
                }, 1000);
            }
        }

        private void updateSpecialUserBadge() {
            if (specialUserBadge != null && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isSpecialUser) {
                        specialUserBadge.setVisibility(View.VISIBLE);
                        Log.d("SpecialUser", "Showing verified badge for user: " + friendId);

                        // Добавляем обработчик клика для открытия фрагмента
                        specialUserBadge.setOnClickListener(v -> openPlaylistsFragment());

                        // Добавляем Tooltip с объяснением
                        specialUserBadge.setOnLongClickListener(v -> {
                            Toast.makeText(getContext(), "✓ Проверенный пользователь", Toast.LENGTH_SHORT).show();
                            return true;
                        });
                    } else {
                        specialUserBadge.setVisibility(View.GONE);
                        specialUserBadge.setOnClickListener(null);
                        specialUserBadge.setOnLongClickListener(null);
                        Log.d("SpecialUser", "Hiding verified badge for user: " + friendId);
                    }
                });
            }
        }

        private void getAudioCount() {
            Context context = getContext();
            if (context == null) {
                updateCombinedStatsText();
                return;
            }

            String accessToken = TokenManager.getInstance(context).getToken();
            if (accessToken == null || accessToken.isEmpty()) {
                updateCombinedStatsText();
                return;
            }

            if (friendId == 0) {
                updateCombinedStatsText();
                return;
            }

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/audio.getCount")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("owner_id", String.valueOf(friendId))
                    .addQueryParameter("v", API_VERSION)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", Authorizer.getKateUserAgent())
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    updateCombinedStatsText();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            updateCombinedStatsText();
                            return;
                        }

                        String responseBody = response.body().string();
                        parseAudioCountResponse(responseBody);
                    } catch (Exception e) {
                        updateCombinedStatsText();
                    }
                }
            });
        }

        private void parseAudioCountResponse(String json) throws JSONException {
            JSONObject jsonObject = new JSONObject(json);

            if (jsonObject.has("error")) {
                updateCombinedStatsText();
                return;
            }

            if (jsonObject.has("response")) {
                Object response = jsonObject.get("response");
                int count = 0;

                if (response instanceof JSONObject) {
                    count = ((JSONObject) response).getInt("count");
                } else if (response instanceof Integer) {
                    count = (Integer) response;
                }

                audioCount = count;
                updateCombinedStatsText();
            } else {
                updateCombinedStatsText();
            }
        }

        private void updateCombinedStatsText() {
            Activity activity = getActivity();
            if (activity != null && textViewResult != null) {
                activity.runOnUiThread(() -> {
                    StringBuilder statsText = new StringBuilder();

                    if (audioCount > 0) {
                        statsText.append("Треки: ").append(audioCount);
                    } else {
                        statsText.append("Треки: 0");
                    }

                    //if (totalPlaylistsCount > 0) {
                    //    statsText.append(" • Плейлисты: ").append(totalPlaylistsCount);
                    //}

                    if (statsText.length() == 0) {
                        statsText.append("Загрузка...");
                    }

                    textViewResult.setText(statsText.toString());
                });
            }
        }

        private void fetchFriendAudio(long userId) {
            String accessToken = TokenManager.getInstance(getContext()).getToken();
            if (accessToken == null) {
                Toast.makeText(getContext(), "Требуется авторизация", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
                return;
            }

            String urlString = "https://api.vk.com/method/audio.get"
                    + "?owner_id=" + userId
                    + "&access_token=" + accessToken
                    + "&v=" + API_VERSION
                    + "&playlist_id=" + playlistId;

            Request request = new Request.Builder()
                    .url(urlString)
                    .header("User-Agent", Authorizer.getKateUserAgent())
                    .build();

            swipeRefreshLayout.setRefreshing(true);

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка загрузки аудио", Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");
                                List<VKAudio> newAudioList = new ArrayList<>();
                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject audioObj = items.getJSONObject(i);
                                    long id = audioObj.optLong("id");
                                    String artist = audioObj.optString("artist");
                                    String title = audioObj.optString("title");
                                    String urlAudio = audioObj.optString("url");
                                    newAudioList.add(new VKAudio(id, artist, title, urlAudio));
                                }
                                requireActivity().runOnUiThread(() -> {
                                    audioList.clear();
                                    audioList.addAll(newAudioList);
                                    audioAdapter.notifyDataSetChanged();
                                    swipeRefreshLayout.setRefreshing(false);
                                });
                            } else if (jsonObject.has("error")) {
                                JSONObject errorObj = jsonObject.getJSONObject("error");
                                String errorMsg = errorObj.optString("error_msg");
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Ошибка: " + errorMsg, Toast.LENGTH_SHORT).show();
                                    swipeRefreshLayout.setRefreshing(false);
                                });
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                                swipeRefreshLayout.setRefreshing(false);
                            });
                        }
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Ошибка сервера", Toast.LENGTH_SHORT).show();
                            swipeRefreshLayout.setRefreshing(false);
                        });
                    }
                }
            });
        }

        private void fetchUserPlaylists() {
            String accessToken = TokenManager.getInstance(getContext()).getToken();
            if (accessToken == null) {
                updatePlaylistsSectionVisibility();
                return;
            }

            // Сначала получаем общее количество плейлистов
            String countUrl = "https://api.vk.com/method/audio.getPlaylists" +
                    "?owner_id=" + friendId +
                    "&access_token=" + accessToken +
                    "&count=0" +
                    "&v=" + API_VERSION;

            Request countRequest = new Request.Builder()
                    .url(countUrl)
                    .header("User-Agent", Authorizer.getKateUserAgent())
                    .build();

            httpClient.newCall(countRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e("Playlists", "Failed to get playlists count: " + e.getMessage());
                    updatePlaylistsSectionVisibility();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String respStr = response.body().string();
                        JSONObject json = new JSONObject(respStr);

                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            totalPlaylistsCount = responseObj.optInt("count", 0);
                            Log.d("Playlists", "Total playlists count: " + totalPlaylistsCount);
                        }
                    } catch (Exception e) {
                        Log.e("Playlists", "Error parsing playlists count: " + e.getMessage());
                    }

                    updateCombinedStatsText();
                    loadPlaylistsData();
                }
            });
        }

        private void setupFriendIcon() {
            if (addFriendIcon != null) {
                addFriendIcon.setOnClickListener(v -> {
                    if (isFriend) {
                        removeFromFriends();
                    } else {
                        addToFriends();
                    }
                });

                // Добавляем Tooltip
                addFriendIcon.setOnLongClickListener(v -> {
                    String message = isFriend ? "Удалить из друзей" : "Добавить в друзья";
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    return true;
                });
            }
        }

        private void addToFriends() {
            String accessToken = TokenManager.getInstance(getContext()).getToken();
            if (accessToken == null) {
                Toast.makeText(getContext(), "Требуется авторизация", Toast.LENGTH_SHORT).show();
                return;
            }

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/friends.add")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("user_id", String.valueOf(friendId))
                    .addQueryParameter("v", API_VERSION)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", Authorizer.getKateUserAgent())
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка добавления в друзья", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            int result = json.getInt("response");
                            if (result == 1 || result == 2) { // 1 - добавлен, 2 - запрос отправлен
                                isFriend = true;
                                requireActivity().runOnUiThread(() -> {
                                    //updateFriendIcon();
                                    Toast.makeText(getContext(), "Добавлено в друзья", Toast.LENGTH_SHORT).show();
                                });
                            }
                        } else if (json.has("error")) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Не удалось добавить в друзья", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Ошибка обработки ответа", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        }

        private void removeFromFriends() {
            String accessToken = TokenManager.getInstance(getContext()).getToken();
            if (accessToken == null) {
                Toast.makeText(getContext(), "Требуется авторизация", Toast.LENGTH_SHORT).show();
                return;
            }

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/friends.delete")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("user_id", String.valueOf(friendId))
                    .addQueryParameter("v", API_VERSION)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", Authorizer.getKateUserAgent())
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка удаления из друзей", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            if (responseObj.has("success")) {
                                isFriend = false;
                                requireActivity().runOnUiThread(() -> {
                                    updateFriendIcon();
                                    Toast.makeText(getContext(), "Удалено из друзей", Toast.LENGTH_SHORT).show();
                                });
                            }
                        } else if (json.has("error")) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Не удалось удалить из друзей", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Ошибка обработки ответа", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        }

        private void updateFriendIcon() {
            if (addFriendIcon != null) {
                if (isFriend) {
                    addFriendIcon.setImageResource(R.drawable.check_circle);
                } else {
                    addFriendIcon.setImageResource(R.drawable.plus);
                }
            }
        }

        private void updateFriendIconVisibility(boolean visible) {
            if (addFriendIcon != null) {
                addFriendIcon.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }


        private void loadPlaylistsData() {
            String accessToken = TokenManager.getInstance(getContext()).getToken();
            if (accessToken == null) {
                updatePlaylistsSectionVisibility();
                return;
            }

            String url = "https://api.vk.com/method/audio.getPlaylists" +
                    "?owner_id=" + friendId +
                    "&access_token=" + accessToken +
                    "&count=20" +
                    "&v=" + API_VERSION;

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", Authorizer.getKateUserAgent())
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        updatePlaylistsSectionVisibility();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String respStr = response.body().string();
                        JSONObject json = new JSONObject(respStr);

                        if (json.has("error")) {
                            requireActivity().runOnUiThread(() -> {
                                updatePlaylistsSectionVisibility();
                            });
                            return;
                        }

                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");

                            List<PlaylistItem> playlists = new ArrayList<>();
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject playlistJson = items.getJSONObject(i);
                                playlists.add(new PlaylistItem(
                                        playlistJson.getInt("id"),
                                        playlistJson.getString("title"),
                                        String.valueOf(playlistJson.getLong("owner_id")),
                                        playlistJson.optString("artist", "Плейлист"),
                                        playlistJson.getInt("count"),
                                        getPlaylistThumb(playlistJson)
                                ));
                            }

                            requireActivity().runOnUiThread(() -> {
                                playlistList.clear();
                                playlistList.addAll(playlists);
                                playlistAdapter.notifyDataSetChanged();
                                updatePlaylistsSectionVisibility();
                            });
                        }
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            updatePlaylistsSectionVisibility();
                        });
                    }
                }
            });
        }

        private void updatePlaylistsSectionVisibility() {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (totalPlaylistsCount > 0 && !playlistList.isEmpty()) {
                        playlistsSection.setVisibility(View.VISIBLE);
                        if (textViewPlaylistsCount != null) {
                            textViewPlaylistsCount.setText("Плейлисты: " + totalPlaylistsCount);
                        }
                    } else {
                        playlistsSection.setVisibility(View.GONE);
                    }
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
        }

        private String getPlaylistThumb(JSONObject playlistJson) throws JSONException {
            if (playlistJson.has("photo")) {
                JSONObject photo = playlistJson.getJSONObject("photo");
                return photo.optString("photo_300", "");
            }
            if (playlistJson.has("thumb")) {
                JSONObject thumb = playlistJson.getJSONObject("thumb");
                return thumb.optString("photo_300", "");
            }
            return playlistJson.optString("thumb", "");
        }

        private void openPlaylist(PlaylistItem playlist) {
            Bundle args = new Bundle();
            args.putInt("playlist_id", playlist.getId());
            args.putString("playlist_title", playlist.getTitle());
            args.putInt("playlist_count", playlist.getCount());
            args.putString("playlist_artist", playlist.getArtist());
            args.putLong("owner_id", Long.parseLong(playlist.getOwnerId()));

            Fragment fragment = PlaylistDetailsFragment.newInstance(
                    playlist.getId(),
                    playlist.getTitle(),
                    playlist.getCount(),
                    playlist.getArtist(),
                    Long.parseLong(playlist.getOwnerId())
            );
            fragment.setArguments(args);

            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, fragment)
                    .addToBackStack(null)
                    .commit();
        }

        private void openPlaylistsFragment() {
            Bundle args = new Bundle();
            args.putLong("friend_id", friendId);

            Fragment otherFragment = new OtherFragment();
            otherFragment.setArguments(args);

            if (getActivity() != null) {
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.container, otherFragment)
                        .addToBackStack("other_fragment")
                        .commit();
            }
        }

        private void showBottomSheet(VKAudio audio) {
            View view = getLayoutInflater().inflate(R.layout.bottom_sheet_audio, null);
            BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(getContext());
            bottomSheetDialog.setContentView(view);

            TextView downloadBtn = view.findViewById(R.id.buttonDownload);
            downloadBtn.setOnClickListener(v -> {
                startDownload(getContext(), audio.url, audio.artist + " - " + audio.title + ".mp3");
                bottomSheetDialog.dismiss();
            });

            bottomSheetDialog.show();
        }

        private static void startDownload(Context context, String url, String filename) {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                Toast.makeText(context, "Не удалось начать скачивание", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri uri = Uri.parse(url);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("Скачивание трека");
            request.setDescription(filename);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            downloadManager.enqueue(request);
            Toast.makeText(context, "Начато скачивание", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
            inflater.inflate(R.menu.menu_playlists, menu);
            MenuItem goToItem = menu.findItem(R.id.action_show_playlists);

            // Скрываем пункт меню если пользователь специальный (чтобы не дублировать функционал)
            if (isSpecialUser) {
                goToItem.setVisible(false);
            } else {
                goToItem.setVisible(true);
                goToItem.setOnMenuItemClickListener(item -> {
                    openPlaylistsFragment();
                    return true;
                });
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            // Обновляем статус при возвращении на фрагмент
            if (friendId != 0) {
                checkIfSpecialUser(friendId);
            }
        }

        static class VKAudio {
            long id;
            String artist;
            String title;
            String url;

            VKAudio(long id, String artist, String title, String url) {
                this.id = id;
                this.artist = artist;
                this.title = title;
                this.url = url;
            }
        }

        static class PlaylistItem {
            private int id;
            private String title;
            private String ownerId;
            private String artist;
            private int count;
            private String thumbUrl;

            public PlaylistItem(int id, String title, String ownerId, String artist, int count, String thumbUrl) {
                this.id = id;
                this.title = title;
                this.ownerId = ownerId;
                this.artist = artist;
                this.count = count;
                this.thumbUrl = thumbUrl;
            }

            public int getId() { return id; }
            public String getTitle() { return title; }
            public String getOwnerId() { return ownerId; }
            public String getArtist() { return artist; }
            public int getCount() { return count; }
            public String getThumbUrl() { return thumbUrl; }
        }

        static class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {
            private List<VKAudio> audios;
            private OnItemClickListener listener;
            private OnMenuClickListener menuClickListener;

            public interface OnMenuClickListener {
                void onMenuClick(VKAudio audio);
            }

            public interface OnItemClickListener {
                void onItemClick(VKAudio audio);
            }

            public void setOnItemClickListener(OnItemClickListener listener) {
                this.listener = listener;
            }

            public void setOnMenuClickListener(OnMenuClickListener listener) {
                this.menuClickListener = listener;
            }

            AudioAdapter(List<VKAudio> audios) {
                this.audios = audios;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_audio, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                VKAudio audio = audios.get(position);
                holder.artistTextView.setText(audio.artist);
                holder.titleTextView.setText(audio.title);

                holder.itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onItemClick(audio);
                    }
                });

                holder.downloadButton.setOnClickListener(v -> {
                    if (menuClickListener != null) {
                        menuClickListener.onMenuClick(audio);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return audios.size();
            }

            static class ViewHolder extends RecyclerView.ViewHolder {
                TextView artistTextView;
                TextView titleTextView;
                ImageView downloadButton;

                ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    artistTextView = itemView.findViewById(R.id.artistText);
                    titleTextView = itemView.findViewById(R.id.titleText);
                    downloadButton = itemView.findViewById(R.id.downloadButton);
                }
            }
        }

        static class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
            private List<PlaylistItem> playlists;
            private OnItemClickListener listener;

            public interface OnItemClickListener {
                void onItemClick(PlaylistItem item);
            }

            public PlaylistAdapter(List<PlaylistItem> playlists, OnItemClickListener listener) {
                this.playlists = playlists;
                this.listener = listener;
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
                PlaylistItem playlist = playlists.get(position);
                holder.titleTextView.setText(playlist.getTitle());
                holder.countTextView.setText(playlist.getCount() + " треков");

                if (playlist.getThumbUrl() != null && !playlist.getThumbUrl().isEmpty()) {
                    Glide.with(holder.itemView.getContext())
                            .load(playlist.getThumbUrl())
                            .placeholder(R.drawable.circle_playlist)
                            .into(holder.coverImageView);
                } else {
                    holder.coverImageView.setImageResource(R.drawable.circle_playlist);
                }

                holder.itemView.setOnClickListener(v -> listener.onItemClick(playlist));
            }

            @Override
            public int getItemCount() {
                return playlists.size();
            }

            static class ViewHolder extends RecyclerView.ViewHolder {
                ImageView coverImageView;
                TextView titleTextView;
                TextView countTextView;

                ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    coverImageView = itemView.findViewById(R.id.coverImage);
                    titleTextView = itemView.findViewById(R.id.titleText);
                    countTextView = itemView.findViewById(R.id.countText);
                }
            }
        }
    }
}