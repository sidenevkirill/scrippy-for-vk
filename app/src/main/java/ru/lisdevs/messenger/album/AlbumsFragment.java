package ru.lisdevs.messenger.album;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.friends.UserPhotosFragment;
import ru.lisdevs.messenger.utils.TokenManager;
import android.graphics.drawable.Drawable;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;


public class AlbumsFragment extends Fragment {
    private RecyclerView recyclerViewAlbums;
    private AlbumsAdapter albumsAdapter;
    private List<AlbumItem> albumList = new ArrayList<>();
    private List<AlbumItem> originalAlbumList = new ArrayList<>();
    private Toolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private OkHttpClient httpClient;
    private static final String API_VERSION = "5.131";
    private TextView textViewAlbumsCount;
    private TextView textViewUserName;
    private ImageView sortButton;

    // Кэш для обложек альбомов
    private Map<Long, String> albumCoverCache = new HashMap<>();

    // Режимы сортировки
    private static final int SORT_DEFAULT = 0;
    private static final int SORT_NEW_FIRST = 1;
    private static final int SORT_OLD_FIRST = 2;
    private static final int SORT_ALPHABETICAL = 3;

    private int currentSortMode = SORT_DEFAULT;

    public static AlbumsFragment newInstance() {
        AlbumsFragment fragment = new AlbumsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    // Тестовые альбомы для демонстрации
    private final AlbumItem[] TEST_ALBUMS = {
            new AlbumItem(-6, "Профиль", "Аватарки и фотографии профиля", 15,
                    "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=500", System.currentTimeMillis() - 86400000L * 30),
            new AlbumItem(-7, "Сохраненные фотографии", "Личные сохраненные фото", 8,
                    "https://images.unsplash.com/photo-1529778873920-4da4926a72c2?w=500", System.currentTimeMillis() - 86400000L * 15),
            new AlbumItem(-15, "Стена", "Фотографии со стены", 23,
                    "https://images.unsplash.com/photo-1518756131217-31eb79b20e8f?w=500", System.currentTimeMillis() - 86400000L * 7),
            new AlbumItem(-9000, "Личные фото", "Приватные фотографии", 5,
                    "https://images.unsplash.com/photo-1501854140801-50d01698950b?w=500", System.currentTimeMillis() - 86400000L * 3),
            new AlbumItem(100, "Путешествия", "Фото из путешествий", 42,
                    "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=500", System.currentTimeMillis())
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_albums, container, false);

        recyclerViewAlbums = view.findViewById(R.id.recyclerView);
        toolbar = view.findViewById(R.id.toolbar);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        textViewAlbumsCount = view.findViewById(R.id.count);
        //textViewUserName = view.findViewById(R.id.group_name);
        sortButton = view.findViewById(R.id.sort);

        setupUI();
        setupToolbar();
        setupSwipeRefresh();
        setupUserInfo();
        setupSortButton();

        if (isTestAccount()) {
            loadTestAlbums();
        } else {
            fetchMyAlbums();
        }

        return view;
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
                "Новые первыми",
                "Старые первыми",
                "По алфавиту (А-Я)"
        };

        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
                .setTitle("Сортировка альбомов")
                .setSingleChoiceItems(sortOptions, currentSortMode, (dialog, which) -> {
                    currentSortMode = which;
                    applySorting();
                    dialog.dismiss();
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("ОК", null) // Добавляем кнопку ОК для лучшего UX
                .show();
    }

    private void applySorting() {
        if (albumList.isEmpty()) return;

        List<AlbumItem> sortedList = new ArrayList<>(albumList);

        switch (currentSortMode) {
            case SORT_DEFAULT:
                sortedList = new ArrayList<>(originalAlbumList);
                break;

            case SORT_NEW_FIRST:
                Collections.sort(sortedList, (a1, a2) -> Long.compare(a2.getCreatedDate(), a1.getCreatedDate()));
                break;

            case SORT_OLD_FIRST:
                Collections.sort(sortedList, (a1, a2) -> Long.compare(a1.getCreatedDate(), a2.getCreatedDate()));
                break;

            case SORT_ALPHABETICAL:
                Collections.sort(sortedList, (a1, a2) -> a1.getTitle().compareToIgnoreCase(a2.getTitle()));
                break;
        }

        albumList.clear();
        albumList.addAll(sortedList);
        albumsAdapter.notifyDataSetChanged();

        String sortMessage = getSortMessage();
        //Toast.makeText(getContext(), sortMessage, Toast.LENGTH_SHORT).show();
    }

    private String getSortMessage() {
        switch (currentSortMode) {
            case SORT_DEFAULT:
                return "Сортировка по умолчанию";
            case SORT_NEW_FIRST:
                return "Новые альбомы первыми";
            case SORT_OLD_FIRST:
                return "Старые альбомы первыми";
            case SORT_ALPHABETICAL:
                return "Сортировка по алфавиту";
            default:
                return "Сортировка применена";
        }
    }

    private boolean isTestAccount() {
        Context context = getContext();
        if (context == null) return true;

        String accessToken = TokenManager.getInstance(context).getToken();
        return accessToken == null || accessToken.isEmpty() || accessToken.equals("test_token");
    }

    private void loadTestAlbums() {
        swipeRefreshLayout.setRefreshing(true);

        List<AlbumItem> testAlbums = new ArrayList<>();
        Collections.addAll(testAlbums, TEST_ALBUMS);

        requireActivity().runOnUiThread(() -> {
            albumList.clear();
            originalAlbumList.clear();
            albumList.addAll(testAlbums);
            originalAlbumList.addAll(testAlbums);
            albumsAdapter.notifyDataSetChanged();
            swipeRefreshLayout.setRefreshing(false);
            updateAlbumsCountText();
            Toast.makeText(getContext(), "Показаны тестовые альбомы", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupUI() {
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 2);
        recyclerViewAlbums.setLayoutManager(layoutManager);
        albumsAdapter = new AlbumsAdapter(albumList, this::openAlbumPhotos);
        recyclerViewAlbums.setAdapter(albumsAdapter);
    }

    private void setupToolbar() {
        if (toolbar != null && getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                activity.getSupportActionBar().setTitle("Мои альбомы");
            }

            toolbar.setNavigationOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            });
        }
    }

    private void setupUserInfo() {
        if (textViewUserName != null) {
            textViewUserName.setText("Мои альбомы");
        }
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Очищаем кэш при обновлении
            albumCoverCache.clear();
            if (isTestAccount()) {
                loadTestAlbums();
            } else {
                fetchMyAlbums();
            }
        });
    }

    private void updateAlbumsCountText() {
        Activity activity = getActivity();
        if (activity != null && textViewAlbumsCount != null) {
            activity.runOnUiThread(() -> {
                String countText = albumList.size() > 0 ?
                        "Альбомы: " + albumList.size() : "Нет альбомов";
                textViewAlbumsCount.setText(countText);
            });
        }
    }

    private void fetchMyAlbums() {
        Context context = getContext();
        if (context == null) {
            swipeRefreshLayout.setRefreshing(false);
            loadTestAlbums();
            return;
        }

        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null || accessToken.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            loadTestAlbums();
            return;
        }

        fetchAlbumsDirectly(accessToken);
    }

    private void fetchAlbumsDirectly(String accessToken) {
        HttpUrl baseUrl = HttpUrl.parse("https://api.vk.com/method/photos.getAlbums");
        if (baseUrl == null) {
            requireActivity().runOnUiThread(() -> {
                swipeRefreshLayout.setRefreshing(false);
                loadTestAlbums();
            });
            return;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("need_system", "1")
                .addQueryParameter("need_covers", "1")
                .addQueryParameter("photo_sizes", "1")
                .addQueryParameter("v", API_VERSION)
                .build();

        Log.d("AlbumsFragment", "Loading albums without owner_id");

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        swipeRefreshLayout.setRefreshing(true);

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("AlbumsFragment", "Network error: " + e.getMessage());
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Ошибка сети", Toast.LENGTH_SHORT).show();
                    swipeRefreshLayout.setRefreshing(false);
                    loadTestAlbums();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body().string();
                Log.d("AlbumsFragment", "API Response length: " + responseBody.length());

                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);

                        if (jsonObject.has("response")) {
                            JSONObject responseObj = jsonObject.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");
                            List<AlbumItem> newAlbumList = new ArrayList<>();

                            Log.d("AlbumsFragment", "Found " + items.length() + " albums");

                            for (int i = 0; i < items.length(); i++) {
                                JSONObject albumObj = items.getJSONObject(i);
                                AlbumItem albumItem = parseAlbumItem(albumObj);
                                if (albumItem != null) {
                                    newAlbumList.add(albumItem);
                                }
                            }

                            requireActivity().runOnUiThread(() -> {
                                handleAlbumsLoaded(newAlbumList);
                            });

                        } else if (jsonObject.has("error")) {
                            JSONObject errorObj = jsonObject.getJSONObject("error");
                            int errorCode = errorObj.getInt("error_code");
                            String errorMsg = errorObj.getString("error_msg");

                            Log.e("AlbumsFragment", "API Error " + errorCode + ": " + errorMsg);
                            requireActivity().runOnUiThread(() -> {
                                handleApiError(errorCode, errorMsg, accessToken);
                            });
                        }
                    } catch (JSONException e) {
                        Log.e("AlbumsFragment", "JSON parsing error: " + e.getMessage());
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                            swipeRefreshLayout.setRefreshing(false);
                            loadTestAlbums();
                        });
                    }
                } else {
                    Log.e("AlbumsFragment", "HTTP error: " + response.code());
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка сервера: " + response.code(), Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                        loadTestAlbums();
                    });
                }
            }
        });
    }

    private void handleAlbumsLoaded(List<AlbumItem> newAlbumList) {
        if (newAlbumList.isEmpty()) {
            Toast.makeText(getContext(), "У вас нет альбомов", Toast.LENGTH_SHORT).show();
            loadTestAlbums();
        } else {
            albumList.clear();
            originalAlbumList.clear();
            albumList.addAll(newAlbumList);
            originalAlbumList.addAll(newAlbumList);
            applySorting();
            updateAlbumsCountText();
            //Toast.makeText(getContext(), "Загружено " + newAlbumList.size() + " альбомов", Toast.LENGTH_SHORT).show();
        }
        swipeRefreshLayout.setRefreshing(false);
    }

    private void handleApiError(int errorCode, String errorMsg, String accessToken) {
        switch (errorCode) {
            case 5:
                Toast.makeText(getContext(), "Ошибка авторизации", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
                loadTestAlbums();
                break;
            case 7:
                getCurrentUserIdAndRetry(accessToken);
                break;
            case 10:
                Toast.makeText(getContext(), "Внутренняя ошибка сервера VK", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
                loadTestAlbums();
                break;
            default:
                Toast.makeText(getContext(), "Ошибка: " + errorMsg, Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
                loadTestAlbums();
                break;
        }
    }

    private void getCurrentUserIdAndRetry(String accessToken) {
        HttpUrl baseUrl = HttpUrl.parse("https://api.vk.com/method/users.get");
        if (baseUrl == null) {
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), "Ошибка создания запроса", Toast.LENGTH_SHORT).show();
                loadTestAlbums();
            });
            return;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("v", API_VERSION)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Не удалось получить данные пользователя", Toast.LENGTH_SHORT).show();
                    loadTestAlbums();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        if (jsonObject.has("response")) {
                            JSONArray responseArray = jsonObject.getJSONArray("response");
                            if (responseArray.length() > 0) {
                                JSONObject user = responseArray.getJSONObject(0);
                                long userId = user.getLong("id");
                                fetchAlbumsWithOwnerId(accessToken, userId);
                                return;
                            }
                        }
                    } catch (JSONException e) {
                        Log.e("AlbumsFragment", "JSON parsing error in users.get: " + e.getMessage());
                    }
                }
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Не удалось загрузить альбомы", Toast.LENGTH_SHORT).show();
                    loadTestAlbums();
                });
            }
        });
    }

    private void fetchAlbumsWithOwnerId(String accessToken, long userId) {
        HttpUrl baseUrl = HttpUrl.parse("https://api.vk.com/method/photos.getAlbums");
        if (baseUrl == null) {
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), "Ошибка создания запроса", Toast.LENGTH_SHORT).show();
                loadTestAlbums();
            });
            return;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("owner_id", String.valueOf(userId))
                .addQueryParameter("need_system", "1")
                .addQueryParameter("need_covers", "1")
                .addQueryParameter("photo_sizes", "1")
                .addQueryParameter("v", API_VERSION)
                .build();

        Log.d("AlbumsFragment", "Loading albums with owner_id: " + userId);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        swipeRefreshLayout.setRefreshing(true);

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Ошибка сети", Toast.LENGTH_SHORT).show();
                    swipeRefreshLayout.setRefreshing(false);
                    loadTestAlbums();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body().string();
                Log.d("AlbumsFragment", "API Response with owner_id: " + responseBody);

                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        if (jsonObject.has("response")) {
                            JSONObject responseObj = jsonObject.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");
                            List<AlbumItem> newAlbumList = new ArrayList<>();

                            for (int i = 0; i < items.length(); i++) {
                                JSONObject albumObj = items.getJSONObject(i);
                                AlbumItem albumItem = parseAlbumItem(albumObj);
                                if (albumItem != null) {
                                    newAlbumList.add(albumItem);
                                }
                            }

                            requireActivity().runOnUiThread(() -> {
                                handleAlbumsLoaded(newAlbumList);
                            });
                        } else {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Не удалось загрузить альбомы", Toast.LENGTH_SHORT).show();
                                swipeRefreshLayout.setRefreshing(false);
                                loadTestAlbums();
                            });
                        }
                    } catch (JSONException e) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                            swipeRefreshLayout.setRefreshing(false);
                            loadTestAlbums();
                        });
                    }
                } else {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка сервера", Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                        loadTestAlbums();
                    });
                }
            }
        });
    }

    private String getUserAgent() {
        try {
            SharedPreferences prefs = requireContext().getSharedPreferences("auth_prefs", Context.MODE_PRIVATE);
            String authType = prefs.getString("auth_type", null);

            if (authType != null && "AuthActivity".equals(authType)) {
                return "VKAndroidApp/1.0";
            } else {
                return Authorizer.getKateUserAgent();
            }
        } catch (Exception e) {
            return "VKAndroidApp/1.0";
        }
    }

    private AlbumItem parseAlbumItem(JSONObject albumObj) throws JSONException {
        long id = albumObj.getLong("id");
        String title = albumObj.getString("title");
        String description = albumObj.optString("description", "");
        int size = albumObj.getInt("size");
        long ownerId = albumObj.optLong("owner_id", 0);

        long createdDate = albumObj.optLong("created", 0);
        if (createdDate == 0) {
            createdDate = System.currentTimeMillis() - (long) (Math.random() * 31536000000L);
        } else {
            createdDate *= 1000;
        }

        Log.d("AlbumsFragment", "Parsing album: " + title + " (id: " + id + ", owner: " + ownerId + ", size: " + size + ", created: " + createdDate + ")");

        String thumbSrc = getAlbumThumbSrc(albumObj);

        AlbumItem albumItem = new AlbumItem(id, title, description, size, thumbSrc, createdDate);
        albumItem.setOwnerId(ownerId);

        return albumItem;
    }

    private String getAlbumThumbSrc(JSONObject albumObj) throws JSONException {
        long albumId = albumObj.getLong("id");

        // Проверяем кэш
        if (albumCoverCache.containsKey(albumId)) {
            String cachedUrl = albumCoverCache.get(albumId);
            Log.d("AlbumsFragment", "Using cached cover for album " + albumId + ": " + cachedUrl);
            return cachedUrl;
        }

        String resultUrl = null;

        // Сначала пробуем стандартные методы VK API
        if (albumObj.has("thumb")) {
            JSONObject thumb = albumObj.getJSONObject("thumb");
            String thumbUrl = getBestPhotoUrl(thumb);
            if (thumbUrl != null) {
                Log.d("AlbumsFragment", "Using thumb field: " + thumbUrl);
                resultUrl = thumbUrl;
            }
        }

        if (resultUrl == null && albumObj.has("thumb_src")) {
            String thumbSrc = albumObj.getString("thumb_src");
            if (thumbSrc != null && !thumbSrc.isEmpty()) {
                Log.d("AlbumsFragment", "Using thumb_src field: " + thumbSrc);
                resultUrl = thumbSrc;
            }
        }

        // Если стандартные методы не дали результата, загружаем первую фотографию из альбома
        if (resultUrl == null) {
            String firstPhotoUrl = getFirstPhotoFromAlbum(albumObj);
            if (firstPhotoUrl != null) {
                Log.d("AlbumsFragment", "Using first photo from album: " + firstPhotoUrl);
                resultUrl = firstPhotoUrl;
            }
        }

        // Если ничего не найдено, используем дефолтную обложку
        if (resultUrl == null) {
            resultUrl = getDefaultThumbForAlbum(albumObj);
            Log.d("AlbumsFragment", "Using default thumb: " + resultUrl);
        }

        // Сохраняем в кэш
        if (resultUrl != null) {
            albumCoverCache.put(albumId, resultUrl);
        }

        return resultUrl;
    }

    private String getFirstPhotoFromAlbum(JSONObject albumObj) {
        try {
            Context context = getContext();
            if (context == null) return null;

            String accessToken = TokenManager.getInstance(context).getToken();
            if (accessToken == null || accessToken.isEmpty()) return null;

            long albumId = albumObj.getLong("id");
            long ownerId = albumObj.optLong("owner_id", 0);

            // Если owner_id не указан в альбоме, используем текущего пользователя
            if (ownerId == 0) {
                ownerId = getCurrentUserId(accessToken);
            }

            if (ownerId == 0) return null;

            // Создаем синхронный запрос для получения первой фотографии
            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/photos.get").newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("owner_id", String.valueOf(ownerId))
                    .addQueryParameter("album_id", String.valueOf(albumId))
                    .addQueryParameter("rev", "1") // сортировка от старых к новым
                    .addQueryParameter("count", "1") // только одна фотография
                    .addQueryParameter("photo_sizes", "1")
                    .addQueryParameter("v", API_VERSION)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", getUserAgent())
                    .build();

            // Выполняем синхронный запрос
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                if (jsonResponse.has("response")) {
                    JSONObject responseObj = jsonResponse.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");

                    if (items.length() > 0) {
                        JSONObject firstPhoto = items.getJSONObject(0);
                        return getBestPhotoUrl(firstPhoto);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("AlbumsFragment", "Error getting first photo from album " + albumObj.optLong("id") + ": " + e.getMessage());
        }
        return null;
    }

    private long getCurrentUserId(String accessToken) {
        try {
            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/users.get").newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("v", API_VERSION)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", getUserAgent())
                    .build();

            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                if (jsonResponse.has("response")) {
                    JSONArray responseArray = jsonResponse.getJSONArray("response");
                    if (responseArray.length() > 0) {
                        return responseArray.getJSONObject(0).getLong("id");
                    }
                }
            }
        } catch (Exception e) {
            Log.e("AlbumsFragment", "Error getting current user ID: " + e.getMessage());
        }
        return 0;
    }

    private String getDefaultThumbForAlbum(JSONObject albumObj) throws JSONException {
        long albumId = albumObj.getLong("id");

        switch ((int) albumId) {
            case -6:
                return "https://github.com/sidenevkirill/Sidenevkirill.github.io/blob/master/img/account.png?raw=true";
            case -7:
                return "https://github.com/sidenevkirill/Sidenevkirill.github.io/blob/master/img/news.png?raw=true";
            case -15:
                return "https://github.com/sidenevkirill/Sidenevkirill.github.io/blob/master/img/save.png?raw=true";
            case -9000:
                return "https://github.com/sidenevkirill/Sidenevkirill.github.io/blob/master/img/image.png?raw=true";
            default:
                return "https://github.com/sidenevkirill/Sidenevkirill.github.io/blob/master/img/account.png?raw=true";
        }
    }

    private String getBestPhotoUrl(JSONObject photoObj) throws JSONException {
        if (photoObj.has("sizes")) {
            JSONArray sizesArray = photoObj.getJSONArray("sizes");
            Log.d("AlbumsFragment", "Found sizes array with " + sizesArray.length() + " items");

            String[] preferredTypes = {"w", "z", "y", "x", "m", "s"};

            for (String type : preferredTypes) {
                for (int i = 0; i < sizesArray.length(); i++) {
                    JSONObject sizeObj = sizesArray.getJSONObject(i);
                    if (sizeObj.getString("type").equals(type)) {
                        String url = sizeObj.getString("url");
                        Log.d("AlbumsFragment", "Selected size " + type + ": " + url);
                        return url;
                    }
                }
            }

            if (sizesArray.length() > 0) {
                JSONObject largestSize = sizesArray.getJSONObject(sizesArray.length() - 1);
                String url = largestSize.getString("url");
                Log.d("AlbumsFragment", "Using largest size " + largestSize.getString("type") + ": " + url);
                return url;
            }
        }

        String[] sizes = {"w", "x", "y", "z", "m", "s"};
        for (String size : sizes) {
            if (photoObj.has("photo_" + size)) {
                String url = photoObj.getString("photo_" + size);
                Log.d("AlbumsFragment", "Using direct photo_" + size + ": " + url);
                return url;
            }
        }

        Log.d("AlbumsFragment", "No suitable photo URL found");
        return null;
    }

    private void openAlbumPhotos(AlbumItem albumItem) {
        if (!isAdded()) return;

        Fragment fragment = PhotosFragment.newInstance(albumItem.getId(), albumItem.getTitle());

        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack("album_photos")
                .commit();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_photos, menu);
    }

    static class AlbumItem {
        private long id;
        private String title;
        private String description;
        private int size;
        private String thumbSrc;
        private long createdDate;
        private long ownerId;

        public AlbumItem(long id, String title, String description, int size, String thumbSrc, long createdDate) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.size = size;
            this.thumbSrc = thumbSrc;
            this.createdDate = createdDate;
        }

        public AlbumItem(long id, String title, String description, int size, String thumbSrc) {
            this(id, title, description, size, thumbSrc, System.currentTimeMillis());
        }

        public long getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public int getSize() { return size; }
        public String getThumbSrc() { return thumbSrc; }
        public long getCreatedDate() { return createdDate; }
        public long getOwnerId() { return ownerId; }
        public void setOwnerId(long ownerId) { this.ownerId = ownerId; }
    }

    static class AlbumsAdapter extends RecyclerView.Adapter<AlbumsAdapter.ViewHolder> {
        private List<AlbumItem> albums;
        private OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(AlbumItem album);
        }

        public AlbumsAdapter(List<AlbumItem> albums, OnItemClickListener listener) {
            this.albums = albums;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_album, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AlbumItem album = albums.get(position);

            loadAlbumCover(holder, album);

            holder.albumTitle.setText(album.getTitle());
            holder.albumCount.setText(formatPhotoCount(album.getSize()));

            if (album.getDescription() != null && !album.getDescription().isEmpty()) {
                holder.albumDescription.setText(album.getDescription());
                holder.albumDescription.setVisibility(View.VISIBLE);
            } else {
                holder.albumDescription.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(album);
                }
            });
        }

        private void loadAlbumCover(ViewHolder holder, AlbumItem album) {
            Context context = holder.itemView.getContext();

            Glide.with(context)
                    .load(album.getThumbSrc())
                    .placeholder(R.drawable.img)
                    .error(R.drawable.img)
                    .override(400, 400)
                    .centerCrop()
                    .addListener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    Target<Drawable> target, boolean isFirstResource) {
                            Log.e("AlbumsAdapter", "Failed to load album cover: " + album.getThumbSrc() +
                                    ", Error: " + (e != null ? e.getMessage() : "unknown"));
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model,
                                                       Target<Drawable> target, DataSource dataSource,
                                                       boolean isFirstResource) {
                            Log.d("AlbumsAdapter", "Successfully loaded album cover: " + album.getTitle());
                            return false;
                        }
                    })
                    .into(holder.albumImageView);
        }

        private String formatPhotoCount(int count) {
            if (count == 0) {
                return "Нет фото";
            } else if (count == 1) {
                return "1 фото";
            } else if (count < 5) {
                return count + " фото";
            } else {
                return count + " фотографий";
            }
        }

        @Override
        public int getItemCount() {
            return albums.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView albumImageView;
            TextView albumTitle;
            TextView albumDescription;
            TextView albumCount;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                albumImageView = itemView.findViewById(R.id.album_image);
                albumTitle = itemView.findViewById(R.id.album_title);
                albumDescription = itemView.findViewById(R.id.album_description);
                albumCount = itemView.findViewById(R.id.album_count);
            }
        }
    }
}