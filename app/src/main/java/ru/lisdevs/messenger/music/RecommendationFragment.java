package ru.lisdevs.messenger.music;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.BaseActivity;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.player.PlayerBottomSheetFragment;
import ru.lisdevs.messenger.playlists.VkPlaylistsFragment;
import ru.lisdevs.messenger.search.MusicSearchFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;

/**
 * Фрагмент для отображения списка музыки из VK и управления воспроизведением.
 */
public class RecommendationFragment extends Fragment {

    private RecyclerView recyclerView;
    private AudioAdapter adapter;
    private List<Audio> audioList = new ArrayList<>();
    private List<Audio> fullAudioList = new ArrayList<>();

    private TextView textViewResult;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Toolbar toolbar;

    // Пагинация
    private int currentOffset = 0;
    private static final int PAGE_SIZE = 500;
    private boolean isLoading = false;
    private boolean hasMore = true;

    // Жанры
    private final Map<String, Integer> genres = new LinkedHashMap<String, Integer>() {{
        put("Все жанры", 0);
        put("Rock", 1);
        put("Pop", 2);
        put("Rap & Hip-Hop", 3);
        put("Easy Listening", 4);
        put("Dance & House", 5);
        put("Instrumental", 6);
        put("Metal", 7);
        put("Dubstep", 8);
        put("Jazz & Blues", 9);
        put("Drum & Bass", 10);
        put("Trance", 11);
        put("Chanson", 12);
        put("Ethnic", 13);
        put("Acoustic & Vocal", 14);
        put("Reggae", 15);
        put("Classical", 16);
        put("Indie Pop", 17);
        put("Other", 18);
        put("Speech", 19);
        put("Alternative", 21);
        put("Electropop & Disco", 22);
    }};

    private int currentGenreId = 0;

    public static RecommendationFragment newInstance() {
        RecommendationFragment fragment = new RecommendationFragment();
        Bundle args = new Bundle();

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends_music, container, false);

        // Инициализация всех View
        initViews(view);

        // Настройка RecyclerView
        setupRecyclerView();

        // Настройка Toolbar
       // setupToolbar();

        // Загрузка данных
        refreshAudioList();
        getAudioCount();

