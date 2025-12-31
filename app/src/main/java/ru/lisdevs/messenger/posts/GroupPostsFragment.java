package ru.lisdevs.messenger.posts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.Authorizer;
import ru.lisdevs.messenger.utils.TokenManager;

public class GroupPostsFragment extends Fragment implements ServiceConnection {
    private static final String TAG = "GroupPostsFragment";
    private static final String VK_API_BASE_URL = "https://api.vk.com/method/";
    private static final String VK_API_VERSION = "5.131";

    private RecyclerView postsRecycler;
    private SwipeRefreshLayout swipeRefresh;
    private PostAdapter postAdapter;
    private List<Post> posts = new ArrayList<>();
    private MusicPlayerService musicService;
    private boolean isBound = false;
    private int currentPlayingPosition = -1;
    private String currentAudioUrl = "";
    private Toolbar toolbar;
    private String groupId;
    private String groupName;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_posts, container, false);

        // Получаем переданные параметры
        if (getArguments() != null) {
            groupId = String.valueOf(getArguments().getLong("group_id"));
            groupName = getArguments().getString("group_name");
            Log.d(TAG, "Received group_id: " + groupId + ", group_name: " + groupName);
        }

        postsRecycler = view.findViewById(R.id.recyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefreshLayout);
        toolbar = view.findViewById(R.id.toolbar);
        setupToolbar();

        initRecyclerView();
        setupSwipeRefresh();

        // Привязываемся к сервису
        Intent serviceIntent = new Intent(getActivity(), MusicPlayerService.class);
        getActivity().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);

        loadVKPosts();

        return view;
    }

    private void initRecyclerView() {
        postAdapter = new PostAdapter(posts, new PostAdapter.AudioClickListener() {
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
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(this::loadVKPosts);
    }

    private void loadVKPosts() {
        swipeRefresh.setRefreshing(true);

        // Используем OkHttp вместо Volley для лучшего контроля над User-Agent
        new Thread(() -> {
            try {
                List<Post> result = loadVKPostsWithAudio();
                requireActivity().runOnUiThread(() -> {
                    if (result != null && !result.isEmpty()) {
                        posts.clear();
                        posts.addAll(result);
                        postAdapter.notifyDataSetChanged();
                    } else {
                        showError("Нет данных или ошибка загрузки");
                    }
                    swipeRefresh.setRefreshing(false);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showError("Ошибка загрузки: " + e.getMessage());
                    swipeRefresh.setRefreshing(false);
                });
            }
        }).start();
    }

    private List<Post> loadVKPostsWithAudio() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        String userAgent = getUserAgent();

        try {
            String urlString = "https://api.vk.com/method/wall.get" +
                    "?owner_id=" + URLEncoder.encode(String.valueOf(-Long.parseLong(groupId)), "UTF-8") +
                    "&count=20" +
                    "&v=" + URLEncoder.encode(VK_API_VERSION, "UTF-8") +
                    "&access_token=" + URLEncoder.encode(accessToken, "UTF-8") +
                    "&extended=1" +
                    "&fields=photo_100,first_name,last_name";

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

            int responseCode = connection.getResponseCode();
            InputStream inputStream;

            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder responseBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
            reader.close();
            connection.disconnect();

            String responseStr = responseBuilder.toString();
            return parseVKResponse(responseStr);

        } catch (Exception e) {
            Log.e(TAG, "Error loading VK posts", e);
            return null;
        }
    }

    private List<Post> parseVKResponse(String response) throws JSONException {
        JSONObject json = new JSONObject(response);

        if (json.has("error")) {
            JSONObject error = json.getJSONObject("error");
            String errorMsg = error.optString("error_msg", "Unknown error");
            Log.e(TAG, "API Error: " + errorMsg);
            return null;
        }

        JSONObject responseObj = json.getJSONObject("response");

        Map<Integer, User> users = parseUsers(responseObj.optJSONArray("profiles"));
        Map<Integer, Group> groups = parseGroups(responseObj.optJSONArray("groups"));

        JSONArray items = responseObj.getJSONArray("items");
        List<Post> postList = new ArrayList<>();

        for (int i = 0; i < items.length(); i++) {
            JSONObject postJson = items.getJSONObject(i);
            Post post = parsePost(postJson, users, groups);
            if (post != null && hasAudioAttachments(post)) {
                postList.add(post);
            }
        }

        return postList;
    }

    private boolean hasAudioAttachments(Post post) {
        if (post.attachments == null) return false;
        for (Attachment attachment : post.attachments) {
            if (attachment instanceof AudioAttachment) {
                return true;
            }
        }
        return false;
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

        JSONObject likesJson = postJson.optJSONObject("likes");
        if (likesJson != null) {
            post.likesCount = likesJson.getInt("count");
        }

        JSONObject commentsJson = postJson.optJSONObject("comments");
        if (commentsJson != null) {
            post.commentsCount = commentsJson.getInt("count");
        }

        JSONObject repostsJson = postJson.optJSONObject("reposts");
        if (repostsJson != null) {
            post.repostsCount = repostsJson.getInt("count");
        }

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
                        if (photo != null && !photo.sizes.isEmpty()) {
                            attachments.add(photo);
                        }
                        break;
                    case "audio":
                        AudioAttachment audio = parseAudioAttachment(attachmentJson);
                        if (audio != null && audio.url != null && !audio.url.isEmpty()) {
                            attachments.add(audio);
                        }
                        break;
                }
            }
        }
        return attachments;
    }

    private PhotoAttachment parsePhotoAttachment(JSONObject attachmentJson) throws JSONException {
        try {
            JSONObject photoJson = attachmentJson.getJSONObject("photo");
            PhotoAttachment photo = new PhotoAttachment();
            photo.id = photoJson.getInt("id");
            photo.albumId = photoJson.getInt("album_id");
            photo.ownerId = photoJson.getInt("owner_id");
            photo.text = photoJson.optString("text");

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
        try {
            JSONObject audioJson = attachmentJson.getJSONObject("audio");
            AudioAttachment audio = new AudioAttachment();
            audio.id = audioJson.getInt("id");
            audio.ownerId = audioJson.getInt("owner_id");
            audio.artist = audioJson.optString("artist", "Неизвестный исполнитель");
            audio.title = audioJson.optString("title", "Без названия");
            audio.duration = audioJson.optInt("duration", 0);
            audio.url = audioJson.optString("url", "");
            return audio;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing audio attachment", e);
            return null;
        }
    }

    private String getUserAgent() {
        if (isAuthViaAuthActivity()) {
            return "VKAndroidApp/1.0";
        } else {
            try {
                return Authorizer.getKateUserAgent();
            } catch (Exception e) {
                return "VKAndroidApp/1.0";
            }
        }
    }

    private boolean isAuthViaAuthActivity() {
        if (getContext() == null) return true;

        SharedPreferences prefs = getContext().getSharedPreferences("auth_prefs", Context.MODE_PRIVATE);
        String authType = prefs.getString("auth_type", null);

        if (authType != null) {
            return "AuthActivity".equals(authType);
        }

        // По умолчанию возвращаем true для совместимости
        return true;
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

    private void setupToolbar() {
        // Устанавливаем иконку "назад" (стрелка)
        toolbar.setNavigationIcon(R.drawable.arrow_left_black);

        // Устанавливаем обработчик нажатия
        toolbar.setNavigationOnClickListener(v -> {
            requireActivity().onBackPressed();
        });

        // Устанавливаем заголовок
        if (groupName != null && !groupName.isEmpty()) {
            toolbar.setTitle(groupName);
        } else {
            toolbar.setTitle("Посты группы");
        }
    }

    // Статический метод для создания нового экземпляра фрагмента
    public static GroupPostsFragment newInstance(long groupId, String groupName) {
        GroupPostsFragment fragment = new GroupPostsFragment();
        Bundle args = new Bundle();
        args.putLong("group_id", groupId);
        args.putString("group_name", groupName);
        fragment.setArguments(args);
        return fragment;
    }
}