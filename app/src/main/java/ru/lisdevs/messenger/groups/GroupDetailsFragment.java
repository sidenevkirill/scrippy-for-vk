package ru.lisdevs.messenger.groups;

import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import ru.lisdevs.messenger.music.AudioAdapter;
import ru.lisdevs.messenger.player.PlayerBottomSheetFragment;
import ru.lisdevs.messenger.playlists.PlaylistTracksFragment;
import ru.lisdevs.messenger.posts.GroupPostsFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;

public class GroupDetailsFragment extends Fragment {

    private RecyclerView recyclerView;
    private AudioAdapter adapter;
    private List<Audio> audioList = new ArrayList<>();
    private List<Audio> fullAudioList = new ArrayList<>();
    private long groupId;
    private String groupName;

    private TextView textViewResult;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Toolbar toolbar;

    // Новые элементы для плейлистов
    private RecyclerView playlistsRecyclerView;
    private PlaylistHorizontalAdapter playlistAdapter;
    private List<GroupPlaylistsFragment.PlaylistItem> playlistItems = new ArrayList<>();
    private TextView playlistsTitle;

    private MusicPlayerService musicService;
    private boolean serviceBound = false;
    private int currentTrackIndex = -1;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            musicService = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    public interface OnMenuClickListener {
        void onMenuClick(Audio audio);
    }

    private OnMenuClickListener menuClickListener;

