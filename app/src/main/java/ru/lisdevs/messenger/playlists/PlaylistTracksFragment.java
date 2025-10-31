package ru.lisdevs.messenger.playlists;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.official.audios.Audio;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;

public class PlaylistTracksFragment extends Fragment {

    private static final String ARG_PLAYLIST_ID = "playlist_id";
    private static final String ARG_OWNER_ID = "owner_id";
    private static final String ARG_PLAYLIST_TITLE = "playlist_title";

    private long playlistId;
    private long ownerId;
    private String playlistTitle;
    private String accessToken;

    private RecyclerView recyclerView;
    private TrackAdapter adapter;
    private List<TrackItem> trackItems = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView albumTitle, albumInfo;

    // Элементы мини-плеера
    private LinearLayout miniPlayerContainer;
    private TextView currentTrackText;
    private ImageButton playPauseButton;
    private int currentTrackIndex = -1;

    private MusicPlayerService musicService;
    private boolean serviceBound = false;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            musicService = binder.getService();
            serviceBound = true;
            updatePlayerStateFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    public static PlaylistTracksFragment newInstance(long playlistId, long ownerId, String playlistTitle) {
        PlaylistTracksFragment fragment = new PlaylistTracksFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PLAYLIST_ID, playlistId);
        args.putLong(ARG_OWNER_ID, ownerId);
        args.putString(ARG_PLAYLIST_TITLE, playlistTitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            playlistId = getArguments().getLong(ARG_PLAYLIST_ID);
            ownerId = getArguments().getLong(ARG_OWNER_ID);
            playlistTitle = getArguments().getString(ARG_PLAYLIST_TITLE);
        }
        accessToken = TokenManager.getInstance(requireContext()).getToken();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tracks_fragment, container, false);

        // Инициализация мини-плеера
        miniPlayerContainer = view.findViewById(R.id.miniPlayerContainer);
        currentTrackText = view.findViewById(R.id.currentTrackText);
        playPauseButton = view.findViewById(R.id.playPauseButton);

        playPauseButton.setOnClickListener(v -> togglePlayPause());

        // Настройка Toolbar
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                activity.getSupportActionBar().setTitle("Плейлист");
            }
        }

        albumTitle = view.findViewById(R.id.albumTitle);
        albumInfo = view.findViewById(R.id.albumInfo);

        albumTitle.setText(playlistTitle);
        albumInfo.setText(String.format("Плейлист • %d треков", 0));

        recyclerView = view.findViewById(R.id.recyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TrackAdapter(trackItems);

        adapter.setOnTrackClickListener(position -> {
            if (position >= 0 && position < trackItems.size()) {
                playTrack(trackItems.get(position), position);
            }
        });

        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (accessToken != null) {
                fetchPlaylistTracks(accessToken);
            }
        });

        if (accessToken != null) {
            fetchPlaylistTracks(accessToken);
        } else {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_LONG).show();
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(getActivity(), MusicPlayerService.class);
        getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (serviceBound) {
            getActivity().unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private void updatePlayerStateFromService() {
        if (serviceBound && musicService != null && musicService.isPlaying()) {
            currentTrackText.setText(musicService.getCurrentArtist() + " - " +
                    musicService.getCurrentTrackTitle());
            miniPlayerContainer.setVisibility(View.GONE);
            updatePlayPauseButton(true);
        }
    }

    private void playTrack(TrackItem track, int position) {
        if (track.getUrl() == null || track.getUrl().isEmpty()) {
            Toast.makeText(getContext(), "Невозможно воспроизвести трек", Toast.LENGTH_SHORT).show();
            return;
        }

        currentTrackIndex = position;
        currentTrackText.setText(track.getArtist() + " - " + track.getTitle());
        miniPlayerContainer.setVisibility(View.GONE);

        if (serviceBound) {
            List<Audio> playlist = new ArrayList<>();
            for (TrackItem item : trackItems) {

            }

            musicService.setPlaylist(playlist, position);

            Intent playIntent = new Intent(getActivity(), MusicPlayerService.class);
            playIntent.setAction(MusicPlayerService.ACTION_PLAY);
            playIntent.putExtra("URL", track.getUrl());
            playIntent.putExtra("TITLE", track.getTitle());
            playIntent.putExtra("ARTIST", track.getArtist());
            ContextCompat.startForegroundService(getActivity(), playIntent);
        }

        updatePlayPauseButton(true);
    }

    private void togglePlayPause() {
        if (currentTrackIndex == -1) return;

        if (serviceBound) {
            Intent toggleIntent = new Intent(getActivity(), MusicPlayerService.class);
            toggleIntent.setAction(MusicPlayerService.ACTION_TOGGLE);
            ContextCompat.startForegroundService(getActivity(), toggleIntent);
        }
    }

    public void updatePlayerState(boolean playing) {
        updatePlayPauseButton(playing);
    }

    private void updatePlayPauseButton(boolean playing) {
        playPauseButton.setImageResource(playing ? R.drawable.pause : R.drawable.play);
    }

    private void fetchPlaylistTracks(String accessToken) {
        swipeRefreshLayout.setRefreshing(true);

        // Для групп owner_id должен быть отрицательным
        long effectiveOwnerId = ownerId > 0 ? -ownerId : ownerId;

        String url = "https://api.vk.com/method/audio.get" +
                "?owner_id=" + effectiveOwnerId +
                "&album_id=" + playlistId +  // Используем album_id вместо playlist_id для групп
                "&access_token=" + accessToken +
                "&v=5.131" +
                "&count=100";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), "Ошибка загрузки треков: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(body);

                        // Добавляем логирование ответа для отладки
                        Log.d("PlaylistTracks", "API Response: " + jsonObject.toString(2));

                        if (jsonObject.has("response")) {
                            JSONObject responseObj = jsonObject.getJSONObject("response");
                            if (responseObj.has("items")) {
                                JSONArray items = responseObj.getJSONArray("items");
                                List<TrackItem> tempList = new ArrayList<>();

                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject trackObj = items.getJSONObject(i);
                                    String title = trackObj.getString("title");
                                    String artist = trackObj.getString("artist");
                                    String url = trackObj.optString("url", "");
                                    int duration = trackObj.getInt("duration");

                                    // Добавляем только треки с валидным URL
                                    if (!url.isEmpty()) {
                                        tempList.add(new TrackItem(title, artist, url, duration));
                                    }
                                }

                                requireActivity().runOnUiThread(() -> {
                                    trackItems.clear();
                                    trackItems.addAll(tempList);
                                    albumInfo.setText(String.format("Плейлист • %d треков", trackItems.size()));
                                    adapter.notifyDataSetChanged();
                                    swipeRefreshLayout.setRefreshing(false);

                                    if (trackItems.isEmpty()) {
                                        Toast.makeText(getContext(),
                                                "В плейлисте нет треков или они недоступны",
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                                return;
                            }
                        }

                        // Обработка ошибок API
                        if (jsonObject.has("error")) {
                            JSONObject error = jsonObject.getJSONObject("error");
                            String errorMsg = error.getString("error_msg");
                            requireActivity().runOnUiThread(() -> {
                                swipeRefreshLayout.setRefreshing(false);
                                Toast.makeText(getContext(),
                                        "Ошибка API: " + errorMsg,
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    } catch (JSONException e) {
                        requireActivity().runOnUiThread(() -> {
                            swipeRefreshLayout.setRefreshing(false);
                            Toast.makeText(getContext(),
                                    "Ошибка обработки данных: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                } else {
                    requireActivity().runOnUiThread(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(),
                                "Ошибка сервера: " + response.code(),
                                Toast.LENGTH_LONG).show();
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

    private static class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {
        private List<TrackItem> trackItems;
        private OnTrackClickListener listener;

        public interface OnTrackClickListener {
            void onTrackClick(int position);
        }

        public void setOnTrackClickListener(OnTrackClickListener listener) {
            this.listener = listener;
        }

        public TrackAdapter(List<TrackItem> trackItems) {
            this.trackItems = trackItems;
        }

        @NonNull
        @Override
        public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false);
            return new TrackViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
            TrackItem track = trackItems.get(position);
            holder.titleText.setText(track.getTitle());
            holder.artistText.setText(track.getArtist());
         //   holder.durationText.setText(formatDuration(track.getDuration()));

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTrackClick(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return trackItems.size();
        }

        private String formatDuration(int seconds) {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds);
        }

        static class TrackViewHolder extends RecyclerView.ViewHolder {
            TextView titleText, artistText, durationText;

            public TrackViewHolder(@NonNull View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.titleText);
                artistText = itemView.findViewById(R.id.artistText);
             //   durationText = itemView.findViewById(R.id.durationText);
            }
        }
    }

    public static class TrackItem implements Parcelable {
        private String title;
        private String artist;
        private String url;
        private int duration;

        public TrackItem(String title, String artist, String url, int duration) {
            this.title = title;
            this.artist = artist;
            this.url = url;
            this.duration = duration;
        }

        protected TrackItem(Parcel in) {
            title = in.readString();
            artist = in.readString();
            url = in.readString();
            duration = in.readInt();
        }

        public static final Creator<TrackItem> CREATOR = new Creator<TrackItem>() {
            @Override
            public TrackItem createFromParcel(Parcel in) {
                return new TrackItem(in);
            }

            @Override
            public TrackItem[] newArray(int size) {
                return new TrackItem[size];
            }
        };

        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getUrl() { return url; }
        public int getDuration() { return duration; }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(title);
            dest.writeString(artist);
            dest.writeString(url);
            dest.writeInt(duration);
        }
    }
}