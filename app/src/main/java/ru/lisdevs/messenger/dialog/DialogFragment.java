package ru.lisdevs.messenger.dialog;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call; // ДОБАВИТЬ этот импорт
import okhttp3.Callback; // ДОБАВИТЬ этот импорт
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.model.Message;
import ru.lisdevs.messenger.utils.TokenManager;

import android.content.Intent;
import android.widget.Toast;

import ru.lisdevs.messenger.R;

public class DialogFragment extends Fragment {

    private static final String TAG = "DialogFragment";
    private RecyclerView recyclerView;
    private DialogAdapter adapter;
    private List<Message> messageList = new ArrayList<>();
    private String userId;
    private String userName;
    private String peerId;
    private boolean isSpecialUser;
    private EditText editTextMessage;
    private ImageButton buttonSend;
    private Toolbar toolbar;

    // Метод для создания экземпляра фрагмента с аргументами
    public static DialogFragment newInstance(String userId, String userName, String peerId, boolean isSpecialUser) {
        DialogFragment fragment = new DialogFragment();
        Bundle args = new Bundle();
        args.putString("userId", userId);
        args.putString("userName", userName);
        args.putString("peerId", peerId);
        args.putBoolean("isSpecialUser", isSpecialUser);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Получаем переданные аргументы
        if (getArguments() != null) {
            userId = getArguments().getString("userId");
            userName = getArguments().getString("userName");
            peerId = getArguments().getString("peerId");
            isSpecialUser = getArguments().getBoolean("isSpecialUser", false);

            Log.d(TAG, "Received arguments - userId: " + userId +
                    ", userName: " + userName +
                    ", peerId: " + peerId);
        }

        // Если peerId все еще null, попробуем получить его из активности
        if (peerId == null && getActivity() != null) {
            Intent intent = getActivity().getIntent();
            if (intent != null && intent.hasExtra("peerId")) {
                peerId = intent.getStringExtra("peerId");
                Log.d(TAG, "Retrieved peerId from activity intent: " + peerId);
            }
        }

        // Если peerId все еще null, используем userId как peerId
        if (peerId == null && userId != null) {
            peerId = userId;
            Log.d(TAG, "Using userId as peerId: " + peerId);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dialog, container, false);

        // Настройка тулбара
        toolbar = view.findViewById(R.id.toolbar);
        TextView toolbarTitle = view.findViewById(R.id.toolbarTitle);
        toolbarTitle.setText(userName);

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        // Настройка RecyclerView для отображения сообщений диалога
        recyclerView = view.findViewById(R.id.recyclerViewDialog);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new DialogAdapter(messageList, userId, isSpecialUser);
        recyclerView.setAdapter(adapter);

        // Настройка поля ввода и кнопки отправки
        editTextMessage = view.findViewById(R.id.editTextMessage);
        buttonSend = view.findViewById(R.id.btnSend);

        buttonSend.setOnClickListener(v -> {
            String messageText = editTextMessage.getText().toString().trim();
            if (!messageText.isEmpty()) {
                sendMessage(messageText);
                editTextMessage.setText("");
            }
        });

        // Обработка нажатия Enter в EditText
        editTextMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                String messageText = editTextMessage.getText().toString().trim();
                if (!messageText.isEmpty()) {
                    sendMessage(messageText);
                    editTextMessage.setText("");
                }
                return true;
            }
            return false;
        });

        // Проверяем еще раз перед загрузкой истории
        if (peerId == null) {
            Log.e(TAG, "peerId is still null! Cannot load messages.");
            Toast.makeText(getContext(), "Ошибка: не удалось загрузить диалог", Toast.LENGTH_SHORT).show();
        } else {
            // Загружаем историю сообщений
            loadDialogHistory();
        }

        return view;
    }

    private void loadDialogHistory() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        Log.d(TAG, "Loading dialog history - Token: " + (accessToken != null) + ", PeerId: " + peerId);

        if (accessToken != null && peerId != null) {
            String url = "https://api.vk.com/method/messages.getHistory" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&peer_id=" + peerId +
                    "&count=30" +
                    "&extended=1";

            Log.d(TAG, "Request URL: " + url);

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to load dialog history", e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Ошибка загрузки сообщений", Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    Log.d(TAG, "Response received - Code: " + response.code());

                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        Log.d(TAG, "Response body: " + responseBody);

                        try {
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("error")) {
                                JSONObject error = json.getJSONObject("error");
                                Log.e(TAG, "VK API Error: " + error.toString());
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        Toast.makeText(getContext(), "Ошибка VK API: " + error.optString("error_msg"), Toast.LENGTH_SHORT).show();
                                    });
                                }
                                return;
                            }

                            if (json.has("response")) {
                                JSONObject responseObj = json.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");
                                JSONArray profiles = responseObj.optJSONArray("profiles");

                                Log.d(TAG, "Loaded " + items.length() + " messages");

                                Map<String, String> userNames = parseUserNames(profiles);
                                List<Message> messages = new ArrayList<>();

                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject messageObj = items.getJSONObject(i);

                                    String text = messageObj.optString("text");
                                    String senderId = String.valueOf(messageObj.optInt("from_id"));
                                    long date = messageObj.optLong("date") * 1000;
                                    boolean isOut = messageObj.optInt("out") == 1;

                                    String senderName = userNames.get(senderId);
                                    if (senderName == null) {
                                        senderName = "Пользователь " + senderId;
                                    }

                                    Message message = new Message(senderId, senderName, text, date, null);
                                    message.setOutgoing(isOut);
                                    message.setPeerId(peerId);

                                    messages.add(0, message);
                                }

                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        Log.d(TAG, "Adding " + messages.size() + " messages to adapter");
                                        if (messages.isEmpty()) {
                                            Toast.makeText(getContext(), "Диалог пуст", Toast.LENGTH_SHORT).show();
                                        }
                                        adapter.addMessages(messages);
                                        recyclerView.scrollToPosition(adapter.getItemCount() - 1);
                                    });
                                }
                            } else {
                                Log.e(TAG, "No 'response' field in JSON");
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        Toast.makeText(getContext(), "Ошибка формата ответа", Toast.LENGTH_SHORT).show();
                                    });
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing dialog history", e);
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    } else {
                        Log.e(TAG, "Unsuccessful response: " + response.code());
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Ошибка сети: " + response.code(), Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                }
            });
        } else {
            Log.e(TAG, "Token or peerId is null - Token: " + accessToken + ", PeerId: " + peerId);
            Toast.makeText(getContext(), "Ошибка: токен или ID диалога не доступны", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMessage(String text) {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken != null && peerId != null) {
            // Создаем временное сообщение для отображения
            Message tempMessage = new Message(userId, userName, text, System.currentTimeMillis(), null);
            tempMessage.setOutgoing(true);
            tempMessage.setReadStatus(Message.READ_STATUS_SENT);
            tempMessage.setPeerId(peerId);

            adapter.addMessage(tempMessage);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);

            // Отправляем сообщение через API
            try {
                String url = "https://api.vk.com/method/messages.send" +
                        "?access_token=" + accessToken +
                        "&v=5.131" +
                        "&peer_id=" + peerId +
                        "&message=" + URLEncoder.encode(text, "UTF-8") +
                        "&random_id=" + System.currentTimeMillis();

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e(TAG, "Failed to send message", e);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Ошибка отправки сообщения", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Message sent successfully");
                            // Можно обновить статус сообщения на "доставлено"
                        } else {
                            Log.e(TAG, "Failed to send message, code: " + response.code());
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Ошибка отправки: " + response.code(), Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    }
                });
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Encoding error", e);
                Toast.makeText(getContext(), "Ошибка кодирования", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getContext(), "Не удалось отправить сообщение", Toast.LENGTH_SHORT).show();
        }
    }

    private Map<String, String> parseUserNames(JSONArray profiles) {
        Map<String, String> userNames = new HashMap<>();
        if (profiles != null) {
            for (int i = 0; i < profiles.length(); i++) {
                try {
                    JSONObject profile = profiles.getJSONObject(i);
                    String userId = String.valueOf(profile.optInt("id"));
                    String firstName = profile.optString("first_name");
                    String lastName = profile.optString("last_name");
                    userNames.put(userId, firstName + " " + lastName);
                    Log.d(TAG, "Parsed user: " + userId + " - " + firstName + " " + lastName);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing user profile", e);
                }
            }
        }
        return userNames;
    }
}