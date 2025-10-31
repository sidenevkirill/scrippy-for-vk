package ru.lisdevs.messenger.friends;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

public class FriendsPhotosFragment extends Fragment {

    private RecyclerView recyclerViewFriends;
    private FriendsAdapter adapter;
    private List<VKFriend> friendsList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView statusTextView;
    private TextView friendsCountTextView;
    private OkHttpClient httpClient;
    private String accessToken;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        accessToken = TokenManager.getInstance(requireContext()).getToken();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_audios, container, false);

        initViews(view);
        setupRecyclerView();
        fetchVKFriends();

        return view;
    }

    private void initViews(View view) {
        recyclerViewFriends = view.findViewById(R.id.recyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        statusTextView = view.findViewById(R.id.emptyView);
        friendsCountTextView = view.findViewById(R.id.count);

        swipeRefreshLayout.setOnRefreshListener(this::fetchVKFriends);
    }

    private void setupRecyclerView() {
        adapter = new FriendsAdapter(friendsList, friend -> {
            openFriendProfile(friend);
        });
        recyclerViewFriends.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewFriends.setAdapter(adapter);
    }

    private void fetchVKFriends() {
        // Проверяем токен перед запросом
        if (!checkTokenValidity()) {
            return;
        }

        statusTextView.setText("Загрузка друзей...");
        friendsCountTextView.setText("Загрузка...");
        swipeRefreshLayout.setRefreshing(true);

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            showError("Требуется авторизация");
            swipeRefreshLayout.setRefreshing(false);
            friendsCountTextView.setText("Ошибка авторизации");
            return;
        }

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/friends.get")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("fields", "first_name,last_name,photo_200,photo_max_orig,online")
                .addQueryParameter("v", "5.199")
                .addQueryParameter("count", "1000")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "VKAndroidApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    showError("Ошибка сети: " + e.getMessage());
                    swipeRefreshLayout.setRefreshing(false);
                    friendsCountTextView.setText("Ошибка сети");
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        requireActivity().runOnUiThread(() -> {
                            showError("Ошибка сервера: " + response.code());
                            swipeRefreshLayout.setRefreshing(false);
                            friendsCountTextView.setText("Ошибка сервера");
                        });
                        return;
                    }

                    String json = body.string();
                    JSONObject jsonResponse = new JSONObject(json);

                    if (jsonResponse.has("error")) {
                        handleApiError(jsonResponse.getJSONObject("error"));
                        return;
                    }

                    List<VKFriend> friends = parseFriendsResponse(jsonResponse);
                    requireActivity().runOnUiThread(() -> {
                        updateFriendsList(friends);
                        swipeRefreshLayout.setRefreshing(false);
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        showError("Ошибка обработки данных");
                        swipeRefreshLayout.setRefreshing(false);
                        friendsCountTextView.setText("Ошибка данных");
                    });
                }
            }
        });
    }

    private List<VKFriend> parseFriendsResponse(JSONObject jsonResponse) throws JSONException {
        List<VKFriend> friends = new ArrayList<>();
        JSONArray items = jsonResponse.getJSONObject("response").getJSONArray("items");

        for (int i = 0; i < items.length(); i++) {
            JSONObject friendJson = items.getJSONObject(i);
            long id = friendJson.getLong("id");
            String firstName = friendJson.getString("first_name");
            String lastName = friendJson.getString("last_name");
            String photoUrl = friendJson.optString("photo_200", "");
            String maxPhotoUrl = friendJson.optString("photo_max_orig", "");
            boolean isOnline = friendJson.optInt("online", 0) == 1;

            // Используем максимальное качество фото если доступно
            String bestPhotoUrl = !maxPhotoUrl.isEmpty() ? maxPhotoUrl : photoUrl;

            friends.add(new VKFriend(id, firstName, lastName, bestPhotoUrl, isOnline));
        }
        return friends;
    }

    private void updateFriendsList(List<VKFriend> friends) {
        friendsList.clear();
        friendsList.addAll(friends);

        updateFriendsCount(friends.size());

        if (friends.isEmpty()) {
            statusTextView.setText("Друзья не найдены");
            statusTextView.setVisibility(View.VISIBLE);
        } else {
            statusTextView.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
        }
    }

    private void updateFriendsCount(int count) {
        String countText;
        if (count == 0) {
            countText = "Нет друзей";
        } else {
            countText = formatFriendsCount(count);
        }
        friendsCountTextView.setText(countText);
    }

    private String formatFriendsCount(int count) {
        if (count % 10 == 1 && count % 100 != 11) {
            return count + " друг";
        } else if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20)) {
            return count + " друга";
        } else {
            return count + " друзей";
        }
    }

    private void handleApiError(JSONObject errorObj) {
        String errorMsg = errorObj.optString("error_msg", "Неизвестная ошибка API");
        int errorCode = errorObj.optInt("error_code", 0);

        requireActivity().runOnUiThread(() -> {
            switch (errorCode) {
                case 5: // Authorization failed
                    TokenManager.getInstance(requireContext()).clearAuthData();
                    showReauthorizationDialog();
                    break;
                default:
                    showError(errorMsg);
                    break;
            }
            swipeRefreshLayout.setRefreshing(false);
            friendsCountTextView.setText("Ошибка API");
        });
    }

    private void showError(String message) {
        statusTextView.setText(message);
        statusTextView.setVisibility(View.VISIBLE);
    }

    private boolean checkTokenValidity() {
        TokenManager tokenManager = TokenManager.getInstance(requireContext());

        if (!tokenManager.isTokenValid()) {
            showReauthorizationDialog();
            return false;
        }

        return true;
    }

    private void showReauthorizationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Ошибка авторизации")
                .setMessage("Необходимо войти снова")
                .setPositiveButton("Войти", (dialog, which) -> {
                    restartAuthorization();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void restartAuthorization() {
        TokenManager.getInstance(requireContext()).clearAuthData();
        Intent intent = new Intent(requireContext(), AuthActivity.class);
        startActivity(intent);
        requireActivity().finish();
    }

    private void openFriendProfile(VKFriend friend) {
        UserProfileFragment profileFragment = new UserProfileFragment();
        Bundle args = new Bundle();
        args.putLong("friend_id", friend.id);
        args.putString("first_name", friend.firstName);
        args.putString("last_name", friend.lastName);
        args.putString("screen_name", ""); // Можно добавить если есть в данных
        profileFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.container, profileFragment)
                .addToBackStack("friend_profile")
                .commit();
    }

    static class VKFriend {
        long id;
        String firstName;
        String lastName;
        String photoUrl;
        boolean isOnline;

        VKFriend(long id, String firstName, String lastName, String photoUrl, boolean isOnline) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.photoUrl = photoUrl;
            this.isOnline = isOnline;
        }
    }

    static class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {
        private List<VKFriend> friends;
        private OnItemClickListener listener;
        private OkHttpClient countClient;
        private Random random = new Random();
        private Map<Long, Integer> photosCountCache = new HashMap<>();

        interface OnItemClickListener {
            void onItemClick(VKFriend friend);
        }

        FriendsAdapter(List<VKFriend> friends, OnItemClickListener listener) {
            this.friends = friends;
            this.listener = listener;
            this.countClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build();
        }

        public void updateFriendsList(List<VKFriend> newFriends) {
            this.friends = newFriends;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friend_photo, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VKFriend friend = friends.get(position);
            holder.bind(friend);
            holder.itemView.setOnClickListener(v -> listener.onItemClick(friend));
        }

        @Override
        public int getItemCount() {
            return friends.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView;
            TextView photosCountTextView;
            ImageView photoImageView;
            TextView photoTextView;
            View onlineIndicator;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.user);
                photosCountTextView = itemView.findViewById(R.id.photos_count);
                photoImageView = itemView.findViewById(R.id.photo_image);
                photoTextView = itemView.findViewById(R.id.photo_text);
                onlineIndicator = itemView.findViewById(R.id.online_indicator);
            }

            void bind(VKFriend friend) {
                nameTextView.setText(friend.firstName + " " + friend.lastName);

                // Загружаем фото профиля
                if (friend.photoUrl != null && !friend.photoUrl.isEmpty()) {
                    photoImageView.setVisibility(View.VISIBLE);
                    photoTextView.setVisibility(View.GONE);

                    Glide.with(itemView.getContext())
                            .load(friend.photoUrl)
                            .placeholder(R.drawable.default_avatar)
                            .error(R.drawable.default_avatar)
                            .circleCrop()
                            .into(photoImageView);
                } else {
                    // Показываем текстовый аватар если фото нет
                    photoImageView.setVisibility(View.GONE);
                    photoTextView.setVisibility(View.VISIBLE);
                    String firstLetter = friend.firstName != null && !friend.firstName.isEmpty() ?
                            friend.firstName.substring(0, 1).toUpperCase() : "?";
                    photoTextView.setText(firstLetter);

                    int color = getRandomColor();
                    GradientDrawable drawable = new GradientDrawable();
                    drawable.setShape(GradientDrawable.OVAL);
                    drawable.setColor(color);
                    photoTextView.setBackground(drawable);
                }

                // Показываем индикатор онлайн статуса
                if (onlineIndicator != null) {
                    onlineIndicator.setVisibility(friend.isOnline ? View.VISIBLE : View.GONE);
                }

                // Загружаем количество фотографий
                loadPhotosCount(friend);
            }

            private int getRandomColor() {
                int[] colors = {
                        Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
                        Color.parseColor("#45B7D1"), Color.parseColor("#F9A826"),
                        Color.parseColor("#6A5ACD"), Color.parseColor("#FFA07A"),
                        Color.parseColor("#20B2AA"), Color.parseColor("#9370DB"),
                        Color.parseColor("#3CB371"), Color.parseColor("#FF4500")
                };
                return colors[random.nextInt(colors.length)];
            }

            private void loadPhotosCount(VKFriend friend) {
                if (photosCountCache.containsKey(friend.id)) {
                    int count = photosCountCache.get(friend.id);
                    updateCountText(count);
                    return;
                }

                photosCountTextView.setText("Загрузка...");

                String accessToken = TokenManager.getInstance(itemView.getContext()).getToken();
                if (accessToken == null) {
                    photosCountTextView.setText("Фото: ?");
                    return;
                }

                HttpUrl url = HttpUrl.parse("https://api.vk.com/method/photos.getAll")
                        .newBuilder()
                        .addQueryParameter("access_token", accessToken)
                        .addQueryParameter("owner_id", String.valueOf(friend.id))
                        .addQueryParameter("count", "1") // Только для получения количества
                        .addQueryParameter("v", "5.199")
                        .build();

                countClient.newCall(new Request.Builder()
                                .url(url)
                                .build())
                        .enqueue(new Callback() {
                            @Override
                            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                updateCountText(-1);
                            }

                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                                try {
                                    String json = response.body().string();
                                    JSONObject jsonObject = new JSONObject(json);

                                    if (jsonObject.has("error")) {
                                        updateCountText(-1);
                                        return;
                                    }

                                    if (jsonObject.has("response")) {
                                        JSONObject responseObj = jsonObject.getJSONObject("response");
                                        int count = responseObj.optInt("count", 0);
                                        photosCountCache.put(friend.id, count);
                                        updateCountText(count);
                                    } else {
                                        updateCountText(-1);
                                    }
                                } catch (Exception e) {
                                    updateCountText(-1);
                                }
                            }
                        });
            }

            private void updateCountText(int count) {
                if (itemView.getContext() instanceof Activity) {
                    ((Activity) itemView.getContext()).runOnUiThread(() -> {
                        String text;
                        if (count == -1) {
                            text = "Фото: ?";
                        } else if (count == 0) {
                            text = "Нет фото";
                        } else {
                            text = formatPhotosCount(count);
                        }
                        photosCountTextView.setText(text);
                    });
                }
            }

            private String formatPhotosCount(int count) {
                if (count % 10 == 1 && count % 100 != 11) {
                    return count + " фото";
                } else if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20)) {
                    return count + " фото";
                } else {
                    return count + " фото";
                }
            }
        }
    }
}