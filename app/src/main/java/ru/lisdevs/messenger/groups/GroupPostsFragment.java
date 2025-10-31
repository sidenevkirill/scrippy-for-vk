package ru.lisdevs.messenger.groups;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.news.AudioAttachment;
import ru.lisdevs.messenger.news.PostItem;
import ru.lisdevs.messenger.news.PostsAdapter;
import ru.lisdevs.messenger.utils.TokenManager;

public class GroupPostsFragment extends Fragment {

    private long groupId;
    private String groupName;
    private Toolbar toolbar;
    private Button subscriptionIcon; // иконка подписки
    private RecyclerView recyclerView;
    private PostsAdapter adapter;
    private List<PostItem> newsPosts = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;

    // Токен доступа ВК
    private String accessToken;
    private TextView resultTextView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Инициализация accessToken здесь, чтобы был контекст
        accessToken = TokenManager.getInstance(getContext()).getToken();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_group_details, container, false);

        //resultTextView = view.findViewById(R.id.text_result);
        //fetchPlaylists();

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerView = view.findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PostsAdapter(newsPosts);
        recyclerView.setAdapter(adapter);

        // Настройка свайпа вниз для обновления
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadVKPostsWithAudio();
        });

        // Получение аргументов
        if (getArguments() != null) {
            groupId = getArguments().getLong("group_id");
            groupName = getArguments().getString("group_name");
        }

        // Инициализация Toolbar
        toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null && getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                activity.getSupportActionBar().setTitle(groupName);
            }
        }

        // Инициализация UI элементов
        TextView idTextView = view.findViewById(R.id.textViewId);
        TextView nameTextView = view.findViewById(R.id.textViewName);
        //subscriptionIcon = view.findViewById(R.id.subscription);

        // Отображение ID
        String idDisplay = "@" + groupId;
        idTextView.setText(idDisplay);

        // Отображение названия группы
        nameTextView.setText(groupName);

        // Проверка подписки асинхронно
        new CheckSubscriptionTask().execute();

        // Обработка клика по иконке для смены статуса подписки
        subscriptionIcon.setOnClickListener(v -> {
            new ToggleSubscriptionTask().execute();
        });

        loadVKPostsWithAudio();

        return view;
    }

    private void updateSubscriptionIcon(boolean isSubscribed) {
        if (isSubscribed) {
            subscriptionIcon.setVisibility(View.VISIBLE);
            subscriptionIcon.setText("Вы подписаны");
            //subscriptionIcon.setImageResource(R.drawable.check_circle); // замените на нужное изображение
        } else {
            subscriptionIcon.setVisibility(View.VISIBLE); // или GONE, если не хотите показывать
            subscriptionIcon.setText("Подписаться");
            //    subscriptionIcon.setImageResource(R.drawable.plus_circle);
            // Или можно установить другое изображение для "неподписан"
            // subscriptionIcon.setImageResource(R.drawable.subscribe_icon);
        }
    }

    /**
     * AsyncTask для проверки подписки пользователя на группу.
     */
    private class CheckSubscriptionTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            return checkUserSubscriptionToGroup();
        }

        @Override
        protected void onPostExecute(Boolean isSubscribed) {
            updateSubscriptionIcon(isSubscribed);
            // Можно сохранить состояние в переменную, если нужно использовать позже
            this.isSubscribed = isSubscribed;
        }

        private boolean isSubscribed = false;  // поле для хранения состояния
    }

    /**
     * AsyncTask для переключения статуса подписки.
     */
    private class ToggleSubscriptionTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            boolean currentlySubscribed = checkUserSubscriptionToGroup();
            boolean result;
            if (currentlySubscribed) {
                result = unsubscribeFromGroup();
            } else {
                result = subscribeToGroup();
            }
            return result;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success != null && success) {
                Toast.makeText(getContext(),
                        checkUserSubscriptionToGroup() ? "Подписка оформлена" : "Отписка выполнена",
                        Toast.LENGTH_SHORT).show();
                updateSubscriptionIcon(checkUserSubscriptionToGroup());
            } else {
                Toast.makeText(getContext(), "Ошибка при изменении подписки", Toast.LENGTH_SHORT).show();
            }

            // Обновляем иконку в зависимости от текущего статуса
            new CheckSubscriptionTask().execute();
        }
    }

    /**
     * Метод для проверки подписки через VK API.
     */
    private boolean checkUserSubscriptionToGroup() {
        try {
            String urlString = "https://api.vk.com/method/groups.isMember?group_id=" + groupId +
                    "&access_token=" + accessToken +
                    "&v=5.131";

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();

            InputStream inputStream;
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = conn.getInputStream();
            } else {
                inputStream = conn.getErrorStream();
                // Можно дополнительно логировать ошибку или читать ответ для диагностики
                return false;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder responseBuilder = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) {
                responseBuilder.append(line);
            }
            in.close();

            String respStr = responseBuilder.toString();

            JSONObject jsonObject = new JSONObject(respStr);

            int responseValue = jsonObject.optJSONObject("response") != null ?
                    jsonObject.optJSONObject("response").optInt("response", 0)
                    : jsonObject.optInt("response", 0);

            // В API groups.isMember ответ содержит поле "response": 1 или 0.
            return responseValue == 1;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Метод для подписки через VK API.
     */
    private boolean subscribeToGroup() {
        try {
            String urlString = "https://api.vk.com/method/groups.join?group_id=" + groupId +
                    "&access_token=" + accessToken +
                    "&v=5.131";

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();

            InputStream inputStream;
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = conn.getInputStream();
            } else {
                inputStream = conn.getErrorStream();
                return false;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder responseBuilder = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) {
                responseBuilder.append(line);
            }
            in.close();

            String respStr = responseBuilder.toString();

            JSONObject jsonObject = new JSONObject(respStr);

            int responseValue = jsonObject.optJSONObject("response") != null ?
                    jsonObject.optJSONObject("response").optInt("response", 0)
                    : jsonObject.optInt("response", 0);

            return responseValue == 1;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Метод для отписки через VK API.
     */
    private boolean unsubscribeFromGroup() {
        try {
            String urlString = "https://api.vk.com/method/groups.leave?group_id=" + groupId +
                    "&access_token=" + accessToken +
                    "&v=5.131";

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();

            InputStream inputStream;
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = conn.getInputStream();
            } else {
                inputStream = conn.getErrorStream();
                return false;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder responseBuilder = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) {
                responseBuilder.append(line);
            }
            in.close();

            String respStr = responseBuilder.toString();

            JSONObject jsonObject = new JSONObject(respStr);

            int responseValue = jsonObject.optJSONObject("response") != null ?
                    jsonObject.optJSONObject("response").optInt("response", 0) :
                    jsonObject.optInt("response", 0);

            return responseValue == 1;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void loadVKPostsWithAudio() {
        // Показываем индикатор обновления
        if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(true);
        }
        new GroupPostsFragment.LoadVKPostsWithAudioTask().execute();
    }

    // Асинхронная задача для загрузки постов с аудио
    private class LoadVKPostsWithAudioTask extends AsyncTask<Void, Void, List<PostItem>> {

        @Override
        protected List<PostItem> doInBackground(Void... voids) {
            String userId = getUserId(getContext()); // ваш ID
            String accessToken = TokenManager.getInstance(getContext()).getToken(); // токен доступа
            String apiVersion = "5.131";



            try {
                URL url = new URL("https://api.vk.com/method/wall.get" +
                        "?owner_id=" + URLEncoder.encode(String.valueOf(-groupId), "UTF-8") +
                        //  "?owner_id=" + URLEncoder.encode(String.valueOf(groupId), "UTF-8") +

                        "&v=" + URLEncoder.encode(apiVersion, "UTF-8") +
                        "&access_token=" + URLEncoder.encode(accessToken, "UTF-8"));

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                int responseCode = connection.getResponseCode();

                InputStream inputStream;

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.getInputStream();
                } else {
                    inputStream = connection.getErrorStream();
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder responseBuilder = new StringBuilder();
                String line;

                while ((line=reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close();

                String responseStr= responseBuilder.toString();

                JSONObject jsonObject= new JSONObject(responseStr);

                if (jsonObject.has("error")) {
                    // Обработка ошибок API
                    return null;
                }

                JSONObject responseObj= jsonObject.getJSONObject("response");
                JSONArray itemsArray= responseObj.getJSONArray("items");

                List<PostItem> postItems= new ArrayList<>();

                for (int i=0; i<itemsArray.length(); i++) {
                    JSONObject item= itemsArray.getJSONObject(i);

                    List<AudioAttachment> audioList= new ArrayList<>();
                    if (item.has("attachments")) {
                        JSONArray attachments= item.getJSONArray("attachments");
                        for (int j=0; j<attachments.length(); j++) {
                            JSONObject attachment= attachments.getJSONObject(j);
                            if ("audio".equals(attachment.optString("type"))) {
                                JSONObject audioObj= attachment.optJSONObject("audio");
                                if (audioObj != null) {
                                    String artist= audioObj.optString("artist", "Неизвестный исполнитель");
                                    String title= audioObj.optString("title", "Без названия");
                                    String urlStr= audioObj.optString("url", "");
                                    audioList.add(new AudioAttachment(artist, title, urlStr));
                                }
                            }
                        }
                    }

                    String coverImageUrl = null;

                    // Предположим, что у вас есть вложения типа "photo" (фотографии)
                    if (item.has("attachments")) {
                        JSONArray attachments= item.getJSONArray("attachments");
                        for (int j=0; j<attachments.length(); j++) {
                            JSONObject attachment= attachments.getJSONObject(j);
                            if ("photo".equals(attachment.optString("type"))) {
                                JSONArray photoSizes= attachment.optJSONObject("photo").optJSONArray("sizes");
                                if (photoSizes != null && photoSizes.length() > 0) {
                                    // Можно выбрать самое большое изображение или первое
                                    JSONObject sizeObj= photoSizes.getJSONObject(photoSizes.length() - 1);
                                    coverImageUrl= sizeObj.optString("url");
                                    break; // если нужно только одно изображение
                                }
                            }
                        }
                    }

                    String groupName = null;
                    if (item.has("owner_id")) {
                        long ownerId = item.getLong("owner_id");
                        if (ownerId < 0) {
                            String groupIdStr = String.valueOf(-ownerId);
                            // Получить название группы по ID
                            groupName = getGroupNameById(groupIdStr);
                        } else {
                            // Это может быть личный профиль или что-то другое
                            // Можно оставить null или обработать отдельно
                        }
                    }

                    // Добавляем только если есть аудио
                    if (!audioList.isEmpty()) {
                        String postId= item.optString("post_id", "без ID");
                        String text= item.optString("text", "");
                        long dateTimestamp= item.optLong("date", 0);
                        String dateStr;

                        if (dateTimestamp != 0) {
                            Date dateObj= new Date(dateTimestamp*1000L);
                            SimpleDateFormat sdf= new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                            dateStr= sdf.format(dateObj);
                        } else {
                            dateStr="Нет даты";
                        }

                        PostItem postItem = new PostItem(postId, text, dateStr, audioList, coverImageUrl, groupName);
                        postItems.add(postItem);
                    }
                }

                return postItems;

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<PostItem> result) {
            super.onPostExecute(result);
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false); // скрываем индикатор обновления
            }
            if (result == null || result.isEmpty()) {
                Toast.makeText(getContext(), "Нет данных или ошибка загрузки", Toast.LENGTH_LONG).show();
                return;
            }
            newsPosts.clear();
            newsPosts.addAll(result);
            adapter.notifyDataSetChanged();
        }
    }

    private String getGroupNameById(String groupId) {
        // Можно реализовать через кеш или синхронный вызов
        // Для простоты — сделать синхронный вызов (или асинхронно и кешировать)
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        try {
            String urlStr = "https://api.vk.com/method/groups.getById" +
                    "?group_ids=" + URLEncoder.encode(groupId, "UTF-8") +
                    "&v=5.131&access_token=" + URLEncoder.encode(accessToken, "UTF-8");
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line=reader.readLine()) != null) {
                responseBuilder.append(line);
            }
            reader.close();

            JSONObject jsonResponse = new JSONObject(responseBuilder.toString());
            if (jsonResponse.has("response")) {
                JSONArray respArray = jsonResponse.getJSONArray("response");
                if (respArray.length() > 0) {
                    JSONObject groupObj = respArray.getJSONObject(0);
                    return groupObj.optString("name", "Неизвестная группа");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Неизвестная группа";
    }

    public String getUserId(Context context) {
        // Предполагается, что у вас есть сохранённый user_id в SharedPreferences
        return context.getSharedPreferences("VK", MODE_PRIVATE).getString("user_id", null);
    }

    public static GroupDetailsFragment newInstance(long groupId, String groupName) {
        GroupDetailsFragment fragment = new GroupDetailsFragment();
        Bundle args = new Bundle();
        args.putLong("group_id", groupId);
        args.putString("group_name", groupName);
        fragment.setArguments(args);
        return fragment;
    }
}
