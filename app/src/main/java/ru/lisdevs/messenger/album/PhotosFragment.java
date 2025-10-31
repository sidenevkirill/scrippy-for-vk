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
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.friends.PhotoViewerFragment;
import ru.lisdevs.messenger.utils.Authorizer;
import ru.lisdevs.messenger.utils.TokenManager;

public class PhotosFragment extends Fragment {
    private RecyclerView recyclerViewPhotos;
    private PhotosAdapter photosAdapter;
    private List<PhotoItem> photoList = new ArrayList<>();
    private Toolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private OkHttpClient httpClient;
    private static final String API_VERSION = "5.131";
    private TextView textViewPhotosCount;
    private TextView textViewUserName;
    private int totalPhotosCount = 0;

    private long albumId;
    private String albumTitle;

    // В классе PhotosFragment добавьте:
    public static PhotosFragment newInstance(long albumId, String albumTitle) {
        PhotosFragment fragment = new PhotosFragment();
        Bundle args = new Bundle();
        args.putLong("album_id", albumId);
        args.putString("album_title", albumTitle);
        fragment.setArguments(args);
        return fragment;
    }

    // Тестовые фотографии для демонстрации
    private final String[] TEST_PHOTOS = {
            "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=500",
            "https://images.unsplash.com/photo-1529778873920-4da4926a72c2?w=500",
            "https://images.unsplash.com/photo-1518756131217-31eb79b20e8f?w=500",
            "https://images.unsplash.com/photo-1551963831-b3b1ca40c98e?w=500",
            "https://images.unsplash.com/photo-1470093851219-69951fcbb533?w=500",
            "https://images.unsplash.com/photo-1501854140801-50d01698950b?w=500",
            "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=500",
            "https://images.unsplash.com/photo-1505144808419-1957a94ca61e?w=500",
            "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=500",
            "https://images.unsplash.com/photo-1433086966358-54859d0ed716?w=500"
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        // Получение аргументов
        Bundle args = getArguments();
        if (args != null) {
            albumId = args.getLong("album_id", -1); // -1 означает все фотографии
            albumTitle = args.getString("album_title", "Фотографии");
            Log.d("PhotosFragment", "Received albumId: " + albumId + ", albumTitle: " + albumTitle);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photos_list, container, false);

        recyclerViewPhotos = view.findViewById(R.id.recyclerView);
        toolbar = view.findViewById(R.id.toolbar);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        textViewPhotosCount = view.findViewById(R.id.count);
        textViewUserName = view.findViewById(R.id.group_name);

        setupUI();
        setupToolbar();
        setupSwipeRefresh();
        setupUserInfo();

        // Загрузка данных
        if (isTestAccount()) {
            loadTestPhotos();
        } else {
            fetchMyPhotos();
            getPhotosCount();
        }

        return view;
    }

    private boolean isTestAccount() {
        // Проверяем наличие валидного токена
        Context context = getContext();
        if (context == null) return true;

        String accessToken = TokenManager.getInstance(context).getToken();
        return accessToken == null || accessToken.isEmpty() || accessToken.equals("test_token");
    }

    private void loadTestPhotos() {
        swipeRefreshLayout.setRefreshing(true);

        List<PhotoItem> testPhotos = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < TEST_PHOTOS.length; i++) {
            testPhotos.add(new PhotoItem(
                    i + 1,
                    0, // ownerId для тестовых фото
                    TEST_PHOTOS[i],
                    System.currentTimeMillis() / 1000 - random.nextInt(1000000),
                    random.nextInt(1000),
                    random.nextInt(100),
                    "Тестовое фото " + (i + 1)
            ));
        }

