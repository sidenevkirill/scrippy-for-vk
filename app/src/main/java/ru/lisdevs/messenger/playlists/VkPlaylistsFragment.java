package ru.lisdevs.messenger.playlists;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.search.MusicSearchFragment;
import ru.lisdevs.messenger.utils.TokenManager;

import android.widget.ImageView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;

import android.Manifest;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import ru.lisdevs.messenger.utils.Util;

import java.util.Map;

public class VkPlaylistsFragment extends Fragment {
    private static final String API_VERSION = "5.131";
    private static final int PLAYLISTS_PER_REQUEST = 200;
    private static final int PICK_IMAGE_REQUEST = 101;
    private static final int REQUEST_READ_STORAGE = 102;
    private static final int REQUEST_WRITE_STORAGE = 103;
    private String accessToken;

    // UI элементы
    private RecyclerView recyclerView;
    private PlaylistsAdapter adapter;
    private Toolbar toolbar;
    private SearchView searchView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView textViewAlbumsCount;
    private ImageView sortButton;

    // Данные
    private List<Playlist> playlistList = new ArrayList<>();
    private List<Playlist> filteredPlaylistList = new ArrayList<>();
    private List<Audio> fullAudioList = new ArrayList<>();

    // Пагинация
    private int currentOffset = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;

    // Для создания/редактирования плейлиста
    private Uri selectedImageUri;
    private String playlistDescription = "";

    // Режимы сортировки
    private static final int SORT_DEFAULT = 0;
    private static final int SORT_TITLE_ASC = 1;
    private static final int SORT_TITLE_DESC = 2;
    private static final int SORT_COUNT_ASC = 3;
    private static final int SORT_COUNT_DESC = 4;
    private static final int SORT_DATE_ASC = 5;
    private static final int SORT_DATE_DESC = 6;

    private int currentSortMode = SORT_DEFAULT;

    public static VkPlaylistsFragment newInstance() {
        VkPlaylistsFragment fragment = new VkPlaylistsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        accessToken = TokenManager.getInstance(requireContext()).getToken();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlists_view, container, false);
        setupViews(view);
        setupRecyclerView();
        setupToolbar();
        setupSwipeRefresh();
        setupSortButton();
        fetchPlaylists();
        loadUserAudio();

