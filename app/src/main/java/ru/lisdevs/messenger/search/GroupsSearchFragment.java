package ru.lisdevs.messenger.search;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.groups.GroupDetailsFragment;
import ru.lisdevs.messenger.utils.TokenManager;

import android.util.Log;
import android.widget.ImageView;

import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

import okhttp3.ResponseBody;

import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import java.util.Arrays;

public class GroupsSearchFragment extends Fragment {

    private RecyclerView recyclerView;
    private List<Group> groups = new ArrayList<>();
    private List<Group> filteredGroups = new ArrayList<>();
    private GroupsAdapter adapter;
    private String accessToken;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyView;
    private TextView groupsCountTextView;
    private AutoCompleteTextView searchAutoComplete;
    private Toolbar toolbar;
    private OkHttpClient client;

    private int currentOffset = 0;
    private String currentQuery = "";
    private boolean isLoadingMore = false;
    private boolean isShowingUserGroups = true;

    private Set<Long> specialGroups = new HashSet<>();
    private boolean isSpecialGroupsLoaded = false;
    private ArrayAdapter<String> searchSuggestionsAdapter;
    private List<String> searchHistory = new ArrayList<>();

    public static GroupsSearchFragment newInstance() {
        GroupsSearchFragment fragment = new GroupsSearchFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        accessToken = TokenManager.getInstance(requireContext()).getToken();
        client = new OkHttpClient();

        loadSearchHistory();
        loadSpecialGroups();
    }

    private void loadSearchHistory() {
        SharedPreferences prefs = requireContext().getSharedPreferences("search_history", Context.MODE_PRIVATE);
        String history = prefs.getString("group_search_history", "");
        if (!history.isEmpty()) {
            searchHistory = new ArrayList<>(Arrays.asList(history.split(",")));
        }
    }

    private void saveSearchHistory() {
        SharedPreferences prefs = requireContext().getSharedPreferences("search_history", Context.MODE_PRIVATE);
        String history = TextUtils.join(",", searchHistory);
        prefs.edit().putString("group_search_history", history).apply();
    }

    private void addToSearchHistory(String query) {
        if (!TextUtils.isEmpty(query) && !searchHistory.contains(query)) {
            searchHistory.add(0, query);
            if (searchHistory.size() > 10) {
                searchHistory = searchHistory.subList(0, 10);
            }
            saveSearchHistory();
            updateSearchSuggestions();
        }
    }

