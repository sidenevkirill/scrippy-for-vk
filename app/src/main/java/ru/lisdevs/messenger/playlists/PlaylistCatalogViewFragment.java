package ru.lisdevs.messenger.playlists;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

import com.google.android.material.bottomsheet.BottomSheetDialog;

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
import ru.lisdevs.messenger.player.PlayerBottomSheetFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;

public class PlaylistCatalogViewFragment extends Fragment {

    // UI Components
    private TextView textViewDetails;
    private RecyclerView recyclerViewAudio;
    private AudioAdapter audioAdapter;
    private Toolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Data
    private List<VKAudio> audioList = new ArrayList<>();
    private String ACCESS_TOKEN;
    private static final String API_VERSION = "5.131";
    private static final String ownerId = "-147845620";
    private int playlistId;
    private static final String ARG_PLAYLIST_ID = "playlist_id";

    public static PlaylistCatalogViewFragment newInstance(long ownerId, int playlistId) {
        PlaylistCatalogViewFragment fragment = new PlaylistCatalogViewFragment();
        Bundle args = new Bundle();
        args.putLong("owner_id", ownerId);
        args.putInt("playlist_id", playlistId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (getArguments() != null) {
            playlistId = getArguments().getInt(ARG_PLAYLIST_ID, -1);
        }
        ACCESS_TOKEN = TokenManager.getInstance(requireContext()).getToken();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist_details, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        setupToolbar();

        // Initialize UI components
        textViewDetails = view.findViewById(R.id.textView);
        recyclerViewAudio = view.findViewById(R.id.recyclerViewTracks);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        toolbar = view.findViewById(R.id.toolbar);

        // Setup RecyclerView
        recyclerViewAudio.setLayoutManager(new LinearLayoutManager(getContext()));
        audioAdapter = new AudioAdapter(audioList);
        recyclerViewAudio.setAdapter(audioAdapter);

        // Setup Toolbar
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
                // Установим название плейлиста в заголовок
                actionBar.setTitle(getPlaylistTitle(playlistId));
            }
        }

        // Setup listeners
        audioAdapter.setOnItemClickListener(audio -> {
            playTrack(audio);
        });

