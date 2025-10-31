package ru.lisdevs.messenger.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.service.MusicPlayerService;

public class PlayerBottomSheetFragment extends BottomSheetDialogFragment
        implements SeekBar.OnSeekBarChangeListener {

    public interface PlayerInteractionListener {
        void onPlayerClosed();
        void onPlayPauseClicked();
        void onNextClicked();
        void onPreviousClicked();
        void onSeekTo(int position);
        void onRepeatModeChanged(int repeatMode);
        void onShuffleModeChanged(boolean shuffleMode); // Новый метод для перемешивания
    }

    private TextView trackTitle, artistName, currentTime, totalTime;
    private ImageView coverImage;
    private ImageButton playPauseButton, nextButton, prevButton, repeatButton, shuffleButton;
    private SeekBar seekBar;
    private Handler seekBarHandler = new Handler();
    private Runnable updateSeekBar;
    private MusicPlayerService musicService;
    private boolean isBound = false;
    private boolean isDragging = false;
    private PlayerInteractionListener listener;

    // Режимы повтора
    private static final int REPEAT_OFF = 0;
    private static final int REPEAT_ONE = 1;
    private static final int REPEAT_ALL = 2;
    private int currentRepeatMode = REPEAT_OFF;
    private boolean currentShuffleMode = false; // Состояние перемешивания

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            musicService = binder.getService();
            isBound = true;

            // Получаем текущие режимы из сервиса
            if (musicService != null) {
                currentRepeatMode = musicService.getRepeatMode();
                currentShuffleMode = musicService.getShuffleMode();
            }

            updatePlayerUI();
            updateRepeatButtonIcon();
            updateShuffleButtonIcon();
            startSeekBarUpdates();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (PlayerInteractionListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement PlayerInteractionListener");
        }
    }

    public static PlayerBottomSheetFragment newInstance() {
        return new PlayerBottomSheetFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.player_bottom_sheet, container, false);
        initViews(view);
        setupListeners();

        Intent serviceIntent = new Intent(getActivity(), MusicPlayerService.class);
        requireActivity().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        return view;
    }

    private void initViews(View view) {
        trackTitle = view.findViewById(R.id.trackTitle);
        artistName = view.findViewById(R.id.artistName);
        currentTime = view.findViewById(R.id.currentTime);
        totalTime = view.findViewById(R.id.totalTime);
        coverImage = view.findViewById(R.id.coverImage);
        playPauseButton = view.findViewById(R.id.playPauseButton);
        nextButton = view.findViewById(R.id.nextButton);
        prevButton = view.findViewById(R.id.prevButton);
        repeatButton = view.findViewById(R.id.repeatButton);
        shuffleButton = view.findViewById(R.id.shuffleButton); // Кнопка перемешивания
        seekBar = view.findViewById(R.id.seekBar);
    }

    private void setupListeners() {
        playPauseButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlayPauseClicked();
                updatePlayerUI();
            }
        });

        nextButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNextClicked();
                playPauseButton.setImageResource(R.drawable.ic_vector_round_skip_next);
                new Handler().postDelayed(this::updatePlayerUI, 500);
            }
        });

        prevButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPreviousClicked();
                playPauseButton.setImageResource(R.drawable.ic_vector_round_skip_previous);
                new Handler().postDelayed(this::updatePlayerUI, 500);
            }
        });

        // Обработчик для кнопки повтора
        repeatButton.setOnClickListener(v -> {
            cycleRepeatMode();
            if (listener != null) {
                listener.onRepeatModeChanged(currentRepeatMode);
            }
            updateRepeatButtonIcon();
            showRepeatModeToast();
        });

        // Долгое нажатие для показа подсказки повтора
        repeatButton.setOnLongClickListener(v -> {
            showRepeatModeToast();
            return true;
        });

        // Обработчик для кнопки перемешивания
        shuffleButton.setOnClickListener(v -> {
            toggleShuffleMode();
            if (listener != null) {
                listener.onShuffleModeChanged(currentShuffleMode);
            }
            updateShuffleButtonIcon();
            showShuffleModeToast();
        });

        // Долгое нажатие для показа подсказки перемешивания
        shuffleButton.setOnLongClickListener(v -> {
            showShuffleModeToast();
            return true;
        });

        seekBar.setOnSeekBarChangeListener(this);
    }

    // Переключение режима перемешивания
    private void toggleShuffleMode() {
        currentShuffleMode = !currentShuffleMode;

        // Сохраняем режим в сервисе, если он привязан
        if (isBound && musicService != null) {
            musicService.setShuffleMode(currentShuffleMode);
        }
    }

    // Обновление иконки кнопки перемешивания
    private void updateShuffleButtonIcon() {
        int colorFilter;

        if (currentShuffleMode) {
            colorFilter = ContextCompat.getColor(requireContext(), R.color.black);
        } else {
            colorFilter = ContextCompat.getColor(requireContext(), R.color.gray);
        }

        shuffleButton.setColorFilter(colorFilter, PorterDuff.Mode.SRC_IN);
    }

    // Показ тоста с текущим режимом перемешивания
    private void showShuffleModeToast() {
        String message = currentShuffleMode ? "Перемешивание включено" : "Перемешивание выключено";
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    // Циклическое переключение режимов повтора
    private void cycleRepeatMode() {
        currentRepeatMode = (currentRepeatMode + 1) % 3;

        // Сохраняем режим в сервисе, если он привязан
        if (isBound && musicService != null) {
            musicService.setRepeatMode(currentRepeatMode);
        }
    }

    // Обновление иконки кнопки повтора
    private void updateRepeatButtonIcon() {
        int iconRes;
        int colorFilter;

        switch (currentRepeatMode) {
            case REPEAT_ONE:
                iconRes = R.drawable.refresh;
                colorFilter = ContextCompat.getColor(requireContext(), R.color.black);
                break;
            case REPEAT_ALL:
                iconRes = R.drawable.refresh;
                colorFilter = ContextCompat.getColor(requireContext(), R.color.black);
                break;
            case REPEAT_OFF:
            default:
                iconRes = R.drawable.refresh;
                colorFilter = ContextCompat.getColor(requireContext(), R.color.gray);
                break;
        }

        repeatButton.setImageResource(iconRes);
        repeatButton.setColorFilter(colorFilter, PorterDuff.Mode.SRC_IN);
    }

    // Показ тоста с текущим режимом повтора
    private void showRepeatModeToast() {
        String message;
        switch (currentRepeatMode) {
            case REPEAT_ONE:
                message = "Повтор одного трека";
                break;
            case REPEAT_ALL:
                message = "Повтор всех треков";
                break;
            case REPEAT_OFF:
            default:
                message = "Повтор выключен";
                break;
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    public void updatePlayerUI() {
        if (isBound && musicService != null) {
            requireActivity().runOnUiThread(() -> {
                trackTitle.setText(musicService.getCurrentTrackTitle());
                artistName.setText(musicService.getCurrentArtist());
                seekBar.setMax(musicService.getDuration());
                seekBar.setProgress(musicService.getCurrentPosition());
                currentTime.setText(formatTime(musicService.getCurrentPosition()));
                totalTime.setText(formatTime(musicService.getDuration()));

                // ExoPlayer методы совместимы
                playPauseButton.setImageResource(
                        musicService.isPlaying() ? R.drawable.circle_pause : R.drawable.circle_play);

                String coverUrl = musicService.getCoverUrl();
                if (coverUrl != null && !coverUrl.isEmpty()) {
                    Glide.with(this)
                            .load(coverUrl)
                            .placeholder(R.drawable.music_note_24dp)
                            .into(coverImage);
                }

                // Обновляем режимы из сервиса
                currentRepeatMode = musicService.getRepeatMode();
                currentShuffleMode = musicService.getShuffleMode();
                updateRepeatButtonIcon();
                updateShuffleButtonIcon();
            });
        }
    }

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private void startSeekBarUpdates() {
        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (isBound && musicService != null && !isDragging) {
                    seekBar.setProgress(musicService.getCurrentPosition());
                    currentTime.setText(formatTime(musicService.getCurrentPosition()));
                }
                seekBarHandler.postDelayed(this, 1000);
            }
        };
        seekBarHandler.post(updateSeekBar);
    }

    private void stopSeekBarUpdates() {
        seekBarHandler.removeCallbacks(updateSeekBar);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            currentTime.setText(formatTime(progress));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        isDragging = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (listener != null) {
            listener.onSeekTo(seekBar.getProgress());
        }
        isDragging = false;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (listener != null) {
            listener.onPlayerClosed();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopSeekBarUpdates();
        if (isBound) {
            requireActivity().unbindService(serviceConnection);
            isBound = false;
        }
    }

    // Геттер для текущего режима повтора
    public int getRepeatMode() {
        return currentRepeatMode;
    }

    // Сеттер для режима повтора
    public void setRepeatMode(int repeatMode) {
        this.currentRepeatMode = repeatMode;
        updateRepeatButtonIcon();
    }

    // Геттер для режима перемешивания
    public boolean getShuffleMode() {
        return currentShuffleMode;
    }

    // Сеттер для режима перемешивания
    public void setShuffleMode(boolean shuffleMode) {
        this.currentShuffleMode = shuffleMode;
        updateShuffleButtonIcon();
    }
}