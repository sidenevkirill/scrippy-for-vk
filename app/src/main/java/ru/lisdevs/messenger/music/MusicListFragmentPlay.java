package ru.lisdevs.messenger.music;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
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

public class MusicListFragmentPlay extends Fragment {

    private RecyclerView recyclerView;
    private AudioAdapter adapter;
    private List<Audio> audioList = new ArrayList<>();

    public MusicListFragmentPlay() {
        // Обязательный пустой конструктор
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_music, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AudioAdapter(audioList);
        recyclerView.setAdapter(adapter);

        // Устанавливаем обработчик кликов
        adapter.setOnItemClickListener(position -> {
            Audio selectedAudio = audioList.get(position);
            // Реализуйте проигрывание аудио по URL
            Toast.makeText(getContext(), "Проигрываем: " + selectedAudio.getTitle(), Toast.LENGTH_SHORT).show();
            playAudio(selectedAudio.getUrl());
        });

        // Получите токен из SharedPreferences
        SharedPreferences prefs = requireContext().getSharedPreferences("VK", Context.MODE_PRIVATE);
        String accessToken = prefs.getString("access_token", null);

        if (accessToken != null) {
            fetchAudio(accessToken);
        } else {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_LONG).show();
            // Можно закрыть фрагмент или предложить авторизацию
        }

        return view;
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
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Ошибка загрузки музыки", Toast.LENGTH_LONG).show()
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
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                audioList.clear();
                                audioList.addAll(tempList);
                                adapter.notifyDataSetChanged();
                            });
                        } else if (jsonObject.has("error")) {
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() ->
                                    {
                                        try {
                                            Toast.makeText(getContext(), "Ошибка API: " + jsonObject.getJSONObject("error").optString("error_msg"), Toast.LENGTH_LONG).show();
                                        } catch (JSONException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                            );
                        }
                    } catch (JSONException e) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "Ошибка парсинга", Toast.LENGTH_LONG).show()
                        );
                    }
                }
            }
        });
    }

    // Метод для проигрывания аудио по URL
    private void playAudio(String url) {
        // Здесь реализуйте запуск MediaPlayer или другого способа воспроизведения
        // Например:

        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepare(); // или prepareAsync() для асинхронной подготовки
            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Ошибка воспроизведения", Toast.LENGTH_SHORT).show();
        }


        // Для примера показываю только Toast:
        Toast.makeText(getContext(), "Запуск воспроизведения: " + url, Toast.LENGTH_SHORT).show();
    }
}