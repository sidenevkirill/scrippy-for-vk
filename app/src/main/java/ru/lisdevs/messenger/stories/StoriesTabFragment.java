package ru.lisdevs.messenger.stories;

import android.app.Activity;
import android.os.Bundle;
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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.utils.TokenManager;

// Фрагмент для вкладки с историями
public class StoriesTabFragment extends Fragment {
    private RecyclerView recyclerViewStories;
    private StoriesAdapter storiesAdapter;
    private List<StoryItem> storyList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView textViewStoriesCount;
    private ProgressBar progressBar;
    //private TextView errorTextView;
    private Button retryButton;

    private long friendId;
    private String friendName;
    private OkHttpClient httpClient;
    private boolean isLoading = false;

    public static StoriesTabFragment newInstance(long friendId, String friendName) {
        StoriesTabFragment fragment = new StoriesTabFragment();
        Bundle args = new Bundle();
        args.putLong("friend_id", friendId);
        args.putString("friend_name", friendName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        Bundle args = getArguments();
        if (args != null) {
            friendId = args.getLong("friend_id");
            friendName = args.getString("friend_name");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stories_tab, container, false);

        recyclerViewStories = view.findViewById(R.id.recyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        textViewStoriesCount = view.findViewById(R.id.count);
        progressBar = view.findViewById(R.id.progressBar);
       // errorTextView = view.findViewById(R.id.emptyView);
        retryButton = view.findViewById(R.id.retryButton);

        setupUI();
        setupSwipeRefresh();
        setupRetryButton();

        if (isTestAccount()) {
            loadTestStories();
        } else {
            fetchUserStories();
        }

        return view;
    }

    private void setupUI() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerViewStories.setLayoutManager(layoutManager);
        storiesAdapter = new StoriesAdapter(storyList);
        recyclerViewStories.setAdapter(storiesAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (isTestAccount()) {
                loadTestStories();
            } else {
                fetchUserStories();
            }
        });
    }

    private void setupRetryButton() {
        retryButton.setOnClickListener(v -> {
            if (isTestAccount()) {
                loadTestStories();
            } else {
                fetchUserStories();
            }
        });
    }

    private boolean isTestAccount() {
        return friendId == 123456789L || friendId == 0 || friendName == null;
    }

