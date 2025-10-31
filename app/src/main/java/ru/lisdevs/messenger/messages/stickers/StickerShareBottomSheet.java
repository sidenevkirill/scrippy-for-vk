package ru.lisdevs.messenger.messages.stickers;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Message;
import ru.lisdevs.messenger.model.Sticker;
import ru.lisdevs.messenger.utils.TokenManager;

public class StickerShareBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_STICKER = "sticker";
    private StickerShareListener listener;
    private Sticker sticker;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyText;
    private DialogListAdapter adapter;
    private List<Message> dialogs = new ArrayList<>();

    public interface StickerShareListener {
        void onDialogSelected(Message dialog, Sticker sticker);
    }

    public static StickerShareBottomSheet newInstance(Sticker sticker) {
        StickerShareBottomSheet fragment = new StickerShareBottomSheet();
        Bundle args = new Bundle();
        args.putParcelable(ARG_STICKER, sticker);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            sticker = getArguments().getParcelable(ARG_STICKER);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_share_sticker, container, false);
        initViews(view);
        loadDialogs();
        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerViewDialogs);
        progressBar = view.findViewById(R.id.progressBar);
        emptyText = view.findViewById(R.id.emptyText);

        // Настройка RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        adapter = new DialogListAdapter(dialogs, this::onDialogSelected);
        recyclerView.setAdapter(adapter);

        // Заголовок
        TextView title = view.findViewById(R.id.title);
        title.setText("Отправить стикер в диалог");

        // Кнопка закрытия
        ImageButton closeButton = view.findViewById(R.id.btnClose);
        closeButton.setOnClickListener(v -> dismiss());
    }

    private void onDialogSelected(Message dialog) {
        if (listener != null && sticker != null) {
            listener.onDialogSelected(dialog, sticker);
        }
        dismiss();
    }

    private void loadDialogs() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            showError("Ошибка авторизации");
            return;
        }

        String url = "https://api.vk.com/method/messages.getConversations" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&count=50" +
                "&extended=1" +
                "&fields=photo_100,online,verified";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showError("Ошибка загрузки диалогов");
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    parseDialogs(responseBody);
                } else {
                    requireActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        showError("Ошибка сети");
                    });
                }
            }
        });
    }

    private void parseDialogs(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            if (json.has("response")) {
                JSONObject response = json.getJSONObject("response");
                JSONArray items = response.getJSONArray("items");
                JSONArray profiles = response.optJSONArray("profiles");
                JSONArray groups = response.optJSONArray("groups");

                Map<String, String> userNames = parseUserNames(profiles, groups);
                List<Message> loadedDialogs = new ArrayList<>();

                for (int i = 0; i < items.length(); i++) {
                    JSONObject conversation = items.getJSONObject(i);
                    JSONObject lastMessage = conversation.getJSONObject("last_message");

                    Message dialog = parseDialog(conversation, lastMessage, userNames);
                    if (dialog != null) {
                        loadedDialogs.add(dialog);
                    }
                }

                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    dialogs.clear();
                    dialogs.addAll(loadedDialogs);
                    adapter.notifyDataSetChanged();

                    if (dialogs.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                        emptyText.setText("Диалоги не найдены");
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                    }
                });
            }
        } catch (JSONException e) {
            requireActivity().runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                showError("Ошибка обработки данных");
            });
        }
    }

    private Message parseDialog(JSONObject conversation, JSONObject lastMessage, Map<String, String> userNames) throws JSONException {
        String peerId = String.valueOf(lastMessage.getInt("peer_id"));
        String text = lastMessage.optString("text", "");
        long date = lastMessage.optLong("date") * 1000;

        // Получаем количество непрочитанных сообщений из conversation
        JSONObject conversationObj = conversation.optJSONObject("conversation");
        int unreadCount = 0;
        if (conversationObj != null) {
            unreadCount = conversationObj.optInt("unread_count", 0);
        }

        // Определяем имя диалога
        String dialogName = getDialogName(peerId, userNames, lastMessage);

        // Создаем сообщение с использованием вашего класса Message
        Message dialog = new Message();
        dialog.setPeerId(peerId);
        dialog.setSenderName(dialogName);
        dialog.setBody(text);
        dialog.setDate(date);

        // Устанавливаем количество непрочитанных сообщений
        // Добавляем поле unreadCount в ваш класс Message
        try {
            // Если в вашем классе Message есть поле unreadCount
            Field unreadCountField = Message.class.getDeclaredField("unreadCount");
            unreadCountField.setAccessible(true);
            unreadCountField.set(dialog, unreadCount);
        } catch (Exception e) {
            // Если поля нет, используем альтернативный способ
            Log.d("StickerShare", "unreadCount field not found in Message class");
        }

        return dialog;
    }

    private String getDialogName(String peerId, Map<String, String> userNames, JSONObject lastMessage) {
        // Сначала пробуем найти в userNames
        if (userNames.containsKey(peerId)) {
            return userNames.get(peerId);
        }

        // Для пользователей peer_id может быть в формате "user{id}"
        if (peerId.startsWith("user")) {
            String userId = peerId.substring(4);
            if (userNames.containsKey(userId)) {
                return userNames.get(userId);
            }
        }

        // Для групп peer_id может быть в формате "club{id}" или "group{id}"
        if (peerId.startsWith("club") || peerId.startsWith("group")) {
            String groupId = peerId.substring(4);
            if (userNames.containsKey("-" + groupId)) {
                return userNames.get("-" + groupId);
            }
        }

        // Если имя не найдено, используем текст последнего сообщения как заголовок
        String text = lastMessage.optString("text", "");
        if (!text.isEmpty()) {
            if (text.length() > 30) {
                return text.substring(0, 30) + "...";
            }
            return text;
        }

        return "Диалог " + peerId;
    }

    private Map<String, String> parseUserNames(JSONArray profiles, JSONArray groups) {
        Map<String, String> userNames = new HashMap<>();

        try {
            // Парсим профили пользователей
            if (profiles != null) {
                for (int i = 0; i < profiles.length(); i++) {
                    JSONObject profile = profiles.getJSONObject(i);
                    String userId = String.valueOf(profile.getInt("id"));
                    String firstName = profile.getString("first_name");
                    String lastName = profile.getString("last_name");
                    String fullName = firstName + " " + lastName;

                    userNames.put(userId, fullName);
                    userNames.put("user" + userId, fullName);
                }
            }

            // Парсим группы
            if (groups != null) {
                for (int i = 0; i < groups.length(); i++) {
                    JSONObject group = groups.getJSONObject(i);
                    String groupId = String.valueOf(-group.getInt("id")); // Отрицательный ID для групп
                    String groupName = group.getString("name");

                    userNames.put(groupId, groupName);
                    userNames.put("club" + Math.abs(group.getInt("id")), groupName);
                    userNames.put("group" + Math.abs(group.getInt("id")), groupName);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return userNames;
    }

    private void showError(String message) {
        emptyText.setVisibility(View.VISIBLE);
        emptyText.setText(message);
        recyclerView.setVisibility(View.GONE);
    }

    public void setStickerShareListener(StickerShareListener listener) {
        this.listener = listener;
    }
}