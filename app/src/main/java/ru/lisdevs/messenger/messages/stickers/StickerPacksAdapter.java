package ru.lisdevs.messenger.messages.stickers;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.squareup.picasso.Picasso;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.StickerPack;

public class StickerPacksAdapter extends RecyclerView.Adapter<StickerPacksAdapter.StickerPackViewHolder> {

    private List<StickerPack> stickerPacks;
    private OnStickerPackClickListener listener;

    public interface OnStickerPackClickListener {
        void onStickerPackClick(StickerPack pack);
    }

    public StickerPacksAdapter(List<StickerPack> stickerPacks, OnStickerPackClickListener listener) {
        this.stickerPacks = stickerPacks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StickerPackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sticker_pack, parent, false);
        return new StickerPackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StickerPackViewHolder holder, int position) {
        StickerPack pack = stickerPacks.get(position);
        holder.bind(pack);
    }

    @Override
    public int getItemCount() {
        return stickerPacks != null ? stickerPacks.size() : 0;
    }

    public void setStickerPacks(List<StickerPack> stickerPacks) {
        this.stickerPacks = stickerPacks;
        notifyDataSetChanged();
    }

    class StickerPackViewHolder extends RecyclerView.ViewHolder {
        ImageView packIcon;
        TextView packTitle;

        StickerPackViewHolder(@NonNull View itemView) {
            super(itemView);
            packIcon = itemView.findViewById(R.id.packIcon);
            packTitle = itemView.findViewById(R.id.packTitle);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onStickerPackClick(stickerPacks.get(position));
                }
            });
        }

        void bind(StickerPack pack) {
            packTitle.setText(pack.getTitle());

            if (pack.getIconUrl() != null && !pack.getIconUrl().isEmpty()) {
                Picasso.get()
                        .load(pack.getIconUrl())
                        .placeholder(R.drawable.ic_sticker_pack_placeholder)
                        .error(R.drawable.ic_sticker_pack_placeholder)
                        .into(packIcon);
            } else {
                packIcon.setImageResource(R.drawable.ic_sticker_pack_placeholder);
            }
        }
    }
}