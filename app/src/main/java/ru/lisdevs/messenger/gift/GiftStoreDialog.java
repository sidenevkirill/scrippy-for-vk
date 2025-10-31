package ru.lisdevs.messenger.gift;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Gift;

public class GiftStoreDialog extends Dialog {

    private Context context;
    private long userId;
    private String userName;
    private List<Gift> availableGifts = new ArrayList<>();
    private GiftStoreAdapter adapter;
    private OnGiftSelectedListener giftSelectedListener;

    public interface OnGiftSelectedListener {
        void onGiftSelected(Gift gift);
    }

    public GiftStoreDialog(@NonNull Context context, long userId, String userName) {
        super(context);
        this.context = context;
        this.userId = userId;
        this.userName = userName;
    }

    public void setOnGiftSelectedListener(OnGiftSelectedListener listener) {
        this.giftSelectedListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_gift_store);

        setupWindow();
        initViews();
        loadAvailableGifts();
    }

    private void setupWindow() {
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void initViews() {
        RecyclerView recyclerView = findViewById(R.id.recyclerViewGiftStore);
        MaterialButton closeButton = findViewById(R.id.closeButton);
        TextView titleText = findViewById(R.id.titleText);
        TextView balanceText = findViewById(R.id.balanceText);

        // Устанавливаем заголовок
        titleText.setText("Выберите подарок для " + userName);

        // Устанавливаем баланс (можно получить из VK API)
        balanceText.setText("Ваш баланс: 150 монет");

        // Настройка RecyclerView
        GridLayoutManager layoutManager = new GridLayoutManager(context, 4);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new GiftStoreAdapter(availableGifts);
        recyclerView.setAdapter(adapter);

        // Обработчик закрытия
        closeButton.setOnClickListener(v -> dismiss());
    }

    private void loadAvailableGifts() {
        availableGifts.clear();

        // Демо-данные для витрины подарков
        String[] giftUrls = {
                "https://vk.com/sticker/1-1-128",
                "https://vk.com/sticker/1-2-128",
                "https://vk.com/sticker/1-3-128",
                "https://vk.com/sticker/1-4-128",
                "https://vk.com/sticker/1-5-128",
                "https://vk.com/sticker/1-6-128",
                "https://vk.com/sticker/1-7-128",
                "https://vk.com/sticker/1-8-128",
                "https://vk.com/sticker/1-9-128",
                "https://vk.com/sticker/1-10-128",
                "https://vk.com/sticker/1-11-128",
                "https://vk.com/sticker/1-12-128",
                "https://vk.com/sticker/1-13-128",
                "https://vk.com/sticker/1-14-128",
                "https://vk.com/sticker/1-15-128",
                "https://vk.com/sticker/1-16-128"
        };

        String[] giftNames = {
                "❤️ Сердечко", "🎂 Торт", "⭐ Звезда", "🎁 Подарок",
                "🏆 Кубок", "👑 Корона", "💎 Алмаз", "🔥 Огонь",
                "🌈 Радуга", "🎈 Шарик", "🎵 Музыка", "📚 Книга",
                "⚽ Мяч", "🎮 Геймпад", "☕ Кофе", "🎨 Краски"
        };

        int[] giftPrices = {10, 20, 15, 25, 30, 50, 40, 20, 15, 10, 25, 20, 15, 35, 10, 20};

        Random random = new Random();
        for (int i = 0; i < giftUrls.length; i++) {
            Gift gift = new Gift();
            gift.setId(i + 1000); // ID для витрины
            gift.setStickerId(i + 1);
            gift.setUrl(giftUrls[i]);
            gift.setDescription(giftNames[i]);
            gift.setPrice(giftPrices[i]);
            gift.setIsFree(i % 5 == 0); // Каждый 5-й бесплатный
            availableGifts.add(gift);
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    // Adapter для витрины подарков
    class GiftStoreAdapter extends RecyclerView.Adapter<GiftStoreAdapter.ViewHolder> {
        private List<Gift> gifts;

        public GiftStoreAdapter(List<Gift> gifts) {
            this.gifts = gifts;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = View.inflate(context, R.layout.item_gift_store, null);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Gift gift = gifts.get(position);
            holder.bind(gift);

            holder.itemView.setOnClickListener(v -> {
                if (giftSelectedListener != null) {
                    giftSelectedListener.onGiftSelected(gift);
                }
                showGiftConfirmation(gift);
            });
        }

        @Override
        public int getItemCount() {
            return gifts.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private ImageView giftImage;
            private TextView giftName;
            private TextView giftPrice;
            private MaterialCardView cardView;
            private View freeBadge;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                giftImage = itemView.findViewById(R.id.giftImage);
                giftName = itemView.findViewById(R.id.giftName);
                giftPrice = itemView.findViewById(R.id.giftPrice);
                cardView = itemView.findViewById(R.id.cardView);
                freeBadge = itemView.findViewById(R.id.freeBadge);
            }

            public void bind(Gift gift) {
                // Загружаем изображение подарка
                if (gift.getUrl() != null && !gift.getUrl().isEmpty()) {
                    Glide.with(context)
                            .load(gift.getUrl())
                            .placeholder(R.drawable.gift_placeholder)
                            .error(R.drawable.gift_placeholder)
                            .into(giftImage);
                } else {
                    giftImage.setImageResource(R.drawable.gift_placeholder);
                }

                giftName.setText(gift.getDescription());

                if (gift.getIsFree()) {
                    giftPrice.setText("БЕСПЛАТНО");
                    giftPrice.setTextColor(Color.GREEN);
                    freeBadge.setVisibility(View.VISIBLE);
                } else {
                    giftPrice.setText(gift.getPrice() + " монет");
                    giftPrice.setTextColor(Color.BLUE);
                    freeBadge.setVisibility(View.GONE);
                }

                // Красивый градиентный фон
                setupCardBackground(gift);
            }

            private void setupCardBackground(Gift gift) {
                int[] colors = getGradientColors(gift.getId());
                GradientDrawable gradient = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        colors
                );
                gradient.setCornerRadius(16f);
                gradient.setAlpha(30); // Полупрозрачный

                cardView.setCardBackgroundColor(Color.TRANSPARENT);
                cardView.setBackground(gradient);
            }

            private int[] getGradientColors(int giftId) {
                Random random = new Random(giftId);
                int color1 = Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256));
                int color2 = Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256));
                return new int[]{color1, color2};
            }
        }
    }

    private void showGiftConfirmation(Gift gift) {
        GiftConfirmationDialog confirmationDialog = new GiftConfirmationDialog(context, gift, userName);
        confirmationDialog.setOnGiftSentListener(new GiftConfirmationDialog.OnGiftSentListener() {
            @Override
            public void onGiftSent(Gift gift) {
                // Подарок отправлен - можно обновить баланс и т.д.
                if (giftSelectedListener != null) {
                    giftSelectedListener.onGiftSelected(gift);
                }
                dismiss();
            }

            @Override
            public void onCancel() {
                // Пользователь отменил отправку
            }
        });
        confirmationDialog.show();
    }
}
