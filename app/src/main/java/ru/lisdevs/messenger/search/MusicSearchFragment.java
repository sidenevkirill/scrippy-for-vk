package ru.lisdevs.messenger.search;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.playlists.AlbumPageFragment;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.artist.ArtistTracksFragment;
import ru.lisdevs.messenger.artist.ArtistsAdapter;
import ru.lisdevs.messenger.music.Search;
import ru.lisdevs.messenger.official.audios.Audio;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;


public class MusicSearchFragment extends Fragment {

    private static final String API_VERSION = "5.131";
    private static final int SEARCH_LIMIT = 200;

    // UI элементы для поиска музыки
    private AutoCompleteTextView editTextSearch;
    private ImageView buttonSearch;
    private ImageView buttonPopular;
    private TextView textViewResults;
    private RecyclerView recyclerViewTracks;
    private RecyclerView recyclerViewAlbums;
    private RecyclerView recyclerViewArtists;
    private SearchAdapter trackAdapter;
    private AlbumAdapter albumAdapter;
    private ArtistAdapter artistAdapter;
    private List<Search> searchList = new ArrayList<>();
    private List<VKAlbum> albumList = new ArrayList<>();
    private List<VKArtist> artistList = new ArrayList<>();
    private ProgressBar progressBar;

    // Для популярных исполнителей
    private List<String> popularArtists = new ArrayList<>();
    private boolean isPopularArtistsLoaded = false;

    // Для управления воспроизведением
    private MusicPlayerService musicService;
    private boolean serviceBound = false;
    private int currentTrackPosition = -1;

    // BroadcastReceiver для получения состояния плеера
    private BroadcastReceiver playerStateReceiver;

    public static MusicSearchFragment newInstance() {
        MusicSearchFragment fragment = new MusicSearchFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            musicService = binder.getService();
            serviceBound = true;
            updatePlayerState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_music_search_combined, container, false);

        // Инициализация UI
        editTextSearch = view.findViewById(R.id.editTextSearch);
        buttonSearch = view.findViewById(R.id.buttonSearch);
        buttonPopular = view.findViewById(R.id.buttonPopular);
        textViewResults = view.findViewById(R.id.count);
        recyclerViewTracks = view.findViewById(R.id.recyclerViewTracks);
        recyclerViewAlbums = view.findViewById(R.id.recyclerViewAlbum);
        recyclerViewArtists = view.findViewById(R.id.recyclerViewArtists);
        progressBar = view.findViewById(R.id.progressBar);

        // Настройка RecyclerView для треков
        recyclerViewTracks.setLayoutManager(new LinearLayoutManager(getContext()));
        trackAdapter = new SearchAdapter(searchList);
        recyclerViewTracks.setAdapter(trackAdapter);

        // Настройка RecyclerView для альбомов (горизонтальный)
        LinearLayoutManager albumLayoutManager = new LinearLayoutManager(getContext(),
                LinearLayoutManager.HORIZONTAL, false);
        recyclerViewAlbums.setLayoutManager(albumLayoutManager);
        albumAdapter = new AlbumAdapter(albumList, this::openAlbumPage);
        recyclerViewAlbums.setAdapter(albumAdapter);

        // Настройка RecyclerView для артистов (горизонтальный)
        LinearLayoutManager artistLayoutManager = new LinearLayoutManager(getContext(),
                LinearLayoutManager.HORIZONTAL, false);
        recyclerViewArtists.setLayoutManager(artistLayoutManager);
        artistAdapter = new ArtistAdapter(artistList, this::openArtistPage);
        recyclerViewArtists.setAdapter(artistAdapter);

        // Обработка кликов на треки - ВОСПРОИЗВЕДЕНИЕ ПО ОДНОМУ ТАПУ
        trackAdapter.setOnItemClickListener(position -> {
            if (position >= 0 && position < searchList.size()) {
                playTrack(position);
            }
        });

        trackAdapter.setOnMenuClickListener(search -> {
            showBottomSheetMenu(search);
        });