    private void updateSearchSuggestions() {
        if (searchSuggestionsAdapter != null) {
            searchSuggestionsAdapter.clear();
            searchSuggestionsAdapter.addAll(searchHistory);
            searchSuggestionsAdapter.notifyDataSetChanged();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groups_search_new, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerView = view.findViewById(R.id.groupsRecyclerView);
        emptyView = view.findViewById(R.id.emptyView);
        groupsCountTextView = view.findViewById(R.id.groups_count_text);
        searchAutoComplete = view.findViewById(R.id.search_autocomplete);

        setupToolbar();
        setupSearchAutoComplete();
        setupRecyclerView();
        setupSwipeRefresh();
        setupScrollListener();

        loadUserGroups();

        return view;
    }

    private void setupToolbar() {
        //toolbar.setNavigationIcon(R.drawable.arrow_left_black);
        //toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        toolbar.setTitle("Группы");
    }

    private void setupSearchAutoComplete() {
        searchSuggestionsAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                searchHistory
        );

        searchAutoComplete.setAdapter(searchSuggestionsAdapter);
        searchAutoComplete.setThreshold(1);
        searchAutoComplete.setHint("Поиск групп...");

        searchAutoComplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.isEmpty() && !isShowingUserGroups) {
                    isShowingUserGroups = true;
                    loadUserGroups();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        searchAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
            String selectedQuery = (String) parent.getItemAtPosition(position);
            searchAutoComplete.setText(selectedQuery);
            performSearch(selectedQuery);
        });

        searchAutoComplete.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = searchAutoComplete.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                    return true;
                }
            }
            return false;
        });

        searchAutoComplete.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Drawable[] drawables = searchAutoComplete.getCompoundDrawables();
                if (drawables[2] != null) {
                    if (event.getRawX() >= (searchAutoComplete.getRight() - drawables[2].getBounds().width() - searchAutoComplete.getPaddingRight())) {
                        searchAutoComplete.setText("");
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private void performSearch(String query) {
        if (query != null && !query.trim().isEmpty()) {
            currentQuery = query.trim();
            currentOffset = 0;
            isShowingUserGroups = false;
            addToSearchHistory(currentQuery);
            searchGroups(currentQuery, currentOffset);
            hideKeyboard();
        }
    }

    private void setupRecyclerView() {
        adapter = new GroupsAdapter(filteredGroups, group -> {
            openGroupDetails(group.id, group.name);
        }, this::isSpecialGroup);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (isShowingUserGroups) {
                loadUserGroups();
            } else if (!currentQuery.isEmpty()) {
                currentOffset = 0;
                searchGroups(currentQuery, currentOffset);
            } else {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void setupScrollListener() {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (!isShowingUserGroups) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int visibleItemCount = layoutManager.getChildCount();
                        int totalItemCount = layoutManager.getItemCount();
                        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                        if (!isLoadingMore && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                                && firstVisibleItemPosition >= 0 && totalItemCount >= 10) {
                            loadMoreResults();
                        }
                    }
                }
            }
        });
    }

    private void loadMoreResults() {
        isLoadingMore = true;
        currentOffset += 100;
        searchGroups(currentQuery, currentOffset);
    }

    private void loadUserGroups() {
        if (accessToken == null || accessToken.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            showError("Требуется авторизация");
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
        toolbar.setTitle("Группы");

        long userId = 8549;

        String url = "https://api.vk.com/method/groups.get" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&extended=1" +
                "&fields=photo_100,name,members_count,description,activity,is_verified" +
                "&count=100" +
                "&offset=" + currentOffset +
                "&user_id=" + userId;

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    showError("Ошибка сети: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    requireActivity().runOnUiThread(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        showError("Ошибка сервера: " + response.code());
                    });
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject jsonObject = new JSONObject(responseBody);

                    if (jsonObject.has("error")) {
                        handleApiError(jsonObject.getJSONObject("error"));
                        return;
                    }

                    JSONObject responseObj = jsonObject.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.getJSONArray("items");
                    List<Group> userGroups = new ArrayList<>();

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject gObj = items.getJSONObject(i);
                        long id = gObj.getLong("id");
                        String name = gObj.getString("name");
                        String photoUrl = gObj.optString("photo_100", "");
                        String description = gObj.optString("description", "");
                        int membersCount = gObj.optInt("members_count", 0);
                        String activity = gObj.optString("activity", "");

                        userGroups.add(new Group(id, name, photoUrl, description, membersCount, activity));
                    }

                    requireActivity().runOnUiThread(() -> {
                        updateUserGroupsResults(userGroups, totalCount);
                        swipeRefreshLayout.setRefreshing(false);
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        showError("Ошибка данных: " + e.getMessage());
                    });
                }
            }
        });
    }

    private void updateUserGroupsResults(List<Group> userGroups, int totalCount) {
        groups.clear();
        groups.addAll(userGroups);
        filteredGroups.clear();
        filteredGroups.addAll(userGroups);

        adapter.notifyDataSetChanged();
        updateEmptyView();
        updateGroupsCount(totalCount);

        if (userGroups.isEmpty()) {
            showError("У вас пока нет групп");
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void updateGroupsCount(int count) {
        if (groupsCountTextView != null) {
            String countText = getResources().getQuantityString(
                    R.plurals.groups_count, count, count);
            groupsCountTextView.setText(countText);
            groupsCountTextView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void searchGroups(String query, int offset) {
        if (accessToken == null || accessToken.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            showError("Требуется авторизация");
            return;
        }

        if (query.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            showError("Введите поисковый запрос");
            return;
        }

        if (offset == 0) {
            swipeRefreshLayout.setRefreshing(true);
            toolbar.setTitle("Поиск: " + query);
        }

        try {
            String url = "https://api.vk.com/method/groups.search" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name()) +
                    "&type=group" +
                    "&sort=0" +
                    "&count=100" +
                    "&offset=" + offset +
                    "&fields=photo_100,name,members_count,description,activity";

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", Authorizer.getKateUserAgent())
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        isLoadingMore = false;
                        swipeRefreshLayout.setRefreshing(false);
                        showError("Ошибка сети: " + e.getMessage());
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        requireActivity().runOnUiThread(() -> {
                            isLoadingMore = false;
                            swipeRefreshLayout.setRefreshing(false);
                            showError("Ошибка сервера: " + response.code());
                        });
                        return;
                    }

                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonObject = new JSONObject(responseBody);

                        if (jsonObject.has("error")) {
                            handleApiError(jsonObject.getJSONObject("error"));
                            return;
                        }

                        JSONObject responseObj = jsonObject.getJSONObject("response");
                        int count = responseObj.optInt("count", 0);
                        JSONArray items = responseObj.getJSONArray("items");
                        List<Group> searchResults = new ArrayList<>();

                        for (int i = 0; i < items.length(); i++) {
                            JSONObject gObj = items.getJSONObject(i);
                            long id = gObj.getLong("id");
                            String name = gObj.getString("name");
                            String photoUrl = gObj.optString("photo_100", "");
                            String description = gObj.optString("description", "");
                            int membersCount = gObj.optInt("members_count", 0);
                            String activity = gObj.optString("activity", "");

                            searchResults.add(new Group(id, name, photoUrl, description, membersCount, activity));
                        }

                        requireActivity().runOnUiThread(() -> {
                            isLoadingMore = false;
                            if (offset == 0) {
                                updateSearchResults(searchResults, query, count);
                            } else {
                                addMoreResults(searchResults);
                            }
                            swipeRefreshLayout.setRefreshing(false);
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            isLoadingMore = false;
                            swipeRefreshLayout.setRefreshing(false);
                            showError("Ошибка данных: " + e.getMessage());
                        });
                    }
                }
            });
        } catch (Exception e) {
            isLoadingMore = false;
            swipeRefreshLayout.setRefreshing(false);
            showError("Ошибка: " + e.getMessage());
        }
    }

    private void updateSearchResults(List<Group> searchResults, String query, int totalCount) {
        groups.clear();
        groups.addAll(searchResults);
        filteredGroups.clear();
        filteredGroups.addAll(searchResults);

        adapter.notifyDataSetChanged();
        updateEmptyView();
        updateGroupsCount(totalCount);

        if (searchResults.isEmpty()) {
            showError("По запросу \"" + query + "\" ничего не найдено");
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void addMoreResults(List<Group> newResults) {
        int startPosition = groups.size();
        groups.addAll(newResults);
        filteredGroups.addAll(newResults);
        adapter.notifyItemRangeInserted(startPosition, newResults.size());
    }

    private void updateEmptyView() {
        if (filteredGroups.isEmpty()) {
            if (isShowingUserGroups) {
                emptyView.setText("У вас пока нет групп");
            } else if (currentQuery.isEmpty()) {
                emptyView.setText("Введите запрос для поиска групп");
            } else {
                emptyView.setText("Ничего не найдено");
            }
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            groupsCountTextView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void handleApiError(JSONObject errorObj) {
        int errorCode = errorObj.optInt("error_code", 0);
        String errorMsg = errorObj.optString("error_msg", "Ошибка API");

        if (errorCode == 6) {
            errorMsg = "Слишком много запросов. Попробуйте позже.";
        } else if (errorCode == 9) {
            errorMsg = "Слишком частые запросы. Подождите немного.";
        } else if (errorCode == 5) {
            errorMsg = "Требуется авторизация";
        }

        String finalErrorMsg = errorMsg;
        requireActivity().runOnUiThread(() -> {
            isLoadingMore = false;
            swipeRefreshLayout.setRefreshing(false);
            showError(finalErrorMsg + " (код: " + errorCode + ")");
        });
    }

    private void showError(String message) {
        emptyView.setText(message);
        emptyView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        groupsCountTextView.setVisibility(View.GONE);
    }

    private void hideKeyboard() {
        if (getActivity() != null && searchAutoComplete != null) {
            InputMethodManager imm = (InputMethodManager) getActivity()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(searchAutoComplete.getWindowToken(), 0);
        }
    }

    private void openGroupDetails(long groupId, String name) {
        Bundle args = new Bundle();
        args.putLong("group_id", groupId);
        args.putString("group_name", name);

        Fragment fragment = new GroupDetailsFragment();
        fragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, fragment)
                .addToBackStack("group_details")
                .commit();
    }

    private void loadSpecialGroups() {
        Request request = new Request.Builder()
                .url("https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/special_groups.json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("GroupsSearchFragment", "Failed to load special groups", e);
                isSpecialGroupsLoaded = true;
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        String json = body.string();
                        parseSpecialGroups(json);
                    }
                } catch (Exception e) {
                    Log.e("GroupsSearchFragment", "Error parsing special groups", e);
                } finally {
                    isSpecialGroupsLoaded = true;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                        });
                    }
                }
            }
        });
    }

    private void parseSpecialGroups(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray groupsArray = jsonObject.getJSONArray("special_groups");

            specialGroups.clear();
            for (int i = 0; i < groupsArray.length(); i++) {
                long groupId = groupsArray.getLong(i);
                specialGroups.add(groupId);
            }

            Log.d("GroupsSearchFragment", "Loaded " + specialGroups.size() + " special groups");
        } catch (JSONException e) {
            Log.e("GroupsSearchFragment", "Error parsing special groups JSON", e);
        }
    }

    private boolean isSpecialGroup(long groupId) {
        return specialGroups.contains(groupId);
    }

    static class Group {
        long id;
        String name;
        String photoUrl;
        String description;
        int membersCount;
        String activity;

        Group(long id, String name, String photoUrl, String description, int membersCount, String activity) {
            this.id = id;
            this.name = name;
            this.photoUrl = photoUrl;
            this.description = description;
            this.membersCount = membersCount;
            this.activity = activity;
        }
    }

    static class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.GroupViewHolder> {

        interface OnGroupClickListener {
            void onGroupClick(Group group);
        }

        interface SpecialGroupChecker {
            boolean isSpecialGroup(long groupId);
        }

        private List<Group> groups;
        private OnGroupClickListener listener;
        private SpecialGroupChecker specialGroupChecker;

        GroupsAdapter(List<Group> groups, OnGroupClickListener listener, SpecialGroupChecker specialGroupChecker) {
            this.groups = groups;
            this.listener = listener;
            this.specialGroupChecker = specialGroupChecker;
        }

        @NonNull
        @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_group_seacrh, parent, false);
            return new GroupViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
            holder.bind(groups.get(position));
        }

        @Override
        public int getItemCount() {
            return groups.size();
        }

        class GroupViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView;
            TextView activityTextView;
            TextView membersTextView;
            ShapeableImageView photoImageView;
            ImageView verifiedIcon;

            GroupViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.group_name);
                membersTextView = itemView.findViewById(R.id.group_members);
                photoImageView = itemView.findViewById(R.id.group_photo);
                verifiedIcon = itemView.findViewById(R.id.verified_icon);

                itemView.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (listener != null && position != RecyclerView.NO_POSITION) {
                        listener.onGroupClick(groups.get(position));
                    }
                });
            }

            void bind(Group group) {
                nameTextView.setText(group.name);

                if (group.membersCount > 0) {
                    membersTextView.setText(formatMembersCount(group.membersCount));
                    membersTextView.setVisibility(View.VISIBLE);
                } else {
                    membersTextView.setVisibility(View.GONE);
                }

                if (specialGroupChecker != null && specialGroupChecker.isSpecialGroup(group.id)) {
                    verifiedIcon.setVisibility(View.VISIBLE);
                    verifiedIcon.setImageResource(R.drawable.check_verif);
                } else {
                    verifiedIcon.setVisibility(View.GONE);
                }

                Glide.with(itemView.getContext())
                        .load(group.photoUrl)
                        .placeholder(R.drawable.circle_friend)
                        .into(photoImageView);
            }

            private String formatMembersCount(int count) {
                if (count >= 1000000) {
                    return String.format(Locale.getDefault(), "%.1fM участников", count / 1000000.0);
                } else if (count >= 1000) {
                    return String.format(Locale.getDefault(), "%.1fK участников", count / 1000.0);
                } else {
                    return count + " участников";
                }
            }
        }
    }
}