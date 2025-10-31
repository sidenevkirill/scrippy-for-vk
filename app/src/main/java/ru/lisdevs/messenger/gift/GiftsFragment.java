package ru.lisdevs.messenger.gift;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Gift;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.utils.TokenManager;

public class GiftsFragment extends Fragment {

    private RecyclerView recyclerViewGifts;
    private ProgressBar progressBar;
    private TextView emptyView;
    private MaterialButton sendGiftButton;
    private TextView giftsCountText;

    private List<Gift> giftList = new ArrayList<>();
    private GiftsAdapter giftsAdapter;
    private OkHttpClient httpClient;

    private long userId;
    private String userName;

    public static GiftsFragment newInstance(long userId, String userName) {
        GiftsFragment fragment = new GiftsFragment();
        Bundle args = new Bundle();
        args.putLong("user_id", userId);
        args.putString("user_name", userName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        httpClient = new OkHttpClient();

        Bundle args = getArguments();
        if (args != null) {
            userId = args.getLong("user_id");
            userName = args.getString("user_name");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gifts, container, false);

        initViews(view);
        setupRecyclerView();
        loadGifts();

        return view;
    }

    private void initViews(View view) {
        recyclerViewGifts = view.findViewById(R.id.recyclerViewGifts);
        progressBar = view.findViewById(R.id.progressBar);
        emptyView = view.findViewById(R.id.emptyView);
        sendGiftButton = view.findViewById(R.id.sendGiftButton);
        giftsCountText = view.findViewById(R.id.giftsCountText);

        sendGiftButton.setOnClickListener(v -> showGiftStore());
    }

    private void setupRecyclerView() {
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
        recyclerViewGifts.setLayoutManager(layoutManager);
        giftsAdapter = new GiftsAdapter(giftList);
        recyclerViewGifts.setAdapter(giftsAdapter);
    }

    private void loadGifts() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        // Для демо - загружаем тестовые подарки
        loadDemoGifts();

        // Для реального использования - раскомментируйте:
        //fetchRealGifts();
    }

    private void loadDemoGifts() {
        giftList.clear();

        // Демо-подарки (стикеры)
        String[] giftUrls = {
                "https://vk.ru/sticker/packs/10101/icon/square_1.5x.webp?version=2",
                "https://vk.com/sticker/1-2-128",
                "https://vk.com/sticker/1-3-128",
                "https://vk.com/sticker/1-4-128",
                "https://vk.com/sticker/1-5-128",
                "https://vk.com/sticker/1-6-128",
                "https://vk.com/sticker/1-7-128",
                "https://vk.com/sticker/1-8-128",
                "https://vk.com/sticker/1-9-128"
        };

        String[] giftNames = {
                "❤️ Сердечко", "🎂 Торт", "⭐ Звезда", "🎁 Подарок", "🏆 Кубок",
                "👑 Корона", "💎 Алмаз", "🔥 Огонь", "🌈 Радуга"
        };

        Random random = new Random();
        for (int i = 0; i < giftUrls.length; i++) {
            Gift gift = new Gift();
            gift.setId(i + 1);
            gift.setStickerId(i + 1);
            gift.setUrl(giftUrls[i]);
            gift.setDescription(giftNames[i]);
            gift.setPrice(random.nextInt(50) + 10); // Цена от 10 до 60 монет
            gift.setIsFree(i % 3 == 0); // Каждый третий бесплатный
            gift.setSenderName("Друг " + (i + 1));
            gift.setDate(System.currentTimeMillis() / 1000 - i * 86400);
            giftList.add(gift);
        }

        updateUI();
    }

    private void fetchRealGifts() {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            loadDemoGifts(); // Fallback на демо-данные
            return;
        }

