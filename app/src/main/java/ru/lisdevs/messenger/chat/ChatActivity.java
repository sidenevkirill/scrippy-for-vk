package ru.lisdevs.messenger.chat;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ru.lisdevs.messenger.R;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Random;
import java.util.Map;

import android.util.Log;
import android.view.View;

import org.json.JSONException;

public class ChatActivity extends AppCompatActivity {

    private TextView chatTitle;
    private ListView messagesListView;
    private EditText messageInput;
    private ImageButton sendButton;

    private ArrayList<Message> messagesList;
    private MessageAdapter messageAdapter;
    private String chatName;
    private int avatarRes;

    private HashMap<String, ArrayList<String>> responsesMap;
    private ArrayList<String> defaultResponses;
    private Random random;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Получаем данные из интента
        chatName = getIntent().getStringExtra("chatName");
        avatarRes = getIntent().getIntExtra("avatarRes", R.drawable.default_avatar);

        random = new Random();
        responsesMap = new HashMap<>();
        defaultResponses = new ArrayList<>();

        setupToolbar();
        setupViews();
        setupMessages();
        loadResponsesFromGitHub();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        chatTitle = findViewById(R.id.chatTitle);
        chatTitle.setText(chatName);

//        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupViews() {
        messagesListView = findViewById(R.id.messagesListView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        sendButton.setOnClickListener(v -> sendMessage());

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
    }

    private void setupMessages() {
        messagesList = new ArrayList<>();

        // Приветственное сообщение
        messagesList.add(new Message(
                "Привет! Я бот с готовыми ответами. Задайте мне вопрос!",
                getCurrentTime(),
                false,
                "Авто-бот",
                R.drawable.accoun_oval
        ));

        messageAdapter = new MessageAdapter(this, messagesList);
        messagesListView.setAdapter(messageAdapter);
        scrollToBottom();
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();

        if (!messageText.isEmpty()) {
            // Добавляем сообщение пользователя
            Message userMessage = new Message(messageText, getCurrentTime(), true);
            messagesList.add(userMessage);
            messageAdapter.notifyDataSetChanged();
            messageInput.setText("");
            scrollToBottom();

            // Показываем индикатор набора
            showTypingIndicator();

            // Генерируем ответ
            generateResponse(messageText);
        }
    }

    private void showTypingIndicator() {
        Message typingMessage = new Message("...", getCurrentTime(), false, "Авто-бот", R.drawable.accoun_oval);
        messagesList.add(typingMessage);
        messageAdapter.notifyDataSetChanged();
        scrollToBottom();
    }

    private void removeTypingIndicator() {
        for (int i = messagesList.size() - 1; i >= 0; i--) {
            if (messagesList.get(i).getText().equals("...")) {
                messagesList.remove(i);
                break;
            }
        }
    }

    private void generateResponse(String userMessage) {
        // Имитируем задержку ответа
        new android.os.Handler().postDelayed(() -> {
            removeTypingIndicator();

            String response = findBestResponse(userMessage);
            Message botMessage = new Message(
                    response,
                    getCurrentTime(),
                    false,
                    "Авто-бот",
                    R.drawable.accoun_oval
            );
            messagesList.add(botMessage);
            messageAdapter.notifyDataSetChanged();
            scrollToBottom();
        }, 1000 + random.nextInt(1000)); // Случайная задержка 1-2 секунды
    }

