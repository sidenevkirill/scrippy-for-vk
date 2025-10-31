package ru.lisdevs.messenger.music;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicActivity extends AppCompatActivity {

    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<AudioItem> audioItems = new ArrayList<>();
    private MediaPlayer mediaPlayer = null;

    // Вставьте сюда свой ACCESS_TOKEN и USER_ID
    private static final String ACCESS_TOKEN = "vk1.a.6YDdX2DHqCdyzL8f_QCRV7rh14eMKe-lqz0A0GyPQBOU_Zmh3omZ-bJZ1I3SI-wdtbzgpFs1SMVLoFMYR5TluvN_SvrhoxBbS2FyavHo0RJAHevMRrxX4JY7WnzrFdBOCFIuA6B4OAzP4f6Y8PjfYkb4dkmBVPt2SonvBMi9QBiaR1xhNV3G1EBGqsSR2ZUhWUyWkX8Zf0mU_F866uDkPg";
    private static final String USER_ID = "613752664";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);

        listView = findViewById(R.id.listView);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);

        fetchAudio();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                playAudio(position);
            }
        });
    }

    private void fetchAudio() {
        String url = "https://api.vk.com/method/audio.get?owner_id=" + USER_ID +
                "&access_token=" + ACCESS_TOKEN +
                "&v=5.131";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MusicActivity.this, "Ошибка сети: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(MusicActivity.this, "Ошибка ответа сервера", Toast.LENGTH_LONG).show());
                    return;
                }
                String responseBody = response.body().string();
                Log.d("API_RESPONSE", responseBody); // Логируем ответ для отладки

                try {
                    JSONObject jsonObject = new JSONObject(responseBody);
                    JSONObject responseObj = jsonObject.getJSONObject("response");
                    JSONArray itemsArray = responseObj.getJSONArray("items");

                    audioItems.clear();
                    final List<String> titles = new ArrayList<>();

                    for (int i=0; i<itemsArray.length(); i++) {
                        JSONObject item = itemsArray.getJSONObject(i);
                        String artist = item.optString("artist", "Неизвестный исполнитель");
                        String titleStr = item.optString("title", "Без названия");
                        String urlStr = item.optString("url", null);

                        if (urlStr != null && !urlStr.isEmpty()) {
                            AudioItem audioItem = new AudioItem(artist + " - " + titleStr, urlStr);
                            audioItems.add(audioItem);
                            titles.add(audioItem.title);
                        }
                    }

                    runOnUiThread(() -> {
                        adapter.clear();
                        adapter.addAll(titles);
                        adapter.notifyDataSetChanged();
                    });

                } catch (JSONException e) {
                    runOnUiThread(() -> Toast.makeText(MusicActivity.this, "Ошибка парсинга: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    private void playAudio(int position) {
        if (position < 0 || position >= audioItems.size()) return;

        // Остановить текущий воспроизводимый трек
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        String url = audioItems.get(position).url;

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync(); // асинхронная подготовка
            mediaPlayer.setOnPreparedListener(mp -> mp.start());
            Toast.makeText(this, "Воспроизведение: " + audioItems.get(position).title, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Ошибка воспроизведения: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // Вспомогательный класс для хранения информации о треке
    private static class AudioItem {
        String title;
        String url;

        public AudioItem(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }
}