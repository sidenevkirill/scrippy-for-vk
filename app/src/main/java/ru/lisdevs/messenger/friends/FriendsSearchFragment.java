package ru.lisdevs.messenger.friends;


import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
import ru.lisdevs.messenger.utils.CircleTransform;
import ru.lisdevs.messenger.utils.TokenManager;

public class FriendsSearchFragment extends Fragment {

    private RecyclerView recyclerViewFriends;
    private FriendsAdapter adapter;
    private List<VKFriend> friendsList = new ArrayList<>();
    private List<VKFriend> filteredFriendsList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView statusTextView;
    private TextView friendsCountTextView;
    private OkHttpClient httpClient;
    private String accessToken;
    private Toolbar toolbar;
    private MenuItem searchMenuItem;
    private SearchView searchView;
    private boolean isSearchMode = false;

    private Set<Long> specialUsers = new HashSet<>();
    private boolean isSpecialUsersLoaded = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        accessToken = TokenManager.getInstance(requireContext()).getToken();

        // Загружаем специальных пользователей
        loadSpecialUsers();
    }

    // НОВЫЙ МЕТОД: Проверка прикрепленности фрагмента
    private boolean isFragmentAttached() {
        return isAdded() && getActivity() != null && !getActivity().isFinishing();
    }

    // НОВЫЙ МЕТОД: Безопасный запуск в UI потоке
    private void runOnUiThreadIfAttached(Runnable action) {
        if (isFragmentAttached()) {
            getActivity().runOnUiThread(() -> {
                if (isFragmentAttached()) {
                    action.run();
                }
            });
        }
    }

    private void loadSpecialUsers() {
        Request request = new Request.Builder()
                .url("https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/special_users.json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("FriendsSearchFragment", "Failed to load special users", e);
                isSpecialUsersLoaded = true;
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        String json = body.string();
                        parseSpecialUsers(json);
                    }
                } catch (Exception e) {
                    Log.e("FriendsSearchFragment", "Error parsing special users", e);
                } finally {
                    isSpecialUsersLoaded = true;
                    runOnUiThreadIfAttached(() -> {
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    });
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

            Log.d("FriendsSearchFragment", "Loaded " + specialUsers.size() + " special users");
        } catch (JSONException e) {
            Log.e("FriendsSearchFragment", "Error parsing special users JSON", e);
        }
    }

    private boolean isSpecialUser(long userId) {
        return specialUsers.contains(userId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_friends_search, container, false);

        toolbar = view.findViewById(R.id.toolbar);

        initViews(view);
        setupRecyclerView();
        setupToolbar();

        // Загружаем рекомендуемых пользователей при открытии фрагмента
        loadRecommendedUsers();

        return view;
    }

    private void setupRecyclerView() {
        if (recyclerViewFriends == null) return;

        adapter = new FriendsAdapter(friendsList, this::openFriendDetails, this::isSpecialUser);
        recyclerViewFriends.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewFriends.setAdapter(adapter);
    }

    private void initViews(View view) {
        recyclerViewFriends = view.findViewById(R.id.friendsRecyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        statusTextView = view.findViewById(R.id.statusTextView);
        friendsCountTextView = view.findViewById(R.id.count);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            // При обновлении загружаем рекомендуемых пользователей
            loadRecommendedUsers();
        });
    }

    private void setupToolbar() {
        if (toolbar != null) {
            ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
            toolbar.setTitle("Поиск людей");
            toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.search_friends, menu);

        searchMenuItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) searchMenuItem.getActionView();
        setupSearchView();

        super.onCreateOptionsMenu(menu, inflater);
    }

    private void setupSearchView() {
        if (searchView == null) return;

        searchView.setQueryHint("Начните вводить имя");
        searchView.setIconifiedByDefault(true);
        searchView.setIconified(true);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performGlobalSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    // При очистке поиска показываем рекомендуемых пользователей
                    loadRecommendedUsers();
                } else if (newText.length() >= 3) {
                    performGlobalSearch(newText);
                }
                return true;
            }
        });

        searchView.setOnCloseListener(() -> {
            loadRecommendedUsers();
            isSearchMode = false;
            return false;
        });

        searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                isSearchMode = true;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                loadRecommendedUsers();
                isSearchMode = false;
                return true;
            }
        });

        searchView.setMaxWidth(Integer.MAX_VALUE);

        ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_button);
        if (searchIcon != null) {
            searchIcon.setImageResource(R.drawable.ic_search);
        }

        EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (searchEditText != null) {
            searchEditText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
            searchEditText.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
        }
    }

    private void loadRecommendedUsers() {
        if (accessToken == null || accessToken.isEmpty()) {
            showError("Ошибка авторизации");
            return;
        }

        statusTextView.setText("Загрузка...");
        statusTextView.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setRefreshing(true);

        // Используем метод friends.getRecommendations для получения рекомендуемых друзей
        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/friends.getRecommendations")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("fields", "first_name,last_name,photo_100,online,is_closed,deactivated")
                .addQueryParameter("v", "5.131")
                .addQueryParameter("count", "50")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "VKAndroidApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThreadIfAttached(() -> {
                    showError("Ошибка сети: " + e.getMessage());
                    swipeRefreshLayout.setRefreshing(false);
                    friendsCountTextView.setText("Ошибка сети");
                    // Если не удалось загрузить рекомендации, показываем популярных пользователей
                    loadPopularUsers();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        runOnUiThreadIfAttached(() -> {
                            showError("Ошибка сервера: " + response.code());
                            swipeRefreshLayout.setRefreshing(false);
                            friendsCountTextView.setText("Ошибка сервера");
                            loadPopularUsers();
                        });
                        return;
                    }

                    String json = body.string();
                    JSONObject jsonResponse = new JSONObject(json);

                    if (jsonResponse.has("error")) {
                        // Если метод recommendations недоступен, загружаем популярных пользователей
                        runOnUiThreadIfAttached(() -> {
                            loadPopularUsers();
                        });
                        return;
                    }

                    List<VKFriend> recommendedUsers = parseRecommendedResponse(jsonResponse);
                    runOnUiThreadIfAttached(() -> {
                        updateFriendsList(recommendedUsers, "");
                        swipeRefreshLayout.setRefreshing(false);
                    });
                } catch (Exception e) {
                    runOnUiThreadIfAttached(() -> {
                        showError("Ошибка обработки данных");
                        swipeRefreshLayout.setRefreshing(false);
                        friendsCountTextView.setText("Ошибка данных");
                        loadPopularUsers();
                    });
                }
            }
        });
    }

    private void loadPopularUsers() {
        // Альтернативный метод - поиск популярных пользователей
        HttpUrl.Builder urlBuilder = HttpUrl.parse("https://api.vk.com/method/users.search")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("fields", "first_name,last_name,photo_100,online,is_closed,deactivated")
                .addQueryParameter("v", "5.131")
                .addQueryParameter("count", "50")
                .addQueryParameter("sort", "0")
                .addQueryParameter("q", "а"); // Поиск по популярным именам

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "VKAndroidApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThreadIfAttached(() -> {
                    showError("Не удалось загрузить пользователей");
                    swipeRefreshLayout.setRefreshing(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        runOnUiThreadIfAttached(() -> {
                            createDemoUsers();
                            swipeRefreshLayout.setRefreshing(false);
                        });
                        return;
                    }

                    String json = body.string();
                    JSONObject jsonResponse = new JSONObject(json);

                    if (jsonResponse.has("error")) {
                        runOnUiThreadIfAttached(() -> {
                            createDemoUsers();
                            swipeRefreshLayout.setRefreshing(false);
                        });
                        return;
                    }

                    List<VKFriend> popularUsers = parseSearchResponse(jsonResponse);
                    runOnUiThreadIfAttached(() -> {
                        updateFriendsList(popularUsers, "");
                        swipeRefreshLayout.setRefreshing(false);
                    });
                } catch (Exception e) {
                    runOnUiThreadIfAttached(() -> {
                        createDemoUsers();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }
        });
    }

    private List<VKFriend> parseRecommendedResponse(JSONObject jsonResponse) throws JSONException {
        List<VKFriend> users = new ArrayList<>();
        JSONArray items = jsonResponse.getJSONObject("response").getJSONArray("items");

        for (int i = 0; i < items.length(); i++) {
            JSONObject userJson = items.getJSONObject(i);

            if (userJson.has("deactivated")) {
                continue;
            }

            long id = userJson.getLong("id");
            String firstName = userJson.getString("first_name");
            String lastName = userJson.getString("last_name");
            String photoUrl = userJson.optString("photo_100", "");
            boolean isOnline = userJson.optInt("online", 0) == 1;
            boolean isClosed = userJson.optInt("is_closed", 1) == 1;

            // Пропускаем закрытые профили
            if (!isClosed) {
                users.add(new VKFriend(id, firstName, lastName, photoUrl, isOnline));
            }
        }
        return users;
    }

    private void createDemoUsers() {
        // Создаем демо-пользователей, если API не доступно
        List<VKFriend> demoUsers = new ArrayList<>();
        String[] names = {"Александр", "Мария", "Дмитрий", "Анна", "Сергей", "Екатерина", "Андрей", "Ольга"};
        String[] lastNames = {"Иванов", "Петрова", "Сидоров", "Смирнова", "Кузнецов", "Васильева", "Попов", "Новикова"};

        Random random = new Random();
        for (int i = 0; i < 15; i++) {
            String firstName = names[random.nextInt(names.length)];
            String lastName = lastNames[random.nextInt(lastNames.length)];
            demoUsers.add(new VKFriend(1000000 + i, firstName, lastName, "", random.nextBoolean()));
        }

        updateFriendsList(demoUsers, "");
    }

    private void performGlobalSearch(String query) {
        if (accessToken == null || accessToken.isEmpty()) {
            showError("Ошибка авторизации");
            return;
        }

        statusTextView.setText("Поиск...");
        statusTextView.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setRefreshing(true);

        HttpUrl.Builder urlBuilder = HttpUrl.parse("https://api.vk.com/method/users.search")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("fields", "first_name,last_name,photo_100,online,is_closed,deactivated")
                .addQueryParameter("v", "5.131")
                .addQueryParameter("count", "1000")
                .addQueryParameter("sort", "0");

        if (!query.isEmpty()) {
            urlBuilder.addQueryParameter("q", query);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "VKAndroidApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThreadIfAttached(() -> {
                    showError("Ошибка сети: " + e.getMessage());
                    swipeRefreshLayout.setRefreshing(false);
                    friendsCountTextView.setText("Ошибка сети");
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        runOnUiThreadIfAttached(() -> {
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

                    List<VKFriend> foundUsers = parseSearchResponse(jsonResponse);
                    runOnUiThreadIfAttached(() -> {
                        updateFriendsList(foundUsers, "найденных");
                        swipeRefreshLayout.setRefreshing(false);
                    });
                } catch (Exception e) {
                    runOnUiThreadIfAttached(() -> {
                        showError("Ошибка обработки данных");
                        swipeRefreshLayout.setRefreshing(false);
                        friendsCountTextView.setText("Ошибка данных");
                    });
                }
            }
        });
    }

    private List<VKFriend> parseSearchResponse(JSONObject jsonResponse) throws JSONException {
        List<VKFriend> users = new ArrayList<>();
        JSONArray items = jsonResponse.getJSONObject("response").getJSONArray("items");

        for (int i = 0; i < items.length(); i++) {
            JSONObject userJson = items.getJSONObject(i);

            if (userJson.has("deactivated")) {
                continue;
            }

            long id = userJson.getLong("id");
            String firstName = userJson.getString("first_name");
            String lastName = userJson.getString("last_name");
            String photoUrl = userJson.optString("photo_100", "");
            boolean isOnline = userJson.optInt("online", 0) == 1;
            boolean isClosed = userJson.optInt("is_closed", 1) == 1;

            users.add(new VKFriend(id, firstName, lastName, photoUrl, isOnline));
        }
        return users;
    }

    private void updateFriendsList(List<VKFriend> friends, String listType) {
        if (!isFragmentAttached()) return;

        friendsList.clear();
        friendsList.addAll(friends);
        updateFriendsCount(friends.size(), listType);

        if (friends.isEmpty()) {
            statusTextView.setText("Пользователи не найдены");
            statusTextView.setVisibility(View.VISIBLE);
        } else {
            statusTextView.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
        }
    }

    private void updateFriendsCount(int count, String listType) {
        if (!isFragmentAttached()) return;

        String countText;
        if (count == 0) {
            countText = "Не найдено";
        } else {
            countText = formatFriendsCount(count) + " " + listType;
        }
        friendsCountTextView.setText(countText);
    }

    private String formatFriendsCount(int count) {
        if (count % 10 == 1 && count % 100 != 11) {
            return count + " рекомендуемый";
        } else if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20)) {
            return count + " рекомендуемых";
        } else {
            return count + " рекомендуемых";
        }
    }

    private void handleApiError(JSONObject errorObj) {
        String errorMsg = errorObj.optString("error_msg", "Неизвестная ошибка API");
        runOnUiThreadIfAttached(() -> {
            showError(errorMsg);
            swipeRefreshLayout.setRefreshing(false);
            friendsCountTextView.setText("Ошибка API");
        });
    }

    private void showError(String message) {
        if (!isFragmentAttached()) return;
        statusTextView.setText(message);
        statusTextView.setVisibility(View.VISIBLE);
    }

    private void openFriendDetails(VKFriend friend) {
        if (!isFragmentAttached()) return;

        UserProfileFragment userPhotosFragment = new UserProfileFragment();
        Bundle args = new Bundle();
        args.putLong("friend_id", friend.id);
        args.putString("friend_name", friend.firstName + " " + friend.lastName);
        userPhotosFragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.container, userPhotosFragment)
                .addToBackStack("friend_details")
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
        private Map<Long, Integer> audioCountCache = new HashMap<>();
        private SpecialUserChecker specialUserChecker;

        interface OnItemClickListener {
            void onItemClick(VKFriend friend);
        }

        interface SpecialUserChecker {
            boolean isSpecialUser(long userId);
        }

        FriendsAdapter(List<VKFriend> friends, OnItemClickListener listener, SpecialUserChecker specialUserChecker) {
            this.friends = friends;
            this.listener = listener;
            this.specialUserChecker = specialUserChecker;
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
                    .inflate(R.layout.list_item_friend, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VKFriend friend = friends.get(position);
            holder.bind(friend, specialUserChecker);
        }

        @Override
        public int getItemCount() {
            return friends.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView;
            TextView audioCountTextView;
            TextView avatarTextView;
            ImageView avatarImageView;
            View onlineIndicator;
            ImageView verifiedIcon;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.user);
                audioCountTextView = itemView.findViewById(R.id.audio_count);
                avatarTextView = itemView.findViewById(R.id.image_name);
                avatarImageView = itemView.findViewById(R.id.avatar_image);
                onlineIndicator = itemView.findViewById(R.id.online_indicator);
                verifiedIcon = itemView.findViewById(R.id.verified_icon);
            }

            void bind(VKFriend friend, SpecialUserChecker specialUserChecker) {
                nameTextView.setText(friend.firstName + " " + friend.lastName);

                // Загружаем аватарку если есть URL
                if (friend.photoUrl != null && !friend.photoUrl.isEmpty()) {
                    // Показываем ImageView, скрываем TextView
                    avatarImageView.setVisibility(View.VISIBLE);
                    avatarTextView.setVisibility(View.GONE);

                    // Загружаем аватарку с помощью Picasso
                    Picasso.get()
                            .load(friend.photoUrl)
                            .placeholder(createPlaceholder(friend.firstName))
                            .error(createPlaceholder(friend.firstName))
                            .resize(100, 100)
                            .centerCrop()
                            .transform(new CircleTransform())
                            .into(avatarImageView);
                } else {
                    // Если URL нет, показываем текстовый аватар
                    showTextAvatar(friend.firstName);
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

                loadAudioCount(friend);

                itemView.setOnClickListener(v -> listener.onItemClick(friend));
            }

            private Drawable createPlaceholder(String userName) {
                String firstLetter = getFirstLetter(userName);
                int color = getRandomColor();

                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(color);

                return drawable;
            }

            private void showTextAvatar(String userName) {
                // Скрываем ImageView, показываем TextView
                avatarImageView.setVisibility(View.GONE);
                avatarTextView.setVisibility(View.VISIBLE);

                String firstLetter = getFirstLetter(userName);
                avatarTextView.setText(firstLetter);

                int color = getRandomColor();
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(color);
                avatarTextView.setBackground(drawable);
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
                return colors[random.nextInt(colors.length)];
            }

            private void loadAudioCount(VKFriend friend) {
                if (audioCountCache.containsKey(friend.id)) {
                    int count = audioCountCache.get(friend.id);
                    updateCountText(count);
                    return;
                }

                audioCountTextView.setText("Загрузка...");

                String accessToken = TokenManager.getInstance(itemView.getContext()).getToken();
                if (accessToken == null) {
                    audioCountTextView.setText("Треки: 0");
                    return;
                }

                HttpUrl url = HttpUrl.parse("https://api.vk.com/method/audio.getCount")
                        .newBuilder()
                        .addQueryParameter("access_token", accessToken)
                        .addQueryParameter("owner_id", String.valueOf(friend.id))
                        .addQueryParameter("v", "5.131")
                        .build();

                countClient.newCall(new Request.Builder()
                                .url(url)
                                .build())
                        .enqueue(new Callback() {
                            @Override
                            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                updateCountText(0);
                            }

                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                                try {
                                    String json = response.body().string();
                                    JSONObject jsonObject = new JSONObject(json);

                                    if (jsonObject.has("error")) {
                                        updateCountText(0);
                                        return;
                                    }

                                    Object responseObj = jsonObject.get("response");
                                    int count = 0;

                                    if (responseObj instanceof JSONObject) {
                                        count = ((JSONObject) responseObj).getInt("count");
                                    } else if (responseObj instanceof Integer) {
                                        count = (Integer) responseObj;
                                    }

                                    audioCountCache.put(friend.id, count);
                                    updateCountText(count);
                                } catch (Exception e) {
                                    updateCountText(0);
                                }
                            }
                        });
            }

            private void updateCountText(int count) {
                if (itemView.getContext() instanceof Activity) {
                    Activity activity = (Activity) itemView.getContext();
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        activity.runOnUiThread(() -> {
                            String text;
                            if (count == 0) {
                                text = "Нет треков";
                            } else {
                                text = formatTrackCount(count);
                            }
                            audioCountTextView.setText(text);
                        });
                    }
                }
            }

            private String formatTrackCount(int count) {
                if (count % 10 == 1 && count % 100 != 11) {
                    return count + " трек";
                } else if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20)) {
                    return count + " трека";
                } else {
                    return count + " треков";
                }
            }
        }
    }
}