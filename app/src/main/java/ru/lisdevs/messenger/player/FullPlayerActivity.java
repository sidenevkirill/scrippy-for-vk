package ru.lisdevs.messenger.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.service.MusicPlayerService;

public class FullPlayerActivity extends AppCompatActivity implements
        SeekBar.OnSeekBarChangeListener {

    private TextView trackTitle, artistName;
    private ImageView nextButton, prevButton;
    private ImageView playPauseButton;
    private SeekBar seekBar;
    private Handler seekBarHandler = new Handler();
    private Runnable updateSeekBar;
    private MusicPlayerService musicService;
    private boolean isBound = false;

    private GestureDetector gestureDetector;
    private View rootView;
    private float initialY;
    private float initialTouchY;
    private boolean isDragging = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            musicService = binder.getService();
            isBound = true;
            updatePlayerUI();
            startSeekBarUpdates();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_player);

        // Инициализация элементов UI
        trackTitle = findViewById(R.id.full_player_track_title);
        artistName = findViewById(R.id.full_player_artist);
        playPauseButton = findViewById(R.id.full_player_play_pause);
        nextButton = findViewById(R.id.full_player_next);
        prevButton = findViewById(R.id.full_player_prev);
        seekBar = findViewById(R.id.full_player_seekbar);
        rootView = findViewById(R.id.root_layout);
        SeekBar seekBar = findViewById(R.id.full_player_seekbar);

        seekBar.getProgressDrawable().setColorFilter(
                ContextCompat.getColor(this, R.color.seekbar_progress),
                PorterDuff.Mode.SRC_IN
        );

        seekBar.getThumb().setColorFilter(
                ContextCompat.getColor(this, R.color.seekbar_thumb),
                PorterDuff.Mode.SRC_IN
        );

// Для фона (только API 21+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setBackgroundTintList(
                    ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.seekbar_background)
                    )
            );
        }

        // Настройка жестов
        gestureDetector = new GestureDetector(this, new SwipeGestureListener());

        // Обработка касаний
        rootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = rootView.getY();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaY = event.getRawY() - initialTouchY;
                        if (deltaY > 0) { // Только вниз
                            float progress = deltaY / (getResources().getDisplayMetrics().heightPixels * 0.5f);
                            rootView.setY(initialY + deltaY);
                            rootView.setAlpha(1 - progress * 0.5f);
                            isDragging = true;
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            if (rootView.getY() > getResources().getDisplayMetrics().heightPixels / 4) {
                                finishWithAnimation();
                            } else {
                                // Возврат на место
                                rootView.animate()
                                        .y(initialY)
                                        .alpha(1f)
                                        .start();
                            }
                            isDragging = false;
                        }
                        return true;
                }
                return false;
            }
        });

        // Установка слушателей кнопок
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        nextButton.setOnClickListener(v -> playNext());
        prevButton.setOnClickListener(v -> playPrevious());
        seekBar.setOnSeekBarChangeListener(this);

        // Получение данных из Intent
        Intent intent = getIntent();
        if (intent != null) {
            trackTitle.setText(intent.getStringExtra("TITLE"));
            artistName.setText(intent.getStringExtra("ARTIST"));
            seekBar.setMax(intent.getIntExtra("DURATION", 0));
            seekBar.setProgress(intent.getIntExtra("CURRENT_POSITION", 0));

            boolean isPlaying = intent.getBooleanExtra("IS_PLAYING", false);
            playPauseButton.setImageResource(
                    isPlaying ? R.drawable.pause : R.drawable.play);
        }

        // Привязка к сервису
        Intent serviceIntent = new Intent(this, MusicPlayerService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float diffY = e2.getY() - e1.getY();
            float diffX = e2.getX() - e1.getX();

            if (Math.abs(diffY) > Math.abs(diffX) &&
                    Math.abs(diffY) > SWIPE_THRESHOLD &&
                    Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {

                if (diffY > 0) {
                    finishWithAnimation();
                    return true;
                }
            }
            return false;
        }
    }

    private void finishWithAnimation() {
        finish();
        overridePendingTransition(R.anim.slide_in_top, R.anim.slide_out_bottom);
    }

    private void updatePlayerUI() {
        if (isBound && musicService != null) {
            trackTitle.setText(musicService.getCurrentTrackTitle());
            artistName.setText(musicService.getCurrentArtist());
            seekBar.setMax(musicService.getDuration());
            seekBar.setProgress(musicService.getCurrentPosition());
            playPauseButton.setImageResource(
                    musicService.isPlaying() ? R.drawable.pause : R.drawable.play);
        }
    }

    private void togglePlayPause() {
        if (isBound) {
            Intent intent = new Intent(this, MusicPlayerService.class);
            intent.setAction("TOGGLE");
            startService(intent);
            updatePlayerUI();
        }
    }

    private void playNext() {
        if (isBound) {
            // Реализация перехода к следующему треку
            updatePlayerUI();
        }
    }

    private void playPrevious() {
        if (isBound) {
            // Реализация перехода к предыдущему треку
            updatePlayerUI();
        }
    }

    private void startSeekBarUpdates() {
        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (isBound && musicService != null) {
                    seekBar.setProgress(musicService.getCurrentPosition());
                    playPauseButton.setImageResource(
                            musicService.isPlaying() ? R.drawable.pause : R.drawable.play);
                }
                seekBarHandler.postDelayed(this, 1000);
            }
        };
        seekBarHandler.postDelayed(updateSeekBar, 1000);
    }

    private void stopSeekBarUpdates() {
        seekBarHandler.removeCallbacks(updateSeekBar);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && isBound) {
            Intent intent = new Intent(this, MusicPlayerService.class);
            intent.setAction("SEEK");
            intent.putExtra("POSITION", progress);
            startService(intent);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        stopSeekBarUpdates();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        startSeekBarUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSeekBarUpdates();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    //public void onCloseClick(View view) {
    //    finishWithAnimation();
    //}
}