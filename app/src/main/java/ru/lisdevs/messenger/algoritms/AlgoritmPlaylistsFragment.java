package ru.lisdevs.messenger.algoritms;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.playlists.PlaylistPageFragment;
import ru.lisdevs.messenger.playlists.VkPlaylistsFragment;

public class AlgoritmPlaylistsFragment extends Fragment {

    private RecyclerView recyclerView;
    private PlaylistAdapter adapter;
    private List<Playlist> playlists = new ArrayList<>();
    private Toolbar toolbar;
    private ProgressBar progressBar;
    private TextView errorTextView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private OkHttpClient httpClient;

    // URL вашего JSON файла
    private static final String PLAYLISTS_JSON_URL = "https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/playlists.json";

    public static AlgoritmPlaylistsFragment newInstance() {
        AlgoritmPlaylistsFragment fragment = new AlgoritmPlaylistsFragment();
        Bundle args = new Bundle();

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
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_algoritm_playlists, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        errorTextView = view.findViewById(R.id.errorTextView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        setupToolbar();
        setupRecyclerView();
        setupSwipeRefresh();

        loadPlaylistsFromServer();

        return view;
    }

    private void setupToolbar() {
        toolbar.setNavigationIcon(R.drawable.arrow_left_black);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        toolbar.setTitle("Алгоритмы");
    }

    private void setupRecyclerView() {
        adapter = new PlaylistAdapter(playlists);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(this::openPlaylistFragment);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::loadPlaylistsFromServer);
    }

    private void loadPlaylistsFromServer() {
        showLoading();

        Request request = new Request.Builder()
                .url(PLAYLISTS_JSON_URL)
                .header("User-Agent", "VKAndroidApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    showError("Ошибка загрузки: " + e.getMessage());
                    swipeRefreshLayout.setRefreshing(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    requireActivity().runOnUiThread(() -> {
                        showError("Ошибка сервера: " + response.code());
                        swipeRefreshLayout.setRefreshing(false);
                    });
                    return;
                }

                try {
                    String json = response.body().string();
                    List<Playlist> loadedPlaylists = parsePlaylistsFromJson(json);

                    requireActivity().runOnUiThread(() -> {
                        updatePlaylists(loadedPlaylists);
                        swipeRefreshLayout.setRefreshing(false);
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        showError("Ошибка обработки данных");
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }
        });
    }

    private List<Playlist> parsePlaylistsFromJson(String json) throws JSONException {
        List<Playlist> playlists = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(json);
        JSONArray playlistsArray = jsonObject.getJSONArray("playlists");

        for (int i = 0; i < playlistsArray.length(); i++) {
            JSONObject playlistJson = playlistsArray.getJSONObject(i);

            int id = playlistJson.getInt("id");
            String title = playlistJson.getString("title");
            String iconName = playlistJson.getString("icon_name");
            String tag = playlistJson.getString("tag");
            String description = playlistJson.getString("description");

            // Получаем resource ID по имени иконки
            int iconRes = getIconResourceByName(iconName);

            playlists.add(new Playlist(id, title, iconRes, tag, description));
        }

        return playlists;
    }

    private int getIconResourceByName(String iconName) {
        try {
            // Ищем ресурс по имени в drawable
            return getResources().getIdentifier(iconName, "drawable", requireContext().getPackageName());
        } catch (Exception e) {
            // Возвращаем иконку по умолчанию если не найдена
            return R.drawable.circle_playlist;
        }
    }

    private void updatePlaylists(List<Playlist> newPlaylists) {
        playlists.clear();
        playlists.addAll(newPlaylists);
        adapter.notifyDataSetChanged();

        if (playlists.isEmpty()) {
            showError("Плейлисты не найдены");
        } else {
            showContent();
        }
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        errorTextView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
    }

    private void showContent() {
        progressBar.setVisibility(View.GONE);
        errorTextView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        errorTextView.setText(message);
        errorTextView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    private void openPlaylistFragment(Playlist playlist) {
        if (!isAdded()) return;

        PlaylistPageFragment fragment = PlaylistPageFragment.newInstance(playlist.getId());

        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    // Класс Playlist остается без изменений
    public static class Playlist {
        private int id;
        private String title;
        private int iconRes;
        private String tag;
        private String description;

        public Playlist(int id, String title, int iconRes, String tag, String description) {
            this.id = id;
            this.title = title;
            this.iconRes = iconRes;
            this.tag = tag;
            this.description = description;
        }

        public int getId() { return id; }
        public String getTitle() { return title; }
        public int getIconRes() { return iconRes; }
        public String getTag() { return tag; }
        public String getDescription() { return description; }
    }


    private static class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
        private List<Playlist> playlists;
        private OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(Playlist playlist);
        }

        PlaylistAdapter(List<Playlist> playlists) {
            this.playlists = playlists;
        }

        void setOnItemClickListener(OnItemClickListener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_algoritm, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Playlist playlist = playlists.get(position);
            holder.title.setText(playlist.getTitle());
            holder.description.setText(playlist.getDescription());
            holder.icon.setImageResource(playlist.getIconRes());

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(playlist);
                }
            });
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView description;
            ShapeableImageView icon;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.titleText);
                description = itemView.findViewById(R.id.descriptionText);
                icon = itemView.findViewById(R.id.coverImageView);
            }
        }
    }
}