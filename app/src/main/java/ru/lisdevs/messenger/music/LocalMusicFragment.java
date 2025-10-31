package ru.lisdevs.messenger.music;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.service.MusicPlayerService;

public class LocalMusicFragment extends Fragment implements ServiceConnection {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private RecyclerView recyclerView;
    private LocalAudioAdapter adapter;
    private List<LocalAudio> audioList = new ArrayList<>();
    private List<LocalAudio> filteredList = new ArrayList<>();
    private MusicPlayerService musicService;
    private boolean isBound = false;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyView;
    private boolean isSearchMode = false;
    private Toolbar toolbar; // Добавлен Toolbar

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local, container, false);

        // Инициализация Toolbar
        toolbar = view.findViewById(R.id.toolbar);
        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);

        // Настройка стрелки назад
        if (((AppCompatActivity) requireActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> {
            // Возвращаемся назад
            requireActivity().onBackPressed();
        });

        recyclerView = view.findViewById(R.id.recyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        emptyView = view.findViewById(R.id.emptyView);

        adapter = new LocalAudioAdapter(audioList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(position -> {
            LocalAudio audio = isSearchMode ? filteredList.get(position) : audioList.get(position);
            playAudio(audio);
        });

        swipeRefreshLayout.setOnRefreshListener(this::loadLocalAudio);

        Intent serviceIntent = new Intent(getActivity(), MusicPlayerService.class);
        getActivity().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);

        checkPermissionsAndLoad();

        return view;
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.search, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setQueryHint("Поиск треков");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterAudio(newText);
                return true;
            }
        });

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                isSearchMode = true;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                isSearchMode = false;
                adapter.updateList(audioList);
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    private void filterAudio(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(audioList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (LocalAudio audio : audioList) {
                if (audio.getTitle().toLowerCase().contains(lowerCaseQuery) ||
                        audio.getArtist().toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(audio);
                }
            }
        }
        adapter.updateList(filteredList);
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (isSearchMode) {
            if (filteredList.isEmpty()) {
                emptyView.setText("Ничего не найдено");
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        } else {
            if (audioList.isEmpty()) {
                emptyView.setText("Нет локальных треков");
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void playAudio(LocalAudio audio) {
        if (isBound && musicService != null) {
            File file = new File(audio.getPath());
            if (file.exists()) {
                Intent playIntent = new Intent(getActivity(), MusicPlayerService.class);
                playIntent.setAction("PLAY_LOCAL");
                playIntent.putExtra("FILE_PATH", audio.getPath());
                playIntent.putExtra("TITLE", audio.getTitle());
                playIntent.putExtra("ARTIST", audio.getArtist());
                getActivity().startService(playIntent);
            } else {
                Toast.makeText(getContext(), "Файл не найден", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkPermissionsAndLoad() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        } else {
            loadLocalAudio();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLocalAudio();
            } else {
                Toast.makeText(getContext(), "Доступ запрещен", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
                updateEmptyView();
            }
        }
    }

    private void loadLocalAudio() {
        swipeRefreshLayout.setRefreshing(true);

        new Thread(() -> {
            List<LocalAudio> localAudios = new ArrayList<>();
            String[] folders = {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath(),
                    Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download"
            };

            for (String folderPath : folders) {
                File folder = new File(folderPath);
                if (folder.exists() && folder.isDirectory()) {
                    File[] files = folder.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (isAudioFile(file)) {
                                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                try {
                                    retriever.setDataSource(file.getAbsolutePath());
                                    String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                                    String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);

                                    if (title == null || title.isEmpty()) {
                                        title = file.getName().replaceFirst("[.][^.]+$", "");
                                    }
                                    if (artist == null) {
                                        artist = "Неизвестный исполнитель";
                                    }

                                    localAudios.add(new LocalAudio(title, artist, file.getAbsolutePath()));
                                } catch (Exception e) {
                                    Log.e("LocalMusic", "Ошибка чтения метаданных", e);
                                } finally {
                                    try {
                                        retriever.release();
                                    } catch (IOException e) {
                                        Log.e("LocalMusic", "Ошибка освобождения ресурсов", e);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            getActivity().runOnUiThread(() -> {
                audioList.clear();
                audioList.addAll(localAudios);
                adapter.updateList(audioList);
                swipeRefreshLayout.setRefreshing(false);
                updateEmptyView();
            });
        }).start();
    }

    private boolean isAudioFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a")
                || name.endsWith(".ogg") || name.endsWith(".flac");
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
        musicService = binder.getService();
        isBound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        isBound = false;
        musicService = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isBound) {
            getActivity().unbindService(this);
            isBound = false;
        }
    }

    public static class LocalAudio {
        private final String title;
        private final String artist;
        private final String path;

        public LocalAudio(String title, String artist, String path) {
            this.title = title;
            this.artist = artist;
            this.path = path;
        }

        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getPath() { return path; }
    }
}