        requireActivity().runOnUiThread(() -> {
            photoList.clear();
            photoList.addAll(testPhotos);
            photosAdapter.notifyDataSetChanged();
            swipeRefreshLayout.setRefreshing(false);

            // Обновляем счетчик
            totalPhotosCount = testPhotos.size();
            updatePhotosCountText();

            Toast.makeText(getContext(), "Показаны тестовые фотографии", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupUI() {
        // Настройка RecyclerView для фотографий (сетка 3 колонки)
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
        recyclerViewPhotos.setLayoutManager(layoutManager);
        photosAdapter = new PhotosAdapter(photoList, this::openPhotoViewer);
        recyclerViewPhotos.setAdapter(photosAdapter);
    }

    private void setupToolbar() {
        if (toolbar != null && getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                activity.getSupportActionBar().setTitle(albumTitle);
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
            textViewUserName.setText(albumTitle);
        }
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Сбрасываем счетчик
            totalPhotosCount = 0;
            updatePhotosCountText();

            if (isTestAccount()) {
                loadTestPhotos();
            } else {
                fetchMyPhotos();
                getPhotosCount();
            }
        });
    }

    private void getPhotosCount() {
        Context context = getContext();
        if (context == null) {
            updatePhotosCountText();
            return;
        }

        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null || accessToken.isEmpty()) {
            updatePhotosCountText();
            return;
        }

        // Создаем базовый URL
        HttpUrl baseUrl = HttpUrl.parse("https://api.vk.com/method/photos.get");
        if (baseUrl == null) {
            updatePhotosCountText();
            return;
        }

        // Строим URL с параметрами
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("count", "0")
                .addQueryParameter("v", API_VERSION);

        // Добавляем album_id только если он указан
        if (albumId != -1) {
            urlBuilder.addQueryParameter("album_id", String.valueOf(albumId));
        } else {
            urlBuilder.addQueryParameter("album_id", "all");
        }

        HttpUrl url = urlBuilder.build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("PhotosFragment", "Error getting photos count: " + e.getMessage());
                updatePhotosCountText();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        updatePhotosCountText();
                        return;
                    }