        return view;
    }

    private void setupViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        toolbar = view.findViewById(R.id.toolbar);
        progressBar = view.findViewById(R.id.progressBar);
        emptyView = view.findViewById(R.id.emptyStateText);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        textViewAlbumsCount = view.findViewById(R.id.count);
        sortButton = view.findViewById(R.id.btnCreateGroupChat);
    }

    private void setupSortButton() {
        if (sortButton != null) {
            sortButton.setOnClickListener(v -> showSortDialog());
            sortButton.setVisibility(View.VISIBLE);
        }
    }

    private void showSortDialog() {
        String[] sortOptions = {
                "По умолчанию",
                "По названию (А-Я)",
                "По названию (Я-А)",
                "По количеству треков (↑)",
                "По количеству треков (↓)",
        };

        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
                .setTitle("Сортировка плейлистов")
                .setSingleChoiceItems(sortOptions, currentSortMode, (dialog, which) -> {
                    currentSortMode = which;
                    applySorting();
                    dialog.dismiss();
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("ОК", null)
                .show();
    }

    private void applySorting() {
        if (filteredPlaylistList.isEmpty()) return;

        List<Playlist> sortedList = new ArrayList<>(filteredPlaylistList);

        switch (currentSortMode) {
            case SORT_DEFAULT:
                // Оставляем исходный порядок
                break;

            case SORT_TITLE_ASC:
                Collections.sort(sortedList, (p1, p2) -> p1.title.compareToIgnoreCase(p2.title));
                break;

            case SORT_TITLE_DESC:
                Collections.sort(sortedList, (p1, p2) -> p2.title.compareToIgnoreCase(p1.title));
                break;

            case SORT_COUNT_ASC:
                Collections.sort(sortedList, (p1, p2) -> Integer.compare(p1.count, p2.count));
                break;

            case SORT_COUNT_DESC:
                Collections.sort(sortedList, (p1, p2) -> Integer.compare(p2.count, p1.count));
                break;

            case SORT_DATE_ASC:
                // Если есть дата создания, можно добавить сортировку по дате
                // Collections.sort(sortedList, (p1, p2) -> Long.compare(p1.createTime, p2.createTime));
                break;

            case SORT_DATE_DESC:
                // Collections.sort(sortedList, (p1, p2) -> Long.compare(p2.createTime, p1.createTime));
                break;
        }

        filteredPlaylistList.clear();
        filteredPlaylistList.addAll(sortedList);
        adapter.notifyDataSetChanged();
        updateAlbumsCountText();

        String sortMessage = getSortMessage();
        //Toast.makeText(getContext(), sortMessage, Toast.LENGTH_SHORT).show();
    }

    private String getSortMessage() {
        switch (currentSortMode) {
            case SORT_DEFAULT:
                return "Сортировка по умолчанию";
            case SORT_TITLE_ASC:
                return "Сортировка по названию (А-Я)";
            case SORT_TITLE_DESC:
                return "Сортировка по названию (Я-А)";
            case SORT_COUNT_ASC:
                return "Сортировка по количеству треков (возрастание)";
            case SORT_COUNT_DESC:
                return "Сортировка по количеству треков (убывание)";
            default:
                return "Сортировка применена";
        }
    }

    private void updateAlbumsCountText() {
        if (textViewAlbumsCount != null) {
            String countText;
            if (filteredPlaylistList.isEmpty()) {
                countText = "Нет плейлистов";
            } else {
                int totalTracks = 0;
                for (Playlist playlist : filteredPlaylistList) {
                    totalTracks += playlist.count;
                }

                countText = String.format("Плейлисты: %d",
                        filteredPlaylistList.size());
            }
            textViewAlbumsCount.setText(countText);
        }
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            currentOffset = 0;
            hasMore = true;
            playlistList.clear();
            fetchPlaylists();
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PlaylistsAdapter(filteredPlaylistList,
                this::openPlaylist,
                this::showBottomSheetMenu);
        setupPagination();
        recyclerView.setAdapter(adapter);
    }

    private void setupPagination() {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                int totalItems = layoutManager.getItemCount();

                if (!isLoading && hasMore && lastVisibleItem >= totalItems - 5) {
                    loadMorePlaylists();
                }
            }
        });
    }

    private void loadUserAudio() {
        String userId = TokenManager.getInstance(requireContext()).getUserId();
        String url = "https://api.vk.com/method/audio.get" +
                "?owner_id=" + userId +
                "&access_token=" + accessToken +
                "&count=6000" +
                "&v=" + API_VERSION;

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("VkPlaylistsFragment", "Ошибка загрузки аудио", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String json = response.body().string();
                    JSONObject jsonObject = new JSONObject(json);

                    if (jsonObject.has("error")) {
                        Log.e("VkPlaylistsFragment", "Ошибка API: " + jsonObject.getJSONObject("error").getString("error_msg"));
                        return;
                    }

                    JSONArray items = jsonObject.getJSONObject("response").getJSONArray("items");
                    fullAudioList.clear();

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        Audio audio = new Audio(
                                item.getLong("id"),
                                item.getLong("owner_id"),
                                item.getString("artist"),
                                item.getString("title"),
                                item.getInt("duration"),
                                item.getString("url"),
                                item.optLong("date", 0),
                                item.optLong("album_id", 0),
                                item.optString("genre_id", "0")
                        );
                        fullAudioList.add(audio);
                    }

                } catch (Exception e) {
                    Log.e("VkPlaylistsFragment", "Ошибка парсинга аудио", e);
                } finally {
                    response.close();
                }
            }
        });
    }

    // Метод для определения User-Agent
    private String getUserAgent() {
        if (isAuthViaAuthActivity()) {
            return "VKAndroidApp/1.0";
        } else {
            try {
                return Authorizer.getKateUserAgent();
            } catch (Exception e) {
                // Fallback на стандартный User-Agent
                return "VKAndroidApp/1.0";
            }
        }
    }

    // Метод для определения типа авторизации
    private boolean isAuthViaAuthActivity() {
        // Проверка через SharedPreferences
        SharedPreferences prefs = requireContext().getSharedPreferences("auth_prefs", Context.MODE_PRIVATE);
        String authType = prefs.getString("auth_type", null);

        if (authType != null) {
            return "AuthActivity".equals(authType);
        }

        // По умолчанию возвращаем true для совместимости
        return true;
    }


    private void openPlaylist(Playlist playlist) {
        if (!isAdded()) return;

        Fragment fragment = PlaylistPageFragment.newInstance(playlist.id);

        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void showBottomSheetMenu(Playlist playlist) {
        if (!isAdded() || getActivity() == null) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_album_menu, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        TextView copyLink = bottomSheetView.findViewById(R.id.copy_link);
        TextView deletePlaylist = bottomSheetView.findViewById(R.id.add_to_my_albums);
        TextView editPlaylist = bottomSheetView.findViewById(R.id.edit_playlists);
        TextView sharePlaylist = bottomSheetView.findViewById(R.id.share_playlists);
        TextView downPlaylist = bottomSheetView.findViewById(R.id.down_playlists);

        setMenuItem(copyLink, "Скопировать", R.drawable.content_copy);
        setMenuItem(deletePlaylist, "Удалить", R.drawable.delete);
        setMenuItem(editPlaylist, "Изменить", R.drawable.edit_24px);
        setMenuItem(sharePlaylist, "Поделиться", R.drawable.share_black);
        setMenuItem(downPlaylist, "Скачать", R.drawable.save_black);

        copyLink.setOnClickListener(v -> {
            copyPlaylistLink(playlist);
            bottomSheetDialog.dismiss();
            showToast("Ссылка скопирована");
        });

        deletePlaylist.setOnClickListener(v -> {
            deletePlaylist(playlist);
            bottomSheetDialog.dismiss();
        });

        editPlaylist.setOnClickListener(v -> {
            showEditPlaylistDialog(playlist);
            bottomSheetDialog.dismiss();
        });

        sharePlaylist.setOnClickListener(v -> {
            sharePlaylist(playlist);
            bottomSheetDialog.dismiss();
        });

        downPlaylist.setOnClickListener(v -> {
            downloadPlaylist(playlist);
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void downloadPlaylist(Playlist playlist) {
        if (!isAdded() || getActivity() == null) return;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            return;
        }

        showToast("Начинаем загрузку плейлиста...");

        String url = "https://api.vk.com/method/audio.get" +
                "?access_token=" + accessToken +
                "&owner_id=" + playlist.ownerId +
                "&album_id=" + playlist.id +
                "&count=1000" +
                "&v=" + API_VERSION;

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isAdded()) return;
                showError("Ошибка при получении треков: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("error")) {
                        String errorMsg = json.getJSONObject("error").getString("error_msg");
                        showError("Ошибка VK: " + errorMsg);
                        return;
                    }

                    JSONObject responseObj = json.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    if (items.length() == 0) {
                        showError("В плейлисте нет треков");
                        return;
                    }

                    String playlistFolderName = sanitizeFileName(playlist.title);
                    File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                    File playlistDir = new File(musicDir, playlistFolderName);

                    if (!playlistDir.exists() && !playlistDir.mkdirs()) {
                        showError("Не удалось создать папку для плейлиста");
                        return;
                    }

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject track = items.getJSONObject(i);
                        String trackUrl = track.getString("url");
                        String trackTitle = track.getString("title");
                        String trackArtist = track.getString("artist");

                        String fileName = sanitizeFileName(trackArtist + " - " + trackTitle + ".mp3");
                        File outputFile = new File(playlistDir, fileName);

                        downloadTrack(trackUrl, outputFile, i, items.length());
                    }

                } catch (Exception e) {
                    showError("Ошибка обработки треков: " + e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void downloadTrack(String url, File outputFile, int current, int total) {
        Request request = new Request.Builder().url(url).build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isAdded()) return;
                showError("Ошибка загрузки трека " + (current + 1));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try (InputStream inputStream = response.body().byteStream();
                     FileOutputStream outputStream = new FileOutputStream(outputFile)) {

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    String message = "Загружен трек " + (current + 1) + " из " + total;
                    if (current + 1 == total) {
                        message = "Плейлист загружен полностью!";
                    }
                    showToast(message);

                } catch (Exception e) {
                    showError("Ошибка сохранения трека " + (current + 1));
                } finally {
                    response.close();
                }
            }
        });
    }

    private void setMenuItem(TextView menuItem, String text, @DrawableRes int iconRes) {
        menuItem.setText(text);
        Drawable icon = ContextCompat.getDrawable(requireContext(), iconRes);
        if (icon != null) {
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            menuItem.setCompoundDrawables(icon, null, null, null);
            menuItem.setCompoundDrawablePadding(16);
        }
    }

    private void sharePlaylist(Playlist playlist) {
        if (!isAdded() || getActivity() == null) return;

        String link = "https://vk.com/music/playlist/" + playlist.ownerId + "_" + playlist.id;
        String shareText = "Послушайте мой плейлист: " + playlist.title + "\n" + link;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Поделиться плейлистом");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

        if (shareIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivity(Intent.createChooser(shareIntent, "Поделиться плейлистом"));
        } else {
            showToast("Нет приложений для отправки");
        }
    }

    private void copyPlaylistLink(Playlist playlist) {
        if (!isAdded() || getActivity() == null) return;

        String link = "https://vk.com/music/playlist/" + playlist.ownerId + "_" + playlist.id;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Playlist Link", link);
        clipboard.setPrimaryClip(clip);
    }

    private void deletePlaylist(Playlist playlist) {
        if (!isAdded() || getActivity() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Удалить плейлист")
                .setMessage("Вы уверены, что хотите удалить этот плейлист?")
                .setPositiveButton("Удалить", (dialog, which) -> performDeletePlaylist(playlist))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void performDeletePlaylist(Playlist playlist) {
        if (!isAdded() || getActivity() == null) return;

        OkHttpClient client = new OkHttpClient();
        String url = "https://api.vk.com/method/audio.deletePlaylist" +
                "?access_token=" + accessToken +
                "&owner_id=" + playlist.ownerId +
                "&playlist_id=" + playlist.id +
                "&v=" + API_VERSION;

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isAdded()) return;
                showError("Ошибка соединения: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        String errorMsg = error.getString("error_msg");
                        showError("Ошибка VK: " + errorMsg);
                        return;
                    }

                    if (json.has("response") && json.getInt("response") == 1) {
                        getActivity().runOnUiThread(() -> {
                            showToast("Плейлист удален");
                            refreshPlaylists();
                        });
                    } else {
                        showError("Неизвестный ответ от сервера");
                    }
                } catch (Exception e) {
                    showError("Ошибка обработки ответа");
                } finally {
                    response.close();
                }
            }
        });
    }

    private void showEditPlaylistDialog(Playlist playlist) {
        if (!isAdded() || getActivity() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_create_playlist, null);
        dialog.setContentView(dialogView);

        EditText playlistNameEditText = dialogView.findViewById(R.id.playlist_name_edit_text);
        EditText playlistDescEditText = dialogView.findViewById(R.id.playlist_desc_edit_text);
        ImageView coverImageView = dialogView.findViewById(R.id.cover_image_view);
        Button selectImageButton = dialogView.findViewById(R.id.select_image_button);
        Button createButton = dialogView.findViewById(R.id.create_button);

        playlistNameEditText.setText(playlist.title);
        playlistDescEditText.setText(playlist.description);
        createButton.setText("Сохранить");

        if (playlist.photo != null && !playlist.photo.photo_300.isEmpty()) {
            Glide.with(this)
                    .load(playlist.photo.photo_300)
                    .into(coverImageView);
            coverImageView.setVisibility(View.VISIBLE);
        }

        selectImageButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_READ_STORAGE);
            } else {
                openImagePicker();
            }
        });

        createButton.setOnClickListener(v -> {
            String playlistName = playlistNameEditText.getText().toString().trim();
            String description = playlistDescEditText.getText().toString().trim();

            if (!playlistName.isEmpty()) {
                if (selectedImageUri != null) {
                    uploadCoverAndEditPlaylist(playlist, playlistName, description);
                } else {
                    editPlaylist(playlist, playlistName, description, null);
                }
                dialog.dismiss();
            } else {
                showToast("Введите название");
            }
        });

        dialog.show();
    }

    private void uploadCoverAndEditPlaylist(Playlist playlist, String title, String description) {
        if (!isAdded() || getActivity() == null) return;

        showLoading(true);

        OkHttpClient client = new OkHttpClient();
        Request uploadServerRequest = new Request.Builder()
                .url("https://api.vk.com/method/audio.getUploadServer" +
                        "?access_token=" + accessToken +
                        "&v=" + API_VERSION)
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(uploadServerRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isAdded()) return;
                getActivity().runOnUiThread(() -> {
                    showError("Ошибка получения сервера загрузки");
                    editPlaylist(playlist, title, description, null);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try {
                    String json = response.body().string();
                    JSONObject jsonObject = new JSONObject(json);

                    if (jsonObject.has("error")) {
                        String errorMsg = jsonObject.getJSONObject("error").getString("error_msg");
                        getActivity().runOnUiThread(() -> {
                            showError("Ошибка: " + errorMsg);
                            editPlaylist(playlist, title, description, null);
                        });
                        return;
                    }

                    String uploadUrl = jsonObject.getJSONObject("response").getString("upload_url");
                    uploadImageForEdit(uploadUrl, playlist, title, description);

                } catch (Exception e) {
                    getActivity().runOnUiThread(() -> {
                        showError("Ошибка обработки сервера загрузки");
                        editPlaylist(playlist, title, description, null);
                    });
                }
            }
        });
    }

    private void uploadImageForEdit(String uploadUrl, Playlist playlist, String title, String description) {
        if (!isAdded() || getActivity() == null) return;

        try {
            InputStream inputStream = requireActivity().getContentResolver().openInputStream(selectedImageUri);
            byte[] imageData = Util.toByteArray(inputStream);
            inputStream.close();

            OkHttpClient client = new OkHttpClient();
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "cover.jpg",
                            RequestBody.create(imageData, MediaType.parse("image/jpeg")))
                    .build();

            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .header("User-Agent", getUserAgent())
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (!isAdded()) return;
                    getActivity().runOnUiThread(() -> {
                        showError("Ошибка загрузки изображения");
                        editPlaylist(playlist, title, description, null);
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!isAdded()) return;

                    try {
                        String json = response.body().string();
                        JSONObject jsonObject = new JSONObject(json);

                        if (jsonObject.has("error")) {
                            getActivity().runOnUiThread(() -> {
                                try {
                                    showError("Ошибка загрузки: " + jsonObject.getString("error"));
                                } catch (JSONException e) {
                                    showError("Ошибка загрузки");
                                }
                                editPlaylist(playlist, title, description, null);
                            });
                            return;
                        }

                        String hash = jsonObject.getString("hash");
                        String photo = jsonObject.getString("photo");
                        saveUploadedPhotoForEdit(hash, photo, playlist, title, description);

                    } catch (Exception e) {
                        getActivity().runOnUiThread(() -> {
                            showError("Ошибка обработки загруженного изображения");
                            editPlaylist(playlist, title, description, null);
                        });
                    }
                }
            });

        } catch (Exception e) {
            if (!isAdded()) return;
            getActivity().runOnUiThread(() -> {
                showError("Ошибка чтения изображения");
                editPlaylist(playlist, title, description, null);
            });
        }
    }

    private void saveUploadedPhotoForEdit(String hash, String photo, Playlist playlist, String title, String description) {
        if (!isAdded() || getActivity() == null) return;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://api.vk.com/method/audio.saveCover" +
                        "?access_token=" + accessToken +
                        "&hash=" + hash +
                        "&photo=" + photo +
                        "&v=" + API_VERSION)
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isAdded()) return;
                getActivity().runOnUiThread(() -> {
                    showError("Ошибка сохранения обложки");
                    editPlaylist(playlist, title, description, null);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try {
                    String json = response.body().string();
                    JSONObject jsonObject = new JSONObject(json);

                    if (jsonObject.has("error")) {
                        String errorMsg = jsonObject.getJSONObject("error").getString("error_msg");
                        getActivity().runOnUiThread(() -> {
                            showError("Ошибка сохранения: " + errorMsg);
                            editPlaylist(playlist, title, description, null);
                        });
                        return;
                    }

                    String photoId = jsonObject.getJSONObject("response").getString("id");
                    editPlaylist(playlist, title, description, photoId);

                } catch (Exception e) {
                    getActivity().runOnUiThread(() -> {
                        showError("Ошибка обработки сохраненной обложки");
                        editPlaylist(playlist, title, description, null);
                    });
                }
            }
        });
    }

    private void editPlaylist(Playlist playlist, String title, String description, String photoId) {
        if (!isAdded() || getActivity() == null) return;

        OkHttpClient client = new OkHttpClient();
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add("access_token", accessToken)
                .add("owner_id", String.valueOf(playlist.ownerId))
                .add("playlist_id", String.valueOf(playlist.id))
                .add("title", title)
                .add("v", API_VERSION);

        if (description != null && !description.isEmpty()) {
            formBuilder.add("description", description);
        }

        if (photoId != null) {
            formBuilder.add("photo_id", photoId);
        }

        Request request = new Request.Builder()
                .url("https://api.vk.com/method/audio.editPlaylist")
                .post(formBuilder.build())
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isAdded()) return;
                showError("Ошибка сети");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try {
                    JSONObject json = new JSONObject(response.body().string());
                    if (json.has("response") && json.getInt("response") == 1) {
                        getActivity().runOnUiThread(() -> {
                            showToast("Плейлист обновлен");
                            refreshPlaylists();
                        });
                    } else if (json.has("error")) {
                        String errorMsg = json.getJSONObject("error").getString("error_msg");
                        showError("Ошибка: " + errorMsg);
                    }
                } catch (Exception e) {
                    showError("Ошибка обработки");
                }
            }
        });
    }

    private void loadMorePlaylists() {
        isLoading = true;
        currentOffset += PLAYLISTS_PER_REQUEST;
        fetchPlaylists();
    }

    private void fetchPlaylists() {
        if (!isAdded() || getActivity() == null) return;

        showLoading(true);
        String userId = TokenManager.getInstance(requireContext()).getUserId();

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(buildPlaylistsUrl(userId, currentOffset))
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isAdded()) return;
                getActivity().runOnUiThread(() -> {
                    showError("Ошибка сети");
                    swipeRefreshLayout.setRefreshing(false);
                    isLoading = false;
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try {
                    String json = response.body().string();
                    Gson gson = new Gson();
                    VkResponse vkResponse = gson.fromJson(json, VkResponse.class);

                    getActivity().runOnUiThread(() -> {
                        if (vkResponse.response != null && vkResponse.response.items != null) {
                            handlePlaylistsResponse(vkResponse);
                        } else {
                            showError("Ошибка данных");
                        }
                        swipeRefreshLayout.setRefreshing(false);
                        isLoading = false;
                    });
                } catch (Exception e) {
                    if (!isAdded()) return;
                    getActivity().runOnUiThread(() -> {
                        showError("Ошибка обработки");
                        swipeRefreshLayout.setRefreshing(false);
                        isLoading = false;
                    });
                }
            }
        });
    }

    private void handlePlaylistsResponse(VkResponse vkResponse) {
        if (!isAdded() || getActivity() == null) return;

        List<Playlist> newPlaylists = vkResponse.response.items;

        // Заполняем имена владельцев
        if (vkResponse.response.profiles != null || vkResponse.response.groups != null) {
            Map<Long, String> ownersMap = new HashMap<>();

            if (vkResponse.response.profiles != null) {
                for (Profile profile : vkResponse.response.profiles) {
                    ownersMap.put(profile.id, profile.getFullName());
                }
            }

            if (vkResponse.response.groups != null) {
                for (Group group : vkResponse.response.groups) {
                    ownersMap.put(-group.id, group.name); // ID групп отрицательные
                }
            }

            for (Playlist playlist : newPlaylists) {
                playlist.ownerName = ownersMap.get(playlist.ownerId);
            }
        }

        if (currentOffset == 0) {
            playlistList.clear();
        }

        playlistList.addAll(newPlaylists);
        hasMore = newPlaylists.size() >= PLAYLISTS_PER_REQUEST;
        updateFilteredList();
        updateEmptyView();
        updateAlbumsCountText();
        showLoading(false);
    }

    private String buildPlaylistsUrl(String userId, int offset) {
        return "https://api.vk.com/method/audio.getPlaylists" +
                "?owner_id=" + userId +
                "&offset=" + offset +
                "&count=" + PLAYLISTS_PER_REQUEST +
                "&extended=1" +
                "&v=" + API_VERSION +
                "&access_token=" + accessToken;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.playlists_menu, menu);
        setupSearchView(menu);
    }

    private void setupSearchView(Menu menu) {
        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint("Поиск по плейлистам");
        searchView.setMaxWidth(Integer.MAX_VALUE);

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
        filteredPlaylistList.clear();
        if (query.isEmpty()) {
            filteredPlaylistList.addAll(playlistList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (Playlist playlist : playlistList) {
                if (playlist.title.toLowerCase().contains(lowerCaseQuery) ||
                        (playlist.ownerName != null && playlist.ownerName.toLowerCase().contains(lowerCaseQuery)) ||
                        (playlist.description != null && playlist.description.toLowerCase().contains(lowerCaseQuery))) {
                    filteredPlaylistList.add(playlist);
                }
            }
        }
        applySorting();
        updateEmptyView();
        updateAlbumsCountText();
        adapter.notifyDataSetChanged();
    }

    private void updateFilteredList() {
        filteredPlaylistList.clear();
        if (searchView != null && !searchView.getQuery().toString().isEmpty()) {
            filterPlaylists(searchView.getQuery().toString());
        } else {
            filteredPlaylistList.addAll(playlistList);
            applySorting();
        }
        adapter.notifyDataSetChanged();
    }

    private void updateEmptyView() {
        if (!isAdded() || getActivity() == null) return;

        if (filteredPlaylistList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(searchView != null && !searchView.getQuery().toString().isEmpty()
                    ? "Ничего не найдено"
                    : "Нет плейлистов");
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    private void setupToolbar() {
        if (!isAdded() || getActivity() == null) return;

        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
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
        if (!isAdded() || getActivity() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_create_playlist, null);
        dialog.setContentView(dialogView);

        EditText playlistNameEditText = dialogView.findViewById(R.id.playlist_name_edit_text);
        EditText playlistDescEditText = dialogView.findViewById(R.id.playlist_desc_edit_text);
        ImageView coverImageView = dialogView.findViewById(R.id.cover_image_view);
        Button selectImageButton = dialogView.findViewById(R.id.select_image_button);
        Button createButton = dialogView.findViewById(R.id.create_button);
        CheckBox addTracksCheckbox = dialogView.findViewById(R.id.add_tracks_checkbox);
        RecyclerView tracksRecyclerView = dialogView.findViewById(R.id.tracks_recycler_view);
        TextView tracksTitle = dialogView.findViewById(R.id.tracks_title);

        // Скрываем выбор треков по умолчанию
        tracksRecyclerView.setVisibility(View.GONE);
        tracksTitle.setVisibility(View.GONE);

        // Настройка списка треков
        List<Audio> selectedTracks = new ArrayList<>();
        TracksSelectionAdapter tracksAdapter = new TracksSelectionAdapter(fullAudioList, selectedTracks);
        tracksRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        tracksRecyclerView.setAdapter(tracksAdapter);

        selectImageButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_READ_STORAGE);
            } else {
                openImagePicker();
            }
        });

        if (selectedImageUri != null) {
            Glide.with(this)
                    .load(selectedImageUri)
                    .into(coverImageView);
            coverImageView.setVisibility(View.VISIBLE);
        }

        // Обработчик для чекбокса добавления треков
        addTracksCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                tracksRecyclerView.setVisibility(View.VISIBLE);
                tracksTitle.setVisibility(View.VISIBLE);
            } else {
                tracksRecyclerView.setVisibility(View.GONE);
                tracksTitle.setVisibility(View.GONE);
                selectedTracks.clear();
            }
        });

        createButton.setOnClickListener(v -> {
            String playlistName = playlistNameEditText.getText().toString().trim();
            playlistDescription = playlistDescEditText.getText().toString().trim();

            if (!playlistName.isEmpty()) {
                if (selectedImageUri != null) {
                    uploadCoverAndCreatePlaylist(playlistName, selectedTracks);
                } else {
                    createPlaylist(playlistName, null, selectedTracks);
                }
                dialog.dismiss();
            } else {
                showToast("Введите название");
            }
        });

        dialog.show();
    }

    private void openImagePicker() {
        if (!isAdded() || getActivity() == null) return;

        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Выберите обложку"), PICK_IMAGE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_READ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                showToast("Для выбора обложки необходимо разрешение");
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            showToast("Изображение выбрано");
        }
    }

    private void uploadCoverAndCreatePlaylist(String title, List<Audio> selectedTracks) {
        if (!isAdded() || getActivity() == null) return;

        if (selectedImageUri == null) {
            createPlaylist(title, null, selectedTracks);
            return;
        }

        showLoading(true);

        OkHttpClient client = new OkHttpClient();
        Request uploadServerRequest = new Request.Builder()
                .url("https://api.vk.com/method/audio.getUploadServer" +
                        "?access_token=" + accessToken +
                        "&v=" + API_VERSION)
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(uploadServerRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isAdded()) return;
                getActivity().runOnUiThread(() -> {
                    showError("Ошибка получения сервера загрузки");
                    createPlaylist(title, null, selectedTracks);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try {
                    String json = response.body().string();
                    JSONObject jsonObject = new JSONObject(json);

                    if (jsonObject.has("error")) {
                        String errorMsg = jsonObject.getJSONObject("error").getString("error_msg");
                        getActivity().runOnUiThread(() -> {
                            showError("Ошибка: " + errorMsg);
                            createPlaylist(title, null, selectedTracks);
                        });
                        return;
                    }

                    String uploadUrl = jsonObject.getJSONObject("response").getString("upload_url");
                    uploadImageToServer(uploadUrl, title, selectedTracks);

                } catch (Exception e) {
                    getActivity().runOnUiThread(() -> {
                        showError("Ошибка обработки сервера загрузки");
                        createPlaylist(title, null, selectedTracks);
                    });
                }
            }
        });
    }

    private void uploadImageToServer(String uploadUrl, String title, List<Audio> selectedTracks) {
        try {
            InputStream inputStream = requireActivity().getContentResolver().openInputStream(selectedImageUri);
            byte[] imageData = Util.toByteArray(inputStream);
            inputStream.close();

            OkHttpClient client = new OkHttpClient();
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "cover.jpg",
                            RequestBody.create(imageData, MediaType.parse("image/jpeg")))
                    .build();

            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .header("User-Agent", Authorizer.getKateUserAgent())
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        showError("Ошибка загрузки изображения");
                        createPlaylist(title, null, selectedTracks);
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String json = response.body().string();
                        JSONObject jsonObject = new JSONObject(json);

                        if (jsonObject.has("error")) {
                            requireActivity().runOnUiThread(() -> {
                                try {
                                    showError("Ошибка загрузки: " + jsonObject.getString("error"));
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                                createPlaylist(title, null, selectedTracks);
                            });
                            return;
                        }

                        String hash = jsonObject.getString("hash");
                        String photo = jsonObject.getString("photo");
                        saveUploadedPhoto(hash, photo, title, selectedTracks);

                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            showError("Ошибка обработки загруженного изображения");
                            createPlaylist(title, null, selectedTracks);
                        });
                    }
                }
            });

        } catch (Exception e) {
            requireActivity().runOnUiThread(() -> {
                showError("Ошибка чтения изображения");
                createPlaylist(title, null, selectedTracks);
            });
        }
    }

    private void saveUploadedPhoto(String hash, String photo, String title, List<Audio> selectedTracks) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://api.vk.com/method/audio.saveCover" +
                        "?access_token=" + accessToken +
                        "&hash=" + hash +
                        "&photo=" + photo +
                        "&v=" + API_VERSION)
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    showError("Ошибка сохранения обложки");
                    createPlaylist(title, null, selectedTracks);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String json = response.body().string();
                    JSONObject jsonObject = new JSONObject(json);

                    if (jsonObject.has("error")) {
                        String errorMsg = jsonObject.getJSONObject("error").getString("error_msg");
                        requireActivity().runOnUiThread(() -> {
                            showError("Ошибка сохранения: " + errorMsg);
                            createPlaylist(title, null, selectedTracks);
                        });
                        return;
                    }

                    String photoId = jsonObject.getJSONObject("response").getString("id");
                    createPlaylist(title, photoId, selectedTracks);

                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        showError("Ошибка обработки сохраненной обложки");
                        createPlaylist(title, null, selectedTracks);
                    });
                }
            }
        });
    }

    private void createPlaylist(String title, String photoId, List<Audio> selectedTracks) {
        OkHttpClient client = new OkHttpClient();
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add("access_token", accessToken)
                .add("owner_id", TokenManager.getInstance(requireContext()).getUserId())
                .add("title", title)
                .add("v", API_VERSION);

        if (!playlistDescription.isEmpty()) {
            formBuilder.add("description", playlistDescription);
        }

        if (photoId != null) {
            formBuilder.add("photo_id", photoId);
        }

        Request request = new Request.Builder()
                .url("https://api.vk.com/method/audio.createPlaylist")
                .post(formBuilder.build())
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> showToast("Ошибка сети"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    if (json.has("response")) {
                        JSONObject responseObj = json.getJSONObject("response");
                        int playlistId = responseObj.getInt("id");
                        long ownerId = responseObj.getLong("owner_id");

                        // Добавляем треки в плейлист, если они выбраны
                        if (!selectedTracks.isEmpty()) {
                            addTracksToPlaylist(playlistId, ownerId, selectedTracks);
                        } else {
                            requireActivity().runOnUiThread(() -> {
                                showToast("Плейлист создан");
                                refreshPlaylists();
                            });
                        }
                    } else if (json.has("error")) {
                        String errorMsg = json.getJSONObject("error").getString("error_msg");
                        requireActivity().runOnUiThread(() -> showToast("Ошибка: " + errorMsg));
                    }
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> showToast("Ошибка обработки"));
                }
            }
        });
    }

    private void addTracksToPlaylist(int playlistId, long ownerId, List<Audio> tracks) {
        OkHttpClient client = new OkHttpClient();

        // Формируем список аудио для добавления
        StringBuilder audioIds = new StringBuilder();
        for (Audio track : tracks) {
            if (audioIds.length() > 0) {
                audioIds.append(",");
            }
            audioIds.append(track.getOwnerId()).append("_").append(track.getAudioId());
        }

        FormBody formBody = new FormBody.Builder()
                .add("access_token", accessToken)
                .add("owner_id", String.valueOf(ownerId))
                .add("playlist_id", String.valueOf(playlistId))
                .add("audio_ids", audioIds.toString())
                .add("v", API_VERSION)
                .build();

        Request request = new Request.Builder()
                .url("https://api.vk.com/method/audio.addToPlaylist")
                .post(formBody)
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() ->
                        showToast("Плейлист создан, но не все треки добавлены"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                requireActivity().runOnUiThread(() -> {
                    showToast("Плейлист создан с " + tracks.size() + " треками");
                    refreshPlaylists();
                });
            }
        });
    }

    private void refreshPlaylists() {
        currentOffset = 0;
        hasMore = true;
        fetchPlaylists();
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            showLoading(false);
            updateEmptyView();
        });
    }

    private void showToast(String message) {
        requireActivity().runOnUiThread(() ->
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show()
        );
    }

    // Вспомогательные классы

    public static class Audio {
        private long audioId;
        private long ownerId;
        private String artist;
        private String title;
        private int duration;
        private String url;
        private long date;
        private long albumId;
        private String genreId;

        public Audio(long audioId, long ownerId, String artist, String title, int duration,
                     String url, long date, long albumId, String genreId) {
            this.audioId = audioId;
            this.ownerId = ownerId;
            this.artist = artist;
            this.title = title;
            this.duration = duration;
            this.url = url;
            this.date = date;
            this.albumId = albumId;
            this.genreId = genreId;
        }

        // Геттеры
        public long getAudioId() { return audioId; }
        public long getOwnerId() { return ownerId; }
        public String getArtist() { return artist; }
        public String getTitle() { return title; }
        public int getDuration() { return duration; }
        public String getUrl() { return url; }
        public long getDate() { return date; }
        public long getAlbumId() { return albumId; }
        public String getGenreId() { return genreId; }
    }

    private static class TracksSelectionAdapter extends RecyclerView.Adapter<TracksSelectionAdapter.ViewHolder> {
        private List<Audio> allTracks;
        private List<Audio> selectedTracks;

        TracksSelectionAdapter(List<Audio> allTracks, List<Audio> selectedTracks) {
            this.allTracks = allTracks;
            this.selectedTracks = selectedTracks;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_track_selection, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Audio track = allTracks.get(position);
            holder.bind(track, selectedTracks.contains(track));
        }

        @Override
        public int getItemCount() {
            return allTracks.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView artistTextView;
            TextView titleTextView;
            CheckBox selectionCheckbox;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                artistTextView = itemView.findViewById(R.id.artist_text);
                titleTextView = itemView.findViewById(R.id.title_text);
                selectionCheckbox = itemView.findViewById(R.id.selection_checkbox);
            }

            void bind(Audio track, boolean isSelected) {
                artistTextView.setText(track.getArtist());
                titleTextView.setText(track.getTitle());
                selectionCheckbox.setChecked(isSelected);

                itemView.setOnClickListener(v -> {
                    boolean newState = !selectionCheckbox.isChecked();
                    selectionCheckbox.setChecked(newState);

                    if (newState) {
                        if (!selectedTracks.contains(track)) {
                            selectedTracks.add(track);
                        }
                    } else {
                        selectedTracks.remove(track);
                    }
                });

                selectionCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        if (!selectedTracks.contains(track)) {
                            selectedTracks.add(track);
                        }
                    } else {
                        selectedTracks.remove(track);
                    }
                });
            }
        }
    }

    public static class VkResponse {
        @SerializedName("response")
        public ResponseData response;
    }

    public static class ResponseData {
        @SerializedName("items")
        public List<Playlist> items;
        @SerializedName("count")
        public int count;
        @SerializedName("profiles")
        public List<Profile> profiles;
        @SerializedName("groups")
        public List<Group> groups;
    }

    public static class Playlist {
        @SerializedName("id")
        public int id;
        @SerializedName("title")
        public String title;
        @SerializedName("description")
        public String description;
        @SerializedName("owner_id")
        public long ownerId;
        @SerializedName("photo")
        public Photo photo;
        @SerializedName("count")
        public int count;
        public String ownerName; // Имя владельца плейлиста

        public String getPhotoUrl() {
            return photo != null ? photo.photo_300 : "";
        }
    }

    public static class Photo {
        @SerializedName("photo_300")
        public String photo_300;
    }

    public static class Profile {
        @SerializedName("id")
        public long id;
        @SerializedName("first_name")
        public String firstName;
        @SerializedName("last_name")
        public String lastName;

        public String getFullName() {
            return firstName + " " + lastName;
        }
    }

    public static class Group {
        @SerializedName("id")
        public long id;
        @SerializedName("name")
        public String name;
    }

    private static class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.ViewHolder> {
        private final List<Playlist> playlists;
        private final OnPlaylistClickListener listener;
        private final OnMenuButtonClickListener menuButtonClickListener;

        interface OnPlaylistClickListener {
            void onClick(Playlist playlist);
        }

        interface OnMenuButtonClickListener {
            void onMenuClick(Playlist playlist);
        }

        PlaylistsAdapter(List<Playlist> playlists,
                         OnPlaylistClickListener listener,
                         OnMenuButtonClickListener menuButtonClickListener) {
            this.playlists = playlists;
            this.listener = listener;
            this.menuButtonClickListener = menuButtonClickListener;
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

            // Формируем подпись: "Исполнитель · X треков"
            String subtitle = (playlist.ownerName != null ? playlist.ownerName : "Исполнитель");
            holder.subtitleText.setText(subtitle);

            Glide.with(holder.itemView)
                    .load(playlist.getPhotoUrl())
                    .placeholder(R.drawable.circle_playlist)
                    .into(holder.coverImage);

            holder.itemView.setOnClickListener(v -> listener.onClick(playlist));

            // Обработка нажатия на кнопку меню
            holder.menuButton.setOnClickListener(v -> {
                menuButtonClickListener.onMenuClick(playlist);
            });
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView coverImage;
            TextView titleText;
            TextView subtitleText;
            ImageView menuButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                coverImage = itemView.findViewById(R.id.coverImageView);
                titleText = itemView.findViewById(R.id.titleText);
                subtitleText = itemView.findViewById(R.id.artistText);
                menuButton = itemView.findViewById(R.id.downloadButton);
            }
        }
    }
}