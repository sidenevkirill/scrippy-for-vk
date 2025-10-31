package ru.lisdevs.messenger.gift;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.utils.TokenManager;

public class VoiceStatsFragment extends Fragment {

    private MaterialToolbar toolbar;
    private RecyclerView recyclerViewVoiceStats;
    private SwipeRefreshLayout swipeRefreshLayout;
    private VoiceStatsAdapter adapter;
    private List<VoiceStat> voiceStatsList;

    private FrameLayout progressBarContainer;
    private TextView emptyStateText;

    private TokenManager tokenManager;
    private RequestQueue requestQueue;

    public VoiceStatsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_voice_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        toolbar = view.findViewById(R.id.toolbar);
        tokenManager = TokenManager.getInstance(requireContext());
        requestQueue = Volley.newRequestQueue(requireContext());

        initViews(view);
        setupToolbar();
        setupRecyclerView();
        setupSwipeRefresh();
        loadVoiceStats();
    }

    private void initViews(View view) {
        recyclerViewVoiceStats = view.findViewById(R.id.recyclerViewVoiceStats);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        progressBarContainer = view.findViewById(R.id.progressBarContainer);
        emptyStateText = view.findViewById(R.id.emptyStateText);
    }

    private void setupToolbar() {
        toolbar.setTitle("Статистика голосов");
        if (!isAdded() || getActivity() == null) return;

        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void setupRecyclerView() {
        voiceStatsList = new ArrayList<>();
        adapter = new VoiceStatsAdapter(voiceStatsList);
        recyclerViewVoiceStats.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewVoiceStats.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadVoiceStats();
        });
    }

    private void loadVoiceStats() {
        String accessToken = tokenManager.getToken();

        if (accessToken == null) {
            Toast.makeText(requireContext(), "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            showEmptyState(true);
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        showLoading(true);

        // Загружаем список друзей
        Uri.Builder uriBuilder = Uri.parse("https://api.vk.com/method/friends.get")
                .buildUpon()
                .appendQueryParameter("access_token", accessToken)
                .appendQueryParameter("fields", "photo_100,online")
                .appendQueryParameter("count", "100")
                .appendQueryParameter("v", "5.199");

        String finalUrl = uriBuilder.build().toString();

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, finalUrl, null,
                response -> {
                    try {
                        Log.d("VoiceStatsFragment", "Friends response: " + response.toString());
                        if (response.has("error")) {
                            handleVKError(response.getJSONObject("error"));
                            return;
                        }

                        JSONObject data = response.getJSONObject("response");
                        JSONArray items = data.getJSONArray("items");

                        // Парсим друзей и загружаем статистику голосов
                        parseFriendsAndLoadStats(items);

                    } catch (JSONException e) {
                        Log.e("VoiceStatsFragment", "JSON parsing error", e);
                        handleError("Ошибка обработки данных");
                        swipeRefreshLayout.setRefreshing(false);
                        showLoading(false);
                    }
                },
                error -> {
                    Log.e("VoiceStatsFragment", "Network error", error);
                    handleError("Ошибка сети: " + error.getMessage());
                    swipeRefreshLayout.setRefreshing(false);
                    showLoading(false);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", getUserAgent());
                return headers;
            }
        };

        requestQueue.add(request);
    }

    private void parseFriendsAndLoadStats(JSONArray friends) throws JSONException {
        voiceStatsList.clear();

        // Создаем временный список для хранения базовой информации
        List<VoiceStat> tempList = new ArrayList<>();

        for (int i = 0; i < friends.length(); i++) {
            JSONObject friend = friends.getJSONObject(i);
            VoiceStat voiceStat = new VoiceStat();
            voiceStat.userId = friend.optInt("id", 0);
            voiceStat.firstName = friend.optString("first_name", "");
            voiceStat.lastName = friend.optString("last_name", "");
            voiceStat.photoUrl = friend.optString("photo_100", "");
            voiceStat.isOnline = friend.optInt("online", 0) == 1;

            // Инициализируем счетчики
            voiceStat.positiveVotes = 0;
            voiceStat.negativeVotes = 0;
            voiceStat.totalVotes = 0;

            tempList.add(voiceStat);
        }

        // Здесь можно добавить логику для загрузки реальной статистики голосов
        // Например, из локальной БД или другого API

        // Временно заполняем случайными данными для демонстрации
        fillWithDemoData(tempList);

        voiceStatsList.addAll(tempList);
        adapter.notifyDataSetChanged();
        updateUI();

        swipeRefreshLayout.setRefreshing(false);
        showLoading(false);
    }

    private void fillWithDemoData(List<VoiceStat> tempList) {
        Random random = new Random();
        for (VoiceStat stat : tempList) {
            stat.positiveVotes = random.nextInt(100);
            stat.negativeVotes = random.nextInt(50);
            stat.totalVotes = stat.positiveVotes + stat.negativeVotes;
        }
    }

    // Метод для определения User-Agent
    private String getUserAgent() {
        // Ваша реализация getUserAgent
        return "VKAndroidApp/1.0";
    }

    private void handleVKError(JSONObject error) throws JSONException {
        String errorMsg = error.getString("error_msg");
        int errorCode = error.optInt("error_code", 0);

        Log.e("VoiceStatsFragment", "VK API Error: " + errorCode + " - " + errorMsg);
        handleError("Ошибка VK: " + errorMsg);
        swipeRefreshLayout.setRefreshing(false);
        showLoading(false);
    }

    private void sortByVotes() {
        Collections.sort(voiceStatsList, (v1, v2) -> Integer.compare(v2.totalVotes, v1.totalVotes));
        adapter.notifyDataSetChanged();
        Toast.makeText(requireContext(), "Сортировка по голосам", Toast.LENGTH_SHORT).show();
    }

    private void sortByName() {
        Collections.sort(voiceStatsList, (v1, v2) -> {
            String name1 = v1.firstName + " " + v1.lastName;
            String name2 = v2.firstName + " " + v2.lastName;
            return name1.compareToIgnoreCase(name2);
        });
        adapter.notifyDataSetChanged();
        Toast.makeText(requireContext(), "Сортировка по имени", Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        if (voiceStatsList.isEmpty()) {
            showEmptyState(true);
        } else {
            showEmptyState(false);
        }
    }

    private void handleError(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        showEmptyState(true);
    }

    private void showLoading(boolean show) {
        if (progressBarContainer != null) {
            progressBarContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyStateText != null) {
            emptyStateText.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                emptyStateText.setText("Нет данных о голосах");
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (requestQueue != null) {
            requestQueue.cancelAll(this);
        }
    }

    // Класс для хранения статистики голосов
    public static class VoiceStat {
        public long userId;
        public String firstName;
        public String lastName;
        public String photoUrl;
        public boolean isOnline;
        public int positiveVotes;
        public int negativeVotes;
        public int totalVotes;
    }

    // Адаптер для отображения статистики голосов
    static class VoiceStatsAdapter extends RecyclerView.Adapter<VoiceStatsAdapter.ViewHolder> {

        private List<VoiceStat> voiceStatsList;

        VoiceStatsAdapter(List<VoiceStat> voiceStatsList) {
            this.voiceStatsList = voiceStatsList;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_voice_stat, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VoiceStat stat = voiceStatsList.get(position);
            holder.bind(stat);
        }

        @Override
        public int getItemCount() {
            return voiceStatsList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private ImageView avatarImageView;
            private TextView nameTextView;
            private TextView onlineStatusTextView;
            private TextView positiveVotesTextView;
            private TextView negativeVotesTextView;
            private TextView totalVotesTextView;
            private ProgressBar ratingProgressBar;
            private View onlineIndicator;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                avatarImageView = itemView.findViewById(R.id.avatar_image);
                nameTextView = itemView.findViewById(R.id.name_text);
                onlineStatusTextView = itemView.findViewById(R.id.online_status);
                positiveVotesTextView = itemView.findViewById(R.id.positive_votes);
                negativeVotesTextView = itemView.findViewById(R.id.negative_votes);
                totalVotesTextView = itemView.findViewById(R.id.total_votes);
                ratingProgressBar = itemView.findViewById(R.id.rating_progress);
                onlineIndicator = itemView.findViewById(R.id.online_indicator);
            }

            void bind(VoiceStat stat) {
                // Устанавливаем аватар
                if (stat.photoUrl != null && !stat.photoUrl.isEmpty()) {
                    Picasso.get()
                            .load(stat.photoUrl)
                            .placeholder(R.drawable.accoun_oval)
                            .error(R.drawable.accoun_oval)
                            .resize(80, 80)
                            .centerCrop()
                            .transform(new CircleTransform())
                            .into(avatarImageView);
                }

                // Устанавливаем имя
                nameTextView.setText(stat.firstName + " " + stat.lastName);

                // Онлайн статус
                if (stat.isOnline) {
                    onlineIndicator.setVisibility(View.VISIBLE);
                    onlineStatusTextView.setText("Online");
                    onlineStatusTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green_500));
                } else {
                    onlineIndicator.setVisibility(View.GONE);
                    onlineStatusTextView.setText("Offline");
                    onlineStatusTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.gray));
                }

                // Голоса
                positiveVotesTextView.setText("+" + stat.positiveVotes);
                negativeVotesTextView.setText("-" + stat.negativeVotes);
                totalVotesTextView.setText(String.valueOf(stat.totalVotes));

                // Прогресс бар рейтинга
                if (stat.totalVotes > 0) {
                    int progress = (int) ((float) stat.positiveVotes / stat.totalVotes * 100);
                    ratingProgressBar.setProgress(progress);
                } else {
                    ratingProgressBar.setProgress(0);
                }

                // Цвета в зависимости от рейтинга
                if (stat.positiveVotes > stat.negativeVotes) {
                    totalVotesTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green_500));
                } else if (stat.positiveVotes < stat.negativeVotes) {
                    totalVotesTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red));
                } else {
                    totalVotesTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.gray));
                }

                // Обработчик клика
                itemView.setOnClickListener(v -> {
                    // Можно открыть детальную статистику пользователя
                    showUserStatsDialog(stat);
                });
            }

            private void showUserStatsDialog(VoiceStat stat) {
                Context context = itemView.getContext();
                new MaterialAlertDialogBuilder(context)
                        .setTitle("Статистика голосов")
                        .setMessage(
                                stat.firstName + " " + stat.lastName + "\n\n" +
                                        "Положительные голоса: " + stat.positiveVotes + "\n" +
                                        "Отрицательные голоса: " + stat.negativeVotes + "\n" +
                                        "Всего голосов: " + stat.totalVotes + "\n" +
                                        "Рейтинг: " + (stat.totalVotes > 0 ?
                                        String.format("%.1f%%", (float) stat.positiveVotes / stat.totalVotes * 100) : "0%")
                        )
                        .setPositiveButton("OK", null)
                        .show();
            }
        }
    }

    // Класс для круглых изображений (если у вас его нет)
    public static class CircleTransform implements Transformation {
        @Override
        public Bitmap transform(Bitmap source) {
            int size = Math.min(source.getWidth(), source.getHeight());
            int x = (source.getWidth() - size) / 2;
            int y = (source.getHeight() - size) / 2;

            Bitmap squaredBitmap = Bitmap.createBitmap(source, x, y, size, size);
            if (squaredBitmap != source) {
                source.recycle();
            }

            Bitmap bitmap = Bitmap.createBitmap(size, size, source.getConfig());

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            BitmapShader shader = new BitmapShader(squaredBitmap,
                    BitmapShader.TileMode.CLAMP, BitmapShader.TileMode.CLAMP);
            paint.setShader(shader);
            paint.setAntiAlias(true);

            float r = size / 2f;
            canvas.drawCircle(r, r, r, paint);

            squaredBitmap.recycle();
            return bitmap;
        }

        @Override
        public String key() {
            return "circle";
        }
    }
}