    private void loadTestStories() {
        setLoadingState(true);
        List<StoryItem> testStories = new ArrayList<>();

        testStories.add(new StoryItem(
                "1",
                "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=400",
                "Мой день",
                System.currentTimeMillis() / 1000 - 3600, // 1 час назад
                150,
                false
        ));
        testStories.add(new StoryItem(
                "2",
                "https://images.unsplash.com/photo-1606787620819-8bdf0c44c293?w=400",
                "Отпуск",
                System.currentTimeMillis() / 1000 - 7200, // 2 часа назад
                89,
                true
        ));
        testStories.add(new StoryItem(
                "3",
                "https://images.unsplash.com/photo-1551963831-b3b1ca40c98e?w=400",
                "Работа",
                System.currentTimeMillis() / 1000 - 10800, // 3 часа назад
                45,
                false
        ));

        requireActivity().runOnUiThread(() -> {
            storyList.clear();
            storyList.addAll(testStories);
            storiesAdapter.notifyDataSetChanged();
            setLoadingState(false);
            updateStoriesCountText();
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void fetchUserStories() {
        if (isLoading) return;

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            showErrorState("Требуется авторизация");
            return;
        }

        setLoadingState(true);
        hideErrorState();

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/stories.get")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("owner_id", String.valueOf(friendId))
                .addQueryParameter("extended", "1")
                .addQueryParameter("v", "5.131")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "VKAndroidApp/1.0")
                .build();

        isLoading = true;

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    isLoading = false;
                    setLoadingState(false);
                    showErrorState("Ошибка сети: " + e.getMessage());
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
                            JSONArray items = responseObj.optJSONArray("items");
                            List<StoryItem> newStoryList = new ArrayList<>();

                            if (items != null) {
                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject storyObj = items.getJSONObject(i);
                                    StoryItem storyItem = parseStoryItem(storyObj);
                                    if (storyItem != null) {
                                        newStoryList.add(storyItem);
                                    }
                                }
                            }

                            requireActivity().runOnUiThread(() -> {
                                isLoading = false;
                                setLoadingState(false);
                                swipeRefreshLayout.setRefreshing(false);

                                storyList.clear();
                                storyList.addAll(newStoryList);
                                storiesAdapter.notifyDataSetChanged();
                                updateStoriesCountText();

                                if (storyList.isEmpty()) {
                                    showErrorState("Нет историй");
                                } else {
                                    showContentState();
                                }
                            });
                        } else if (jsonObject.has("error")) {
                            handleApiError(jsonObject.getJSONObject("error"));
                        }
                    } catch (JSONException e) {
                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            setLoadingState(false);
                            showErrorState("Ошибка обработки данных");
                        });
                    }
                } else {
                    requireActivity().runOnUiThread(() -> {
                        isLoading = false;
                        setLoadingState(false);
                        showErrorState("Ошибка сервера");
                    });
                }
            }
        });
    }

    private StoryItem parseStoryItem(JSONObject storyObj) throws JSONException {
        String id = storyObj.optString("id");
        String text = storyObj.optString("text", "");
        long date = storyObj.optLong("date", 0);
        int viewsCount = storyObj.optInt("views", 0);
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

        return new StoryItem(id, mediaUrl, text, date, viewsCount, isExpired);
    }

    private String getBestPhotoUrl(JSONObject photoObj) throws JSONException {
        String[] sizes = {"x", "y", "z", "m", "s"};
        for (String size : sizes) {
            if (photoObj.has("photo_" + size)) {
                return photoObj.getString("photo_" + size);
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
                if (url != null && !url.isEmpty()) {
                    return url;
                }
            }
        }
        return null;
    }

    private void handleApiError(JSONObject error) {
        int errorCode = error.optInt("error_code");
        String errorMsg = error.optString("error_msg");

        requireActivity().runOnUiThread(() -> {
            isLoading = false;
            setLoadingState(false);

            switch (errorCode) {
                case 5:
                    showErrorState("Ошибка авторизации");
                    break;
                case 6:
                    showErrorState("Слишком много запросов");
                    break;
                case 15:
                    showErrorState("Нет доступа к историям пользователя");
                    break;
                default:
                    showErrorState("Ошибка: " + errorMsg);
            }
        });
    }

    private void updateStoriesCountText() {
        Activity activity = getActivity();
        if (activity != null && textViewStoriesCount != null) {
            activity.runOnUiThread(() -> {
                String countText = isTestAccount() ?
                        "Тестовые истории: " + storyList.size() :
                        "Истории: " + storyList.size();
                textViewStoriesCount.setText(countText);
            });
        }
    }

    private void setLoadingState(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        recyclerViewStories.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (!loading) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void showErrorState(String message) {
      //  errorTextView.setText(message);
      //  errorTextView.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        recyclerViewStories.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    private void hideErrorState() {
        //errorTextView.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
    }

    private void showContentState() {
        recyclerViewStories.setVisibility(View.VISIBLE);
       // errorTextView.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    static class StoryItem {
        String id;
        String mediaUrl;
        String text;
        long date;
        int viewsCount;
        boolean isExpired;

        public StoryItem(String id, String mediaUrl, String text, long date, int viewsCount, boolean isExpired) {
            this.id = id;
            this.mediaUrl = mediaUrl;
            this.text = text;
            this.date = date;
            this.viewsCount = viewsCount;
            this.isExpired = isExpired;
        }
    }

    class StoriesAdapter extends RecyclerView.Adapter<StoriesAdapter.ViewHolder> {
        private List<StoryItem> stories;

        public StoriesAdapter(List<StoryItem> stories) {
            this.stories = stories;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_story, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StoryItem item = stories.get(position);

            holder.titleText.setText(item.text != null && !item.text.isEmpty() ? item.text : "История");

            if (item.mediaUrl != null && !item.mediaUrl.isEmpty()) {
                Glide.with(requireContext())
                        .load(item.mediaUrl)
                        .placeholder(R.drawable.img)
                        .error(R.drawable.img)
                        .centerCrop()
                        .into(holder.storyImage);
            } else {
                holder.storyImage.setImageResource(R.drawable.img);
            }

            if (item.viewsCount > 0) {
                holder.viewsText.setText(formatViews(item.viewsCount));
                holder.viewsText.setVisibility(View.VISIBLE);
            } else {
                holder.viewsText.setVisibility(View.GONE);
            }

            if (item.date > 0) {
                holder.timeText.setText(formatTime(item.date));
                holder.timeText.setVisibility(View.VISIBLE);
            } else {
                holder.timeText.setVisibility(View.GONE);
            }

            if (item.isExpired) {
                holder.expiredBadge.setVisibility(View.VISIBLE);
                holder.storyImage.setAlpha(0.7f);
            } else {
                holder.expiredBadge.setVisibility(View.GONE);
                holder.storyImage.setAlpha(1.0f);
            }

            holder.itemView.setOnClickListener(v -> {
                if (!item.isExpired) {
                    openStoryViewer(position);
                } else {
                    Toast.makeText(requireContext(), "История истекла", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return stories.size();
        }

        private String formatViews(int views) {
            if (views >= 1000000) {
                return String.format(Locale.getDefault(), "%.1fM", views / 1000000.0);
            } else if (views >= 1000) {
                return String.format(Locale.getDefault(), "%.1fK", views / 1000.0);
            } else {
                return String.valueOf(views);
            }
        }

        private String formatTime(long timestamp) {
            long currentTime = System.currentTimeMillis() / 1000;
            long diff = currentTime - timestamp;

            if (diff < 60) {
                return "только что";
            } else if (diff < 3600) {
                return (diff / 60) + " мин. назад";
            } else if (diff < 86400) {
                return (diff / 3600) + " ч. назад";
            } else {
                return (diff / 86400) + " дн. назад";
            }
        }

        private void openStoryViewer(int position) {
            // Здесь можно реализовать открытие полноэкранного просмотрщика историй
            Toast.makeText(requireContext(), "Открытие истории " + (position + 1), Toast.LENGTH_SHORT).show();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView storyImage;
            TextView titleText, viewsText, timeText;
            View expiredBadge;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                storyImage = itemView.findViewById(R.id.story_image);
                titleText = itemView.findViewById(R.id.title_text);
                viewsText = itemView.findViewById(R.id.views_text);
                timeText = itemView.findViewById(R.id.time_text);
                expiredBadge = itemView.findViewById(R.id.expired_badge);
            }
        }
    }
}