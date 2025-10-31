package ru.lisdevs.messenger.local;

// Импорты
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.section.SectionFragment;

// Ваши классы модели
class LocalAudio {
    private String title;
    private String artist; // Можно оставить пустым
    private String path;

    public LocalAudio(String title, String artist, String path) {
        this.title = title;
        this.artist = artist;
        this.path = path;
    }

    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getPath() { return path; }
}

// Адаптер для RecyclerView
class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.AudioViewHolder> {

    interface OnMenuClickListener {
        void onMenuClick(LocalAudio audio);
    }

    private List<LocalAudio> data = new ArrayList<>();
    private OnMenuClickListener menuClickListener;

    public void setData(List<LocalAudio> data) {
        this.data = data;
    }

    public void setOnMenuClickListener(OnMenuClickListener listener) {
        this.menuClickListener = listener;
    }

    @NonNull
    @Override
    public AudioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_local_audio, parent, false);
        return new AudioViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AudioViewHolder holder, int position) {
        LocalAudio audio = data.get(position);
        holder.titleText.setText(audio.getTitle());
        holder.artistText.setText(audio.getArtist());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(audio);
            }
        });

        holder.menuButton.setOnClickListener(v -> {
            if (menuClickListener != null) {
                menuClickListener.onMenuClick(audio);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    // Объявление ViewHolder
    static class AudioViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView artistText;
        ImageView menuButton;

        public AudioViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.titleText);
            artistText = itemView.findViewById(R.id.artistText);
            menuButton = itemView.findViewById(R.id.downloadButton);
        }
    }

    // Обработчик кликов по элементам
    interface OnItemClickListener {
        void onItemClick(LocalAudio audio);
    }

    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
}

// BottomSheet для меню (можете расширить по необходимости)
class MyBottomSheet extends BottomSheetDialogFragment {

    static final String ARG_AUDIO_PATH = "audio_path";

    public static MyBottomSheet newInstance(String audioPath) {
        MyBottomSheet fragment = new MyBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_AUDIO_PATH, audioPath);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_audio, container, false);

        // Тут можно добавить обработчики кнопок внутри BottomSheet

        return view;
    }
}

// Сам фрагмент
public class LocalMusicFragment extends Fragment {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private RecyclerView recyclerView;
    private AudioAdapter adapter;

    private MediaPlayer mediaPlayer;
    private TextView currentTrackTitle;

    // Элементы управления плеером
    private ImageView playPauseButton;
    private SeekBar seekBar;
    private TextView currentTimeTextView;

    private boolean isPlaying = false;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBar;
    private View playerLayout;

