package ru.lisdevs.messenger.friends;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import ru.lisdevs.messenger.playlists.PlaylistDetailsFragment;
import ru.lisdevs.messenger.utils.TokenManager;

public class OtherFragment extends Fragment {
    private String friendIdStr;
    private RecyclerView recyclerView;
    private PlaylistAdapter adapter;
    private List<PlaylistItem> playlistData = new ArrayList<>();
    private TextView textViewDetails;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Toolbar toolbar;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            long friendIdLong = getArguments().getLong("friend_id");
            friendIdStr = String.valueOf(friendIdLong);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_other, container, false);

        textViewDetails = view.findViewById(R.id.statusTextView);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        toolbar = view.findViewById(R.id.toolbar);
        setupToolbar();

        adapter = new PlaylistAdapter(playlistData, item -> {
            Bundle args = new Bundle();
            args.putInt("playlist_id", item.getId());
            args.putString("playlist_title", item.getTitle());
            args.putInt("playlist_count", item.getCount());
            args.putString("playlist_artist", item.getArtist());
            args.putLong("owner_id", Long.parseLong(item.getOwnerId()));

            Fragment fragment = PlaylistDetailsFragment.newInstance(
                    item.getId(),
                    item.getTitle(),
                    item.getCount(),
                    item.getArtist(),
                    Long.parseLong(item.getOwnerId())
            );
            fragment.setArguments(args);

            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, fragment)
                    .addToBackStack(null)
                    .commit();
        });
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this::fetchUserPlaylists);

        Bundle args = getArguments();
        if (args != null) {
            long friendIdLong = args.getLong("friend_id");
            friendIdStr = String.valueOf(friendIdLong);
            String friendName = args.getString("friend_name");

            textViewDetails.setText("ID: " + friendIdStr + "\nИмя: " + friendName);
            if (getActivity() instanceof AppCompatActivity && friendName != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(friendName);
            }

            fetchUserPlaylists();
        }

        return view;
    }

    private void fetchUserPlaylists() {
        OkHttpClient client = new OkHttpClient();
        String accessToken = TokenManager.getInstance(getContext()).getToken();

        String url = "https://api.vk.com/method/audio.getPlaylists" +
                "?owner_id=" + friendIdStr +
                "&access_token=" + accessToken +
                "&count=100" +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        swipeRefreshLayout.setRefreshing(true);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Ошибка сети", Toast.LENGTH_SHORT).show();
                    swipeRefreshLayout.setRefreshing(false);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String respStr = response.body().string();
                    JSONObject json = new JSONObject(respStr);

                    if (json.has("response")) {
                        JSONObject responseObj = json.getJSONObject("response");
                        JSONArray items = responseObj.getJSONArray("items");

                        List<PlaylistItem> playlists = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject playlistJson = items.getJSONObject(i);
                            playlists.add(new PlaylistItem(
                                    playlistJson.getInt("id"),
                                    playlistJson.getString("title"),
                                    String.valueOf(playlistJson.getLong("owner_id")),
                                    playlistJson.optString("artist", "Плейлист"),
                                    playlistJson.getInt("count")
                            ));
                        }

                        requireActivity().runOnUiThread(() -> {
                            playlistData.clear();
                            playlistData.addAll(playlists);
                            adapter.notifyDataSetChanged();
                            swipeRefreshLayout.setRefreshing(false);
                        });
                    }
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void setupToolbar() {
        toolbar.setNavigationIcon(R.drawable.arrow_left_black);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        toolbar.setTitle("Плейлисты");
    }

    public static class PlaylistItem {
        private int id;
        private String title;
        private String ownerId;
        private String artist;
        private int count;

        public PlaylistItem(int id, String title, String ownerId, String artist, int count) {
            this.id = id;
            this.title = title;
            this.ownerId = ownerId;
            this.artist = artist;
            this.count = count;
        }

        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getOwnerId() { return ownerId; }
        public String getArtist() { return artist; }
        public int getCount() { return count; }
    }

    public static class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
        private List<PlaylistItem> playlists;
        private OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(PlaylistItem item);
        }

        public PlaylistAdapter(List<PlaylistItem> playlists, OnItemClickListener listener) {
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
            PlaylistItem playlist = playlists.get(position);
            holder.titleTextView.setText(playlist.getTitle());
            holder.artistTextView.setText(playlist.getArtist());
        //    holder.countTextView.setText("Треков: " + playlist.getCount());

            holder.itemView.setOnClickListener(v -> listener.onItemClick(playlist));
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView titleTextView;
            TextView artistTextView;
          //  TextView countTextView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                titleTextView = itemView.findViewById(R.id.titleText);
                artistTextView = itemView.findViewById(R.id.artistText);
         //       countTextView = itemView.findViewById(R.id.playlistCount);
            }
        }
    }
}