package ru.lisdevs.messenger.messages.stickers;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Sticker;


public class StickerPackViewAdapter extends RecyclerView.Adapter<StickerPackViewAdapter.ViewHolder> {

    private List<Sticker> stickers = new ArrayList<>();
    private final OnStickerClickListener listener;

    public interface OnStickerClickListener {
        void onStickerClick(Sticker sticker);
    }

    public StickerPackViewAdapter(List<Sticker> stickers, OnStickerClickListener listener) {
        this.stickers = stickers != null ? stickers : new ArrayList<>();
        this.listener = listener;
    }

    public void setStickers(List<Sticker> stickers) {
        this.stickers = stickers != null ? stickers : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sticker_view, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Sticker sticker = stickers.get(position);
        holder.bind(sticker, listener);
    }

    @Override
    public int getItemCount() {
        return stickers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView stickerImage;
        private final TextView stickerEmoji;
        private final View stickerContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            stickerImage = itemView.findViewById(R.id.sticker_image);
            stickerEmoji = itemView.findViewById(R.id.sticker_emoji);
            stickerContainer = itemView.findViewById(R.id.sticker_container);
        }

        public void bind(Sticker sticker, OnStickerClickListener listener) {
            if (sticker == null) {
                showPlaceholder();
                return;
            }

            try {
                // Проверяем, является ли стикер эмодзи
                if (isEmojiSticker(sticker)) {
                    showEmojiSticker(sticker);
                } else {
                    showImageSticker(sticker);
                }
            } catch (Exception e) {
                showPlaceholder();
            }

            stickerContainer.setOnClickListener(v -> {
                if (listener != null && sticker != null) {
                    listener.onStickerClick(sticker);
                }
            });

            // Добавляем долгое нажатие для предпросмотра
            stickerContainer.setOnLongClickListener(v -> {
                showStickerPreview(sticker);
                return true;
            });
        }

        private boolean isEmojiSticker(Sticker sticker) {
            return sticker.getName() != null &&
                    (sticker.getName().startsWith("Emoji") ||
                            sticker.getImageUrl() == null ||
                            sticker.getImageUrl().isEmpty());
        }

        private void showEmojiSticker(Sticker sticker) {
            stickerImage.setVisibility(View.GONE);
            stickerEmoji.setVisibility(View.VISIBLE);

            String emoji = "😊";
            if (sticker.getName() != null && sticker.getName().length() > 6) {
                emoji = sticker.getName().substring(6).trim();
            }
            stickerEmoji.setText(emoji);
        }

        private void showImageSticker(Sticker sticker) {
            stickerImage.setVisibility(View.VISIBLE);
            stickerEmoji.setVisibility(View.GONE);

            String imageUrl = getStickerImageUrl(sticker);

            if (imageUrl != null && !imageUrl.isEmpty()) {
                // Исправляем URL если нужно
                if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                    imageUrl = "https://" + imageUrl;
                }

                RequestOptions requestOptions = new RequestOptions()
                        .placeholder(R.drawable.ic_sticker_placeholder)
                        .error(R.drawable.emoticon_outline)
                        .override(256, 256) // Больший размер для детального просмотра
                        .centerInside();

                Glide.with(itemView.getContext())
                        .load(imageUrl)
                        .apply(requestOptions)
                        .transition(DrawableTransitionOptions.withCrossFade(200))
                        .into(stickerImage);
            } else {
                showPlaceholder();
            }
        }

        private String getStickerImageUrl(Sticker sticker) {
            try {
                // Пробуем получить чистый URL
                return sticker.getImageUrl();
            } catch (Exception e) {
                // Если метод не существует, используем обычный imageUrl
                return sticker.getImageUrl();
            }
        }

        private void showPlaceholder() {
            stickerImage.setVisibility(View.VISIBLE);
            stickerEmoji.setVisibility(View.GONE);
            stickerImage.setImageResource(R.drawable.ic_sticker_placeholder);
        }

        private void showStickerPreview(Sticker sticker) {
            // TODO: Реализовать полноэкранный предпросмотр стикера
            // Можно использовать DialogFragment или новое Activity
        }
    }
}