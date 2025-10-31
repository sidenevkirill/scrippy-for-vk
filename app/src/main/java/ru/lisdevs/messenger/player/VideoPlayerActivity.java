package ru.lisdevs.messenger.player;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException;
import com.google.android.exoplayer2.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.video.VideoItem;
import ru.lisdevs.messenger.utils.TokenManager;

public class VideoPlayerActivity extends AppCompatActivity {
    private static final String TAG = "VideoPlayerActivity";
    private static final String EXTRA_VIDEO = "video";
    private static final String EXTRA_VIDEOS = "videos";

    private ExoPlayer player;
    private PlayerView playerView;
    private WebView webView;
    private FrameLayout playerContainer;
    private VideoItem currentVideo;
    private List<VideoItem> videos;

    private ProgressBar loadingProgressBar;
    private TextView errorTextView;
    private TextView retryButton;
    private TextView titleView, descriptionView, viewsView, durationView, dateView;
    private TextView useWebViewButton;

    private boolean useWebViewFallback = false;

    public static void start(Context context, VideoItem video, List<VideoItem> videos) {
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.putExtra(EXTRA_VIDEO, video);
        intent.putParcelableArrayListExtra(EXTRA_VIDEOS, new ArrayList<>(videos));
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        currentVideo = getIntent().getParcelableExtra(EXTRA_VIDEO);
        videos = getIntent().getParcelableArrayListExtra(EXTRA_VIDEOS);

        initViews();
        setupVideoInfo();

        // Пробуем загрузить через ExoPlayer сначала
        loadVideoUrl();
    }

    private void initViews() {
        playerContainer = findViewById(R.id.playerContainer);
        playerView = findViewById(R.id.playerView);
        webView = findViewById(R.id.webView);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        errorTextView = findViewById(R.id.errorTextView);
        retryButton = findViewById(R.id.retryButton);
        useWebViewButton = findViewById(R.id.useWebViewButton);

        titleView = findViewById(R.id.titleTextView);
        descriptionView = findViewById(R.id.descriptionTextView);
        viewsView = findViewById(R.id.viewsTextView);
        durationView = findViewById(R.id.durationTextView);
        dateView = findViewById(R.id.dateTextView);

        // Настройка WebView
        setupWebView();

        // Скрываем WebView по умолчанию
        webView.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);

