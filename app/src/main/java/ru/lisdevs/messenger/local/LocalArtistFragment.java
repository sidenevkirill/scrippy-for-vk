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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.service.MusicPlayerService;

public class LocalArtistFragment extends Fragment implements ServiceConnection {
    private static final String ARG_PLAYLIST_PATH = "playlist_path";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private RecyclerView recyclerView;
    private ArtistAdapter adapter;
    private Map<String, List<LocalAudio>> artistAudioMap = new LinkedHashMap<>();
    private List<String> artistNames = new ArrayList<>();
    private MusicPlayerService musicService;
    private boolean isBound = false;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyView;
    private boolean isSearchMode = false;
    private Toolbar toolbar;
    private String playlistPath = "";

    public static LocalArtistFragment newInstance(String playlistPath) {
        if (playlistPath == null || playlistPath.isEmpty()) {
            throw new IllegalArgumentException("Playlist path cannot be null or empty");
        }

        LocalArtistFragment fragment = new LocalArtistFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PLAYLIST_PATH, playlistPath);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        Bundle args = getArguments();
        if (args == null || !args.containsKey(ARG_PLAYLIST_PATH)) {
            Log.e("LocalArtistFragment", "No playlist path provided");
            showErrorAndExit("Invalid playlist path");
            return;
        }

        playlistPath = args.getString(ARG_PLAYLIST_PATH, "");
        if (playlistPath.isEmpty()) {
            Log.e("LocalArtistFragment", "Empty playlist path");
            showErrorAndExit("Empty playlist path");
            return;
        }

