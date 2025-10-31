package ru.lisdevs.messenger.calls;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.dialog.DialogActivity;
import ru.lisdevs.messenger.utils.TokenManager;

public class CallsFragment extends Fragment {

    private static final String TAG = "CallsFragment";
    private RecyclerView recyclerView;
    private CallsAdapter adapter;
    private List<VKCall> callList = new ArrayList<>();
    private Set<String> specialUsers = new HashSet<>();
    private OkHttpClient httpClient = new OkHttpClient();
    private boolean isSpecialUsersLoaded = false;
    private TextView callsCountText;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calls, container, false);

        recyclerView = view.findViewById(R.id.recyclerViewCalls);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        callsCountText = view.findViewById(R.id.count);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        updateCallsCountText(0);

        adapter = new CallsAdapter(callList, new CallsAdapter.SpecialUserChecker() {
            @Override
            public boolean isSpecialUser(String userId) {
                return specialUsers.contains(userId);
            }
        });

        adapter.setOnItemClickListener(new CallsAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(VKCall call) {
                openUserProfile(call);
            }
        });

        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            String accessToken = TokenManager.getInstance(getContext()).getToken();
            if (accessToken != null) {
                fetchCalls(accessToken);
            } else {
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_SHORT).show();
            }
        });

        loadSpecialUsers();

        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken != null) {
            fetchCalls(accessToken);
        } else {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    private void openUserProfile(VKCall call) {
        String userId = call.getUserId();
        String userName = call.getUserName();
        boolean isSpecialUser = specialUsers.contains(userId);

        Log.d(TAG, "Opening user profile - UserId: " + userId +
                ", UserName: " + userName +
                ", IsSpecial: " + isSpecialUser);

        DialogActivity.start(requireContext(), userId, userName, userId, isSpecialUser);
    }

    private void updateCallsCountText(int count) {
        if (callsCountText != null) {
            String countText = formatCallsCount(count);
            callsCountText.setText(countText);
        }
    }

    private String formatCallsCount(int count) {
        if (count == 0) {
            return "Звонки не загружены";
        } else if (count == 1) {
            return "1 звонок";
        } else if (count >= 2 && count <= 4) {
            return count + " звонка";
        } else {
            return count + " звонков";
        }
    }

    private void loadSpecialUsers() {
        Request request = new Request.Builder()
                .url("https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/special_users.json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("CallsFragment", "Failed to load special users", e);
                isSpecialUsersLoaded = true;
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        parseSpecialUsers(json);
                    }
                } catch (Exception e) {
                    Log.e("CallsFragment", "Error parsing special users", e);
                } finally {
                    isSpecialUsersLoaded = true;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                        });
                    }
                }
            }
        });
    }

    private void parseSpecialUsers(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray usersArray = jsonObject.getJSONArray("special_users");

            specialUsers.clear();
            for (int i = 0; i < usersArray.length(); i++) {
                long userId = usersArray.getLong(i);
                specialUsers.add(String.valueOf(userId));
            }

            Log.d("CallsFragment", "Loaded " + specialUsers.size() + " special users");
        } catch (JSONException e) {
            Log.e("CallsFragment", "Error parsing special users JSON", e);
        }
    }

    private void fetchCalls(String accessToken) {
        // Получаем список диалогов
        String conversationsUrl = "https://api.vk.com/method/messages.getConversations" +
                "?access_token=" + accessToken +
                "&v=5.199" +
                "&count=100" +
                "&extended=1";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(conversationsUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), "Ошибка сети: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    updateCallsCountText(0);
                    createDemoCalls();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");
                            JSONArray profiles = responseObj.optJSONArray("profiles");
                            Map<String, String> userNames = parseUserNames(profiles);

                            // Получаем ID текущего пользователя
                            String currentUserId = getCurrentUserId(responseObj);

                            // Собираем все звонки из диалогов
                            List<VKCall> allCalls = new ArrayList<>();

                            for (int i = 0; i < items.length(); i++) {
                                JSONObject conversation = items.getJSONObject(i);
                                JSONObject lastMessage = conversation.getJSONObject("last_message");

                                // Проверяем последнее сообщение на наличие информации о звонке
                                if (isCallMessage(lastMessage)) {
                                    // Определяем ID пользователя, который звонил
                                    String callerUserId = determineCallerUserId(lastMessage, currentUserId, userNames);
                                    String userName = userNames.get(callerUserId);

                                    if (userName == null) {
                                        userName = "Пользователь " + callerUserId;
                                    }

                                    long time = lastMessage.optLong("date") * 1000;

                                    VKCall callItem = new VKCall(
                                            lastMessage.optString("id"),
                                            callerUserId,
                                            userName,
                                            time,
                                            extractCallDuration(lastMessage),
                                            determineCallDirection(lastMessage, currentUserId, callerUserId),
                                            extractCallType(lastMessage)
                                    );

                                    allCalls.add(callItem);
                                }
                            }

                            requireActivity().runOnUiThread(() -> {
                                adapter.setCalls(allCalls);
                                updateCallsCountText(allCalls.size());
                                swipeRefreshLayout.setRefreshing(false);

                                if (allCalls.isEmpty()) {
                                    createDemoCalls();
                                    Toast.makeText(getContext(),
                                            "Демо-звонки загружены (реальные звонки не найдены)",
                                            Toast.LENGTH_LONG).show();
                                } else {

                                }
                            });
                        } else if (json.has("error")) {
                            String errorMsg = json.getJSONObject("error").optString("error_msg");
                            requireActivity().runOnUiThread(() -> {
                                swipeRefreshLayout.setRefreshing(false);
                                Toast.makeText(getContext(), "Ошибка API: " + errorMsg, Toast.LENGTH_LONG).show();
                                updateCallsCountText(0);
                                createDemoCalls();
                            });
                        }
                    } catch (JSONException e) {
                        requireActivity().runOnUiThread(() -> {
                            swipeRefreshLayout.setRefreshing(false);
                            Toast.makeText(getContext(), "Ошибка парсинга: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            updateCallsCountText(0);
                            createDemoCalls();
                        });
                    }
                } else {
                    requireActivity().runOnUiThread(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), "Ошибка сервера: " + response.code(), Toast.LENGTH_LONG).show();
                        updateCallsCountText(0);
                        createDemoCalls();
                    });
                }
            }
        });
    }

    private String getCurrentUserId(JSONObject responseObj) {
        // Получаем ID текущего пользователя из профилей
        try {
            JSONArray profiles = responseObj.optJSONArray("profiles");
            if (profiles != null && profiles.length() > 0) {
                // Предполагаем, что первый профиль - это текущий пользователь
                JSONObject firstProfile = profiles.getJSONObject(0);
                return firstProfile.optString("id");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error getting current user ID", e);
        }
        return null;
    }

    private String determineCallerUserId(JSONObject message, String currentUserId, Map<String, String> userNames) {
        // Анализируем сообщение, чтобы определить, кто звонил

        String text = message.optString("text", "").toLowerCase();
        String fromId = message.optString("from_id");

        // Если сообщение от текущего пользователя, значит он совершал звонок
        if (fromId.equals(currentUserId)) {
            // Ищем ID получателя в тексте или контексте
            return findOtherUserIdInConversation(message, currentUserId, userNames);
        } else {
            // Сообщение от другого пользователя - он и есть звонящий
            return fromId;
        }
    }

    private String findOtherUserIdInConversation(JSONObject message, String currentUserId, Map<String, String> userNames) {
        // В реальном приложении здесь нужно анализировать контекст диалога
        // Для демонстрации возвращаем случайного пользователя из доступных

        for (String userId : userNames.keySet()) {
            if (!userId.equals(currentUserId)) {
                return userId;
            }
        }

        // Если не нашли другого пользователя, возвращаем фиктивный ID
        return "unknown_user";
    }

    private boolean determineCallDirection(JSONObject message, String currentUserId, String callerUserId) {
        // Определяем направление звонка
        // Если звонящий - текущий пользователь, то исходящий, иначе входящий

        String fromId = message.optString("from_id");

        // Если сообщение о звонке от текущего пользователя, значит он звонил (исходящий)
        if (fromId.equals(currentUserId)) {
            return false; // исходящий
        } else {
            return true; // входящий
        }
    }

    private void createDemoCalls() {
        // Создаем демонстрационные данные для тестирования
        List<VKCall> demoCalls = new ArrayList<>();

        String[] demoUsers = {
                "Иван Петров", "Мария Сидорова", "Алексей Козлов",
                "Екатерина Смирнова", "Дмитрий Васильев"
        };

        String[] demoUserIds = {"12345", "23456", "34567", "45678", "56789"};

        long currentTime = System.currentTimeMillis();

        for (int i = 0; i < demoUsers.length; i++) {
            VKCall call = new VKCall(
                    "demo_" + i,
                    demoUserIds[i],
                    demoUsers[i],
                    currentTime - (i * 3600000L), // разное время для каждого звонка
                    i % 2 == 0 ? 125 : 0, // разная длительность
                    i % 3 != 0, // разные направления (true - входящий, false - исходящий)
                    i % 2 == 0 ? "voice" : "video"
            );
            demoCalls.add(call);
        }

        adapter.setCalls(demoCalls);
        updateCallsCountText(demoCalls.size());
    }

    private boolean isCallMessage(JSONObject message) {
        // Проверяем текст сообщения на наличие ключевых слов о звонках
        String text = message.optString("text", "").toLowerCase();

        String[] callKeywords = {
                "звонок", "call", "вызов", "позвонил", "позвонила",
                "длительность", "duration", "📞", "📱", "☎️", "звонка", "звонке"
        };

        for (String keyword : callKeywords) {
            if (text.contains(keyword)) {
                Log.d(TAG, "Found call message: " + text);
                return true;
            }
        }

        // Проверяем вложения
        if (message.has("attachments")) {
            try {
                JSONArray attachments = message.getJSONArray("attachments");
                for (int i = 0; i < attachments.length(); i++) {
                    JSONObject attachment = attachments.getJSONObject(i);
                    String type = attachment.optString("type");
                    if ("call".equals(type)) {
                        Log.d(TAG, "Found call attachment");
                        return true;
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing attachments", e);
            }
        }

        // Проверяем action (для групповых чатов)
        if (message.has("action")) {
            JSONObject action = message.optJSONObject("action");
            if (action != null) {
                String actionType = action.optString("type");
                if ("chat_call".equals(actionType)) {
                    Log.d(TAG, "Found chat call action");
                    return true;
                }
            }
        }

        return false;
    }

    private int extractCallDuration(JSONObject message) {
        String text = message.optString("text", "").toLowerCase();

        // Пытаемся извлечь длительность из текста
        if (text.contains("длительность")) {
            try {
                // Ищем числа в тексте (в секундах или минутах)
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)\\s*(сек|с|мин|м)");
                java.util.regex.Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    int number = Integer.parseInt(matcher.group(1));
                    String unit = matcher.group(2);
                    if (unit.contains("мин") || unit.contains("м")) {
                        return number * 60; // конвертируем минуты в секунды
                    }
                    return number;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting call duration", e);
            }
        }

        return 0;
    }

    private String extractCallType(JSONObject message) {
        String text = message.optString("text", "").toLowerCase();

        if (text.contains("видео") || text.contains("video")) {
            return "video";
        }
        return "voice";
    }

    private Map<String, String> parseUserNames(JSONArray profiles) {
        Map<String, String> userNames = new HashMap<>();
        if (profiles != null) {
            for (int i = 0; i < profiles.length(); i++) {
                try {
                    JSONObject profile = profiles.getJSONObject(i);
                    String userId = profile.optString("id");
                    String firstName = profile.optString("first_name");
                    String lastName = profile.optString("last_name");
                    userNames.put(userId, firstName + " " + lastName);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing user profile", e);
                }
            }
        }
        return userNames;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
        }
    }

    public static class VKCall {
        private String callId;
        private String userId;
        private String userName;
        private long time;
        private int duration;
        private boolean isIncoming;
        private String callType;

        public VKCall(String callId, String userId, String userName, long time,
                      int duration, boolean isIncoming, String callType) {
            this.callId = callId;
            this.userId = userId;
            this.userName = userName;
            this.time = time;
            this.duration = duration;
            this.isIncoming = isIncoming;
            this.callType = callType;
        }

        public String getCallId() { return callId; }
        public String getUserId() { return userId; }
        public String getUserName() { return userName; }
        public long getTime() { return time; }
        public int getDuration() { return duration; }
        public boolean isIncoming() { return isIncoming; }
        public String getCallType() { return callType; }

        public String getFormattedDuration() {
            if (duration == 0) return "Пропущенный";
            int minutes = duration / 60;
            int seconds = duration % 60;
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    public static class CallsAdapter extends RecyclerView.Adapter<CallsAdapter.CallViewHolder> {
        private List<VKCall> calls;
        private Random random = new Random();
        private SpecialUserChecker specialUserChecker;
        private OnItemClickListener onItemClickListener;

        public interface SpecialUserChecker {
            boolean isSpecialUser(String userId);
        }

        public interface OnItemClickListener {
            void onItemClick(VKCall call);
        }

        public CallsAdapter(List<VKCall> calls, SpecialUserChecker specialUserChecker) {
            this.calls = calls;
            this.specialUserChecker = specialUserChecker;
        }

        public void setCalls(List<VKCall> calls) {
            this.calls = calls;
            notifyDataSetChanged();
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            this.onItemClickListener = listener;
        }

        @NonNull
        @Override
        public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_call, parent, false);
            return new CallViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
            VKCall call = calls.get(position);
            holder.bind(call, specialUserChecker, onItemClickListener);
        }

        @Override
        public int getItemCount() {
            return calls.size();
        }

        static class CallViewHolder extends RecyclerView.ViewHolder {
            TextView textUserName;
            TextView textCallInfo;
            TextView textDate;
            TextView textDuration;
            TextView avatarTextView;
            ImageView verifiedIcon;
            ImageView callTypeIcon;
            ImageView callDirectionIcon;
            private Random random = new Random();

            public CallViewHolder(@NonNull View itemView) {
                super(itemView);
                textUserName = itemView.findViewById(R.id.textUserName);
                textCallInfo = itemView.findViewById(R.id.textCallInfo);
                textDate = itemView.findViewById(R.id.textDate);
                textDuration = itemView.findViewById(R.id.textDuration);
                avatarTextView = itemView.findViewById(R.id.avatarTextView);
                verifiedIcon = itemView.findViewById(R.id.verified_icon);
                callTypeIcon = itemView.findViewById(R.id.callTypeIcon);
                callDirectionIcon = itemView.findViewById(R.id.callDirectionIcon);
            }

            void bind(VKCall call, SpecialUserChecker specialUserChecker, OnItemClickListener listener) {
                // Отображаем имя пользователя, который звонил
                textUserName.setText(call.getUserName());

                // Отображаем направление звонка с точки зрения текущего пользователя
                String directionText = call.isIncoming() ? "Входящий" : "Исходящий";
                textCallInfo.setText(directionText);

                textDate.setText(formatDate(call.getTime()));
                textDuration.setText(call.getFormattedDuration());

                String firstLetter = getFirstLetter(call.getUserName());
                avatarTextView.setText(firstLetter);

                int color = getRandomColor();
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(color);
                avatarTextView.setBackground(drawable);

                // Иконка типа звонка
                if (callTypeIcon != null) {
                    if ("video".equals(call.getCallType())) {
                        callTypeIcon.setImageResource(R.drawable.account_voice);
                    } else {
                        callTypeIcon.setImageResource(R.drawable.account_voice);
                    }
                    callTypeIcon.setVisibility(View.VISIBLE);
                }

                // Иконка направления звонка
                if (callDirectionIcon != null) {
                    if (call.isIncoming()) {
                        // Входящий звонок - зеленый
                        callDirectionIcon.setImageResource(R.drawable.call_received);
                        callDirectionIcon.setColorFilter(Color.GREEN);
                    } else {
                        // Исходящий звонок - синий
                        callDirectionIcon.setImageResource(R.drawable.call_made);
                        callDirectionIcon.setColorFilter(Color.BLUE);
                    }
                    callDirectionIcon.setVisibility(View.VISIBLE);
                }

                // Галочка верификации
                if (verifiedIcon != null) {
                    if (specialUserChecker.isSpecialUser(call.getUserId())) {
                        verifiedIcon.setVisibility(View.VISIBLE);
                        verifiedIcon.setImageResource(R.drawable.check_decagram);
                    } else {
                        verifiedIcon.setVisibility(View.GONE);
                    }
                }

                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onItemClick(call);
                    }
                });
            }

            private String getFirstLetter(String name) {
                if (!TextUtils.isEmpty(name)) {
                    String[] nameParts = name.split(" ");
                    if (nameParts.length > 0) {
                        return nameParts[0].substring(0, 1).toUpperCase();
                    }
                    return name.substring(0, 1).toUpperCase();
                }
                return "?";
            }

            private int getRandomColor() {
                int[] colors = {
                        Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
                        Color.parseColor("#45B7D1"), Color.parseColor("#F9A826"),
                        Color.parseColor("#6A5ACD"), Color.parseColor("#FFA07A"),
                        Color.parseColor("#20B2AA"), Color.parseColor("#9370DB"),
                        Color.parseColor("#3CB371"), Color.parseColor("#FF4500")
                };
                return colors[random.nextInt(colors.length)];
            }

            private String formatDate(long timestamp) {
                Date date = new Date(timestamp);
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault());
                return sdf.format(date);
            }
        }
    }
}