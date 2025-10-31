package ru.lisdevs.messenger.offline;

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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.local.LocalPlaylistsFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;

public class OfflineMusicFragment extends Fragment implements ServiceConnection {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int DELETE_PERMISSION_REQUEST_CODE = 1002;
    private RecyclerView recyclerView;
    private OfflineAudioAdapter adapter;
    private List<LocalAudio> audioList = new ArrayList<>();
    private List<LocalAudio> filteredList = new ArrayList<>();
    private MusicPlayerService musicService;
    private boolean isBound = false;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyView;
    private boolean isSearchMode = false;
    private Toolbar toolbar;
    private LocalAudio selectedAudioForDeletion;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_offline, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);

        // Setup Toolbar
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayShowHomeEnabled(true);
            }
        }

        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        recyclerView = view.findViewById(R.id.recyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        emptyView = view.findViewById(R.id.emptyView);

        adapter = new OfflineAudioAdapter(audioList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(position -> {
            LocalAudio audio = isSearchMode ? filteredList.get(position) : audioList.get(position);
            playAudio(audio);
        });

        adapter.setOnMenuClickListener(position -> {
            selectedAudioForDeletion = isSearchMode ? filteredList.get(position) : audioList.get(position);
            showBottomSheetMenu();
        });

        swipeRefreshLayout.setOnRefreshListener(this::loadLocalAudio);

        Intent serviceIntent = new Intent(getActivity(), MusicPlayerService.class);
        requireActivity().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);

        checkPermissionsAndLoad();

        return view;
    }

    private void showBottomSheetMenu() {
        if (selectedAudioForDeletion == null || getContext() == null) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(getContext())
                .inflate(R.layout.bottom_sheet_menu_del, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        TextView title = bottomSheetView.findViewById(R.id.bs_title);
        TextView subtitle = bottomSheetView.findViewById(R.id.bs_subtitle);
        LinearLayout deleteOption = bottomSheetView.findViewById(R.id.bs_delete);

        if (title != null) {
            title.setText(selectedAudioForDeletion.getTitle());
        }
        if (subtitle != null) {
            subtitle.setText(selectedAudioForDeletion.getArtist());
        }
        if (deleteOption != null) {
            deleteOption.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                showDeleteConfirmationDialog();
            });
        }

        bottomSheetDialog.show();
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Удаление трека")
                .setMessage("Вы уверены, что хотите удалить этот трек?")
                .setPositiveButton("Удалить", (dialog, which) -> checkDeletePermission())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void checkDeletePermission() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    DELETE_PERMISSION_REQUEST_CODE);
        } else {
            deleteSelectedAudio();
        }
    }

    private void deleteSelectedAudio() {
        if (selectedAudioForDeletion == null || getContext() == null) return;

        new Thread(() -> {
            File audioFile = new File(selectedAudioForDeletion.getPath());
            boolean deleted = false;

            try {
                if (audioFile.exists()) {
                    deleted = audioFile.delete();
                }
            } catch (SecurityException e) {
                Log.e("OfflineMusic", "Security exception when deleting file", e);
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Нет прав для удаления файла", Toast.LENGTH_SHORT).show());
                return;
            }

            boolean finalDeleted = deleted;
            requireActivity().runOnUiThread(() -> {
                if (finalDeleted) {
                    Toast.makeText(getContext(), "Трек удален", Toast.LENGTH_SHORT).show();
                    removeAudioFromLists(selectedAudioForDeletion.getPath());
                    adapter.updateList(isSearchMode ? filteredList : audioList);
                    updateEmptyView();
                } else {
                    Toast.makeText(getContext(), "Ошибка при удалении", Toast.LENGTH_SHORT).show();
                }
                selectedAudioForDeletion = null;
            });
        }).start();
    }

    private void removeAudioFromLists(String path) {
        Iterator<LocalAudio> iterator = audioList.iterator();
        while (iterator.hasNext()) {
            LocalAudio audio = iterator.next();
            if (audio.getPath().equals(path)) {
                iterator.remove();
                break;
            }
        }

        iterator = filteredList.iterator();
        while (iterator.hasNext()) {
            LocalAudio audio = iterator.next();
            if (audio.getPath().equals(path)) {
                iterator.remove();
                break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLocalAudio();
            } else {
                Toast.makeText(getContext(), "Доступ запрещен", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
                updateEmptyView();
            }
        } else if (requestCode == DELETE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                deleteSelectedAudio();
            } else {
                Toast.makeText(getContext(), "Нет разрешения на удаление", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.search_local, menu);

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

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            requireActivity().onBackPressed();
            return true;
        }

        if (item.getItemId() == R.id.action_show_playlists) {
            navigateToFragment(new LocalPlaylistsFragment());
            return true;
        }
        return super.onOptionsItemSelected(item);
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
        if (isBound && musicService != null && getContext() != null) {
            File file = new File(audio.getPath());
            if (file.exists()) {
                Intent playIntent = new Intent(getActivity(), MusicPlayerService.class);
                playIntent.setAction("PLAY_LOCAL");
                playIntent.putExtra("FILE_PATH", audio.getPath());
                playIntent.putExtra("TITLE", audio.getTitle());
                playIntent.putExtra("ARTIST", audio.getArtist());
                requireActivity().startService(playIntent);
            } else {
                Toast.makeText(getContext(), "Файл не найден", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkPermissionsAndLoad() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        } else {
            loadLocalAudio();
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

            requireActivity().runOnUiThread(() -> {
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
            requireActivity().unbindService(this);
            isBound = false;
        }
    }

    private void navigateToFragment(Fragment fragment) {
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }


    public static class OfflineAudioAdapter extends RecyclerView.Adapter<OfflineAudioAdapter.AudioViewHolder> {
        private List<LocalAudio> audioList;
        private OnItemClickListener onItemClickListener;
        private OnMenuClickListener onMenuClickListener;

        public interface OnItemClickListener {
            void onItemClick(int position);
        }

        public interface OnMenuClickListener {
            void onMenuClick(int position);
        }

        public OfflineAudioAdapter(List<LocalAudio> audioList) {
            this.audioList = audioList;
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            this.onItemClickListener = listener;
        }

        public void setOnMenuClickListener(OnMenuClickListener listener) {
            this.onMenuClickListener = listener;
        }

        public void updateList(List<LocalAudio> newList) {
            this.audioList = newList;
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
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(position);
                }
            });

            holder.menuButton.setOnClickListener(v -> {
                if (onMenuClickListener != null) {
                    onMenuClickListener.onMenuClick(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return audioList.size();
        }

        public static class AudioViewHolder extends RecyclerView.ViewHolder {
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