        audioAdapter.setOnMenuClickListener(this::showBottomSheet);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadPlaylistTracks();
        });

        // Load data
        loadPlaylistTracks();

        return view;
    }

    private void setupToolbar() {
        toolbar.setNavigationIcon(R.drawable.arrow_left_black);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    }

    private String getPlaylistTitle(int playlistId) {
        switch(playlistId) {
            case -21: return "Для Вас";
            case -22: return "Открытия";
            case -23: return "Новинки";
            case -24: return "Плейлист дня 1";
            case -25: return "Плейлист дня 2";
            case -26: return "Плейлист дня 3";
            case -27: return "Плейлист дня 4";
            default: return "Плейлист";
        }
    }

    private void loadPlaylistTracks() {
        String urlString = "https://api.vk.com/method/audio.get" +
                "?owner_id=" + TokenManager.getInstance(getContext()).getUserId() +
                "&access_token=" + ACCESS_TOKEN +
                "&v=" + API_VERSION +
                "&playlist_id=" + playlistId;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(urlString)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        swipeRefreshLayout.setRefreshing(true);

        client.newCall(request).enqueue(new Callback() {
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
                                String url = audioObj.optString("url");
                                if (!url.isEmpty()) {
                                    VKAudio audio = new VKAudio(
                                            audioObj.optLong("id"),
                                            audioObj.optString("artist"),
                                            audioObj.optString("title"),
                                            url
                                    );
                                    audio.duration = audioObj.optInt("duration", 0);
                                    newAudioList.add(audio);
                                }
                            }

                            requireActivity().runOnUiThread(() -> {
                                audioList.clear();
                                audioList.addAll(newAudioList);
                                audioAdapter.notifyDataSetChanged();
                                swipeRefreshLayout.setRefreshing(false);
                                textViewDetails.setText("Треков: " + audioList.size());
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

    private void playTrack(VKAudio audio) {
        if (audio.url == null || audio.url.isEmpty()) {
            Toast.makeText(getContext(), "Трек недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(getContext(), MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_PLAY);
        intent.putExtra("URL", audio.url);
        intent.putExtra("TITLE", audio.title);
        intent.putExtra("ARTIST", audio.artist);
        intent.putExtra("DURATION", audio.duration);

        // Передаем весь плейлист и текущую позицию для переключения треков
        if (audioList != null && !audioList.isEmpty()) {
            int position = findAudioPosition(audio);
            if (position != -1) {
                // Конвертируем VKAudio в Audio для сервиса
                ArrayList<Audio> playlist = convertVKAudioListToAudioList(audioList);
                intent.putParcelableArrayListExtra("PLAYLIST", playlist);
                intent.putExtra("POSITION", position);
            }
        }

        ContextCompat.startForegroundService(requireContext(), intent);

        // Показываем плеер
        showPlayerBottomSheet();
    }

    // Конвертация VKAudio в Audio
    private ArrayList<Audio> convertVKAudioListToAudioList(List<VKAudio> vkAudioList) {
        ArrayList<Audio> result = new ArrayList<>();
        for (VKAudio vkAudio : vkAudioList) {
            Audio audio = new Audio(vkAudio.artist, vkAudio.title, vkAudio.url, null);
            audio.setDuration(vkAudio.duration);
            result.add(audio);
        }
        return result;
    }

    // Вспомогательный метод для поиска позиции аудио в списке
    private int findAudioPosition(VKAudio audio) {
        if (audioList == null) return -1;

        for (int i = 0; i < audioList.size(); i++) {
            VKAudio current = audioList.get(i);
            // Сравниваем по URL или ID
            if (current.url != null && current.url.equals(audio.url)) {
                return i;
            }
            // Альтернативное сравнение по ID если URL разный
            if (current.id == audio.id) {
                return i;
            }
        }
        return -1;
    }

    private void showPlayerBottomSheet() {
        PlayerBottomSheetFragment playerFragment = new PlayerBottomSheetFragment();
        playerFragment.show(getParentFragmentManager(), "player_bottom_sheet");
    }

    private void showBottomSheet(VKAudio audio) {
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_audio, null);
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        bottomSheetDialog.setContentView(view);

        TextView downloadBtn = view.findViewById(R.id.buttonDownload);
        downloadBtn.setOnClickListener(v -> {
            startDownload(requireContext(), audio.url, audio.artist + " - " + audio.title + ".mp3");
            bottomSheetDialog.dismiss();
        });

        // Добавляем другие кнопки если нужно
        TextView addToPlaylistBtn = view.findViewById(R.id.buttonAddToPlaylist);
        if (addToPlaylistBtn != null) {
            addToPlaylistBtn.setOnClickListener(v -> {
                // Логика добавления в плейлист
                bottomSheetDialog.dismiss();
            });
        }

        bottomSheetDialog.show();
    }

    private static void startDownload(Context context, String url, String filename) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(url);

        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setTitle("Скачивание трека");
        request.setDescription(filename);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, filename);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        if (downloadManager != null) {
            downloadManager.enqueue(request);
            Toast.makeText(context, "Начато скачивание: " + filename, Toast.LENGTH_SHORT).show();
        }
    }

    // Делаем VKAudio Parcelable для передачи между компонентами
    static class VKAudio implements Parcelable {
        long id;
        String artist;
        String title;
        String url;
        int duration;

        VKAudio(long id, String artist, String title, String url) {
            this.id = id;
            this.artist = artist;
            this.title = title;
            this.url = url;
        }

        protected VKAudio(Parcel in) {
            id = in.readLong();
            artist = in.readString();
            title = in.readString();
            url = in.readString();
            duration = in.readInt();
        }

        public static final Creator<VKAudio> CREATOR = new Creator<VKAudio>() {
            @Override
            public VKAudio createFromParcel(Parcel in) {
                return new VKAudio(in);
            }

            @Override
            public VKAudio[] newArray(int size) {
                return new VKAudio[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(id);
            dest.writeString(artist);
            dest.writeString(title);
            dest.writeString(url);
            dest.writeInt(duration);
        }
    }

    static class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {
        private List<VKAudio> audios;
        private OnItemClickListener listener;
        private OnMenuClickListener menuClickListener;

        interface OnItemClickListener {
            void onItemClick(VKAudio audio);
        }

        interface OnMenuClickListener {
            void onMenuClick(VKAudio audio);
        }

        AudioAdapter(List<VKAudio> audios) {
            this.audios = audios;
        }

        void setOnItemClickListener(OnItemClickListener listener) {
            this.listener = listener;
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
            VKAudio audio = audios.get(position);
            holder.artistTextView.setText(audio.artist + " " + audio.duration);
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

        private String formatDuration(int seconds) {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds);
        }

        @Override
        public int getItemCount() {
            return audios.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView artistTextView;
            TextView titleTextView;
            //TextView durationTextView;
            ImageView downloadButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                artistTextView = itemView.findViewById(R.id.artistText);
                titleTextView = itemView.findViewById(R.id.titleText);
               // durationTextView = itemView.findViewById(R.id.durationText);
                downloadButton = itemView.findViewById(R.id.downloadButton);

                // Если нет TextView для длительности, можно добавить его в layout

            }
        }
    }
}