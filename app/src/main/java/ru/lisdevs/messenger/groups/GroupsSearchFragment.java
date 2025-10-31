package ru.lisdevs.messenger.groups;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
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
import ru.lisdevs.messenger.utils.TokenManager;

public class GroupsSearchFragment extends Fragment {

    private RecyclerView recyclerView;
    private List<Group> groups = new ArrayList<>();
    private List<Group> filteredGroups = new ArrayList<>();
    private GroupsAdapter adapter;
    private String accessToken;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyView;
    private androidx.appcompat.widget.SearchView searchView; // Правильный тип
    private MaterialToolbar toolbar;
    private OkHttpClient client;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        accessToken = TokenManager.getInstance(requireContext()).getToken();
        client = new OkHttpClient();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_groups, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerView = view.findViewById(R.id.groupsRecyclerView);
        emptyView = view.findViewById(R.id.emptyView);

        setupToolbar(); // Настройка SearchView через меню
        setupRecyclerView();
        setupSwipeRefresh();

        return view;
    }

    private void setupToolbar() {
        //toolbar.setNavigationIcon(R.drawable.arrow_left);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        toolbar.setTitle("Мои группы");

        // Настройка SearchView через меню
      //  toolbar.inflateMenu(R.menu.search);

        MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        searchView = (androidx.appcompat.widget.SearchView) searchItem.getActionView();

        setupSearchView();
    }

    private void setupSearchView() {
        if (searchView == null) return;

        searchView.setQueryHint("Поиск групп...");
        searchView.setMaxWidth(Integer.MAX_VALUE);

        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterGroups(query);
                hideKeyboard();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterGroups(newText);
                return true;
            }
        });

        // Обработка закрытия поиска
        searchView.setOnCloseListener(() -> {
            showAllGroups();
            return false;
        });

        // Правильная обработка расширения/сворачивания через MenuItem
        MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true; // Разрешить расширение
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                showAllGroups();
                return true; // Разрешить сворачивание
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new GroupsAdapter(filteredGroups, group -> {
            openGroupDetails(group.id, group.name);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::fetchVKGroups);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (groups.isEmpty()) {
            fetchVKGroups();
        }
    }

    private void filterGroups(String query) {
        if (query == null || query.trim().isEmpty()) {
            showAllGroups();
            return;
        }

        String searchQuery = query.toLowerCase().trim();
        filteredGroups.clear();

        for (Group group : groups) {
            if (group.name.toLowerCase().contains(searchQuery)) {
                filteredGroups.add(group);
            }
        }

        adapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void showAllGroups() {
        filteredGroups.clear();
        filteredGroups.addAll(groups);
        adapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (filteredGroups.isEmpty()) {
            if (groups.isEmpty()) {
                emptyView.setText("Группы не найдены");
            } else {
                emptyView.setText("Ничего не найдено");
            }
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void fetchVKGroups() {
        if (accessToken == null || accessToken.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            showError("Требуется авторизация");
            return;
        }

        String url = "https://api.vk.com/method/groups.get" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&extended=1" +
                "&fields=photo_100,name,members_count,description" +
                "&count=1000";

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
                    JSONArray items = responseObj.getJSONArray("items");
                    List<Group> newGroups = new ArrayList<>();

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject gObj = items.getJSONObject(i);
                        long id = gObj.getLong("id");
                        String name = gObj.getString("name");
                        String photoUrl = gObj.optString("photo_100", "");
                        String description = gObj.optString("description", "");
                        int membersCount = gObj.optInt("members_count", 0);

                        newGroups.add(new Group(id, name, photoUrl, description, membersCount));
                    }

                    requireActivity().runOnUiThread(() -> {
                        updateGroupsList(newGroups);
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

    private void updateGroupsList(List<Group> newGroups) {
        groups.clear();
        groups.addAll(newGroups);

        // Обновляем отфильтрованный список
        if (searchView != null) {
            String currentQuery = searchView.getQuery().toString();
            if (currentQuery.isEmpty()) {
                showAllGroups();
            } else {
                filterGroups(currentQuery);
            }
        }
    }

    private void handleApiError(JSONObject errorObj) {
        String errorMsg = errorObj.optString("error_msg", "Ошибка API");
        int errorCode = errorObj.optInt("error_code", 0);

        requireActivity().runOnUiThread(() -> {
            swipeRefreshLayout.setRefreshing(false);
            showError(errorMsg + " (код: " + errorCode + ")");
        });
    }

    private void showError(String message) {
        emptyView.setText(message);
        emptyView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    private void hideKeyboard() {
        if (getActivity() != null && searchView != null) {
            InputMethodManager imm = (InputMethodManager) getActivity()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
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

    // Остальной код классов Group и GroupsAdapter остается без изменений
    static class Group {
        long id;
        String name;
        String photoUrl;
        String description;
        int membersCount;

        Group(long id, String name, String photoUrl, String description, int membersCount) {
            this.id = id;
            this.name = name;
            this.photoUrl = photoUrl;
            this.description = description;
            this.membersCount = membersCount;
        }
    }

    static class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.GroupViewHolder> {

        interface OnGroupClickListener {
            void onGroupClick(Group group);
        }

        private List<Group> groups;
        private OnGroupClickListener listener;

        GroupsAdapter(List<Group> groups, OnGroupClickListener listener) {
            this.groups = groups;
            this.listener = listener;
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
          //  TextView descriptionTextView;
            TextView membersTextView;
            ShapeableImageView photoImageView;

            GroupViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.group_name);
              //  descriptionTextView = itemView.findViewById(R.id.group_description);
                membersTextView = itemView.findViewById(R.id.group_members);
                photoImageView = itemView.findViewById(R.id.group_photo);

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

                Glide.with(itemView.getContext())
                        .load(group.photoUrl)
                        .placeholder(R.drawable.group_24px)
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
