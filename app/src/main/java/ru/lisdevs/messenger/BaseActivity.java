package ru.lisdevs.messenger;


import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationMenuView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.auth.PinLoginActivity;
import ru.lisdevs.messenger.favorites.FavoritesFragment;
import ru.lisdevs.messenger.friends.FriendsFragment;
import ru.lisdevs.messenger.friends.FriendsSearchFragment;
import ru.lisdevs.messenger.messages.MessagesFragment;
import ru.lisdevs.messenger.music.MusicListFragment;
import ru.lisdevs.messenger.music.MusicTabsFragment;
import ru.lisdevs.messenger.music.MyMusicPlaylistsFragment;
import ru.lisdevs.messenger.official.audios.AudioListFragment;
import ru.lisdevs.messenger.player.MiniPlayer;
import ru.lisdevs.messenger.player.PlayerBottomSheetFragment;
import ru.lisdevs.messenger.playlists.VkPlaylistsFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.PinManager;
import ru.lisdevs.messenger.utils.TokenManager;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class BaseActivity extends AppCompatActivity
        implements MusicListFragment.OnMusicControlListener,
        PlayerBottomSheetFragment.PlayerInteractionListener {

    private MiniPlayer miniPlayer;
    private PlayerBottomSheetFragment playerBottomSheet;
    private MusicPlayerService musicService;
    private boolean isServiceBound = false;
    private BroadcastReceiver playerStateReceiver;
    private BottomNavigationView bottomNavigationView;
    private boolean isTestMode = false;
    private boolean isPinRequired = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Проверяем требование PIN-кода только если пользователь еще не аутентифицирован
        if (isPinRequired() && !PinManager.isPinAuthenticated(this)) {
            startPinActivity();
            return;
        }

        setContentView(R.layout.activity_nav);

        // Проверяем тестовый режим
        checkTestMode();

        initializeMiniPlayer();
        setupBottomNavigation();

        if (!isTestMode) {
            bindMusicService();
            setupPlayerStateReceiver();

            // Принудительное обновление через небольшой промежуток времени
            new Handler().postDelayed(() -> {
                Log.d("BaseActivity", "Forcing UI update after delay");
                updatePlayerUI();

                // Дополнительная проверка через сервис
                if (isServiceBound && musicService != null) {
                    String currentTrack = musicService.getCurrentTrackTitle();
                    if (currentTrack != null && !currentTrack.equals("Unknown Track")) {
                        LinearLayout miniPlayerContainer = findViewById(R.id.mini_player_container);
                        if (miniPlayerContainer != null) {
                            miniPlayerContainer.setVisibility(View.VISIBLE);
                            Log.d("BaseActivity", "Forced mini player show");
                        }
                    }
                }
            }, 1000);
        } else {
            showTestModeNotification();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Дополнительная проверка при возвращении в приложение
        if (isPinRequired() && !PinManager.isPinAuthenticated(this)) {
            startPinActivity();
            return;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // При сворачивании приложения сбрасываем аутентификацию для безопасности
        if (isPinRequired()) {
            PinManager.resetAuthentication(this);
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Когда пользователь сворачивает приложение, сбрасываем аутентификацию
        if (isPinRequired()) {
            PinManager.resetAuthentication(this);
        }
    }

    private void checkTestMode() {
        String token = TokenManager.getInstance(this).getToken();
        // Проверяем, является ли токен тестовым
        if (token == null || token.contains("test") || token.equals("demo") ||
                token.length() < 10 || token.equals("000000") || token.contains("demo_token")) {
            isTestMode = true;
            Log.d("BaseActivity", "Тестовый режим активирован");
        } else {
            isTestMode = false;
        }
    }

    private void showTestModeNotification() {
        Toast.makeText(this, "Демо-режим: используются тестовые данные", Toast.LENGTH_LONG).show();

        // Скрываем мини-плеер в тестовом режиме
        LinearLayout miniPlayerContainer = findViewById(R.id.mini_player_container);
        if (miniPlayerContainer != null) {
            miniPlayerContainer.setVisibility(View.GONE);
        }
    }

    private void initializeMiniPlayer() {
        LinearLayout miniPlayerContainer = findViewById(R.id.mini_player_container);

        if (miniPlayerContainer == null) {
            Log.e("BaseActivity", "mini_player_container not found in layout");
            return;
        }

        TextView trackTitle = findViewById(R.id.currentTrackText);
        TextView artistName = findViewById(R.id.currentTrackTitle);
        ImageView playPauseButton = findViewById(R.id.playPauseButton);
        ImageButton nextButton = findViewById(R.id.miniPlayerNext);
        ImageButton prevButton = findViewById(R.id.miniPlayerprevious);

        // Проверяем что все элементы найдены
        if (trackTitle == null || artistName == null || playPauseButton == null ||
                nextButton == null || prevButton == null) {
            Log.e("BaseActivity", "Some mini player views are missing");
            return;
        }

        miniPlayer = new MiniPlayer(this, miniPlayerContainer, artistName,
                trackTitle, playPauseButton, nextButton, prevButton);

        // Устанавливаем обработчики кликов
        miniPlayerContainer.setOnClickListener(v -> showFullPlayer());

        playPauseButton.setOnClickListener(v -> {
            if (isTestMode) return;
            onPlayPauseClicked();
        });

        nextButton.setOnClickListener(v -> {
            if (isTestMode) return;
            onNextClicked();
        });

        prevButton.setOnClickListener(v -> {
            if (isTestMode) return;
            onPreviousClicked();
        });

        // Изначально скрываем мини-плеер
        miniPlayerContainer.setVisibility(View.GONE);

        Log.d("BaseActivity", "Mini player initialized successfully");
    }

    private void setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.navigation);
        if (bottomNavigationView != null) {
            if (isTestMode) {
                hideAccountMenuItem();
            }

            bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
                switch (item.getItemId()) {
                    case R.id.item_friends:
                        changeFragment(new MessagesFragment());
                        return true;
                    case R.id.item_groups:
                        changeFragment(new FriendsSearchFragment());
                        return true;
                    case R.id.item_search:
                        changeFragment(new FriendsFragment());
                        return true;
                    case R.id.item_account:
                        // В тестовом режиме этот пункт должен быть скрыт
                        if (!isTestMode) {
                            changeFragment(new MusicTabsFragment());
                            return true;
                        }
                        return false;
                }
                return false;
            });

            if (getSupportFragmentManager().findFragmentById(R.id.container) == null) {
                changeFragment(new MessagesFragment());
            }

            // В тестовом режиме добавляем подсказку
            if (isTestMode) {
                addTestModeIndicator();
            }
        }
    }

    private void hideAccountMenuItem() {
        try {
            Menu menu = bottomNavigationView.getMenu();
            MenuItem accountItem = menu.findItem(R.id.item_account);
            if (accountItem != null) {
                accountItem.setVisible(false);
            }
        } catch (Exception e) {
            Log.e("BaseActivity", "Error hiding account menu item", e);
        }
    }

    private void addTestModeIndicator() {
        TextView testModeIndicator = findViewById(R.id.test_mode_indicator);
        if (testModeIndicator == null) {
            // Создаем индикатор если его нет в layout
            testModeIndicator = new TextView(this);
            testModeIndicator.setId(R.id.test_mode_indicator);
            testModeIndicator.setText("ДЕМО-РЕЖИМ");
            testModeIndicator.setTextColor(Color.RED);
            testModeIndicator.setTextSize(10);
            testModeIndicator.setBackgroundColor(Color.YELLOW);
            testModeIndicator.setPadding(8, 4, 8, 4);
            testModeIndicator.setGravity(Gravity.CENTER);

            // Добавляем в верхнюю часть экрана
            ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL
            );
            params.topMargin = 16;
            rootView.addView(testModeIndicator, params);
        } else {
            testModeIndicator.setVisibility(View.VISIBLE);
        }
    }

    private void bindMusicService() {
        // В тестовом режиме не связываемся с сервисом
        if (isTestMode) {
            Log.d("BaseActivity", "Test mode - skipping service binding");
            return;
        }

        try {
            Intent intent = new Intent(this, MusicPlayerService.class);
            boolean bound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            Log.d("BaseActivity", "Service binding attempted: " + bound);

            // Также запускаем сервис для гарантии
            startService(intent);
        } catch (Exception e) {
            Log.e("BaseActivity", "Error binding to music service", e);
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // В тестовом режиме игнорируем
            if (isTestMode) {
                return;
            }

            try {
                MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
                musicService = binder.getService();
                isServiceBound = true;

                Log.d("BaseActivity", "Service connected successfully");

                // Обновляем UI сразу после подключения
                runOnUiThread(() -> {
                    updatePlayerUI();
                    // Принудительно показываем мини-плеер если есть активный трек
                    if (musicService != null && musicService.getCurrentTrackTitle() != null &&
                            !musicService.getCurrentTrackTitle().equals("Unknown Track")) {
                        LinearLayout miniPlayerContainer = findViewById(R.id.mini_player_container);
                        if (miniPlayerContainer != null) {
                            miniPlayerContainer.setVisibility(View.VISIBLE);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e("BaseActivity", "Error in service connection", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            musicService = null;
            Log.d("BaseActivity", "Service disconnected");

            // Скрываем мини-плеер при отключении сервиса
            runOnUiThread(() -> {
                LinearLayout miniPlayerContainer = findViewById(R.id.mini_player_container);
                if (miniPlayerContainer != null) {
                    miniPlayerContainer.setVisibility(View.GONE);
                }
            });
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void setupPlayerStateReceiver() {
        // В тестовом режиме не настраиваем receiver
        if (isTestMode) {
            return;
        }

        playerStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("BaseActivity", "Player state changed broadcast received");

                String title = intent.getStringExtra("TITLE");
                String artist = intent.getStringExtra("ARTIST");
                boolean isPlaying = intent.getBooleanExtra("IS_PLAYING", false);

                Log.d("BaseActivity", "Received - Title: " + title + ", Artist: " + artist + ", Playing: " + isPlaying);

                // ОБНОВЛЯЕМ МИНИ-ПЛЕЕР НЕЗАВИСИМО ОТ СЕРВИСА
                updateMiniPlayerFromBroadcast(intent);

                if (playerBottomSheet != null && playerBottomSheet.isVisible()) {
                    playerBottomSheet.updatePlayerUI();
                }
            }
        };

        IntentFilter filter = new IntentFilter("PLAYER_STATE_CHANGED");
        // ДОБАВЬТЕ ЭТОТ ФИЛЬТР ДЛЯ НАДЕЖНОСТИ
        filter.addCategory(Intent.CATEGORY_DEFAULT);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(playerStateReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(playerStateReceiver, filter);
            }
            Log.d("BaseActivity", "Player state receiver registered successfully");
        } catch (Exception e) {
            Log.e("BaseActivity", "Error registering player state receiver", e);
        }
    }

    // НОВЫЙ МЕТОД ДЛЯ ОБНОВЛЕНИЯ МИНИ-ПЛЕЕРА ИЗ BROADCAST
    private void updateMiniPlayerFromBroadcast(Intent intent) {
        String title = intent.getStringExtra("TITLE");
        String artist = intent.getStringExtra("ARTIST");
        boolean isPlaying = intent.getBooleanExtra("IS_PLAYING", false);

        Log.d("BaseActivity", "Updating mini player from broadcast - " + title);

        TextView trackTitle = findViewById(R.id.currentTrackText);
        TextView artistName = findViewById(R.id.currentTrackTitle);
        ImageView playPauseButton = findViewById(R.id.playPauseButton);
        LinearLayout miniPlayerContainer = findViewById(R.id.mini_player_container);

        if (trackTitle == null || artistName == null || playPauseButton == null || miniPlayerContainer == null) {
            Log.e("BaseActivity", "Mini player views not found");
            return;
        }

        // ОБНОВЛЯЕМ UI НЕЗАВИСИМО ОТ СЕРВИСА
        runOnUiThread(() -> {
            trackTitle.setText(title != null ? title : "Unknown Track");
            artistName.setText(artist != null ? artist : "Unknown Artist");
            playPauseButton.setImageResource(isPlaying ? R.drawable.pause_black : R.drawable.play_black);

            // ПОКАЗЫВАЕМ МИНИ-ПЛЕЕР ЕСЛИ ЕСТЬ ВАЛИДНЫЙ ТРЕК
            if (title != null && !title.equals("Unknown Track") && !title.isEmpty()) {
                miniPlayerContainer.setVisibility(View.VISIBLE);
                Log.d("BaseActivity", "Mini player shown from broadcast");
            } else {
                miniPlayerContainer.setVisibility(View.GONE);
                Log.d("BaseActivity", "Mini player hidden - no valid track");
            }
        });
    }

    private void updatePlayerUI() {
        // В тестовом режиме не обновляем UI плеера
        if (isTestMode) {
            return;
        }

        if (isServiceBound && musicService != null) {
            TextView trackTitle = findViewById(R.id.currentTrackText);
            TextView artistName = findViewById(R.id.currentTrackTitle);
            ImageView playPauseButton = findViewById(R.id.playPauseButton);
            LinearLayout miniPlayerContainer = findViewById(R.id.mini_player_container);

            if (trackTitle == null || artistName == null || playPauseButton == null || miniPlayerContainer == null) {
                Log.e("BaseActivity", "Mini player views not found");
                return;
            }

            String currentTrack = musicService.getCurrentTrackTitle();
            String currentArtist = musicService.getCurrentArtist();
            boolean isPlaying = musicService.isPlaying();

            Log.d("BaseActivity", "Updating mini player from service - Track: " + currentTrack);

            trackTitle.setText(currentTrack);
            artistName.setText(currentArtist);
            playPauseButton.setImageResource(isPlaying ? R.drawable.pause_black : R.drawable.play_black);

            // ПОКАЗЫВАЕМ МИНИ-ПЛЕЕР ЕСЛИ ЕСТЬ ВАЛИДНЫЙ ТРЕК
            if (currentTrack != null && !currentTrack.equals("Unknown Track") && !currentTrack.isEmpty()) {
                miniPlayerContainer.setVisibility(View.VISIBLE);
                Log.d("BaseActivity", "Mini player shown from service");
            } else {
                miniPlayerContainer.setVisibility(View.GONE);
                Log.d("BaseActivity", "Mini player hidden - no valid track");
            }
        } else {
            Log.d("BaseActivity", "Service not bound or null");
            // Скрываем мини-плеер если сервис не доступен
            LinearLayout miniPlayerContainer = findViewById(R.id.mini_player_container);
            if (miniPlayerContainer != null) {
                miniPlayerContainer.setVisibility(View.GONE);
            }
        }
    }

    private void showFullPlayer() {
        // В тестовом режиме показываем сообщение
        if (isTestMode) {
            Toast.makeText(this, "Демо-режим: музыкальный плеер недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        if (musicService == null || musicService.getCurrentTrackTitle() == null ||
                musicService.getCurrentTrackTitle().equals("Unknown Track")) {
            return;
        }

        if (playerBottomSheet == null) {
            playerBottomSheet = PlayerBottomSheetFragment.newInstance();
        }
        playerBottomSheet.show(getSupportFragmentManager(), "player_bottom_sheet");
    }

    @Override
    public void onPlayAudio(String url, String title) {
        // В тестовом режиме показываем сообщение
        if (isTestMode) {
            Toast.makeText(this, "Демо-режим: воспроизведение музыки недоступно", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isServiceBound && musicService != null) {
            Intent intent = new Intent(this, MusicPlayerService.class);
            intent.setAction(MusicPlayerService.ACTION_PLAY);
            intent.putExtra("URL", url);
            intent.putExtra("TITLE", title);
            intent.putExtra("ARTIST", (String) null);
            intent.putExtra("COVER_URL", (String) null);
            startService(intent);

            updatePlayerUI();
            showFullPlayer();
        }
    }

    @Override
    public void onTogglePlayPause() {
        if (isTestMode) return;

        if (isServiceBound && musicService != null) {
            musicService.togglePlayPause();
        }
    }

    @Override
    public void onNext() {
        if (isTestMode) {
            Toast.makeText(this, "Демо-режим: функции плеера недоступны", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isServiceBound && musicService != null) {
            if (musicService.getPlaylist() == null || musicService.getPlaylist().isEmpty()) {
                showToast("Нет треков для воспроизведения");
                return;
            }

            musicService.playNext();

            new Handler().postDelayed(() -> {
                updatePlayerUI();
                if (playerBottomSheet != null && playerBottomSheet.isVisible()) {
                    playerBottomSheet.updatePlayerUI();
                }
            }, 300);
        }
    }

    @Override
    public void onPrevious() {
        if (isTestMode) {
            Toast.makeText(this, "Демо-режим: функции плеера недоступны", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isServiceBound && musicService != null) {
            if (musicService.getPlaylist() == null || musicService.getPlaylist().isEmpty()) {
                showToast("Нет треков для воспроизведения");
                return;
            }

            musicService.playPrevious();

            new Handler().postDelayed(() -> {
                updatePlayerUI();
                if (playerBottomSheet != null && playerBottomSheet.isVisible()) {
                    playerBottomSheet.updatePlayerUI();
                }
            }, 300);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNextClicked() {
        if (isTestMode) return;

        Intent intent = new Intent(this, MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_NEXT);
        startService(intent);
    }

    @Override
    public void onPreviousClicked() {
        if (isTestMode) return;

        Intent intent = new Intent(this, MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_PREVIOUS);
        startService(intent);
    }

    @Override
    public void onPlayPauseClicked() {
        if (isTestMode) return;

        Intent intent = new Intent(this, MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_TOGGLE);
        startService(intent);
    }

    @Override
    public void onSeekTo(int position) {
        if (isTestMode) return;

        Intent intent = new Intent(this, MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_SEEK);
        intent.putExtra("POSITION", position);
        startService(intent);
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        if (isTestMode) return;
        // Реализация при необходимости
    }

    @Override
    public void onShuffleModeChanged(boolean shuffleMode) {
        if (isTestMode) return;
        // Реализация при необходимости
    }

    @Override
    public void onPlayerClosed() {
        // Обработка закрытия плеера
    }

    private void changeFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    public void onPlaylists(View v) {
        // В тестовом режиме показываем сообщение
        if (isTestMode) {
            Toast.makeText(this, "Демо-режим: плейлисты недоступны", Toast.LENGTH_SHORT).show();
            return;
        }

        VkPlaylistsFragment nextFrag = new VkPlaylistsFragment();

        BaseActivity.this.getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, nextFrag, "findThisFragment")
                .addToBackStack(null)
                .commit();
    }

    private boolean isPinRequired() {
        // Проверяем, включен ли PIN-код и пользователь авторизован
        boolean isAuthorized = TokenManager.getInstance(this).getToken() != null;
        boolean pinEnabled = PinManager.isPinEnabled(this);

        return isAuthorized && pinEnabled;
    }

    private void startPinActivity() {
        Intent intent = new Intent(this, PinLoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // В тестовом режиме не выполняем очистку сервиса
        if (!isTestMode) {
            if (isServiceBound) {
                unbindService(serviceConnection);
                isServiceBound = false;
            }
            if (playerStateReceiver != null) {
                unregisterReceiver(playerStateReceiver);
            }
        }

        if (miniPlayer != null) {
            miniPlayer.release();
        }
    }

    // Метод для получения состояния тестового режима
    public boolean isTestMode() {
        return isTestMode;
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        // Проверяем, есть ли фрагменты в back stack
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            // Если есть фрагменты в back stack - возвращаемся назад по навигации
            fragmentManager.popBackStack();
        } else {
            // Если нет фрагментов в back stack - сворачиваем приложение
            moveTaskToBack(true);
        }
    }
}