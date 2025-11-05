package ru.lisdevs.messenger.stories;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.StoryItem;

public class StoryViewerFragment extends Fragment {
    private ViewPager2 viewPager;
    private TextView textViewCounter;
    private ImageView imageViewBack;
    private ImageView imageViewDownload;
    private ImageView imageViewShare;
    private List<StoryItem> storyItems;
    private int currentPosition;
    private StoryPagerAdapter pagerAdapter;
    private OkHttpClient httpClient;
    private boolean controlsVisible = true;

    public static StoryViewerFragment newInstance(ArrayList<StoryItem> storyItems, int currentPosition) {
        StoryViewerFragment fragment = new StoryViewerFragment();
        Bundle args = new Bundle();
        args.putSerializable("story_items", storyItems);
        args.putInt("current_position", currentPosition);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Bundle args = getArguments();
        if (args != null) {
            storyItems = (ArrayList<StoryItem>) args.getSerializable("story_items");
            currentPosition = args.getInt("current_position", 0);
        }

        if (storyItems == null) {
            storyItems = new ArrayList<>();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_story_viewer, container, false);

        viewPager = view.findViewById(R.id.viewPager);
        textViewCounter = view.findViewById(R.id.textViewCounter);
        imageViewBack = view.findViewById(R.id.imageViewBack);
        imageViewDownload = view.findViewById(R.id.imageViewDownload);
        imageViewShare = view.findViewById(R.id.imageViewShare);

        setupViewPager();
        setupControls();
        updateCounter();

        return view;
    }

