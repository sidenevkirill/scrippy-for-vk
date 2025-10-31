package ru.lisdevs.messenger.search;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.playlists.AlbumPageFragment;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.utils.TokenManager;

public class PlaylistSearchFragment extends Fragment {

    private RecyclerView recyclerView;
    private PlaylistAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private AutoCompleteTextView searchInput;
    private ImageView searchButton;
    private List<String> searchHistory = new ArrayList<>();
    private SharedPreferences sharedPreferences;
    private String accessToken;
    private OkHttpClient client;
    private Call currentCall;
    private Toolbar toolbar;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        accessToken = TokenManager.getInstance(requireContext()).getToken();
        sharedPreferences = requireContext().getSharedPreferences("PlaylistSearchHistory", Context.MODE_PRIVATE);
        client = new OkHttpClient();
        loadSearchHistory();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_album_search, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                activity.getSupportActionBar().setTitle("Поиск плейлистов");
            }
        }

        initializeViews(view);
        setupRecyclerView();
        setupToolbar();

        return view;
    }

    private void setupToolbar() {
        toolbar.setNavigationIcon(R.drawable.arrow_left_black);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        toolbar.setTitle("Поиск плейлистов");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }
    }

    private void initializeViews(View view) {
        searchInput = view.findViewById(R.id.editTextSearch);
        searchButton = view.findViewById(R.id.buttonSearch);
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyView = view.findViewById(R.id.emptyView);

        ArrayAdapter<String> historyAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                searchHistory
        );
        searchInput.setAdapter(historyAdapter);
        searchInput.setThreshold(1);
        searchInput.setOnItemClickListener((parent, view1, position, id) -> {
            String query = (String) parent.getItemAtPosition(position);
            searchPlaylists(query);
        });

        searchButton.setOnClickListener(v -> performSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PlaylistAdapter(new ArrayList<>(), playlist -> {
            if (!isAdded()) return;

            Fragment fragment = AlbumPageFragment.newInstance(
                    playlist.id,
                    playlist.ownerId,
                    playlist.title,
                    playlist.coverUrl,
                    playlist.accessKey
            );

            getParentFragmentManager().beginTransaction()
                    .setCustomAnimations(
                            R.anim.slide_in_right,
                            R.anim.slide_out_left,
                            R.anim.slide_in_left,
                            R.anim.slide_out_right
                    )
                    .replace(R.id.container, fragment)
                    .addToBackStack(null)
                    .commit();
        });
        recyclerView.setAdapter(adapter);
    }

    private void performSearch() {
        String query = searchInput.getText().toString().trim();
        if (!query.isEmpty()) {
            searchPlaylists(query);
            addToSearchHistory(query);
        }
    }

    private void searchPlaylists(String query) {
        hideKeyboard();
        if (query.isEmpty()) {
            showEmptyView("Введите запрос для поиска");
            return;
        }

        if (!isAdded()) return;

        showLoading();

        String encodedQuery = Uri.encode(query);
        String url = "https://api.vk.com/method/audio.searchPlaylists" +
                "?access_token=" + accessToken +
                "&q=" + encodedQuery +
                "&count=50" +
                "&filters=playlists" +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        currentCall = client.newCall(request);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled() || !isAdded()) return;
                showError("Ошибка соединения: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (call.isCanceled() || !isAdded()) {
                    response.close();
                    return;
                }

                try {
                    handleSearchResponse(response);
                } finally {
                    response.close();
                }
            }
        });
    }

    private void handleSearchResponse(Response response) throws IOException {
        if (!isAdded()) return;

        try {
            String responseBody = response.body().string();
            JSONObject json = new JSONObject(responseBody);

            if (json.has("error")) {
                showError("Ошибка API: " + json.getJSONObject("error").getString("error_msg"));
                return;
            }

            JSONObject responseObj = json.getJSONObject("response");
            List<VKPlaylist> playlists = parsePlaylists(responseObj);
            updateUI(playlists);

        } catch (JSONException e) {
            showError("Ошибка обработки данных: " + e.getMessage());
        }
    }

    private List<VKPlaylist> parsePlaylists(JSONObject response) throws JSONException {
        List<VKPlaylist> playlists = new ArrayList<>();
        if (!response.has("items")) return playlists;

        JSONArray items = response.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject playlistObj = items.getJSONObject(i);

            String coverUrl = null;
            if (playlistObj.has("photo")) {
                JSONObject photo = playlistObj.getJSONObject("photo");
                if (photo.has("photo_300")) {
                    coverUrl = photo.getString("photo_300");
                } else if (photo.has("photo_270")) {
                    coverUrl = photo.getString("photo_270");
                }
            }

            playlists.add(new VKPlaylist(
                    playlistObj.getInt("id"),
                    playlistObj.getInt("owner_id"),
                    playlistObj.getString("title"),
                    coverUrl,
                    playlistObj.optString("access_key", "")
            ));
        }
        return playlists;
    }

    private void updateUI(List<VKPlaylist> playlists) {
        if (!isAdded() || getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (playlists.isEmpty()) {
                showEmptyView("Плейлисты не найдены");
            } else {
                showResults(playlists);
            }
        });
    }

    private void hideKeyboard() {
        if (!isAdded()) return;

        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    private void showLoading() {
        if (!isAdded()) return;

        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
    }

    private void showResults(List<VKPlaylist> playlists) {
        if (!isAdded()) return;

        progressBar.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        adapter.updateData(playlists);
    }

    private void showEmptyView(String message) {
        if (!isAdded()) return;

        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(message);
    }

    private void showError(String message) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> showEmptyView(message));
    }

    private void loadSearchHistory() {
        String history = sharedPreferences.getString("playlist_search_history", "");
        if (!history.isEmpty()) {
            searchHistory = new ArrayList<>(Arrays.asList(history.split(",")));
        }
    }

    private void addToSearchHistory(String query) {
        if (!searchHistory.contains(query)) {
            searchHistory.add(0, query);
            if (searchHistory.size() > 5) {
                searchHistory = searchHistory.subList(0, 5);
            }

            sharedPreferences.edit()
                    .putString("playlist_search_history", TextUtils.join(",", searchHistory))
                    .apply();

            ArrayAdapter<String> adapter = (ArrayAdapter<String>) searchInput.getAdapter();
            if (adapter != null) {
                adapter.clear();
                adapter.addAll(searchHistory);
                adapter.notifyDataSetChanged();
            }
        }
    }

    public static class VKPlaylist {
        public final int id;
        public final int ownerId;
        public final String title;
        public final String coverUrl;
        public final String accessKey;

        public VKPlaylist(int id, int ownerId, String title, String coverUrl, String accessKey) {
            this.id = id;
            this.ownerId = ownerId;
            this.title = title;
            this.coverUrl = coverUrl;
            this.accessKey = accessKey;
        }
    }

    private static class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
        private List<VKPlaylist> playlists;
        private final OnPlaylistClickListener listener;
        private Context context;

        interface OnPlaylistClickListener {
            void onPlaylistClick(VKPlaylist playlist);
        }

        PlaylistAdapter(List<VKPlaylist> playlists, OnPlaylistClickListener listener) {
            this.playlists = playlists;
            this.listener = listener;
        }

        void updateData(List<VKPlaylist> newPlaylists) {
            this.playlists = newPlaylists;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            context = parent.getContext();
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlists, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VKPlaylist playlist = playlists.get(position);
            holder.bind(playlist);
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView coverImageView;
            private final TextView titleTextView;
            private final ImageView menuButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                coverImageView = itemView.findViewById(R.id.coverImageView);
                titleTextView = itemView.findViewById(R.id.titleText);
                menuButton = itemView.findViewById(R.id.menu_button);
            }

            void bind(VKPlaylist playlist) {
                titleTextView.setText(playlist.title);

                Glide.with(itemView.getContext())
                        .load(playlist.coverUrl)
                        .placeholder(R.drawable.circle_playlist)
                        .error(R.drawable.circle_playlist)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(coverImageView);

                itemView.setOnClickListener(v -> listener.onPlaylistClick(playlist));
                menuButton.setOnClickListener(v -> showBottomSheetMenu(playlist));
            }

            private void showBottomSheetMenu(VKPlaylist playlist) {
                BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
                View bottomSheetView = LayoutInflater.from(context)
                        .inflate(R.layout.bottom_sheet_album_menu, null);
                bottomSheetDialog.setContentView(bottomSheetView);

                TextView copyLink = bottomSheetView.findViewById(R.id.copy_link);
                TextView addToMyPlaylists = bottomSheetView.findViewById(R.id.add_to_my_albums);

                copyLink.setOnClickListener(v -> {
                    copyPlaylistLink(playlist);
                    bottomSheetDialog.dismiss();
                    Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show();
                });

                addToMyPlaylists.setOnClickListener(v -> {
                    addPlaylistToMyCollection(playlist);
                    bottomSheetDialog.dismiss();
                    Toast.makeText(context, "Плейлист добавлен в вашу коллекцию", Toast.LENGTH_SHORT).show();
                });

                bottomSheetDialog.show();
            }

            private void copyPlaylistLink(VKPlaylist playlist) {
                String link = "https://vk.com/music/playlist/" + playlist.ownerId + "_" + playlist.id;
                if (!playlist.accessKey.isEmpty()) {
                    link += "_" + playlist.accessKey;
                }

                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Playlist Link", link);
                clipboard.setPrimaryClip(clip);
            }

            private void addPlaylistToMyCollection(VKPlaylist playlist) {
                String accessToken = TokenManager.getInstance(context).getToken();
                OkHttpClient client = new OkHttpClient();
                String url = "https://api.vk.com/method/audio.followPlaylist" +
                        "?access_token=" + accessToken +
                        "&owner_id=" + playlist.ownerId +
                        "&playlist_id=" + playlist.id +
                        "&v=5.95";

                if (!playlist.accessKey.isEmpty()) {
                    url += "&access_key=" + playlist.accessKey;
                }

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", Authorizer.getKateUserAgent())
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        showErrorToast("Ошибка соединения: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("error")) {
                                JSONObject error = json.getJSONObject("error");
                                String errorMsg = error.getString("error_msg");
                                showErrorToast("Ошибка VK: " + errorMsg);
                                return;
                            }

                            if (json.has("response")) {
                                showSuccessToast("Плейлист успешно добавлен");
                            } else {
                                showErrorToast("Неизвестный ответ от сервера");
                            }
                        } catch (Exception e) {
                            showErrorToast("Ошибка обработки ответа");
                        } finally {
                            response.close();
                        }
                    }
                });
            }

            private void showErrorToast(String message) {
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
                }
            }

            private void showSuccessToast(String message) {
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
                }
            }
        }
    }
}