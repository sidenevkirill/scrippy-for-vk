package ru.lisdevs.messenger.official.audios;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.auth.AuthActivity;

import ru.lisdevs.messenger.model.Message;
import ru.lisdevs.messenger.player.PlayerBottomSheetFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;
import ru.lisdevs.messenger.utils.VkAuthorizer;
import android.view.Menu;


public class AudioListFragment extends Fragment {

    private RecyclerView recyclerView;
    private AudioAdapter adapter;
    private List<Audio> audioList = new ArrayList<>();
    private List<Audio> filteredAudioList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView errorTextView;
    private Button retryButton;

    private TokenManager tokenManager;
    private OkHttpClient client;
    private List<Message> availableChats = new ArrayList<>();
    private List<Friend> availableFriends = new ArrayList<>();

    private boolean isLoading = false;
    private boolean hasMore = true;
    private int offset = 0;
    private static final int PAGE_SIZE = 200;
    private String currentQuery = "";

    private MenuItem searchMenuItem;
    private SearchView searchView;
    private boolean isSearchMode = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlists, container, false);
        initViews(view);
        initHttpClient();
        initTokenManager();
        setupRecyclerView();
        checkAuthAndLoadAudio();
        loadChats();
        loadFriends();
        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        progressBar = view.findViewById(R.id.progressBar);
        errorTextView = view.findViewById(R.id.emptyView);
        retryButton = view.findViewById(R.id.retryButton);

        swipeRefreshLayout.setOnRefreshListener(this::refreshAudio);
        retryButton.setOnClickListener(v -> checkAuthAndLoadAudio());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.search, menu);
        searchMenuItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) searchMenuItem.getActionView();

        if (searchView != null) {
            setupSearchView();
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    private void setupSearchView() {
        // Настройка SearchView
        searchView.setQueryHint("Поиск по трекам...");
        searchView.setIconifiedByDefault(false); // Всегда раскрыт
        searchView.setFocusable(true);
        searchView.requestFocus();

        // Очищаем предыдущие слушатели
        searchView.setOnQueryTextListener(null);

        // Устанавливаем новые слушатели
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d("Search", "Query submitted: " + query);
                filterAudio(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d("Search", "Query changed: " + newText);
                currentQuery = newText.trim();
                filterAudio(currentQuery);
                return true;
            }
        });

        // Слушатель закрытия поиска
        searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                Log.d("Search", "Search expanded");
                isSearchMode = true;
                // Даем фокус SearchView при раскрытии
                searchView.post(() -> {
                    searchView.setIconified(false);
                    searchView.requestFocus();
                });
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                Log.d("Search", "Search collapsed");
                resetSearch();
                isSearchMode = false;
                return true;
            }
        });

        // Принудительно раскрываем SearchView
        searchView.postDelayed(() -> {
            if (!searchView.isIconified()) {
                searchView.setIconified(false);
            }
        }, 100);

        // Кастомизация SearchView
        customizeSearchView();
    }

    private void customizeSearchView() {
        try {
            // Иконка поиска
            ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_button);
            if (searchIcon != null) {
                searchIcon.setImageResource(R.drawable.ic_search);
            }

            // Кнопка закрытия
            ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
            if (closeButton != null) {
                closeButton.setImageResource(R.drawable.close);
                closeButton.setOnClickListener(v -> {
                    searchView.setQuery("", false);
                    resetSearch();
                });
            }

            // Поле ввода
            EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (searchEditText != null) {
                searchEditText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
                searchEditText.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
                searchEditText.setHint("Поиск по трекам...");

                // Даем фокус полю ввода
                searchEditText.requestFocus();

                // Показываем клавиатуру
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        } catch (Exception e) {
            Log.e("Search", "Error customizing SearchView", e);
        }
    }

    private void filterAudio(String query) {
        Log.d("Search", "Filtering with query: '" + query + "', total audio: " + audioList.size());

        if (query.isEmpty()) {
            resetSearch();
            return;
        }

        List<Audio> tempList = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();

        for (Audio audio : audioList) {
            String artist = audio.getArtist() != null ? audio.getArtist().toLowerCase() : "";
            String title = audio.getTitle() != null ? audio.getTitle().toLowerCase() : "";

            if (artist.contains(lowerQuery) || title.contains(lowerQuery)) {
                tempList.add(audio);
            }
        }

        Log.d("Search", "Found " + tempList.size() + " results");

        // Обновляем filteredAudioList
        filteredAudioList.clear();
        filteredAudioList.addAll(tempList);

        // Обновляем адаптер
        if (adapter != null) {
            adapter.updateAudioList(filteredAudioList);
        } else {
            Log.e("Search", "Adapter is null!");
        }

        // Обновляем UI
        if (filteredAudioList.isEmpty() && !query.isEmpty()) {
            showErrorState("По запросу \"" + query + "\" ничего не найдено");
        } else if (filteredAudioList.isEmpty()) {
            showErrorState("Нет доступных аудиозаписей");
        } else {
            showContentState();
        }
    }

    private void resetSearch() {
        Log.d("Search", "Resetting search");
        currentQuery = "";
        filteredAudioList.clear();
        filteredAudioList.addAll(audioList);

        if (adapter != null) {
            adapter.updateAudioList(filteredAudioList);
        }

        if (audioList.isEmpty()) {
            showErrorState("Нет доступных аудиозаписей");
        } else {
            showContentState();
        }
    }

    private void initHttpClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private void initTokenManager() {
        tokenManager = TokenManager.getInstance(requireContext());
    }

    private void setupRecyclerView() {
        // Инициализируем адаптер с filteredAudioList
        adapter = new AudioAdapter(filteredAudioList, requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(position -> {
            if (position >= 0 && position < filteredAudioList.size()) {
                Audio audio = filteredAudioList.get(position);
                playAudio(audio, position);
            }
        });

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && !isLoading && hasMore && currentQuery.isEmpty()) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= PAGE_SIZE) {
                        loadMoreAudio();
                    }
                }
            }
        });
    }

    private void checkAuthAndLoadAudio() {
        if (tokenManager.isTokenValid()) {
            refreshAudio();
        } else {
            showErrorState("Требуется авторизация");
            navigateToAuth();
        }
    }

    private void refreshAudio() {
        offset = 0;
        hasMore = true;
        loadAudio();
    }

    private void loadAudio() {
        if (isLoading) return;

        String accessToken = tokenManager.getToken();
        if (accessToken == null || accessToken.isEmpty()) {
            showErrorState("Токен не найден");
            return;
        }

        setLoadingState(true);
        hideErrorState();

        String url = "https://api.vk.com/method/audio.get" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&count=" + PAGE_SIZE +
                "&offset=" + offset +
                "&owner_id=" + tokenManager.getUserId();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", VkAuthorizer.getKateUserAgent())
                .build();

        isLoading = true;

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    isLoading = false;
                    setLoadingState(false);
                    showErrorState("Ошибка сети: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";

                requireActivity().runOnUiThread(() -> {
                    isLoading = false;
                    setLoadingState(false);
                    swipeRefreshLayout.setRefreshing(false);

                    try {
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            if (responseObj.has("items")) {
                                JSONArray items = responseObj.getJSONArray("items");
                                List<Audio> newAudioList = parseAudioItems(items);

                                Log.d("AudioLoad", "Loaded " + newAudioList.size() + " audio items");

                                if (offset == 0) {
                                    // Первая загрузка
                                    audioList.clear();
                                    audioList.addAll(newAudioList);
                                    resetSearch(); // Это обновит filteredAudioList и адаптер
                                } else {
                                    // Подгрузка
                                    audioList.addAll(newAudioList);
                                    if (currentQuery.isEmpty()) {
                                        filteredAudioList.addAll(newAudioList);
                                        if (adapter != null) {
                                            adapter.notifyItemRangeInserted(audioList.size() - newAudioList.size(), newAudioList.size());
                                        }
                                    } else {
                                        // Если есть активный поиск, перефильтруем
                                        filterAudio(currentQuery);
                                    }
                                }

                                hasMore = newAudioList.size() == PAGE_SIZE;
                                offset += newAudioList.size();

                                if (audioList.isEmpty()) {
                                    showErrorState("Нет доступных аудиозаписей");
                                } else {
                                    showContentState();
                                }
                            } else {
                                showErrorState("Нет аудиозаписей в ответе");
                            }
                        } else if (json.has("error")) {
                            handleApiError(json.getJSONObject("error"));
                        } else {
                            showErrorState("Неизвестный формат ответа");
                        }
                    } catch (JSONException e) {
                        showErrorState("Ошибка обработки данных");
                        Log.e("AudioListFragment", "JSON error: " + e.getMessage());
                    }
                });
            }
        });
    }

    private void loadMoreAudio() {
        if (isLoading || !hasMore || !currentQuery.isEmpty()) return;
        loadAudio();
    }

    private List<Audio> parseAudioItems(JSONArray items) throws JSONException {
        List<Audio> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);

            String artist = item.optString("artist", "Unknown Artist");
            String title = item.optString("title", "Unknown Title");
            String url = item.optString("url");
            int duration = item.optInt("duration", 0);
            int genreId = item.optInt("genre_id", 0);
            long id = item.optLong("id", 0);
            long ownerId = item.optLong("owner_id", 0);

            if (url != null && !url.isEmpty() && !url.equals("null")) {
                Audio audio = new Audio(artist, title, url);
                audio.setDuration(duration);
                audio.setGenreId(genreId);
                audio.setAudioId(id);
                audio.setOwnerId(ownerId);
                result.add(audio);
            }
        }
        return result;
    }

    private void handleApiError(JSONObject error) {
        int errorCode = error.optInt("error_code");
        String errorMsg = error.optString("error_msg");

        switch (errorCode) {
            case 5:
                showErrorState("Ошибка авторизации");
                tokenManager.clearAuthData();
                navigateToAuth();
                break;
            case 6:
                showErrorState("Слишком много запросов. Попробуйте позже");
                break;
            case 15:
                showErrorState("Нет доступа к аудиозаписям");
                break;
            default:
                showErrorState("Ошибка API: " + errorMsg);
        }
    }

    private void playAudio(Audio audio, int position) {
        if (audio.getUrl() == null || audio.getUrl().isEmpty()) {
            Toast.makeText(getContext(), "Трек недоступен для прослушивания", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(getContext(), MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_PLAY);
        intent.putExtra("AUDIO", audio);
        intent.putExtra("POSITION", position);

        ArrayList<Audio> playlist = new ArrayList<>(filteredAudioList);
        intent.putParcelableArrayListExtra("PLAYLIST", playlist);

        ContextCompat.startForegroundService(requireContext(), intent);

        showPlayerFragment(audio);
    }

    private void showPlayerFragment(Audio audio) {
        PlayerBottomSheetFragment playerFragment = new PlayerBottomSheetFragment();
        playerFragment.show(getParentFragmentManager(), "player");
    }

    private void loadChats() {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken != null) {
            fetchChats(accessToken);
        }
    }

    private void fetchChats(String accessToken) {
        String url = "https://api.vk.com/method/messages.getConversations" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&count=20" +
                "&extended=1";

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("AudioListFragment", "Failed to load chats", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");
                            JSONArray profiles = responseObj.optJSONArray("profiles");
                            Map<String, String> userNames = parseUserNames(profiles);

                            List<Message> chats = new ArrayList<>();
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject conversationObj = items.getJSONObject(i);
                                JSONObject lastMessage = conversationObj.getJSONObject("last_message");
                                JSONObject conversation = conversationObj.getJSONObject("conversation");
                                String peerId = conversation.getJSONObject("peer").optString("id");

                                String text = lastMessage.optString("text");
                                String senderId = lastMessage.optString("from_id");

                                Message message = new Message(senderId, null, text,
                                        System.currentTimeMillis(), null);
                                message.setPeerId(peerId);
                                chats.add(message);
                            }

                            requireActivity().runOnUiThread(() -> {
                                availableChats.clear();
                                availableChats.addAll(chats);
                                if (adapter != null) {
                                    adapter.setAvailableChats(availableChats);
                                }
                            });
                        }
                    } catch (JSONException e) {
                        Log.e("AudioListFragment", "Error parsing chats", e);
                    }
                }
            }
        });
    }

    private void loadFriends() {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken != null) {
            fetchFriends(accessToken);
        }
    }

    private void fetchFriends(String accessToken) {
        String url = "https://api.vk.com/method/friends.get" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&fields=first_name,last_name,photo_100" +
                "&count=100";

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("AudioListFragment", "Failed to load friends", e);
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Ошибка загрузки списка друзей", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");

                            List<Friend> friends = new ArrayList<>();
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject friendObj = items.getJSONObject(i);
                                String id = friendObj.optString("id");
                                String firstName = friendObj.optString("first_name");
                                String lastName = friendObj.optString("last_name");
                                String photoUrl = friendObj.optString("photo_100");

                                Friend friend = new Friend(id, firstName + " " + lastName, photoUrl);
                                friends.add(friend);
                            }

                            requireActivity().runOnUiThread(() -> {
                                availableFriends.clear();
                                availableFriends.addAll(friends);
                                if (adapter != null) {
                                    adapter.setAvailableFriends(availableFriends);
                                }
                            });
                        }
                    } catch (JSONException e) {
                        Log.e("AudioListFragment", "Error parsing friends", e);
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "Ошибка обработки списка друзей", Toast.LENGTH_SHORT).show());
                    }
                }
            }
        });
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

    private void navigateToAuth() {
        Intent intent = new Intent(getContext(), AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void setLoadingState(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(loading ? View.GONE : View.VISIBLE);
    }

    private void showErrorState(String message) {
        errorTextView.setText(message);
        errorTextView.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    private void hideErrorState() {
        errorTextView.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
    }

    private void showContentState() {
        recyclerView.setVisibility(View.VISIBLE);
        errorTextView.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    public static class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {

        private List<Audio> audioList;
        private OnItemClickListener onItemClickListener;
        private Context context;
        private List<Message> availableChats = new ArrayList<>();
        private List<Friend> availableFriends = new ArrayList<>();

        public interface OnItemClickListener {
            void onItemClick(int position);
        }

        public AudioAdapter(List<Audio> audioList, Context context) {
            this.audioList = new ArrayList<>(audioList);
            this.context = context;
        }

        public void updateAudioList(List<Audio> newAudioList) {
            Log.d("AudioAdapter", "Updating list from " + this.audioList.size() + " to " + newAudioList.size() + " items");
            this.audioList.clear();
            this.audioList.addAll(newAudioList);
            notifyDataSetChanged();
        }

        public void setAvailableChats(List<Message> chats) {
            this.availableChats.clear();
            this.availableChats.addAll(chats);
        }

        public void setAvailableFriends(List<Friend> friends) {
            this.availableFriends.clear();
            this.availableFriends.addAll(friends);
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            this.onItemClickListener = listener;
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
            Audio audio = audioList.get(position);

            holder.artistTextView.setText(audio.getArtist());
            holder.titleTextView.setText(audio.getTitle());

            holder.itemView.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(position);
                }
            });

            holder.menuButton.setOnClickListener(v -> {
                showPopupMenu(v, audio, position);
            });
        }

        @Override
        public int getItemCount() {
            return audioList.size();
        }

        private void showPopupMenu(View view, Audio audio, int position) {
            PopupMenu popupMenu = new PopupMenu(context, view);
            popupMenu.inflate(R.menu.audio_item_menu);

            popupMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.menu_copy_url) {
                    copyAudioUrl(audio);
                    return true;
                } else if (item.getItemId() == R.id.menu_share) {
                    shareAudioUrl(audio);
                    return true;
                } else if (item.getItemId() == R.id.menu_share_chat) {
                    shareAudioToChat(audio);
                    return true;
                } else if (item.getItemId() == R.id.menu_share_friends) {
                    shareAudioToFriends(audio);
                    return true;
                }
                return false;
            });

            popupMenu.show();
        }

        private void shareAudioToFriends(Audio audio) {
            if (availableFriends.isEmpty()) {
                Toast.makeText(context, "Список друзей пуст", Toast.LENGTH_SHORT).show();
                return;
            }

            ShareToFriendsBottomSheet bottomSheet = ShareToFriendsBottomSheet.newInstance(
                    audio,
                    new ArrayList<>(availableFriends)
            );

            bottomSheet.setShareAudioListener((friend, audioToShare) -> {
                sendAudioToFriend(friend, audioToShare);
            });

            if (context instanceof FragmentActivity) {
                bottomSheet.show(((FragmentActivity) context).getSupportFragmentManager(),
                        "share_audio_friends_bottom_sheet");
            }
        }

        private void sendAudioToFriend(Friend friend, Audio audio) {
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
                            ((Activity) context).runOnUiThread(() ->
                                    Toast.makeText(context, "Аудио отправлено другу: " + friend.getName(),
                                            Toast.LENGTH_SHORT).show());
                        }
                    }
                });
            } catch (Exception e) {
                Toast.makeText(context, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        private void shareAudioToChat(Audio audio) {
            if (availableChats.isEmpty()) {
                Toast.makeText(context, "Список диалогов пуст", Toast.LENGTH_SHORT).show();
                return;
            }

            ShareAudioBottomSheet bottomSheet = ShareAudioBottomSheet.newInstance(
                    audio,
                    new ArrayList<>(availableChats)
            );

            bottomSheet.setShareAudioListener((chat, audioToShare) -> {
                sendAudioToChat(chat, audioToShare);
            });

            if (context instanceof FragmentActivity) {
                bottomSheet.show(((FragmentActivity) context).getSupportFragmentManager(),
                        "share_audio_bottom_sheet");
            }
        }

        private void sendAudioToChat(Message chat, Audio audio) {
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
                            ((Activity) context).runOnUiThread(() ->
                                    Toast.makeText(context, "Аудио отправлено в диалог",
                                            Toast.LENGTH_SHORT).show());
                        }
                    }
                });
            } catch (Exception e) {
                Toast.makeText(context, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        private void copyAudioUrl(Audio audio) {
            String audioUrl = audio.getUrl();
            if (audioUrl != null && !audioUrl.isEmpty() && !audioUrl.equals("null")) {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Audio URL", audioUrl);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Ссылка на аудио скопирована", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Ссылка недоступна", Toast.LENGTH_SHORT).show();
            }
        }

        private void shareAudioUrl(Audio audio) {
            String audioUrl = audio.getUrl();
            if (audioUrl != null && !audioUrl.isEmpty() && !audioUrl.equals("null")) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, audioUrl);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, audio.getArtist() + " - " + audio.getTitle());
                context.startActivity(Intent.createChooser(shareIntent, "Поделиться аудиозаписью"));
            } else {
                Toast.makeText(context, "Ссылка недоступна", Toast.LENGTH_SHORT).show();
            }
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView artistTextView, titleTextView;
            ImageView menuButton;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                artistTextView = itemView.findViewById(R.id.artistText);
                titleTextView = itemView.findViewById(R.id.titleText);
                menuButton = itemView.findViewById(R.id.downloadButton);
            }
        }
    }

    public static class Friend {
        private String id;
        private String name;
        private String photoUrl;

        public Friend(String id, String name, String photoUrl) {
            this.id = id;
            this.name = name;
            this.photoUrl = photoUrl;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getPhotoUrl() { return photoUrl; }
    }
}