package ru.lisdevs.messenger.playlists;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.utils.TokenManager;

public class PlaylistsСategoriesFragment extends Fragment {
    private static final String API_VERSION = "5.131";
    private static final int PLAYLISTS_PER_REQUEST = 200;

    public static final Map<String, Integer> PLAYLIST_CATEGORIES = new HashMap<String, Integer>() {{
        put("Мои плейлисты", -1);
        put("Популярное", 613752664);
        put("Выбор редакции", -232021303);
        put("Альбомы", 232021303);
        put("Тематические", 3);
        put("Подкасты", 4);
        put("Для тренировки", 5);
        put("Для работы", 6);
        put("Для учебы", 7);
        put("Для релакса", 8);
        put("Для сна", 9);
        put("Для путешествий", 10);
        put("Для вечеринки", 11);
        put("Для детей", 12);
        put("Классика", 13);
        put("Джаз", 14);
        put("Рок", 15);
        put("Поп", 16);
        put("Хип-хоп", 17);
        put("Электронная", 18);
        put("Метал", 19);
        put("Альтернатива", 20);
        put("Инди", 21);
        put("Регги", 22);
        put("Фонк", 23);
    }};

    private String accessToken;
    private RecyclerView recyclerView;
    private PlaylistsAdapter adapter;
    private List<Playlist> playlistList = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView playlistSelectionText;
    private Toolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        accessToken = TokenManager.getInstance(requireContext()).getToken();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_categories, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        progressBar = view.findViewById(R.id.progressBar);
        recyclerView = view.findViewById(R.id.recyclerView);
        playlistSelectionText = view.findViewById(R.id.playlistCategoryText);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        emptyView = view.findViewById(R.id.emptyView);

        setupToolbar();
        setupRecyclerView();
        setupPlaylistCategorySelection();
        setupSwipeRefresh();
        loadFirstCategoryPlaylists();

