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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import ru.lisdevs.messenger.R;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    private List<Audio> trackList;
    private OnTrackClickListener listener;

    public interface OnTrackClickListener {
        void onTrackClick(int position);
        void onMoreClick(int position);
    }

    public TrackAdapter(List<Audio> trackList) {
        this.trackList = trackList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Audio track = trackList.get(position);
        holder.bind(track);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrackClick(position);
            }
        });

        holder.moreButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMoreClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return trackList.size();
    }

    public void updateData(List<Audio> newTracks) {
        trackList.clear();
        trackList.addAll(newTracks);
        notifyDataSetChanged();
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleTextView;
        private final TextView artistTextView;
        private final TextView durationTextView;
        private final ImageView coverImageView;
        private final ImageView moreButton;

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.track_title);
            artistTextView = itemView.findViewById(R.id.track_artist);
            durationTextView = itemView.findViewById(R.id.track_duration);
            coverImageView = itemView.findViewById(R.id.track_cover);
            moreButton = itemView.findViewById(R.id.track_more);
        }

        public void bind(Audio track) {
            titleTextView.setText(track.getTitle());
            artistTextView.setText(track.getArtist());

            // Форматирование длительности (секунды в MM:SS)
            String duration = String.format(Locale.getDefault(), "%02d:%02d",
                    TimeUnit.SECONDS.toMinutes(track.getDuration()),
                    TimeUnit.SECONDS.toSeconds(track.getDuration()) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(track.getDuration()))
            );
            durationTextView.setText(duration);

            // Загрузка обложки (если есть)
            if (track.getCoverUrl() != null && !track.getCoverUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(track.getCoverUrl())
                        .placeholder(R.drawable.circle_audio)
                        .into(coverImageView);
            } else {
                coverImageView.setImageResource(R.drawable.circle_audio);
            }
        }
    }
}
