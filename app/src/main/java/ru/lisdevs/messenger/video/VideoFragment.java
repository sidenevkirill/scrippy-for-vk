package ru.lisdevs.messenger.video;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.utils.TokenManager;

public class VideoFragment extends Fragment {

    private static final String TAG = "VideoFragment";

    private String accessToken;
    private String ownerId;

    private RecyclerView recyclerView;
    private VideoAdapter adapter;
    private List<VideoItem> videoList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private OkHttpClient httpClient;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Инициализация OkHttpClient
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: Создание фрагмента");
        View view = inflater.inflate(R.layout.fragment_video, container, false);

        // Инициализация SwipeRefreshLayout
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            Log.d(TAG, "SwipeRefreshLayout: Обновление видео");
            fetchVKVideos();
        });

        recyclerView = view.findViewById(R.id.recyclerViewVideos);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new VideoAdapter(videoList);
        recyclerView.setAdapter(adapter);

        // Получаем токен и user_id
        accessToken = TokenManager.getInstance(getContext()).getToken();
        ownerId = getUserId(getContext());

        Log.d(TAG, "accessToken: " + accessToken);
        Log.d(TAG, "ownerId: " + ownerId);

        // Первоначальная загрузка видео
        fetchVKVideos();

        return view;
    }

    private void fetchVKVideos() {
        Log.d(TAG, "fetchVKVideos: Начинаем загрузку видео");
        getActivity().runOnUiThread(() -> {
            Log.d(TAG, "Устанавливаем SwipeRefreshLayout в true");
            swipeRefreshLayout.setRefreshing(true);
        });

        new Thread(() -> {
            String response = getVKVideos();
            if (response != null && !response.startsWith("error")) {
                Log.d(TAG, "Ответ получен успешно");
                parseAndDisplayVideos(response);
            } else {
                Log.e(TAG, "Ошибка при получении видео: " + response);
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Ошибка при получении видео", Toast.LENGTH_SHORT).show();
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
        }).start();
    }

    private String getVKVideos() {
        String apiVersion = "5.131";
        try {
            // Используем OkHttpClient для запроса с User-Agent
            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/video.get")
                    .newBuilder()
                    .addQueryParameter("owner_id", ownerId)
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("v", apiVersion)
                    .addQueryParameter("count", "100") // Добавляем лимит
                    .addQueryParameter("extended", "1") // Получаем дополнительную информацию
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", getUserAgent())
                    .build();

            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                Log.d(TAG, "HTTP 200 OK");
                Log.d(TAG, "Ответ API: " + responseBody);
                return responseBody;
            } else {
                Log.e(TAG, "HTTP ошибка: код ответа " + response.code());
                return "error: HTTP " + response.code();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Исключение при запросе API: ", e);
            return "error:" + e.getMessage();
        }
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


    private void parseAndDisplayVideos(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            if (jsonObject.has("error")) {
                JSONObject errorObj = jsonObject.getJSONObject("error");
                String errorMsg = errorObj.optString("error_msg", "Неизвестная ошибка");
                Log.e(TAG, "API ошибка: " + errorMsg);
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Ошибка API: " + errorMsg, Toast.LENGTH_LONG).show()
                );
                getActivity().runOnUiThread(() -> swipeRefreshLayout.setRefreshing(false));
                return;
            }

            JSONObject responseObj = jsonObject.getJSONObject("response");
            JSONArray itemsArray = responseObj.getJSONArray("items");

            List<VideoItem> tempList = new ArrayList<>();
            for (int i = 0; i < itemsArray.length(); i++) {
                JSONObject videoObj = itemsArray.getJSONObject(i);
                String title = videoObj.optString("title");
                String description = videoObj.optString("description");
                String playerUrl = videoObj.optString("player"); // ссылка на видео

                // Получаем информацию о превью
                String thumbUrl = getBestThumbnail(videoObj);
                int duration = videoObj.optInt("duration", 0);
                int views = videoObj.optInt("views", 0);
                long date = videoObj.optLong("date", 0);

                tempList.add(new VideoItem(title, description, playerUrl, thumbUrl, duration, views, date));
                Log.d(TAG, "Добавлено видео: " + title);
            }

            getActivity().runOnUiThread(() -> {
                videoList.clear();
                videoList.addAll(tempList);
                adapter.notifyDataSetChanged();

                Log.d(TAG, "Обновление адаптера завершено. Загружено видео: " + videoList.size());
                swipeRefreshLayout.setRefreshing(false);
            });

        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, "Ошибка парсинга JSON", e);
            getActivity().runOnUiThread(() -> swipeRefreshLayout.setRefreshing(false));
        }
    }

    private String getBestThumbnail(JSONObject videoObj) throws JSONException {
        // Пытаемся получить лучшее доступное изображение
        if (videoObj.has("image")) {
            JSONArray images = videoObj.getJSONArray("image");
            for (int i = images.length() - 1; i >= 0; i--) {
                JSONObject image = images.getJSONObject(i);
                String url = image.optString("url");
                if (url != null && !url.isEmpty()) {
                    return url;
                }
            }
        }

        // Альтернативные варианты получения превью
        if (videoObj.has("photo_800")) {
            return videoObj.optString("photo_800");
        } else if (videoObj.has("photo_640")) {
            return videoObj.optString("photo_640");
        } else if (videoObj.has("photo_320")) {
            return videoObj.optString("photo_320");
        } else if (videoObj.has("first_frame_800")) {
            return videoObj.optString("first_frame_800");
        } else if (videoObj.has("first_frame_320")) {
            return videoObj.optString("first_frame_320");
        }

        return null;
    }

    static class VideoItem {
        String title;
        String description;
        String url;
        String thumbnailUrl;
        int duration;
        int views;
        long date;

        public VideoItem(String title, String description, String url, String thumbnailUrl, int duration, int views, long date) {
            this.title = title;
            this.description = description;
            this.url = url;
            this.thumbnailUrl = thumbnailUrl;
            this.duration = duration;
            this.views = views;
            this.date = date;
        }
    }

    class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.ViewHolder> {

        private List<VideoItem> videos;

        public VideoAdapter(List<VideoItem> videos) {
            this.videos = videos;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            VideoItem item = videos.get(position);

            holder.titleText.setText(item.title);
            holder.descriptionText.setText(item.description);

            // Загрузка превью, если доступно
            if (item.thumbnailUrl != null && !item.thumbnailUrl.isEmpty()) {
                Glide.with(requireContext())
                        .load(item.thumbnailUrl)
                        .placeholder(R.drawable.img)
                        .error(R.drawable.img)
                        .into(holder.thumbnailImage);
            }

            // Установка длительности
            if (item.duration > 0) {
                holder.durationText.setText(formatDuration(item.duration));
                holder.durationText.setVisibility(View.VISIBLE);
            } else {
                holder.durationText.setVisibility(View.GONE);
            }

            // Установка количества просмотров
            if (item.views > 0) {
                holder.viewsText.setText(formatViews(item.views));
                holder.viewsText.setVisibility(View.VISIBLE);
            } else {
                holder.viewsText.setVisibility(View.GONE);
            }

            // Обработка клика по всему элементу (открытие видео)
            holder.itemView.setOnClickListener(v -> {
                Log.d(TAG, "Клик по видео: " + item.title + " URL: " + item.url);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
                startActivity(intent);
            });

            // Обработка клика по кнопке "Скачать"
            holder.downloadButton.setOnClickListener(v -> {
                Log.d(TAG, "Клик по кнопке скачивания для видео: " + item.title);
                downloadVideo(item.url, item.title);
            });
        }

        @Override
        public int getItemCount() {
            return videos.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView titleText;
            TextView descriptionText;
            TextView durationText;
            TextView viewsText;
            ImageView thumbnailImage;
            Button downloadButton;

            public ViewHolder(View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.textTitle);
                descriptionText = itemView.findViewById(R.id.textDescription);
                durationText = itemView.findViewById(R.id.durationText);
                viewsText = itemView.findViewById(R.id.viewsText);
                thumbnailImage = itemView.findViewById(R.id.thumbnailImage);
                downloadButton = itemView.findViewById(R.id.buttonDownload);
            }
        }
    }

    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int hours = minutes / 60;
        minutes = minutes % 60;
        seconds = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    private String formatViews(int views) {
        if (views >= 1000000) {
            return String.format(Locale.getDefault(), "%.1fM просмотров", views / 1000000.0);
        } else if (views >= 1000) {
            return String.format(Locale.getDefault(), "%.1fK просмотров", views / 1000.0);
        } else {
            return views + " просмотров";
        }
    }

    // Метод для скачивания видео через DownloadManager
    private void downloadVideo(String urlStr, String title) {
        Log.d(TAG, "Начинаем скачивание файла: " + title + " URL: " + urlStr);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
            Log.w(TAG, "Запрос разрешения на запись в хранилище");
            return;
        }

        try {
            Uri uri = Uri.parse(urlStr);
            DownloadManager.Request request = new DownloadManager.Request(uri);

            request.setTitle(title);
            request.setDescription("Скачивание видео");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);

            // Указываем папку для сохранения файла
            String fileName = title.replaceAll("[^a-zA-Z0-9а-яА-Я\\s]", "") + ".mp4";
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager manager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);

            if (manager != null) {
                manager.enqueue(request);
                Log.i(TAG, "Задача скачивания добавлена в очередь");
                Toast.makeText(getContext(), "Начато скачивание: " + title, Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "DownloadManager не доступен");
                Toast.makeText(getContext(), "Ошибка доступа к менеджеру загрузок", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Ошибка при скачивании", e);
            Toast.makeText(getContext(), "Ошибка при скачивании: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Обработка результата запроса разрешений
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Разрешение на запись получено");
            Toast.makeText(getContext(), "Разрешение получено. Повторите попытку скачивания.", Toast.LENGTH_SHORT).show();
        } else {
            Log.w(TAG, "Разрешение отклонено");
            Toast.makeText(getContext(), "Разрешение отклонено.", Toast.LENGTH_SHORT).show();
        }
    }

    public String getUserId(Context context) {
        String userId = context.getSharedPreferences("VK", Context.MODE_PRIVATE).getString("user_id", null);
        Log.d(TAG, "Полученный user_id из SharedPreferences: " + userId);
        return userId;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Закрываем OkHttpClient при уничтожении фрагмента
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }
}