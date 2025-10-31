package ru.lisdevs.messenger.search;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import okhttp3.*;
import ru.lisdevs.messenger.playlists.AlbumPageFragment;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.utils.TokenManager;

import org.json.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class AlbumSearchFragment extends Fragment {

    private RecyclerView recyclerView;
    private AlbumAdapter adapter;
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
        sharedPreferences = requireContext().getSharedPreferences("SearchHistory", Context.MODE_PRIVATE);
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
                activity.getSupportActionBar().setTitle("Альбомы");
            }
        }

        FloatingActionButton fabArtists = view.findViewById(R.id.fab_go_to_artists);
        fabArtists.setOnClickListener(v -> navigateToArtistSearch());

        initializeViews(view);
        setupRecyclerView();
        loadUserAlbums();
        setupToolbar();

        return view;
    }

    private void setupToolbar() {
        toolbar.setNavigationIcon(R.drawable.arrow_left_black);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        toolbar.setTitle("Альбомы");
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
            searchAlbums(query);
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
        adapter = new AlbumAdapter(new ArrayList<>(), album -> {
            if (!isAdded()) return;

            Fragment fragment = AlbumPageFragment.newInstance(
                    album.id,
                    album.ownerId,
                    album.title,
                    album.artist,
                    album.coverUrl
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

    private void navigateToArtistSearch() {
        try {
            if (!isAdded()) return;

            Fragment artistFragment = new GroupsSearchFragment();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, artistFragment)
                    .addToBackStack("artist_search")
                    .commit();
        } catch (Exception e) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Ошибка перехода", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadUserAlbums() {
        if (!isAdded()) return;

        showLoading();

        String url = "https://api.vk.com/method/audio.getPlaylists" +
                "?access_token=" + accessToken +
                "&owner_id=" + -232021303 +
                "&count=50" +
                "&extended=1" +
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
                    handleAlbumsResponse(response);
                } finally {
                    response.close();
                }
            }
        });
    }

    private void handleAlbumsResponse(Response response) throws IOException {
        if (!isAdded()) return;

        try {
            String responseBody = response.body().string();
            JSONObject json = new JSONObject(responseBody);

            if (json.has("error")) {
                showError("Ошибка API: " + json.getJSONObject("error").getString("error_msg"));
                return;
            }

            JSONObject responseObj = json.getJSONObject("response");
            List<VKAlbum> albums = parseAlbums(responseObj);
            updateUI(albums);

        } catch (JSONException e) {
            showError("Ошибка обработки данных: " + e.getMessage());
        }
    }

    private List<VKAlbum> parseAlbums(JSONObject response) throws JSONException {
        List<VKAlbum> albums = new ArrayList<>();
        if (!response.has("items")) return albums;

        JSONArray items = response.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject albumObj = items.getJSONObject(i);

            String coverUrl = null;
            String artist = "Неизвестный исполнитель";

            if (albumObj.has("photo")) {
                JSONObject photo = albumObj.getJSONObject("photo");
                if (photo.has("photo_300")) {
                    coverUrl = photo.getString("photo_300");
                } else if (photo.has("photo_270")) {
                    coverUrl = photo.getString("photo_270");
                }
            }

            if (albumObj.has("main_artists")) {
                JSONArray artists = albumObj.getJSONArray("main_artists");
                if (artists.length() > 0) {
                    artist = artists.getJSONObject(0).getString("name");
                }
            } else if (albumObj.has("artist")) {
                artist = albumObj.getString("artist");
            }

            albums.add(new VKAlbum(
                    albumObj.getInt("id"),
                    albumObj.getInt("owner_id"),
                    albumObj.getString("title"),
                    artist,
                    coverUrl,
                    albumObj.optString("access_key", "")
            ));
        }
        return albums;
    }

    private void performSearch() {
        String query = searchInput.getText().toString().trim();
        if (!query.isEmpty()) {
            searchAlbums(query);
            addToSearchHistory(query);
        }
    }

    private void searchAlbums(String query) {
        hideKeyboard();
        if (query.isEmpty()) {
            loadUserAlbums();
            return;
        }

        if (!isAdded()) return;

        showLoading();

        String encodedQuery = Uri.encode(query);
        String url = "https://api.vk.com/method/audio.searchAlbums" +
                "?access_token=" + accessToken +
                "&q=" + encodedQuery +
                "&count=50" +
                "&extended=1" +
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
            List<VKAlbum> albums = parseAlbums(responseObj);
            updateUI(albums);

        } catch (JSONException e) {
            showError("Ошибка обработки данных: " + e.getMessage());
        }
    }

    private void updateUI(List<VKAlbum> albums) {
        if (!isAdded() || getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (albums.isEmpty()) {
                showEmptyView("Альбомы не найдены");
            } else {
                showResults(albums);
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

    private void showResults(List<VKAlbum> albums) {
        if (!isAdded()) return;

        progressBar.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        adapter.updateData(albums);
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
        String history = sharedPreferences.getString("album_search_history", "");
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
                    .putString("album_search_history", TextUtils.join(",", searchHistory))
                    .apply();

            ArrayAdapter<String> adapter = (ArrayAdapter<String>) searchInput.getAdapter();
            if (adapter != null) {
                adapter.clear();
                adapter.addAll(searchHistory);
                adapter.notifyDataSetChanged();
            }
        }
    }

    public static class VKAlbum {
        public final int id;
        public final int ownerId;
        public final String title;
        public final String artist;
        public final String coverUrl;
        public final String accessKey;

        public VKAlbum(int id, int ownerId, String title, String artist, String coverUrl, String accessKey) {
            this.id = id;
            this.ownerId = ownerId;
            this.title = title;
            this.artist = artist;
            this.coverUrl = coverUrl;
            this.accessKey = accessKey;
        }
    }

    private static class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.ViewHolder> {
        private List<VKAlbum> albums;
        private final OnAlbumClickListener listener;
        private Context context;

        interface OnAlbumClickListener {
            void onAlbumClick(VKAlbum album);
        }

        AlbumAdapter(List<VKAlbum> albums, OnAlbumClickListener listener) {
            this.albums = albums;
            this.listener = listener;
        }

        void updateData(List<VKAlbum> newAlbums) {
            this.albums = newAlbums;
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
            VKAlbum album = albums.get(position);
            holder.bind(album);
        }

        @Override
        public int getItemCount() {
            return albums.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView coverImageView;
            private final TextView titleTextView;
            private final TextView artistTextView;
            private final ImageView menuButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                coverImageView = itemView.findViewById(R.id.coverImageView);
                titleTextView = itemView.findViewById(R.id.titleText);
                artistTextView = itemView.findViewById(R.id.artistText);
                menuButton = itemView.findViewById(R.id.downloadButton);
            }

            void bind(VKAlbum album) {
                titleTextView.setText(album.title);
                artistTextView.setText(album.artist);

                Glide.with(itemView.getContext())
                        .load(album.coverUrl)
                        .placeholder(R.drawable.circle_playlist)
                        .error(R.drawable.circle_playlist)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(coverImageView);

                itemView.setOnClickListener(v -> listener.onAlbumClick(album));
                menuButton.setOnClickListener(v -> showBottomSheetMenu(album));
            }

            private void showBottomSheetMenu(VKAlbum album) {
                BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
                View bottomSheetView = LayoutInflater.from(context)
                        .inflate(R.layout.bottom_sheet_album_menu, null);
                bottomSheetDialog.setContentView(bottomSheetView);

                TextView copyLink = bottomSheetView.findViewById(R.id.copy_link);
                TextView addToMyAlbums = bottomSheetView.findViewById(R.id.add_to_my_albums);
                TextView downloadAlbum = bottomSheetView.findViewById(R.id.down_playlists);

                copyLink.setOnClickListener(v -> {
                    copyAlbumLink(album);
                    bottomSheetDialog.dismiss();
                    Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show();
                });

                addToMyAlbums.setOnClickListener(v -> {
                    addAlbumToMyCollection(album);
                    bottomSheetDialog.dismiss();
                    Toast.makeText(context, "Альбом добавлен в вашу коллекцию", Toast.LENGTH_SHORT).show();
                });

                downloadAlbum.setOnClickListener(v -> {
                    downloadAlbum(album);
                    bottomSheetDialog.dismiss();
                });

                bottomSheetDialog.show();
            }

            private void copyAlbumLink(VKAlbum album) {
                String link = "https://vk.com/music/album/" + album.ownerId + "_" + album.id + "_" + album.accessKey;
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Album Link", link);
                clipboard.setPrimaryClip(clip);
            }

            private void addAlbumToMyCollection(VKAlbum album) {
                String accessToken = TokenManager.getInstance(context).getToken();
                OkHttpClient client = new OkHttpClient();
                String url = "https://api.vk.com/method/audio.followPlaylist" +
                        "?access_token=" + accessToken +
                        "&owner_id=" + album.ownerId +
                        "&playlist_id=" + album.id +
                        "&v=5.95";

                if (!album.accessKey.isEmpty()) {
                    url += "&access_key=" + album.accessKey;
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
                                showSuccessToast("Альбом успешно добавлен");
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

            private void downloadAlbum(VKAlbum album) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions((Activity) context,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                    return;
                }

                showProgressToast("Начинаем загрузку альбома...");

                String accessToken = TokenManager.getInstance(context).getToken();
                String url = "https://api.vk.com/method/audio.get" +
                        "?access_token=" + accessToken +
                        "&owner_id=" + album.ownerId +
                        "&album_id=" + album.id +
                        "&count=1000" +
                        "&v=5.131";

                if (!album.accessKey.isEmpty()) {
                    url += "&access_key=" + album.accessKey;
                }

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", Authorizer.getKateUserAgent())
                        .build();

                new OkHttpClient().newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        showErrorToast("Ошибка при получении треков: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("error")) {
                                String errorMsg = json.getJSONObject("error").getString("error_msg");
                                showErrorToast("Ошибка VK: " + errorMsg);
                                return;
                            }

                            JSONArray items = json.getJSONObject("response").getJSONArray("items");
                            if (items.length() == 0) {
                                showErrorToast("В альбоме нет треков");
                                return;
                            }

                            String albumFolderName = sanitizeFileName(album.artist + " - " + album.title);
                            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                            File albumDir = new File(musicDir, albumFolderName);

                            if (!albumDir.exists()) {
                                albumDir.mkdirs();
                            }

                            for (int i = 0; i < items.length(); i++) {
                                JSONObject track = items.getJSONObject(i);
                                String trackUrl = track.getString("url");
                                String trackTitle = track.getString("title");
                                String trackArtist = track.getString("artist");

                                String fileName = sanitizeFileName(trackArtist + " - " + trackTitle + ".mp3");
                                File outputFile = new File(albumDir, fileName);

                                downloadTrack(trackUrl, outputFile, i, items.length());
                            }

                        } catch (Exception e) {
                            showErrorToast("Ошибка обработки треков: " + e.getMessage());
                        } finally {
                            response.close();
                        }
                    }
                });
            }

            private void downloadTrack(String url, File outputFile, int current, int total) {
                Request request = new Request.Builder().url(url).build();

                new OkHttpClient().newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        showErrorToast("Ошибка загрузки трека " + (current + 1));
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try (InputStream inputStream = response.body().byteStream();
                             FileOutputStream outputStream = new FileOutputStream(outputFile)) {

                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }

                            String message = "Загружен трек " + (current + 1) + " из " + total;
                            if (current + 1 == total) {
                                message = "Альбом загружен полностью!";
                            }
                            showProgressToast(message);

                        } catch (Exception e) {
                            showErrorToast("Ошибка сохранения трека " + (current + 1));
                        } finally {
                            response.close();
                        }
                    }
                });
            }

            private String sanitizeFileName(String name) {
                return name.replaceAll("[\\\\/:*?\"<>|]", "_");
            }

            private void showProgressToast(String message) {
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
                }
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