        return view;
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setTitle("");
            }
        }
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            String currentCategory = playlistSelectionText.getText().toString();
            Integer categoryId = PLAYLIST_CATEGORIES.get(currentCategory);
            if (categoryId != null) {
                if (categoryId == -1) {
                    fetchMyPlaylists();
                } else {
                    fetchPlaylistsByCategory(categoryId);
                }
            }
        });
    }

    private void loadFirstCategoryPlaylists() {
        playlistSelectionText.setText("Категории");
        fetchMyPlaylists();
    }

    private void setupRecyclerView() {
        adapter = new PlaylistsAdapter(playlistList, this::openPlaylist);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void openPlaylist(Playlist playlist) {
        Fragment fragment = PlaylistPageFragment.newInstance(playlist.id);
        getParentFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void setupPlaylistCategorySelection() {
        playlistSelectionText.setOnClickListener(v -> showCategoryBottomSheet());
    }

    private void showCategoryBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_playlist_categories, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        RecyclerView categoriesRecyclerView = bottomSheetView.findViewById(R.id.categoriesRecyclerView);
        List<String> categoryNames = new ArrayList<>(PLAYLIST_CATEGORIES.keySet());
        Collections.sort(categoryNames);

        PlaylistCategoryAdapter categoryAdapter = new PlaylistCategoryAdapter(categoryNames, category -> {
            playlistSelectionText.setText(category);
            int categoryId = PLAYLIST_CATEGORIES.get(category);
            if (categoryId == -1) {
                fetchMyPlaylists();
            } else {
                fetchPlaylistsByCategory(categoryId);
            }
            bottomSheetDialog.dismiss();
        });

        categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        categoriesRecyclerView.setAdapter(categoryAdapter);
        bottomSheetDialog.show();
    }

    private void fetchMyPlaylists() {
        progressBar.setVisibility(View.VISIBLE);
        playlistList.clear();
        adapter.notifyDataSetChanged();

        String userId = TokenManager.getInstance(requireContext()).getUserId();
        String url = "https://api.vk.com/method/audio.getPlaylists" +
                "?owner_id=" + userId +
                "&access_token=" + accessToken +
                "&count=" + PLAYLISTS_PER_REQUEST +
                "&extended=1" +
                "&v=" + API_VERSION;

        fetchPlaylists(url);
    }

    private void fetchPlaylistsByCategory(int categoryId) {
        progressBar.setVisibility(View.VISIBLE);
        playlistList.clear();
        adapter.notifyDataSetChanged();

        String url = "https://api.vk.com/method/audio.getPlaylists" +
                "?owner_id=" + categoryId +
                "&access_token=" + accessToken +
                "&count=" + PLAYLISTS_PER_REQUEST +
                "&extended=1" +
                "&v=" + API_VERSION;

        if (categoryId != 0) {
            url += "&genre_id=" + categoryId;
        }

        fetchPlaylists(url);
    }

    private void fetchPlaylists(String url) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    updateEmptyView();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("error")) {
                        handleApiError(json.getJSONObject("error"));
                        return;
                    }

                    JSONObject responseObj = json.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    List<Playlist> newPlaylists = new ArrayList<>();

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject playlistObj = items.getJSONObject(i);
                        Playlist playlist = parsePlaylist(playlistObj);
                        newPlaylists.add(playlist);
                    }

                    requireActivity().runOnUiThread(() -> {
                        playlistList.clear();
                        playlistList.addAll(newPlaylists);
                        adapter.notifyDataSetChanged();
                        progressBar.setVisibility(View.GONE);
                        swipeRefreshLayout.setRefreshing(false);
                        updateEmptyView();
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                        updateEmptyView();
                    });
                }
            }
        });
    }

    private void updateEmptyView() {
        emptyView.setVisibility(playlistList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private Playlist parsePlaylist(JSONObject playlistObj) throws JSONException {
        Playlist playlist = new Playlist();
        playlist.id = playlistObj.getInt("id");
        playlist.ownerId = playlistObj.getLong("owner_id");
        playlist.title = playlistObj.getString("title");
        playlist.description = playlistObj.optString("description", "");
        playlist.count = playlistObj.optInt("count", 0);

        if (playlistObj.has("photo")) {
            JSONObject photoObj = playlistObj.getJSONObject("photo");
            playlist.photo = new Photo();
            playlist.photo.photo_300 = photoObj.optString("photo_300", "");
        }

        return playlist;
    }

    private void handleApiError(JSONObject errorObj) {
        String errorMsg = errorObj.optString("error_msg", "Неизвестная ошибка");
        requireActivity().runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(getContext(), "Ошибка API: " + errorMsg, Toast.LENGTH_SHORT).show();
            updateEmptyView();
        });
    }

    static class Playlist {
        int id;
        long ownerId;
        String title;
        String description;
        int count;
        Photo photo;

        String getPhotoUrl() {
            return photo != null ? photo.photo_300 : "";
        }
    }

    static class Photo {
        String photo_300;
    }

    static class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.ViewHolder> {
        private final List<Playlist> playlists;
        private final OnPlaylistClickListener listener;

        interface OnPlaylistClickListener {
            void onClick(Playlist playlist);
        }

        PlaylistsAdapter(List<Playlist> playlists, OnPlaylistClickListener listener) {
            this.playlists = playlists;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlists, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Playlist playlist = playlists.get(position);
            holder.titleText.setText(playlist.title);
            holder.countText.setText(String.format(Locale.getDefault(), "%d треков", playlist.count));

            // Загрузка изображения (используйте Glide/Picasso)
            // Glide.with(holder.itemView.getContext())
            //     .load(playlist.getPhotoUrl())
            //     .into(holder.coverImage);

            holder.itemView.setOnClickListener(v -> listener.onClick(playlist));
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ShapeableImageView coverImage;
            TextView titleText;
            TextView countText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                coverImage = itemView.findViewById(R.id.coverImage);
                titleText = itemView.findViewById(R.id.titleText);
                countText = itemView.findViewById(R.id.artistText);
            }
        }
    }

    static class PlaylistCategoryAdapter extends RecyclerView.Adapter<PlaylistCategoryAdapter.ViewHolder> {
        private final List<String> categories;
        private final OnCategoryClickListener listener;

        interface OnCategoryClickListener {
            void onCategoryClick(String category);
        }

        PlaylistCategoryAdapter(List<String> categories, OnCategoryClickListener listener) {
            this.categories = categories;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist_category, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.categoryText.setText(categories.get(position));
            holder.itemView.setOnClickListener(v ->
                    listener.onCategoryClick(categories.get(position)));
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView categoryText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                categoryText = itemView.findViewById(R.id.categoryText);
            }
        }
    }
}