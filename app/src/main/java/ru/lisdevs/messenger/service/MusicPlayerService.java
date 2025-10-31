package ru.lisdevs.messenger.service;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import android.util.Log;
import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;

import ru.lisdevs.messenger.BaseActivity;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.official.audios.Audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MusicPlayerService extends Service implements Player.Listener {

    public static final String ACTION_PLAY = "PLAY";
    public static final String ACTION_PLAY_LOCAL = "PLAY_LOCAL";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_RESUME = "RESUME";
    public static final String ACTION_STOP = "STOP";
    public static final String ACTION_TOGGLE = "TOGGLE";
    public static final String ACTION_SEEK = "SEEK";
    public static final String ACTION_NEXT = "NEXT";
    public static final String ACTION_PREVIOUS = "PREVIOUS";
    public static final String ACTION_CHANGE_REPEAT_MODE = "CHANGE_REPEAT_MODE";
    public static final String ACTION_CHANGE_SHUFFLE_MODE = "CHANGE_SHUFFLE_MODE";

    // Константы для режима повтора
    public static final int REPEAT_MODE_OFF = 0;
    public static final int REPEAT_MODE_ONE = 1;
    public static final int REPEAT_MODE_ALL = 2;

    private ExoPlayer exoPlayer;
    private final IBinder binder = new LocalBinder();
    private NotificationManager notificationManager;
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "music_channel";

    private String currentTrackTitle = "Unknown Track";
    private String currentArtist = "Unknown Artist";
    private String currentFilePath;
    private String coverUrl = "";
    private boolean isLocalFile = false;

    private List<Audio> playlist = new ArrayList<>();
    private List<Audio> originalPlaylist = new ArrayList<>();
    private int currentPlaylistPosition = 0;

    private int repeatMode = REPEAT_MODE_OFF;
    private boolean shuffleMode = false;
    private Random random = new Random();

    private MediaSessionCompat mediaSession;

    public class LocalBinder extends Binder {
        public MusicPlayerService getService() {
            return MusicPlayerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializePlayer();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();

        // Инициализация медиа-сессии
        mediaSession = new MediaSessionCompat(this, "MusicPlayerService");
        mediaSession.setActive(true);
    }

    private void initializePlayer() {
        // Настройка аудио атрибутов
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        // Создаем TrackSelector
        TrackSelector trackSelector = new DefaultTrackSelector(requireContext());

        exoPlayer = new ExoPlayer.Builder(requireContext())
                .setTrackSelector(trackSelector)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        // Добавляем слушатель событий плеера
        exoPlayer.addListener(this);

        // Устанавливаем начальный режим повтора
        updateExoPlayerRepeatMode();
    }

    private Context requireContext() {
        return getApplicationContext();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleAction(intent);
        }
        return START_STICKY;
    }

    private void handleAction(Intent intent) {
        switch (intent.getAction()) {
            case ACTION_PLAY_LOCAL:
                handlePlayLocal(intent);
                break;
            case ACTION_PLAY:
                handlePlay(intent);
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
            case ACTION_TOGGLE:
                togglePlayPause();
                break;
            case ACTION_SEEK:
                seekTo(intent.getIntExtra("POSITION", 0));
                break;
            case ACTION_NEXT:
                playNext();
                break;
            case ACTION_PREVIOUS:
                playPrevious();
                break;
            case ACTION_CHANGE_REPEAT_MODE:
                changeRepeatMode();
                break;
            case ACTION_CHANGE_SHUFFLE_MODE:
                setShuffleMode(!shuffleMode);
                break;
        }
    }

    private void changeRepeatMode() {
        repeatMode = (repeatMode + 1) % 3;
        updateExoPlayerRepeatMode();
        updateNotification();
        broadcastPlayerState();
    }

    private void updateExoPlayerRepeatMode() {
        switch (repeatMode) {
            case REPEAT_MODE_ONE:
                exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
                break;
            case REPEAT_MODE_ALL:
                exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
                break;
            case REPEAT_MODE_OFF:
            default:
                exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
                break;
        }
    }

    private void handlePlayLocal(Intent intent) {
        currentFilePath = intent.getStringExtra("FILE_PATH");
        currentTrackTitle = intent.getStringExtra("TITLE");
        currentArtist = intent.getStringExtra("ARTIST");
        isLocalFile = true;
        playAudio(currentFilePath);
    }

    private void handlePlay(Intent intent) {
        Audio audio = intent.getParcelableExtra("AUDIO");
        if (audio != null) {
            currentTrackTitle = audio.getTitle();
            currentArtist = audio.getArtist();
            currentFilePath = audio.getUrl();
            isLocalFile = false;
        } else {
            currentFilePath = intent.getStringExtra("URL");
            currentTrackTitle = intent.getStringExtra("TITLE");
            currentArtist = intent.getStringExtra("ARTIST");
            coverUrl = intent.getStringExtra("COVER_URL");
            isLocalFile = false;
        }

        if (intent.hasExtra("PLAYLIST") && intent.hasExtra("POSITION")) {
            ArrayList<Audio> playlist = intent.getParcelableArrayListExtra("PLAYLIST");
            int position = intent.getIntExtra("POSITION", 0);
            setPlaylist(playlist, position);
        }

        playAudio(currentFilePath);
    }

    public void setPlaylist(List<Audio> playlist, int position) {
        this.originalPlaylist = new ArrayList<>(playlist);
        this.playlist = new ArrayList<>(playlist);
        this.currentPlaylistPosition = position;

        if (shuffleMode && playlist != null && !playlist.isEmpty()) {
            Audio currentTrack = playlist.get(position);
            List<Audio> shuffled = new ArrayList<>(playlist);
            shuffled.remove(position);
            Collections.shuffle(shuffled, random);
            shuffled.add(0, currentTrack);

            this.playlist = shuffled;
            this.currentPlaylistPosition = 0;
        }
    }

    private void playAudio(String audioUrl) {
        try {
            if (audioUrl == null || audioUrl.isEmpty()) {
                Log.e("MusicPlayer", "Audio URL is null or empty");
                return;
            }

            // Останавливаем текущее воспроизведение
            exoPlayer.stop();

            // Создаем MediaSource в зависимости от формата
            MediaSource mediaSource = createMediaSource(audioUrl);

            // Устанавливаем источник и начинаем воспроизведение
            exoPlayer.setMediaSource(mediaSource);
            exoPlayer.prepare();
            exoPlayer.play();

            showNotification(true);
            broadcastPlayerState();

        } catch (Exception e) {
            Log.e("MusicPlayer", "Error playing audio: " + audioUrl, e);
            handlePlaybackError();
        }
    }

    private MediaSource createMediaSource(String audioUrl) {
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(requireContext());

        // Определяем тип контента по расширению файла или URL
        if (audioUrl.toLowerCase().contains(".m3u8")) {
            // HLS поток
            return new HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(MediaItem.fromUri(audioUrl));
        } else {
            // Прогрессивная загрузка (MP3, AAC и т.д.)
            return new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl));
        }
    }

    // Реализация методов Player.Listener
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        switch (playbackState) {
            case Player.STATE_READY:
                broadcastPlayerState();
                break;
            case Player.STATE_ENDED:
                handleTrackCompletion();
                break;
            case Player.STATE_BUFFERING:
                broadcastPlayerState();
                break;
            case Player.STATE_IDLE:
                break;
        }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        Log.e("MusicPlayer", "Playback error: " + error.getMessage(), error);
        handlePlaybackError();
    }

    private void handleTrackCompletion() {
        if (repeatMode == REPEAT_MODE_ONE) {
            // Повторяем текущий трек
            seekTo(0);
            exoPlayer.play();
        } else if (hasNextTrack()) {
            playNext();
        } else if (repeatMode == REPEAT_MODE_ALL) {
            currentPlaylistPosition = 0;
            playTrackFromPlaylist();
        } else {
            showNotification(false);
            broadcastPlayerState();
            stopForeground(false);
        }
    }

    private void handlePlaybackError() {
        if (hasNextTrack()) {
            new Handler().postDelayed(this::playNext, 500);
        } else {
            stopSelf();
        }
    }

    public void playNext() {
        if (playlist == null || playlist.isEmpty()) {
            Log.d("MusicPlayer", "Playlist is empty");
            return;
        }

        if (!hasNextTrack()) {
            if (repeatMode == REPEAT_MODE_ALL) {
                currentPlaylistPosition = 0;
                playTrackFromPlaylist();
            } else {
                seekTo(0);
                pause();
            }
            return;
        }

        currentPlaylistPosition++;
        playTrackFromPlaylist();
        broadcastPlayerState();
    }

    public void playPrevious() {
        if (playlist == null || playlist.isEmpty()) {
            Log.d("MusicPlayer", "Playlist is empty");
            return;
        }

        if (exoPlayer.getCurrentPosition() > 3000) {
            seekTo(0);
            return;
        }

        if (!hasPreviousTrack()) {
            if (repeatMode == REPEAT_MODE_ALL) {
                currentPlaylistPosition = playlist.size() - 1;
                playTrackFromPlaylist();
            } else {
                seekTo(0);
            }
            return;
        }

        currentPlaylistPosition--;
        playTrackFromPlaylist();
        broadcastPlayerState();
    }

    private void playTrackFromPlaylist() {
        if (playlist == null || playlist.isEmpty() || currentPlaylistPosition >= playlist.size()) {
            return;
        }

        try {
            Audio audio = playlist.get(currentPlaylistPosition);
            currentTrackTitle = audio.getTitle();
            currentArtist = audio.getArtist();
            currentFilePath = audio.getUrl();
            isLocalFile = false;

            playAudio(currentFilePath);
        } catch (Exception e) {
            Log.e("MusicPlayer", "Error playing track from playlist", e);
            if (hasNextTrack()) {
                playNext();
            } else {
                stop();
            }
        }
    }

    private void pause() {
        if (exoPlayer.isPlaying()) {
            exoPlayer.pause();
            showNotification(false);
            broadcastPlayerState();
        }
    }

    private void resume() {
        if (!exoPlayer.isPlaying()) {
            exoPlayer.play();
            showNotification(true);
            broadcastPlayerState();
        }
    }

    public void togglePlayPause() {
        if (exoPlayer.isPlaying()) {
            pause();
        } else {
            resume();
        }
        broadcastPlayerState();
    }

    public void stop() {
        exoPlayer.stop();
        stopForeground(true);
        stopSelf();
        broadcastPlayerState();
    }

    public void seekTo(int position) {
        exoPlayer.seekTo(position);
        broadcastPlayerState();
    }

    private void broadcastPlayerState() {
        Intent intent = new Intent("PLAYER_STATE_CHANGED");
        intent.putExtra("TITLE", currentTrackTitle);
        intent.putExtra("ARTIST", currentArtist);
        intent.putExtra("IS_PLAYING", isPlaying());
        intent.putExtra("CURRENT_POSITION", getCurrentPosition());
        intent.putExtra("DURATION", getDuration());
        intent.putExtra("COVER_URL", coverUrl);
        intent.putExtra("PLAYLIST_POSITION", currentPlaylistPosition);
        intent.putExtra("PLAYLIST_SIZE", playlist != null ? playlist.size() : 0);
        intent.putExtra("REPEAT_MODE", repeatMode);
        intent.putExtra("SHUFFLE_MODE", shuffleMode);

        // ВАЖНО: добавляем флаг для надежной доставки
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        sendBroadcast(intent);

        Log.d("MusicPlayerService", "Broadcast sent - " + currentTrackTitle + ", Playing: " + isPlaying());
    }

    @SuppressLint({"ForegroundServiceType", "NewApi"})
    private void showNotification(boolean isPlaying) {
        Intent notificationIntent = new Intent(this, BaseActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        String playlistInfo = (playlist != null && !playlist.isEmpty()) ?
                (currentPlaylistPosition + 1) + "/" + playlist.size() : "";

        // Создаем иконки для режимов повтора
        int repeatIcon = getRepeatIcon();
        int shuffleIcon = shuffleMode ? R.drawable.shuffle : R.drawable.shuffle;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(currentTrackTitle)
                .setContentText(currentArtist + (playlistInfo.isEmpty() ? "" : " • " + playlistInfo))
                .setSmallIcon(R.drawable.music_note_24dp)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .addAction(createAction(R.drawable.ic_vector_round_skip_previous, "Previous", ACTION_PREVIOUS))
                .addAction(createAction(
                        isPlaying ? R.drawable.pause : R.drawable.play,
                        isPlaying ? "Pause" : "Play",
                        ACTION_TOGGLE))
                .addAction(createAction(R.drawable.ic_vector_round_skip_next, "Next", ACTION_NEXT))
                .addAction(createAction(repeatIcon, "Repeat", ACTION_CHANGE_REPEAT_MODE))
                .addAction(createAction(shuffleIcon, "Shuffle", ACTION_CHANGE_SHUFFLE_MODE))
                .addAction(createAction(R.drawable.pause, "Stop", ACTION_STOP));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, builder.build());
        }
    }

    private int getRepeatIcon() {
        switch (repeatMode) {
            case REPEAT_MODE_ONE:
                return R.drawable.refresh_black;
            case REPEAT_MODE_ALL:
                return R.drawable.refresh_black;
            case REPEAT_MODE_OFF:
            default:
                return R.drawable.refresh_black;
        }
    }

    private void updateNotification() {
        if (exoPlayer != null) {
            showNotification(exoPlayer.isPlaying());
        }
    }

    private NotificationCompat.Action createAction(int icon, String title, String action) {
        Intent intent = new Intent(this, MusicPlayerService.class);
        intent.setAction(action);
        PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action(icon, title, pendingIntent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Music playback controls");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    public int getCurrentPosition() {
        return exoPlayer != null ? (int) exoPlayer.getCurrentPosition() : 0;
    }

    public int getDuration() {
        return exoPlayer != null ? (int) exoPlayer.getDuration() : 0;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(int repeatMode) {
        this.repeatMode = repeatMode;
        updateExoPlayerRepeatMode();
        updateNotification();
        broadcastPlayerState();
    }

    public boolean getShuffleMode() {
        return shuffleMode;
    }

    public void setShuffleMode(boolean shuffleMode) {
        this.shuffleMode = shuffleMode;
        if (shuffleMode && playlist != null && !playlist.isEmpty()) {
            originalPlaylist = new ArrayList<>(playlist);
            Audio currentTrack = playlist.get(currentPlaylistPosition);
            List<Audio> shuffled = new ArrayList<>(playlist);
            shuffled.remove(currentPlaylistPosition);
            Collections.shuffle(shuffled, random);
            shuffled.add(0, currentTrack);
            playlist = shuffled;
            currentPlaylistPosition = 0;
        } else if (!shuffleMode && !originalPlaylist.isEmpty()) {
            Audio currentTrack = playlist.get(currentPlaylistPosition);
            playlist = new ArrayList<>(originalPlaylist);
            currentPlaylistPosition = playlist.indexOf(currentTrack);
            if (currentPlaylistPosition == -1) currentPlaylistPosition = 0;
        }
        updateNotification();
        broadcastPlayerState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public String getCurrentTrackTitle() {
        return currentTrackTitle;
    }

    public String getCurrentArtist() {
        return currentArtist;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public boolean hasNextTrack() {
        return playlist != null && currentPlaylistPosition < playlist.size() - 1;
    }

    public boolean hasPreviousTrack() {
        return playlist != null && currentPlaylistPosition > 0;
    }

    public List<Audio> getPlaylist() {
        return playlist;
    }

    public int getCurrentPlaylistPosition() {
        return currentPlaylistPosition;
    }

    public Audio getCurrentAudio() {
        if (playlist != null && currentPlaylistPosition >= 0 && currentPlaylistPosition < playlist.size()) {
            return playlist.get(currentPlaylistPosition);
        }
        return null;
    }
}