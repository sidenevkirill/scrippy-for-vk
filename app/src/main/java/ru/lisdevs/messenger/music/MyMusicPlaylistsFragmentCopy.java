package ru.lisdevs.messenger.music;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.playlists.PlaylistPageFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;


public class MyMusicPlaylistsFragmentCopy extends Fragment {

    private RecyclerView recyclerView;
    private AudioAdapter adapter;
    private List<Audio> audioList = new ArrayList<>();
    private List<Audio> fullAudioList = new ArrayList<>();

    // Пагинация
    private int currentOffset = 0;
    private static final int PAGE_SIZE = 200;
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

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        okHttpClient = new OkHttpClient();
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
        View view = inflater.inflate(R.layout.activity_my_music, container, false);

        initViews(view);
        setupRecyclerView();
        setupPlaylistsRecyclerView();
        setupToolbar();
        setupScrollListener();

        refreshAudioList();
        getAudioCount();
        fetchPlaylists();

        return view;
    }

    private void initViews(View view) {
        textViewResult = view.findViewById(R.id.count);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        toolbar = view.findViewById(R.id.toolbar);
        recyclerView = view.findViewById(R.id.friendsRecyclerView);
        progressBar = view.findViewById(R.id.playlistsProgressBar);

        // Инициализация элементов для плейлистов
        playlistsRecyclerView = view.findViewById(R.id.recyclerViewPlaylists);
        playlistsProgressBar = view.findViewById(R.id.playlistsProgressBar);
        refreshPlaylistsButton = view.findViewById(R.id.swipeRefreshLayout);

        swipeRefreshLayout.setOnRefreshListener(this::refreshAudioList);

        ImageView shuffleButton = view.findViewById(R.id.shuffle);
        shuffleButton.setOnClickListener(v -> shuffleAudioList());

        ImageView sortButton = view.findViewById(R.id.sort);
        sortButton.setOnClickListener(v -> showSortDialog());

        // Кнопка для обновления плейлистов
        refreshPlaylistsButton.setOnClickListener(v -> fetchPlaylists());
    }

    private void setupToolbar() {
        toolbar.inflateMenu(R.menu.search);

        // Находим элемент поиска
        searchMenuItem = toolbar.getMenu().findItem(R.id.action_search);
        searchView = (SearchView) searchMenuItem.getActionView();

        setupSearchView();

        toolbar.setOnMenuItemClickListener(item -> {
           // if (item.getItemId() == R.id.action_show_friends) {
           //     navigateToFriends();
           //     return true;
             if (item.getItemId() == R.id.action_search) {
                // Обработка открытия поиска
                return true;
           // } else if (item.getItemId() == R.id.action_filter) {
           //     showGenreSelectionBottomSheet();
           //     return true;
            }
            return false;
        });
    }

    private void setupSearchView() {
        if (searchView == null) return;

        // Настройка SearchView - теперь он изначально свернут
        searchView.setQueryHint("Поиск по трекам");
        searchView.setIconifiedByDefault(true); // Теперь изначально свернут
        searchView.setIconified(true); // Свернут по умолчанию

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

        // Дополнительная настройка для красивого отображения
        searchView.setMaxWidth(Integer.MAX_VALUE);

        // Настраиваем иконку поиска
        ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_button);
        if (searchIcon != null) {
            searchIcon.setImageResource(R.drawable.ic_search);
        }

        // Настраиваем поле ввода
        EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (searchEditText != null) {
            searchEditText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
            searchEditText.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
        }
    }

    private void setupPlaylistsRecyclerView() {
        playlistsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(),
                LinearLayoutManager.HORIZONTAL, false));
        playlistsAdapter = new PlaylistsAdapter(playlistList, this::openPlaylist, this::showPlaylistMenu);
        playlistsRecyclerView.setAdapter(playlistsAdapter);
    }

    private void fetchPlaylists() {
        playlistsProgressBar.setVisibility(View.VISIBLE);
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        String userId = TokenManager.getInstance(getContext()).getUserId();

        if (accessToken == null || userId == null) {
            playlistsProgressBar.setVisibility(View.GONE);
            return;
        }

        String url = "https://api.vk.com/method/audio.getPlaylists" +
                "?owner_id=" + userId +
                "&offset=0" +
                "&count=20" +
                "&extended=1" +
                "&v=5.131" +
                "&access_token=" + accessToken;

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    playlistsProgressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Ошибка загрузки плейлистов", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);

                    if (json.has("error")) {
                        requireActivity().runOnUiThread(() -> {
                            playlistsProgressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Ошибка API плейлистов", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    if (json.has("response")) {
                        JSONObject responseObj = json.getJSONObject("response");
                        JSONArray items = responseObj.getJSONArray("items");
                        List<Playlist> newPlaylists = parsePlaylists(items, responseObj);

                        requireActivity().runOnUiThread(() -> {
                            playlistList.clear();
                            playlistList.addAll(newPlaylists);
                            playlistsAdapter.notifyDataSetChanged();
                            playlistsProgressBar.setVisibility(View.GONE);

                            // Показываем/скрываем заголовок в зависимости от наличия плейлистов
                            if (playlistList.isEmpty()) {
                                playlistsRecyclerView.setVisibility(View.GONE);
                                refreshPlaylistsButton.setVisibility(View.GONE);
                            } else {
                                playlistsRecyclerView.setVisibility(View.VISIBLE);
                                refreshPlaylistsButton.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                } catch (JSONException e) {
                    requireActivity().runOnUiThread(() -> {
                        playlistsProgressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Ошибка обработки плейлистов", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private List<Playlist> parsePlaylists(JSONArray items, JSONObject responseObj) throws JSONException {
        List<Playlist> result = new ArrayList<>();
        Map<Long, String> ownersMap = new HashMap<>();

        // Парсим профили
        if (responseObj.has("profiles")) {
            JSONArray profiles = responseObj.getJSONArray("profiles");
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject profile = profiles.getJSONObject(i);
                long id = profile.getLong("id");
                String name = profile.getString("first_name") + " " + profile.getString("last_name");
                ownersMap.put(id, name);
            }
        }

        // Парсим группы
        if (responseObj.has("groups")) {
            JSONArray groups = responseObj.getJSONArray("groups");
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);
                long id = -group.getLong("id"); // ID групп отрицательные
                String name = group.getString("name");
                ownersMap.put(id, name);
            }
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject playlistJson = items.getJSONObject(i);
            Playlist playlist = new Playlist();
            playlist.id = playlistJson.getInt("id");
            playlist.title = playlistJson.getString("title");
            playlist.ownerId = playlistJson.getLong("owner_id");
            playlist.count = playlistJson.optInt("count", 0);

            // Получаем имя владельца
            playlist.ownerName = ownersMap.get(playlist.ownerId);

            // Парсим фото
            if (playlistJson.has("photo")) {
                JSONObject photoJson = playlistJson.getJSONObject("photo");
                playlist.photo = new Photo();
                playlist.photo.photo_300 = photoJson.optString("photo_300", "");
            }

            result.add(playlist);
        }
        return result;
    }

    private void openPlaylist(Playlist playlist) {
        Fragment fragment = PlaylistPageFragment.newInstance(playlist.id);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void showPlaylistMenu(Playlist playlist) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_album_menu, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        TextView copyLink = bottomSheetView.findViewById(R.id.copy_link);
        TextView deletePlaylist = bottomSheetView.findViewById(R.id.add_to_my_albums);
        TextView editPlaylist = bottomSheetView.findViewById(R.id.edit_playlists);
        TextView sharePlaylist = bottomSheetView.findViewById(R.id.share_playlists);
        TextView downPlaylist = bottomSheetView.findViewById(R.id.down_playlists);

        setMenuItem(copyLink, "Скопировать ссылку", R.drawable.content_copy);
        setMenuItem(deletePlaylist, "Удалить", R.drawable.delete);
        setMenuItem(editPlaylist, "Изменить", R.drawable.edit_24px);
        setMenuItem(sharePlaylist, "Поделиться", R.drawable.share_black);
        setMenuItem(downPlaylist, "Скачать", R.drawable.save_black);

        copyLink.setOnClickListener(v -> {
            copyPlaylistLink(playlist);
            bottomSheetDialog.dismiss();
        });

        deletePlaylist.setOnClickListener(v -> {
            deletePlaylist(playlist);
            bottomSheetDialog.dismiss();
        });

        editPlaylist.setOnClickListener(v -> {
            showEditPlaylistDialog(playlist);
            bottomSheetDialog.dismiss();
        });

        sharePlaylist.setOnClickListener(v -> {
            sharePlaylist(playlist);
            bottomSheetDialog.dismiss();
        });

        downPlaylist.setOnClickListener(v -> {
            downloadPlaylist(playlist);
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void setMenuItem(TextView menuItem, String text, @DrawableRes int iconRes) {
        menuItem.setText(text);
        Drawable icon = ContextCompat.getDrawable(requireContext(), iconRes);
        if (icon != null) {
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            menuItem.setCompoundDrawables(icon, null, null, null);
            menuItem.setCompoundDrawablePadding(16);
        }
    }

    private void copyPlaylistLink(Playlist playlist) {
        String link = "https://vk.com/music/playlist/" + playlist.ownerId + "_" + playlist.id;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Playlist Link", link);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), "Ссылка скопирована", Toast.LENGTH_SHORT).show();
    }

    private void deletePlaylist(Playlist playlist) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Удалить плейлист")
                .setMessage("Вы уверены, что хотите удалить этот плейлист?")
                .setPositiveButton("Удалить", (dialog, which) -> performDeletePlaylist(playlist))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void performDeletePlaylist(Playlist playlist) {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken == null) return;

        String url = "https://api.vk.com/method/audio.deletePlaylist" +
                "?access_token=" + accessToken +
                "&owner_id=" + playlist.ownerId +
                "&playlist_id=" + playlist.id +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showToast("Ошибка соединения");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);

                    if (json.has("response") && json.getInt("response") == 1) {
                        requireActivity().runOnUiThread(() -> {
                            showToast("Плейлист удален");
                            fetchPlaylists(); // Обновляем список
                        });
                    } else {
                        showToast("Ошибка удаления");
                    }
                } catch (JSONException e) {
                    showToast("Ошибка обработки");
                }
            }
        });
    }

    private void showEditPlaylistDialog(Playlist playlist) {
        // Реализация редактирования плейлиста
        Toast.makeText(getContext(), "Редактирование плейлиста", Toast.LENGTH_SHORT).show();
    }

    private void sharePlaylist(Playlist playlist) {
        String link = "https://vk.com/music/playlist/" + playlist.ownerId + "_" + playlist.id;
        String shareText = "Послушайте мой плейлист: " + playlist.title + "\n" + link;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Поделиться плейлистом"));
    }

    private void downloadPlaylist(Playlist playlist) {
        Toast.makeText(getContext(), "Загрузка плейлиста: " + playlist.title, Toast.LENGTH_SHORT).show();
    }

    private void showSortDialog() {
        CharSequence[] items = {
                createItemWithIcon("По названию (А-Я)", R.drawable.sort_alphabetical_ascending),
                createItemWithIcon("По исполнителю", R.drawable.sort_variant),
                createItemWithIcon("По длительности", R.drawable.sort_clock_ascending_outline),
                createItemWithIcon("По дате добавления", R.drawable.sort_calendar_ascending)
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
            case 3:
                // По дате добавления
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

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AudioAdapter(audioList);
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(position -> {
            if (position >= 0 && position < audioList.size()) {
                playTrack(position);
            }
        });

        adapter.setOnMenuClickListener(this::showBottomSheet);
    }

    private void setupScrollListener() {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                if (!isLoading && hasMoreItems && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0 && totalItemCount >= PAGE_SIZE) {
                    loadMoreAudio();
                }
            }
        });
    }

    private void loadMoreAudio() {
        if (isLoading || !hasMoreItems) return;

        isLoading = true;
        progressBar.setVisibility(View.VISIBLE);

        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken != null) {
            fetchAudio(accessToken, currentOffset, true);
        }
    }

    private void refreshAudioList() {
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
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    isLoading = false;
                    swipeRefreshLayout.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

                            if (currentGenreId == 0) {
                                audioList.addAll(newList);
                            } else {
                                for (Audio audio : newList) {
                                    if (audio.getGenreId() == currentGenreId) {
                                        audioList.add(audio);
                                    }
                                }
                            }

                            adapter.notifyDataSetChanged();
                            updateCountText("Мои треки:" + " " + totalCount);
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

            if (url != null && !url.isEmpty()) {
                Audio audio = new Audio(artist, title, url);
                audio.setGenreId(genreId);
                audio.setOwnerId(ownerId);
                audio.setAudioId(audioId);
                audio.setLyricsId(lyricsId);
                audio.setDuration(duration);
                result.add(audio);
            }
        }
        return result;
    }

    private void playTrack(int position) {
        Audio audio = audioList.get(position);
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
        }
    }

    private void showGenreSelectionBottomSheet() {
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_genres, null);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(view);

        RecyclerView genresRv = view.findViewById(R.id.genresRecyclerView);
        genresRv.setLayoutManager(new LinearLayoutManager(getContext()));

        GenreAdapter genreAdapter = new GenreAdapter(new ArrayList<>(genres.keySet()));
        genresRv.setAdapter(genreAdapter);

        genreAdapter.setOnItemClickListener(genreName -> {
            int genreId = genres.get(genreName);
            if (genreId != currentGenreId) {
                currentGenreId = genreId;
                filterByGenre(genreId);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void filterByGenre(int genreId) {
        List<Audio> filteredList = new ArrayList<>();

        if (genreId == 0) {
            filteredList.addAll(fullAudioList);
        } else {
            for (Audio audio : fullAudioList) {
                if (audio.getGenreId() == genreId) {
                    filteredList.add(audio);
                }
            }
        }

        if (filteredList.isEmpty() && genreId != 0) {
            Toast.makeText(getContext(), "Нет треков в выбранном жанре", Toast.LENGTH_SHORT).show();
            return;
        }

        audioList.clear();
        audioList.addAll(filteredList);
        adapter.notifyDataSetChanged();
        updateCountText("Треки: " + audioList.size() + " из " + totalCount);
    }

    public void filterTracks(String query) {
        List<Audio> filteredList = new ArrayList<>();
        String lowerCaseQuery = query.toLowerCase();

        for (Audio audio : fullAudioList) {
            if (audio.getTitle().toLowerCase().contains(lowerCaseQuery) ||
                    audio.getArtist().toLowerCase().contains(lowerCaseQuery)) {

                if (currentGenreId == 0 || audio.getGenreId() == currentGenreId) {
                    filteredList.add(audio);
                }
            }
        }

        audioList.clear();
        audioList.addAll(filteredList);
        adapter.notifyDataSetChanged();
        updateCountText("Найдено: " + audioList.size() + " из " + totalCount);

        if (filteredList.isEmpty() && !query.isEmpty()) {
            Toast.makeText(getContext(), "Ничего не найдено", Toast.LENGTH_SHORT).show();
        }
    }

    public void resetSearch() {
        if (currentGenreId == 0) {
            audioList.clear();
            audioList.addAll(fullAudioList);
        } else {
            filterByGenre(currentGenreId);
        }
        adapter.notifyDataSetChanged();
        updateCountText("Треки: " + audioList.size() + " из " + totalCount);
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

        view.findViewById(R.id.buttonAlbum).setOnClickListener(v -> {
            deleteTrack(audio);
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonCopy).setOnClickListener(v -> {
            copyAudioLink(audio);
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonOpenLyrics).setOnClickListener(v -> {
            if (audio.getLyricsId() > 0) {
                showLyrics(audio.getOwnerId(), audio.getAudioId(), audio.getLyricsId());
            } else {
                Toast.makeText(getContext(), "Текст песни недоступен", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        dialog.show();
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
                        .header("User-Agent", Authorizer.getKateUserAgent()) // Используйте ваш User-Agent
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
                                // Успешное удаление возвращает 1
                                int result = json.getInt("response");
                                if (result == 1) {
                                    requireActivity().runOnUiThread(() ->
                                            showToast("Трек удален"));
                                    // Обновите UI если нужно
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

    // Дополнительный метод с параметрами ownerId и audioId
    private void deleteTrack(long ownerId, long audioId) {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        String url = "https://api.vk.com/method/audio.delete" +
                "?access_token=" + accessToken +
                "&owner_id=" + ownerId +
                "&audio_id=" + audioId +
                "&v=5.131";

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", Authorizer.getKateUserAgent()) // Используйте ваш User-Agent
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        requireActivity().runOnUiThread(() ->
                                showToast("Ошибка сети"));
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.has("response") && json.getInt("response") == 1) {
                                requireActivity().runOnUiThread(() ->
                                        showToast("Трек удален"));
                            } else if (json.has("error")) {
                                String errorMsg = json.getJSONObject("error").optString("error_msg");
                                requireActivity().runOnUiThread(() ->
                                        showToast("Ошибка: " + errorMsg));
                            }
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() ->
                                    showToast("Ошибка обработки"));
                        }
                    }
                });
    }

    // Метод для обновления списка треков после удаления
    private void refreshTrackList() {
        // Реализуйте логику обновления вашего списка треков
        // Например, перезагрузите данные с сервера
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                // Обновите адаптер или перезагрузите данные
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
                        .header("User-Agent", Authorizer.getKateUserAgent())
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
                    // Реализация добавления в плейлист
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showLyrics(long ownerId, long audioId, int lyricsId) {
       // LyricsBottomSheet lyricsSheet = LyricsBottomSheet.newInstance(ownerId, audioId, lyricsId);
        //  lyricsSheet.show(getParentFragmentManager(), "lyrics_bottom_sheet");
    }

    private void navigateToFriends() {
        // Навигация к списку друзей
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
                        //showApiError(json.getJSONObject("error"));
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
         //   holder.menuButton.setOnClickListener(v -> menuListener.onMenuClick(playlist));
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ShapeableImageView image;
            TextView title;
            TextView count;
        //    ImageView menuButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.coverImage);
                title = itemView.findViewById(R.id.titleText);
                count = itemView.findViewById(R.id.countText);
               // menuButton = itemView.findViewById(R.id.playlist_menu);
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