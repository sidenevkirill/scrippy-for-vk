package ru.lisdevs.messenger.artist;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
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
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;

public class ArtistTracksFragment extends Fragment {

    private static final String ARG_ARTIST_ID = "artist_id";
    private static final String ARG_ARTIST_NAME = "artist_name";
    private static final String ARG_ARTIST_PHOTO = "artist_photo";
    private static final String API_VERSION = "5.81";
    private static final int TRACKS_LIMIT = 200;

    // UI elements
    private TextView artistNameTextView;
    private AppCompatImageView artistPhotoImageView;
    private RecyclerView recyclerView;
    private ArtistTracksAdapter adapter;
    private List<Audio> tracksList = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView emptyView;

    // Music player service
    private MusicPlayerService musicPlayerService;
    private boolean isServiceBound = false;

    public static ArtistTracksFragment newInstance(int artistId, String artistName, String artistPhoto) {
        ArtistTracksFragment fragment = new ArtistTracksFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_ARTIST_ID, artistId);
        args.putString(ARG_ARTIST_NAME, artistName);
        args.putString(ARG_ARTIST_PHOTO, artistPhoto);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist_song, container, false);

        // Initialize UI
        artistNameTextView = view.findViewById(R.id.title);
        artistPhotoImageView = view.findViewById(R.id.image);
        recyclerView = view.findViewById(R.id.recycler);
        progressBar = view.findViewById(R.id.progressBar);
        emptyView = view.findViewById(R.id.emptyView);

        // Set artist info
        if (getArguments() != null) {
            artistNameTextView.setText(getArguments().getString(ARG_ARTIST_NAME));

            String photoUrl = getArguments().getString(ARG_ARTIST_PHOTO);
            if (photoUrl != null && !photoUrl.isEmpty()) {
                Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.account_outline)
                        .circleCrop()
                        .into(artistPhotoImageView);
            }
        }

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ArtistTracksAdapter(tracksList);
        recyclerView.setAdapter(adapter);

        // Set click listeners
        adapter.setOnItemClickListener(position -> {
            if (position >= 0 && position < tracksList.size()) {
                playTrack(position);
            }
        });

        adapter.setOnMenuClickListener(audio -> {
            showBottomSheetMenu(audio);
        });

        // Load artist tracks
        loadArtistTracks();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Bind to MusicPlayerService
        Intent intent = new Intent(getActivity(), MusicPlayerService.class);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        // Unbind from service
        if (isServiceBound) {
            requireActivity().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            musicPlayerService = binder.getService();
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    private void loadArtistTracks() {
        if (getArguments() == null) {
            showEmptyView("Ошибка: не указан ID исполнителя");
            return;
        }

        int artistId = getArguments().getInt(ARG_ARTIST_ID);
        String accessToken = TokenManager.getInstance(requireContext()).getToken();

        if (accessToken == null || accessToken.isEmpty()) {
            showEmptyView("Требуется авторизация в VK");
            return;
        }

        showLoading();

        String url = "https://api.vk.com/method/audio.getAudiosByArtist" +
                "?access_token=" + accessToken +
                "&artist_id=" + artistId +
                "&count=" + TRACKS_LIMIT +
                "&v=" + API_VERSION;

        String urlTest = "https://api.vk.com/method/audio.getAudiosByArtist?access_token="  + accessToken + "&artist_id="+ artistId + "&count=200&v=5.81";

        new OkHttpClient().newCall(new Request.Builder()
                        .url(urlTest)
                        .header("User-Agent", Authorizer.getKateUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e("API_ERROR", "Request failed", e);
                        requireActivity().runOnUiThread(() ->
                                showEmptyView("Ошибка соединения: " + e.getMessage())
                        );
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        Log.d("API_RESPONSE", "Response code: " + response.code());
                        try {
                            String responseBody = response.body().string();
                            Log.d("API_RESPONSE", "Raw response: " + responseBody);
                            handleResponse(responseBody);
                        } catch (Exception e) {
                            Log.e("API_ERROR", "Response handling error", e);
                            requireActivity().runOnUiThread(() ->
                                    showEmptyView("Ошибка обработки данных")
                            );
                        }
                    }
                });
    }

    private void handleResponse(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);

            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                String errorMsg = error.getString("error_msg");
                Log.e("API_ERROR", "VK API error: " + errorMsg);
                requireActivity().runOnUiThread(() -> showEmptyView("Ошибка API: " + errorMsg));
                return;
            }

            JSONArray items = json.getJSONObject("response").getJSONArray("items");
            List<Audio> tracks = new ArrayList<>();

            for (int i = 0; i < items.length(); i++) {
                JSONObject trackObj = items.getJSONObject(i);
                String url = trackObj.optString("url", "");
                if (!url.isEmpty()) {
                    tracks.add(new Audio(
                            trackObj.optString("artist", "Unknown Artist"),
                            trackObj.optString("title", "Unknown Title"),
                            url,
                            trackObj.optLong("owner_id", 0),
                            trackObj.optLong("id", 0)
                    ));
                }
            }

            requireActivity().runOnUiThread(() -> updateUI(tracks));

        } catch (JSONException e) {
            Log.e("API_ERROR", "JSON parsing error", e);
            requireActivity().runOnUiThread(() ->
                    showEmptyView("Ошибка обработки данных")
            );
        }
    }

    private void updateUI(List<Audio> tracks) {
        if (tracks.isEmpty()) {
            showEmptyView("Нет доступных треков");
        } else {
            showResults(tracks);
        }
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
    }

    private void showResults(List<Audio> tracks) {
        progressBar.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        tracksList.clear();
        tracksList.addAll(tracks);
        adapter.notifyDataSetChanged();
    }

    private void showEmptyView(String message) {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(message);
    }

    private void playTrack(int position) {
        if (position < 0 || position >= tracksList.size()) return;

        Audio audio = tracksList.get(position);
        if (audio.getUrl() == null || audio.getUrl().isEmpty()) {
            Toast.makeText(getContext(), "Трек недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        // Start playback through service
        Intent serviceIntent = new Intent(getActivity(), MusicPlayerService.class);
        serviceIntent.setAction("PLAY");
        serviceIntent.putExtra("URL", audio.getUrl());
        serviceIntent.putExtra("TITLE", audio.getTitle());
        serviceIntent.putExtra("ARTIST", audio.getArtist());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().startForegroundService(serviceIntent);
        } else {
            requireActivity().startService(serviceIntent);
        }

        // If service is already bound, we can control playback directly
        if (isServiceBound && musicPlayerService != null) {
            if (musicPlayerService.isPlaying()) {
              //  musicPlayerService.stopAudio();
            }
        }
    }

    private void showBottomSheetMenu(Audio audio) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_audio_menu, null);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(view);

        Button addButton = view.findViewById(R.id.buttonAddToMyMusic);
        Button downloadButton = view.findViewById(R.id.buttonDownload);

        addButton.setOnClickListener(v -> {
            addTrackToMyMusic(audio);
            dialog.dismiss();
        });

        downloadButton.setOnClickListener(v -> {
            downloadTrack(audio);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void addTrackToMyMusic(Audio audio) {
        Toast.makeText(getContext(), "Добавление в мою музыку: " + audio.getTitle(), Toast.LENGTH_SHORT).show();
    }

    private void downloadTrack(Audio audio) {
        Toast.makeText(getContext(), "Скачивание: " + audio.getTitle(), Toast.LENGTH_SHORT).show();
    }

    // Adapter class
    private static class ArtistTracksAdapter extends RecyclerView.Adapter<ArtistTracksAdapter.ViewHolder> {
        private List<Audio> tracks;
        private OnItemClickListener itemClickListener;
        private OnMenuClickListener menuClickListener;

        interface OnItemClickListener {
            void onItemClick(int position);
        }

        interface OnMenuClickListener {
            void onMenuClick(Audio audio);
        }

        ArtistTracksAdapter(List<Audio> tracks) {
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
            Audio audio = tracks.get(position);
            holder.titleTextView.setText(audio.getTitle());
            holder.artistTextView.setText(audio.getArtist());

            holder.menuButton.setOnClickListener(v -> {
                if (menuClickListener != null) {
                    menuClickListener.onMenuClick(audio);
                }
            });
        }

        @Override
        public int getItemCount() {
            return tracks.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView titleTextView;
            TextView artistTextView;
            ImageView menuButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                titleTextView = itemView.findViewById(R.id.titleText);
                artistTextView = itemView.findViewById(R.id.artistText);
                menuButton = itemView.findViewById(R.id.downloadButton);

                itemView.setOnClickListener(v -> {
                    if (itemClickListener != null) {
                        itemClickListener.onItemClick(getAdapterPosition());
                    }
                });
            }
        }
    }

    // Audio model class
    public static class Audio {
        private String artist;
        private String title;
        private String url;
        private long ownerId;
        private long id;

        public Audio(String artist, String title, String url, long ownerId, long id) {
            this.artist = artist;
            this.title = title;
            this.url = url;
            this.ownerId = ownerId;
            this.id = id;
        }

        public String getArtist() { return artist; }
        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public long getOwnerId() { return ownerId; }
        public long getId() { return id; }
    }
}