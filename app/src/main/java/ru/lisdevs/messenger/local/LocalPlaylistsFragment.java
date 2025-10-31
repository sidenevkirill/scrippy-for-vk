package ru.lisdevs.messenger.local;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;

public class LocalPlaylistsFragment extends Fragment {
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private RecyclerView recyclerView;
    private PlaylistAdapter adapter;
    private List<LocalPlaylist> playlists = new ArrayList<>();
    private MediaPlayer mediaPlayer;
    private TextView emptyView;
    private ProgressBar progressBar;
    private Toolbar toolbar;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlists_local, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        emptyView = view.findViewById(R.id.emptyView);
        progressBar = view.findViewById(R.id.progressBar);
        toolbar = view.findViewById(R.id.toolbar);

        // Setup Toolbar
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayShowHomeEnabled(true);
            }
        }

        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        setupRecyclerView();
        checkPermissionsAndLoad();

        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // Это важно!
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
         if (item.getItemId() == android.R.id.home) {
            requireActivity().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        adapter = new PlaylistAdapter(playlists, playlist -> {
            // Обработка клика по плейлисту
            openPlaylist(playlist);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void checkPermissionsAndLoad() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        } else {
            loadLocalPlaylists();
        }
    }

    private void loadLocalPlaylists() {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            List<LocalPlaylist> foundPlaylists = new ArrayList<>();

            // Сканируем папки с музыкой
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            File[] folders = musicDir.listFiles(File::isDirectory);

            if (folders != null) {
                for (File folder : folders) {
                    // Создаем плейлист для каждой папки
                    LocalPlaylist playlist = new LocalPlaylist(
                            folder.getName(),
                            folder.getAbsolutePath(),
                            countAudioFiles(folder)
                    );
                    foundPlaylists.add(playlist);
                }
            }

            // Обновляем UI в основном потоке
            requireActivity().runOnUiThread(() -> {
                playlists.clear();
                playlists.addAll(foundPlaylists);
                adapter.notifyDataSetChanged();
                updateEmptyView();
                progressBar.setVisibility(View.GONE);
            });
        }).start();
    }

    private int countAudioFiles(File folder) {
        File[] files = folder.listFiles(file -> {
            String name = file.getName().toLowerCase();
            return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a");
        });
        return files != null ? files.length : 0;
    }

    private void updateEmptyView() {
        if (playlists.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void openPlaylist(LocalPlaylist playlist) {
        if (!isAdded()) return;

        Fragment fragment = LocalPlaylistDetailsFragment.newInstance(playlist.getPath());

        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLocalPlaylists();
            } else {
                Toast.makeText(getContext(), "Permission denied", Toast.LENGTH_SHORT).show();
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
    }

    // Модель локального плейлиста
    public static class LocalPlaylist {
        private String name;
        private String path;
        private int trackCount;

        public LocalPlaylist(String name, String path, int trackCount) {
            this.name = name;
            this.path = path;
            this.trackCount = trackCount;
        }

        public String getName() { return name; }
        public String getPath() { return path; }
        public int getTrackCount() { return trackCount; }
    }

    // Адаптер для плейлистов
    private static class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
        private List<LocalPlaylist> playlists;
        private OnPlaylistClickListener listener;

        interface OnPlaylistClickListener {
            void onClick(LocalPlaylist playlist);
        }

        PlaylistAdapter(List<LocalPlaylist> playlists, OnPlaylistClickListener listener) {
            this.playlists = playlists;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlists_local, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LocalPlaylist playlist = playlists.get(position);
            holder.nameText.setText(playlist.getName());
            holder.trackCountText.setText(playlist.getTrackCount() + " треков");

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onClick(playlist);
                }
            });
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView trackCountText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.titleText);
                trackCountText = itemView.findViewById(R.id.artistText);
            }
        }
    }
}