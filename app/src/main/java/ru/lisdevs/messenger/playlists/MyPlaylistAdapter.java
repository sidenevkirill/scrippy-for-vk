package ru.lisdevs.messenger.playlists;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import ru.lisdevs.messenger.R;

public class MyPlaylistAdapter extends RecyclerView.Adapter<MyPlaylistAdapter.ViewHolder> {

    private List<MyPlaylistFragment.PlaylistItem> data;
    private OnItemClickListener listener;

    public MyPlaylistAdapter(List<MyPlaylistFragment.PlaylistItem> data, OnItemClickListener listener) {
        this.data = data;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlists, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        MyPlaylistFragment.PlaylistItem item = data.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView coverImageView;
        TextView titleTextView;

        ViewHolder(View itemView) {
            super(itemView);
            coverImageView = itemView.findViewById(R.id.coverImageView);
            titleTextView = itemView.findViewById(R.id.titleText);
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(data.get(getAdapterPosition()));
                }
            });
        }

        void bind(MyPlaylistFragment.PlaylistItem item) {
            titleTextView.setText(item.getTitle());
            // Загружаем изображение с помощью библиотеки Picasso или Glide
            if (item.getCoverUrl() != null && !item.getCoverUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(item.getCoverUrl())
                        .placeholder(R.drawable.playlist_placeholder) // изображение-заглушка
                        .into(coverImageView);
            } else {
                coverImageView.setImageResource(R.drawable.playlist_placeholder);
            }
        }
    }

    interface OnItemClickListener {
        void onItemClick(MyPlaylistFragment.PlaylistItem item);
    }
}
