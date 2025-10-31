package ru.lisdevs.messenger.local;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
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

public class LocalPlaylistDetailsFragment extends Fragment implements ServiceConnection {
    private static final String ARG_PLAYLIST_PATH = "playlist_path";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private RecyclerView recyclerView;
    private AudioAdapter adapter;
    private List<LocalAudio> audioList = new ArrayList<>();
    private List<LocalAudio> filteredList = new ArrayList<>();
    private MusicPlayerService musicService;
    private boolean isBound = false;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyView;
    private boolean isSearchMode = false;
    private Toolbar toolbar;
    private String playlistPath;

    public static LocalPlaylistDetailsFragment newInstance(String playlistPath) {
        LocalPlaylistDetailsFragment fragment = new LocalPlaylistDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PLAYLIST_PATH, playlistPath);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (getArguments() != null) {
            playlistPath = getArguments().getString(ARG_PLAYLIST_PATH);
        }

        // Привязка к сервису
        Intent serviceIntent = new Intent(getActivity(), MusicPlayerService.class);
        getActivity().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_playlist_details, container, false);

        // Инициализация UI
        toolbar = view.findViewById(R.id.toolbar);
        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);

        // Устанавливаем название плейлиста в Toolbar
        if (((AppCompatActivity) requireActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(new File(playlistPath).getName());
            ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        recyclerView = view.findViewById(R.id.recyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        emptyView = view.findViewById(R.id.emptyView);

        // Настройка адаптера
        adapter = new AudioAdapter(audioList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(position -> {
            LocalAudio audio = isSearchMode ? filteredList.get(position) : audioList.get(position);
            playAudio(audio);
        });

        swipeRefreshLayout.setOnRefreshListener(this::loadAudioFiles);

        checkPermissionsAndLoad();

        return view;
    }

    private void setupRecyclerView() {
        adapter = new AudioAdapter(audioList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(position -> {
            LocalAudio audio = isSearchMode ? filteredList.get(position) : audioList.get(position);
            playAudio(audio);
        });
    }

    private void loadAudioFiles() {
        swipeRefreshLayout.setRefreshing(true);

        new Thread(() -> {
            List<LocalAudio> loadedAudios = new ArrayList<>();
            File playlistDir = new File(playlistPath);

            if (playlistDir.exists() && playlistDir.isDirectory()) {
                File[] files = playlistDir.listFiles(file -> {
                    String name = file.getName().toLowerCase();
                    return name.endsWith(".mp3") || name.endsWith(".wav") ||
                            name.endsWith(".m4a") || name.endsWith(".flac");
                });

                if (files != null) {
                    for (File file : files) {
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(file.getAbsolutePath());

                            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);

                            if (title == null || title.isEmpty()) {
                                title = file.getName().replaceFirst("[.][^.]+$", "");
                            }
                            if (artist == null) {
                                artist = "Unknown Artist";
                            }

                            loadedAudios.add(new LocalAudio(title, artist, file.getAbsolutePath()));
                        } catch (Exception e) {
                            Log.e("LocalPlaylist", "Error reading metadata", e);
                        } finally {
                            try {
                                retriever.release();
                            } catch (IOException e) {
                                Log.e("LocalPlaylist", "Error releasing retriever", e);
                            }
                        }
                    }
                }
            }

            requireActivity().runOnUiThread(() -> {
                audioList.clear();
                audioList.addAll(loadedAudios);
                adapter.updateList(audioList);
                swipeRefreshLayout.setRefreshing(false);
                updateEmptyView();
            });
        }).start();
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
                requireActivity().startService(playIntent);
            } else {
                Toast.makeText(getContext(), "File not found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.search, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setQueryHint("Search tracks");
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
            emptyView.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(filteredList.isEmpty() ? View.GONE : View.VISIBLE);
            emptyView.setText(filteredList.isEmpty() ? "No tracks found" : "");
        } else {
            emptyView.setVisibility(audioList.isEmpty() ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(audioList.isEmpty() ? View.GONE : View.VISIBLE);
            emptyView.setText(audioList.isEmpty() ? "No tracks in playlist" : "");
        }
    }

    private void checkPermissionsAndLoad() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        } else {
            loadAudioFiles();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAudioFiles();
            } else {
                Toast.makeText(getContext(), "Permission denied", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
                updateEmptyView();
            }
        }
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
            requireActivity().unbindService(this);
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

    public static class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.AudioViewHolder> {
        private List<LocalAudio> audioList;
        private OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(int position);
        }

        public AudioAdapter(List<LocalAudio> audioList) {
            this.audioList = audioList;
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            this.listener = listener;
        }

        public void updateList(List<LocalAudio> newList) {
            this.audioList = new ArrayList<>(newList);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AudioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_audio, parent, false);
            return new AudioViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AudioViewHolder holder, int position) {
            LocalAudio audio = audioList.get(position);
            holder.titleText.setText(audio.getTitle());
            holder.artistText.setText(audio.getArtist());

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return audioList.size();
        }

        static class AudioViewHolder extends RecyclerView.ViewHolder {
            TextView titleText;
            TextView artistText;

            public AudioViewHolder(@NonNull View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.titleText);
                artistText = itemView.findViewById(R.id.artistText);
            }
        }
    }
}