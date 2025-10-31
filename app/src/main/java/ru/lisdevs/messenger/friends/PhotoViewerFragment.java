package ru.lisdevs.messenger.friends;

import android.app.Activity;
import android.app.DownloadManager;
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
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import ru.lisdevs.messenger.R;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public class PhotoViewerFragment extends Fragment {
    private ViewPager2 viewPager;
    private TextView textViewCounter;
    private ImageView imageViewBack;
    private ImageView imageViewDownload;
    private ImageView imageViewShare;
    private List<String> photoUrls;
    private int currentPosition;
    private PhotoPagerAdapter pagerAdapter;
    private OkHttpClient httpClient;
    private boolean controlsVisible = true;

    public static PhotoViewerFragment newInstance(ArrayList<String> photoUrls, int currentPosition) {
        PhotoViewerFragment fragment = new PhotoViewerFragment();
        Bundle args = new Bundle();
        args.putStringArrayList("photo_urls", photoUrls);
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
            photoUrls = args.getStringArrayList("photo_urls");
            currentPosition = args.getInt("current_position", 0);
        }

        if (photoUrls == null) {
            photoUrls = new ArrayList<>();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photo_viewer, container, false);

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
        pagerAdapter = new PhotoPagerAdapter(photoUrls, this::toggleControls);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setCurrentItem(currentPosition, false);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updateCounter();
            }
        });
    }

    private void setupControls() {
        imageViewBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });

        imageViewDownload.setOnClickListener(v -> downloadCurrentPhoto());

        imageViewShare.setOnClickListener(v -> shareCurrentPhoto());

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
        if (getActivity() == null || textViewCounter == null || photoUrls == null) return;

        getActivity().runOnUiThread(() -> {
            String counterText = (currentPosition + 1) + " / " + photoUrls.size();
            textViewCounter.setText(counterText);
        });
    }

    private void downloadCurrentPhoto() {
        if (currentPosition < 0 || currentPosition >= photoUrls.size()) {
            return;
        }

        String photoUrl = photoUrls.get(currentPosition);
        String fileName = "photo_" + System.currentTimeMillis() + ".jpg";

        Context context = getContext();
        if (context == null) return;

        android.app.DownloadManager downloadManager = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            Toast.makeText(context, "Не удалось начать скачивание", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = Uri.parse(photoUrl);
            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(uri);
            request.setTitle("Скачивание фотографии");
            request.setDescription(fileName);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, fileName);
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setMimeType("image/jpeg");

            downloadManager.enqueue(request);
            Toast.makeText(context, "Скачивание начато", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Ошибка при скачивании", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCurrentPhoto() {
        if (currentPosition < 0 || currentPosition >= photoUrls.size()) {
            return;
        }

        String photoUrl = photoUrls.get(currentPosition);
        downloadAndSharePhoto(photoUrl);
    }

    private void downloadAndSharePhoto(String photoUrl) {
        Request request = new Request.Builder()
                .url(photoUrl)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        byte[] imageData = body.bytes();
                        shareImage(imageData);
                    }
                } else {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() ->
                                Toast.makeText(activity, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show());
                    }
                }
            }
        });
    }

    private void shareImage(byte[] imageData) {
        Activity activity = getActivity();
        if (activity == null) return;

        try {
            File cachePath = new File(activity.getCacheDir(), "images");
            if (!cachePath.exists()) {
                cachePath.mkdirs();
            }
            File file = new File(cachePath, "shared_photo_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream stream = new FileOutputStream(file);
            stream.write(imageData);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/jpeg");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Поделиться фотографией"));

        } catch (IOException e) {
            activity.runOnUiThread(() ->
                    Toast.makeText(activity, "Ошибка при обмене фото", Toast.LENGTH_SHORT).show());
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
    class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.PhotoViewHolder> {
        private List<String> photoUrls;
        private OnControlsToggleListener controlsToggleListener;

        public PhotoPagerAdapter(List<String> photoUrls, OnControlsToggleListener listener) {
            this.photoUrls = photoUrls;
            this.controlsToggleListener = listener;
        }

        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_photo_viewer, parent, false);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            String photoUrl = photoUrls.get(position);
            holder.bind(photoUrl);
        }

        @Override
        public int getItemCount() {
            return photoUrls.size();
        }

        class PhotoViewHolder extends RecyclerView.ViewHolder {
            private ImageView photoView;
            private GestureDetector gestureDetector;
            private float scaleFactor = 1.0f;
            private ScaleGestureDetector scaleGestureDetector;

            public PhotoViewHolder(@NonNull View itemView) {
                super(itemView);
                photoView = itemView.findViewById(R.id.photoView);
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
                        photoView.setScaleX(scaleFactor);
                        photoView.setScaleY(scaleFactor);
                        return true;
                    }
                });

                // Детектор масштабирования
                scaleGestureDetector = new ScaleGestureDetector(itemView.getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        scaleFactor *= detector.getScaleFactor();
                        scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f)); // Ограничиваем масштаб
                        photoView.setScaleX(scaleFactor);
                        photoView.setScaleY(scaleFactor);
                        return true;
                    }
                });

                // Обработка касаний для ImageView
                photoView.setOnTouchListener((v, event) -> {
                    gestureDetector.onTouchEvent(event);
                    scaleGestureDetector.onTouchEvent(event);
                    return true;
                });
            }

            public void bind(String photoUrl) {
                Glide.with(itemView.getContext())
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_photo_placeholder)
                        .error(R.drawable.ic_photo_error)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(photoView);

                // Сбрасываем масштаб при загрузке нового изображения
                photoView.setScaleX(1.0f);
                photoView.setScaleY(1.0f);
                scaleFactor = 1.0f;
            }
        }
    }
}