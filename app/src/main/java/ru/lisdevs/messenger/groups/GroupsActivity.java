package ru.lisdevs.messenger.groups;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;

public class GroupsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView statusTextView;

    // Ваш access_token
    private static final String ACCESS_TOKEN = "vk1.a.D3lRexpCUiJFcYjLkDj14susDbHFTB4poQ-5W8mUNrhNryqMoherhMqFvI8UEsJIhxqRcAFN672E828s_XnoRBYoxi44GltqNa8c_7Yi1yw20nADqhSR9LGHb0hEJnVR6FqQzHoXlvY8kQMdn5AqDglF-sNGf489r8vvEcoqqjhtM8Ci6BEnB4cBe-AQOQbWJ9UaOpAUkUKdYTEqMJfi3w";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_groups);

        recyclerView = findViewById(R.id.groupsRecyclerView);
        statusTextView = findViewById(R.id.statusTextView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Запускаем асинхронную задачу для получения групп
        new FetchGroupsTask().execute();
    }

    private class FetchGroupsTask extends AsyncTask<Void, Void, List<Long>> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            statusTextView.setText("Загрузка групп...");
        }

        @Override
        protected List<Long> doInBackground(Void... voids) {
            List<Long> groupIds = new ArrayList<>();
            String urlString = "https://api.vk.com/method/groups.get?access_token="
                    + ACCESS_TOKEN + "&v=5.131";

            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();

                BufferedReader in;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }

                StringBuilder responseStrBuilder = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    responseStrBuilder.append(inputLine);
                }
                in.close();

                JSONObject jsonResponse = new JSONObject(responseStrBuilder.toString());

                if (jsonResponse.has("response")) {
                    JSONObject responseObject = jsonResponse.getJSONObject("response");
                    if (responseObject.has("items")) {
                        JSONArray itemsArray = responseObject.getJSONArray("items");
                        for (int i=0; i<itemsArray.length(); i++) {
                            groupIds.add(itemsArray.getLong(i));
                        }
                    }
                } else if (jsonResponse.has("error")) {
                    JSONObject errorObj = jsonResponse.getJSONObject("error");
                    System.err.println("API Error: " + errorObj.optString("error_msg", "Unknown error"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return groupIds;
        }

        @Override
        protected void onPostExecute(List<Long> groupIds) {
            super.onPostExecute(groupIds);
            if (groupIds.isEmpty()) {
                statusTextView.setText("Группы не найдены или произошла ошибка.");
            } else {
                statusTextView.setVisibility(View.GONE);
             //   recyclerView.setAdapter(new GroupAdapter(groupIds));
            }
        }
    }
}