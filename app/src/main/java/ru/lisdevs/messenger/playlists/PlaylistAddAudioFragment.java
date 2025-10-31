package ru.lisdevs.messenger.playlists;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.music.Audio;
import ru.lisdevs.messenger.utils.TokenManager;

public class PlaylistAddAudioFragment extends BottomSheetDialogFragment {

    private static final String ARG_AUDIO = "audio";

    private RecyclerView recyclerView;
    private PlaylistAdapter adapter;
    private List<Playlist> playlists = new ArrayList<>();
    private Audio audio;
    private ProgressDialog progressDialog;

    public static PlaylistAddAudioFragment newInstance(ru.lisdevs.messenger.music.Audio audio) {
        PlaylistAddAudioFragment fragment = new PlaylistAddAudioFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_AUDIO, audio);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            audio = getArguments().getParcelable(ARG_AUDIO);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist_add_audio, container, false);

        setupToolbar(view);
        setupRecyclerView(view);
        fetchUserPlaylists();

        return view;
    }

    private void setupToolbar(View view) {
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle("Добавить в плейлист");
        toolbar.setNavigationIcon(R.drawable.arrow_left);
        toolbar.setNavigationOnClickListener(v -> dismiss());
    }

    private void setupRecyclerView(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new PlaylistAdapter(playlists, playlist -> {
            addAudioToPlaylist(playlist, audio);
        });

        recyclerView.setAdapter(adapter);
    }

    private void fetchUserPlaylists() {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            showToast("Токен не найден");
            return;
        }

        String userId = TokenManager.getInstance(requireContext()).getUserId();
        String url = "https://api.vk.com/method/audio.getPlaylists" +
                "?owner_id=" + userId +
                "&access_token=" + accessToken +
                "&v=5.131" +
                "&count=100";

        showProgress("Загрузка плейлистов...");

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", Authorizer.getKateUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        handleError("Ошибка загрузки плейлистов");
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("error")) {
                                handleError(json.getJSONObject("error").getString("error_msg"));
                                return;
                            }

                            JSONObject responseObj = json.getJSONObject("response");
                            List<Playlist> loadedPlaylists = parsePlaylists(responseObj);
                            updateUI(() -> {
                                playlists.clear();
                                playlists.addAll(loadedPlaylists);
                                adapter.notifyDataSetChanged();
                            });
                        } catch (Exception e) {
                            handleError("Ошибка обработки плейлистов");
                        } finally {
                            dismissProgress();
                        }
                    }
                });
    }

    private List<Playlist> parsePlaylists(JSONObject response) throws JSONException {
        List<Playlist> result = new ArrayList<>();
        JSONArray items = response.getJSONArray("items");

        for (int i = 0; i < items.length(); i++) {
            JSONObject playlist = items.getJSONObject(i);
            result.add(new Playlist(
                    playlist.getInt("id"),
                    playlist.getString("title"),
                    playlist.getLong("owner_id"),
                    playlist.getJSONObject("photo").getString("photo_300"),
                    playlist.getInt("count"),
                    playlist.optString("access_key", ""),
                    false,
                    playlist.optString("description", "")
            ));
        }
        return result;
    }

    private void addAudioToPlaylist(Playlist playlist, Audio audio) {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            showToast("Токен не найден");
            return;
        }

        showProgress("Добавление трека...");

        // Формируем параметры запроса
        HttpUrl.Builder urlBuilder = HttpUrl.parse("https://api.vk.com/method/audio.addToPlaylist")
                .newBuilder()
                .addQueryParameter("v", "5.131")
                .addQueryParameter("owner_id", String.valueOf(playlist.getOwnerId()))
                .addQueryParameter("playlist_id", String.valueOf(playlist.getId()))
                .addQueryParameter("audio_ids", audio.getOwnerId() + "_" + audio.getId())
                .addQueryParameter("access_token", accessToken);

        // Добавляем access_key если он есть
        if (!playlist.getAccessKey().isEmpty()) {
            urlBuilder.addQueryParameter("access_key", playlist.getAccessKey());
        }

        new OkHttpClient().newCall(new Request.Builder()
                        .url(urlBuilder.build())
                        .header("User-Agent", Authorizer.getKateUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        handleError("Ошибка сети: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);
                            Log.d("API_RESPONSE", json.toString()); // Логируем ответ

                            if (json.has("error")) {
                                JSONObject error = json.getJSONObject("error");
                                String errorMsg = error.getString("error_msg");
                                int errorCode = error.getInt("error_code");
                                handleError("Ошибка (" + errorCode + "): " + errorMsg);
                            } else {
                                updateUI(() -> {
                                    showToast("Трек успешно добавлен в плейлист");
                                    dismiss();
                                });
                            }
                        } catch (Exception e) {
                            handleError("Ошибка обработки ответа: " + e.getMessage());
                        } finally {
                            dismissProgress();
                        }
                    }
                });
    }

    // Вспомогательные методы
    private void showProgress(String message) {
        requireActivity().runOnUiThread(() -> {
            if (progressDialog == null) {
                progressDialog = new ProgressDialog(requireContext());
                progressDialog.setCancelable(false);
            }
            progressDialog.setMessage(message);
            progressDialog.show();
        });
    }

    private void dismissProgress() {
        requireActivity().runOnUiThread(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        });
    }

    private void showToast(String message) {
        requireActivity().runOnUiThread(() ->
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
    }

    private void handleError(String message) {
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            dismissProgress();
        });
    }

    private void updateUI(Runnable action) {
        requireActivity().runOnUiThread(action);
    }

    // Класс адаптера
    private static class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
        private final List<Playlist> playlists;
        private final OnPlaylistClickListener listener;

        interface OnPlaylistClickListener {
            void onClick(Playlist playlist);
        }

        PlaylistAdapter(List<Playlist> playlists, OnPlaylistClickListener listener) {
            this.playlists = playlists;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlists, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Playlist playlist = playlists.get(position);
            holder.title.setText(playlist.getTitle());
            holder.count.setText(playlist.getCount() + " треков");

            Glide.with(holder.itemView)
                    .load(playlist.getPhotoUrl())
                    .placeholder(R.drawable.music_note_24dp)
                    .into(holder.image);

            holder.itemView.setOnClickListener(v -> listener.onClick(playlist));
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView image;
            TextView title, count;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.coverImageView);
                title = itemView.findViewById(R.id.titleText);
                count = itemView.findViewById(R.id.artistText);
            }
        }
    }

    // Модель данных
    public static class Playlist {
        private final int id;
        private final String title;
        private final long ownerId;
        private final String photoUrl;
        private final int count;
        private final String accessKey;
        private final boolean isFollowing;
        private final String description;

        public Playlist(int id, String title, long ownerId, String photoUrl,
                        int count, String accessKey, boolean isFollowing, String description) {
            this.id = id;
            this.title = title;
            this.ownerId = ownerId;
            this.photoUrl = photoUrl;
            this.count = count;
            this.accessKey = accessKey;
            this.isFollowing = isFollowing;
            this.description = description;
        }

        // Геттеры
        public int getId() { return id; }
        public String getTitle() { return title; }
        public long getOwnerId() { return ownerId; }
        public String getPhotoUrl() { return photoUrl; }
        public int getCount() { return count; }
        public String getAccessKey() { return accessKey; }
        public boolean isFollowing() { return isFollowing; }
        public String getDescription() { return description; }
    }

}