    public void setOnMenuClickListener(OnMenuClickListener listener) {
        this.menuClickListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (getArguments() != null) {
            groupId = getArguments().getLong("group_id");
            groupName = getArguments().getString("group_name");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_friends_music, container, false);

        // Инициализация UI
        textViewResult = view.findViewById(R.id.count);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        toolbar = view.findViewById(R.id.toolbar);
        recyclerView = view.findViewById(R.id.friendsRecyclerView);

        // Инициализация элементов плейлистов
        playlistsRecyclerView = view.findViewById(R.id.recyclerViewPlaylists);
        playlistsTitle = view.findViewById(R.id.count);

        // Настройка Toolbar
        setupToolbar();

        // Настройка RecyclerView для треков
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AudioAdapter(audioList);
        recyclerView.setAdapter(adapter);

        // Настройка RecyclerView для плейлистов (горизонтальный)
        playlistsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(),
                LinearLayoutManager.HORIZONTAL, false));
        playlistAdapter = new PlaylistHorizontalAdapter(playlistItems);
        playlistsRecyclerView.setAdapter(playlistAdapter);

        // Обработка кликов на треки
        adapter.setOnItemClickListener(position -> {
            if (position >= 0 && position < audioList.size()) {
                playTrack(position);
            }
        });

        adapter.setOnMenuClickListener(audio -> showBottomSheet(audio));

        // Обработка кликов на плейлисты
        playlistAdapter.setOnPlaylistClickListener((playlistId, ownerId, title) -> {
            Fragment fragment = PlaylistTracksFragment.newInstance(playlistId, ownerId, title);
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.container, fragment)
                    .addToBackStack(null)
                    .commit();
        });

        // Настройка SwipeRefresh
        swipeRefreshLayout.setOnRefreshListener(this::refreshAllData);

        // Кнопки управления
        ImageView shuffleButton = view.findViewById(R.id.shuffle);
        shuffleButton.setOnClickListener(v -> shuffleAudioList());

        ImageView sortButton = view.findViewById(R.id.sort);
        sortButton.setOnClickListener(v -> showBottomSheetAccount());

        // Загрузка данных
        getAudioCount();
        refreshAllData();

        return view;
    }

    private void refreshAllData() {
        refreshAudioList();
        fetchGroupPlaylists();
    }

    private void fetchGroupPlaylists() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken == null) {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_LONG).show();
            return;
        }

        long ownerId = groupId;
        if (ownerId > 0) {
            ownerId = -ownerId;
        }

        String url = "https://api.vk.com/method/audio.getPlaylists" +
                "?owner_id=" + ownerId +
                "&access_token=" + accessToken +
                "&v=5.131" +
                "&count=10" + // Ограничиваем количество для горизонтального отображения
                "&extended=1";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    // Скрываем секцию плейлистов при ошибке
                    playlistsTitle.setVisibility(View.GONE);
                    playlistsRecyclerView.setVisibility(View.GONE);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(body);
                        if (jsonObject.has("response")) {
                            JSONObject responseObj = jsonObject.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");
                            List<GroupPlaylistsFragment.PlaylistItem> tempList = new ArrayList<>();

                            for (int i = 0; i < items.length(); i++) {
                                JSONObject playlistObj = items.getJSONObject(i);
                                long playlistId = playlistObj.getLong("id");
                                String title = playlistObj.getString("title");
                                long playlistOwnerId = playlistObj.getLong("owner_id");
                                String coverUrl = playlistObj.optString("photo_300", "");

                                String artistName = groupName;
                                if (playlistObj.has("owner")) {
                                    JSONObject ownerObj = playlistObj.getJSONObject("owner");
                                    artistName = ownerObj.optString("name", groupName);
                                }

                                tempList.add(new GroupPlaylistsFragment.PlaylistItem(
                                        (int) playlistId,
                                        title,
                                        String.valueOf(playlistOwnerId),
                                        artistName,
                                        coverUrl,
                                        playlistObj.optInt("count", 0)
                                ));
                            }

                            requireActivity().runOnUiThread(() -> {
                                playlistItems.clear();
                                playlistItems.addAll(tempList);
                                playlistAdapter.notifyDataSetChanged();

                                // Показываем или скрываем секцию в зависимости от наличия плейлистов
                                if (playlistItems.isEmpty()) {
                                    playlistsTitle.setVisibility(View.GONE);
                                    playlistsRecyclerView.setVisibility(View.GONE);
                                } else {
                                    playlistsTitle.setVisibility(View.VISIBLE);
                                    playlistsRecyclerView.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                    } catch (JSONException e) {
                        requireActivity().runOnUiThread(() -> {
                            playlistsTitle.setVisibility(View.GONE);
                            playlistsRecyclerView.setVisibility(View.GONE);
                        });
                    }
                }
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_details, menu);

        // Настройка SearchView
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterAudioList(newText);
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_show_friends) {
            navigateToGroupPlaylists();
            return true;
        } else if (id == R.id.action_show_posts) {
            navigateToGroupPosts();
            return true;
        } else if (id == android.R.id.home) {
            requireActivity().onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void filterAudioList(String query) {
        List<Audio> filteredList = new ArrayList<>();
        if (query.isEmpty()) {
            filteredList.addAll(fullAudioList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (Audio audio : fullAudioList) {
                if (audio.getTitle().toLowerCase().contains(lowerCaseQuery) ||
                        audio.getArtist().toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(audio);
                }
            }
        }
        adapter.updateList(filteredList);
        adapter.notifyDataSetChanged();
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);

            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                activity.getSupportActionBar().setTitle(groupName);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(getActivity(), MusicPlayerService.class);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        requireActivity().startService(intent);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (serviceBound) {
            requireActivity().unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private void refreshAudioList() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken != null) {
            fetchAudio(accessToken);
        } else {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
        }
    }

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
        if (audioList != null && !audioList.isEmpty()) {
            Collections.shuffle(audioList);
            adapter.updateList(audioList);
            adapter.notifyDataSetChanged();
        }
    }

    private void fetchAudio(String accessToken) {
        long ownerId = groupId > 0 ? -groupId : groupId;
        String url = "https://api.vk.com/method/audio.get" +
                "?owner_id=" + ownerId +
                "&access_token=" + accessToken +
                "&v=5.131";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    swipeRefreshLayout.setRefreshing(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body().string();
                requireActivity().runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(body);
                        if (json.has("response")) {
                            JSONArray items = json.getJSONObject("response").getJSONArray("items");
                            List<Audio> newList = parseAudioItems(items);
                            updateAudioList(newList);
                        } else if (json.has("error")) {
                            showApiError(json.getJSONObject("error"));
                        }
                    } catch (JSONException e) {
                        Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_LONG).show();
                    }
                    swipeRefreshLayout.setRefreshing(false);
                });
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


    private List<Audio> parseAudioItems(JSONArray items) throws JSONException {
        List<Audio> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject track = items.getJSONObject(i);
            String artist = track.optString("artist", "Unknown Artist");
            String title = track.optString("title", "Unknown Title");
            String url = track.optString("url");
            long ownerId = track.optLong("owner_id", 0);
            long audioId = track.optLong("id", 0);

            if (url != null && !url.isEmpty()) {
                Audio audio = new Audio(artist, title, url);
                audio.setOwnerId(ownerId);
                audio.setAudioId(audioId);
                result.add(audio);
            }
        }
        return result;
    }

    private void updateAudioList(List<Audio> newList) {
        fullAudioList.clear();
        fullAudioList.addAll(newList);

        audioList.clear();
        audioList.addAll(newList);

        adapter.updateList(audioList);
        adapter.notifyDataSetChanged();
    }

    private void showApiError(JSONObject error) {
        int code = error.optInt("error_code");
        String msg = error.optString("error_msg");
        Toast.makeText(getContext(), "Ошибка API (" + code + "): " + msg, Toast.LENGTH_LONG).show();
    }

    private void navigateToGroupPlaylists() {
        GroupPlaylistsFragment fragment = new GroupPlaylistsFragment();
        Bundle args = new Bundle();
        args.putString("GROUP_ID", String.valueOf(groupId));
        fragment.setArguments(args);

        getParentFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void navigateToGroupPosts() {
        GroupPostsFragment fragment = new GroupPostsFragment();
        Bundle args = new Bundle();
        args.putString("GROUP_ID", "-" + groupId);
        fragment.setArguments(args);

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
                .build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                updateCountText("Ошибка сети: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    if (json.has("response")) {
                        Object responseObj = json.get("response");
                        if (responseObj instanceof Integer) {
                            updateCountText("Перемешать все");
                        } else if (responseObj instanceof JSONObject) {
                            int count = ((JSONObject) responseObj).getInt("count");
                            updateCountText("Количество: " + count);
                        }
                    }
                } catch (JSONException e) {
                    updateCountText("Ошибка данных");
                }
            }
        });
    }

    private void updateCountText(String text) {
        requireActivity().runOnUiThread(() -> textViewResult.setText(text));
    }

    private void showBottomSheet(Audio audio) {
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_audio, null);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(view);

        view.findViewById(R.id.buttonDownload).setOnClickListener(v -> {
            downloadTrack(audio);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void downloadTrack(Audio audio) {
        String fileName = audio.getArtist() + " - " + audio.getTitle() + ".mp3";
        DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
        dm.enqueue(new DownloadManager.Request(Uri.parse(audio.getUrl()))
                .setTitle(fileName)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED));

        Toast.makeText(getContext(), "Скачивание начато", Toast.LENGTH_SHORT).show();
    }

    private void showBottomSheetAccount() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(R.layout.bottom_sheet_audio_duration);
        dialog.show();
    }

    public static GroupDetailsFragment newInstance(long groupId, String groupName) {
        GroupDetailsFragment fragment = new GroupDetailsFragment();
        Bundle args = new Bundle();
        args.putLong("group_id", groupId);
        args.putString("group_name", groupName);
        fragment.setArguments(args);
        return fragment;
    }

    // Адаптер для горизонтального отображения плейлистов
    private static class PlaylistHorizontalAdapter extends RecyclerView.Adapter<PlaylistHorizontalAdapter.PlaylistViewHolder> {
        private List<GroupPlaylistsFragment.PlaylistItem> playlistItems;
        private OnPlaylistClickListener listener;

        public interface OnPlaylistClickListener {
            void onPlaylistClick(long playlistId, long ownerId, String playlistTitle);
        }

        public void setOnPlaylistClickListener(OnPlaylistClickListener listener) {
            this.listener = listener;
        }

        public PlaylistHorizontalAdapter(List<GroupPlaylistsFragment.PlaylistItem> playlistItems) {
            this.playlistItems = playlistItems;
        }

        @NonNull
        @Override
        public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_horizontal, parent, false);
            return new PlaylistViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
            GroupPlaylistsFragment.PlaylistItem playlist = playlistItems.get(position);
            holder.titleText.setText(playlist.getTitle());

            // Устанавливаем количество треков
            String trackCount = playlist.getTrackCount() + " треков";
            holder.trackCountText.setText(trackCount);

            // Загрузка обложки
            if (!playlist.getCoverUrl().isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(playlist.getCoverUrl())
                        .placeholder(R.drawable.circle_playlist)
                        .into(holder.coverImage);
            } else {
                holder.coverImage.setImageResource(R.drawable.circle_playlist);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    long ownerId = Long.parseLong(playlist.getOwnerId());
                    listener.onPlaylistClick(playlist.getId(), ownerId, playlist.getTitle());
                }
            });
        }

        @Override
        public int getItemCount() {
            return playlistItems.size();
        }

        static class PlaylistViewHolder extends RecyclerView.ViewHolder {
            TextView titleText;
            TextView trackCountText;
            ImageView coverImage;

            public PlaylistViewHolder(@NonNull View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.titleText);
                trackCountText = itemView.findViewById(R.id.countText);
                coverImage = itemView.findViewById(R.id.coverImage);
            }
        }
    }
}