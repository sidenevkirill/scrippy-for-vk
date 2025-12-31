package ru.lisdevs.messenger.messages.stickers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Sticker;
import ru.lisdevs.messenger.model.StickerPack;
import ru.lisdevs.messenger.utils.StickerManager;

public class StickerPackManagerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private StickerPackAdapter adapter;
    private List<StickerPack> allStickerPacks = new ArrayList<>();
    private StickerManager stickerManager;
    private ProgressBar progressBar;
    private TextView emptyText;

    // Ключи для SharedPreferences
    private static final String PREFS_NAME = "sticker_packs_prefs";
    private static final String PREF_ENABLED_PACKS = "enabled_sticker_packs";
    private static final int DEFAULT_ENABLED_COUNT = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_manager);

        initViews();
        setupToolbar();
        loadStickerPacks();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerViewStickerPacks);
        progressBar = findViewById(R.id.progressBar);
        emptyText = findViewById(R.id.emptyText);

        // Настройка RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        // Добавляем разделитель между элементами
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dividerItemDecoration);

        // Инициализация менеджера стикеров
        stickerManager = StickerManager.getInstance(this);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Стикерпаки");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Кнопка "Назад" в тулбаре
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadStickerPacks() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        // Загружаем стикерпаки в фоновом потоке
        new Thread(() -> {
            // Получаем все доступные стикерпаки
            allStickerPacks.clear();
            for (int i = 1; i <= stickerManager.getStickerPackCount(); i++) {
                StickerPack pack = stickerManager.getStickerPack(i);
                if (pack != null) {
                    // Проверяем, включен ли пакет (сохраняем состояние)
                    pack.setEnabled(isStickerPackEnabled(pack.getId()));
                    allStickerPacks.add(pack);
                }
            }

            setupDefaultPackStates();

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                setupAdapter();

                if (allStickerPacks.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void setupAdapter() {
        adapter = new StickerPackAdapter(allStickerPacks, new StickerPackAdapter.OnStickerPackClickListener() {
            @Override
            public void onStickerPackClick(StickerPack pack) {
                // Показываем превью стикерпака
                showStickerPackPreview(pack);
            }

            @Override
            public void onStickerPackToggle(StickerPack pack, boolean enabled) {
                // Сохраняем состояние пакета
                saveStickerPackState(pack.getId(), enabled);
                pack.setEnabled(enabled);

                // Показываем уведомление
                String message = enabled ?
                        "Стикерпак \"" + pack.getTitle() + "\" включен" :
                        "Стикерпак \"" + pack.getTitle() + "\" отключен";
               // Toast.makeText(StickerPackManagerActivity.this, message, Toast.LENGTH_SHORT).show();

                // Обновляем список в диалоге (если он открыт)
                sendBroadcastToUpdateStickers();
            }
        });

        recyclerView.setAdapter(adapter);
    }

    private void showStickerPackPreview(StickerPack pack) {
        // Вместо диалога открываем новое активити
        StickerPackViewActivity.start(this, pack);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Проверяет, включен ли стикерпак (сохраненное состояние)
     */
    private boolean isStickerPackEnabled(int packId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Получаем JSON с включенными пакетами
        String enabledPacksJson = prefs.getString(PREF_ENABLED_PACKS, "{}");

        try {
            JSONObject jsonObject = new JSONObject(enabledPacksJson);
            // Если ключ существует, возвращаем его значение, иначе true (по умолчанию включен)
            return jsonObject.optBoolean("pack_" + packId, true);
        } catch (JSONException e) {
            Log.e("StickerPackManager", "Error reading enabled packs", e);
            return true; // По умолчанию все пакеты включены
        }
    }

    /**
     * Сохраняет состояние стикерпака
     */
    private void saveStickerPackState(int packId, boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        try {
            // Получаем текущий JSON
            String enabledPacksJson = prefs.getString(PREF_ENABLED_PACKS, "{}");
            JSONObject jsonObject = new JSONObject(enabledPacksJson);

            // Обновляем состояние пакета
            jsonObject.put("pack_" + packId, enabled);

            // Сохраняем обратно
            prefs.edit()
                    .putString(PREF_ENABLED_PACKS, jsonObject.toString())
                    .apply();

        } catch (JSONException e) {
            Log.e("StickerPackManager", "Error saving pack state", e);
        }
    }

    /**
     * Сохраняет состояния всех пакетов сразу (для enable/disable all)
     */
    private void saveAllStickerPackStates() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        try {
            JSONObject jsonObject = new JSONObject();

            // Сохраняем состояния всех пакетов
            for (StickerPack pack : allStickerPacks) {
                jsonObject.put("pack_" + pack.getId(), pack.isEnabled());
            }

            // Сохраняем JSON
            prefs.edit()
                    .putString(PREF_ENABLED_PACKS, jsonObject.toString())
                    .apply();

        } catch (JSONException e) {
            Log.e("StickerPackManager", "Error saving all pack states", e);
        }
    }

    /**
     * Получает список ID включенных пакетов
     */
    public Set<Integer> getEnabledPackIds() {
        Set<Integer> enabledPackIds = new HashSet<>();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        try {
            String enabledPacksJson = prefs.getString(PREF_ENABLED_PACKS, "{}");
            JSONObject jsonObject = new JSONObject(enabledPacksJson);

            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (jsonObject.getBoolean(key)) {
                    // Извлекаем ID из ключа "pack_1", "pack_2" и т.д.
                    if (key.startsWith("pack_")) {
                        try {
                            int packId = Integer.parseInt(key.substring(5));
                            enabledPackIds.add(packId);
                        } catch (NumberFormatException e) {
                            Log.e("StickerPackManager", "Invalid pack ID format: " + key);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e("StickerPackManager", "Error reading enabled pack IDs", e);
        }

        return enabledPackIds;
    }

    private void sendBroadcastToUpdateStickers() {
        // Отправляем broadcast для обновления стикеров в диалоге
        Intent intent = new Intent("STICKER_PACKS_UPDATED");
        sendBroadcast(intent);
    }

    private String extractEmojiFromName(String name) {
        // Извлекаем эмодзи из названия (формат: "Emoji 😊")
        if (name != null && name.length() > 6) {
            return name.substring(6).trim();
        }
        return "";
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_sticker_packs, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_enable_all) {
            enableAllPacks();
            return true;
        } else if (id == R.id.menu_disable_all) {
            disableAllPacks();
            return true;
        } else if (id == R.id.menu_sticker_store) {
            openStickerStore();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void enableAllPacks() {
        for (StickerPack pack : allStickerPacks) {
            pack.setEnabled(true);
            saveStickerPackState(pack.getId(), true);
        }
        adapter.notifyDataSetChanged();
        sendBroadcastToUpdateStickers();
        Toast.makeText(this, "Все стикерпаки включены", Toast.LENGTH_SHORT).show();
    }

    private void disableAllPacks() {
        for (StickerPack pack : allStickerPacks) {
            pack.setEnabled(false);
            saveStickerPackState(pack.getId(), false);
        }
        adapter.notifyDataSetChanged();
        sendBroadcastToUpdateStickers();
        Toast.makeText(this, "Все стикерпаки отключены", Toast.LENGTH_SHORT).show();
    }

    private void setupDefaultPackStates() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Проверяем, было ли уже инициализировано состояние пакетов
        boolean isInitialized = prefs.getBoolean("is_initialized", false);

        if (!isInitialized) {
            // Инициализируем состояния пакетов в первый запуск
            try {
                JSONObject jsonObject = new JSONObject();

                // Включаем первые DEFAULT_ENABLED_COUNT пакетов, остальные отключаем
                for (int i = 0; i < allStickerPacks.size(); i++) {
                    StickerPack pack = allStickerPacks.get(i);
                    boolean isEnabled = i < DEFAULT_ENABLED_COUNT;
                    jsonObject.put("pack_" + pack.getId(), isEnabled);
                    pack.setEnabled(isEnabled);

                    Log.d("StickerPackManager",
                            "Пакет " + pack.getId() + " (" + pack.getTitle() + "): " +
                                    (isEnabled ? "ВКЛЮЧЕН" : "ОТКЛЮЧЕН"));
                }

                // Сохраняем JSON
                prefs.edit()
                        .putString(PREF_ENABLED_PACKS, jsonObject.toString())
                        .putBoolean("is_initialized", true)
                        .apply();

                Log.d("StickerPackManager",
                        "Инициализировано по умолчанию: " + DEFAULT_ENABLED_COUNT +
                                " пакетов включено, остальные отключены");

            } catch (JSONException e) {
                Log.e("StickerPackManager", "Ошибка инициализации состояний пакетов", e);
                setAllPacksEnabled(true); // Запасной вариант: все включены
            }
        } else {
            // Загружаем сохраненные состояния
            for (StickerPack pack : allStickerPacks) {
                pack.setEnabled(isStickerPackEnabled(pack.getId()));
            }
        }
    }

    private void setAllPacksEnabled(boolean enabled) {
        for (StickerPack pack : allStickerPacks) {
            pack.setEnabled(enabled);
            saveStickerPackState(pack.getId(), enabled);
        }
    }

    private void openStickerStore() {
        Intent intent = new Intent(this, StickerStoreActivity.class);
        startActivity(intent);
    }

    // Адаптер для списка стикерпаков
    public static class StickerPackAdapter extends RecyclerView.Adapter<StickerPackAdapter.ViewHolder> {

        private List<StickerPack> stickerPacks;
        private OnStickerPackClickListener listener;

        public interface OnStickerPackClickListener {
            void onStickerPackClick(StickerPack pack);
            void onStickerPackToggle(StickerPack pack, boolean enabled);
        }

        public StickerPackAdapter(List<StickerPack> stickerPacks, OnStickerPackClickListener listener) {
            this.stickerPacks = stickerPacks;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sticker_pack_test, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StickerPack pack = stickerPacks.get(position);
            holder.bind(pack, listener);
        }

        @Override
        public int getItemCount() {
            return stickerPacks.size();
        }

        public void updateStickerPacks(List<StickerPack> newPacks) {
            this.stickerPacks = newPacks;
            notifyDataSetChanged();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            private TextView titleTextView;
            private TextView countTextView;
            private SwitchCompat enableSwitch;
            private View previewContainer;
            private ImageView previewImageView;
            private TextView previewTextView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                titleTextView = itemView.findViewById(R.id.textPackTitle);
                countTextView = itemView.findViewById(R.id.textStickerCount);
                enableSwitch = itemView.findViewById(R.id.switchEnable);
                previewContainer = itemView.findViewById(R.id.previewContainer);
                previewImageView = itemView.findViewById(R.id.imagePreview);
                previewTextView = itemView.findViewById(R.id.textPreview);
            }

            public void bind(StickerPack pack, OnStickerPackClickListener listener) {
                titleTextView.setText(pack.getTitle());

                int stickerCount = pack.getStickers() != null ? pack.getStickers().size() : 0;
                countTextView.setText(itemView.getContext().getString(R.string.tracks_count, stickerCount));

                enableSwitch.setChecked(pack.isEnabled());

                // Загружаем превью первого стикера
                loadPackPreview(pack);

                // Обработчик клика на весь элемент
                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onStickerPackClick(pack);
                    }
                });

                // Обработчик переключения Switch
                enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (listener != null) {
                        listener.onStickerPackToggle(pack, isChecked);
                    }
                });
            }

            private void loadPackPreview(StickerPack pack) {
                if (pack.getStickers() != null && !pack.getStickers().isEmpty()) {
                    Sticker firstSticker = pack.getStickers().get(0);

                    // Для эмодзи стикеров
                    if (firstSticker.getImageUrl() == null || firstSticker.getImageUrl().isEmpty() ||
                            firstSticker.getName().contains("Emoji")) {

                        previewImageView.setVisibility(View.GONE);
                        previewTextView.setVisibility(View.VISIBLE);

                        String emoji = extractEmojiFromName(firstSticker.getName());
                        previewTextView.setText(emoji.isEmpty() ? "😊" : emoji);

                    } else {
                        // Для обычных стикеров загружаем изображение
                        previewImageView.setVisibility(View.VISIBLE);
                        previewTextView.setVisibility(View.GONE);

                        Glide.with(itemView.getContext())
                                .load(firstSticker.getImageUrl())
                                .placeholder(R.drawable.ic_sticker_placeholder)
                                .error(R.drawable.ic_sticker_placeholder)
                                .override(64, 64)
                                .into(previewImageView);
                    }
                } else {
                    // Если нет стикеров, показываем заглушку
                    previewImageView.setVisibility(View.VISIBLE);
                    previewTextView.setVisibility(View.GONE);
                    previewImageView.setImageResource(R.drawable.ic_sticker_placeholder);
                }
            }

            private String extractEmojiFromName(String name) {
                if (name != null && name.length() > 6) {
                    return name.substring(6).trim();
                }
                return "";
            }
        }
    }

    // Метод для запуска активности
    public static void start(Context context) {
        Intent intent = new Intent(context, StickerPackManagerActivity.class);
        context.startActivity(intent);
    }
}