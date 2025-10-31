package ru.lisdevs.messenger.playlists;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import ru.lisdevs.messenger.R;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {
    private List<PlaylistItem> playlists;
    private OnPlaylistClickListener listener;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(PlaylistItem playlist);
    }

    public PlaylistAdapter(List<PlaylistItem> playlists, OnPlaylistClickListener listener) {
        this.playlists = playlists;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlists, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        PlaylistItem playlist = playlists.get(position);
        holder.bind(playlist);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlaylistClick(playlist);
            }
        });
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final ImageView coverImage;
        private final TextView titleText;
        private final TextView artistText;

        public PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            coverImage = itemView.findViewById(R.id.coverImageView);
            titleText = itemView.findViewById(R.id.titleText);
            artistText = itemView.findViewById(R.id.artistText);
        }

        public void bind(PlaylistItem playlist) {
            titleText.setText(playlist.getTitle());
            artistText.setText(playlist.getArtist());

            // Загрузка обложки с помощью Glide
            if (playlist.getCoverUrl() != null && !playlist.getCoverUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(playlist.getCoverUrl())
                        .placeholder(R.drawable.circle_playlist)
                        .into(coverImage);
            } else {
                coverImage.setImageResource(R.drawable.circle_playlist);
            }
        }
    }
}