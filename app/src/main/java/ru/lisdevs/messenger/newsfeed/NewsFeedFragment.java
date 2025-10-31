package ru.lisdevs.messenger.newsfeed;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
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
import ru.lisdevs.messenger.friends.PhotoViewerFragment;
import ru.lisdevs.messenger.groups.GroupDetailsFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;
import ru.lisdevs.messenger.utils.VkAuthorizer;


public class NewsFeedFragment extends Fragment implements ServiceConnection {
    private static final String TAG = "VKPostsFragment";
    private static final String VK_API_BASE_URL = "https://api.vk.com/method/";
    private static final String VK_API_VERSION = "5.199";

    private RecyclerView postsRecycler;
    private SwipeRefreshLayout swipeRefresh;
    private FeedAdapter postAdapter;
    private List<FeedAdapter.Post> posts = new ArrayList<>();
    private MusicPlayerService musicService;
    private boolean isBound = false;
    private int currentPlayingPosition = -1;
    private String currentAudioUrl = "";
    private Toolbar toolbar;
    private ProgressBar progressBar;
    private TextView emptyView;
    private String nextFrom = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_news_feed, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        postsRecycler = view.findViewById(R.id.recyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefreshLayout);
        progressBar = view.findViewById(R.id.progressBar);
        emptyView = view.findViewById(R.id.emptyStateView);

        toolbar.setNavigationOnClickListener(v -> {
            if (isAdded()) {
                requireActivity().onBackPressed();
            }
        });

        // Создаем адаптер с обработчиком клика на фото
        postAdapter = new FeedAdapter(posts, new FeedAdapter.AudioClickListener() {
            @Override
            public void onAudioClick(FeedAdapter.AudioAttachment audio, int position) {
                if (isAdded()) {
                    playAudio(audio.url, position, audio.title, audio.artist);
                }
            }

            @Override
            public void onPlayPauseClick(FeedAdapter.AudioAttachment audio, int position) {
                if (!isAdded()) return;

                if (currentAudioUrl != null && currentAudioUrl.equals(audio.url)) {
                    togglePlayPause();
                } else {
                    playAudio(audio.url, position, audio.title, audio.artist);
                }
            }
        }, requireContext());

        // Добавляем обработчик клика на фото
        postAdapter.setPhotoClickListener((photoUrls, position) -> {
            if (isAdded()) {
                openPhotoViewer(photoUrls, position);
            }
        });

        // Добавляем обработчик клика на группу фото
        postAdapter.setPhotoGroupClickListener((photoUrls, position) -> {
            if (isAdded()) {
                openPhotoViewer(photoUrls, position);
            }
        });

        postAdapter.setGroupClickListener((groupId, groupName) -> {
            if (isAdded()) {
                openGroupDetails(groupId, groupName);
            }
        });

        postsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        postsRecycler.setAdapter(postAdapter);

        swipeRefresh.setOnRefreshListener(() -> {
            if (isAdded()) {
                refreshNewsFeed();
            }
        });

