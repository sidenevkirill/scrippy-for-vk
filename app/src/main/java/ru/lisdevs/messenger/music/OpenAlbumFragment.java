package ru.lisdevs.messenger.music;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Audio;

public class OpenAlbumFragment extends Fragment {

    private static final String ARG_OWNER_ID = "owner_id";
    private static final String ARG_ALBUM_ID = "album_id";
    private static final String ARG_ALBUM_TITLE = "album_title";
    private static final String ARG_TRACKS = "tracks";

    private RecyclerView recyclerView;
    private AudioAdapter adapter;
    private List<Audio> tracks = new ArrayList<>();
    private long ownerId;
    private long albumId;
    private String albumTitle;

    public static OpenAlbumFragment newInstance(long ownerId, long albumId, String albumTitle, List<Audio> tracks) {
        OpenAlbumFragment fragment = new OpenAlbumFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_OWNER_ID, ownerId);
        args.putLong(ARG_ALBUM_ID, albumId);
        args.putString(ARG_ALBUM_TITLE, albumTitle);
        args.putParcelableArrayList(ARG_TRACKS, new ArrayList<>(tracks));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            ownerId = getArguments().getLong(ARG_OWNER_ID);
            albumId = getArguments().getLong(ARG_ALBUM_ID);
            albumTitle = getArguments().getString(ARG_ALBUM_TITLE);
            tracks = getArguments().getParcelableArrayList(ARG_TRACKS);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_album, container, false);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(albumTitle != null ? albumTitle : "Альбом");
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        recyclerView = view.findViewById(R.id.recyclerView);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new AudioAdapter(tracks);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(position -> {
            if (position >= 0 && position < tracks.size()) {
                playTrack(position);
            }
        });
    }

    private void playTrack(int position) {
        Audio audio = tracks.get(position);
        // Ваш код для воспроизведения трека
    }
}