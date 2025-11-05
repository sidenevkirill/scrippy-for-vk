package ru.lisdevs.messenger.account;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import ru.lisdevs.messenger.model.PostItem;
import ru.lisdevs.messenger.news.PostsAdapter;
import ru.lisdevs.messenger.utils.TokenManager;

public class AcountPostsFragment extends Fragment {

    private RecyclerView recyclerView;
    private PostsAdapter adapter;
    private List<PostItem> newsPosts = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.my_account, container, false);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        if (getActivity() != null && getContext() != null) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                String userId = TokenManager.getInstance(getContext()).getUserId();
                activity.getSupportActionBar().setTitle("Мой аккаунт");
            }
        }



        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerView = view.findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PostsAdapter(newsPosts);
        recyclerView.setAdapter(adapter);

        // Настройка свайпа вниз для обновления
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadVKPostsWithAudio();
        });

        // Первоначальная загрузка
        loadVKPostsWithAudio();

        return view;
    }

    private void loadVKPostsWithAudio() {
        // Показываем индикатор обновления
        if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(true);
        }
        new LoadVKPostsWithAudioTask().execute();
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
                        "?owner_id=" + URLEncoder.encode(String.valueOf(userId), "UTF-8") +
                        "&count=20" +
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
                            groupName = getGroupNameById(groupIdStr);
                        } else {
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
}
