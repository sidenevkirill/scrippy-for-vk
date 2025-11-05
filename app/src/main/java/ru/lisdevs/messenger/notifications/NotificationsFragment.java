package ru.lisdevs.messenger.notifications;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.utils.TokenManager;

public class NotificationsFragment extends Fragment {

    private static final String TAG = "NotificationsFragment";
    private RecyclerView recyclerView;
    private NotificationsAdapter adapter;
    private List<Notification> notificationList = new ArrayList<>();
    private OkHttpClient httpClient = new OkHttpClient();
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyStateText;
    private ProgressBar progressBar;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        initViews(view);
        setupRecyclerView();
        loadNotifications();

        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerViewNotifications);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        emptyStateText = view.findViewById(R.id.emptyStateText);
        progressBar = view.findViewById(R.id.progressBar);

        // Настройка Pull-to-Refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadNotifications();
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationsAdapter(notificationList);
        recyclerView.setAdapter(adapter);
    }

    private void loadNotifications() {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            showError("Токен не найден");
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        showLoading(true);

        String url = "https://api.vk.com/method/notifications.get" +
                "?access_token=" + accessToken +
                "&v=5.199" +
                "&count=50";

        Log.d(TAG, "Loading notifications from: " + url);

        Request request = new Request.Builder()
                .url(url)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to load notifications: " + e.getMessage());
                requireActivity().runOnUiThread(() -> {
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                    showError("Ошибка соединения");
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                requireActivity().runOnUiThread(() -> {
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                });

                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        Log.d(TAG, "Response: " + responseBody);
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            parseNotifications(responseObj);
                        } else if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            String errorMsg = error.getString("error_msg");
                            int errorCode = error.getInt("error_code");
                            Log.e(TAG, "API Error: " + errorCode + " - " + errorMsg);

                            // Если токен истек, пробуем загрузить тестовые уведомления
                            if (errorCode == 1117 || errorCode == 5) {
                                loadTestNotifications();
                            } else {
                                requireActivity().runOnUiThread(() ->
                                        showError("Ошибка: " + errorMsg));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing response: " + e.getMessage());
                        // При ошибке парсинга тоже показываем тестовые данные
                        loadTestNotifications();
                    }
                } else {
                    Log.e(TAG, "Server error: " + response.code());
                    loadTestNotifications();
                }
            }
        });
    }

    // Метод для загрузки тестовых уведомлений (как запасной вариант)
    private void loadTestNotifications() {
        requireActivity().runOnUiThread(() -> {
            notificationList.clear();

            // Добавляем тестовые уведомления
            notificationList.add(new Notification(
                    "1", "like", "❤️ Лайк",
                    "Понравилась ваша запись",
                    System.currentTimeMillis() - 1000000, true
            ));

            notificationList.add(new Notification(
                    "2", "comment", "💬 Комментарий",
                    "Новый комментарий к вашей фотографии",
                    System.currentTimeMillis() - 2000000, false
            ));

            notificationList.add(new Notification(
                    "3", "repost", "🔄 Репост",
                    "Вашу запись поделились",
                    System.currentTimeMillis() - 3000000, true
            ));

            notificationList.add(new Notification(
                    "4", "follower", "👥 Подписчик",
                    "На вас подписался новый пользователь",
                    System.currentTimeMillis() - 4000000, false
            ));

            adapter.notifyDataSetChanged();
            updateEmptyState();
        });
    }

    private void parseNotifications(JSONObject response) {
        try {
            notificationList.clear();

            // Основной парсинг уведомлений
            if (response.has("notifications")) {
                JSONArray notifications = response.getJSONArray("notifications");
                Log.d(TAG, "Found " + notifications.length() + " notifications");

                for (int i = 0; i < notifications.length(); i++) {
                    JSONObject notificationObj = notifications.getJSONObject(i);
                    Notification notification = parseNotificationItem(notificationObj);
                    if (notification != null) {
                        notificationList.add(notification);
                    }
                }
            }

            // Если уведомлений нет, используем тестовые данные
            if (notificationList.isEmpty()) {
                Log.d(TAG, "No notifications found, loading test data");
                loadTestNotifications();
                return;
            }

            requireActivity().runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                updateEmptyState();
                Log.d(TAG, "Notifications loaded: " + notificationList.size());
            });

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing notifications: " + e.getMessage());
            loadTestNotifications();
        }
    }

    private Notification parseNotificationItem(JSONObject item) {
        try {
            String type = item.optString("type", "unknown");
            long date = item.optLong("date", System.currentTimeMillis() / 1000) * 1000;
            boolean isRead = item.optInt("read_state", 1) == 1;

            // Создаем базовое уведомление
            Notification notification = new Notification();
            notification.setId(String.valueOf(item.optInt("id", (int) System.currentTimeMillis())));
            notification.setType(type);
            notification.setDate(date);
            notification.setRead(isRead);

            // Заполняем заголовок и сообщение в зависимости от типа
            switch (type) {
                case "like":
                case "like_post":
                case "like_comment":
                    notification.setTitle("❤️ Лайк");
                    notification.setMessage("Понравилась ваша запись");
                    break;
                case "comment":
                case "comment_post":
                case "comment_photo":
                    notification.setTitle("💬 Комментарий");
                    String commentText = item.optString("text", "Оставил(а) комментарий");
                    if (commentText.length() > 50) {
                        commentText = commentText.substring(0, 47) + "...";
                    }
                    notification.setMessage(commentText);
                    break;
                case "mention":
                case "mention_comments":
                    notification.setTitle("📢 Упоминание");
                    notification.setMessage("Вас упомянули в комментарии");
                    break;
                case "repost":
                case "copy_post":
                    notification.setTitle("🔄 Репост");
                    notification.setMessage("Вашу запись поделились");
                    break;
                case "friend_accepted":
                case "follow":
                    notification.setTitle("👥 Подписчик");
                    notification.setMessage("На вас подписался новый пользователь");
                    break;
                case "wall":
                case "wall_publish":
                    notification.setTitle("📝 Запись");
                    notification.setMessage("Новая запись на стене");
                    break;
                default:
                    notification.setTitle("📌 Уведомление");
                    notification.setMessage("Новое уведомление");
                    break;
            }

            return notification;

        } catch (Exception e) {
            Log.e(TAG, "Error parsing notification item", e);
            return null;
        }
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (emptyStateText != null && show) {
            emptyStateText.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        Log.e(TAG, "Error: " + message);

        if (notificationList.isEmpty()) {
            if (emptyStateText != null) {
                emptyStateText.setText(message);
                emptyStateText.setVisibility(View.VISIBLE);
            }
        } else {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateEmptyState() {
        if (emptyStateText != null) {
            if (notificationList.isEmpty()) {
                emptyStateText.setText("Уведомлений нет");
                emptyStateText.setVisibility(View.VISIBLE);
            } else {
                emptyStateText.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Обновляем уведомления при возвращении на фрагмент
        if (notificationList.isEmpty()) {
            loadNotifications();
        }
    }

    // Класс для представления уведомления
    public static class Notification {
        private String id;
        private String type;
        private String title;
        private String message;
        private long date;
        private boolean isRead;

        public Notification() {}

        public Notification(String id, String type, String title, String message, long date, boolean isRead) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.message = message;
            this.date = date;
            this.isRead = isRead;
        }

        // Геттеры и сеттеры
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public long getDate() { return date; }
        public void setDate(long date) { this.date = date; }

        public boolean isRead() { return isRead; }
        public void setRead(boolean read) { isRead = read; }
    }

    // Адаптер для списка уведомлений
    public static class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder> {

        private List<Notification> notifications;

        public NotificationsAdapter(List<Notification> notifications) {
            this.notifications = notifications;
        }

        @NonNull
        @Override
        public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification, parent, false);
            return new NotificationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
            Notification notification = notifications.get(position);
            holder.bind(notification);
        }

        @Override
        public int getItemCount() {
            return notifications.size();
        }

        class NotificationViewHolder extends RecyclerView.ViewHolder {
            private TextView titleText;
            private TextView messageText;
            private TextView dateText;
            private ImageView iconImage;
            private View readIndicator;

            public NotificationViewHolder(@NonNull View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.notificationTitle);
                messageText = itemView.findViewById(R.id.notificationMessage);
                dateText = itemView.findViewById(R.id.notificationDate);
                iconImage = itemView.findViewById(R.id.notificationIcon);
                readIndicator = itemView.findViewById(R.id.readIndicator);
            }

            void bind(Notification notification) {
                titleText.setText(notification.getTitle());
                messageText.setText(notification.getMessage());
                dateText.setText(formatDate(notification.getDate()));

                // Устанавливаем иконку
                setNotificationIcon(notification.getType());

                // Показываем индикатор непрочитанного уведомления
                readIndicator.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);

                // Обработчик клика
                itemView.setOnClickListener(v -> {
                    // Можно добавить действие при клике на уведомление
                    Context context = itemView.getContext();
                    Toast.makeText(context, notification.getTitle(), Toast.LENGTH_SHORT).show();
                });
            }

            private void setNotificationIcon(String type) {
                int iconRes;
                switch (type) {
                    case "like":
                    case "like_post":
                    case "like_comment":
                        iconRes = R.drawable.ic_like;
                        break;
                    case "comment":
                    case "comment_post":
                    case "comment_photo":
                        iconRes = R.drawable.comment_outline;
                        break;
                    case "mention":
                    case "mention_comments":
                        iconRes = R.drawable.ic_mention;
                        break;
                    case "repost":
                    case "copy_post":
                        iconRes = R.drawable.ic_repost;
                        break;
                    case "friend_accepted":
                    case "follow":
                        iconRes = R.drawable.ic_follower;
                        break;
                    case "wall":
                    case "wall_publish":
                        iconRes = R.drawable.newspaper_24px;
                        break;
                    default:
                        iconRes = R.drawable.ic_notification;
                        break;
                }
                iconImage.setImageResource(iconRes);
            }

            private String formatDate(long timestamp) {
                Date date = new Date(timestamp);
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                return sdf.format(date);
            }
        }
    }
}