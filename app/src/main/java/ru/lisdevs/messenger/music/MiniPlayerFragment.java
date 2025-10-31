// Новый класс MiniPlayerFragment.java
package ru.lisdevs.messenger.music;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ru.lisdevs.messenger.R;

public class MiniPlayerFragment extends Fragment {

    private ImageView trackIcon;
    private TextView trackTitle;
    private ImageButton playPauseButton;

    private boolean isPlaying = false;

    // Коллбек для управления воспроизведением из активности/фрагмента
    public interface OnPlayPauseListener {
        void onPlayPause();
    }

    private OnPlayPauseListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnPlayPauseListener) {
            listener = (OnPlayPauseListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mini_player, container, false);

        trackIcon = view.findViewById(R.id.trackIcon);
        trackTitle = view.findViewById(R.id.trackTitle);
        playPauseButton = view.findViewById(R.id.buttonPlayPause);

        playPauseButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlayPause();
            }
            togglePlayPause();
        });

        return view;
    }

    public void updateTrackInfo(String title, int iconRes) {
        trackTitle.setText(title);
        trackIcon.setImageResource(iconRes);
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
        if (isPlaying) {
            playPauseButton.setImageResource(R.drawable.play);
        } else {
            playPauseButton.setImageResource(R.drawable.play);
        }
    }

    private void togglePlayPause() {
        setPlaying(!isPlaying);
    }
}
