package ru.lisdevs.messenger.playlists;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import okhttp3.Call;
import okhttp3.Callback;
import ru.lisdevs.messenger.R;

import org.json.JSONArray;
import org.json.JSONObject;

import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;

import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PlaylistDetailsFragment extends Fragment {
    private static final String ARG_PLAYLIST_ID = "playlist_id";
    private static final String ARG_PLAYLIST_TITLE = "playlist_title";
    private static final String ARG_PLAYLIST_COUNT = "playlist_count";
    private static final String ARG_PLAYLIST_ARTIST = "playlist_artist";
    private static final String ARG_OWNER_ID = "owner_id";
    private static final String API_VERSION = "5.131";

    private RecyclerView recyclerViewTracks;
    private AudioAdapter audioAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Toolbar toolbar;
    private List<VKAudio> audioList = new ArrayList<>();
    private String accessToken;
    private int playlistId;
    private String playlistTitle;
    private int trackCount;
    private String playlistArtist;
    private long ownerId;

    public static PlaylistDetailsFragment newInstance(int playlistId, String title,
                                                      int count, String artist, long ownerId) {
        PlaylistDetailsFragment fragment = new PlaylistDetailsFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PLAYLIST_ID, playlistId);
        args.putString(ARG_PLAYLIST_TITLE, title);
        args.putInt(ARG_PLAYLIST_COUNT, count);
        args.putString(ARG_PLAYLIST_ARTIST, artist);
        args.putLong(ARG_OWNER_ID, ownerId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        Bundle args = getArguments();
        if (args != null) {
            playlistId = args.getInt(ARG_PLAYLIST_ID);
            playlistTitle = args.getString(ARG_PLAYLIST_TITLE);
            trackCount = args.getInt(ARG_PLAYLIST_COUNT);
            playlistArtist = args.getString(ARG_PLAYLIST_ARTIST);
            ownerId = args.getLong(ARG_OWNER_ID);
        }

        accessToken = TokenManager.getInstance(requireContext()).getToken();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist_details, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        recyclerViewTracks = view.findViewById(R.id.recyclerViewTracks);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        setupToolbar();
        setupRecyclerView();
        setupSwipeRefresh();

        loadPlaylistData();

        return view;
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setTitle(playlistTitle);
            }
        }
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void setupRecyclerView() {
        recyclerViewTracks.setLayoutManager(new LinearLayoutManager(getContext()));
        audioAdapter = new AudioAdapter(audioList);
        recyclerViewTracks.setAdapter(audioAdapter);

        audioAdapter.setOnItemClickListener(this::playAudio);
        audioAdapter.setOnMenuClickListener(this::showAudioOptionsMenu);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::loadPlaylistData);
    }

    private void loadPlaylistData() {
        swipeRefreshLayout.setRefreshing(true);
        fetchPlaylistTracks();
    }

    private void fetchPlaylistTracks() {
        String url = "https://api.vk.com/method/audio.get" +
                "?owner_id=" + ownerId +
                "&access_token=" + accessToken +
                "&v=" + API_VERSION +
                "&playlist_id=" + playlistId +
                "&count=" + trackCount;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Ошибка загрузки треков", Toast.LENGTH_SHORT).show();
                    swipeRefreshLayout.setRefreshing(false);
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

                        List<VKAudio> tracks = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject trackJson = items.getJSONObject(i);
                            tracks.add(new VKAudio(
                                    trackJson.getLong("id"),
                                    trackJson.getString("artist"),
                                    trackJson.getString("title"),
                                    trackJson.optString("url", ""),
                                    trackJson.optInt("duration", 0)
                            ));
                        }

                        requireActivity().runOnUiThread(() -> {
                            audioList.clear();
                            audioList.addAll(tracks);
                            audioAdapter.notifyDataSetChanged();
                            swipeRefreshLayout.setRefreshing(false);
                        });
                    }
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }
        });
    }

    private void playAudio(VKAudio audio) {
        if (audio.url == null || audio.url.isEmpty()) {
            Toast.makeText(getContext(), "Трек недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(getContext(), MusicPlayerService.class);
        intent.setAction("PLAY");
        intent.putExtra("URL", audio.url);
        intent.putExtra("TITLE", audio.artist + " - " + audio.title);
        ContextCompat.startForegroundService(requireContext(), intent);
    }

    private void showAudioOptionsMenu(VKAudio audio) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_audio, null);
        dialog.setContentView(view);

        view.findViewById(R.id.buttonDownload).setOnClickListener(v -> {
            downloadAudio(audio);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void downloadAudio(VKAudio audio) {
        String fileName = audio.artist + " - " + audio.title + ".mp3";
        DownloadManager downloadManager =
                (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(audio.url))
                .setTitle(fileName)
                .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_MUSIC,
                        fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        if (downloadManager != null) {
            downloadManager.enqueue(request);
            Toast.makeText(getContext(), "Скачивание начато", Toast.LENGTH_SHORT).show();
        }
    }

    static class VKAudio {
        long id;
        String artist;
        String title;
        String url;
        int duration;

        VKAudio(long id, String artist, String title, String url, int duration) {
            this.id = id;
            this.artist = artist;
            this.title = title;
            this.url = url;
            this.duration = duration;
        }
    }

    static class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {
        private List<VKAudio> tracks;
        private OnItemClickListener itemClickListener;
        private OnMenuClickListener menuClickListener;

        interface OnItemClickListener {
            void onItemClick(VKAudio audio);
        }

        interface OnMenuClickListener {
            void onMenuClick(VKAudio audio);
        }

        AudioAdapter(List<VKAudio> tracks) {
            this.tracks = tracks;
        }

        void setOnItemClickListener(OnItemClickListener listener) {
            this.itemClickListener = listener;
        }

        void setOnMenuClickListener(OnMenuClickListener listener) {
            this.menuClickListener = listener;
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
            VKAudio track = tracks.get(position);
            holder.artistTextView.setText(track.artist);
            holder.titleTextView.setText(track.title);

            holder.itemView.setOnClickListener(v -> {
                if (itemClickListener != null) {
                    itemClickListener.onItemClick(track);
                }
            });

            holder.menuButton.setOnClickListener(v -> {
                if (menuClickListener != null) {
                    menuClickListener.onMenuClick(track);
                }
            });
        }

        @Override
        public int getItemCount() {
            return tracks.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView artistTextView;
            TextView titleTextView;
            ImageView menuButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                artistTextView = itemView.findViewById(R.id.artistText);
                titleTextView = itemView.findViewById(R.id.titleText);
                menuButton = itemView.findViewById(R.id.downloadButton);
            }
        }
    }
}