    public void refreshAudioList() {
        loadLocalAudios(); // повторно загружаем список файлов
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local, container, false);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        // Убедитесь, что у вас есть эта строка:
        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);

        // Добавляем кнопку "назад"
        if (((AppCompatActivity) requireActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

     //   toolbar.setNavigationOnClickListener(v -> navigateBack());

        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        playerLayout = view.findViewById(R.id.controllers_root);
        // Изначально скрываем плеер
        setPlayerVisibility(false);
        currentTrackTitle = view.findViewById(R.id.currentTrackTitle);

        adapter = new AudioAdapter();
        recyclerView.setAdapter(adapter);

        // Инициализация элементов управления плеером
        playPauseButton = view.findViewById(R.id.playPauseButton);
        seekBar = view.findViewById(R.id.seekBar);
        currentTimeTextView = view.findViewById(R.id.currentTimeTextView);

        // Обработка клика по элементу для воспроизведения
        adapter.setOnItemClickListener(audio -> {
            playAudio(audio.getPath(), audio.getTitle());
            updatePlayPauseButton();
        });

        adapter.setOnMenuClickListener(audio -> {
            MyBottomSheet bottomSheet = MyBottomSheet.newInstance(audio.getPath());
            bottomSheet.show(getChildFragmentManager(), "MyBottomSheet");
        });

        checkPermissionsAndLoad();

        setupSeekBar();

        return view;
    }

    private void navigateBack() {
        SectionFragment fragment = new SectionFragment();
        getParentFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean userTouch = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.seekTo(progress);
                    updateCurrentTime();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userTouch = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userTouch = false;
            }
        });
    }

    private void playAudio(String path, String title) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            stopUpdatingSeekBar();
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
            updatePlayPauseButton();

            // Обновляем название текущего трека
            currentTrackTitle.setText(title);

            // Настраиваем прогресс-бар и таймер обновления
            seekBar.setMax(mediaPlayer.getDuration());
            startUpdatingSeekBar();

            // Показываем плеер при начале воспроизведения
            setPlayerVisibility(true);

            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                updatePlayPauseButton();
                stopUpdatingSeekBar();
                seekBar.setProgress(0);
                currentTimeTextView.setText("00:00");

                // Можно сбросить название или оставить как есть
                // currentTrackTitle.setText("Выберите трек");

                // Скрываем плеер после окончания воспроизведения
                setPlayerVisibility(false);
            });

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error playing audio", Toast.LENGTH_SHORT).show();
        }

        // Обработка кнопки паузы/воспроизведения
        playPauseButton.setOnClickListener(v -> {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                isPlaying = false;
            } else {
                mediaPlayer.start();
                isPlaying = true;
            }
            updatePlayPauseButton();
        });
    }

    // Метод для управления видимостью
    private void setPlayerVisibility(boolean visible) {
        if (playerLayout != null) {
            playerLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void updatePlayPauseButton() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            playPauseButton.setImageResource(R.drawable.pause);
        } else {
            playPauseButton.setImageResource(R.drawable.play);
        }
    }

    private void startUpdatingSeekBar() {
        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int currentPos = mediaPlayer.getCurrentPosition();
                    seekBar.setProgress(currentPos);
                    updateCurrentTime();

                    handler.postDelayed(this, 500);
                }
            }
        };
        handler.post(updateSeekBar);
    }

    private void stopUpdatingSeekBar() {
        if (updateSeekBar != null) {
            handler.removeCallbacks(updateSeekBar);
        }
    }

    private void updateCurrentTime() {
        if (mediaPlayer != null) {
            int currentPos = mediaPlayer.getCurrentPosition();

            int seconds = (currentPos / 1000) % 60;
            int minutes = (currentPos / (1000 * 60));

            String timeString = String.format("%02d:%02d", minutes, seconds);

            currentTimeTextView.setText(timeString);
        }
    }

    private void checkPermissionsAndLoad() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        } else {
            loadLocalAudios();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLocalAudios();
            } else {
                Toast.makeText(getContext(), "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadLocalAudios() {
        String folderPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath();

        List<File> files = getAudioFilesFromFolder(folderPath);

        List<LocalAudio> localAudios = new ArrayList<>();
        for (File file : files) {
            String title = file.getName().substring(0, file.getName().lastIndexOf('.'));
            localAudios.add(new LocalAudio(title, "", file.getAbsolutePath()));
        }

        adapter.setData(localAudios);
        adapter.notifyDataSetChanged();
    }

    private List<File> getAudioFilesFromFolder(String folderPath) {
        List<File> audioFiles = new ArrayList<>();
        File folder = new File(folderPath);
        if (folder.exists() && folder.isDirectory()) {
            File[] filesArray = folder.listFiles();
            if (filesArray != null) {
                for (File file : filesArray) {
                    String nameLower = file.getName().toLowerCase();
                    if (nameLower.endsWith(".mp3") || nameLower.endsWith(".wav") || nameLower.endsWith(".m4a")) {
                        audioFiles.add(file);
                    }
                }
            }
        }
        return audioFiles;
    }

    public static class MyBottomSheet extends BottomSheetDialogFragment {

        static final String ARG_AUDIO_PATH = "audio_path";

        private String audioPath;

        public static MyBottomSheet newInstance(String audioPath) {
            MyBottomSheet fragment = new MyBottomSheet();
            Bundle args = new Bundle();
            args.putString(ARG_AUDIO_PATH, audioPath);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.bottom_sheet_audio_del, container, false);

            if (getArguments() != null) {
                audioPath = getArguments().getString(ARG_AUDIO_PATH);
            }

            //Button deleteButton = view.findViewById(R.id.buttonDel);
            //deleteButton.setOnClickListener(v -> {
                // Удаляем файл
           //     deleteAudioFile();
           //     dismiss(); // закрываем BottomSheet
           // });

            return view;
        }

        private void deleteAudioFile() {
            if (audioPath != null) {
                File file = new File(audioPath);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        Toast.makeText(getContext(), "Трек удален", Toast.LENGTH_SHORT).show();
                        // Обновляем список треков в фрагменте
                        if (getParentFragment() instanceof LocalMusicFragment) {
                            ((LocalMusicFragment) getParentFragment()).refreshAudioList();
                        }
                    } else {
                        Toast.makeText(getContext(), "Не удалось удалить трек", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        stopUpdatingSeekBar();
    }
}