        retryButton.setOnClickListener(v -> retryPlayback());
        useWebViewButton.setOnClickListener(v -> switchToWebView());
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    loadingProgressBar.setVisibility(View.VISIBLE);
                } else {
                    loadingProgressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                showError("WebView error: " + description);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                hideError();
            }
        });
    }

    private void setupVideoInfo() {
        if (currentVideo != null) {
            titleView.setText(currentVideo.title);
            descriptionView.setText(currentVideo.description);

            if (currentVideo.views > 0) {
                viewsView.setText(formatViews(currentVideo.views));
                viewsView.setVisibility(View.VISIBLE);
            } else {
                viewsView.setVisibility(View.GONE);
            }

            if (currentVideo.duration > 0) {
                durationView.setText(formatDuration(currentVideo.duration));
                durationView.setVisibility(View.VISIBLE);
            } else {
                durationView.setVisibility(View.GONE);
            }

            if (currentVideo.date > 0) {
                dateView.setText(formatDate(currentVideo.date));
                dateView.setVisibility(View.VISIBLE);
            } else {
                dateView.setVisibility(View.GONE);
            }
        }
    }

    private void loadVideoUrl() {
        if (currentVideo == null) {
            showError("Video data is null");
            return;
        }

        showLoading(true);
        useWebViewFallback = false;

        // Сначала пробуем получить прямую ссылку через VK API
        fetchDirectVideoUrl();
    }

    private void fetchDirectVideoUrl() {
        String token = TokenManager.getInstance(this).getToken();
        if (token == null) {
            Log.w(TAG, "No token available, trying direct playback");
            tryDirectPlayback();
            return;
        }

        String url = "https://api.vk.com/method/video.get" +
                "?access_token=" + token +
                "&videos=" + currentVideo.ownerId + "_" + currentVideo.videoId +
                "&extended=1" +
                "&v=5.131";

        Log.d(TAG, "Fetching direct URL from VK API");

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", "VKAndroidApp/5.52-4543")
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> {
                            Log.e(TAG, "Network error fetching video URL", e);
                            tryDirectPlayback();
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            String responseBody = response.body().string();
                            Log.d(TAG, "API Response received");
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("response")) {
                                JSONArray items = json.getJSONObject("response").getJSONArray("items");
                                if (items.length() > 0) {
                                    JSONObject videoJson = items.getJSONObject(0);
                                    String directUrl = extractDirectVideoUrl(videoJson);

                                    runOnUiThread(() -> {
                                        if (directUrl != null && isPlayableUrl(directUrl)) {
                                            Log.d(TAG, "Using extracted URL: " + directUrl);
                                            initExoPlayer(directUrl);
                                        } else {
                                            Log.w(TAG, "No playable URL found, trying WebView");
                                            tryWebViewPlayback();
                                        }
                                    });
                                    return;
                                }
                            }

                            runOnUiThread(() -> {
                                Log.w(TAG, "No video data in API response");
                                tryDirectPlayback();
                            });

                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                Log.e(TAG, "Error parsing API response", e);
                                tryDirectPlayback();
                            });
                        }
                    }
                });
    }

    private String extractDirectVideoUrl(JSONObject videoJson) throws JSONException {
        // Пробуем разные источники в порядке приоритета
        String[] urlSources = {
                "mp4_1080", "mp4_720", "mp4_480", "mp4_360", "mp4_240",
                "external", "player", "hls"
        };

        for (String source : urlSources) {
            if (videoJson.has(source)) {
                String url = videoJson.optString(source);
                if (!TextUtils.isEmpty(url)) {
                    Log.d(TAG, "Found URL from source: " + source);
                    return url;
                }
            }
        }

        // Пробуем files object
        if (videoJson.has("files")) {
            JSONObject files = videoJson.getJSONObject("files");
            String[] qualities = {"mp4_1080", "mp4_720", "mp4_480", "mp4_360", "mp4_240"};
            for (String quality : qualities) {
                if (files.has(quality)) {
                    String url = files.optString(quality);
                    if (!TextUtils.isEmpty(url)) {
                        Log.d(TAG, "Found URL from files: " + quality);
                        return url;
                    }
                }
            }
        }

        return null;
    }

    private boolean isPlayableUrl(String url) {
        return url != null && (
                url.contains(".mp4") ||
                        url.contains(".m3u8") ||
                        url.contains("googlevideo") ||
                        url.contains("youtube")
        );
    }

    private void tryDirectPlayback() {
        if (currentVideo.videoUrl != null && isPlayableUrl(currentVideo.videoUrl)) {
            Log.d(TAG, "Trying direct playback with original URL");
            initExoPlayer(currentVideo.videoUrl);
        } else {
            Log.w(TAG, "Original URL not playable, switching to WebView");
            tryWebViewPlayback();
        }
    }

    private void initExoPlayer(String videoUrl) {
        runOnUiThread(() -> {
            try {
                if (player != null) {
                    player.release();
                }

                player = new ExoPlayer.Builder(this).build();
                playerView.setPlayer(player);
                playerView.setKeepScreenOn(true);

                Log.d(TAG, "Initializing ExoPlayer with URL: " + videoUrl);

                MediaItem mediaItem = MediaItem.fromUri(videoUrl);
                player.setMediaItem(mediaItem);
                player.prepare();
                player.play();

                player.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        Log.d(TAG, "ExoPlayer state: " + playbackState);
                        switch (playbackState) {
                            case Player.STATE_BUFFERING:
                                showLoading(true);
                                break;
                            case Player.STATE_READY:
                                showLoading(false);
                                hideError();
                                Log.d(TAG, "ExoPlayer ready");
                                break;
                            case Player.STATE_ENDED:
                                showLoading(false);
                                break;
                            case Player.STATE_IDLE:
                                showLoading(false);
                                break;
                        }
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        Log.e(TAG, "ExoPlayer error: " + error.getMessage(), error);

                        if (error.getCause() instanceof UnrecognizedInputFormatException) {
                            runOnUiThread(() -> {
                                showError("Video format not supported by ExoPlayer. Switching to WebView...");
                                useWebViewButton.setVisibility(View.VISIBLE);
                            });
                        } else {
                            runOnUiThread(() -> {
                                showError("Playback error: " + error.getMessage());
                                retryButton.setVisibility(View.VISIBLE);
                            });
                        }
                        showLoading(false);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "ExoPlayer initialization failed", e);
                runOnUiThread(() -> {
                    showError("Player initialization failed: " + e.getMessage());
                    useWebViewButton.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void tryWebViewPlayback() {
        runOnUiThread(() -> {
            Log.d(TAG, "Switching to WebView playback");
            useWebViewFallback = true;

            // Скрываем ExoPlayer, показываем WebView
            playerView.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);

            if (player != null) {
                player.release();
                player = null;
            }

            // Загружаем видео через WebView
            loadVideoInWebView();
        });
    }

    private void loadVideoInWebView() {
        if (currentVideo == null) {
            showError("No video data");
            return;
        }

        showLoading(true);

        // Создаем HTML страницу с видео плеером
        String htmlContent = createVideoHtml();
        webView.loadDataWithBaseURL(
                "https://vk.com",
                htmlContent,
                "text/html",
                "UTF-8",
                null
        );

        // Альтернативно: загружаем напрямую VK страницу
        // String vkUrl = "https://vk.com/video" + currentVideo.ownerId + "_" + currentVideo.videoId;
        // webView.loadUrl(vkUrl);
    }

    private String createVideoHtml() {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<style>" +
                "body { margin: 0; padding: 0; background: black; display: flex; justify-content: center; align-items: center; height: 100vh; }" +
                "video { width: 100%; height: auto; max-height: 100vh; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<video controls autoplay>" +
                "<source src=\"" + (currentVideo.videoUrl != null ? currentVideo.videoUrl : "") + "\" type=\"video/mp4\">" +
                "Your browser does not support the video tag." +
                "</video>" +
                "<script>" +
                "var video = document.querySelector('video');" +
                "video.addEventListener('error', function(e) {" +
                "   console.log('Video error:', e);" +
                "});" +
                "video.addEventListener('canplay', function() {" +
                "   console.log('Video can play');" +
                "});" +
                "</script>" +
                "</body>" +
                "</html>";
    }

    private void switchToWebView() {
        useWebViewButton.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        tryWebViewPlayback();
    }

    private void retryPlayback() {
        hideError();
        if (useWebViewFallback) {
            loadVideoInWebView();
        } else {
            loadVideoUrl();
        }
    }

    private String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%d:%02d", minutes, secs);
        }
    }

    private String formatViews(int views) {
        if (views >= 1000000) {
            return String.format("%.1fM views", views / 1000000.0);
        } else if (views >= 1000) {
            return String.format("%.1fK views", views / 1000.0);
        } else {
            return views + " views";
        }
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp * 1000));
    }

    private void showLoading(boolean show) {
        loadingProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        errorTextView.setText(message);
        errorTextView.setVisibility(View.VISIBLE);
        loadingProgressBar.setVisibility(View.GONE);
    }

    private void hideError() {
        errorTextView.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        useWebViewButton.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && player.isPlaying()) {
            player.pause();
        }
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        if (webView != null) {
            webView.destroy();
        }
    }
}