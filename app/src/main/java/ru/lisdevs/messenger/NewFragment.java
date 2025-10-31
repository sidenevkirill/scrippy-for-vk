package ru.lisdevs.messenger;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.lisdevs.messenger.news.NewsAdapter;
import ru.lisdevs.messenger.model.NewsPost;
import ru.lisdevs.messenger.utils.TokenManager;

public class NewFragment extends Fragment {

    private RecyclerView recyclerView;
    private NewsAdapter adapter;
    private List<NewsPost> newsPosts = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_test, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new NewsAdapter(newsPosts);
        recyclerView.setAdapter(adapter);

        // Запускаем загрузку данных
        new LoadVKPostsWithAudioTask().execute();

        return view;
    }

    private class LoadVKPostsWithAudioTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            String userId = getUserId(getContext()); // ваш ID
            String accessToken = TokenManager.getInstance(getContext()).getToken(); // токен доступа
            String apiVersion = "5.131";

            Uri uri = Uri.parse("https://api.vk.com/method/wall.get")
                    .buildUpon()
                    //.appendQueryParameter("owner_id", userId)
                    .appendQueryParameter("owner_id", String.valueOf(-147845620))
                    .appendQueryParameter("count", "50") // число постов
                    .appendQueryParameter("v", apiVersion)
                    .appendQueryParameter("access_token", accessToken)
                    .build();

            String urlString = uri.toString();

            try {
                URL url = new URL(urlString);
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

                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }

                reader.close();
                return responseBuilder.toString();

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (result == null) {
                Toast.makeText(getContext(), "Ошибка при получении данных", Toast.LENGTH_LONG).show();
                return;
            }

            try {
                JSONObject jsonObject = new JSONObject(result);

                if (jsonObject.has("error")) {
                    JSONObject errorObj = jsonObject.getJSONObject("error");
                    String errorMsg = errorObj.optString("error_msg", "Неизвестная ошибка");
                    Toast.makeText(getContext(), "Ошибка API: " + errorMsg, Toast.LENGTH_LONG).show();
                    return;
                }

                JSONObject responseObject = jsonObject.getJSONObject("response");
                JSONArray itemsArray = responseObject.getJSONArray("items");

                newsPosts.clear(); // очищаем старые данные

                for (int i=0; i<itemsArray.length(); i++) {
                    JSONObject item = itemsArray.getJSONObject(i);

                    // Проверяем наличие вложений и фильтруем только с аудио
                    if (item.has("attachments")) {
                        JSONArray attachments = item.getJSONArray("attachments");
                        boolean hasAudio = false;

                        for (int j=0; j<attachments.length(); j++) {
                            JSONObject attachment = attachments.getJSONObject(j);
                            String type = attachment.optString("type");
                            if ("audio".equals(type)) {
                                hasAudio = true;
                                break;
                            }
                        }

                        if (!hasAudio) continue; // пропускаем пост без аудио
                    } else {
                        continue; // пропускаем пост без вложений
                    }

                    String postId = item.optString("post_id", "без ID");
                    String text = item.optString("text", "Нет текста");

                    long dateTimestamp = item.optLong("date", 0);
                    String dateStr;
                    if (dateTimestamp != 0) {
                        Date dateObj = new Date(dateTimestamp * 1000);
                        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                        dateStr= sdf.format(dateObj);
                    } else {
                        dateStr= "Нет даты";
                    }

                    newsPosts.add(new NewsPost(postId, text, dateStr));
                }

                adapter.notifyDataSetChanged();

            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Ошибка парсинга данных", Toast.LENGTH_LONG).show();
            }
        }
    }

    public String getUserId(Context context) {
        // Предполагается, что у вас есть сохранённый user_id в SharedPreferences
        return context.getSharedPreferences("VK", MODE_PRIVATE).getString("user_id", null);
    }
}