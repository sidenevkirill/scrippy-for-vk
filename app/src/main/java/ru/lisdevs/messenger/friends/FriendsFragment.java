package ru.lisdevs.messenger.friends;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.groups.GroupsTabFragment;
import ru.lisdevs.messenger.dialog.DialogActivity;
import ru.lisdevs.messenger.search.GroupsSearchFragment;
import ru.lisdevs.messenger.utils.CircleTransform;
import ru.lisdevs.messenger.utils.TokenManager;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.core.content.ContextCompat;


import android.util.Log;

import java.util.HashSet;
import java.util.Set;

import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.squareup.picasso.Picasso;


public class FriendsFragment extends Fragment {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private FriendsPagerAdapter pagerAdapter;
    private OkHttpClient httpClient;
    private String accessToken;
    private Toolbar toolbar;

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

        Context context = getSafeContext();
        if (context != null) {
            accessToken = TokenManager.getInstance(context).getToken();
        }
        loadSpecialUsers();
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
                Log.e("FriendsFragment", "Failed to load special users", e);
                isSpecialUsersLoaded = true;
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // Проверяем, прикреплен ли еще фрагмент
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
                    Log.e("FriendsFragment", "Error parsing special users", e);
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

            Log.d("FriendsFragment", "Loaded " + specialUsers.size() + " special users");
        } catch (JSONException e) {
            Log.e("FriendsFragment", "Error parsing special users JSON", e);
        }
    }

    private boolean isSpecialUser(long userId) {
        return specialUsers.contains(userId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends_with_tabs, container, false);

        initViews(view);
        setupViewPager();
        setupToolbar();

        return view;
    }

    private void initViews(View view) {
        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);
        toolbar = view.findViewById(R.id.toolbar);
    }

    private void setupViewPager() {
        pagerAdapter = new FriendsPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("ДРУЗЬЯ");
                    break;
                case 1:
                    tab.setText("ГРУППЫ");
                    break;
            }
        }).attach();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Отменяем все сетевые запросы при уничтожении фрагмента
        if (httpClient != null) {
            httpClient.dispatcher().cancelAll();
        }
    }

    // Вложенный класс для адаптера ViewPager
    public static class FriendsPagerAdapter extends FragmentStateAdapter {

        public FriendsPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new AllFriendsTabFragment();
                case 1:
                    return GroupsTabFragment.newInstance(GroupsTabFragment.TAB_TYPE_MY_GROUPS);
                    //return new OnlineFriendsTabFragment();
                default:
                    return new AllFriendsTabFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    // Фрагмент для вкладки "Все друзья"
    public static class AllFriendsTabFragment extends Fragment {

        private RecyclerView recyclerViewFriends;
        private FriendsAdapter adapter;
        private List<VKFriend> friendsList = new ArrayList<>();
        private List<VKFriend> filteredFriendsList = new ArrayList<>();
        private List<VKFriend> importantFriendsList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;
        private TextView statusTextView;
        private TextView friendsCountTextView;
        private OkHttpClient httpClient;
        private String accessToken;
        private MenuItem searchMenuItem;
        private SearchView searchView;
        private boolean isSearchMode = false;

        // ID важных друзей
        private Set<Long> importantFriendIds = new HashSet<>(Arrays.asList(
                123456789L,
                987654321L,
                555555555L,
                111111111L,
                999999999L
        ));

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);

            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Context context = getSafeContext();
            if (context != null) {
                accessToken = TokenManager.getInstance(context).getToken();
                loadImportantFriendIds();
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

        private void loadImportantFriendIds() {
            Context context = getSafeContext();
            if (context == null) return;

            SharedPreferences prefs = context.getSharedPreferences("important_friends", Context.MODE_PRIVATE);
            String savedIds = prefs.getString("important_friend_ids", "");
            if (!savedIds.isEmpty()) {
                importantFriendIds.clear();
                String[] ids = savedIds.split(",");
                for (String id : ids) {
                    try {
                        importantFriendIds.add(Long.parseLong(id.trim()));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void saveImportantFriendIds() {
            Context context = getSafeContext();
            if (context == null) return;

            SharedPreferences prefs = context.getSharedPreferences("important_friends", Context.MODE_PRIVATE);
            StringBuilder sb = new StringBuilder();
            for (Long id : importantFriendIds) {
                if (sb.length() > 0) sb.append(",");
                sb.append(id);
            }
            prefs.edit().putString("important_friend_ids", sb.toString()).apply();
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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

        @Override
        public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
            inflater.inflate(R.menu.menu_friends, menu);

            searchMenuItem = menu.findItem(R.id.action_show_friends_search);
            searchView = (SearchView) searchMenuItem.getActionView();
            setupSearchView();

            super.onCreateOptionsMenu(menu, inflater);
        }

        private void setupSearchView() {
            if (searchView == null) return;

            searchView.setQueryHint("Поиск по друзьям");
            searchView.setIconifiedByDefault(true);
            searchView.setIconified(true);

            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    filterFriends(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    if (newText.isEmpty()) {
                        resetSearch();
                    } else {
                        filterFriends(newText);
                    }
                    return true;
                }
            });

            searchView.setOnCloseListener(() -> {
                resetSearch();
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
                    resetSearch();
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

        private void filterFriends(String query) {
            filteredFriendsList.clear();
            importantFriendsList.clear();

            String lowerCaseQuery = query.toLowerCase();

            for (VKFriend friend : friendsList) {
                String fullName = friend.firstName + " " + friend.lastName;
                if (fullName.toLowerCase().contains(lowerCaseQuery) ||
                        friend.firstName.toLowerCase().contains(lowerCaseQuery) ||
                        friend.lastName.toLowerCase().contains(lowerCaseQuery)) {
                    filteredFriendsList.add(friend);
                }
            }

            adapter.updateFriendsList(filteredFriendsList, new ArrayList<>());
            updateFriendsCount(filteredFriendsList.size());

            if (filteredFriendsList.isEmpty() && !query.isEmpty()) {
                statusTextView.setText("Друзья не найдены");
                statusTextView.setVisibility(View.VISIBLE);
            } else {
                statusTextView.setVisibility(View.GONE);
            }
        }

        private void resetSearch() {
            separateImportantFriends();
            adapter.updateFriendsList(friendsList, importantFriendsList);
            updateFriendsCount(friendsList.size() + importantFriendsList.size());
            statusTextView.setVisibility(View.GONE);
        }

        private void setupRecyclerView() {
            adapter = new FriendsAdapter(new ArrayList<>(), new ArrayList<>(), friend -> openFriendDetails(friend), this::isSpecialUser);
            recyclerViewFriends.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerViewFriends.setAdapter(adapter);
        }

        private boolean isSpecialUser(long userId) {
            FriendsFragment parentFragment = (FriendsFragment) getParentFragment();
            return parentFragment != null && parentFragment.isSpecialUser(userId);
        }

        private void fetchVKFriends() {
            // Проверяем, прикреплен ли фрагмент
            if (!isAdded()) {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                return;
            }

            statusTextView.setText("Загрузка друзей...");
            friendsCountTextView.setText("Загрузка...");
            swipeRefreshLayout.setRefreshing(true);

            if (accessToken == null || accessToken.isEmpty()) {
                showError("Ошибка авторизации");
                swipeRefreshLayout.setRefreshing(false);
                friendsCountTextView.setText("Ошибка");
                return;
            }

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/friends.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("fields", "first_name,last_name,photo_100,online")
                    .addQueryParameter("v", "5.131")
                    .addQueryParameter("count", "1000")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "VKAndroidApp/1.0")
                    .build();

            httpClient.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    // Проверяем, прикреплен ли еще фрагмент
                    if (!isAdded()) return;

                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            if (!isAdded()) return;
                            showError("Ошибка сети: " + e.getMessage());
                            swipeRefreshLayout.setRefreshing(false);
                            friendsCountTextView.setText("Ошибка сети");
                        });
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    // Проверяем, прикреплен ли еще фрагмент
                    if (!isAdded()) return;

                    try (ResponseBody body = response.body()) {
                        if (!response.isSuccessful() || body == null) {
                            Activity activity = getActivity();
                            if (activity != null) {
                                activity.runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    showError("Ошибка сервера: " + response.code());
                                    swipeRefreshLayout.setRefreshing(false);
                                    friendsCountTextView.setText("Ошибка сервера");
                                });
                            }
                            return;
                        }

                        String json = body.string();
                        JSONObject jsonResponse = new JSONObject(json);

                        if (jsonResponse.has("error")) {
                            handleApiError(jsonResponse.getJSONObject("error"));
                            return;
                        }

                        List<VKFriend> friends = parseFriendsResponse(jsonResponse);
                        Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(() -> {
                                if (!isAdded()) return;
                                updateFriendsList(friends);
                                swipeRefreshLayout.setRefreshing(false);
                            });
                        }
                    } catch (Exception e) {
                        Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(() -> {
                                if (!isAdded()) return;
                                showError("Ошибка обработки данных");
                                swipeRefreshLayout.setRefreshing(false);
                                friendsCountTextView.setText("Ошибка данных");
                            });
                        }
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
                String photoUrl = friendJson.optString("photo_100", "");
                boolean isOnline = friendJson.optInt("online", 0) == 1;

                friends.add(new VKFriend(id, firstName, lastName, photoUrl, isOnline));
            }
            return friends;
        }

        private void updateFriendsList(List<VKFriend> friends) {
            friendsList.clear();
            friendsList.addAll(friends);

            separateImportantFriends();

            updateFriendsCount(friendsList.size() + importantFriendsList.size());

            if (friendsList.isEmpty() && importantFriendsList.isEmpty()) {
                statusTextView.setText("Друзья не найдены");
                statusTextView.setVisibility(View.VISIBLE);
            } else {
                statusTextView.setVisibility(View.GONE);
                adapter.updateFriendsList(friendsList, importantFriendsList);
            }
        }

        private void separateImportantFriends() {
            importantFriendsList.clear();
            List<VKFriend> regularFriends = new ArrayList<>();

            for (VKFriend friend : friendsList) {
                if (importantFriendIds.contains(friend.id)) {
                    importantFriendsList.add(friend);
                } else {
                    regularFriends.add(friend);
                }
            }

            friendsList.clear();
            friendsList.addAll(regularFriends);

            if (importantFriendsList.size() > 5) {
                importantFriendsList = importantFriendsList.subList(0, 5);
            }
        }

        private void updateFriendsCount(int count) {
            String countText;
            if (count == 0) {
                countText = "Нет друзей";
            } else {
                countText = formatFriendsCount(count);
                if (importantFriendsList.size() > 0) {
                    countText += " (" + importantFriendsList.size() + " важных)";
                }
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
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    showError(errorMsg);
                    swipeRefreshLayout.setRefreshing(false);
                    friendsCountTextView.setText("Друзья: 0");
                });
            }
        }

        private void showError(String message) {
            statusTextView.setText(message);
            statusTextView.setVisibility(View.VISIBLE);
        }

        private void openFriendDetails(VKFriend friend) {
            if (!isAdded()) return;

            UserProfileFragment userPhotosFragment = new UserProfileFragment();
           // FriendsMusicFragment.FriendDetailsFragment userPhotosFragment = new FriendsMusicFragment.FriendDetailsFragment();
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

        public void toggleImportantFriend(long friendId, boolean isImportant) {
            if (isImportant) {
                importantFriendIds.add(friendId);
            } else {
                importantFriendIds.remove(friendId);
            }
            saveImportantFriendIds();
            separateImportantFriends();
            adapter.updateFriendsList(friendsList, importantFriendsList);
            updateFriendsCount(friendsList.size() + importantFriendsList.size());
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            // Отменяем все сетевые запросы при уничтожении фрагмента
            if (httpClient != null) {
                httpClient.dispatcher().cancelAll();
            }
        }

        // Вложенный класс адаптера для вкладки "Все друзья"
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
                        headerHolder.bind("Все друзья");
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

    // Фрагмент для вкладки "Онлайн друзья"
    public static class OnlineFriendsTabFragment extends Fragment {

        private RecyclerView recyclerViewOnlineFriends;
        private FriendsAdapter adapter;
        private List<VKFriend> onlineFriendsList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;
        private TextView statusTextView;
        private TextView friendsCountTextView;
        private OkHttpClient httpClient;
        private String accessToken;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Context context = getSafeContext();
            if (context != null) {
                accessToken = TokenManager.getInstance(context).getToken();
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

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_audios, container, false);
            initViews(view);
            setupRecyclerView();
            fetchOnlineFriends();
            return view;
        }

        private void initViews(View view) {
            recyclerViewOnlineFriends = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            statusTextView = view.findViewById(R.id.emptyView);
            friendsCountTextView = view.findViewById(R.id.currentTimeTextView);

            swipeRefreshLayout.setOnRefreshListener(this::fetchOnlineFriends);
        }

        private void setupRecyclerView() {
            adapter = new FriendsAdapter(onlineFriendsList, friend -> openFriendDetails(friend), this::isSpecialUser);
            recyclerViewOnlineFriends.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerViewOnlineFriends.setAdapter(adapter);
        }

        private boolean isSpecialUser(long userId) {
            FriendsFragment parentFragment = (FriendsFragment) getParentFragment();
            return parentFragment != null && parentFragment.isSpecialUser(userId);
        }

        private void fetchOnlineFriends() {
            // Проверяем, прикреплен ли фрагмент
            if (!isAdded()) {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                return;
            }

            statusTextView.setText("Загрузка онлайн друзей...");
            friendsCountTextView.setText("Загрузка...");
            swipeRefreshLayout.setRefreshing(true);

            if (accessToken == null || accessToken.isEmpty()) {
                showError("Ошибка авторизации");
                swipeRefreshLayout.setRefreshing(false);
                friendsCountTextView.setText("Ошибка");
                return;
            }

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/friends.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("fields", "first_name,last_name,photo_100,online")
                    .addQueryParameter("v", "5.131")
                    .addQueryParameter("count", "1000")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "VKAndroidApp/1.0")
                    .build();

            httpClient.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    // Проверяем, прикреплен ли еще фрагмент
                    if (!isAdded()) return;

                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            if (!isAdded()) return;
                            showError("Ошибка сети: " + e.getMessage());
                            swipeRefreshLayout.setRefreshing(false);
                            friendsCountTextView.setText("Ошибка сети");
                        });
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    // Проверяем, прикреплен ли еще фрагмент
                    if (!isAdded()) return;

                    try (ResponseBody body = response.body()) {
                        if (!response.isSuccessful() || body == null) {
                            Activity activity = getActivity();
                            if (activity != null) {
                                activity.runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    showError("Ошибка сервера: " + response.code());
                                    swipeRefreshLayout.setRefreshing(false);
                                    friendsCountTextView.setText("Ошибка сервера");
                                });
                            }
                            return;
                        }

                        String json = body.string();
                        JSONObject jsonResponse = new JSONObject(json);

                        if (jsonResponse.has("error")) {
                            handleApiError(jsonResponse.getJSONObject("error"));
                            return;
                        }

                        List<VKFriend> allFriends = parseFriendsResponse(jsonResponse);
                        List<VKFriend> onlineFriends = filterOnlineFriends(allFriends);

                        Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(() -> {
                                if (!isAdded()) return;
                                updateOnlineFriendsList(onlineFriends);
                                swipeRefreshLayout.setRefreshing(false);
                            });
                        }
                    } catch (Exception e) {
                        Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(() -> {
                                if (!isAdded()) return;
                                showError("Ошибка обработки данных");
                                swipeRefreshLayout.setRefreshing(false);
                                friendsCountTextView.setText("Ошибка данных");
                            });
                        }
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
                String photoUrl = friendJson.optString("photo_100", "");
                boolean isOnline = friendJson.optInt("online", 0) == 1;

                friends.add(new VKFriend(id, firstName, lastName, photoUrl, isOnline));
            }
            return friends;
        }

        private List<VKFriend> filterOnlineFriends(List<VKFriend> allFriends) {
            List<VKFriend> onlineFriends = new ArrayList<>();
            for (VKFriend friend : allFriends) {
                if (friend.isOnline) {
                    onlineFriends.add(friend);
                }
            }
            return onlineFriends;
        }

        private void updateOnlineFriendsList(List<VKFriend> friends) {
            onlineFriendsList.clear();
            onlineFriendsList.addAll(friends);
            updateFriendsCount(friends.size());

            if (friends.isEmpty()) {
                statusTextView.setText("Нет друзей онлайн");
                statusTextView.setVisibility(View.VISIBLE);
            } else {
                statusTextView.setVisibility(View.GONE);
                adapter.notifyDataSetChanged();
            }
        }

        private void updateFriendsCount(int count) {
            String countText;
            if (count == 0) {
                countText = "Нет друзей онлайн";
            } else {
                countText = formatOnlineFriendsCount(count);
            }
            friendsCountTextView.setText(countText);
        }

        private String formatOnlineFriendsCount(int count) {
            if (count % 10 == 1 && count % 100 != 11) {
                return count + " друг онлайн";
            } else if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20)) {
                return count + " друга онлайн";
            } else {
                return count + " друзей онлайн";
            }
        }

        private void handleApiError(JSONObject errorObj) {
            String errorMsg = errorObj.optString("error_msg", "Неизвестная ошибка API");
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    showError(errorMsg);
                    swipeRefreshLayout.setRefreshing(false);
                    friendsCountTextView.setText("Ошибка API");
                });
            }
        }

        private void showError(String message) {
            statusTextView.setText(message);
            statusTextView.setVisibility(View.VISIBLE);
        }

        private void openFriendDetails(VKFriend friend) {
            if (!isAdded()) return;

            FriendsMusicFragment.FriendDetailsFragment detailsFragment = new FriendsMusicFragment.FriendDetailsFragment();
            Bundle args = new Bundle();
            args.putLong("friend_id", friend.id);
            args.putString("friend_name", friend.firstName + " " + friend.lastName);
            detailsFragment.setArguments(args);

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.container, detailsFragment)
                    .addToBackStack("friend_details")
                    .commit();
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

    // Основной адаптер для друзей
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
            ImageView messageIcon;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.user);
                audioCountTextView = itemView.findViewById(R.id.audio_count);
                avatarTextView = itemView.findViewById(R.id.image_name);
                avatarImageView = itemView.findViewById(R.id.avatar_image);
                onlineIndicator = itemView.findViewById(R.id.online_indicator);
                verifiedIcon = itemView.findViewById(R.id.verified_icon);
                messageIcon = itemView.findViewById(R.id.message_icon);
            }

            void bind(VKFriend friend, SpecialUserChecker specialUserChecker) {
                nameTextView.setText(friend.firstName + " " + friend.lastName);

                if (friend.photoUrl != null && !friend.photoUrl.isEmpty()) {
                    avatarTextView.setVisibility(View.GONE);
                    avatarImageView.setVisibility(View.VISIBLE);

                    Picasso.get()
                            .load(friend.photoUrl)
                            .placeholder(R.drawable.circle_friend)
                            .error(R.drawable.circle_friend)
                            .resize(100, 100)
                            .centerCrop()
                            .transform(new CircleTransform())
                            .into(avatarImageView, new com.squareup.picasso.Callback() {
                                @Override
                                public void onSuccess() {
                                    Log.d("Picasso", "Avatar loaded successfully for: " + friend.firstName);
                                }

                                @Override
                                public void onError(Exception e) {
                                    Log.e("Picasso", "Error loading avatar for: " + friend.firstName, e);
                                    showTextAvatar(friend.firstName);
                                }
                            });
                } else {
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

                // НАСТРОЙКА ИКОНКИ СООБЩЕНИЯ - ОСНОВНОЙ ФУНКЦИОНАЛ
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

                loadAudioCount(friend);

                itemView.setOnClickListener(v -> listener.onItemClick(friend));
            }

            private void showTextAvatar(String firstName) {
                avatarImageView.setVisibility(View.GONE);
                avatarTextView.setVisibility(View.VISIBLE);

                String firstLetter = getFirstLetter(firstName);
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

            private void loadAudioCount(VKFriend friend) {
                if (audioCountCache.containsKey(friend.id)) {
                    int count = audioCountCache.get(friend.id);
                    updateCountText(count);
                    return;
                }

                audioCountTextView.setText("Загрузка...");

                Context context = itemView.getContext();
                String accessToken = TokenManager.getInstance(context).getToken();
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
                        .enqueue(new okhttp3.Callback() {
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
                Activity activity = (Activity) itemView.getContext();
                if (activity != null) {
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

        public String getPhotoUrl() {
            return photoUrl;
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // Просто не вызываем inflater - меню не будет создано
        // inflater.inflate(R.menu.menu_friends, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void setupToolbar() {
        if (toolbar != null && getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {

            }

        }
    }



    private void navigateToGroupsSearchFragment() {
        GroupsSearchFragment groupsSearchFragment = new GroupsSearchFragment();
        Bundle args = new Bundle();

        groupsSearchFragment.setArguments(args);

        // Используем getChildFragmentManager() для вложенных фрагментов
        // или getParentFragmentManager() в зависимости от структуры
        FragmentManager fragmentManager = getParentFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.container, groupsSearchFragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }
}