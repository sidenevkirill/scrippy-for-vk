package ru.lisdevs.messenger.gift;


import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Gift;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;

public class GiftConfirmationDialog extends Dialog {

    private Context context;
    private Gift gift;
    private String userName;
    private OnGiftSentListener giftSentListener;
    private OkHttpClient httpClient;

    public interface OnGiftSentListener {
        void onGiftSent(Gift gift);
        void onCancel();
    }

    public GiftConfirmationDialog(@NonNull Context context, Gift gift, String userName) {
        super(context);
        this.context = context;
        this.gift = gift;
        this.userName = userName;
        this.httpClient = new OkHttpClient();
    }

    public void setOnGiftSentListener(OnGiftSentListener listener) {
        this.giftSentListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_gift_confirmation);

        setupWindow();
        initViews();
    }

    private void setupWindow() {
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void initViews() {
        ImageView giftImage = findViewById(R.id.confirmGiftImage);
        TextView giftName = findViewById(R.id.confirmGiftName);
        TextView giftPrice = findViewById(R.id.confirmGiftPrice);
        TextView recipientText = findViewById(R.id.recipientText);
        TextInputEditText messageInput = findViewById(R.id.messageInput);
        MaterialButton sendButton = findViewById(R.id.confirmSendButton);
        MaterialButton cancelButton = findViewById(R.id.confirmCancelButton);
        MaterialCardView giftCard = findViewById(R.id.giftCard);
        TextView balanceText = findViewById(R.id.balanceAfterText);

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

        // Устанавливаем данные подарка
        giftName.setText(gift.getDescription());
        recipientText.setText("Для: " + userName);

        if (gift.getIsFree()) {
            giftPrice.setText("Бесплатный подарок");
            giftPrice.setTextColor(Color.GREEN);
            balanceText.setText("Баланс после отправки: 150 монет");
        } else {
            giftPrice.setText("Стоимость: " + gift.getPrice() + " монет");
            giftPrice.setTextColor(Color.BLUE);
            int newBalance = 150 - gift.getPrice(); // Демо-баланс
            balanceText.setText("Баланс после отправки: " + newBalance + " монет");

            if (newBalance < 0) {
                balanceText.setTextColor(Color.RED);
                sendButton.setEnabled(false);
                sendButton.setText("Недостаточно монет");
            } else {
                balanceText.setTextColor(Color.GREEN);
            }
        }

        // Устанавливаем плейсхолдер для сообщения
        String defaultMessage = "Для " + userName + " с наилучшими пожеланиями!";
        messageInput.setHint(defaultMessage);

        // Обработчики кнопок
        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (message.isEmpty()) {
                message = defaultMessage;
            }
            gift.setMessage(message);
            sendGiftToUser();
        });

        cancelButton.setOnClickListener(v -> {
            if (giftSentListener != null) {
                giftSentListener.onCancel();
            }
            dismiss();
        });

        // Анимация появления
        startEntryAnimation();
    }

    private void startEntryAnimation() {
        View dialogView = findViewById(R.id.dialogContainer);
        dialogView.setAlpha(0f);
        dialogView.setScaleX(0.8f);
        dialogView.setScaleY(0.8f);

        dialogView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .start();
    }

    private void sendGiftToUser() {
        // Показываем прогресс
        MaterialButton sendButton = findViewById(R.id.confirmSendButton);
        sendButton.setText("Отправка...");
        sendButton.setEnabled(false);

        // Для демо - имитируем отправку
        if (isDemoMode()) {
            simulateGiftSending();
            return;
        }

        // Реальная отправка через VK API
        sendRealGift();
    }

    private void simulateGiftSending() {
        // Имитация задержки отправки
        new android.os.Handler().postDelayed(() -> {
            if (giftSentListener != null) {
                giftSentListener.onGiftSent(gift);
            }

            // Показываем анимацию успеха
            showSuccessAnimation();

            // Закрываем диалог через секунду
            new android.os.Handler().postDelayed(this::dismiss, 1000);
        }, 1500);
    }

    private void sendRealGift() {
        String accessToken = "YOUR_ACCESS_TOKEN"; // Получите из TokenManager

        // Формируем URL для отправки подарка
        String url = "https://api.vk.com/method/gifts.send" +
                "?access_token=" + accessToken +
                "&user_id=" + 123 +
                "&gift_id=" + gift.getId() +
                "&message=" + (gift.getMessage() != null ? gift.getMessage() : "") +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
               // requireActivity().runOnUiThread(() -> showError("Ошибка сети"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            // Подарок успешно отправлен
                          {
                                if (giftSentListener != null) {
                                    giftSentListener.onGiftSent(gift);
                                }
                                showSuccessAnimation();
                                new android.os.Handler().postDelayed(() -> dismiss(), 1000);
                            };
                        } else if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            String errorMsg = error.optString("error_msg", "Неизвестная ошибка");
                        //    requireActivity().runOnUiThread(() -> showError(errorMsg));
                        }
                    } catch (JSONException e) {
                       // requireActivity().runOnUiThread(() -> showError("Ошибка обработки ответа"));
                    }
                } else {
                  //  requireActivity().runOnUiThread(() -> showError("Ошибка сервера"));
                }
            }
        });
    }

    private void showSuccessAnimation() {
        View successView = findViewById(R.id.successAnimation);
        TextView successText = findViewById(R.id.successText);
        View dialogContent = findViewById(R.id.dialogContent);

        // Скрываем основной контент
        dialogContent.setVisibility(View.GONE);

        // Показываем анимацию успеха
        successView.setVisibility(View.VISIBLE);
        successText.setText("Подарок отправлен!");

        // Анимация появления
        successView.setAlpha(0f);
        successView.setScaleX(0.5f);
        successView.setScaleY(0.5f);

        successView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .start();
    }


    private boolean isDemoMode() {
        // В реальном приложении проверяйте, используете ли вы демо-режим
        return true; // Замените на реальную проверку
    }


}