    private String findBestResponse(String userMessage) {
        String message = userMessage.toLowerCase().trim();

        // Убираем знаки препинания для лучшего поиска
        String cleanMessage = message.replaceAll("[?!.,]", "").trim();

        // 1. Сначала ищем точное совпадение ключевых слов
        for (String keyword : responsesMap.keySet()) {
            String cleanKeyword = keyword.toLowerCase().replaceAll("[?!.,]", "").trim();

            // Точное совпадение ключевого слова
            if (cleanMessage.equals(cleanKeyword)) {
                ArrayList<String> responses = responsesMap.get(keyword);
                if (responses != null && !responses.isEmpty()) {
                    Log.d("ChatActivity", "Найдено точное совпадение: " + keyword);
                    return responses.get(0);
                }
            }
        }

        // 2. Ищем частичные совпадения (ключевое слово содержится в сообщении)
        for (String keyword : responsesMap.keySet()) {
            String cleanKeyword = keyword.toLowerCase().replaceAll("[?!.,]", "").trim();

            // Если ключевое слово содержится в сообщении пользователя
            if (cleanMessage.contains(cleanKeyword) && cleanKeyword.length() > 2) {
                ArrayList<String> responses = responsesMap.get(keyword);
                if (responses != null && !responses.isEmpty()) {
                    Log.d("ChatActivity", "Найдено частичное совпадение: " + keyword);
                    return responses.get(0);
                }
            }
        }

        // 3. Ищем по словам (разбиваем на слова)
        String[] userWords = cleanMessage.split("\\s+");

        // Создаем карту совпадений: ключевое слово -> количество совпавших слов
        HashMap<String, Integer> matches = new HashMap<>();

        for (String keyword : responsesMap.keySet()) {
            String cleanKeyword = keyword.toLowerCase().replaceAll("[?!.,]", "").trim();
            String[] keywordWords = cleanKeyword.split("\\s+");

            int wordMatches = 0;

            for (String userWord : userWords) {
                if (userWord.length() > 2) { // Игнорируем короткие слова
                    for (String keywordWord : keywordWords) {
                        if (keywordWord.equals(userWord)) {
                            wordMatches++;
                            break;
                        }
                    }
                }
            }

            if (wordMatches > 0) {
                matches.put(keyword, wordMatches);
            }
        }

        // Находим ключевое слово с наибольшим количеством совпадений
        if (!matches.isEmpty()) {
            String bestMatch = null;
            int maxMatches = 0;

            for (Map.Entry<String, Integer> entry : matches.entrySet()) {
                if (entry.getValue() > maxMatches) {
                    maxMatches = entry.getValue();
                    bestMatch = entry.getKey();
                }
            }

            // Если найдено достаточно совпадений
            if (bestMatch != null && maxMatches >= 1) {
                ArrayList<String> responses = responsesMap.get(bestMatch);
                if (responses != null && !responses.isEmpty()) {
                    Log.d("ChatActivity", "Найдено по ключевым словам: " + bestMatch + " (" + maxMatches + " совпадений)");
                    return responses.get(0);
                }
            }
        }

        // 4. Ищем по синонимам и популярным вариациям
        String synonymResponse = searchBySynonyms(cleanMessage);
        if (synonymResponse != null) {
            Log.d("ChatActivity", "Найдено через синонимы");
            return synonymResponse;
        }

        // 5. Ответ по умолчанию
        if (!defaultResponses.isEmpty()) {
            String defaultResponse = defaultResponses.get(random.nextInt(defaultResponses.size()));
            Log.d("ChatActivity", "Использован ответ по умолчанию");
            return defaultResponse;
        }

        Log.d("ChatActivity", "Использован запасной ответ");
        return getFallbackResponse(userMessage);
    }

    private String searchBySynonyms(String userMessage) {
        // Карта синонимов: вариация -> оригинальный вопрос
        HashMap<String, String> synonyms = new HashMap<>();

        // Добавляем синонимы для вопросов из вашего JSON
        synonyms.put("как жизнь", "как дела");
        synonyms.put("как ты", "как дела");
        synonyms.put("чего как", "как дела");
        synonyms.put("што как", "как дела");
        synonyms.put("че как", "как дела");
        synonyms.put("как сам", "как дела");
        synonyms.put("как оно", "как дела");
        synonyms.put("как поживаешь", "как дела");

        synonyms.put("здарова", "привет");
        synonyms.put("хай", "привет");
        synonyms.put("здорово", "привет");
        synonyms.put("добрый день", "привет");
        synonyms.put("добрый вечер", "привет");
        synonyms.put("доброе утро", "привет");
        synonyms.put("прив", "привет");
        synonyms.put("приветик", "привет");
        synonyms.put("салют", "привет");
        synonyms.put("ку", "привет");

        synonyms.put("пасиб", "спасибо");
        synonyms.put("благодарю", "спасибо");
        synonyms.put("сяб", "спасибо");
        synonyms.put("спс", "спасибо");
        synonyms.put("благодарствую", "спасибо");

        synonyms.put("прощай", "пока");
        synonyms.put("до встречи", "пока");
        synonyms.put("всего хорошего", "пока");
        synonyms.put("до завтра", "пока");
        synonyms.put("бывай", "пока");
        synonyms.put("увидимся", "пока");
        synonyms.put("до скорого", "пока");

        synonyms.put("чем занят", "что делаешь");
        synonyms.put("чем занимаешься", "что делаешь");
        synonyms.put("че делаешь", "что делаешь");
        synonyms.put("што делаешь", "что делаешь");
        synonyms.put("чем занята", "что делаешь");
        synonyms.put("что творишь", "что делаешь");

        synonyms.put("как настроение", "как настроение");
        synonyms.put("какое настроение", "как настроение");
        synonyms.put("настроение как", "как настроение");
        synonyms.put("как настрой", "как настроение");

        synonyms.put("какие планы", "какие планы");
        synonyms.put("планы какие", "какие планы");
        synonyms.put("что планируешь", "какие планы");
        synonyms.put("какие намерения", "какие планы");

        synonyms.put("о чем думаешь", "о чем думаешь");
        synonyms.put("что думаешь", "о чем думаешь");
        synonyms.put("какие мысли", "о чем думаешь");
        synonyms.put("что в голове", "о чем думаешь");

        synonyms.put("что нового", "что нового");
        synonyms.put("чего нового", "что нового");
        synonyms.put("какие новости", "что нового");
        synonyms.put("что интересного", "что нового");

        synonyms.put("как работа", "как работа");
        synonyms.put("как на работе", "как работа");
        synonyms.put("как дела на работе", "как работа");

        synonyms.put("как учеба", "как учеба");
        synonyms.put("как с учебой", "как учеба");
        synonyms.put("как в универе", "как учеба");
        synonyms.put("как в школе", "как учеба");

        synonyms.put("как семья", "как семья");
        synonyms.put("как дома", "как семья");
        synonyms.put("как родные", "как семья");
        synonyms.put("как родители", "как семья");

        synonyms.put("как здоровье", "как здоровье");
        synonyms.put("как самочувствие", "как здоровье");
        synonyms.put("как здоровьице", "как здоровье");

        // Проверяем синонимы
        for (String synonym : synonyms.keySet()) {
            if (userMessage.contains(synonym)) {
                String originalKeyword = synonyms.get(synonym);

                // Ищем оригинальный ключ в responsesMap
                for (String keyword : responsesMap.keySet()) {
                    if (keyword.toLowerCase().contains(originalKeyword)) {
                        ArrayList<String> responses = responsesMap.get(keyword);
                        if (responses != null && !responses.isEmpty()) {
                            Log.d("ChatActivity", "Найден синоним: " + synonym + " -> " + keyword);
                            return responses.get(0);
                        }
                    }
                }
            }
        }

        return null;
    }

