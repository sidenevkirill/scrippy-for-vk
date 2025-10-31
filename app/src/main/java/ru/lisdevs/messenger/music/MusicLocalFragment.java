package ru.lisdevs.messenger.music;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.service.MusicPlayerService;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MusicLocalFragment extends Fragment {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private RecyclerView recyclerView;
    private MusicAdapter adapter;
    private List<File> audioFiles;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Проверяем разрешения
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Запрашиваем разрешение
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        } else {
            // Разрешение уже есть, загружаем файлы
            loadAudioFiles();
        }

        return view;
    }

    // Обработка результата запроса разрешений
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAudioFiles();
            } else {
                Toast.makeText(getContext(), "Разрешение на чтение хранилища необходимо", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Метод для загрузки аудиофайлов
    private void loadAudioFiles() {
        audioFiles = getAudioFiles(getContext());
        Log.d("MusicLocalFragment", "Найдено треков: " + audioFiles.size());

        if (audioFiles.isEmpty()) {
            Toast.makeText(getContext(), "Аудиофайлы не найдены", Toast.LENGTH_SHORT).show();
        }

        adapter = new MusicAdapter(audioFiles, file -> {
            // Обработка клика по треку
            Intent intent = new Intent(getContext(), MusicPlayerService.class);
            intent.setAction("PLAY");
            intent.putExtra("URL", file.getAbsolutePath());
            getContext().startService(intent);
        });
        recyclerView.setAdapter(adapter);
    }

    // Метод для получения аудиофайлов из MediaStore
    private List<File> getAudioFiles(Context context) {
        List<File> files = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";

        try (Cursor cursor = resolver.query(uri, projection, selection, null, null)) {
            if (cursor != null) {
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                while (cursor.moveToNext()) {
                    String path = cursor.getString(dataColumn);
                    File file = new File(path);
                    if (file.exists()) {
                        files.add(file);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Ошибка при получении файлов: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return files;
    }

    // Адаптер для RecyclerView
    private static class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.MusicViewHolder> {

        interface OnItemClickListener {
            void onItemClick(File file);
        }

        private List<File> files;
        private OnItemClickListener listener;

        public MusicAdapter(List<File> files, OnItemClickListener listener) {
            this.files = files;
            this.listener = listener;
        }

        @NonNull
        @Override
        public MusicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_audio, parent, false);
            return new MusicViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull MusicViewHolder holder, int position) {
            File file = files.get(position);
            holder.bind(file, listener);
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        static class MusicViewHolder extends RecyclerView.ViewHolder {

            TextView textViewTitle;
            TextView textViewArtist;

            public MusicViewHolder(@NonNull View itemView) {
                super(itemView);
                textViewTitle = itemView.findViewById(R.id.titleText);
                textViewArtist = itemView.findViewById(R.id.artistText);
            }

            public void bind(final File file, final OnItemClickListener listener) {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                try {
                    mmr.setDataSource(file.getAbsolutePath());
                    String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                    String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);

                    if (title == null || title.isEmpty()) {
                        title = file.getName(); // если метаданные отсутствуют
                    }
                    if (artist == null || artist.isEmpty()) {
                        artist = "Неизвестный исполнитель";
                    }

                    textViewTitle.setText(title);
                    textViewArtist.setText(artist);
                } catch (Exception e) {
                    e.printStackTrace();
                    // В случае ошибки показываем имя файла
                    textViewTitle.setText(file.getName());
                    textViewArtist.setText("Неизвестный исполнитель");
                } finally {
                    try {
                        mmr.release();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                itemView.setOnClickListener(v -> listener.onItemClick(file));
            }
        }
    }
}