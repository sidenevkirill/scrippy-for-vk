package ru.lisdevs.messenger.favorites;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.dialog.DialogActivity;
import ru.lisdevs.messenger.utils.CircleTransform;
import ru.lisdevs.messenger.utils.TokenManager;


public class FavoritesFragment extends Fragment {

    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbar;
    private MaterialToolbar toolbar;
    private RecyclerView recyclerViewFavorites;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FriendsAdapter friendsAdapter;
    private List<VKFriend> favoritesList;

    // Header views
    private ImageView cover;
    private ImageView userAvatar;
    private TextView userName;
    private TextView userStatus;
    private TextView favoritesCount;

    // Action buttons
    private MaterialButton downloadButton;
    private MaterialButton shareButton;
    private MaterialButton sortButton;
    private MaterialButton menuButton;

    private FrameLayout progressBarContainer;
    private TextView emptyStateText;

    private TokenManager tokenManager;
    private RequestQueue requestQueue;

    private static final int STORAGE_PERMISSION_CODE = 1;

    public FavoritesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_documents, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        toolbar = view.findViewById(R.id.toolbar);
        tokenManager = TokenManager.getInstance(requireContext());
        requestQueue = Volley.newRequestQueue(requireContext());

        initViews(view);
        setupToolbar();
        setupRecyclerView();
        setupSwipeRefresh();
        loadUserInfo();
        loadFavoritesWithUsersGet(); // Используем новый метод
        setupClickListeners();
    }

    // Метод для определения User-Agent
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

    // Метод для определения типа авторизации
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

    private void initViews(View view) {
        appBarLayout = view.findViewById(R.id.appBarLayout);
        collapsingToolbar = view.findViewById(R.id.collapsingToolbar);
        recyclerViewFavorites = view.findViewById(R.id.recyclerViewDocuments);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        progressBarContainer = view.findViewById(R.id.progressBarContainer);
        emptyStateText = view.findViewById(R.id.emptyStateText);

        // Header views
        cover = view.findViewById(R.id.cover);
        userAvatar = view.findViewById(R.id.user_avatars);
        userName = view.findViewById(R.id.user_name);
        userStatus = view.findViewById(R.id.user_status);
        favoritesCount = view.findViewById(R.id.documents_count);

        // Action buttons
        downloadButton = view.findViewById(R.id.download_button);
        shareButton = view.findViewById(R.id.share_button);
        sortButton = view.findViewById(R.id.sort_button);
        menuButton = view.findViewById(R.id.menu_button);
    }

    private void setupToolbar() {
        toolbar.setTitle("Люди в закладках");
        if (!isAdded() || getActivity() == null) return;

        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_documents, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_search) {
            showSearchDialog();
            return true;
        } else if (id == R.id.action_refresh) {
            loadFavoritesWithUsersGet(); // Обновляем через новый метод
            return true;
        } else if (id == R.id.action_sort_name) {
            sortFavoritesByName();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        favoritesList = new ArrayList<>();

        // Используем ваш FriendsAdapter
        friendsAdapter = new FriendsAdapter(
                favoritesList, // обычные друзья
                new ArrayList<>(), // важные друзья - пустой список
                new FriendsAdapter.OnItemClickListener() {
                    @Override
                    public void onItemClick(VKFriend friend) {
                        openUserProfile(friend);
                    }
                },
                new FriendsAdapter.SpecialUserChecker() {
                    @Override
                    public boolean isSpecialUser(long userId) {
                        // Здесь можно добавить логику проверки верификации пользователя
                        // Например, проверка по списку верифицированных пользователей
                        return false;
                    }
                }
        );

        recyclerViewFavorites.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewFavorites.setAdapter(friendsAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadFavoritesWithUsersGet(); // Обновляем через новый метод
        });
    }

    private void setupClickListeners() {
        // Скрываем кнопки, которые не нужны для списка пользователей
        downloadButton.setVisibility(View.GONE);
        shareButton.setVisibility(View.GONE);

        sortButton.setOnClickListener(v -> {
            showSortDialog();
        });

        menuButton.setOnClickListener(v -> {
            showMoreOptionsMenu();
        });
    }

    private void loadUserInfo() {
        String fullName = tokenManager.getFullName();
        String photoUrl = tokenManager.getPhotoUrl();

        if (userName != null) {
            userName.setText(fullName != null ? fullName : "Люди в закладках");
        }
    }

    private void loadFavoritesWithUsersGet() {
        String accessToken = tokenManager.getToken();

        if (accessToken == null) {
            Toast.makeText(requireContext(), "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            showEmptyState(true);
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        showLoading(true);

        // Сначала получаем общий список закладок
        Uri.Builder uriBuilder = Uri.parse("https://api.vk.com/method/fave.get")
                .buildUpon()
                .appendQueryParameter("access_token", accessToken)
                .appendQueryParameter("v", "5.199");

        String finalUrl = uriBuilder.build().toString();

        Log.d("FavoritesFragment", "Request URL: " + finalUrl);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, finalUrl, null,
                response -> {
                    try {
                        Log.d("FavoritesFragment", "Fave.get response: " + response.toString());
                        if (response.has("error")) {
                            handleVKError(response.getJSONObject("error"));
                            return;
                        }

                        JSONObject data = response.getJSONObject("response");
                        JSONArray items = data.getJSONArray("items");

                        // Извлекаем ID пользователей из закладок
                        Set<Long> userIds = extractUserIdsFromFavorites(items);

                        if (userIds.isEmpty()) {
                            handleError("В закладках нет пользователей");
                            showEmptyState(true);
                            swipeRefreshLayout.setRefreshing(false);
                            showLoading(false);
                            return;
                        }

                        // Загружаем информацию о пользователях
                        loadUsersInfo(new ArrayList<>(userIds));

                    } catch (JSONException e) {
                        Log.e("FavoritesFragment", "JSON parsing error", e);
                        handleError("Ошибка обработки данных");
                        swipeRefreshLayout.setRefreshing(false);
                        showLoading(false);
                    }
                },
                error -> {
                    Log.e("FavoritesFragment", "Network error", error);
                    handleError("Ошибка сети: " + error.getMessage());
                    swipeRefreshLayout.setRefreshing(false);
                    showLoading(false);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", getUserAgent());
                return headers;
            }
        };

        requestQueue.add(request);
    }

    private Set<Long> extractUserIdsFromFavorites(JSONArray items) throws JSONException {
        Set<Long> userIds = new HashSet<>();

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String type = item.optString("type", "");

            Log.d("FavoritesFragment", "Processing item type: " + type);

            if ("page".equals(type)) {
                JSONObject page = item.getJSONObject("page");
                // Проверяем, что это пользователь (есть first_name и last_name)
                if (page.has("first_name") && page.has("last_name")) {
                    long userId = page.optLong("id", 0);
                    if (userId > 0) {
                        userIds.add(userId);
                        Log.d("FavoritesFragment", "Found user ID: " + userId);
                    }
                }
            }
        }

        Log.d("FavoritesFragment", "Extracted user IDs: " + userIds);
        return userIds;
    }

    private void loadUsersInfo(List<Long> userIds) {
        if (userIds.isEmpty()) {
            showEmptyState(true);
            swipeRefreshLayout.setRefreshing(false);
            showLoading(false);
            return;
        }

        // Формируем строку с ID пользователей
        StringBuilder userIdsStr = new StringBuilder();
        for (Long id : userIds) {
            if (userIdsStr.length() > 0) userIdsStr.append(",");
            userIdsStr.append(id);
        }

        Log.d("FavoritesFragment", "Loading users info for IDs: " + userIdsStr);

        // Загружаем информацию о пользователях
        Uri.Builder uriBuilder = Uri.parse("https://api.vk.com/method/users.get")
                .buildUpon()
                .appendQueryParameter("access_token", tokenManager.getToken())
                .appendQueryParameter("user_ids", userIdsStr.toString())
                .appendQueryParameter("fields", "photo_100,photo_200,online,last_seen")
                .appendQueryParameter("v", "5.199");

        String finalUrl = uriBuilder.build().toString();

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, finalUrl, null,
                response -> {
                    try {
                        Log.d("FavoritesFragment", "Users.get response: " + response.toString());
                        parseUsersResponse(response);
                    } catch (JSONException e) {
                        Log.e("FavoritesFragment", "JSON parsing error", e);
                        handleError("Ошибка обработки данных пользователей");
                        swipeRefreshLayout.setRefreshing(false);
                        showLoading(false);
                    }
                },
                error -> {
                    Log.e("FavoritesFragment", "Network error in users.get", error);
                    handleError("Ошибка загрузки информации о пользователях");
                    swipeRefreshLayout.setRefreshing(false);
                    showLoading(false);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", getUserAgent());
                return headers;
            }
        };

        requestQueue.add(request);
    }

    private void parseUsersResponse(JSONObject response) throws JSONException {
        if (response.has("error")) {
            handleVKError(response.getJSONObject("error"));
            swipeRefreshLayout.setRefreshing(false);
            showLoading(false);
            return;
        }

        JSONArray users = response.getJSONArray("response");
        favoritesList.clear();

        Log.d("FavoritesFragment", "Parsing " + users.length() + " users");

        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.getJSONObject(i);
            VKFriend friend = new VKFriend();
            friend.id = user.optInt("id", 0);
            friend.firstName = user.optString("first_name", "");
            friend.lastName = user.optString("last_name", "");
            friend.photoUrl = user.optString("photo_100", "");
            friend.isOnline = user.optInt("online", 0) == 1;
            friend.lastSeen = user.optLong("last_seen", 0);

            favoritesList.add(friend);

            Log.d("FavoritesFragment", "Added user: " + friend.firstName + " " + friend.lastName);
        }

        friendsAdapter.updateFriendsList(favoritesList, new ArrayList<>());
        updateHeaderInfo();

        if (favoritesList.isEmpty()) {
            showEmptyState(true);
        } else {
            showEmptyState(false);
        }

        swipeRefreshLayout.setRefreshing(false);
        showLoading(false);
    }

    private void handleVKError(JSONObject error) throws JSONException {
        String errorMsg = error.getString("error_msg");
        int errorCode = error.optInt("error_code", 0);

        Log.e("FavoritesFragment", "VK API Error: " + errorCode + " - " + errorMsg);

        switch (errorCode) {
            case 5:
                handleError("Ошибка авторизации. Войдите заново");
                break;
            case 6:
                handleError("Слишком много запросов. Попробуйте позже");
                break;
            case 10:
                handleError("Временная ошибка сервера VK. Попробуйте через несколько минут");
                break;
            case 15:
                handleError("Доступ к закладкам запрещен");
                break;
            case 100:
                handleError("Ошибка параметров VK API");
                break;
            default:
                handleError("Ошибка VK: " + errorMsg);
        }
    }

    private void openUserProfile(VKFriend friend) {
        // Открываем профиль пользователя
        try {
            String profileUrl = "https://vk.com/id" + friend.id;
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl));
            startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Не удалось открыть профиль", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateHeaderInfo() {
        if (favoritesCount != null) {
            String countText = favoritesList.size() + " пользователей";
            favoritesCount.setText(countText);
        }

        if (userStatus != null) {
            userStatus.setVisibility(View.VISIBLE);
            userStatus.setText("Люди в закладках");
        }
    }

    // Методы для сортировки
    private void sortFavoritesByName() {
        Collections.sort(favoritesList, (f1, f2) -> {
            String name1 = f1.firstName + " " + f1.lastName;
            String name2 = f2.firstName + " " + f2.lastName;
            return name1.compareToIgnoreCase(name2);
        });
        friendsAdapter.updateFriendsList(favoritesList, new ArrayList<>());
        Toast.makeText(requireContext(), "Сортировка по имени", Toast.LENGTH_SHORT).show();
    }

    private void showSortDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Сортировка пользователей")
                .setItems(new String[]{"По имени"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            sortFavoritesByName();
                            break;
                    }
                })
                .show();
    }

    private void showMoreOptionsMenu() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Дополнительные опции")
                .setItems(new String[]{"Очистить кэш", "О приложении"}, (dialog, which) -> {
                    switch (which) {
                        case 0: clearCache(); break;
                        case 1: showAbout(); break;
                    }
                })
                .show();
    }

    private void clearCache() {
        Toast.makeText(requireContext(), "Очистка кэша", Toast.LENGTH_SHORT).show();
    }

    private void showAbout() {
        Toast.makeText(requireContext(), "О приложении", Toast.LENGTH_SHORT).show();
    }

    private void showSearchDialog() {
        // Поиск по пользователям в закладках
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Поиск пользователей")
                .setMessage("Функция поиска будет реализована в будущем")
                .setPositiveButton("OK", null)
                .show();
    }

    private void handleError(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        showEmptyState(true);
    }

    private void showLoading(boolean show) {
        if (progressBarContainer != null) {
            progressBarContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyStateText != null) {
            emptyStateText.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                emptyStateText.setText("Пользователи в закладках не найдены");
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (requestQueue != null) {
            requestQueue.cancelAll(this);
        }
    }

    // Класс VKFriend для представления пользователя
    public static class VKFriend {
        public long id;
        public String firstName;
        public String lastName;
        public String photoUrl;
        public boolean isOnline;
        public long lastSeen;
        public int audioCount;
    }

    // Внутренний класс FriendsAdapter (ваш адаптер)
    static class FriendsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_IMPORTANT_HEADER = 0;
        private static final int TYPE_IMPORTANT_FRIEND = 1;
        private static final int TYPE_REGULAR_HEADER = 2;
        private static final int TYPE_REGULAR_FRIEND = 3;

        private List<VKFriend> regularFriends;
        private List<VKFriend> importantFriends;
        private OnItemClickListener listener;
        private SpecialUserChecker specialUserChecker;

        interface OnItemClickListener {
            void onItemClick(VKFriend friend);
        }

        interface SpecialUserChecker {
            boolean isSpecialUser(long userId);
        }

        FriendsAdapter(List<VKFriend> regularFriends, List<VKFriend> importantFriends,
                       OnItemClickListener listener, SpecialUserChecker specialUserChecker) {
            this.regularFriends = regularFriends;
            this.importantFriends = importantFriends;
            this.listener = listener;
            this.specialUserChecker = specialUserChecker;
        }

        public void updateFriendsList(List<VKFriend> regularFriends, List<VKFriend> importantFriends) {
            this.regularFriends = regularFriends;
            this.importantFriends = importantFriends;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            if (!importantFriends.isEmpty()) {
                if (position == 0) {
                    return TYPE_IMPORTANT_HEADER;
                } else if (position <= importantFriends.size()) {
                    return TYPE_IMPORTANT_FRIEND;
                } else if (position == importantFriends.size() + 1) {
                    return TYPE_REGULAR_HEADER;
                } else {
                    return TYPE_REGULAR_FRIEND;
                }
            } else {
                if (position == 0) {
                    return TYPE_REGULAR_HEADER;
                } else {
                    return TYPE_REGULAR_FRIEND;
                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            switch (viewType) {
                case TYPE_IMPORTANT_HEADER:
                case TYPE_REGULAR_HEADER:
                    View headerView = inflater.inflate(R.layout.item_friends_header, parent, false);
                    return new HeaderViewHolder(headerView);
                case TYPE_IMPORTANT_FRIEND:
                case TYPE_REGULAR_FRIEND:
                    View friendView = inflater.inflate(R.layout.list_item_friend, parent, false);
                    return new FriendViewHolder(friendView);
                default:
                    throw new IllegalArgumentException("Unknown view type: " + viewType);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
                if (getItemViewType(position) == TYPE_IMPORTANT_HEADER) {
                    headerHolder.bind("Важные друзья");
                } else {
                    headerHolder.bind("Люди в закладках");
                }
            } else if (holder instanceof FriendViewHolder) {
                FriendViewHolder friendHolder = (FriendViewHolder) holder;
                VKFriend friend = getFriendForPosition(position);
                friendHolder.bind(friend, specialUserChecker);

                friendHolder.itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onItemClick(friend);
                    }
                });
            }
        }

        private VKFriend getFriendForPosition(int position) {
            if (!importantFriends.isEmpty()) {
                if (position > 0 && position <= importantFriends.size()) {
                    return importantFriends.get(position - 1);
                } else if (position > importantFriends.size() + 1) {
                    return regularFriends.get(position - importantFriends.size() - 2);
                }
            } else {
                if (position > 0) {
                    return regularFriends.get(position - 1);
                }
            }
            return null;
        }

        @Override
        public int getItemCount() {
            int count = 0;
            if (!importantFriends.isEmpty()) {
                count += 2;
                count += importantFriends.size();
                count += regularFriends.size();
            } else {
                count += 1;
                count += regularFriends.size();
            }
            return count;
        }

        static class HeaderViewHolder extends RecyclerView.ViewHolder {
            private TextView headerText;

            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                headerText = itemView.findViewById(R.id.header_text);
            }

            void bind(String title) {
                headerText.setText(title);
            }
        }

        static class FriendViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView;
            TextView audioCountTextView;
            TextView avatarTextView;
            ImageView avatarImageView;
            View onlineIndicator;
            ImageView verifiedIcon;
            ImageView importantIcon;
            ImageView messageIcon;

            FriendViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.user);
                audioCountTextView = itemView.findViewById(R.id.audio_count);
                avatarTextView = itemView.findViewById(R.id.image_name);
                avatarImageView = itemView.findViewById(R.id.avatar_image);
                onlineIndicator = itemView.findViewById(R.id.online_indicator);
                verifiedIcon = itemView.findViewById(R.id.verified_icon);
                importantIcon = itemView.findViewById(R.id.important_icon);
                messageIcon = itemView.findViewById(R.id.message_icon);
            }

            void bind(VKFriend friend, SpecialUserChecker specialUserChecker) {
                nameTextView.setText(friend.firstName + " " + friend.lastName);

                if (friend.photoUrl != null && !friend.photoUrl.isEmpty()) {
                    avatarImageView.setVisibility(View.VISIBLE);
                    avatarTextView.setVisibility(View.GONE);
                    Picasso.get()
                            .load(friend.photoUrl)
                            .placeholder(createPlaceholder(friend.firstName))
                            .error(createPlaceholder(friend.firstName))
                            .resize(100, 100)
                            .centerCrop()
                            .transform(new CircleTransform())
                            .into(avatarImageView);
                } else {
                    avatarImageView.setVisibility(View.GONE);
                    avatarTextView.setVisibility(View.VISIBLE);
                    String firstLetter = getFirstLetter(friend.firstName);
                    avatarTextView.setText(firstLetter);
                    int color = getRandomColor();
                    GradientDrawable drawable = new GradientDrawable();
                    drawable.setShape(GradientDrawable.OVAL);
                    drawable.setColor(color);
                    avatarTextView.setBackground(drawable);
                }

                if (onlineIndicator != null) {
                    onlineIndicator.setVisibility(friend.isOnline ? View.VISIBLE : View.GONE);
                }

                if (verifiedIcon != null) {
                    if (specialUserChecker.isSpecialUser(friend.id)) {
                        verifiedIcon.setVisibility(View.VISIBLE);
                        verifiedIcon.setImageResource(R.drawable.check_verif);
                    } else {
                        verifiedIcon.setVisibility(View.GONE);
                    }
                }

                // НАСТРОЙКА ИКОНКИ СООБЩЕНИЯ
                if (messageIcon != null) {
                    messageIcon.setVisibility(View.VISIBLE);
                    messageIcon.setOnClickListener(v -> {
                        openDialogWithFriend(friend, specialUserChecker);
                    });

                    try {
                        messageIcon.setBackground(ContextCompat.getDrawable(itemView.getContext(), R.drawable.ripple_effect));
                    } catch (Exception e) {
                        messageIcon.setBackgroundResource(android.R.drawable.btn_default);
                    }
                }

                if (importantIcon != null) {
                    importantIcon.setVisibility(View.GONE);
                }
            }

            private void openDialogWithFriend(VKFriend friend, SpecialUserChecker specialUserChecker) {
                Context context = itemView.getContext();

                DialogActivity.start(
                        context,
                        String.valueOf(friend.id),
                        friend.firstName + " " + friend.lastName,
                        String.valueOf(friend.id),
                        specialUserChecker.isSpecialUser(friend.id)
                );

                if (context instanceof Activity) {
                    ((Activity) context).overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }

                Log.d("FriendsAdapter", "Opening dialog with friend: " + friend.firstName + " " + friend.lastName + " (ID: " + friend.id + ")");
            }

            private GradientDrawable createPlaceholder(String userName) {
                String firstLetter = getFirstLetter(userName);
                int color = getRandomColor();

                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(color);

                return drawable;
            }

            private String getFirstLetter(String name) {
                if (!TextUtils.isEmpty(name)) {
                    return name.substring(0, 1).toUpperCase();
                }
                return "?";
            }

            private int getRandomColor() {
                int[] colors = {
                        Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
                        Color.parseColor("#45B7D1"), Color.parseColor("#F9A826"),
                        Color.parseColor("#6A5ACD"), Color.parseColor("#FFA07A"),
                        Color.parseColor("#20B2AA"), Color.parseColor("#9370DB"),
                        Color.parseColor("#3CB371"), Color.parseColor("#FF4500")
                };
                return colors[new Random().nextInt(colors.length)];
            }
        }
    }
}