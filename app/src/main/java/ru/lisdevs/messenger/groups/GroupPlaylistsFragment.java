package ru.lisdevs.messenger.groups;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;

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
import ru.lisdevs.messenger.playlists.PlaylistTracksFragment;
import ru.lisdevs.messenger.utils.TokenManager;

public class GroupPlaylistsFragment extends Fragment {

    private long groupId;
    private String groupName;
    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private PlayListAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private List<PlaylistItem> playlistData = new ArrayList<>();

    private String accessToken;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(long playlistId, long ownerId, String playlistTitle);
    }

    private OnPlaylistClickListener playlistClickListener;

    public void setOnPlaylistClickListener(OnPlaylistClickListener listener) {
        this.playlistClickListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        accessToken = TokenManager.getInstance(getContext()).getToken();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_group_playlists, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerView = view.findViewById(R.id.recyclerView);

        if (getArguments() != null) {
            groupId = Long.parseLong(getArguments().getString("GROUP_ID"));
            groupName = getArguments().getString("group_name");
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PlayListAdapter(playlistData);

        adapter.setOnPlaylistClickListener((playlistId, ownerId, title) -> {
            Fragment fragment = PlaylistTracksFragment.newInstance(playlistId, ownerId, title);
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.container, fragment)
                    .addToBackStack(null)
                    .commit();
        });

        recyclerView.setAdapter(adapter);

        toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null && getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                activity.getSupportActionBar().setTitle("Плейлисты");
            }
        }

        TextView idTextView = view.findViewById(R.id.textViewId);
        TextView nameTextView = view.findViewById(R.id.textViewName);

        String idDisplay = "@" + groupId;
        idTextView.setText(idDisplay);
        nameTextView.setText(groupName);

        if (accessToken != null) {
            fetchPlaylists(accessToken);
        } else {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_LONG).show();
        }

        return view;
    }

    private void fetchPlaylists(String accessToken) {
        long ownerId = groupId;
        if (ownerId > 0) {
            ownerId = -ownerId;
        }

        String url = "https://api.vk.com/method/audio.getPlaylists" +
                "?owner_id=" + ownerId +
                "&access_token=" + accessToken +
                "&v=5.131" +
                "&count=100" +
                "&extended=1";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Ошибка загрузки плейлистов", Toast.LENGTH_LONG).show()
                );
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
                            List<PlaylistItem> tempList = new ArrayList<>();

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

                                tempList.add(new PlaylistItem(
                                        (int) playlistId,
                                        title,
                                        String.valueOf(playlistOwnerId),
                                        artistName,
                                        coverUrl,
                                        playlistObj.optInt("count", 0)
                                ));
                            }

                            requireActivity().runOnUiThread(() -> {
                                playlistData.clear();
                                playlistData.addAll(tempList);
                                adapter.notifyDataSetChanged();
                            });
                        }
                    } catch (JSONException e) {
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_LONG).show()
                        );
                    }
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

    public static GroupPlaylistsFragment newInstance(long groupId, String groupName) {
        GroupPlaylistsFragment fragment = new GroupPlaylistsFragment();
        Bundle args = new Bundle();
        args.putLong("group_id", groupId);
        args.putString("group_name", groupName);
        fragment.setArguments(args);
        return fragment;
    }

    public static class PlaylistItem {
        private int id;
        private String title;
        private String ownerId;
        private String artist;
        private String coverUrl;
        private int trackCount;

        public PlaylistItem(int id, String title, String ownerId, String artist, String coverUrl, int trackCount) {
            this.id = id;
            this.title = title;
            this.ownerId = ownerId;
            this.artist = artist;
            this.coverUrl = coverUrl;
            this.trackCount = trackCount;
        }

        public int getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getOwnerId() {
            return ownerId;
        }

        public String getArtist() {
            return artist;
        }

        public String getCoverUrl() {
            return coverUrl;
        }

        public int getTrackCount() {
            return trackCount;
        }
    }

    private static class PlayListAdapter extends RecyclerView.Adapter<PlayListAdapter.PlaylistViewHolder> {
        private List<PlaylistItem> playlistItems;
        private OnPlaylistClickListener listener;

        public interface OnPlaylistClickListener {
            void onPlaylistClick(long playlistId, long ownerId, String playlistTitle);
        }

        public void setOnPlaylistClickListener(OnPlaylistClickListener listener) {
            this.listener = listener;
        }

        public PlayListAdapter(List<PlaylistItem> playlistItems) {
            this.playlistItems = playlistItems;
        }

        @NonNull
        @Override
        public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlists, parent, false);
            return new PlaylistViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
            PlaylistItem playlist = playlistItems.get(position);
            holder.titleText.setText(playlist.getTitle());

            // Устанавливаем имя исполнителя и количество треков
            String infoText = String.format("%s • %d треков",
                    playlist.getArtist(),
                    playlist.getTrackCount());
            holder.artistText.setText(infoText);

            // Загрузка обложки
            if (!playlist.getCoverUrl().isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(playlist.getCoverUrl())
                        .placeholder(R.drawable.music_note_24dp)
                        .into(holder.coverImage);
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
            TextView artistText;
            ImageView coverImage;

            public PlaylistViewHolder(@NonNull View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.titleText);
                artistText = itemView.findViewById(R.id.artistText);
                coverImage = itemView.findViewById(R.id.coverImage);
            }
        }
    }
}