    private void loadResponsesFromGitHub() {
        new Thread(() -> {
            try {
                URL url = new URL("https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/master/auto_responses_max.json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();

                // Добавляем отладочное сообщение о коде ответа
                runOnUiThread(() -> {
                    Message debugMessage = new Message(
                            "Код ответа сервера: " + responseCode,
                            getCurrentTime(),
                            false,
                            "Отладка",
                            R.drawable.accoun_oval
                    );
                    messagesList.add(debugMessage);
                    messageAdapter.notifyDataSetChanged();
                    scrollToBottom();
                });

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }

                    String jsonResponse = stringBuilder.toString();

                    // Добавляем отладочное сообщение о размере ответа
                    runOnUiThread(() -> {
                        Message sizeMessage = new Message(
                                "Получено данных: " + jsonResponse.length() + " символов",
                                getCurrentTime(),
                                false,
                                "Отладка",
                                R.drawable.accoun_oval
                        );
                        messagesList.add(sizeMessage);
                        messageAdapter.notifyDataSetChanged();
                        scrollToBottom();
                    });

                    parseJsonResponse(jsonResponse);

                } else {
                    runOnUiThread(() -> {
                        setupDefaultResponses();
                        Message errorMessage = new Message(
                                "Ошибка загрузки. Код: " + responseCode + ". Использую встроенные ответы.",
                                getCurrentTime(),
                                false,
                                "Авто-бот",
                                R.drawable.accoun_oval
                        );
                        messagesList.add(errorMessage);
                        messageAdapter.notifyDataSetChanged();
                        scrollToBottom();
                    });
                }

                connection.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    // Используем встроенные ответы если не удалось загрузить
                    setupDefaultResponses();
                    Message errorMessage = new Message(
                            "Использую встроенные ответы. Ошибка: " + e.getMessage(),
                            getCurrentTime(),
                            false,
                            "Авто-бот",
                            R.drawable.accoun_oval
                    );
                    messagesList.add(errorMessage);
                    messageAdapter.notifyDataSetChanged();
                    scrollToBottom();
                });
            }
        }).start();
    }

    private void parseJsonResponse(String jsonString) {
        try {
            JSONObject jsonObject = new JSONObject(jsonString);

            // Проверяем версию формата
            if (jsonObject.has("version")) {
                // Новый формат с категориями
                parseNewFormat(jsonObject);
            } else if (jsonObject.has("categories")) {
                // Альтернативная проверка на новый формат
                parseNewFormat(jsonObject);
            } else {
                // Старый формат - пробуем разные варианты
                parseOldFormat(jsonObject);
            }

        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> {
                setupDefaultResponses();
                Message errorMessage = new Message(
                        "Ошибка парсинга JSON: " + e.getMessage() + ". Использую встроенные ответы.",
                        getCurrentTime(),
                        false,
                        "Авто-бот",
                        R.drawable.accoun_oval
                );
                messagesList.add(errorMessage);
                messageAdapter.notifyDataSetChanged();
                scrollToBottom();
            });
        }
    }

    // Метод для парсинга нового формата с категориями
    private void parseNewFormat(JSONObject jsonObject) throws JSONException {
        int loadedCount = 0;

        if (jsonObject.has("categories")) {
            JSONArray categories = jsonObject.getJSONArray("categories");

            for (int i = 0; i < categories.length(); i++) {
                JSONObject category = categories.getJSONObject(i);
                JSONArray responses = category.getJSONArray("responses");

                for (int j = 0; j < responses.length(); j++) {
                    JSONObject responseObj = responses.getJSONObject(j);

                    // Извлекаем ключевое слово и ответ
                    String keyword = responseObj.getString("keyword");
                    String response = responseObj.getString("response");
                    boolean active = responseObj.optBoolean("active", true);

                    // Добавляем только активные ответы
                    if (active) {
                        ArrayList<String> responsesList = new ArrayList<>();
                        responsesList.add(response);

                        responsesMap.put(keyword.toLowerCase(), responsesList);
                        loadedCount++;

                        // Логируем загрузку для отладки
                        Log.d("ChatActivity", "Загружен ключ: " + keyword + " -> " + response);
                    }
                }
            }
        }

        showSuccessMessage(loadedCount, "ключевых слов");
    }

    // Метод для парсинга старого формата (простой массив)
    private void parseOldFormat(JSONObject jsonObject) throws JSONException {
        int loadedCount = 0;

        // Пробуем разные возможные ключи для старого формата
        String[] possibleArrayKeys = {"items", "questions", "responses", "data"};

        for (String key : possibleArrayKeys) {
            if (jsonObject.has(key)) {
                JSONArray jsonArray = jsonObject.getJSONArray(key);
                loadedCount = parseSimpleArray(jsonArray);
                break;
            }
        }

        // Если не нашли массива по ключам, пробуем как корневой массив
        if (loadedCount == 0) {
            try {
                // Пробуем интерпретировать JSONObject как массив
                JSONArray jsonArray = new JSONArray(jsonObject.toString());
                loadedCount = parseSimpleArray(jsonArray);
            } catch (JSONException e) {
                Log.e("ChatActivity", "Не удалось распарсить как массив: " + e.getMessage());
            }
        }

        showSuccessMessage(loadedCount, "вопросов");
    }

    // Метод для парсинга простого массива объектов
    private int parseSimpleArray(JSONArray jsonArray) throws JSONException {
        int loadedCount = 0;

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject item = jsonArray.getJSONObject(i);

            // Пробуем разные возможные ключи для вопроса и ответа
            String question = null;
            String answer = null;

            // Варианты ключей для вопроса
            String[] questionKeys = {"question", "keyword", "key", "text", "query"};
            String[] answerKeys = {"answer", "response", "value", "text"};

            // Ищем вопрос
            for (String key : questionKeys) {
                if (item.has(key)) {
                    question = item.getString(key);
                    break;
                }
            }

            // Ищем ответ
            for (String key : answerKeys) {
                if (item.has(key)) {
                    answer = item.getString(key);
                    break;
                }
            }

            // Если нашли и вопрос и ответ, добавляем
            if (question != null && answer != null) {
                ArrayList<String> responsesList = new ArrayList<>();
                responsesList.add(answer);
                responsesMap.put(question.toLowerCase(), responsesList);
                loadedCount++;

                Log.d("ChatActivity", "Загружен вопрос: " + question + " -> " + answer);
            }
        }

        return loadedCount;
    }

    // Обновленный метод для показа сообщения об успешной загрузке
    private void showSuccessMessage(int loadedCount, String type) {
        // Добавляем ответы по умолчанию
        setupDefaultResponses();

        final int finalLoadedCount = loadedCount;
        final String finalType = type;
        runOnUiThread(() -> {
            Message successMessage = new Message(
                    "Успешно загружено: " + finalLoadedCount + " " + finalType,
                    getCurrentTime(),
                    false,
                    "Авто-бот",
                    R.drawable.accoun_oval
            );
            messagesList.add(successMessage);

            // Показываем примеры
            if (!responsesMap.isEmpty()) {
                StringBuilder examples = new StringBuilder("Примеры: ");
                int count = 0;
                for (String key : responsesMap.keySet()) {
                    if (count < 5) { // Показываем только первые 5 примеров
                        examples.append("\n• ").append(key);
                        count++;
                    } else {
                        break;
                    }
                }

                Message examplesMessage = new Message(
                        examples.toString(),
                        getCurrentTime(),
                        false,
                        "Авто-бот",
                        R.drawable.accoun_oval
                );
                messagesList.add(examplesMessage);
            }

            messageAdapter.notifyDataSetChanged();
            scrollToBottom();
        });
    }

    private void setupDefaultResponses() {
        // Универсальные ответы по умолчанию
        defaultResponses.add("Извините, я не нашел подходящий ответ на ваш вопрос.");
        defaultResponses.add("Интересный вопрос! Можете переформулировать его?");
        defaultResponses.add("Пока не могу ответить на этот вопрос.");
        defaultResponses.add("Моя база знаний еще не содержит ответ на этот запрос.");
        defaultResponses.add("Попробуйте задать вопрос по-другому.");

        // Если responsesMap пустой, добавляем базовые ключевые слова
        if (responsesMap.isEmpty()) {
            ArrayList<String> greetings = new ArrayList<>();
            greetings.add("Привет! Как дела?");
            responsesMap.put("привет", greetings);

            ArrayList<String> howAreYou = new ArrayList<>();
            howAreYou.add("У меня всё отлично! Спасибо, что спросили!");
            responsesMap.put("как дела", howAreYou);

            ArrayList<String> thanks = new ArrayList<>();
            thanks.add("Пожалуйста! Обращайтесь!");
            responsesMap.put("спасибо", thanks);

            ArrayList<String> goodbye = new ArrayList<>();
            goodbye.add("До свидания! Хорошего дня!");
            responsesMap.put("пока", goodbye);
        }
    }

    private String getFallbackResponse(String userMessage) {
        String message = userMessage.toLowerCase();

        if (message.contains("привет")) {
            return "Привет! Рад вас видеть!";
        } else if (message.contains("как дела")) {
            return "Всё отлично! А у вас?";
        } else if (message.contains("спасибо")) {
            return "Пожалуйста! Обращайтесь!";
        } else if (message.contains("пока")) {
            return "До свидания! Хорошего дня!";
        } else if (message.contains("что делаешь")) {
            return "Общаюсь с вами! А вы?";
        } else {
            return "Извините, я не совсем понял вопрос. Можете переформулировать?";
        }
    }

    private void scrollToBottom() {
        messagesListView.post(() -> {
            messagesListView.setSelection(messageAdapter.getCount() - 1);
        });
    }

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date());
    }

    // Класс Message
    public static class Message {
        private String text;
        private String time;
        private boolean isMyMessage;
        private String senderName;
        private int avatarRes;

        public Message(String text, String time, boolean isMyMessage) {
            this.text = text;
            this.time = time;
            this.isMyMessage = isMyMessage;
            this.senderName = isMyMessage ? "Вы" : "Авто-бот";
            this.avatarRes = isMyMessage ? R.drawable.accoun_oval : R.drawable.accoun_oval;
        }

        public Message(String text, String time, boolean isMyMessage, String senderName, int avatarRes) {
            this.text = text;
            this.time = time;
            this.isMyMessage = isMyMessage;
            this.senderName = senderName;
            this.avatarRes = avatarRes;
        }

        // Геттеры
        public String getText() { return text; }
        public String getTime() { return time; }
        public boolean isMyMessage() { return isMyMessage; }
        public String getSenderName() { return senderName; }
        public int getAvatarRes() { return avatarRes; }
    }

    // Адаптер для сообщений
    public static class MessageAdapter extends android.widget.BaseAdapter {
        private ArrayList<Message> messages;
        private android.content.Context context;

        public MessageAdapter(android.content.Context context, ArrayList<Message> messages) {
            this.context = context;
            this.messages = messages;
        }

        @Override
        public int getCount() {
            return messages.size();
        }

        @Override
        public Object getItem(int position) {
            return messages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Message message = messages.get(position);

            if (convertView == null) {
                if (message.isMyMessage()) {
                    convertView = android.view.LayoutInflater.from(context).inflate(R.layout.item_message_my, parent, false);
                } else {
                    convertView = android.view.LayoutInflater.from(context).inflate(R.layout.item_message_other, parent, false);
                }
            }

            TextView messageText = convertView.findViewById(R.id.messageText);
            TextView messageTime = convertView.findViewById(R.id.messageTime);

            if (message != null) {
                messageText.setText(message.getText());
                messageTime.setText(message.getTime());
            }

            return convertView;
        }
    }
}