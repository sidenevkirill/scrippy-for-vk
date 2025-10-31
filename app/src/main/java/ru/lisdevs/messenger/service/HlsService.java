package ru.lisdevs.messenger.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.util.List;
import java.util.Random;

import ru.lisdevs.messenger.BaseActivity;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.official.audios.AudioInputFragment;

public class HlsService extends Service implements
        Player.Listener,
        AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "HlsService";

    // Действия сервиса
    public static final String ACTION_PLAY = "ACTION_PLAY";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_RESUME = "ACTION_RESUME";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_PREVIOUS = "ACTION_PREVIOUS";
    public static final String ACTION_SEEK_TO = "ACTION_SEEK_TO";
    public static final String ACTION_CHANGE_REPEAT = "ACTION_CHANGE_REPEAT";
    public static final String ACTION_CHANGE_SHUFFLE = "ACTION_CHANGE_SHUFFLE";

    // Состояния плеера
    public static final int STATE_IDLE = 0;
    public static final int STATE_BUFFERING = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_ENDED = 3;
    public static final int STATE_ERROR = 4;

    // Режимы повтора
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;

    // ExoPlayer компоненты
    private SimpleExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private MediaSourceFactory mediaSourceFactory;

    // Текущие данные
    private AudioInputFragment.Audio currentAudio;
    private List<AudioInputFragment.Audio> playlist;
    private int currentPlaylistIndex = 0;

    // Состояние
    private int playerState = STATE_IDLE;
    private int repeatMode = REPEAT_OFF;
    private boolean shuffleMode = false;
    private boolean isPrepared = false;

    // Аудио фокус
    private AudioManager audioManager;
    private boolean audioFocusGranted = false;

    // Уведомления
    private NotificationManager notificationManager;
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "hls_service_channel";

    // Broadcast для обновления UI
    private final BroadcastReceiver playerUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePlayerState();
        }
    };

    // Binder для связи с активностью
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public HlsService getService() {
            return HlsService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "HlsService создан");

        initializePlayer();
        initializeAudioManager();
        createNotificationChannel();
        registerBroadcastReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + (intent != null ? intent.getAction() : "null"));

        if (intent != null) {
            handleIntent(intent);
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void initializePlayer() {
        // Инициализация TrackSelector
        trackSelector = new DefaultTrackSelector(getApplicationContext());
        trackSelector.setParameters(
                trackSelector.buildUponParameters()
                        .setMaxVideoSizeSd()
                        .setPreferredAudioLanguage("ru")
        );

        // Инициализация MediaSourceFactory
        mediaSourceFactory = new DefaultMediaSourceFactory(getApplicationContext())
                .setLiveTargetOffsetMs(5000)
                .setLiveMinOffsetMs(2000)
                .setLiveMaxOffsetMs(10000);

        // Создание ExoPlayer
        player = new SimpleExoPlayer.Builder(getApplicationContext())
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();

        player.addListener(this);
        player.setHandleAudioBecomingNoisy(true);
        player.setWakeMode(C.WAKE_MODE_NETWORK);
    }

    private void initializeAudioManager() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Обработка действия: " + action);

        if (action == null) return;

        switch (action) {
            case ACTION_PLAY:
                AudioInputFragment.Audio audio = intent.getParcelableExtra("AUDIO");
                if (audio != null) {
                    playAudio(audio);
                }
                break;

            case ACTION_PAUSE:
                pause();
                break;

            case ACTION_RESUME:
                resume();
                break;

            case ACTION_STOP:
                stop();
                break;

            case ACTION_NEXT:
                playNext();
                break;

            case ACTION_PREVIOUS:
                playPrevious();
                break;

            case ACTION_SEEK_TO:
                int position = intent.getIntExtra("POSITION", 0);
                seekTo(position);
                break;

            case ACTION_CHANGE_REPEAT:
                int newRepeatMode = intent.getIntExtra("REPEAT_MODE", REPEAT_OFF);
                setRepeatMode(newRepeatMode);
                break;

            case ACTION_CHANGE_SHUFFLE:
                boolean shuffle = intent.getBooleanExtra("SHUFFLE_MODE", false);
                setShuffleMode(shuffle);
                break;
        }
    }

    public void playAudio(AudioInputFragment.Audio audio) {
        Log.d(TAG, "Воспроизведение аудио: " + audio.getTitle());

        if (!requestAudioFocus()) {
            Log.e(TAG, "Не удалось получить аудио фокус");
            return;
        }

        currentAudio = audio;
        playerState = STATE_BUFFERING;

        try {
            // Создание MediaSource для HLS или обычного аудио
            MediaSource mediaSource = createMediaSource(audio.getUrl());
            player.setMediaSource(mediaSource);
            player.prepare();
            player.setPlayWhenReady(true);

            isPrepared = true;
            updateNotification();
            updatePlayerState();

        } catch (Exception e) {
            Log.e(TAG, "Ошибка воспроизведения: " + e.getMessage());
            playerState = STATE_ERROR;
            sendErrorBroadcast("Ошибка воспроизведения: " + e.getMessage());
        }
    }

    private MediaSource createMediaSource(String url) {
        Uri uri = Uri.parse(url);
        String userAgent = "HlsService/1.0";

        // Определяем тип контента по расширению или содержимому URL
        if (url.toLowerCase().contains(".m3u8")) {
            // HLS поток
            HlsMediaSource.Factory factory = new HlsMediaSource.Factory(
                    new DefaultHttpDataSource.Factory()
                            .setUserAgent(userAgent)
                            .setConnectTimeoutMs(10000)
                            .setReadTimeoutMs(30000)
            );

            return factory.createMediaSource(MediaItem.fromUri(uri));
        } else {
            // Прогрессивное аудио (MP3, AAC, etc.)
            return new ProgressiveMediaSource.Factory(
                    new DefaultHttpDataSource.Factory()
                            .setUserAgent(userAgent)
            ).createMediaSource(MediaItem.fromUri(uri));
        }
    }

    public void pause() {
        if (player != null && isPrepared) {
            player.setPlayWhenReady(false);
            playerState = STATE_READY;
            updateNotification();
            updatePlayerState();
        }
    }

    public void resume() {
        if (player != null && isPrepared) {
            if (requestAudioFocus()) {
                player.setPlayWhenReady(true);
                playerState = STATE_BUFFERING;
                updateNotification();
                updatePlayerState();
            }
        }
    }

    public void stop() {
        if (player != null) {
            player.stop();
            playerState = STATE_IDLE;
            releaseAudioFocus();
            stopForeground(true);
            stopSelf();
        }
    }

    public void playNext() {
        if (playlist != null && !playlist.isEmpty()) {
            if (shuffleMode) {
                currentPlaylistIndex = new Random().nextInt(playlist.size());
            } else {
                currentPlaylistIndex = (currentPlaylistIndex + 1) % playlist.size();
            }
            playAudio(playlist.get(currentPlaylistIndex));
        }
    }

    public void playPrevious() {
        if (playlist != null && !playlist.isEmpty()) {
            if (shuffleMode) {
                currentPlaylistIndex = new Random().nextInt(playlist.size());
            } else {
                currentPlaylistIndex = (currentPlaylistIndex - 1 + playlist.size()) % playlist.size();
            }
            playAudio(playlist.get(currentPlaylistIndex));
        }
    }

    public void seekTo(int position) {
        if (player != null && isPrepared) {
            player.seekTo(position);
        }
    }

    private boolean requestAudioFocus() {
        int result = audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
        );

        audioFocusGranted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        return audioFocusGranted;
    }

    private void releaseAudioFocus() {
        audioManager.abandonAudioFocus(this);
        audioFocusGranted = false;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // Возвращение фокуса - продолжаем воспроизведение
                if (player != null && !player.isPlaying()) {
                    player.setPlayWhenReady(true);
                }
                player.setVolume(1.0f);
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                // Потеря фокуса на длительное время - останавливаем
                pause();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Временная потеря фокуса - пауза
                if (player != null && player.isPlaying()) {
                    player.setPlayWhenReady(false);
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Временная потеря - уменьшаем громкость
                if (player != null) {
                    player.setVolume(0.2f);
                }
                break;
        }
    }

    // Реализация Player.Listener
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        switch (playbackState) {
            case Player.STATE_IDLE:
                playerState = STATE_IDLE;
                break;
            case Player.STATE_BUFFERING:
                playerState = STATE_BUFFERING;
                break;
            case Player.STATE_READY:
                playerState = STATE_READY;
                break;
            case Player.STATE_ENDED:
                playerState = STATE_ENDED;
                handlePlaybackEnded();
                break;
        }
        updatePlayerState();
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        Log.e(TAG, "Ошибка плеера: " + error.getMessage());
        playerState = STATE_ERROR;
        sendErrorBroadcast("Ошибка воспроизведения: " + error.getMessage());
        updatePlayerState();
    }

    private void handlePlaybackEnded() {
        switch (repeatMode) {
            case REPEAT_ONE:
                player.seekTo(0);
                player.setPlayWhenReady(true);
                break;
            case REPEAT_ALL:
                playNext();
                break;
            case REPEAT_OFF:
            default:
                // Останавливаем или переходим к следующему
                if (playlist != null && playlist.size() > 1) {
                    playNext();
                } else {
                    pause();
                }
                break;
        }
    }

    // Методы для получения состояния
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public int getCurrentPosition() {
        return player != null ? (int) player.getCurrentPosition() : 0;
    }

    public int getDuration() {
        return player != null ? (int) player.getDuration() : 0;
    }

    public String getCurrentTrackTitle() {
        return currentAudio != null ? currentAudio.getTitle() : "Неизвестный трек";
    }

    public String getCurrentArtist() {
        return currentAudio != null ? currentAudio.getArtist() : "Неизвестный исполнитель";
    }

    public String getCoverUrl() {
        return currentAudio != null ? currentAudio.getCoverUrl() : null;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(int repeatMode) {
        this.repeatMode = repeatMode;
        if (player != null) {
            switch (repeatMode) {
                case REPEAT_ONE:
                    player.setRepeatMode(Player.REPEAT_MODE_ONE);
                    break;
                case REPEAT_ALL:
                    player.setRepeatMode(Player.REPEAT_MODE_ALL);
                    break;
                case REPEAT_OFF:
                default:
                    player.setRepeatMode(Player.REPEAT_MODE_OFF);
                    break;
            }
        }
        updatePlayerState();
    }

    public boolean getShuffleMode() {
        return shuffleMode;
    }

    public void setShuffleMode(boolean shuffleMode) {
        this.shuffleMode = shuffleMode;
        if (player != null) {
            player.setShuffleModeEnabled(shuffleMode);
        }
        updatePlayerState();
    }

    public AudioInputFragment.Audio getCurrentAudio() {
        return currentAudio;
    }

    public void setPlaylist(List<AudioInputFragment.Audio> playlist) {
        this.playlist = playlist;
    }

    public List<AudioInputFragment.Audio> getPlaylist() {
        return playlist;
    }

    // Уведомления
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "HLS Audio Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Воспроизведение аудио потоков");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void updateNotification() {
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, BaseActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        // Создаем действия для уведомления
        NotificationCompat.Action playAction = new NotificationCompat.Action(
                R.drawable.play,
                "Play",
                getPendingIntent(ACTION_RESUME)
        );

        NotificationCompat.Action pauseAction = new NotificationCompat.Action(
                R.drawable.pause,
                "Pause",
                getPendingIntent(ACTION_PAUSE)
        );

        NotificationCompat.Action stopAction = new NotificationCompat.Action(
                R.drawable.stop,
                "Stop",
                getPendingIntent(ACTION_STOP)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getCurrentTrackTitle())
                .setContentText(getCurrentArtist())
                .setSmallIcon(R.drawable.music_note_24dp)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true);

        if (isPlaying()) {
            builder.addAction(pauseAction);
        } else {
            builder.addAction(playAction);
        }
        builder.addAction(stopAction);

        return builder.build();
    }

    private PendingIntent getPendingIntent(String action) {
        Intent intent = new Intent(this, HlsService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    // Broadcast для обновления UI
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("UPDATE_PLAYER_UI");
        registerReceiver(playerUpdateReceiver, filter);
    }

    private void updatePlayerState() {
        Intent intent = new Intent("PLAYER_STATE_CHANGED");
        intent.putExtra("STATE", playerState);
        intent.putExtra("IS_PLAYING", isPlaying());
        intent.putExtra("CURRENT_POSITION", getCurrentPosition());
        intent.putExtra("DURATION", getDuration());
        intent.putExtra("REPEAT_MODE", repeatMode);
        intent.putExtra("SHUFFLE_MODE", shuffleMode);

        if (currentAudio != null) {
            intent.putExtra("CURRENT_AUDIO", currentAudio);
        }

        sendBroadcast(intent);
    }

    private void sendErrorBroadcast(String errorMessage) {
        Intent intent = new Intent("PLAYER_ERROR");
        intent.putExtra("ERROR_MESSAGE", errorMessage);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "HlsService уничтожается");

        if (player != null) {
            player.release();
            player = null;
        }

        releaseAudioFocus();
        unregisterReceiver(playerUpdateReceiver);

        super.onDestroy();
    }

    // Статические методы для удобного управления сервисом
    public static void playAudio(Context context, AudioInputFragment.Audio audio) {
        Intent intent = new Intent(context, HlsService.class);
        intent.setAction(ACTION_PLAY);
        intent.putExtra("AUDIO", audio);
        context.startService(intent);
    }

    public static void pause(Context context) {
        Intent intent = new Intent(context, HlsService.class);
        intent.setAction(ACTION_PAUSE);
        context.startService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, HlsService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }
}