package ru.lisdevs.messenger.stories;

import static androidx.core.content.ContentProviderCompat.requireContext;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.auth.AuthActivity;
import ru.lisdevs.messenger.utils.TokenManager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class FriendsStoriesFragment extends Fragment {

    private RecyclerView recyclerViewStories;
    private StoriesAdapter storiesAdapter;
    private List<FriendStory> storyList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView textViewStoriesCount;
    private ProgressBar progressBar;
    private Button retryButton;
    private OkHttpClient httpClient;
    private String accessToken;
    private boolean isLoading = false;

    private Set<Long> specialUsers = new HashSet<>();
    private boolean isSpecialUsersLoaded = false;

    private TokenManager tokenManager;

    public static FriendsStoriesFragment newInstance() {
        return new FriendsStoriesFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        Context context = getSafeContext();
        if (context != null) {
            tokenManager = TokenManager.getInstance(context);
            accessToken = tokenManager.getToken();

            // Проверяем валидность токена при создании фрагмента
            if (!tokenManager.isTokenValid()) {
                handleTokenExpired();
            }
        }

        loadSpecialUsers();
    }

    // Обработка истечения срока действия токена
    private void handleTokenExpired() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (!isAdded()) return;

                new AlertDialog.Builder(requireContext())
                        .setTitle("Сессия истекла")
                        .setMessage("Ваша сессия авторизации истекла. Необходимо войти заново.")
                        .setPositiveButton("Войти", (dialog, which) -> {
                            // Переход на экран логина
                            TokenManager.logout(requireContext());
                            Intent intent = new Intent(requireContext(), AuthActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            requireActivity().finish();
                        })
                        .setCancelable(false)
                        .show();
            });
        }
    }

    // Вспомогательный метод для безопасного получения контекста
    @Nullable
    private Context getSafeContext() {
        if (isAdded()) {
            return getContext();
        }
        return null;
    }

    private void loadSpecialUsers() {
        Request request = new Request.Builder()
                .url("https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/special_users.json")
                .build();

        httpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("FriendsStoriesFragment", "Failed to load special users", e);
                isSpecialUsersLoaded = true;
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) {
                    isSpecialUsersLoaded = true;
                    return;
                }

                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        String json = body.string();
                        parseSpecialUsers(json);
                    }
                } catch (Exception e) {
                    Log.e("FriendsStoriesFragment", "Error parsing special users", e);
                } finally {
                    isSpecialUsersLoaded = true;
                }
            }
        });
    }

    private void parseSpecialUsers(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray usersArray = jsonObject.getJSONArray("special_users");

            specialUsers.clear();
            for (int i = 0; i < usersArray.length(); i++) {
                long userId = usersArray.getLong(i);
                specialUsers.add(userId);
            }

            Log.d("FriendsStoriesFragment", "Loaded " + specialUsers.size() + " special users");
        } catch (JSONException e) {
            Log.e("FriendsStoriesFragment", "Error parsing special users JSON", e);
        }
    }

    private boolean isSpecialUser(long userId) {
        return specialUsers.contains(userId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stories_tab, container, false);

        recyclerViewStories = view.findViewById(R.id.recyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        textViewStoriesCount = view.findViewById(R.id.count);
        progressBar = view.findViewById(R.id.progressBar);
        retryButton = view.findViewById(R.id.retryButton);

        setupUI();
        setupSwipeRefresh();
        setupRetryButton();

        // Проверяем токен перед загрузкой данных
        if (tokenManager != null && !tokenManager.isTokenValid()) {
            handleTokenExpired();
        } else {
            fetchFriendsStories();
        }

        return view;
    }

    private void setupUI() {
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
        recyclerViewStories.setLayoutManager(layoutManager);
        storiesAdapter = new StoriesAdapter(storyList);
        recyclerViewStories.setAdapter(storiesAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Проверяем токен перед обновлением
            if (tokenManager != null && !tokenManager.isTokenValid()) {
                handleTokenExpired();
                swipeRefreshLayout.setRefreshing(false);
            } else {
                fetchFriendsStories();
            }
        });
    }

    private void setupRetryButton() {
        retryButton.setOnClickListener(v -> {
            // Проверяем токен перед повторной попыткой
            if (tokenManager != null && !tokenManager.isTokenValid()) {
                handleTokenExpired();
            } else {
                fetchFriendsStories();
            }
        });
    }

    private void fetchFriendsStories() {
        if (!isAdded()) {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        if (isLoading) return;

        // Дополнительная проверка валидности токена
        if (tokenManager != null && !tokenManager.isTokenValid()) {
            handleTokenExpired();
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        textViewStoriesCount.setText("Загрузка...");
        swipeRefreshLayout.setRefreshing(true);

        if (accessToken == null || accessToken.isEmpty()) {
            showError("Ошибка авторизации");
            swipeRefreshLayout.setRefreshing(false);
            textViewStoriesCount.setText("Ошибка");
            return;
        }

        isLoading = true;

        // Получаем истории друзей и групп
        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/stories.get")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("extended", "1")
                .addQueryParameter("fields", "first_name,last_name,photo_100,name,screen_name")
                .addQueryParameter("v", "5.199")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "VKAndroidApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isAdded()) return;

                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (!isAdded()) return;
                        isLoading = false;
                        showError("Ошибка сети: " + e.getMessage());
                        swipeRefreshLayout.setRefreshing(false);
                        textViewStoriesCount.setText("Ошибка сети");
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        handleNetworkError(response.code());
                        return;
                    }

                    String json = body.string();
                    JSONObject jsonResponse = new JSONObject(json);

                    if (jsonResponse.has("error")) {
                        JSONObject errorObj = jsonResponse.getJSONObject("error");
                        int errorCode = errorObj.optInt("error_code", 0);

                        // Проверяем, является ли ошибка связанной с авторизацией
                        if (errorCode == 5 || errorCode == 401) { // Ошибка авторизации
                            handleTokenExpired();
                        } else {
                            handleApiError(errorObj);
                        }
                        return;
                    }

                    List<FriendStory> newStoryList = parseStoriesResponse(jsonResponse);

                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            if (!isAdded()) return;
                            isLoading = false;
                            updateStoriesList(newStoryList);
                            swipeRefreshLayout.setRefreshing(false);
                        });
                    }
                } catch (Exception e) {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            if (!isAdded()) return;
                            isLoading = false;
                            showError("Ошибка обработки данных");
                            swipeRefreshLayout.setRefreshing(false);
                            textViewStoriesCount.setText("Ошибка данных");
                        });
                    }
                }
            }
        });
    }

    private void handleNetworkError(int errorCode) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (!isAdded()) return;
                isLoading = false;
                showError("Ошибка сервера: " + errorCode);
                swipeRefreshLayout.setRefreshing(false);
                textViewStoriesCount.setText("Ошибка сервера");
            });
        }
    }

    private List<FriendStory> parseStoriesResponse(JSONObject responseObj) throws JSONException {
        List<FriendStory> stories = new ArrayList<>();

        // Парсим истории
        JSONArray items = responseObj.optJSONArray("items");
        JSONArray profiles = responseObj.optJSONArray("profiles");
        JSONArray groups = responseObj.optJSONArray("groups");

        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject storyObj = items.getJSONObject(i);
                FriendStory story = parseFriendStory(storyObj, profiles, groups);
                if (story != null && !story.getStories().isEmpty()) {
                    stories.add(story);
                }
            }
        }

        return stories;
    }

    private FriendStory parseFriendStory(JSONObject storyObj, JSONArray profiles, JSONArray groups) throws JSONException {
        long ownerId = storyObj.getLong("owner_id");
        JSONArray storyItems = storyObj.optJSONArray("items");

        if (storyItems == null || storyItems.length() == 0) {
            return null;
        }

        // Получаем информацию о владельце
        String ownerName = "Неизвестно";
        String ownerPhoto = null;
        boolean isGroup = ownerId < 0;

        if (isGroup) {
            // Ищем группу
            long groupId = Math.abs(ownerId);
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);
                if (group.getLong("id") == groupId) {
                    ownerName = group.getString("name");
                    ownerPhoto = group.optString("photo_100", null);
                    break;
                }
            }
        } else {
            // Ищем пользователя
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject profile = profiles.getJSONObject(i);
                if (profile.getLong("id") == ownerId) {
                    String firstName = profile.getString("first_name");
                    String lastName = profile.getString("last_name");
                    ownerName = firstName + " " + lastName;
                    ownerPhoto = profile.optString("photo_100", null);
                    break;
                }
            }
        }

        // Парсим отдельные истории
        List<StoryItem> stories = new ArrayList<>();
        for (int i = 0; i < storyItems.length(); i++) {
            JSONObject item = storyItems.getJSONObject(i);
            StoryItem storyItem = parseStoryItem(item);
            if (storyItem != null) {
                stories.add(storyItem);
            }
        }

        if (stories.isEmpty()) {
            return null;
        }

        return new FriendStory(ownerId, ownerName, ownerPhoto, isGroup, stories);
    }

    private StoryItem parseStoryItem(JSONObject storyObj) throws JSONException {
        String id = storyObj.optString("id");
        String text = storyObj.optString("text", "");
        long date = storyObj.optLong("date", 0);

        int viewsCount = 0;
        if (storyObj.has("views")) {
            JSONObject viewsObj = storyObj.optJSONObject("views");
            if (viewsObj != null) {
                viewsCount = viewsObj.optInt("count", 0);
            }
        }

        boolean isExpired = storyObj.optBoolean("is_expired", false);

        // Получаем URL изображения или видео
        String mediaUrl = null;
        if (storyObj.has("photo")) {
            JSONObject photo = storyObj.getJSONObject("photo");
            mediaUrl = getBestPhotoUrl(photo);
        } else if (storyObj.has("video")) {
            JSONObject video = storyObj.getJSONObject("video");
            mediaUrl = getVideoImageUrl(video);
        }

        if (mediaUrl == null) {
            return null;
        }

        return new StoryItem(id, mediaUrl, text, date, viewsCount, isExpired);
    }

    private String getBestPhotoUrl(JSONObject photoObj) throws JSONException {
        String[] sizes = {"photo_2560", "photo_1280", "photo_807", "photo_604", "photo_320", "x", "y", "z", "m", "s"};
        for (String size : sizes) {
            if (photoObj.has(size)) {
                String url = photoObj.optString(size);
                if (url != null && !url.isEmpty() && !url.equals("null")) {
                    return url;
                }
            }
        }

        if (photoObj.has("sizes")) {
            JSONArray sizesArray = photoObj.getJSONArray("sizes");
            for (int i = sizesArray.length() - 1; i >= 0; i--) {
                JSONObject sizeObj = sizesArray.getJSONObject(i);
                String type = sizeObj.getString("type");
                if (type.equals("x") || type.equals("y") || type.equals("z")) {
                    String url = sizeObj.getString("url");
                    if (url != null && !url.isEmpty() && !url.equals("null")) {
                        return url;
                    }
                }
            }
        }
        return null;
    }

    private String getVideoImageUrl(JSONObject videoObj) throws JSONException {
        if (videoObj.has("image")) {
            JSONArray images = videoObj.getJSONArray("image");
            for (int i = images.length() - 1; i >= 0; i--) {
                JSONObject image = images.getJSONObject(i);
                String url = image.optString("url");
                if (url != null && !url.isEmpty() && !url.equals("null")) {
                    return url;
                }
            }
        }
        return null;
    }

    private void handleApiError(JSONObject errorObj) {
        String errorMsg = errorObj.optString("error_msg", "Неизвестная ошибка API");
        int errorCode = errorObj.optInt("error_code", 0);

        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (!isAdded()) return;
                isLoading = false;

                // Если ошибка авторизации, обрабатываем отдельно
                if (errorCode == 5 || errorCode == 401) {
                    handleTokenExpired();
                } else {
                    showError(errorMsg);
                    swipeRefreshLayout.setRefreshing(false);
                    textViewStoriesCount.setText("Ошибка API");
                }
            });
        }
    }

    private void updateStoriesList(List<FriendStory> stories) {
        storyList.clear();
        storyList.addAll(stories);
        updateStoriesCount(stories.size());

        if (stories.isEmpty()) {
            showError("Нет новых историй");
        } else {
            hideError();
            storiesAdapter.notifyDataSetChanged();
        }
    }

    private void updateStoriesCount(int count) {
        String countText;
        if (count == 0) {
            countText = "Нет историй";
        } else {
            countText = "Истории: " + count;
        }
        textViewStoriesCount.setText(countText);
    }

    private void showError(String message) {
        retryButton.setVisibility(View.VISIBLE);
        recyclerViewStories.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void hideError() {
        retryButton.setVisibility(View.GONE);
        recyclerViewStories.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    // Классы данных
    public static class FriendStory implements Serializable {
        private long ownerId;
        private String ownerName;
        private String ownerPhoto;
        private boolean isGroup;
        private List<StoryItem> stories;

        public FriendStory(long ownerId, String ownerName, String ownerPhoto, boolean isGroup, List<StoryItem> stories) {
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.ownerPhoto = ownerPhoto;
            this.isGroup = isGroup;
            this.stories = stories;
        }

        public long getOwnerId() { return ownerId; }
        public String getOwnerName() { return ownerName; }
        public String getOwnerPhoto() { return ownerPhoto; }
        public boolean isGroup() { return isGroup; }
        public List<StoryItem> getStories() { return stories; }

        // Получаем первую доступную историю для превью
        public StoryItem getPreviewStory() {
            for (StoryItem story : stories) {
                if (!story.isExpired()) {
                    return story;
                }
            }
            return stories.size() > 0 ? stories.get(0) : null;
        }

        // Количество непросмотренных историй
        public int getUnviewedCount() {
            int count = 0;
            for (StoryItem story : stories) {
                if (!story.isExpired() && story.getViewsCount() == 0) {
                    count++;
                }
            }
            return count;
        }
    }

    // Адаптер для RecyclerView
    class StoriesAdapter extends RecyclerView.Adapter<StoriesAdapter.ViewHolder> {
        private List<FriendStory> stories;

        public StoriesAdapter(List<FriendStory> stories) {
            this.stories = stories;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friend_story, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FriendStory friendStory = stories.get(position);
            StoryItem previewStory = friendStory.getPreviewStory();

            if (previewStory != null) {
                // Загружаем превью истории
                Glide.with(requireContext())
                        .load(previewStory.getMediaUrl())
                        .placeholder(R.drawable.img)
                        .error(R.drawable.img)
                        .centerCrop()
                        .into(holder.storyImage);
            }

            // Загружаем аватар владельца
            if (friendStory.getOwnerPhoto() != null) {
                Glide.with(requireContext())
                        .load(friendStory.getOwnerPhoto())
                        .placeholder(R.drawable.img)
                        .error(R.drawable.img)
                        .circleCrop()
                        .into(holder.avatarImage);
            }

            // Устанавливаем имя владельца
            holder.nameText.setText(friendStory.getOwnerName());

            // Показываем бейдж группы если это паблик
            if (friendStory.isGroup()) {
                holder.groupBadge.setVisibility(View.VISIBLE);
            } else {
                holder.groupBadge.setVisibility(View.GONE);
            }

            // Показываем количество непросмотренных историй
            int unviewedCount = friendStory.getUnviewedCount();
            if (unviewedCount > 0) {
                holder.unviewedBadge.setVisibility(View.VISIBLE);
                holder.unviewedText.setText(String.valueOf(unviewedCount));
            } else {
                holder.unviewedBadge.setVisibility(View.GONE);
            }

            // Проверяем специального пользователя
            if (isSpecialUser(friendStory.getOwnerId())) {
                holder.verifiedIcon.setVisibility(View.VISIBLE);
            } else {
                holder.verifiedIcon.setVisibility(View.GONE);
            }

            // Обработчик клика
            holder.itemView.setOnClickListener(v -> {
                openFriendStories(position);
            });
        }

        @Override
        public int getItemCount() {
            return stories.size();
        }

        private void openFriendStories(int position) {
            FriendStory friendStory = stories.get(position);

            // Собираем только не истекшие истории
            List<StoryItem> availableStories = new ArrayList<>();
            for (StoryItem story : friendStory.getStories()) {
                if (!story.isExpired()) {
                    availableStories.add(story);
                }
            }

            if (availableStories.isEmpty()) {
                Toast.makeText(requireContext(), "Нет доступных историй", Toast.LENGTH_SHORT).show();
                return;
            }

            // Открываем просмотрщик историй
            StoryViewerFragment storyViewerFragment = StoryViewerFragment.newInstance(
                    new ArrayList<>(availableStories), 0);

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, storyViewerFragment)
                    .addToBackStack("friend_stories_viewer")
                    .commit();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView storyImage, avatarImage, verifiedIcon;
            TextView nameText, unviewedText;
            View groupBadge, unviewedBadge;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                storyImage = itemView.findViewById(R.id.story_image);
                avatarImage = itemView.findViewById(R.id.avatar_image);
                nameText = itemView.findViewById(R.id.name_text);
                unviewedText = itemView.findViewById(R.id.unviewed_text);
                groupBadge = itemView.findViewById(R.id.group_badge);
                unviewedBadge = itemView.findViewById(R.id.unviewed_badge);
                verifiedIcon = itemView.findViewById(R.id.verified_icon);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Отменяем все сетевые запросы при уничтожении фрагмента
        if (httpClient != null) {
            httpClient.dispatcher().cancelAll();
        }
    }
}