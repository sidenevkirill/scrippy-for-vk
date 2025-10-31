package ru.lisdevs.messenger.groups;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.utils.TokenManager;

public class GroupsFragmentNew extends Fragment {

    private RecyclerView recyclerView;
    private List<Group> groups = new ArrayList<>();
    private GroupsAdapter adapter;
    private String accessToken;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Инициализация accessToken здесь, чтобы был контекст
        accessToken = TokenManager.getInstance(getContext()).getToken();
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_groups, container, false);
        recyclerView = view.findViewById(R.id.groupsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new GroupsAdapter(groups, group -> {
            // Обработка клика по группе
            openGroupDetails(group.id, group.name);
        });
        recyclerView.setAdapter(adapter);

        fetchVKGroups();

        return view;
    }

    private void fetchVKGroups() {
        OkHttpClient client = new OkHttpClient();

        String url = "https://api.vk.com/method/groups.get" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&extended=1";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                // Можно показать ошибку пользователю
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        JSONArray items = jsonObject.getJSONObject("response").getJSONArray("items");
                        groups.clear();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject gObj = items.getJSONObject(i);
                            long id = gObj.getLong("id");
                            String name = gObj.getString("name");
                            String photoUrl = gObj.optString("photo_100", "");
                            groups.add(new Group(id, name, photoUrl));
                        }
                        // Обновляем UI на главном потоке
                        requireActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void openGroupDetails(long groupId, String name) {
        // Передача id группы в другой фрагмент
        Bundle args = new Bundle();
        args.putLong("group_id", groupId);
        args.putString("group_name",name);

        Fragment fragment = new GroupDetailsFragment(); // создайте такой фрагмент
        fragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    // Модель данных группы
    public static class Group {
        public long id;
        public String name;
        public String photoUrl;

        public Group(long id, String name, String photoUrl) {
            this.id = id;
            this.name = name;
            this.photoUrl = photoUrl;
        }
    }

    // Адаптер RecyclerView
    public static class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.GroupViewHolder> {

        interface OnGroupClickListener {
            void onGroupClick(Group group);
        }

        private List<Group> groups;
        private OnGroupClickListener listener;

        public GroupsAdapter(List<Group> groups, OnGroupClickListener listener) {
            this.groups = groups;
            this.listener = listener;
        }

        @NonNull
        @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_group, parent, false);
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
            ShapeableImageView photoImageView;

            public GroupViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.group_name);
                photoImageView = itemView.findViewById(R.id.group_photo);

                itemView.setOnClickListener(v -> {
                    if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                        listener.onGroupClick(groups.get(getAdapterPosition()));
                    }
                });
            }

            void bind(Group group) {
                nameTextView.setText(group.name);
                Glide.with(itemView.getContext())
                        .load(group.photoUrl)
                        .into(photoImageView);
            }
        }
    }

    private void openFriendFragment() {
        // Передача друга в новый фрагмент
        Bundle bundle = new Bundle();

        GroupsFragmentNew detailsFragment = new GroupsFragmentNew();
        detailsFragment.setArguments(bundle);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, detailsFragment)
                .addToBackStack(null)
                .commit();
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_friends, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_show_friends) {
            // Здесь вызываете ваш метод перехода
            // Например, создадим фиктивного друга:
            openFriendFragment();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
