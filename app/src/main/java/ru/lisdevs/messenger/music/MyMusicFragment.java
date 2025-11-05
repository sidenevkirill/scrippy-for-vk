package ru.lisdevs.messenger.music;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.model.Audio;
import ru.lisdevs.messenger.playlists.VkPlaylistsFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;

/**
 * Фрагмент для отображения списка музыки из VK и управления воспроизведением.
 */
import androidx.annotation.Nullable;

import android.widget.ProgressBar;

import java.util.Set;
import java.util.concurrent.TimeUnit;


public class MyMusicFragment extends Fragment {

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
    private boolean isSearchActive = false;

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
        put("Электропоп & дискотека", 22);
    }};

    private int currentGenreId = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        okHttpClient = new OkHttpClient();
        setHasOptionsMenu(true); // Включаем меню в фрагменте
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
        View view = inflater.inflate(R.layout.fragment_my_music, container, false);

        initViews(view);
        setupRecyclerView();
        setupToolbar();
        setupScrollListener();

        refreshAudioList();
        getAudioCount();

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.main_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);

        // Настройка поиска
        searchMenuItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) searchMenuItem.getActionView();
        setupSearchView();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_show_friends) {
            navigateToFriends();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupSearchView() {
        if (searchView != null) {
            searchView.setQueryHint("Поиск треков...");

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

            searchView.setOnSearchClickListener(v -> {
                isSearchActive = true;
            });

            searchView.setOnCloseListener(() -> {
                isSearchActive = false;
                resetSearch();
                return false;
            });

            // Кастомная иконка для поиска
            int searchIconId = getResources().getIdentifier("android:id/search_mag_icon", null, null);
            ImageView searchIcon = searchView.findViewById(searchIconId);
            if (searchIcon != null) {
                searchIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_search));
            }
        }
    }

    private void initViews(View view) {
        textViewResult = view.findViewById(R.id.count);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        toolbar = view.findViewById(R.id.toolbar);
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);

        swipeRefreshLayout.setOnRefreshListener(this::refreshAudioList);

        ImageView shuffleButton = view.findViewById(R.id.shuffle);
        shuffleButton.setOnClickListener(v -> shuffleAudioList());

        ImageView sortButton = view.findViewById(R.id.sort);
        sortButton.setOnClickListener(v -> showSortDialog());

        // ImageView genreButton = view.findViewById(R.id.sort);
        // genreButton.setOnClickListener(v -> showGenreSelectionBottomSheet());
    }

    private void setupToolbar() {
        toolbar.inflateMenu(R.menu.main_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.action_show_friends) {
                navigateToFriends();
                return true;
            }

            return false;
        });
    }

    private void navigateToFriends() {
        // Навигация к списку друзей
        VkPlaylistsFragment vkPlaylistsFragment = new VkPlaylistsFragment();
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, vkPlaylistsFragment)
                .addToBackStack(null)
                .commit();
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
                            updateCountText("Треки:" + " " + totalCount);
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
                Log.e("Download", "Error downloading file", e);
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
        //lyricsSheet.show(getParentFragmentManager(), "lyrics_bottom_sheet");
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
                // Сортировка по дате
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
                        // showApiError(json.getJSONObject("error"));
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