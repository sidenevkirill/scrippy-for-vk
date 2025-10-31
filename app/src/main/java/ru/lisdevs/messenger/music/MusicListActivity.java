package ru.lisdevs.messenger.music;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;

public class MusicListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AudioAdapter adapter;
    private List<Audio> audioList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AudioAdapter(audioList);
        recyclerView.setAdapter(adapter);

        // Получите токен из SharedPreferences или передайте через Intent
        SharedPreferences prefs = getSharedPreferences("VK", MODE_PRIVATE);
        String accessToken = prefs.getString("access_token", null);

        if (accessToken != null) {
            fetchAudio(accessToken);
        } else {
            Toast.makeText(this, "Токен не найден", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void fetchAudio(String accessToken) {
        String url = "https://api.vk.com/method/audio.get?access_token=" + accessToken + "&v=5.131";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(MusicListActivity.this, "Ошибка загрузки музыки", Toast.LENGTH_LONG).show()
                );
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(body);
                        if (jsonObject.has("response")) {
                            JSONArray items = jsonObject.getJSONObject("response").getJSONArray("items");
                            List<Audio> tempList = new ArrayList<>();
                            for (int i=0; i<items.length(); i++) {
                                JSONObject trackObj = items.getJSONObject(i);
                                String artist = trackObj.optString("artist");
                                String title = trackObj.optString("title");
                                String urlAudio = trackObj.optString("url");
                                tempList.add(new Audio(artist, title, urlAudio));
                            }
                            runOnUiThread(() -> {
                                audioList.clear();
                                audioList.addAll(tempList);
                                adapter.notifyDataSetChanged();
                            });
                        } else if (jsonObject.has("error")) {
                            runOnUiThread(() ->
                                    {
                                        try {
                                            Toast.makeText(MusicListActivity.this, "Ошибка API: " + jsonObject.getJSONObject("error").optString("error_msg"), Toast.LENGTH_LONG).show();
                                        } catch (JSONException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                            );
                        }
                    } catch (JSONException e) {
                        runOnUiThread(() ->
                                Toast.makeText(MusicListActivity.this, "Ошибка парсинга", Toast.LENGTH_LONG).show()
                        );
                    }
                }
            }
        });
    }
}