        // Правильный метод API - получаем полученные пользователем подарки
        String url = "https://api.vk.com/method/gifts.get" +
                "?access_token=" + accessToken +
                "&user_id=" + userId +
                "&count=100" +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "VKAndroidApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    Log.e("GiftsFragment", "Network error: " + e.getMessage());
                    loadDemoGifts(); // Fallback на демо-данные
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        Log.d("GiftsFragment", "API Response: " + responseBody);

                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            parseGifts(responseObj);
                        } else if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            int errorCode = error.getInt("error_code");
                            String errorMsg = error.getString("error_msg");
                            Log.e("GiftsFragment", "API Error " + errorCode + ": " + errorMsg);
                            loadDemoGifts(); // Fallback на демо-данные
                        }
                    } catch (JSONException e) {
                        Log.e("GiftsFragment", "JSON parsing error: " + e.getMessage());
                        loadDemoGifts(); // Fallback на демо-данные
                    }
                } else {
                    Log.e("GiftsFragment", "HTTP error: " + response.code());
                    loadDemoGifts(); // Fallback на демо-данные
                }
            }
        });
    }

    private void parseGifts(JSONObject response) throws JSONException {
        giftList.clear();

        if (response.has("items")) {
            JSONArray items = response.getJSONArray("items");
            Log.d("GiftsFragment", "Found " + items.length() + " gifts");

            for (int i = 0; i < items.length(); i++) {
                JSONObject giftObj = items.getJSONObject(i);
                Gift gift = parseGiftItem(giftObj);
                if (gift != null) {
                    giftList.add(gift);
                }
            }
        }

        requireActivity().runOnUiThread(this::updateUI);
    }

    private Gift parseGiftItem(JSONObject giftObj) throws JSONException {
        Gift gift = new Gift();

        // Базовые поля
        if (giftObj.has("id")) {
            gift.setId(giftObj.getInt("id"));
        }

        if (giftObj.has("from_id")) {
            // gift.setSenderId(giftObj.getLong("from_id"));
        }

        if (giftObj.has("message")) {
            gift.setMessage(giftObj.getString("message"));
        }

        if (giftObj.has("date")) {
            gift.setDate(giftObj.getLong("date"));
        }

        // Информация о самом подарке (стикере)
        if (giftObj.has("gift")) {
            JSONObject giftData = giftObj.getJSONObject("gift");

            if (giftData.has("id")) {
                gift.setStickerId(giftData.getInt("id"));
            }

            if (giftData.has("description")) {
                gift.setDescription(giftData.getString("description"));
            } else {
                gift.setDescription("Подарок");
            }

            // Получаем URL изображения подарка
            String thumbUrl = getGiftImageUrl(giftData);
            gift.setUrl(thumbUrl);

            Log.d("GiftsFragment", "Parsed gift: " + gift.getDescription() + ", URL: " + thumbUrl);
        }

        // Получаем имя отправителя
        if (giftObj.has("user")) {
            JSONObject user = giftObj.getJSONObject("user");
            String firstName = user.optString("first_name", "");
            String lastName = user.optString("last_name", "");
            gift.setSenderName(firstName + " " + lastName);

        } else {
            gift.setSenderName("Аноним");
        }

        return gift;
    }

    private String getGiftImageUrl(JSONObject giftData) throws JSONException {
        // Пробуем разные варианты получения изображения
        String[] possibleFields = {"thumb_256", "thumb_128", "thumb_96", "thumb_48",
                "thumb_256_url", "thumb_128_url", "thumb_96_url", "thumb_48_url"};

        for (String field : possibleFields) {
            if (giftData.has(field)) {
                String url = giftData.optString(field);
                if (url != null && !url.isEmpty() && !url.equals("null")) {
                    return url;
                }
            }
        }

        // Если есть массив изображений
        if (giftData.has("thumbs")) {
            JSONArray thumbs = giftData.getJSONArray("thumbs");
            for (int i = 0; i < thumbs.length(); i++) {
                JSONObject thumb = thumbs.getJSONObject(i);
                if (thumb.has("url")) {
                    String url = thumb.getString("url");
                    if (url != null && !url.isEmpty()) {
                        return url;
                    }
                }
            }
        }

        // Если есть массив images
        if (giftData.has("images")) {
            JSONArray images = giftData.getJSONArray("images");
            for (int i = 0; i < images.length(); i++) {
                JSONObject image = images.getJSONObject(i);
                if (image.has("url")) {
                    String url = image.getString("url");
                    if (url != null && !url.isEmpty()) {
                        return url;
                    }
                }
            }
        }

        return null;
    }

    private void updateUI() {
        progressBar.setVisibility(View.GONE);

        if (giftList.isEmpty()) {
            emptyView.setText("Подарков пока нет");
            emptyView.setVisibility(View.VISIBLE);
            recyclerViewGifts.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerViewGifts.setVisibility(View.VISIBLE);
            giftsAdapter.notifyDataSetChanged();

            // Обновляем счетчик
            giftsCountText.setText("Подарки • " + giftList.size());
        }
    }

    private void showError(String message) {
        requireActivity().runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            emptyView.setText(message);
            emptyView.setVisibility(View.VISIBLE);
        });
    }

    private void showGiftStore() {
        GiftStoreDialog dialog = new GiftStoreDialog(requireContext(), userId, userName);
        dialog.show();
    }

    // Adapter для списка подарков
    class GiftsAdapter extends RecyclerView.Adapter<GiftsAdapter.ViewHolder> {
        private List<Gift> gifts;

        public GiftsAdapter(List<Gift> gifts) {
            this.gifts = gifts;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_gift, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Gift gift = gifts.get(position);
            holder.bind(gift);

            holder.itemView.setOnClickListener(v -> showGiftDetails(gift));
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

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                giftImage = itemView.findViewById(R.id.giftImage);
                giftName = itemView.findViewById(R.id.giftName);
                giftPrice = itemView.findViewById(R.id.giftPrice);
                cardView = itemView.findViewById(R.id.cardView);
            }

            public void bind(Gift gift) {
                // Загружаем изображение подарка
                if (gift.getUrl() != null && !gift.getUrl().isEmpty()) {
                    Glide.with(requireContext())
                            .load(gift.getUrl())
                            .placeholder(R.drawable.gift_placeholder)
                            .error(R.drawable.gift_placeholder)
                            .into(giftImage);
                } else {
                    giftImage.setImageResource(R.drawable.gift_placeholder);
                }

                giftName.setText(gift.getDescription());

                if (gift.getIsFree()) {
                    giftPrice.setText("Бесплатно");
                    giftPrice.setTextColor(Color.GREEN);
                } else {
                    giftPrice.setText(gift.getPrice() + " монет");
                    giftPrice.setTextColor(Color.BLUE);
                }

                // Добавляем красивый градиентный фон
                GradientDrawable gradient = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{getRandomColor(), getRandomColor()}
                );
                gradient.setCornerRadius(16f);
                cardView.setCardBackgroundColor(Color.TRANSPARENT);
                cardView.setBackground(gradient);
            }

            private int getRandomColor() {
                Random random = new Random();
                return Color.argb(30, random.nextInt(256), random.nextInt(256), random.nextInt(256));
            }
        }
    }

    private void showGiftDetails(Gift gift) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.MaterialAlertDialog_Rounded);

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_gift_details_card, null);

        ImageView giftImage = dialogView.findViewById(R.id.dialogGiftImage);
        TextView giftName = dialogView.findViewById(R.id.dialogGiftName);
        TextView giftPrice = dialogView.findViewById(R.id.dialogGiftPrice);
        MaterialButton sendButton = dialogView.findViewById(R.id.sendGiftButton);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);

        // Загружаем изображение
        if (gift.getUrl() != null && !gift.getUrl().isEmpty()) {
            Glide.with(requireContext())
                    .load(gift.getUrl())
                    .placeholder(R.drawable.gift_placeholder)
                    .error(R.drawable.gift_placeholder)
                    .into(giftImage);
        }

        giftName.setText(gift.getDescription());

        if (gift.getIsFree()) {
            giftPrice.setText("Бесплатный подарок");
            giftPrice.setTextColor(Color.GREEN);
        } else {
            giftPrice.setText("Стоимость: " + gift.getPrice() + " монет");
            giftPrice.setTextColor(Color.BLUE);
        }

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        sendButton.setOnClickListener(v -> {
            sendGift(gift);
            dialog.dismiss();
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void sendGift(Gift gift) {
        // Здесь реализация отправки подарка через VK API
        showGiftSentDialog(gift);
    }

    private void showGiftSentDialog(Gift gift) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.MaterialAlertDialog_Rounded);

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_gift_sent, null);

        ImageView giftImage = dialogView.findViewById(R.id.sentGiftImage);
        TextView messageText = dialogView.findViewById(R.id.sentMessageText);
        MaterialButton okButton = dialogView.findViewById(R.id.okButton);

        if (gift.getUrl() != null && !gift.getUrl().isEmpty()) {
            Glide.with(requireContext())
                    .load(gift.getUrl())
                    .into(giftImage);
        }

        messageText.setText("Подарок \"" + gift.getDescription() + "\" отправлен " + userName + "!");

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        okButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}