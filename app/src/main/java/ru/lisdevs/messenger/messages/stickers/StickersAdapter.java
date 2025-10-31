package ru.lisdevs.messenger.messages.stickers;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Sticker;
import ru.lisdevs.messenger.model.StickerImage;


public class StickersAdapter extends RecyclerView.Adapter<StickersAdapter.StickerViewHolder> {

    private static final String TAG = "StickersAdapter";
    private List<Sticker> stickers;
    private OnStickerClickListener listener;
    private int preferredStickerSize = 128;

    public interface OnStickerClickListener {
        void onStickerClick(Sticker sticker);
    }

    public StickersAdapter(List<Sticker> stickers, OnStickerClickListener listener) {
        this.stickers = stickers;
        this.listener = listener;
        Log.d(TAG, "Adapter created with " + (stickers != null ? stickers.size() : 0) + " stickers");
    }

    @NonNull
    @Override
    public StickerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sticker, parent, false);
        return new StickerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StickerViewHolder holder, int position) {
        Sticker sticker = stickers.get(position);
        Log.d(TAG, "Binding sticker at position " + position + ": " + sticker.getName());
        holder.bind(sticker, preferredStickerSize);
    }

    @Override
    public int getItemCount() {
        int count = stickers != null ? stickers.size() : 0;
        Log.d(TAG, "Item count: " + count);
        return count;
    }

    public void setStickers(List<Sticker> stickers) {
        this.stickers = stickers;
        Log.d(TAG, "Stickers updated: " + (stickers != null ? stickers.size() : 0) + " items");
        notifyDataSetChanged();
    }

    public void setPreferredStickerSize(int size) {
        this.preferredStickerSize = size;
    }

    class StickerViewHolder extends RecyclerView.ViewHolder {
        ImageView stickerImage;
        private static final String TAG = "StickerViewHolder";

        StickerViewHolder(@NonNull View itemView) {
            super(itemView);
            stickerImage = itemView.findViewById(R.id.stickerImage);

            if (stickerImage == null) {
                Log.e(TAG, "stickerImage is NULL! Check item_sticker.xml layout");
                return;
            }

            // Убедитесь, что ImageView правильно настроен
            stickerImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            stickerImage.setAdjustViewBounds(true);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onStickerClick(stickers.get(position));
                }
            });
        }

        void bind(Sticker sticker, int preferredSize) {
            if (stickerImage == null) return;

            // Получаем чистый URL без параметров size
            String imageUrl = getCleanStickerUrl(sticker, preferredSize);
            Log.d(TAG, "Loading sticker: " + sticker.getName() + " from URL: " + imageUrl);

            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                Picasso.get()
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_sticker_placeholder)
                        .error(R.drawable.ic_sticker_placeholder)
                        .fit()
                        .centerInside()
                        .into(stickerImage, new Callback() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "✓ Sticker loaded successfully: " + sticker.getName());
                            }

                            @Override
                            public void onError(Exception e) {
                                Log.e(TAG, "✗ Error loading sticker: " + imageUrl, e);

                                // Пробуем альтернативные URL
                                loadAlternativeUrls(sticker);
                            }
                        });
            } else {
                Log.w(TAG, "Sticker image URL is null or empty for: " + sticker.getName());
                stickerImage.setImageResource(R.drawable.ic_sticker_placeholder);
            }
        }

        private String getCleanStickerUrl(Sticker sticker, int preferredSize) {
            // Сначала пробуем получить оптимальный URL
            String url = sticker.getOptimalImageUrl(preferredSize);

            // Если URL содержит параметры size - очищаем их
            if (url != null && url.contains("?size=")) {
                url = url.substring(0, url.indexOf("?size="));
            }

            // Если URL все еще пустой, пробуем основной imageUrl
            if ((url == null || url.isEmpty()) && sticker.getImageUrl() != null) {
                url = sticker.getImageUrl();
                if (url.contains("?size=")) {
                    url = url.substring(0, url.indexOf("?size="));
                }
            }

            return url;
        }

        private void loadAlternativeUrls(Sticker sticker) {
            // Пробуем разные варианты URL в порядке приоритета

            // 1. Пробуем основной imageUrl без параметров
            String cleanUrl = sticker.getOptimalImageUrl();
            if (cleanUrl != null && !cleanUrl.isEmpty() && !cleanUrl.contains("?size=")) {
                Log.d(TAG, "Trying clean URL: " + cleanUrl);
                loadUrlWithFallback(cleanUrl, sticker);
                return;
            }

            // 2. Пробуем первый image из списка images
            if (sticker.getImages() != null && !sticker.getImages().isEmpty()) {
                StickerImage firstImage = sticker.getImages().get(0);
                if (firstImage != null && firstImage.getUrl() != null) {
                    String firstImageUrl = firstImage.getUrl();
                    if (firstImageUrl.contains("?size=")) {
                        firstImageUrl = firstImageUrl.substring(0, firstImageUrl.indexOf("?size="));
                    }
                    Log.d(TAG, "Trying first image URL: " + firstImageUrl);
                    loadUrlWithFallback(firstImageUrl, sticker);
                    return;
                }
            }

            // 3. Все варианты исчерпаны - показываем placeholder
            Log.e(TAG, "All URL attempts failed for sticker: " + sticker.getName());
            stickerImage.setImageResource(R.drawable.ic_sticker_placeholder);
        }

        private void loadUrlWithFallback(String url, Sticker sticker) {
            Picasso.get()
                    .load(url)
                    .placeholder(R.drawable.ic_sticker_placeholder)
                    .error(R.drawable.ic_sticker_placeholder)
                    .fit()
                    .centerInside()
                    .into(stickerImage, new Callback() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "✓ Alternative URL worked for: " + sticker.getName());
                        }

                        @Override
                        public void onError(Exception e) {
                            Log.e(TAG, "✗ Alternative URL also failed: " + url);
                            stickerImage.setImageResource(R.drawable.ic_sticker_placeholder);
                        }
                    });
        }
    }
}