                    String responseBody = response.body().string();
                    parsePhotosCountResponse(responseBody);
                } catch (Exception e) {
                    Log.e("PhotosFragment", "Error parsing photos count: " + e.getMessage());
                    updatePhotosCountText();
                }
            }
        });
    }

    private void parsePhotosCountResponse(String json) throws JSONException {
        JSONObject jsonObject = new JSONObject(json);

        if (jsonObject.has("error")) {
            updatePhotosCountText();
            return;
        }

        if (jsonObject.has("response")) {
            JSONObject response = jsonObject.getJSONObject("response");
            int count = response.optInt("count", 0);
            totalPhotosCount = count;
            updatePhotosCountText();
        } else {
            updatePhotosCountText();
        }
    }

    private void updatePhotosCountText() {
        Activity activity = getActivity();
        if (activity != null && textViewPhotosCount != null) {
            activity.runOnUiThread(() -> {
                String countText;
                if (isTestAccount()) {
                    countText = "Тестовые фотографии: " + totalPhotosCount;
                } else {
                    countText = totalPhotosCount > 0 ?
                            "Фотографии: " + totalPhotosCount : "Нет фотографий";
                }
                textViewPhotosCount.setText(countText);
            });
        }
    }

    private void fetchMyPhotos() {
        Context context = getContext();
        if (context == null) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null) {
            swipeRefreshLayout.setRefreshing(false);
            loadTestPhotos();
            return;
        }

        // Создаем базовый URL
        HttpUrl baseUrl = HttpUrl.parse("https://api.vk.com/method/photos.get");
        if (baseUrl == null) {
            swipeRefreshLayout.setRefreshing(false);
            loadTestPhotos();
            return;
        }

        // Строим URL с параметрами
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("count", "100")
                .addQueryParameter("rev", "1")
                .addQueryParameter("extended", "1")
                .addQueryParameter("v", API_VERSION);

        // Добавляем album_id только если он указан
        if (albumId != -1) {
            urlBuilder.addQueryParameter("album_id", String.valueOf(albumId));
        } else {
            urlBuilder.addQueryParameter("album_id", "all");
        }

        HttpUrl url = urlBuilder.build();

        Log.d("PhotosFragment", "Fetching photos for album: " + albumId);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        swipeRefreshLayout.setRefreshing(true);

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("PhotosFragment", "Network error: " + e.getMessage());
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Ошибка загрузки фотографий", Toast.LENGTH_SHORT).show();
                    swipeRefreshLayout.setRefreshing(false);
                    loadTestPhotos();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body().string();
                Log.d("PhotosFragment", "API Response: " + responseBody);

                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        if (jsonObject.has("response")) {
                            JSONObject responseObj = jsonObject.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");
                            List<PhotoItem> newPhotoList = new ArrayList<>();

                            Log.d("PhotosFragment", "Found " + items.length() + " photos");

                            for (int i = 0; i < items.length(); i++) {
                                JSONObject photoObj = items.getJSONObject(i);
                                PhotoItem photoItem = parsePhotoItem(photoObj);
                                if (photoItem != null) {
                                    newPhotoList.add(photoItem);
                                }
                            }

                            requireActivity().runOnUiThread(() -> {
                                if (newPhotoList.isEmpty()) {
                                    Toast.makeText(getContext(), "В альбоме нет фотографий", Toast.LENGTH_SHORT).show();
                                    loadTestPhotos();
                                } else {
                                    photoList.clear();
                                    photoList.addAll(newPhotoList);
                                    photosAdapter.notifyDataSetChanged();
                                    //Toast.makeText(getContext(), "Загружено " + newPhotoList.size() + " фотографий", Toast.LENGTH_SHORT).show();
                                }
                                swipeRefreshLayout.setRefreshing(false);
                            });
                        } else if (jsonObject.has("error")) {
                            JSONObject errorObj = jsonObject.getJSONObject("error");
                            String errorMsg = errorObj.optString("error_msg", "Unknown error");
                            int errorCode = errorObj.optInt("error_code", 0);

                            Log.e("PhotosFragment", "API Error " + errorCode + ": " + errorMsg);

                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Ошибка: " + errorMsg, Toast.LENGTH_SHORT).show();
                                swipeRefreshLayout.setRefreshing(false);
                                loadTestPhotos();
                            });
                        }
                    } catch (JSONException e) {
                        Log.e("PhotosFragment", "JSON parsing error: " + e.getMessage());
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                            swipeRefreshLayout.setRefreshing(false);
                            loadTestPhotos();
                        });
                    }
                } else {
                    Log.e("PhotosFragment", "HTTP error: " + response.code());
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка сервера: " + response.code(), Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                        loadTestPhotos();
                    });
                }
            }
        });
    }

    // Метод для определения User-Agent
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

    private PhotoItem parsePhotoItem(JSONObject photoObj) throws JSONException {
        long id = photoObj.getLong("id");
        long ownerId = photoObj.getLong("owner_id");

        // Получаем URL фотографии в разных размерах
        String photoUrl = getBestPhotoUrl(photoObj);
        if (photoUrl == null) {
            return null;
        }

        // Получаем дату создания
        long date = photoObj.getLong("date");

        // Получаем количество лайков и комментариев
        int likesCount = 0;
        int commentsCount = 0;

        if (photoObj.has("likes")) {
            JSONObject likes = photoObj.getJSONObject("likes");
            likesCount = likes.getInt("count");
        }

        if (photoObj.has("comments")) {
            JSONObject comments = photoObj.getJSONObject("comments");
            commentsCount = comments.getInt("count");
        }

        // Получаем текст описания
        String text = photoObj.optString("text", "");

        return new PhotoItem(id, ownerId, photoUrl, date, likesCount, commentsCount, text);
    }

    private String getBestPhotoUrl(JSONObject photoObj) throws JSONException {
        // Приоритет размеров: x -> y -> z -> m -> s
        String[] sizes = {"x", "y", "z", "m", "s"};

        for (String size : sizes) {
            if (photoObj.has("photo_" + size)) {
                return photoObj.getString("photo_" + size);
            }
        }

        // Если нет стандартных размеров, проверяем массив sizes
        if (photoObj.has("sizes")) {
            JSONArray sizesArray = photoObj.getJSONArray("sizes");
            // Ищем самый большой размер
            for (int i = sizesArray.length() - 1; i >= 0; i--) {
                JSONObject sizeObj = sizesArray.getJSONObject(i);
                String type = sizeObj.getString("type");
                if (type.equals("x") || type.equals("y") || type.equals("z")) {
                    return sizeObj.getString("url");
                }
            }
            // Если не нашли большой, берем последний (самый большой доступный)
            if (sizesArray.length() > 0) {
                return sizesArray.getJSONObject(sizesArray.length() - 1).getString("url");
            }
        }

        return null;
    }

    private void openPhotoViewer(PhotoItem photoItem) {
        // Создаем список URL всех фотографий для просмотра
        List<String> photoUrls = new ArrayList<>();
        int currentPosition = -1;

        for (int i = 0; i < photoList.size(); i++) {
            PhotoItem item = photoList.get(i);
            photoUrls.add(item.getPhotoUrl());
            if (item.getId() == photoItem.getId()) {
                currentPosition = i;
            }
        }

        if (currentPosition == -1) {
            currentPosition = 0;
        }

        // Открываем фрагмент просмотра фотографий
        PhotoViewerFragment photoViewerFragment = PhotoViewerFragment.newInstance(
                new ArrayList<>(photoUrls),
                currentPosition
        );

        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.container, photoViewerFragment)
                .addToBackStack("photo_viewer")
                .commit();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_photos, menu);
    }

    // Классы данных
    static class PhotoItem {
        private long id;
        private long ownerId;
        private String photoUrl;
        private long date;
        private int likesCount;
        private int commentsCount;
        private String text;

        public PhotoItem(long id, long ownerId, String photoUrl, long date,
                         int likesCount, int commentsCount, String text) {
            this.id = id;
            this.ownerId = ownerId;
            this.photoUrl = photoUrl;
            this.date = date;
            this.likesCount = likesCount;
            this.commentsCount = commentsCount;
            this.text = text;
        }

        public long getId() { return id; }
        public long getOwnerId() { return ownerId; }
        public String getPhotoUrl() { return photoUrl; }
        public long getDate() { return date; }
        public int getLikesCount() { return likesCount; }
        public int getCommentsCount() { return commentsCount; }
        public String getText() { return text; }
    }

    static class PhotosAdapter extends RecyclerView.Adapter<PhotosAdapter.ViewHolder> {
        private List<PhotoItem> photos;
        private OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(PhotoItem photo);
        }

        public PhotosAdapter(List<PhotoItem> photos, OnItemClickListener listener) {
            this.photos = photos;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_photo, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PhotoItem photo = photos.get(position);

            // Загрузка изображения с помощью Glide
            Glide.with(holder.itemView.getContext())
                    .load(photo.getPhotoUrl())
                    .placeholder(R.drawable.img)
                    .error(R.drawable.img)
                    .centerCrop()
                    .into(holder.photoImageView);

            // Показываем количество лайков, если есть
            if (photo.getLikesCount() > 0) {
                holder.likesTextView.setText(String.valueOf(photo.getLikesCount()));
                holder.likesTextView.setVisibility(View.VISIBLE);
            } else {
                holder.likesTextView.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(photo);
                }
            });
        }

        @Override
        public int getItemCount() {
            return photos.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView photoImageView;
            TextView likesTextView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                photoImageView = itemView.findViewById(R.id.photo_image);
                likesTextView = itemView.findViewById(R.id.photo_description);
            }
        }
    }
}