package ru.lisdevs.messenger.playlists;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.utils.TokenManager;

public class MyPlaylistsFragment extends Fragment {

    private static final String API_VERSION = "5.131";
    private String accessToken;

    private RecyclerView recyclerView;
    private PlaylistsAdapter adapter;
    private List<Playlist> playlistList = new ArrayList<>();
    private List<Playlist> filteredList = new ArrayList<>();
    private Toolbar toolbar;
    private SearchView searchView;
    private boolean isSearchMode = false;
    private ProgressBar progressBar;
    private TextView emptyView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        accessToken = TokenManager.getInstance(requireContext()).getToken();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlists, container, false);
        initializeViews(view);
        setupRecyclerView();
        setupToolbar();
        fetchPlaylists();
        return view;
    }

    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        toolbar = view.findViewById(R.id.toolbar);
        progressBar = view.findViewById(R.id.progressBar);
        //emptyView = view.findViewById(R.id.emptyView);
    }

    private void setupRecyclerView() {
        adapter = new PlaylistsAdapter(filteredList, this::navigateToPlaylist);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void navigateToPlaylist(Playlist playlist) {
        Bundle args = new Bundle();
        args.putInt("id", playlist.id);
        args.putLong("ownerId", playlist.ownerId);
        args.putString("title", playlist.title != null ? playlist.title : "");
        args.putString("photoUrl", playlist.getPhotoUrl());
        args.putString("accessKey", playlist.accessKey != null ? playlist.accessKey : "");

        Fragment fragment = new PlaylistPageFragment();
        fragment.setArguments(args);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.playlists_menu_black, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint("Поиск по плейлистам");

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterPlaylists(newText);
                return true;
            }
        });
    }

    private void filterPlaylists(String query) {
        filteredList.clear();

        if (query.isEmpty()) {
            filteredList.addAll(playlistList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (Playlist playlist : playlistList) {
                if (playlist.title.toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(playlist);
                }
            }
        }

      //  updateEmptyView();
        adapter.notifyDataSetChanged();
    }

    private void updateEmptyView() {
        if (filteredList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(isSearchMode ? "Ничего не найдено" : "Нет плейлистов");
        } else {
//            emptyView.setVisibility(View.GONE);
        }
    }

    private void setupToolbar() {
        ((AppCompatActivity)requireActivity()).setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_create_playlist) {
                showCreatePlaylistDialog();
                return true;
            }
            return false;
        });
    }

    private void showCreatePlaylistDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_create_playlist, null);
        dialog.setContentView(dialogView);

        EditText playlistNameEditText = dialogView.findViewById(R.id.playlist_name_edit_text);
        Button createButton = dialogView.findViewById(R.id.create_button);

        createButton.setOnClickListener(v -> {
            String playlistName = playlistNameEditText.getText().toString().trim();
            if (!playlistName.isEmpty()) {
                createPlaylist(playlistName);
                dialog.dismiss();
            } else {
                Toast.makeText(getContext(), "Введите название плейлиста", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void createPlaylist(String title) {
        accessToken = TokenManager.getInstance(getContext()).getToken();
        String ownerId = TokenManager.getInstance(getContext()).getUserId();

        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("access_token", accessToken)
                .add("owner_id", ownerId)
                .add("title", title)
                .add("v", API_VERSION)
                .build();

        Request request = new Request.Builder()
                .url("https://api.vk.com/method/audio.createPlaylist")
                .post(formBody)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showToast("Ошибка сети: " + e.getMessage());
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

                    if (json.has("response")) {
                        handleSuccessResponse(json.getJSONObject("response"));
                    }
                } catch (JSONException e) {
                    showToast("Ошибка обработки ответа");
                }
            }
        });
    }

    private void handleApiError(JSONObject error) throws JSONException {
        String errorMsg = error.getString("error_msg");
        showToast("Ошибка: " + errorMsg);
    }

    private void handleSuccessResponse(JSONObject response) throws JSONException {
        int playlistId = response.getInt("id");
        showToast("Плейлист создан успешно!");
        refreshPlaylists();
    }

    private void refreshPlaylists() {
        requireActivity().runOnUiThread(this::fetchPlaylists);
    }

    private void fetchPlaylists() {
        showLoading(true);

        String url = "https://api.vk.com/method/audio.getPlaylists" +
                "?owner_id=" + TokenManager.getInstance(requireContext()).getUserId() +
                "&access_token=" + accessToken +
                "&v=" + API_VERSION +
                "&extended=1";

        new OkHttpClient().newCall(new Request.Builder()
                .url(url)
                .build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showError("Ошибка загрузки");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    if (json.has("error")) {
                        showError(json.getJSONObject("error").getString("error_msg"));
                        return;
                    }

                    JSONArray items = json.getJSONObject("response").getJSONArray("items");
                    List<Playlist> playlists = new ArrayList<>();

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        Playlist playlist = new Playlist();
                        playlist.id = item.getInt("id");
                        playlist.title = item.getString("title");
                        playlist.ownerId = item.getLong("owner_id");
                        playlist.count = item.getInt("count");
                        playlist.accessKey = item.optString("access_key", "");

                        if (item.has("photo")) {
                            playlist.photo = new Photo();
                            playlist.photo.photo_300 = item.getJSONObject("photo").getString("photo_300");
                        }

                        playlists.add(playlist);
                    }

                    updatePlaylists(playlists);
                } catch (Exception e) {
                    showError("Ошибка обработки данных");
                }
            }
        });
    }

    private void updatePlaylists(List<Playlist> playlists) {
        requireActivity().runOnUiThread(() -> {
            playlistList.clear();
            playlistList.addAll(playlists);

            if (searchView != null && !searchView.getQuery().toString().isEmpty()) {
                filterPlaylists(searchView.getQuery().toString());
            } else {
                filteredList.clear();
                filteredList.addAll(playlistList);
                adapter.notifyDataSetChanged();
            }

            updateEmptyView();
            showLoading(false);
        });
    }

    private void showLoading(boolean isLoading) {
        requireActivity().runOnUiThread(() -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        });
    }

    private void showError(String message) {
        requireActivity().runOnUiThread(() -> {
            showToast(message);
            showLoading(false);
            updateEmptyView();
        });
    }

    private void showToast(String message) {
      //  Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    public static class Playlist {
        public int id;
        public String title;
        public long ownerId;
        public Photo photo;
        public int count;
        public String accessKey;

        public String getPhotoUrl() {
            return photo != null ? photo.photo_300 : "";
        }
    }

    public static class Photo {
        public String photo_300;
    }

    private static class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.ViewHolder> {
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
            holder.title.setText(playlist.title);
            holder.count.setText(String.format(Locale.getDefault(), "%d треков", playlist.count));

            Glide.with(holder.itemView)
                    .load(playlist.getPhotoUrl())
                    .placeholder(R.drawable.music_note_24dp)
                    .into(holder.cover);

            holder.itemView.setOnClickListener(v -> listener.onClick(playlist));
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView cover;
            TextView title;
            TextView count;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                cover = itemView.findViewById(R.id.coverImageView);
                title = itemView.findViewById(R.id.titleText);
                count = itemView.findViewById(R.id.artistText);
            }
        }
    }
}