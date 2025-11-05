package ru.lisdevs.messenger.playlists;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.model.Audio;
import ru.lisdevs.messenger.music.AudioAdapter;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;

import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Menu;
import android.view.MenuInflater;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.Collections;

public class AlbumPageFragment extends Fragment {

    private static final String API_VERSION = "5.131";
    private String accessToken;

    private RecyclerView recyclerView;
    private AudioAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private Toolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageView albumCover;
    private TextView albumTitle;
    private TextView albumInfo;
    private Button buttonAddAlbum;

    // Mini player views
    private LinearLayout miniPlayerContainer;
    private TextView currentTrackText;
    private ImageView playPauseButton;
    private int currentTrackIndex = -1;

    private List<Audio> audioList = new ArrayList<>();
    private int playlistId;
    private int ownerId;
    private String title;
    private String coverUrl;
    private boolean isAlbumAdded = false;

    public static AlbumPageFragment newInstance(int playlistId, int ownerId, String title, String coverUrl, String url) {
        AlbumPageFragment fragment = new AlbumPageFragment();
        Bundle args = new Bundle();
        args.putInt("playlist_id", playlistId);
        args.putInt("owner_id", ownerId);
        args.putString("title", title);
        args.putString("cover_url", coverUrl);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (getArguments() != null) {
            playlistId = getArguments().getInt("playlist_id");
            ownerId = getArguments().getInt("owner_id");
            title = getArguments().getString("title");
            coverUrl = getArguments().getString("cover_url");
        }

        accessToken = TokenManager.getInstance(requireContext()).getToken();
        isAlbumAdded = loadAlbumState();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_album_page, container, false);

