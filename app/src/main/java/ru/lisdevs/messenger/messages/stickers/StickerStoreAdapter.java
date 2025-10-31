package ru.lisdevs.messenger.messages.stickers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.StickerPack;

public class StickerStoreAdapter extends RecyclerView.Adapter<StickerStoreAdapter.StickerPackViewHolder> {

    private List<StickerPack> stickerPacks;
    private OnStickerPackClickListener listener;

    public interface OnStickerPackClickListener {
        void onStickerPackClick(StickerPack pack);
    }

    public StickerStoreAdapter(List<StickerPack> stickerPacks, OnStickerPackClickListener listener) {
        this.stickerPacks = stickerPacks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StickerPackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sticker_store_pack, parent, false);
        return new StickerPackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StickerPackViewHolder holder, int position) {
        StickerPack pack = stickerPacks.get(position);
        holder.bind(pack);
    }

    @Override
    public int getItemCount() {
        return stickerPacks.size();
    }

    class StickerPackViewHolder extends RecyclerView.ViewHolder {
        private ImageView previewImage;
        private TextView titleText;
        private TextView priceText;
        private TextView purchasedBadge;
        private ProgressBar imageProgress;

        public StickerPackViewHolder(@NonNull View itemView) {
            super(itemView);
            previewImage = itemView.findViewById(R.id.previewImage);
            titleText = itemView.findViewById(R.id.titleText);
            priceText = itemView.findViewById(R.id.priceText);
            purchasedBadge = itemView.findViewById(R.id.purchasedBadge);
            imageProgress = itemView.findViewById(R.id.imageProgress);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onStickerPackClick(stickerPacks.get(position));
                }
            });
        }

        void bind(StickerPack pack) {
            titleText.setText(pack.getTitle());

            if (pack.isPurchased()) {
                priceText.setVisibility(View.GONE);
                purchasedBadge.setVisibility(View.VISIBLE);
            } else {
                purchasedBadge.setVisibility(View.GONE);
                priceText.setVisibility(View.VISIBLE);
                if (pack.getPrice() == 0) {
                    priceText.setText("Бесплатно");
                    priceText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.color_accent));
                } else {
                    priceText.setText(pack.getPrice() + " ₽");
                    priceText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.color_accent));
                }
            }

            // Загрузка превью изображения
            if (pack.getPreviewUrl() != null && !pack.getPreviewUrl().isEmpty()) {
                imageProgress.setVisibility(View.VISIBLE);
                Picasso.get()
                        .load(pack.getPreviewUrl())
                        .placeholder(R.drawable.ic_sticker_pack_placeholder)
                        .error(R.drawable.ic_sticker_pack_placeholder)
                        .into(previewImage, new Callback() {
                            @Override
                            public void onSuccess() {
                                imageProgress.setVisibility(View.GONE);
                            }

                            @Override
                            public void onError(Exception e) {
                                imageProgress.setVisibility(View.GONE);
                            }
                        });
            } else {
                previewImage.setImageResource(R.drawable.ic_sticker_pack_placeholder);
                imageProgress.setVisibility(View.GONE);
            }
        }
    }
}