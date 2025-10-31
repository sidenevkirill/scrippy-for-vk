package ru.lisdevs.messenger.groups;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
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
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

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

public class GroupPagerFragment extends Fragment implements ServiceConnection {

    private static final String TAG = "GroupDetailsFragment";
    private static final String ARG_GROUP_ID = "group_id";
    private static final String ARG_GROUP_NAME = "group_name";

    private long groupId;
    private String groupName;
    private Toolbar toolbar;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private MusicPlayerService musicService;
    private boolean serviceBound = false;

    public static GroupPagerFragment newInstance(long groupId, String groupName) {
        GroupPagerFragment fragment = new GroupPagerFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_GROUP_ID, groupId);
        args.putString(ARG_GROUP_NAME, groupName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            groupId = getArguments().getLong(ARG_GROUP_ID);
            groupName = getArguments().getString(ARG_GROUP_NAME);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_section, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        viewPager = view.findViewById(R.id.view_pager);
        tabLayout = view.findViewById(R.id.tabs);

        setupToolbar();
        setupViewPager();

        return view;
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setTitle(groupName);
            }
        }
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void setupViewPager() {
        GroupPagerAdapter adapter = new GroupPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Аудио"); break;
                case 1: tab.setText("Посты"); break;
                case 2: tab.setText("Плейлисты"); break;
            }
        }).attach();
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(getActivity(), MusicPlayerService.class);
        requireActivity().bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (serviceBound) {
            requireActivity().unbindService(this);
            serviceBound = false;
        }
    }

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

    private static class GroupPagerAdapter extends FragmentStateAdapter {
        private final GroupPagerFragment parentFragment;

        public GroupPagerAdapter(GroupPagerFragment fragment) {
            super(fragment);
            this.parentFragment = fragment;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return GroupAudioFragment.newInstance(parentFragment.groupId);
                case 1: return GroupPostsFragment.newInstance(parentFragment.groupId);
                case 2: return GroupPlaylistsFragment.newInstance(parentFragment.groupId);
                default: throw new IllegalArgumentException("Invalid position: " + position);
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    public static class GroupAudioFragment extends Fragment {
        private static final String ARG_GROUP_ID = "group_id";
        private long groupId;
        private RecyclerView recyclerView;
        private AudioAdapter adapter;
        private List<Audio> audioList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;

        public static GroupAudioFragment newInstance(long groupId) {
            GroupAudioFragment fragment = new GroupAudioFragment();
            Bundle args = new Bundle();
            args.putLong(ARG_GROUP_ID, groupId);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (getArguments() != null) {
                groupId = getArguments().getLong(ARG_GROUP_ID);
            }
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_audios, container, false);

            recyclerView = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

            adapter = new AudioAdapter(audioList, requireContext());
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(adapter);

            adapter.setOnItemClickListener(position -> {
                Audio audio = audioList.get(position);
                playAudio(audio);
            });

            swipeRefreshLayout.setOnRefreshListener(this::loadAudio);
            loadAudio();

            return view;
        }

        private void loadAudio() {
            swipeRefreshLayout.setRefreshing(true);
            String accessToken = TokenManager.getInstance(requireContext()).getToken();
            long ownerId = groupId > 0 ? -groupId : groupId;

            String url = "https://api.vk.com/method/audio.get" +
                    "?owner_id=" + ownerId +
                    "&access_token=" + accessToken +
                    "&v=5.131";

            new OkHttpClient().newCall(new Request.Builder()
                            .url(url)
                            .header("User-Agent", Authorizer.getKateUserAgent())
                            .build())
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Ошибка загрузки аудио", Toast.LENGTH_SHORT).show();
                                swipeRefreshLayout.setRefreshing(false);
                            });
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                if (json.has("response")) {
                                    JSONArray items = json.getJSONObject("response").getJSONArray("items");
                                    List<Audio> newList = parseAudioItems(items);

                                    requireActivity().runOnUiThread(() -> {
                                        audioList.clear();
                                        audioList.addAll(newList);
                                        adapter.notifyDataSetChanged();
                                        swipeRefreshLayout.setRefreshing(false);
                                    });
                                }
                            } catch (JSONException e) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                                    swipeRefreshLayout.setRefreshing(false);
                                });
                            }
                        }
                    });
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

        private void playAudio(Audio audio) {
            Intent intent = new Intent(getActivity(), MusicPlayerService.class);
            intent.setAction("PLAY");
            intent.putExtra("URL", audio.getUrl());
            intent.putExtra("TITLE", audio.getTitle());
            intent.putExtra("ARTIST", audio.getArtist());
            requireActivity().startService(intent);
        }

        public static class Audio {
            private String artist;
            private String title;
            private String url;
            private long ownerId;
            private long audioId;

            public Audio(String artist, String title, String url) {
                this.artist = artist;
                this.title = title;
                this.url = url;
            }

            public String getArtist() { return artist; }
            public String getTitle() { return title; }
            public String getUrl() { return url; }
            public long getOwnerId() { return ownerId; }
            public void setOwnerId(long ownerId) { this.ownerId = ownerId; }
            public long getAudioId() { return audioId; }
            public void setAudioId(long audioId) { this.audioId = audioId; }
        }

        public static class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.AudioViewHolder> {
            private List<Audio> audioList;
            private Context context;
            private OnItemClickListener listener;

            public interface OnItemClickListener {
                void onItemClick(int position);
            }

            public AudioAdapter(List<Audio> audioList, Context context) {
                this.audioList = audioList;
                this.context = context;
            }

            public void setOnItemClickListener(OnItemClickListener listener) {
                this.listener = listener;
            }

            @NonNull
            @Override
            public AudioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_audio, parent, false);
                return new AudioViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull AudioViewHolder holder, int position) {
                Audio audio = audioList.get(position);
                holder.titleText.setText(audio.getTitle());
                holder.artistText.setText(audio.getArtist());

                holder.itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onItemClick(position);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return audioList.size();
            }

            static class AudioViewHolder extends RecyclerView.ViewHolder {
                TextView titleText;
                TextView artistText;

                public AudioViewHolder(@NonNull View itemView) {
                    super(itemView);
                    titleText = itemView.findViewById(R.id.titleText);
                    artistText = itemView.findViewById(R.id.artistText);
                }
            }
        }
    }

    public static class GroupPostsFragment extends Fragment {
        private static final String ARG_GROUP_ID = "group_id";
        private String groupId;
        private RecyclerView recyclerView;
        private PostAdapter adapter;
        private List<Post> posts = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;

        public static GroupPostsFragment newInstance(long groupId) {
            GroupPostsFragment fragment = new GroupPostsFragment();
            Bundle args = new Bundle();
            args.putString(ARG_GROUP_ID, String.valueOf(groupId));
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (getArguments() != null) {
                groupId = getArguments().getString(ARG_GROUP_ID);
            }
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_audios, container, false);

            recyclerView = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

            adapter = new PostAdapter(posts, requireContext());
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(adapter);

            swipeRefreshLayout.setOnRefreshListener(this::loadPosts);
            loadPosts();

            return view;
        }

        private void loadPosts() {
            swipeRefreshLayout.setRefreshing(true);
            String accessToken = TokenManager.getInstance(requireContext()).getToken();

            String url = "https://api.vk.com/method/wall.get" +
                    "?owner_id=" + groupId +
                    "&access_token=" + accessToken +
                    "&v=5.131" +
                    "&count=20" +
                    "&extended=1";

            new OkHttpClient().newCall(new Request.Builder()
                            .url(url)
                            .header("User-Agent", Authorizer.getKateUserAgent())
                            .build())
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Ошибка загрузки постов", Toast.LENGTH_SHORT).show();
                                swipeRefreshLayout.setRefreshing(false);
                            });
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                if (json.has("response")) {
                                    JSONArray items = json.getJSONObject("response").getJSONArray("items");
                                    List<Post> newPosts = parsePosts(items);

                                    requireActivity().runOnUiThread(() -> {
                                        posts.clear();
                                        posts.addAll(newPosts);
                                        adapter.notifyDataSetChanged();
                                        swipeRefreshLayout.setRefreshing(false);
                                    });
                                }
                            } catch (JSONException e) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                                    swipeRefreshLayout.setRefreshing(false);
                                });
                            }
                        }
                    });
        }

        private List<Post> parsePosts(JSONArray items) throws JSONException {
            List<Post> result = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject postJson = items.getJSONObject(i);
                Post post = new Post();
                post.id = postJson.getInt("id");
                post.text = postJson.getString("text");
                post.date = postJson.getLong("date");

                JSONArray attachments = postJson.optJSONArray("attachments");
                if (attachments != null) {
                    post.attachments = parseAttachments(attachments);
                }

                result.add(post);
            }
            return result;
        }

        private List<Attachment> parseAttachments(JSONArray attachments) throws JSONException {
            List<Attachment> result = new ArrayList<>();
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject attachment = attachments.getJSONObject(i);
                String type = attachment.getString("type");

                if ("photo".equals(type)) {
                    result.add(parsePhotoAttachment(attachment.getJSONObject("photo")));
                } else if ("audio".equals(type)) {
                    result.add(parseAudioAttachment(attachment.getJSONObject("audio")));
                }
            }
            return result;
        }

        private PhotoAttachment parsePhotoAttachment(JSONObject photo) throws JSONException {
            PhotoAttachment attachment = new PhotoAttachment();
            attachment.id = photo.getInt("id");
            // Дополнительные поля для фото
            return attachment;
        }

        private AudioAttachment parseAudioAttachment(JSONObject audio) throws JSONException {
            AudioAttachment attachment = new AudioAttachment();
            attachment.id = audio.getInt("id");
            attachment.title = audio.getString("title");
            attachment.artist = audio.getString("artist");
            attachment.url = audio.getString("url");
            return attachment;
        }

        public static class Post {
            public int id;
            public String text;
            public long date;
            public List<Attachment> attachments;
        }

        public static abstract class Attachment {
            public int id;
        }

        public static class PhotoAttachment extends Attachment {
            // Дополнительные поля для фото
        }

        public static class AudioAttachment extends Attachment {
            public String title;
            public String artist;
            public String url;
        }

        public static class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {
            private List<Post> posts;
            private Context context;
            private OnAudioClickListener audioClickListener;

            public interface OnAudioClickListener {
                void onAudioClick(AudioAttachment audio);
            }

            public PostAdapter(List<Post> posts, Context context) {
                this.posts = posts;
                this.context = context;
            }

            public void setOnAudioClickListener(OnAudioClickListener listener) {
                this.audioClickListener = listener;
            }

            @NonNull
            @Override
            public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
                return new PostViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
                Post post = posts.get(position);
                holder.bind(post);
            }

            @Override
            public int getItemCount() {
                return posts.size();
            }

            class PostViewHolder extends RecyclerView.ViewHolder {
                public PostViewHolder(@NonNull View itemView) {
                    super(itemView);
                }

                public void bind(Post post) {
                    // Реализация привязки данных поста
                }
            }
        }
    }

    public static class GroupPlaylistsFragment extends Fragment {
        private static final String ARG_GROUP_ID = "group_id";
        private long groupId;
        private RecyclerView recyclerView;
        private PlaylistAdapter adapter;
        private List<Playlist> playlists = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;

        public static GroupPlaylistsFragment newInstance(long groupId) {
            GroupPlaylistsFragment fragment = new GroupPlaylistsFragment();
            Bundle args = new Bundle();
            args.putLong(ARG_GROUP_ID, groupId);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (getArguments() != null) {
                groupId = getArguments().getLong(ARG_GROUP_ID);
            }
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_audios, container, false);

            recyclerView = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

            adapter = new PlaylistAdapter(playlists);
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(adapter);

            adapter.setOnPlaylistClickListener(playlist -> {
                // Обработка клика на плейлист
            });

            swipeRefreshLayout.setOnRefreshListener(this::loadPlaylists);
            loadPlaylists();

            return view;
        }

        private void loadPlaylists() {
            swipeRefreshLayout.setRefreshing(true);
            String accessToken = TokenManager.getInstance(requireContext()).getToken();
            long ownerId = groupId > 0 ? -groupId : groupId;

            String url = "https://api.vk.com/method/audio.getPlaylists" +
                    "?owner_id=" + ownerId +
                    "&access_token=" + accessToken +
                    "&v=5.131" +
                    "&count=100";

            new OkHttpClient().newCall(new Request.Builder()
                            .url(url)
                            .header("User-Agent", Authorizer.getKateUserAgent())
                            .build())
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Ошибка загрузки плейлистов", Toast.LENGTH_SHORT).show();
                                swipeRefreshLayout.setRefreshing(false);
                            });
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                if (json.has("response")) {
                                    JSONArray items = json.getJSONObject("response").getJSONArray("items");
                                    List<Playlist> newPlaylists = parsePlaylists(items);

                                    requireActivity().runOnUiThread(() -> {
                                        playlists.clear();
                                        playlists.addAll(newPlaylists);
                                        adapter.notifyDataSetChanged();
                                        swipeRefreshLayout.setRefreshing(false);
                                    });
                                }
                            } catch (JSONException e) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                                    swipeRefreshLayout.setRefreshing(false);
                                });
                            }
                        }
                    });
        }

        private List<Playlist> parsePlaylists(JSONArray items) throws JSONException {
            List<Playlist> result = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject playlistJson = items.getJSONObject(i);
                Playlist playlist = new Playlist();
                playlist.id = playlistJson.getLong("id");
                playlist.title = playlistJson.getString("title");
                playlist.ownerId = playlistJson.getLong("owner_id");
                playlist.coverUrl = playlistJson.optString("photo_300", "");
                result.add(playlist);
            }
            return result;
        }

        public static class Playlist {
            public long id;
            public String title;
            public long ownerId;
            public String coverUrl;
        }

        public static class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {
            private List<Playlist> playlists;
            private OnPlaylistClickListener listener;

            public interface OnPlaylistClickListener {
                void onPlaylistClick(Playlist playlist);
            }

            public PlaylistAdapter(List<Playlist> playlists) {
                this.playlists = playlists;
            }

            public void setOnPlaylistClickListener(OnPlaylistClickListener listener) {
                this.listener = listener;
            }

            @NonNull
            @Override
            public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlists, parent, false);
                return new PlaylistViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
                Playlist playlist = playlists.get(position);
                holder.titleText.setText(playlist.title);

                holder.itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onPlaylistClick(playlist);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return playlists.size();
            }

            static class PlaylistViewHolder extends RecyclerView.ViewHolder {
                TextView titleText;

                public PlaylistViewHolder(@NonNull View itemView) {
                    super(itemView);
                    titleText = itemView.findViewById(R.id.titleText);
                }
            }
        }
    }
}