    private void setupViewPager() {
        pagerAdapter = new StoryPagerAdapter(storyItems, this::toggleControls);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setCurrentItem(currentPosition, false);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updateCounter();

                // Автоматически скрываем контролы при перелистывании
                if (!controlsVisible) {
                    toggleControls();
                }
            }
        });
    }

    private void setupControls() {
        imageViewBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });

        imageViewDownload.setOnClickListener(v -> downloadCurrentStory());

        imageViewShare.setOnClickListener(v -> shareCurrentStory());

        // Обработчик клика по самому ViewPager для скрытия/показа контролов
        viewPager.setOnTouchListener(new View.OnTouchListener() {
            private GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    toggleControls();
                    return true;
                }
            });

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return false;
            }
        });
    }

    void toggleControls() {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            controlsVisible = !controlsVisible;
            int visibility = controlsVisible ? View.VISIBLE : View.GONE;

            textViewCounter.setVisibility(visibility);
            imageViewBack.setVisibility(visibility);
            imageViewDownload.setVisibility(visibility);
            imageViewShare.setVisibility(visibility);
        });
    }

    private void updateCounter() {
        if (getActivity() == null || textViewCounter == null || storyItems == null) return;

        getActivity().runOnUiThread(() -> {
            String counterText = (currentPosition + 1) + " / " + storyItems.size();
            textViewCounter.setText(counterText);
        });
    }

    private void downloadCurrentStory() {
        if (currentPosition < 0 || currentPosition >= storyItems.size()) {
            return;
        }

        StoryItem story = storyItems.get(currentPosition);
        String storyUrl = story.getMediaUrl();
        String fileName = "story_" + System.currentTimeMillis() + ".jpg";

        Context context = getContext();
        if (context == null) return;

        android.app.DownloadManager downloadManager = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            Toast.makeText(context, "Не удалось начать скачивание", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = Uri.parse(storyUrl);
            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(uri);
            request.setTitle("Скачивание истории");
            request.setDescription(story.getText());
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, fileName);
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setMimeType("image/jpeg");

            downloadManager.enqueue(request);
            Toast.makeText(context, "Скачивание начато", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Ошибка при скачивании", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCurrentStory() {
        if (currentPosition < 0 || currentPosition >= storyItems.size()) {
            return;
        }

        StoryItem story = storyItems.get(currentPosition);
        String storyUrl = story.getMediaUrl();
        downloadAndShareStory(storyUrl, story.getText());
    }

    private void downloadAndShareStory(String storyUrl, String text) {
        Request request = new Request.Builder()
                .url(storyUrl)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Ошибка загрузки истории", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        byte[] imageData = body.bytes();
                        shareStory(imageData, text);
                    }
                } else {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() ->
                                Toast.makeText(activity, "Ошибка загрузки истории", Toast.LENGTH_SHORT).show());
                    }
                }
            }
        });
    }

    private void shareStory(byte[] imageData, String text) {
        Activity activity = getActivity();
        if (activity == null) return;

        try {
            File cachePath = new File(activity.getCacheDir(), "images");
            if (!cachePath.exists()) {
                cachePath.mkdirs();
            }
            File file = new File(cachePath, "shared_story_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream stream = new FileOutputStream(file);
            stream.write(imageData);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/jpeg");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Поделиться историей"));

        } catch (IOException e) {
            activity.runOnUiThread(() ->
                    Toast.makeText(activity, "Ошибка при обмене историей", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    // Интерфейс для обратного вызова
    public interface OnControlsToggleListener {
        void onControlsToggle();
    }

    // Адаптер для ViewPager2
    class StoryPagerAdapter extends RecyclerView.Adapter<StoryPagerAdapter.StoryViewHolder> {
        private List<StoryItem> storyItems;
        private OnControlsToggleListener controlsToggleListener;

        public StoryPagerAdapter(List<StoryItem> storyItems, OnControlsToggleListener listener) {
            this.storyItems = storyItems;
            this.controlsToggleListener = listener;
        }

        @NonNull
        @Override
        public StoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_story_viewer, parent, false);
            return new StoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull StoryViewHolder holder, int position) {
            StoryItem story = storyItems.get(position);
            holder.bind(story);
        }

        @Override
        public int getItemCount() {
            return storyItems.size();
        }

        class StoryViewHolder extends RecyclerView.ViewHolder {
            private ImageView storyView;
            private TextView textViewStoryText;
            private TextView textViewStoryInfo;
            private GestureDetector gestureDetector;
            private float scaleFactor = 1.0f;
            private ScaleGestureDetector scaleGestureDetector;

            public StoryViewHolder(@NonNull View itemView) {
                super(itemView);
                storyView = itemView.findViewById(R.id.storyView);
                textViewStoryText = itemView.findViewById(R.id.textViewStoryText);
                textViewStoryInfo = itemView.findViewById(R.id.textViewStoryInfo);
                initGestureDetectors();
            }

            private void initGestureDetectors() {
                // Детектор жестов для скрытия контролов
                gestureDetector = new GestureDetector(itemView.getContext(), new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        if (controlsToggleListener != null) {
                            controlsToggleListener.onControlsToggle();
                        }
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        // Простое масштабирование при двойном тапе
                        if (scaleFactor == 1.0f) {
                            scaleFactor = 2.0f;
                        } else {
                            scaleFactor = 1.0f;
                        }
                        storyView.setScaleX(scaleFactor);
                        storyView.setScaleY(scaleFactor);
                        return true;
                    }
                });

                // Детектор масштабирования
                scaleGestureDetector = new ScaleGestureDetector(itemView.getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        scaleFactor *= detector.getScaleFactor();
                        scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f)); // Ограничиваем масштаб
                        storyView.setScaleX(scaleFactor);
                        storyView.setScaleY(scaleFactor);
                        return true;
                    }
                });

                // Обработка касаний для ImageView
                storyView.setOnTouchListener((v, event) -> {
                    gestureDetector.onTouchEvent(event);
                    scaleGestureDetector.onTouchEvent(event);
                    return true;
                });
            }

            public void bind(StoryItem story) {
                // Загружаем изображение истории
                Glide.with(itemView.getContext())
                        .load(story.getMediaUrl())
                        .placeholder(R.drawable.img)
                        .error(R.drawable.img)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(storyView);

                // Устанавливаем текст истории
                if (story.getText() != null && !story.getText().isEmpty()) {
                    textViewStoryText.setText(story.getText());
                    textViewStoryText.setVisibility(View.VISIBLE);
                } else {
                    textViewStoryText.setVisibility(View.GONE);
                }

                // Устанавливаем информацию о истории
                String info = formatStoryInfo(story);
                textViewStoryInfo.setText(info);

                // Сбрасываем масштаб при загрузке нового изображения
                storyView.setScaleX(1.0f);
                storyView.setScaleY(1.0f);
                scaleFactor = 1.0f;
            }

            private String formatStoryInfo(StoryItem story) {
                StringBuilder info = new StringBuilder();

                if (story.getViewsCount() > 0) {
                    info.append("👁 ").append(formatViews(story.getViewsCount()));
                }

                if (story.getDate() > 0) {
                    if (info.length() > 0) info.append(" • ");
                    info.append("🕒 ").append(formatTime(story.getDate()));
                }

                if (story.isExpired()) {
                    if (info.length() > 0) info.append(" • ");
                    info.append("⏰ Истекла");
                }

                return info.toString();
            }

            private String formatViews(int views) {
                if (views >= 1000000) {
                    return String.format(Locale.getDefault(), "%.1fM", views / 1000000.0);
                } else if (views >= 1000) {
                    return String.format(Locale.getDefault(), "%.1fK", views / 1000.0);
                } else {
                    return String.valueOf(views);
                }
            }

            private String formatTime(long timestamp) {
                long currentTime = System.currentTimeMillis() / 1000;
                long diff = currentTime - timestamp;

                if (diff < 60) {
                    return "только что";
                } else if (diff < 3600) {
                    long minutes = diff / 60;
                    return minutes + " мин. назад";
                } else if (diff < 86400) {
                    long hours = diff / 3600;
                    return hours + " ч. назад";
                } else {
                    long days = diff / 86400;
                    return days + " дн. назад";
                }
            }
        }
    }
}