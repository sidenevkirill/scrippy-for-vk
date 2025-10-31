package ru.lisdevs.messenger.messages;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.AutoResponse;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.content.ContextCompat;


import java.util.List;
import java.util.Random;

import android.widget.LinearLayout;
import android.widget.Toast;

public class AutoResponseAdapter extends RecyclerView.Adapter<AutoResponseAdapter.ResponseViewHolder> {
    private List<AutoResponse> responses;
    private OnResponseActionListener listener;
    private Random random = new Random();

    public interface OnResponseActionListener {
        void onEdit(AutoResponse response);
        void onDelete(AutoResponse response);
        void onToggleActive(AutoResponse response);
    }

    public AutoResponseAdapter(List<AutoResponse> responses, OnResponseActionListener listener) {
        this.responses = responses;
        this.listener = listener;
    }

    public void updateData(List<AutoResponse> newResponses) {
        this.responses = newResponses;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ResponseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_auto_response, parent, false);
        return new ResponseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResponseViewHolder holder, int position) {
        AutoResponse response = responses.get(position);
        holder.bind(response, listener);
    }

    @Override
    public int getItemCount() {
        return responses.size();
    }

    static class ResponseViewHolder extends RecyclerView.ViewHolder {
        private TextView tvKeyword, tvResponse, tvCategory, categoryBadge, userResponseIndicator;
        private SwitchCompat switchActive;
        private ImageButton btnEdit, btnDelete;
        private View predefinedIndicator;
        private LinearLayout actionsLayout;
        private Random random = new Random();

        // Цвета для категорий
        private final int[] categoryColors = {
                Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
                Color.parseColor("#45B7D1"), Color.parseColor("#F9A826"),
                Color.parseColor("#6A5ACD"), Color.parseColor("#FFA07A"),
                Color.parseColor("#20B2AA"), Color.parseColor("#9370DB"),
                Color.parseColor("#3CB371"), Color.parseColor("#FF4500"),
                Color.parseColor("#9C27B0"), Color.parseColor("#607D8B")
        };

        public ResponseViewHolder(@NonNull View itemView) {
            super(itemView);

            tvKeyword = itemView.findViewById(R.id.tvKeyword);
            tvResponse = itemView.findViewById(R.id.tvResponse);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            categoryBadge = itemView.findViewById(R.id.categoryBadge);
            switchActive = itemView.findViewById(R.id.switchActive);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            predefinedIndicator = itemView.findViewById(R.id.predefinedIndicator);
            actionsLayout = itemView.findViewById(R.id.actionsLayout);
            userResponseIndicator = itemView.findViewById(R.id.userResponseIndicator);
        }

        void bind(AutoResponse response, OnResponseActionListener listener) {
            // Устанавливаем текст
            tvKeyword.setText(response.getKeyword());
            tvResponse.setText(response.getResponse());
            tvCategory.setText(response.getCategory());
            categoryBadge.setText(response.getCategory());

            // Устанавливаем состояние переключателя
            switchActive.setChecked(response.isActive());

            // Настраиваем внешний вид в зависимости от типа ответа
            if (response.isPredefined()) {
                // Системный ответ
                setupPredefinedResponse(response);
            } else {
                // Пользовательский ответ
                setupUserResponse(response);
            }

            // Настраиваем цвет категории
            setupCategoryColor(response.getCategory());

            // Обработчики событий
            setupEventHandlers(response, listener);
        }

        private void setupPredefinedResponse(AutoResponse response) {
            // Показываем индикатор системного ответа
            if (predefinedIndicator != null) {
                predefinedIndicator.setVisibility(View.VISIBLE);
                predefinedIndicator.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.black));
            }

            // Скрываем кнопки действий и индикатор пользовательского ответа
            if (actionsLayout != null) {
                actionsLayout.setVisibility(View.GONE);
            }
            if (userResponseIndicator != null) {
                userResponseIndicator.setVisibility(View.GONE);
            }

            // Настраиваем внешний вид для системного ответа
            tvKeyword.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.dark_background));
        }

        private void setupUserResponse(AutoResponse response) {
            // Скрываем индикатор системного ответа
            if (predefinedIndicator != null) {
                predefinedIndicator.setVisibility(View.GONE);
            }

            // Показываем кнопки действий и индикатор пользовательского ответа
            if (actionsLayout != null) {
                actionsLayout.setVisibility(View.VISIBLE);
            }
            if (userResponseIndicator != null) {
                userResponseIndicator.setVisibility(View.VISIBLE);
            }

            // Настраиваем внешний вид для пользовательского ответа
            tvKeyword.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.black));

            // Настраиваем кнопки
            if (btnEdit != null) {
                btnEdit.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.current_track_bg));
            }
            if (btnDelete != null) {
                btnDelete.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.red));
            }
        }

        private void setupCategoryColor(String category) {
            if (categoryBadge != null) {
                // Генерируем цвет на основе названия категории
                int colorIndex = Math.abs(category.hashCode()) % categoryColors.length;
                int color = categoryColors[colorIndex];

                // Создаем фон для бейджа категории
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(16f);
                drawable.setColor(adjustColorAlpha(color, 0.2f)); // Прозрачный фон
                drawable.setStroke(2, color); // Граница цвета категории

                categoryBadge.setBackground(drawable);
                categoryBadge.setTextColor(color);
            }
        }

        private int adjustColorAlpha(int color, float alpha) {
            int alphaValue = (int) (alpha * 255);
            return (alphaValue << 24) | (color & 0x00FFFFFF);
        }

        private void setupEventHandlers(AutoResponse response, OnResponseActionListener listener) {
            // Обработчик переключателя активности
            switchActive.setOnCheckedChangeListener(null); // Сначала удаляем старый listener
            switchActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onToggleActive(response);
                }
            });

            // Обработчик кнопки редактирования (только для пользовательских ответов)
            if (btnEdit != null && !response.isPredefined()) {
                btnEdit.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onEdit(response);
                    }
                });
            }

            // Обработчик кнопки удаления (только для пользовательских ответов)
            if (btnDelete != null && !response.isPredefined()) {
                btnDelete.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onDelete(response);
                    }
                });
            }

            // Обработчик клика на всю карточку (для быстрого включения/выключения)
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    // Анимация нажатия
                    itemView.animate().scaleX(0.98f).scaleY(0.98f).setDuration(100)
                            .withEndAction(() -> itemView.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                            .start();

                    // Переключаем состояние с небольшой задержкой для лучшего UX
                    itemView.postDelayed(() -> {
                        boolean newState = !switchActive.isChecked();
                        switchActive.setChecked(newState);
                        // Вызываем listener только если состояние действительно изменилось
                        if (response.isActive() != newState) {
                            listener.onToggleActive(response);
                        }
                    }, 50);
                }
            });

            // Долгое нажатие на карточку (для системных ответов показывает информацию)
            itemView.setOnLongClickListener(v -> {
                if (response.isPredefined()) {
                    Toast.makeText(itemView.getContext(),
                            "Системный ответ. Для редактирования создайте свой вариант.",
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
        }
    }
}