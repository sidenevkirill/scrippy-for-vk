package ru.lisdevs.messenger.messages.stickers;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Sticker;
import ru.lisdevs.messenger.model.StickerPack;
import ru.lisdevs.messenger.utils.StickerManager;

public class StickerStoreActivity extends AppCompatActivity {

    private static final String TAG = "StickerStoreActivity";

    // UI элементы
    private RecyclerView recyclerView;
    private StickerStoreAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyState;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Данные
    private List<StickerPack> availablePacks = new ArrayList<>();
    private List<StickerPack> purchasedPacks = new ArrayList<>();
    private StickerManager stickerManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_store);

        initViews();
        setupToolbar();
        setupRecyclerView();
        loadStickerPacks();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
      //  emptyState = findViewById(R.id.emptyState);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        stickerManager = StickerManager.getInstance(this);

        // Настройка SwipeRefresh
        swipeRefreshLayout.setOnRefreshListener(this::loadStickerPacks);
        swipeRefreshLayout.setColorSchemeColors(
                getResources().getColor(R.color.color_primary),
                getResources().getColor(R.color.color_accent)
        );
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Магазин стикеров");
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupRecyclerView() {
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        recyclerView.setLayoutManager(layoutManager);

        // Добавляем отступы между элементами
        int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.grid_spacing);
       // recyclerView.addItemDecoration(new GridSpacingItemDecoration(2, spacingInPixels, true));

        adapter = new StickerStoreAdapter(availablePacks, this::onStickerPackClick);
        recyclerView.setAdapter(adapter);
    }

    private void loadStickerPacks() {
        progressBar.setVisibility(View.VISIBLE);
        //emptyState.setVisibility(View.GONE);

        // Загружаем доступные стикерпаки
        loadAvailableStickerPacks();

        // Загружаем купленные стикерпаки для сравнения
        purchasedPacks = stickerManager.getPurchasedStickerPacks();
    }

    private void loadAvailableStickerPacks() {
        // Здесь должен быть API вызов для загрузки доступных стикерпаков
        // Временно используем mock данные

        new Thread(() -> {
            try {
                // Имитация загрузки из сети
                Thread.sleep(1000);

                List<StickerPack> mockPacks = createMockStickerPacks();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);

                    availablePacks.clear();
                    availablePacks.addAll(mockPacks);

                    // Помечаем купленные пакеты
                    markPurchasedPacks();

                    adapter.notifyDataSetChanged();
                    updateEmptyState();

                    Log.d(TAG, "Loaded " + availablePacks.size() + " sticker packs");
                });

            } catch (InterruptedException e) {
                Log.e(TAG, "Error loading sticker packs", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    showError("Ошибка загрузки стикеров");
                });
            }
        }).start();
    }

    private List<StickerPack> createMockStickerPacks() {
        List<StickerPack> packs = new ArrayList<>();

        // Пакет 1 - Базовые эмодзи
        StickerPack pack1 = new StickerPack();
        pack1.setId(1);
        pack1.setTitle("Базовые эмодзи");
        pack1.setStickers(createBasicStickers());
        pack1.setPrice(0); // Бесплатный
        pack1.setPreviewUrl("https://vk.com/images/stickers/preview/1.png");
        packs.add(pack1);

        // Пакет 2 - Мемы
        StickerPack pack2 = new StickerPack();
        pack2.setId(2);
        pack2.setTitle("Популярные мемы");
        pack2.setStickers(createMemeStickers());
        pack2.setPrice(49);
        pack2.setPreviewUrl("https://vk.com/images/stickers/preview/2.png");
        packs.add(pack2);

        // Пакет 3 - Котики
        StickerPack pack3 = new StickerPack();
        pack3.setId(3);
        pack3.setTitle("Милые котики");
        pack3.setStickers(createCatStickers());
        pack3.setPrice(79);
        pack3.setPreviewUrl("https://vk.com/images/stickers/preview/3.png");
        packs.add(pack3);

        // Пакет 4 - Игры
        StickerPack pack4 = new StickerPack();
        pack4.setId(4);
        pack4.setTitle("Геймерские");
        pack4.setStickers(createGameStickers());
        pack4.setPrice(99);
        pack4.setPreviewUrl("https://vk.com/images/stickers/preview/4.png");
        packs.add(pack4);

        return packs;
    }

    private List<Sticker> createBasicStickers() {
        List<Sticker> stickers = new ArrayList<>();
        String[] emojis = {"😊", "😂", "❤️", "👍", "😍", "🔥", "🎉", "🙏"};

        for (int i = 0; i < emojis.length; i++) {
            Sticker sticker = new Sticker();
            sticker.setId(100 + i);
            sticker.setName("Emoji " + emojis[i]);
            sticker.setImageUrl("");
            sticker.setWidth(128);
            sticker.setHeight(128);
            stickers.add(sticker);
        }
        return stickers;
    }

    private List<Sticker> createMemeStickers() {
        List<Sticker> stickers = new ArrayList<>();
        String[] memeNames = {"Facepalm", "LOL", "Seriously", "Mind Blown", "Troll", "Shrug"};

        for (int i = 0; i < memeNames.length; i++) {
            Sticker sticker = new Sticker();
            sticker.setId(200 + i);
            sticker.setName(memeNames[i]);
            sticker.setImageUrl("https://example.com/stickers/meme_" + (i + 1) + ".png");
            sticker.setWidth(256);
            sticker.setHeight(256);
            stickers.add(sticker);
        }
        return stickers;
    }

    private List<Sticker> createCatStickers() {
        List<Sticker> stickers = new ArrayList<>();
        String[] catNames = {"Sleepy Cat", "Happy Cat", "Angry Cat", "Curious Cat", "Playful Cat"};

        for (int i = 0; i < catNames.length; i++) {
            Sticker sticker = new Sticker();
            sticker.setId(300 + i);
            sticker.setName(catNames[i]);
            sticker.setImageUrl("https://example.com/stickers/cat_" + (i + 1) + ".png");
            sticker.setWidth(256);
            sticker.setHeight(256);
            stickers.add(sticker);
        }
        return stickers;
    }

    private List<Sticker> createGameStickers() {
        List<Sticker> stickers = new ArrayList<>();
        String[] gameNames = {"Victory", "Defeat", "GG", "Play Again", "Noob", "Pro"};

        for (int i = 0; i < gameNames.length; i++) {
            Sticker sticker = new Sticker();
            sticker.setId(400 + i);
            sticker.setName(gameNames[i]);
            sticker.setImageUrl("https://example.com/stickers/game_" + (i + 1) + ".png");
            sticker.setWidth(256);
            sticker.setHeight(256);
            stickers.add(sticker);
        }
        return stickers;
    }

    private void markPurchasedPacks() {
        for (StickerPack pack : availablePacks) {
            boolean isPurchased = false;
            for (StickerPack purchased : purchasedPacks) {
                if (purchased.getId() == pack.getId()) {
                    isPurchased = true;
                    break;
                }
            }
            pack.setPurchased(isPurchased);
        }
    }

    private void onStickerPackClick(StickerPack pack) {
        if (pack.isPurchased()) {
            showAlreadyPurchasedDialog(pack);
        } else {
            showPurchaseDialog(pack);
        }
    }

    private void showPurchaseDialog(StickerPack pack) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Покупка стикерпака");
        builder.setMessage("Вы хотите приобрести стикерпак \"" + pack.getTitle() + "\" за " + pack.getPrice() + " ₽?");

        builder.setPositiveButton("Купить", (dialog, which) -> {
            purchaseStickerPack(pack);
        });

        builder.setNegativeButton("Отмена", null);

        // Показать предпросмотр стикеров
        builder.setNeutralButton("Предпросмотр", (dialog, which) -> {
            showStickerPackPreview(pack);
        });

        builder.show();
    }

    private void showAlreadyPurchasedDialog(StickerPack pack) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Стикерпак уже куплен");
        builder.setMessage("Стикерпак \"" + pack.getTitle() + "\" уже находится в вашей коллекции.");

        builder.setPositiveButton("OK", null);
        builder.setNeutralButton("Предпросмотр", (dialog, which) -> {
            showStickerPackPreview(pack);
        });

        builder.show();
    }

    private void showStickerPackPreview(StickerPack pack) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Предпросмотр: " + pack.getTitle());

        // Создаем GridView для показа стикеров
        GridView gridView = new GridView(this);
        gridView.setNumColumns(4);
        gridView.setPadding(16, 16, 16, 16);

        StickerPreviewAdapter previewAdapter = new StickerPreviewAdapter(pack.getStickers());
        gridView.setAdapter(previewAdapter);

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            // Можно добавить увеличение стикера при клике
        });

        builder.setView(gridView);
        builder.setPositiveButton("Закрыть", null);

        if (!pack.isPurchased()) {
            builder.setNegativeButton("Купить за " + pack.getPrice() + " ₽", (dialog, which) -> {
                purchaseStickerPack(pack);
            });
        }

        AlertDialog dialog = builder.create();

        // Устанавливаем фиксированную высоту для диалога
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(metrics);
                int maxHeight = (int) (metrics.heightPixels * 0.7);
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
            }
        });

        dialog.show();
    }

    private void purchaseStickerPack(StickerPack pack) {
        progressBar.setVisibility(View.VISIBLE);

        // Имитация процесса покупки
        new Thread(() -> {
            try {
                Thread.sleep(1500); // Имитация сетевого запроса

                // В реальном приложении здесь должен быть API вызов для покупки
                boolean purchaseSuccess = simulatePurchase(pack);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (purchaseSuccess) {
                        // Обновляем статус пакета
                        pack.setPurchased(true);
                        purchasedPacks.add(pack);

                        // Сохраняем в SharedPreferences или БД
                        savePurchasedPack(pack);

                        // Обновляем UI
                        adapter.notifyDataSetChanged();

                        showSuccessDialog(pack);
                    } else {
                        showError("Ошибка при покупке. Попробуйте позже.");
                    }
                });

            } catch (InterruptedException e) {
                Log.e(TAG, "Purchase interrupted", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showError("Ошибка при покупке");
                });
            }
        }).start();
    }

    private boolean simulatePurchase(StickerPack pack) {
        // В реальном приложении здесь должна быть логика обработки платежа
        // Сейчас просто возвращаем true для успешной покупки
        return true;
    }

    private void savePurchasedPack(StickerPack pack) {
        // Сохраняем информацию о покупке в SharedPreferences
        SharedPreferences prefs = getSharedPreferences("purchased_packs", MODE_PRIVATE);
        Set<String> purchasedSet = prefs.getStringSet("purchased_ids", new HashSet<>());

        Set<String> newSet = new HashSet<>(purchasedSet);
        newSet.add(String.valueOf(pack.getId()));

        prefs.edit().putStringSet("purchased_ids", newSet).apply();

        Log.d(TAG, "Saved purchased pack: " + pack.getId());
    }

    private void showSuccessDialog(StickerPack pack) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Покупка успешна!");
        builder.setMessage("Стикерпак \"" + pack.getTitle() + "\" добавлен в вашу коллекцию.");

        builder.setPositiveButton("Отлично", (dialog, which) -> {
            // Возвращаем результат в DialogActivity
            setResult(RESULT_OK);
        });

        builder.show();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void updateEmptyState() {
        if (availablePacks.isEmpty()) {
            //emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
           // emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    // Adapter для предпросмотра стикеров
    private static class StickerPreviewAdapter extends BaseAdapter {
        private List<Sticker> stickers;

        public StickerPreviewAdapter(List<Sticker> stickers) {
            this.stickers = stickers != null ? stickers : new ArrayList<>();
        }

        @Override
        public int getCount() {
            return stickers.size();
        }

        @Override
        public Sticker getItem(int position) {
            return stickers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;

            if (convertView == null) {
                imageView = new ImageView(parent.getContext());
                int size = (int) (60 * parent.getContext().getResources().getDisplayMetrics().density);
                imageView.setLayoutParams(new GridView.LayoutParams(size, size));
                imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                imageView.setPadding(8, 8, 8, 8);
            } else {
                imageView = (ImageView) convertView;
            }

            Sticker sticker = getItem(position);

            // Загружаем изображение стикера
            if (sticker.getImageUrl() != null && !sticker.getImageUrl().isEmpty()) {
                Picasso.get()
                        .load(sticker.getImageUrl())
                        .placeholder(R.drawable.ic_sticker_placeholder)
                        .error(R.drawable.ic_sticker_placeholder)
                        .resize(64, 64)
                        .centerInside()
                        .into(imageView);
            } else {
                imageView.setImageResource(R.drawable.ic_sticker_placeholder);
            }

            return imageView;
        }
    }
}