        // Initialize views
        toolbar = view.findViewById(R.id.toolbar);
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyView = view.findViewById(R.id.emptyView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        albumCover = view.findViewById(R.id.albumCover);
        albumTitle = view.findViewById(R.id.albumTitle);
        albumInfo = view.findViewById(R.id.albumInfo);
        buttonAddAlbum = view.findViewById(R.id.buttonAdd);

        // Mini player views
        miniPlayerContainer = view.findViewById(R.id.miniPlayerContainer);
        currentTrackText = view.findViewById(R.id.currentTrackText);
        playPauseButton = view.findViewById(R.id.playPauseButton);
        Button buttonNext = view.findViewById(R.id.buttonNext);
        Button buttonPrev = view.findViewById(R.id.buttonPrev);

        // Setup button
        updateButtonState();
        buttonAddAlbum.setOnClickListener(v -> {
            if (isAlbumAdded) {
                unfollowPlaylist();
            } else {
                followPlaylist();
            }
        });

        // Setup toolbar with menu
        setupToolbar();

        // Setup RecyclerView
        adapter = new AudioAdapter(audioList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // Load album cover with enhanced styling
        loadAlbumCover();

        albumTitle.setText(title);
        albumInfo.setText(String.format("Альбом • %d треков", 0));

        // Setup listeners
        swipeRefreshLayout.setOnRefreshListener(this::loadAlbumTracks);
        playPauseButton.setOnClickListener(v -> onTogglePlayPause());
        buttonNext.setOnClickListener(v -> onNext());
        buttonPrev.setOnClickListener(v -> onPrevious());

        // Set click listeners for adapter
        adapter.setOnItemClickListener(position -> {
            if (position >= 0 && position < audioList.size()) {
                currentTrackIndex = position;
                Audio selectedAudio = audioList.get(position);
                onPlayAudio(selectedAudio.getUrl(), selectedAudio.getTitle());
                updateMiniPlayer(selectedAudio);
            }
        });

        adapter.setOnMenuClickListener(audio -> showBottomSheet(audio));

        // Load data
        loadAlbumTracks();

        return view;
    }

    private void setupToolbar() {
        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setTitle("");
        }

        toolbar.setNavigationIcon(R.drawable.arrow_left_black);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void loadAlbumCover() {
        if (coverUrl != null && !coverUrl.isEmpty()) {
            RequestOptions requestOptions = new RequestOptions()
                    .placeholder(R.drawable.circle_playlist)
                    .error(R.drawable.circle_playlist)
                    .transform(new CenterCrop(), new RoundedCorners(16));

            Glide.with(this)
                    .load(coverUrl)
                    .apply(requestOptions)
                    .into(albumCover);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                albumCover.setElevation(8f);
                albumCover.setClipToOutline(true);
            }
        } else {
            albumCover.setImageResource(R.drawable.circle_playlist);
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF6A11CB, 0xFF2575FC}
            );
            gradient.setCornerRadius(16f);
            albumCover.setBackground(gradient);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_album, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            requireActivity().onBackPressed();
            return true;
        } else if (id == R.id.action_share) {
            shareAlbum();
            return true;
        } else if (id == R.id.action_download_all) {
            downloadAllTracks();
            return true;
        } else if (id == R.id.action_add_to_queue) {
            addAllToQueue();
            return true;
        } else if (id == R.id.action_sort) {
            showSortDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void shareAlbum() {
        try {
            String shareText = "Послушай альбом \"" + title + "\" в VK Music!";
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(shareIntent, "Поделиться альбомом"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Ошибка при попытке поделиться", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadAllTracks() {
        if (audioList.isEmpty()) {
            Toast.makeText(getContext(), "Нет треков для скачивания", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Скачать все треки")
                .setMessage("Скачать все " + audioList.size() + " треков этого альбома?")
                .setPositiveButton("Скачать", (dialog, which) -> {
                    for (Audio audio : audioList) {
                        startDownload(getContext(), audio.getUrl(),
                                audio.getArtist() + " - " + audio.getTitle() + ".mp3");
                    }
                    Toast.makeText(getContext(), "Начато скачивание " + audioList.size() + " треков",
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void addAllToQueue() {
        if (audioList.isEmpty()) {
            Toast.makeText(getContext(), "Нет треков для добавления", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> urls = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        for (Audio audio : audioList) {
            urls.add(audio.getUrl());
            titles.add(audio.getTitle());
        }

        Intent intent = new Intent(getContext(), MusicPlayerService.class);
        intent.setAction("ADD_TO_QUEUE");
        intent.putStringArrayListExtra("URLS", new ArrayList<>(urls));
        intent.putStringArrayListExtra("TITLES", new ArrayList<>(titles));
        ContextCompat.startForegroundService(requireContext(), intent);

        Toast.makeText(getContext(), "Добавлено " + audioList.size() + " треков в очередь",
                Toast.LENGTH_SHORT).show();
    }

    private void showSortDialog() {
        String[] sortOptions = {"По названию (А-Я)", "По исполнителю", "По длительности", "По дате добавления"};

        new AlertDialog.Builder(requireContext())
                .setTitle("Сортировка треков")
                .setItems(sortOptions, (dialog, which) -> {
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
                            break;
                    }
                })
                .show();
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

    private void loadAlbumTracks() {
        swipeRefreshLayout.setRefreshing(true);
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        String url = "https://api.vk.com/method/audio.get" +
                "?access_token=" + accessToken +
                "&owner_id=" + ownerId +
                "&album_id=" + playlistId +
                "&v=" + API_VERSION;

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", Authorizer.getKateUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        requireActivity().runOnUiThread(() -> {
                            swipeRefreshLayout.setRefreshing(false);
                            progressBar.setVisibility(View.GONE);
                            emptyView.setVisibility(View.VISIBLE);
                            emptyView.setText("Ошибка загрузки треков");
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);
                            JSONObject responseObj = json.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");

                            List<Audio> newAudioList = new ArrayList<>();
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject trackObj = items.getJSONObject(i);
                                Audio audio = new Audio(
                                        trackObj.getString("artist"),
                                        trackObj.getString("title"),
                                        trackObj.getString("url")
                                );
                                audio.setId(trackObj.getLong("id"));
                                audio.setOwnerId(trackObj.getLong("owner_id"));
                                audio.setDuration((int) trackObj.getLong("duration"));
                                newAudioList.add(audio);
                            }

                            requireActivity().runOnUiThread(() -> {
                                swipeRefreshLayout.setRefreshing(false);
                                progressBar.setVisibility(View.GONE);

                                if (newAudioList.isEmpty()) {
                                    emptyView.setVisibility(View.VISIBLE);
                                    emptyView.setText("В альбоме нет треков");
                                } else {
                                    emptyView.setVisibility(View.GONE);
                                    audioList.clear();
                                    audioList.addAll(newAudioList);
                                    adapter.notifyDataSetChanged();
                                    albumInfo.setText(String.format("Альбом • %d треков", newAudioList.size()));
                                }
                            });
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() -> {
                                swipeRefreshLayout.setRefreshing(false);
                                progressBar.setVisibility(View.GONE);
                                emptyView.setVisibility(View.VISIBLE);
                                emptyView.setText("Ошибка обработки данных");
                            });
                        }
                    }
                });
    }

    private void showBottomSheet(Audio audio) {
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_audio, null);
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        bottomSheetDialog.setContentView(view);

        TextView downloadBtn = view.findViewById(R.id.buttonDownload);
        downloadBtn.setOnClickListener(v -> {
            startDownload(getContext(), audio.getUrl(), audio.getArtist() + " - " + audio.getTitle() + ".mp3");
            bottomSheetDialog.dismiss();
        });

        TextView addToMyMusicBtn = view.findViewById(R.id.buttonAlbum);
        addToMyMusicBtn.setOnClickListener(v -> {
            addToMyMusic(audio);
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void addToMyMusic(Audio audio) {
        if (audio.getOwnerId() == 0 || audio.getId() == 0) {
            Toast.makeText(getContext(), "Не удалось получить данные трека", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        String url = "https://api.vk.com/method/audio.add" +
                "?access_token=" + accessToken +
                "&owner_id=" + audio.getOwnerId() +
                "&audio_id=" + audio.getId() +
                "&v=" + API_VERSION;

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", Authorizer.getKateUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        requireActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Ошибка сети: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        requireActivity().runOnUiThread(() -> progressBar.setVisibility(View.GONE));

                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("response")) {
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(getContext(), "Трек добавлен в Мою музыку",
                                                Toast.LENGTH_SHORT).show());
                            } else if (json.has("error")) {
                                JSONObject error = json.getJSONObject("error");
                                String errorMsg = error.getString("error_msg");
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(getContext(), "Ошибка: " + errorMsg,
                                                Toast.LENGTH_SHORT).show());
                            }
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(getContext(), "Ошибка обработки ответа",
                                            Toast.LENGTH_SHORT).show());
                        }
                    }
                });
    }

    private static void startDownload(Context context, String url, String filename) {
        android.app.DownloadManager downloadManager = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(url);

        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(uri);
        request.setTitle("Скачивание трека");
        request.setDescription(filename);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, filename);

        if (downloadManager != null) {
            downloadManager.enqueue(request);
            Toast.makeText(context, "Начато скачивание", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Не удалось начать скачивание", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMiniPlayer(Audio audio) {
        miniPlayerContainer.setVisibility(View.GONE);
        currentTrackText.setText(audio.getArtist() + " - " + audio.getTitle());
        playPauseButton.setImageResource(R.drawable.pause);
    }

    private void followPlaylist() {
        progressBar.setVisibility(View.VISIBLE);

        String url = "https://api.vk.com/method/audio.followPlaylist" +
                "?access_token=" + accessToken +
                "&owner_id=" + ownerId +
                "&playlist_id=" + playlistId +
                "&v=5.95";

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", Authorizer.getKateUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        requireActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Ошибка добавления альбома: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        requireActivity().runOnUiThread(() -> progressBar.setVisibility(View.GONE));

                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("error")) {
                                JSONObject error = json.getJSONObject("error");
                                String errorMsg = error.getString("error_msg");
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(getContext(), "Ошибка: " + errorMsg,
                                                Toast.LENGTH_SHORT).show());
                            } else {
                                isAlbumAdded = true;
                                saveAlbumState(true);
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Альбом успешно добавлен",
                                            Toast.LENGTH_SHORT).show();
                                    updateButtonState();
                                });
                            }
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(getContext(), "Ошибка обработки ответа",
                                            Toast.LENGTH_SHORT).show());
                        }
                    }
                });
    }

    private void unfollowPlaylist() {
        progressBar.setVisibility(View.VISIBLE);

        String url = "https://api.vk.com/method/audio.deletePlaylist" +
                "?access_token=" + accessToken +
                "&owner_id=" + ownerId +
                "&playlist_id=" + playlistId +
                "&v=5.95";

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", Authorizer.getKateUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        requireActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Ошибка удаления альбома: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        requireActivity().runOnUiThread(() -> progressBar.setVisibility(View.GONE));

                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("error")) {
                                JSONObject error = json.getJSONObject("error");
                                String errorMsg = error.getString("error_msg");
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(getContext(), "Ошибка: " + errorMsg,
                                                Toast.LENGTH_SHORT).show());
                            } else {
                                isAlbumAdded = false;
                                saveAlbumState(false);
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Альбом успешно удален",
                                            Toast.LENGTH_SHORT).show();
                                    updateButtonState();
                                });
                            }
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(getContext(), "Ошибка обработки ответа",
                                            Toast.LENGTH_SHORT).show());
                        }
                    }
                });
    }

    private void updateButtonState() {
        requireActivity().runOnUiThread(() -> {
            if (isAlbumAdded) {
                buttonAddAlbum.setText("Удалить");
                buttonAddAlbum.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red));
                buttonAddAlbum.setTextColor(Color.WHITE);
            } else {
                buttonAddAlbum.setText("Добавить");
                buttonAddAlbum.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary));
                buttonAddAlbum.setTextColor(Color.WHITE);
            }
        });
    }

    private void saveAlbumState(boolean added) {
        SharedPreferences prefs = requireContext().getSharedPreferences("AlbumPrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("album_" + playlistId, added).apply();
    }

    private boolean loadAlbumState() {
        SharedPreferences prefs = requireContext().getSharedPreferences("AlbumPrefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("album_" + playlistId, false);
    }

    public void onPlayAudio(String url, String title) {
        Intent intent = new Intent(getContext(), MusicPlayerService.class);
        intent.setAction("PLAY");
        intent.putExtra("URL", url);
        ContextCompat.startForegroundService(requireContext(), intent);
    }

    public void onTogglePlayPause() {
        Intent intent = new Intent(getContext(), MusicPlayerService.class);
        intent.setAction("TOGGLE_PAUSE");
        ContextCompat.startForegroundService(requireContext(), intent);
    }

    public void onNext() {
        if (audioList.isEmpty() || currentTrackIndex == -1) return;

        currentTrackIndex = (currentTrackIndex + 1) % audioList.size();
        Audio nextAudio = audioList.get(currentTrackIndex);
        onPlayAudio(nextAudio.getUrl(), nextAudio.getTitle());
        updateMiniPlayer(nextAudio);
    }

    public void onPrevious() {
        if (audioList.isEmpty() || currentTrackIndex == -1) return;

        currentTrackIndex = (currentTrackIndex - 1 + audioList.size()) % audioList.size();
        Audio prevAudio = audioList.get(currentTrackIndex);
        onPlayAudio(prevAudio.getUrl(), prevAudio.getTitle());
        updateMiniPlayer(prevAudio);
    }
}