        // Проверяем, есть ли предустановленный запрос
        if (getArguments() != null) {
            String searchQuery = getArguments().getString("search_query");
            if (searchQuery != null && !searchQuery.isEmpty()) {
                editTextSearch.setText(searchQuery);
                // Автоматически запускаем поиск
                new Handler().postDelayed(() -> startSearch(), 300);
            }
        }

        // Кнопка поиска
        buttonSearch.setOnClickListener(v -> startSearch());

        // Кнопка популярных исполнителей
        buttonPopular.setOnClickListener(v -> showPopularArtistsBottomSheet());

        // Поиск по нажатию Enter
        editTextSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                startSearch();
                return true;
            }
            return false;
        });

        // Загружаем популярных исполнителей
        loadPopularArtists();

        // Инициализация BroadcastReceiver для получения состояния плеера
        initPlayerStateReceiver();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Привязываемся к сервису
        Intent intent = new Intent(getActivity(), MusicPlayerService.class);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        requireActivity().startService(intent);

        // Регистрируем BroadcastReceiver
        registerPlayerStateReceiver();
    }

    @Override
    public void onStop() {
        super.onStop();
        // Отвязываемся от сервиса
        if (serviceBound) {
            requireActivity().unbindService(serviceConnection);
            serviceBound = false;
        }

        // Отменяем регистрацию BroadcastReceiver
        unregisterPlayerStateReceiver();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Очищаем ресурсы
        if (playerStateReceiver != null) {
            try {
                requireContext().unregisterReceiver(playerStateReceiver);
            } catch (Exception e) {
                // Игнорируем ошибки отмены регистрации
            }
        }
    }

    private void initPlayerStateReceiver() {
        playerStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("PLAYER_STATE_CHANGED".equals(intent.getAction())) {
                    updatePlayerStateFromBroadcast(intent);

                    // Также отправляем обновление для BaseActivity
                    if (serviceBound && musicService != null) {
                        sendPlayerUpdate();
                    }
                }
            }
        };
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerPlayerStateReceiver() {
        IntentFilter filter = new IntentFilter("PLAYER_STATE_CHANGED");

        // Используем ContextCompat для кросс-версионной совместимости
        ContextCompat.registerReceiver(
                requireContext(),
                playerStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    private void unregisterPlayerStateReceiver() {
        try {
            requireContext().unregisterReceiver(playerStateReceiver);
        } catch (Exception e) {
            // Игнорируем ошибки отмены регистрации
        }
    }

    private void updatePlayerStateFromBroadcast(Intent intent) {
        boolean isPlaying = intent.getBooleanExtra("IS_PLAYING", false);
        int currentPosition = intent.getIntExtra("PLAYLIST_POSITION", -1);

        // Обновляем UI в соответствии с состоянием плеера
        if (trackAdapter != null) {
            trackAdapter.setCurrentlyPlayingPosition(isPlaying ? currentPosition : -1);
            trackAdapter.notifyDataSetChanged();
        }
    }

    private void updatePlayerState() {
        if (serviceBound && musicService != null) {
            // Можно запросить текущее состояние у сервиса
            boolean isPlaying = musicService.isPlaying();
            int currentPosition = musicService.getCurrentPlaylistPosition();

            if (trackAdapter != null) {
                trackAdapter.setCurrentlyPlayingPosition(isPlaying ? currentPosition : -1);
                trackAdapter.notifyDataSetChanged();
            }
        }
    }

    private void openArtistPage(VKArtist artist) {
        Fragment fragment = ArtistTracksFragment.newInstance(
                artist.id,
                artist.name,
                artist.photo
        );

        getParentFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void openAlbumPage(VKAlbum album) {
        Fragment fragment = AlbumPageFragment.newInstance(
                album.id,
                album.ownerId,
                album.title,
                album.artist,
                album.coverUrl
        );

        getParentFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void loadPopularArtists() {
        new Thread(() -> {
            try {
                URL url = new URL("https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/master/popular_artists.json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    reader.close();

                    JSONObject jsonObject = new JSONObject(responseBuilder.toString());
                    JSONArray queriesArray = jsonObject.getJSONArray("popular_artists");

                    popularArtists.clear();
                    for (int i = 0; i < queriesArray.length(); i++) {
                        popularArtists.add(queriesArray.getString(i));
                    }

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            isPopularArtistsLoaded = true;
                            setupAutoComplete();
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка загрузки популярных исполнителей", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }

    private void setupAutoComplete() {
        if (!isPopularArtistsLoaded || getActivity() == null) return;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                popularArtists
        );

        editTextSearch.setAdapter(adapter);
        editTextSearch.setThreshold(1);
        editTextSearch.setOnItemClickListener((parent, view, position, id) -> {
            String selectedArtist = (String) parent.getItemAtPosition(position);
            editTextSearch.setText(selectedArtist);
            startSearch();
        });
    }

    private void showPopularArtistsBottomSheet() {
        if (popularArtists.isEmpty()) {
            Toast.makeText(getContext(), "Загружаем список исполнителей...", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_artists, null);
        bottomSheetDialog.setContentView(view);

        RecyclerView artistsRecyclerView = view.findViewById(R.id.artistsRecyclerView);
        artistsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        ArtistsAdapter adapter = new ArtistsAdapter(popularArtists, artist -> {
            editTextSearch.setText(artist);
            startSearch();
            bottomSheetDialog.dismiss();
        });

        artistsRecyclerView.setAdapter(adapter);

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from((View) view.getParent());
        behavior.setPeekHeight((int) (400 * getResources().getDisplayMetrics().density));
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        bottomSheetDialog.show();
    }

    private void startSearch() {
        String query = editTextSearch.getText().toString().trim();
        if (!query.isEmpty()) {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editTextSearch.getWindowToken(), 0);

            if (!isNetworkAvailable()) {
                Toast.makeText(getContext(), "Нет интернет-соединения", Toast.LENGTH_SHORT).show();
                return;
            }

            new SearchMusicTask().execute(query);
        } else {
            Toast.makeText(getContext(), "Введите запрос", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    // Добавьте этот метод в класс MusicSearchFragment
    private void sendPlayerUpdate() {
        if (serviceBound && musicService != null) {
            @SuppressLint("UnsafeImplicitIntentLaunch") Intent broadcastIntent = new Intent("PLAYER_STATE_CHANGED");
            broadcastIntent.putExtra("TITLE", musicService.getCurrentTrackTitle());
            broadcastIntent.putExtra("ARTIST", musicService.getCurrentArtist());
            broadcastIntent.putExtra("IS_PLAYING", musicService.isPlaying());
            broadcastIntent.putExtra("PLAYLIST_POSITION", musicService.getCurrentPlaylistPosition());
            broadcastIntent.putExtra("PLAYLIST_SIZE",
                    musicService.getPlaylist() != null ? musicService.getPlaylist().size() : 0);
            requireContext().sendBroadcast(broadcastIntent);
        }
    }

    // ОСНОВНОЙ МЕТОД ВОСПРОИЗВЕДЕНИЯ ТРЕКА
    private void playTrack(int position) {
        if (position < 0 || position >= searchList.size()) return;

        Search search = searchList.get(position);
        if (search.getUrl() == null || search.getUrl().isEmpty()) {
            Toast.makeText(getContext(), "Трек недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        currentTrackPosition = position;
        trackAdapter.setCurrentlyPlayingPosition(position);

        // Создаем список Audio объектов из searchList
        ArrayList<Audio> playlist = new ArrayList<>();
        for (Search s : searchList) {
            Audio audio = new Audio(
                    s.getArtist(),
                    s.getTitle(),
                    s.getUrl()
            );
            audio.setOwnerId(s.getOwnerId());
            audio.setAudioId(s.getAudioId());
            audio.setDuration(0);
            playlist.add(audio);
        }

        // Запускаем воспроизведение через сервис
        Intent intent = new Intent(getContext(), MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_PLAY);
        intent.putExtra("URL", search.getUrl());
        intent.putExtra("TITLE", search.getTitle());
        intent.putExtra("ARTIST", search.getArtist());
        intent.putExtra("DURATION", 0);
        intent.putParcelableArrayListExtra("PLAYLIST", playlist);
        intent.putExtra("POSITION", position);

        ContextCompat.startForegroundService(requireContext(), intent);

        // НЕМЕДЛЕННО ОТПРАВЛЯЕМ BROADCAST ДЛЯ BASEACTIVITY
        @SuppressLint("UnsafeImplicitIntentLaunch") Intent broadcastIntent = new Intent("PLAYER_STATE_CHANGED");
        broadcastIntent.putExtra("TITLE", search.getTitle());
        broadcastIntent.putExtra("ARTIST", search.getArtist());
        broadcastIntent.putExtra("IS_PLAYING", true);
        broadcastIntent.putExtra("PLAYLIST_POSITION", position);
        broadcastIntent.putExtra("PLAYLIST_SIZE", playlist.size());
        broadcastIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        requireContext().sendBroadcast(broadcastIntent);

        Log.d("MusicSearch", "Track started and broadcast sent: " + search.getTitle());
        Toast.makeText(getContext(), "Воспроизведение: " + search.getTitle(), Toast.LENGTH_SHORT).show();
    }

    // Метод для отправки broadcast уведомления
    private void sendPlayerStateBroadcast(String title, String artist, boolean isPlaying, int position, int playlistSize) {
        @SuppressLint("UnsafeImplicitIntentLaunch") Intent broadcastIntent = new Intent("PLAYER_STATE_CHANGED");
        broadcastIntent.putExtra("TITLE", title);
        broadcastIntent.putExtra("ARTIST", artist);
        broadcastIntent.putExtra("IS_PLAYING", isPlaying);
        broadcastIntent.putExtra("PLAYLIST_POSITION", position);
        broadcastIntent.putExtra("PLAYLIST_SIZE", playlistSize);
        requireContext().sendBroadcast(broadcastIntent);

        Log.d("MusicSearch", "Broadcast sent - Title: " + title + ", Artist: " + artist);
    }

    private void showBottomSheetMenu(Search search) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_audio_menu, null);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(view);

        Button addButton = view.findViewById(R.id.buttonAddToMyMusic);
        Button downloadButton = view.findViewById(R.id.buttonDownload);

        addButton.setOnClickListener(v -> {
            addTrackToMyMusic(search);
            dialog.dismiss();
        });

        downloadButton.setOnClickListener(v -> {
            downloadTrack(search);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void addTrackToMyMusic(Search search) {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        String url = "https://api.vk.com/method/audio.add" +
                "?v=" + API_VERSION +
                "&access_token=" + accessToken +
                "&audio_id=" + search.getAudioId() +
                "&owner_id=" + search.getOwnerId();

        new AddTrackTask().execute(url);
    }

    private class AddTrackTask extends AsyncTask<String, Void, Boolean> {
        private String errorMessage;

        @Override
        protected Boolean doInBackground(String... params) {
            String urlString = params[0];
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", Authorizer.getKateUserAgent());
                connection.connect();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                JSONObject json = new JSONObject(response.toString());
                return !json.has("error");
            } catch (Exception e) {
                errorMessage = e.getMessage();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                Toast.makeText(getContext(), "Трек добавлен в вашу музыку", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Ошибка: " + (errorMessage != null ? errorMessage : "Не удалось добавить трек"),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void downloadTrack(Search search) {
        String fileName = search.getArtist() + " - " + search.getTitle() + ".mp3";
        DownloadManager downloadManager =
                (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(search.getUrl()))
                .setTitle(fileName)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        downloadManager.enqueue(request);
        Toast.makeText(getContext(), "Скачивание начато", Toast.LENGTH_SHORT).show();
    }

    // Внутренний класс SearchAdapter с поддержкой выделения текущего трека
    public static class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private List<Search> searchList;
        private OnItemClickListener itemClickListener;
        private OnMenuClickListener menuClickListener;
        private int currentlyPlayingPosition = -1;

        public interface OnItemClickListener {
            void onItemClick(int position);
        }

        public interface OnMenuClickListener {
            void onMenuClick(Search search);
        }

        public SearchAdapter(List<Search> searchList) {
            this.searchList = searchList;
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            this.itemClickListener = listener;
        }

        public void setOnMenuClickListener(OnMenuClickListener listener) {
            this.menuClickListener = listener;
        }

        public void setCurrentlyPlayingPosition(int position) {
            this.currentlyPlayingPosition = position;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Search search = searchList.get(position);
            holder.bind(search, position == currentlyPlayingPosition);
        }

        @Override
        public int getItemCount() {
            return searchList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private TextView titleTextView;
            private TextView artistTextView;
            private TextView durationTextView;
            private ImageView menuButton;
            private ImageView playingIndicator;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                titleTextView = itemView.findViewById(R.id.titleText);
                artistTextView = itemView.findViewById(R.id.artistText);
                //durationTextView = itemView.findViewById(R.id.duration_text);
                menuButton = itemView.findViewById(R.id.menu_button);
                playingIndicator = itemView.findViewById(R.id.playing_indicator);

                itemView.setOnClickListener(v -> {
                    if (itemClickListener != null) {
                        itemClickListener.onItemClick(getAdapterPosition());
                    }
                });

                menuButton.setOnClickListener(v -> {
                    if (menuClickListener != null) {
                        menuClickListener.onMenuClick(searchList.get(getAdapterPosition()));
                    }
                });
            }

            @SuppressLint("NewApi")
            void bind(Search search, boolean isPlaying) {
                titleTextView.setText(search.getTitle());
                artistTextView.setText(search.getArtist());

                // Показываем/скрываем индикатор воспроизведения
                if (playingIndicator != null) {
                    playingIndicator.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
                }

                // Подсвечиваем текущий трек
                if (isPlaying) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        itemView.setBackgroundColor(itemView.getContext().getResources()
                                .getColor(R.color.color_primary, null));
                    }
                } else {
                    itemView.setBackgroundColor(Color.TRANSPARENT);
                }
            }
        }
    }

    private class SearchMusicTask extends AsyncTask<String, Void, String> {
        private String accessToken;
        private String errorMessage;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE);
            textViewResults.setVisibility(View.GONE);
            searchList.clear();
            albumList.clear();
            artistList.clear(); // Очищаем список артистов
            trackAdapter.notifyDataSetChanged();
            albumAdapter.notifyDataSetChanged();
            artistAdapter.notifyDataSetChanged(); // Обновляем адаптер артистов
            accessToken = TokenManager.getInstance(getContext()).getToken();
        }

        @Override
        protected String doInBackground(String... params) {
            String query = params[0];

            // Поиск треков
            String tracksUrl = "https://api.vk.com/method/audio.search" +
                    "?v=" + API_VERSION +
                    "&access_token=" + accessToken +
                    "&q=" + Uri.encode(query) +
                    "&auto_complete=1" +
                    "&sort=2" +
                    "&count=" + SEARCH_LIMIT;

            // Поиск альбомов
            String albumsUrl = "https://api.vk.com/method/audio.searchAlbums" +
                    "?v=" + API_VERSION +
                    "&access_token=" + accessToken +
                    "&q=" + Uri.encode(query) +
                    "&count=20";

            // Поиск артистов
            String artistsUrl = "https://api.vk.com/method/audio.searchArtists" +
                    "?v=" + API_VERSION +
                    "&access_token=" + accessToken +
                    "&q=" + Uri.encode(query) +
                    "&count=20";

            try {
                // Выполняем все три запроса параллельно
                String tracksResult = executeApiCall(tracksUrl);
                String albumsResult = executeApiCall(albumsUrl);
                String artistsResult = executeApiCall(artistsUrl);

                JSONObject combinedResult = new JSONObject();
                if (tracksResult != null) {
                    combinedResult.put("tracks", new JSONObject(tracksResult));
                }
                if (albumsResult != null) {
                    combinedResult.put("albums", new JSONObject(albumsResult));
                }
                if (artistsResult != null) {
                    combinedResult.put("artists", new JSONObject(artistsResult));
                }

                return combinedResult.toString();
            } catch (Exception e) {
                errorMessage = e.getMessage();
                e.printStackTrace();
                return null;
            }
        }

        private String executeApiCall(String urlString) throws IOException {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", getUserAgent());
            connection.connect();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();
            return response.toString();
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

        @Override
        protected void onPostExecute(String result) {
            progressBar.setVisibility(View.GONE);

            if (result == null) {
                textViewResults.setText(errorMessage != null ? errorMessage : "Ошибка сети");
                textViewResults.setVisibility(View.VISIBLE);
                return;
            }

            try {
                JSONObject combinedResult = new JSONObject(result);

                // Обработка артистов (первыми)
                if (combinedResult.has("artists")) {
                    JSONObject artistsJson = combinedResult.getJSONObject("artists");
                    if (artistsJson.has("response")) {
                        JSONObject response = artistsJson.getJSONObject("response");
                        JSONArray items = response.getJSONArray("items");

                        List<VKArtist> newArtists = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject artistObj = items.getJSONObject(i);
                            newArtists.add(new VKArtist(
                                    artistObj.getInt("id"),
                                    artistObj.getString("name"),
                                    artistObj.optString("photo", null),
                                    artistObj.optString("domain", "")
                            ));
                        }

                        updateArtistList(newArtists);
                    }
                }

                // Обработка треков
                if (combinedResult.has("tracks")) {
                    JSONObject tracksJson = combinedResult.getJSONObject("tracks");
                    if (tracksJson.has("response")) {
                        JSONObject response = tracksJson.getJSONObject("response");
                        JSONArray items = response.getJSONArray("items");

                        List<Search> newList = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject track = items.getJSONObject(i);
                            String artist = track.optString("artist", "Unknown Artist");
                            String title = track.optString("title", "Unknown Title");
                            String url = track.optString("url");

                            if (url != null && !url.isEmpty()) {
                                Search search = new Search();
                                search.setArtist(artist);
                                search.setTitle(title);
                                search.setUrl(url);
                                search.setOwnerId(track.optLong("owner_id", 0));
                                search.setAudioId(track.optLong("id", 0));
                                newList.add(search);
                            }
                        }

                        updateTrackList(newList);
                    }
                }

                // Обработка альбомов
                if (combinedResult.has("albums")) {
                    JSONObject albumsJson = combinedResult.getJSONObject("albums");
                    if (albumsJson.has("response")) {
                        JSONObject response = albumsJson.getJSONObject("response");
                        JSONArray items = response.getJSONArray("items");

                        List<VKAlbum> newAlbums = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject albumObj = items.getJSONObject(i);

                            String coverUrl = null;
                            String artist = "Неизвестный исполнитель";

                            if (albumObj.has("photo")) {
                                JSONObject photo = albumObj.getJSONObject("photo");
                                if (photo.has("photo_300")) {
                                    coverUrl = photo.getString("photo_300");
                                } else if (photo.has("photo_270")) {
                                    coverUrl = photo.getString("photo_270");
                                }
                            }

                            if (albumObj.has("main_artists")) {
                                JSONArray artists = albumObj.getJSONArray("main_artists");
                                if (artists.length() > 0) {
                                    artist = artists.getJSONObject(0).getString("name");
                                }
                            } else if (albumObj.has("artist")) {
                                artist = albumObj.getString("artist");
                            }

                            newAlbums.add(new VKAlbum(
                                    albumObj.getInt("id"),
                                    albumObj.getInt("owner_id"),
                                    albumObj.getString("title"),
                                    artist,
                                    coverUrl,
                                    albumObj.optString("access_key", "")
                            ));
                        }

                        updateAlbumList(newAlbums);
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
                textViewResults.setText("Ошибка данных");
                textViewResults.setVisibility(View.VISIBLE);
            }
        }

        private void updateArtistList(List<VKArtist> newArtists) {
            artistList.clear();
            artistList.addAll(newArtists);
            artistAdapter.notifyDataSetChanged();

            // Показываем/скрываем секцию артистов
            if (newArtists.isEmpty()) {
                recyclerViewArtists.setVisibility(View.GONE);
            } else {
                recyclerViewArtists.setVisibility(View.VISIBLE);
            }
        }

        private void updateTrackList(List<Search> newList) {
            if (newList.isEmpty() && albumList.isEmpty() && artistList.isEmpty()) {
                textViewResults.setText("Ничего не найдено");
                textViewResults.setVisibility(View.VISIBLE);
            } else {
                textViewResults.setVisibility(View.GONE);
            }

            searchList.clear();
            searchList.addAll(newList);
            trackAdapter.notifyDataSetChanged();
        }

        private void updateAlbumList(List<VKAlbum> newAlbums) {
            albumList.clear();
            albumList.addAll(newAlbums);
            albumAdapter.notifyDataSetChanged();

            // Показываем/скрываем секцию альбомов
            if (newAlbums.isEmpty()) {
                recyclerViewAlbums.setVisibility(View.GONE);
            } else {
                recyclerViewAlbums.setVisibility(View.VISIBLE);
            }
        }
    }

    // Класс VKArtist для представления данных артиста
    public static class VKArtist {
        public final int id;
        public final String name;
        public final String photo;
        public final String domain;

        public VKArtist(int id, String name, String photo, String domain) {
            this.id = id;
            this.name = name;
            this.photo = photo;
            this.domain = domain;
        }
    }

    // ArtistAdapter для горизонтального списка артистов
    private static class ArtistAdapter extends RecyclerView.Adapter<ArtistAdapter.ViewHolder> {
        private List<VKArtist> artists;
        private final OnArtistClickListener listener;

        interface OnArtistClickListener {
            void onArtistClick(VKArtist artist);
        }

        ArtistAdapter(List<VKArtist> artists, OnArtistClickListener listener) {
            this.artists = artists;
            this.listener = listener;
        }

        void updateData(List<VKArtist> newArtists) {
            this.artists = newArtists;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_artist_horizontal, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VKArtist artist = artists.get(position);
            holder.bind(artist);
        }

        @Override
        public int getItemCount() {
            return artists.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView photoImageView;
            private final TextView nameTextView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                photoImageView = itemView.findViewById(R.id.artist_photo);
                nameTextView = itemView.findViewById(R.id.artist_name);
            }

            void bind(VKArtist artist) {
                nameTextView.setText(artist.name);

                if (artist.photo != null && !artist.photo.isEmpty()) {
                    Glide.with(itemView.getContext())
                            .load(artist.photo)
                            .placeholder(R.drawable.circle_friend)
                            .circleCrop()
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .into(photoImageView);
                } else {
                    photoImageView.setImageResource(R.drawable.circle_friend);
                }

                itemView.setOnClickListener(v -> listener.onArtistClick(artist));
            }
        }
    }

    // Классы VKAlbum и адаптеры остаются без изменений
    public static class VKAlbum {
        public final int id;
        public final int ownerId;
        public final String title;
        public final String artist;
        public final String coverUrl;
        public final String accessKey;

        public VKAlbum(int id, int ownerId, String title, String artist, String coverUrl, String accessKey) {
            this.id = id;
            this.ownerId = ownerId;
            this.title = title;
            this.artist = artist;
            this.coverUrl = coverUrl;
            this.accessKey = accessKey;
        }
    }

    // AlbumAdapter для горизонтального списка
    private static class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.ViewHolder> {
        private List<VKAlbum> albums;
        private final OnAlbumClickListener listener;

        interface OnAlbumClickListener {
            void onAlbumClick(VKAlbum album);
        }

        AlbumAdapter(List<VKAlbum> albums, OnAlbumClickListener listener) {
            this.albums = albums;
            this.listener = listener;
        }

        void updateData(List<VKAlbum> newAlbums) {
            this.albums = newAlbums;
            notifyDataSetChanged();
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
            VKAlbum album = albums.get(position);
            holder.bind(album);
        }

        @Override
        public int getItemCount() {
            return albums.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView coverImageView;
            private final TextView titleTextView;
            private final TextView artistTextView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                coverImageView = itemView.findViewById(R.id.coverImage);
                titleTextView = itemView.findViewById(R.id.titleText);
                artistTextView = itemView.findViewById(R.id.countText);
            }

            void bind(VKAlbum album) {
                titleTextView.setText(album.title);
                artistTextView.setText(album.artist);

                Glide.with(itemView.getContext())
                        .load(album.coverUrl)
                        .placeholder(R.drawable.circle_playlist)
                        .error(R.drawable.circle_playlist)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(coverImageView);

                itemView.setOnClickListener(v -> listener.onAlbumClick(album));
            }
        }
    }
}