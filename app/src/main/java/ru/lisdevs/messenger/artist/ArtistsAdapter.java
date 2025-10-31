package ru.lisdevs.messenger.artist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import ru.lisdevs.messenger.R;

public class ArtistsAdapter extends RecyclerView.Adapter<ArtistsAdapter.ArtistViewHolder> {

    private List<String> artists;
    private OnArtistClickListener listener;

    public interface OnArtistClickListener {
        void onArtistClick(String artist);
    }

    public ArtistsAdapter(List<String> artists, OnArtistClickListener listener) {
        this.artists = artists;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ArtistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_artist, parent, false);
        return new ArtistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArtistViewHolder holder, int position) {
        holder.bind(artists.get(position));
    }

    @Override
    public int getItemCount() {
        return artists.size();
    }

    class ArtistViewHolder extends RecyclerView.ViewHolder {
        private final TextView artistName;
        private final ImageView artistIcon;

        ArtistViewHolder(@NonNull View itemView) {
            super(itemView);
            artistName = itemView.findViewById(R.id.artist_name);
            artistIcon = itemView.findViewById(R.id.artist_icon);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onArtistClick(artists.get(position));
                }
            });
        }

        void bind(String artist) {
            artistName.setText(artist);

            // Устанавливаем разные иконки в зависимости от позиции
            int iconRes;
            switch (getAdapterPosition() % 3) {
                case 0:
                    iconRes = R.drawable.account_outline;
                    break;
                case 1:
                    iconRes = R.drawable.account_outline;
                    break;
                default:
                    iconRes = R.drawable.account_outline;
            }
            artistIcon.setImageResource(iconRes);
        }
    }
}
