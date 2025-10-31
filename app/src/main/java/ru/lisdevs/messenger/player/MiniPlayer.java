package ru.lisdevs.messenger.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.service.MusicPlayerService;

public class MiniPlayer {
    private final Context context;
    private final LinearLayout container;
    private final TextView artistTextView;
    private final TextView titleTextView;
    private final ImageView playPauseButton;
    private final ImageButton nextButton;
    private final ImageButton prevButton;
    private MusicPlayerService musicService;
    private boolean isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            musicService = binder.getService();
            isBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    public MiniPlayer(Context context, LinearLayout container,
                      TextView artistTextView, TextView titleTextView,
                      ImageView playPauseButton, ImageButton nextButton,
                      ImageButton prevButton) {
        this.context = context;
        this.container = container;
        this.artistTextView = artistTextView;
        this.titleTextView = titleTextView;
        this.playPauseButton = playPauseButton;
        this.nextButton = nextButton;
        this.prevButton = prevButton;

        init();
    }

    private void init() {
        Intent serviceIntent = new Intent(context, MusicPlayerService.class);
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        playPauseButton.setOnClickListener(v -> togglePlayPause());

        if (nextButton != null) {
            nextButton.setOnClickListener(v -> playNext());
        }

        if (prevButton != null) {
            prevButton.setOnClickListener(v -> playPrevious());
        }
    }

    public void play(String url, String title, String artist) {
        if (isBound) {
            Intent intent = new Intent(context, MusicPlayerService.class);
            intent.setAction(MusicPlayerService.ACTION_PLAY);
            intent.putExtra("URL", url);
            intent.putExtra("TITLE", title);
            intent.putExtra("ARTIST", artist);
            context.startService(intent);
            updateUI();
        }
    }

    public void togglePlayPause() {
        if (isBound) {
            Intent intent = new Intent(context, MusicPlayerService.class);
            intent.setAction(MusicPlayerService.ACTION_TOGGLE);
            context.startService(intent);
            updateUI();
        }
    }

    public void playNext() {
        if (isBound && musicService != null) {
            if (musicService.getPlaylist() == null || musicService.getPlaylist().isEmpty()) {
                Toast.makeText(context, "Нет треков для воспроизведения", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!musicService.hasNextTrack()) {
                Toast.makeText(context, "Это последний трек в плейлисте", Toast.LENGTH_SHORT).show();
            }

            Intent intent = new Intent(context, MusicPlayerService.class);
            intent.setAction(MusicPlayerService.ACTION_NEXT);
            context.startService(intent);
            updateUI();
        }
    }

    public void playPrevious() {
        if (isBound && musicService != null) {
            if (musicService.getPlaylist() == null || musicService.getPlaylist().isEmpty()) {
                Toast.makeText(context, "Нет треков для воспроизведения", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(context, MusicPlayerService.class);
            intent.setAction(MusicPlayerService.ACTION_PREVIOUS);
            context.startService(intent);
            updateUI();
        }
    }

    private void updateUI() {
        if (isBound && musicService != null) {
            titleTextView.setText(musicService.getCurrentTrackTitle());
            artistTextView.setText(musicService.getCurrentArtist());
            playPauseButton.setImageResource(
                    musicService.isPlaying() ? R.drawable.pause_black : R.drawable.play_black);
        }
    }

    public void release() {
        if (isBound) {
            context.unbindService(serviceConnection);
            isBound = false;
        }
    }
}