        if (isAdded()) {
            Intent serviceIntent = new Intent(requireActivity(), MusicPlayerService.class);
            requireActivity().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);
        }

        loadNewsFeed(false);

        return view;
    }

    // Метод для открытия просмотра фото
    private void openPhotoViewer(List<String> photoUrls, int startPosition) {
        if (photoUrls == null || photoUrls.isEmpty()) {
            return;
        }

        PhotoViewerFragment fragment = PhotoViewerFragment.newInstance(
                new ArrayList<>(photoUrls), startPosition
        );

        getParentFragmentManager().beginTransaction()

                .replace(R.id.container, fragment)
                .addToBackStack("photo_viewer")
                .commit();
    }

    private void openGroupDetails(long groupId, String groupName) {
        GroupDetailsFragment fragment = GroupDetailsFragment.newInstance(groupId, groupName);

        getParentFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack("group_details")
                .commit();
    }

    // Остальной код без изменений...
    private void loadNewsFeed(boolean isRefresh) {
        showLoading();
        String accessToken = TokenManager.getInstance(requireContext()).getToken();

        Map<String, String> params = new HashMap<>();
        params.put("filters", "post,photo,audio,video");
        params.put("count", "50");
        params.put("access_token", accessToken);
        params.put("v", VK_API_VERSION);
        params.put("extended", "1");
        params.put("fields", "photo_100,first_name,last_name,name,screen_name");
        params.put("attachments", "photo,audio,video,link,playlist");

        if (!isRefresh && !nextFrom.isEmpty()) {
            params.put("start_from", nextFrom);
        }

        StringRequest request = new StringRequest(
                Request.Method.GET,
                buildUrl("newsfeed.get", params),
                response -> {
                    try {
                        parseNewsFeedResponse(response, isRefresh);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing newsfeed response", e);
                        showError("Ошибка обработки ленты");
                    }
                },
                error -> {
                    Log.e(TAG, "Newsfeed request error", error);
                    showError("Ошибка загрузки ленты");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", VkAuthorizer.getKateUserAgent());
                return headers;
            }
        };

        Volley.newRequestQueue(requireContext()).add(request);
    }

    private void refreshNewsFeed() {
        nextFrom = "";
        loadNewsFeed(true);
    }

    private void parseNewsFeedResponse(String response, boolean isRefresh) throws JSONException {
        JSONObject json = new JSONObject(response);
        if (json.has("error")) {
            JSONObject error = json.getJSONObject("error");
            String errorMsg = error.optString("error_msg", "Неизвестная ошибка");
            int errorCode = error.optInt("error_code", 0);

            if (errorCode == 5) {
                showError("Ошибка авторизации. Войдите снова");
            } else if (errorCode == 6) {
                showError("Слишком много запросов. Попробуйте позже");
            } else {
                showError("Ошибка API: " + errorMsg);
            }
            return;
        }

        JSONObject responseObj = json.getJSONObject("response");
        nextFrom = responseObj.optString("next_from", "");

        Map<Integer, Author> authors = parseAuthors(
                responseObj.optJSONArray("profiles"),
                responseObj.optJSONArray("groups")
        );

        JSONArray items = responseObj.getJSONArray("items");

        if (isRefresh) {
            posts.clear();
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject postJson = items.getJSONObject(i);

            if (postJson.optString("type", "").equals("ad") ||
                    postJson.optBoolean("marked_as_ads", false)) {
                continue;
            }

            FeedAdapter.Post post = parsePost(postJson, authors);
            if (post == null) continue;

            if ((post.text != null && !post.text.isEmpty()) ||
                    (post.attachments != null && !post.attachments.isEmpty()) ||
                    (post.copyHistory != null && !post.copyHistory.isEmpty())) {
                posts.add(post);
            }
        }

        if (posts.isEmpty()) {
            showEmptyView("Нет новых постов");
        } else {
            showResults();
        }

        postAdapter.notifyDataSetChanged();
        swipeRefresh.setRefreshing(false);
    }

    private Map<Integer, Author> parseAuthors(JSONArray usersArray, JSONArray groupsArray) throws JSONException {
        Map<Integer, Author> authors = new HashMap<>();

        if (usersArray != null) {
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject userJson = usersArray.getJSONObject(i);
                Author author = new Author(
                        userJson.getInt("id"),
                        userJson.getString("first_name") + " " + userJson.getString("last_name"),
                        userJson.getString("photo_100"),
                        "user"
                );
                authors.put(author.id, author);
            }
        }

        if (groupsArray != null) {
            for (int i = 0; i < groupsArray.length(); i++) {
                JSONObject groupJson = groupsArray.getJSONObject(i);
                Author author = new Author(
                        -groupJson.getInt("id"),
                        groupJson.getString("name"),
                        groupJson.getString("photo_100"),
                        "group"
                );
                authors.put(author.id, author);
            }
        }

        return authors;
    }

    private FeedAdapter.Post parsePost(JSONObject postJson, Map<Integer, Author> authors) throws JSONException {
        FeedAdapter.Post post = new FeedAdapter.Post();

        post.id = postJson.optInt("post_id", postJson.optInt("id", 0));
        if (post.id == 0) return null;

        post.sourceId = postJson.optInt("source_id", postJson.optInt("owner_id", 0));
        post.date = postJson.optLong("date", 0);
        post.text = postJson.optString("text", "");

        JSONArray copyHistory = postJson.optJSONArray("copy_history");
        if (copyHistory != null && copyHistory.length() > 0) {
            post.copyHistory = new ArrayList<>();
            for (int i = 0; i < copyHistory.length(); i++) {
                JSONObject repostJson = copyHistory.getJSONObject(i);
                FeedAdapter.Post repost = parsePost(repostJson, authors);
                if (repost != null) post.copyHistory.add(repost);
            }
        }

        JSONObject likes = postJson.optJSONObject("likes");
        JSONObject comments = postJson.optJSONObject("comments");
        JSONObject reposts = postJson.optJSONObject("reposts");
        JSONObject views = postJson.optJSONObject("views");

        post.likesCount = likes != null ? likes.optInt("count", 0) : 0;
        post.commentsCount = comments != null ? comments.optInt("count", 0) : 0;
        post.repostsCount = reposts != null ? reposts.optInt("count", 0) : 0;
        post.viewsCount = views != null ? views.optInt("count", 0) : 0;

        if (post.sourceId != 0 && authors.containsKey(post.sourceId)) {
            Author author = authors.get(post.sourceId);
            post.author = new FeedAdapter.Author() {
                @Override public int getId() { return author.id; }
                @Override public String getName() { return author.name; }
                @Override public String getPhoto() { return author.photo; }
                @Override public String getType() { return author.type; }
            };
        }

        post.attachments = parseAttachments(postJson.optJSONArray("attachments"));
        return post;
    }

    private List<FeedAdapter.Attachment> parseAttachments(JSONArray attachmentsArray) throws JSONException {
        List<FeedAdapter.Attachment> attachments = new ArrayList<>();
        List<FeedAdapter.PhotoAttachment> photoGroup = new ArrayList<>();

        if (attachmentsArray != null) {
            for (int i = 0; i < attachmentsArray.length(); i++) {
                JSONObject attachmentJson = attachmentsArray.getJSONObject(i);
                String type = attachmentJson.optString("type");

                if ("photo".equals(type)) {
                    FeedAdapter.PhotoAttachment photo = parsePhotoAttachment(attachmentJson);
                    if (photo != null) {
                        photoGroup.add(photo);
                    }
                }
                else if (!photoGroup.isEmpty()) {
                    if (photoGroup.size() == 1) {
                        attachments.add(photoGroup.get(0));
                    } else {
                        attachments.add(new FeedAdapter.PhotoGroupAttachment(new ArrayList<>(photoGroup)));
                    }
                    photoGroup.clear();

                    switch (type) {
                        case "audio":
                            FeedAdapter.AudioAttachment audio = parseAudioAttachment(attachmentJson);
                            if (audio != null && audio.url != null && !audio.url.isEmpty()) {
                                attachments.add(audio);
                            }
                            break;
                        case "video":
                            FeedAdapter.VideoAttachment video = parseVideoAttachment(attachmentJson);
                            if (video != null) attachments.add(video);
                            break;
                        case "link":
                            FeedAdapter.LinkAttachment link = parseLinkAttachment(attachmentJson);
                            if (link != null) attachments.add(link);
                            break;
                        case "playlist":
                            FeedAdapter.PlaylistAttachment playlist = parsePlaylistAttachment(attachmentJson);
                            if (playlist != null) attachments.add(playlist);
                            break;
                    }
                }
                else {
                    switch (type) {
                        case "audio":
                            FeedAdapter.AudioAttachment audio = parseAudioAttachment(attachmentJson);
                            if (audio != null && audio.url != null && !audio.url.isEmpty()) {
                                attachments.add(audio);
                            }
                            break;
                        case "video":
                            FeedAdapter.VideoAttachment video = parseVideoAttachment(attachmentJson);
                            if (video != null) attachments.add(video);
                            break;
                        case "link":
                            FeedAdapter.LinkAttachment link = parseLinkAttachment(attachmentJson);
                            if (link != null) attachments.add(link);
                            break;
                        case "playlist":
                            FeedAdapter.PlaylistAttachment playlist = parsePlaylistAttachment(attachmentJson);
                            if (playlist != null) attachments.add(playlist);
                            break;
                    }
                }
            }

            if (!photoGroup.isEmpty()) {
                if (photoGroup.size() == 1) {
                    attachments.add(photoGroup.get(0));
                } else {
                    attachments.add(new FeedAdapter.PhotoGroupAttachment(new ArrayList<>(photoGroup)));
                }
            }
        }
        return attachments;
    }

    private FeedAdapter.PhotoAttachment parsePhotoAttachment(JSONObject attachmentJson) throws JSONException {
        try {
            JSONObject photoJson = attachmentJson.getJSONObject("photo");
            FeedAdapter.PhotoAttachment photo = new FeedAdapter.PhotoAttachment();

            JSONArray sizes = photoJson.getJSONArray("sizes");
            String bestUrl = "";
            int bestWidth = 0;

            for (int i = 0; i < sizes.length(); i++) {
                JSONObject size = sizes.getJSONObject(i);
                int width = size.optInt("width", 0);
                if (width > bestWidth) {
                    bestWidth = width;
                    bestUrl = size.optString("url", "");
                }
            }

            photo.url = bestUrl;
            photo.width = bestWidth;
            photo.height = photoJson.optInt("height", 0);
            photo.text = photoJson.optString("text", "");
            return photo;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing photo attachment", e);
            return null;
        }
    }

    private FeedAdapter.AudioAttachment parseAudioAttachment(JSONObject attachmentJson) throws JSONException {
        try {
            JSONObject audioJson = attachmentJson.getJSONObject("audio");
            FeedAdapter.AudioAttachment audio = new FeedAdapter.AudioAttachment();
            audio.id = audioJson.optInt("id", 0);
            audio.ownerId = audioJson.optInt("owner_id", 0);
            audio.artist = audioJson.optString("artist", "");
            audio.title = audioJson.optString("title", "");
            audio.duration = audioJson.optInt("duration", 0);
            audio.url = audioJson.optString("url", "");
            return audio;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing audio attachment", e);
            return null;
        }
    }

    private FeedAdapter.VideoAttachment parseVideoAttachment(JSONObject attachmentJson) throws JSONException {
        try {
            JSONObject videoJson = attachmentJson.getJSONObject("video");
            FeedAdapter.VideoAttachment video = new FeedAdapter.VideoAttachment();
            video.id = videoJson.optInt("id", 0);
            video.ownerId = videoJson.optInt("owner_id", 0);
            video.title = videoJson.optString("title", "");
            video.duration = videoJson.optInt("duration", 0);
            video.views = videoJson.optInt("views", 0);

            if (videoJson.has("image")) {
                JSONArray images = videoJson.getJSONArray("image");
                for (int i = 0; i < images.length(); i++) {
                    JSONObject image = images.getJSONObject(i);
                    if (image.optInt("width", 0) >= 320) {
                        video.previewUrl = image.optString("url", "");
                        break;
                    }
                }
            }
            return video;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing video attachment", e);
            return null;
        }
    }

    private FeedAdapter.LinkAttachment parseLinkAttachment(JSONObject attachmentJson) throws JSONException {
        try {
            JSONObject linkJson = attachmentJson.getJSONObject("link");
            FeedAdapter.LinkAttachment link = new FeedAdapter.LinkAttachment();
            link.url = linkJson.optString("url", "");
            link.title = linkJson.optString("title", "");
            link.description = linkJson.optString("description", "");

            if (linkJson.has("photo")) {
                JSONObject photo = linkJson.getJSONObject("photo");
                JSONArray sizes = photo.getJSONArray("sizes");
                for (int i = 0; i < sizes.length(); i++) {
                    JSONObject size = sizes.getJSONObject(i);
                    if (size.optInt("width", 0) >= 100) {
                        link.previewUrl = size.optString("url", "");
                        break;
                    }
                }
            }
            return link;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing link attachment", e);
            return null;
        }
    }

    private FeedAdapter.PlaylistAttachment parsePlaylistAttachment(JSONObject attachmentJson) throws JSONException {
        try {
            JSONObject playlistJson = attachmentJson.getJSONObject("playlist");
            FeedAdapter.PlaylistAttachment playlist = new FeedAdapter.PlaylistAttachment();

            playlist.id = playlistJson.optInt("id", 0);
            playlist.ownerId = playlistJson.optInt("owner_id", 0);
            playlist.title = playlistJson.optString("title", "");
            playlist.description = playlistJson.optString("description", "");
            playlist.count = playlistJson.optInt("count", 0);

            if (playlistJson.has("photo")) {
                JSONObject photo = playlistJson.getJSONObject("photo");
                JSONArray sizes = photo.getJSONArray("sizes");
                for (int i = 0; i < sizes.length(); i++) {
                    JSONObject size = sizes.getJSONObject(i);
                    if (size.optInt("width", 0) >= 300) {
                        playlist.photoUrl = size.optString("url", "");
                        break;
                    }
                }
            }

            if (playlistJson.has("owner")) {
                JSONObject owner = playlistJson.getJSONObject("owner");
                playlist.ownerName = owner.optString("name", "");
            }

            return playlist;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing playlist attachment", e);
            return null;
        }
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        postsRecycler.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(true);
    }

    private void showResults() {
        progressBar.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        postsRecycler.setVisibility(View.VISIBLE);
        swipeRefresh.setRefreshing(false);
    }

    private void showEmptyView(String message) {
        progressBar.setVisibility(View.GONE);
        postsRecycler.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(message);
        swipeRefresh.setRefreshing(false);
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
        showEmptyView(message);
    }

    private String buildUrl(String method, Map<String, String> params) {
        Uri.Builder builder = Uri.parse(VK_API_BASE_URL + method).buildUpon();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.appendQueryParameter(entry.getKey(), entry.getValue());
        }
        return builder.build().toString();
    }

    private static class Author {
        public int id;
        public String name;
        public String photo;
        public String type;

        public Author(int id, String name, String photo, String type) {
            this.id = id;
            this.name = name;
            this.photo = photo;
            this.type = type;
        }
    }
}