        return view;
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
            toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        }
    }

    private void initViews(View view) {
        textViewResult = view.findViewById(R.id.count);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        toolbar = view.findViewById(R.id.toolbar);
        recyclerView = view.findViewById(R.id.recyclerView);

        // Проверка на null всех важных View
        if (recyclerView == null) {
            throw new IllegalStateException("RecyclerView not found in layout");
        }

        // Настройка SwipeRefresh
        swipeRefreshLayout.setOnRefreshListener(this::refreshAudioList);

        // Кнопка перемешивания
        ImageView shuffleButton = view.findViewById(R.id.shuffle);
        shuffleButton.setOnClickListener(v -> shuffleAudioList());

        // Кнопка сортировки по жанрам
        ImageView sortButton = view.findViewById(R.id.sort);
        sortButton.setOnClickListener(v -> showGenreSelectionBottomSheet());
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        adapter = new AudioAdapter(audioList);
        recyclerView.setAdapter(adapter);

        // Добавляем слушатель прокрутки для пагинации
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                if (!isLoading && hasMore) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= PAGE_SIZE) {
                        loadMoreAudio();
                    }
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
        if (isLoading || !hasMore) return;

        isLoading = true;
        currentOffset += PAGE_SIZE;

        // Показываем индикатор загрузки внизу списка
        //adapter.showLoading(true);

        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken != null) {
            fetchAudio(accessToken, currentOffset, true);
        }
    }

    private void refreshAudioList() {
        currentOffset = 0;
        hasMore = true;
        isLoading = false;

        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken != null) {
            fetchAudio(accessToken, 0, false);
        } else {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void fetchAudio(String accessToken, int offset, boolean isLoadMore) {
        if (!isLoadMore) {
            swipeRefreshLayout.setRefreshing(true);
        }

        String url = "https://api.vk.com/method/audio.getRecommendations" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&count=" + PAGE_SIZE +
                "&offset=" + offset;

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", getUserAgent())
                        .build())

                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        requireActivity().runOnUiThread(() -> {
                            if (!isLoadMore) {
                                swipeRefreshLayout.setRefreshing(false);
                            } else {
                               // adapter.showLoading(false);
                                isLoading = false;
                            }
                            Toast.makeText(getContext(), "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.has("response")) {
                                JSONObject responseObj = json.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");

                                // Проверяем, есть ли еще треки для загрузки
                                int totalCount = responseObj.optInt("count", 0);
                                hasMore = (currentOffset + items.length()) < totalCount;

                                List<Audio> newList = parseAudioItems(items);
                                requireActivity().runOnUiThread(() -> {
                                    if (isLoadMore) {
                                        appendAudioList(newList);
                                    } else {
                                        updateAudioList(newList);
                                    }
                                    if (!isLoadMore) {
                                        swipeRefreshLayout.setRefreshing(false);
                                    } else {
                                      //  adapter.showLoading(false);
                                        isLoading = false;
                                    }
                                });
                            } else if (json.has("error")) {
                            //    showApiError(json.getJSONObject("error"));
                                requireActivity().runOnUiThread(() -> {
                                    if (!isLoadMore) {
                                        swipeRefreshLayout.setRefreshing(false);
                                    } else {
                                     //   adapter.showLoading(false);
                                        isLoading = false;
                                        hasMore = false; // Прекращаем загрузку при ошибке
                                    }
                                });
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                if (!isLoadMore) {
                                    swipeRefreshLayout.setRefreshing(false);
                                } else {
                                 //   adapter.showLoading(false);
                                    isLoading = false;
                                }
                                Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
    }

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

    private void appendAudioList(List<Audio> newList) {
        fullAudioList.addAll(newList);

        // Применяем текущий фильтр жанра
        if (currentGenreId == 0) {
            audioList.addAll(newList);
        } else {
            List<Audio> filtered = new ArrayList<>();
            for (Audio audio : newList) {
                if (audio.getGenreId() == currentGenreId) {
                    filtered.add(audio);
                }
            }
            audioList.addAll(filtered);
        }

        adapter.notifyDataSetChanged();
    }

    private void updateAudioList(List<Audio> newList) {
        fullAudioList.clear();
        fullAudioList.addAll(newList);

        // Применяем текущий фильтр жанра
        if (currentGenreId == 0) {
            audioList.clear();
            audioList.addAll(newList);
        } else {
            List<Audio> filtered = new ArrayList<>();
            for (Audio audio : newList) {
                if (audio.getGenreId() == currentGenreId) {
                    filtered.add(audio);
                }
            }
            audioList.clear();
            audioList.addAll(filtered);
        }

        adapter.notifyDataSetChanged();
    }

    // Остальные методы остаются без изменений...
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

        // Передаем весь плейлист и текущую позицию для переключения треков
        ArrayList<Audio> playlist = new ArrayList<>(audioList);
        intent.putParcelableArrayListExtra("PLAYLIST", playlist);
        intent.putExtra("POSITION", position);

        ContextCompat.startForegroundService(requireContext(), intent);

        // Показываем плеер
        showPlayerBottomSheet();
    }

    private void showPlayerBottomSheet() {
        PlayerBottomSheetFragment playerFragment = new PlayerBottomSheetFragment();
        playerFragment.show(getParentFragmentManager(), "player_bottom_sheet");
    }

    private void shuffleAudioList() {
        if (!audioList.isEmpty()) {
            Collections.shuffle(audioList);
            adapter.notifyDataSetChanged();
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
                currentOffset = 0;
                hasMore = true;
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

        // Если отфильтрованный список пустой, пробуем загрузить больше
        if (audioList.isEmpty() && hasMore) {
            loadMoreAudio();
        }
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
            int duration = track.optInt("duration", 0);

            if (url != null && !url.isEmpty()) {
                Audio audio = new Audio(artist, title, url);
                audio.setGenreId(genreId);
                audio.setOwnerId(ownerId);
                audio.setAudioId(audioId);
                audio.setDuration(duration);
                result.add(audio);
            }
        }
        return result;
    }

    private void showApiError(JSONObject error) {
        int code = error.optInt("error_code");
        String msg = error.optString("error_msg");
//        Toast.makeText(getContext(), "Ошибка API (" + code + "): " + msg, Toast.LENGTH_LONG).show();
    }

    private void navigateToFriends() {
        VkPlaylistsFragment fragment = new VkPlaylistsFragment();
        getParentFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void getAudioCount() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/audio.getCount")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("owner_id", TokenManager.getInstance(getContext()).getUserId())
                .addQueryParameter("v", "5.131")
                .build();

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", Authorizer.getKateUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        updateCountText("Ошибка сети: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.has("response")) {
                                int count = json.getJSONObject("response").getInt("count");
                                updateCountText("Треков: " + count);
                            }
                        } catch (JSONException e) {
                            updateCountText("Треки: 200");
                        }
                    }
                });
    }

    private void updateCountText(String text) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> textViewResult.setText(text));
        }
    }

    private void showBottomSheet(Audio audio) {
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_audio_del, null);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(view);

        view.findViewById(R.id.buttonDownload).setOnClickListener(v -> {
            downloadTrack(audio);
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonCopy).setOnClickListener(v -> {
            copyAudioLink(audio);
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonSearch).setOnClickListener(v -> {
            searchArtist(audio.getArtist());
            dialog.dismiss();
        });

        view.findViewById(R.id.buttonAlbum).setOnClickListener(v -> {
            addToMyMusic(audio);
            dialog.dismiss();
        });

        dialog.show();
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

    private void copyAudioLink(Audio audio) {
        String link = audio.getUrl();
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Playlist Link", link);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), "Ссылка скопирована", Toast.LENGTH_SHORT).show();
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

    private void addToMyMusic(Audio audio) {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        String url = "https://api.vk.com/method/audio.add" +
                "?access_token=" + accessToken +
                "&owner_id=" + audio.getOwnerId() +
                "&audio_id=" + audio.getAudioId() +
                "&v=5.131";

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", "VKAndroidApp/5.52-4543")
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
                                showToast("Трек добавлен");
                            } else if (json.has("error")) {
                                showToast("Ошибка: " + json.getJSONObject("error").optString("error_msg"));
                            }
                        } catch (JSONException e) {
                            showToast("Ошибка обработки");
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