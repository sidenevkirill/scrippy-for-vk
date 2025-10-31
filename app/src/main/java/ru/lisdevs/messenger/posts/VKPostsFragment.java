package ru.lisdevs.messenger.posts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;

public class VKPostsFragment extends Fragment implements ServiceConnection {
    private static final String TAG = "VKPostsFragment";
    private static final String VK_API_BASE_URL = "https://api.vk.com/method/";
    private static final String VK_API_VERSION = "5.131";

    private RecyclerView postsRecycler;
    private SwipeRefreshLayout swipeRefresh;
    private FeedAdapter postAdapter;
    private List<Post> posts = new ArrayList<>();
    private MusicPlayerService musicService;
    private boolean isBound = false;
    private int currentPlayingPosition = -1;
    private String currentAudioUrl = "";
    private Toolbar toolbar;
    private long ownerId = -231807504; // ID группы

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_test, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        postsRecycler = view.findViewById(R.id.recyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefreshLayout);

        // Setup Toolbar
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayShowHomeEnabled(true);
            }
        }

        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        // Настройка RecyclerView
        postAdapter = new FeedAdapter(posts, new FeedAdapter.AudioClickListener() {
            @Override
            public void onAudioClick(AudioAttachment audio, int position) {
                playAudio(audio.url, position, audio.title, audio.artist);
            }

            @Override
            public void onPlayPauseClick(AudioAttachment audio, int position) {
                if (currentAudioUrl.equals(audio.url)) {
                    togglePlayPause();
                } else {
                    playAudio(audio.url, position, audio.title, audio.artist);
                }
            }
        }, requireContext());

        postsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        postsRecycler.setAdapter(postAdapter);

        // Настройка SwipeRefresh
        swipeRefresh.setOnRefreshListener(this::loadVKPosts);

        // Привязка к сервису
        Intent serviceIntent = new Intent(getActivity(), MusicPlayerService.class);
        getActivity().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);

        // Загрузка данных
        loadVKPosts();

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_account, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            requireActivity().onBackPressed();
            return true;
        }
        if (item.getItemId() == R.id.action_show_account) {
            swipeRefresh.setRefreshing(true);
            loadVKPosts();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadVKPosts() {
        swipeRefresh.setRefreshing(true);

        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("count", "20");
        params.put("access_token", TokenManager.getInstance(getContext()).getToken());
        params.put("v", VK_API_VERSION);
        params.put("extended", "1");
        params.put("fields", "photo_100,first_name,last_name");
        params.put("attachments", "photo,audio,playlist");

        StringRequest request = new StringRequest(
                Request.Method.GET,
                buildUrl(params),
                response -> {
                    try {
                        parseResponse(response);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing response", e);
                        showError("Ошибка обработки данных");
                    }
                },
                error -> {
                    Log.e(TAG, "Request error", error);
                    showError("Ошибка загрузки");
                }
        );

        Volley.newRequestQueue(requireContext()).add(request);
    }

    private String buildUrl(Map<String, String> params) {
        Uri.Builder builder = Uri.parse(VK_API_BASE_URL + "wall.get").buildUpon();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.appendQueryParameter(entry.getKey(), entry.getValue());
        }
        return builder.build().toString();
    }

    private void parseResponse(String response) throws JSONException {
        JSONObject json = new JSONObject(response);
        JSONObject responseObj = json.getJSONObject("response");

        Map<Integer, User> users = parseUsers(responseObj.optJSONArray("profiles"));
        Map<Integer, Group> groups = parseGroups(responseObj.optJSONArray("groups"));

        JSONArray items = responseObj.getJSONArray("items");
        posts.clear();

        for (int i = 0; i < items.length(); i++) {
            JSONObject postJson = items.getJSONObject(i);
            Post post = parsePost(postJson, users, groups);
            if (post != null) {
                posts.add(post);
            }
        }

        postAdapter.notifyDataSetChanged();
        swipeRefresh.setRefreshing(false);
    }

    private Map<Integer, User> parseUsers(JSONArray usersArray) throws JSONException {
        Map<Integer, User> users = new HashMap<>();
        if (usersArray != null) {
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject userJson = usersArray.getJSONObject(i);
                User user = new User(
                        userJson.getInt("id"),
                        userJson.getString("first_name"),
                        userJson.getString("last_name"),
                        userJson.getString("photo_100")
                );
                users.put(user.id, user);
            }
        }
        return users;
    }

    private Map<Integer, Group> parseGroups(JSONArray groupsArray) throws JSONException {
        Map<Integer, Group> groups = new HashMap<>();
        if (groupsArray != null) {
            for (int i = 0; i < groupsArray.length(); i++) {
                JSONObject groupJson = groupsArray.getJSONObject(i);
                Group group = new Group(
                        groupJson.getInt("id"),
                        groupJson.getString("name"),
                        groupJson.getString("photo_100")
                );
                groups.put(group.id, group);
            }
        }
        return groups;
    }

    private Post parsePost(JSONObject postJson, Map<Integer, User> users, Map<Integer, Group> groups) throws JSONException {
        Post post = new Post();
        post.id = postJson.getInt("id");
        post.date = postJson.getLong("date");
        post.text = postJson.getString("text");

        int fromId = postJson.getInt("from_id");
        if (fromId > 0) {
            post.author = users.get(fromId);
        } else {
            post.author = groups.get(-fromId);
        }

        post.attachments = parseAttachments(postJson.optJSONArray("attachments"));

        return post;
    }

    private List<Attachment> parseAttachments(JSONArray attachmentsArray) throws JSONException {
        List<Attachment> attachments = new ArrayList<>();
        if (attachmentsArray != null) {
            for (int i = 0; i < attachmentsArray.length(); i++) {
                JSONObject attachmentJson = attachmentsArray.getJSONObject(i);
                String type = attachmentJson.getString("type");

                switch (type) {
                    case "photo":
                        PhotoAttachment photo = parsePhotoAttachment(attachmentJson);
                        if (photo != null) {
                            attachments.add(photo);
                        }
                        break;
                    case "audio":
                        attachments.add(parseAudioAttachment(attachmentJson));
                        break;
                    case "playlist":
                        PlaylistAttachment playlist = parsePlaylistAttachment(attachmentJson);
                        if (playlist != null) {
                            attachments.add(playlist);
                        }
                        break;
                }
            }
        }
        return attachments;
    }

    private PlaylistAttachment parsePlaylistAttachment(JSONObject attachmentJson) throws JSONException {
        try {
            JSONObject playlistJson = attachmentJson.getJSONObject("playlist");
            PlaylistAttachment playlist = new PlaylistAttachment();
            playlist.id = playlistJson.getInt("id");
            playlist.ownerId = playlistJson.getInt("owner_id");
            playlist.title = playlistJson.getString("title");
            playlist.count = playlistJson.getInt("count");

            if (playlistJson.has("photo")) {
                JSONObject photoJson = playlistJson.getJSONObject("photo");
                PhotoAttachment photo = new PhotoAttachment();
                photo.sizes = new HashMap<>();

                JSONArray sizesArray = photoJson.getJSONArray("sizes");
                for (int i = 0; i < sizesArray.length(); i++) {
                    JSONObject sizeJson = sizesArray.getJSONObject(i);
                    photo.sizes.put(sizeJson.getString("type"), sizeJson.getString("url"));
                }

                playlist.photo = photo;
            }

            return playlist;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing playlist attachment", e);
            return null;
        }
    }

    private PhotoAttachment parsePhotoAttachment(JSONObject attachmentJson) throws JSONException {
        try {
            JSONObject photoJson = attachmentJson.getJSONObject("photo");
            PhotoAttachment photo = new PhotoAttachment();
            photo.sizes = new HashMap<>();

            JSONArray sizesArray = photoJson.getJSONArray("sizes");
            for (int i = 0; i < sizesArray.length(); i++) {
                JSONObject sizeJson = sizesArray.getJSONObject(i);
                photo.sizes.put(sizeJson.getString("type"), sizeJson.getString("url"));
            }

            return photo;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing photo attachment", e);
            return null;
        }
    }

    private AudioAttachment parseAudioAttachment(JSONObject attachmentJson) throws JSONException {
        JSONObject audioJson = attachmentJson.getJSONObject("audio");
        AudioAttachment audio = new AudioAttachment();
        audio.id = audioJson.getInt("id");
        audio.ownerId = audioJson.getInt("owner_id");
        audio.artist = audioJson.getString("artist");
        audio.title = audioJson.getString("title");
        audio.duration = audioJson.getInt("duration");
        audio.url = audioJson.getString("url");
        return audio;
    }

    public void playAudio(String url, int position, String title, String artist) {
        if (isBound && musicService != null) {
            if (currentAudioUrl.equals(url)) {
                togglePlayPause();
                return;
            }

            currentAudioUrl = url;
            currentPlayingPosition = position;

            Intent playIntent = new Intent(getActivity(), MusicPlayerService.class);
            playIntent.setAction("PLAY");
            playIntent.putExtra("URL", url);
            playIntent.putExtra("TITLE", title);
            playIntent.putExtra("ARTIST", artist);
            getActivity().startService(playIntent);

            postAdapter.notifyItemChanged(position);
        }
    }

    public void togglePlayPause() {
        if (isBound && musicService != null) {
            Intent toggleIntent = new Intent(getActivity(), MusicPlayerService.class);
            toggleIntent.setAction("TOGGLE");
            getActivity().startService(toggleIntent);

            if (currentPlayingPosition != -1) {
                postAdapter.notifyItemChanged(currentPlayingPosition);
            }
        }
    }

    public boolean isCurrentPlaying(int position, String url) {
        return position == currentPlayingPosition &&
                currentAudioUrl.equals(url) &&
                isBound &&
                musicService != null &&
                musicService.isPlaying();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
        musicService = binder.getService();
        isBound = true;

        if (postAdapter != null) {
            postAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        isBound = false;
        musicService = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isBound) {
            getActivity().unbindService(this);
            isBound = false;
        }
    }

    private void showError(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        swipeRefresh.setRefreshing(false);
    }
}