        try {
            Intent serviceIntent = new Intent(getActivity(), MusicPlayerService.class);
            requireActivity().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e("LocalArtistFragment", "Failed to bind service", e);
        }
    }

    private void showErrorAndExit(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
        if (isAdded() && !isDetached()) {
//            requireActivity().onBackPressed();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_playlist_details, container, false);

        try {
            toolbar = view.findViewById(R.id.toolbar);
            if (toolbar != null && getActivity() != null) {
                ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
                if (((AppCompatActivity) requireActivity()).getSupportActionBar() != null) {
                    ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(
                            new File(playlistPath).getName());
                    ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                }
                toolbar.setNavigationOnClickListener(v -> {
                    if (isAdded() && !isDetached()) {
                        requireActivity().onBackPressed();
                    }
                });
            }

            recyclerView = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            emptyView = view.findViewById(R.id.emptyView);

            if (recyclerView != null) {
                adapter = new ArtistAdapter();
                recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                recyclerView.setAdapter(adapter);
            }

            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setOnRefreshListener(this::loadAudioFiles);
            }

            if (!playlistPath.isEmpty()) {
                checkPermissionsAndLoad();
            }
        } catch (Exception e) {
            Log.e("LocalArtistFragment", "Error initializing view", e);
        }

        return view;
    }

    private void setupRecyclerView(List<LocalAudio> audios) {
        if (audios == null || audios.isEmpty() || adapter == null) {
            updateEmptyView();
            return;
        }

        try {
            artistAudioMap.clear();
            artistNames.clear();

            for (LocalAudio audio : audios) {
                String artist = audio.getArtist() != null ? audio.getArtist() : "Unknown Artist";
                if (!artistAudioMap.containsKey(artist)) {
                    artistAudioMap.put(artist, new ArrayList<>());
                    artistNames.add(artist);
                }
                artistAudioMap.get(artist).add(audio);
            }

            Collections.sort(artistNames);
            adapter.updateData(artistNames, artistAudioMap);
        } catch (Exception e) {
            Log.e("LocalArtistFragment", "Error setting up recycler view", e);
        }
    }

    private void loadAudioFiles() {
        if (getContext() == null || isDetached() || swipeRefreshLayout == null) {
            return;
        }

        if (!checkStoragePermission()) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
            return;
        }

        swipeRefreshLayout.setRefreshing(true);

        new Thread(() -> {
            List<LocalAudio> loadedAudios = new ArrayList<>();
            try {
                File playlistDir = new File(playlistPath);

                if (!playlistDir.exists() || !playlistDir.isDirectory()) {
                    requireActivity().runOnUiThread(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), "Directory not found: " + playlistPath,
                                Toast.LENGTH_LONG).show();
                        updateEmptyView();
                    });
                    return;
                }

                File[] files = playlistDir.listFiles(file -> {
                    if (file == null || file.isDirectory()) return false;
                    String name = file.getName().toLowerCase();
                    return name.endsWith(".mp3") || name.endsWith(".wav") ||
                            name.endsWith(".m4a") || name.endsWith(".flac");
                });

                if (files != null) {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    for (File file : files) {
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
                        } catch (RuntimeException e) {
                            Log.e("LocalArtist", "Error reading file: " + file.getAbsolutePath(), e);
                        }
                    }
                    try {
                        retriever.release();
                    } catch (IOException e) {
                        Log.e("LocalArtist", "Error releasing retriever", e);
                    }
                }
            } catch (Exception e) {
                Log.e("LocalArtistFragment", "Error loading audio files", e);
            }

            if (getActivity() != null && !isDetached()) {
                requireActivity().runOnUiThread(() -> {
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    setupRecyclerView(loadedAudios);
                    updateEmptyView();
                });
            }
        }).start();
    }

    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void playAudio(LocalAudio audio) {
        if (isBound && musicService != null && audio != null && audio.getPath() != null) {
            try {
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
            } catch (Exception e) {
                Log.e("LocalArtistFragment", "Error playing audio", e);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.search, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
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
            }

            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    isSearchMode = true;
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    isSearchMode = false;
                    loadAudioFiles();
                    return true;
                }
            });
        }
    }

    private void filterAudio(String query) {
        if (adapter == null) return;

        List<LocalAudio> filteredList = new ArrayList<>();
        Map<String, List<LocalAudio>> filteredMap = new LinkedHashMap<>();
        List<String> filteredArtists = new ArrayList<>();

        if (query == null || query.isEmpty()) {
            loadAudioFiles();
            return;
        }

        String lowerCaseQuery = query.toLowerCase();
        for (Map.Entry<String, List<LocalAudio>> entry : artistAudioMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            List<LocalAudio> filteredArtistAudios = new ArrayList<>();
            for (LocalAudio audio : entry.getValue()) {
                if (audio == null) continue;

                if ((audio.getTitle() != null && audio.getTitle().toLowerCase().contains(lowerCaseQuery)) ||
                        (audio.getArtist() != null && audio.getArtist().toLowerCase().contains(lowerCaseQuery))) {
                    filteredArtistAudios.add(audio);
                }
            }
            if (!filteredArtistAudios.isEmpty()) {
                filteredMap.put(entry.getKey(), filteredArtistAudios);
                filteredArtists.add(entry.getKey());
            }
        }
        adapter.updateData(filteredArtists, filteredMap);
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (emptyView == null || recyclerView == null || adapter == null) return;

        boolean isEmpty = adapter.getItemCount() == 0;
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyView.setText(isEmpty ? (isSearchMode ? "No tracks found" : "No tracks in playlist") : "");
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
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAudioFiles();
            } else {
                Toast.makeText(getContext(), "Permission denied", Toast.LENGTH_SHORT).show();
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                updateEmptyView();
            }
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        try {
            MusicPlayerService.LocalBinder binder = (MusicPlayerService.LocalBinder) service;
            musicService = binder.getService();
            isBound = true;
        } catch (Exception e) {
            Log.e("LocalArtistFragment", "Error in service connection", e);
        }
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
            try {
                requireActivity().unbindService(this);
            } catch (Exception e) {
                Log.e("LocalArtistFragment", "Error unbinding service", e);
            }
            isBound = false;
        }
    }

    private class ArtistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<String> artists = new ArrayList<>();
        private Map<String, List<LocalAudio>> audioMap = new LinkedHashMap<>();

        public void updateData(List<String> artists, Map<String, List<LocalAudio>> audioMap) {
            this.artists = artists != null ? artists : new ArrayList<>();
            this.audioMap = audioMap != null ? audioMap : new LinkedHashMap<>();
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            int itemCount = 0;
            for (int i = 0; i < artists.size(); i++) {
                if (position == itemCount) {
                    return TYPE_HEADER;
                }
                itemCount++;

                List<LocalAudio> audios = audioMap.get(artists.get(i));
                if (audios != null && position < itemCount + audios.size()) {
                    return TYPE_ITEM;
                }
                itemCount += audios != null ? audios.size() : 0;
            }
            return TYPE_ITEM;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_artist_header, parent, false);
                return new HeaderViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_audio, parent, false);
                return new AudioViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            try {
                int itemCount = 0;
                for (int i = 0; i < artists.size(); i++) {
                    if (position == itemCount) {
                        if (holder instanceof HeaderViewHolder) {
                            ((HeaderViewHolder) holder).artistName.setText(artists.get(i));
                        }
                        return;
                    }
                    itemCount++;

                    List<LocalAudio> audios = audioMap.get(artists.get(i));
                    if (audios != null && position < itemCount + audios.size()) {
                        LocalAudio audio = audios.get(position - itemCount);
                        if (holder instanceof AudioViewHolder && audio != null) {
                            ((AudioViewHolder) holder).titleText.setText(audio.getTitle());
                            ((AudioViewHolder) holder).artistText.setText(audio.getArtist());
                            holder.itemView.setOnClickListener(v -> playAudio(audio));
                        }
                        return;
                    }
                    itemCount += audios != null ? audios.size() : 0;
                }
            } catch (Exception e) {
                Log.e("ArtistAdapter", "Error binding view holder", e);
            }
        }

        @Override
        public int getItemCount() {
            int count = 0;
            for (String artist : artists) {
                count += 1; // header
                List<LocalAudio> audios = audioMap.get(artist);
                count += audios != null ? audios.size() : 0; // items
            }
            return count;
        }
    }

    public static class LocalAudio {
        private final String title;
        private final String artist;
        private final String path;

        public LocalAudio(String title, String artist, String path) {
            this.title = title != null ? title : "";
            this.artist = artist != null ? artist : "Unknown Artist";
            this.path = path != null ? path : "";
        }

        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getPath() { return path; }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView artistName;

        HeaderViewHolder(View itemView) {
            super(itemView);
            artistName = itemView.findViewById(R.id.artistName);
        }
    }

    static class AudioViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView artistText;

        AudioViewHolder(View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.titleText);
            artistText = itemView.findViewById(R.id.artistText);
        }
    }
}