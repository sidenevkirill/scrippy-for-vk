package ru.lisdevs.messenger.playlists;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.net.URLEncoder;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;

public class CreatePlaylist extends Fragment {

    private EditText editTextPlaylistName;
    private Button buttonCreatePlaylist;

    // ваши остальные переменные...

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create, container, false);

        // Инициализация элементов
        editTextPlaylistName = view.findViewById(R.id.editTextPlaylistName);
        buttonCreatePlaylist = view.findViewById(R.id.buttonCreatePlaylist);

        buttonCreatePlaylist.setOnClickListener(v -> {
            String playlistName = editTextPlaylistName.getText().toString().trim();
            if (!playlistName.isEmpty()) {
                // вызов метода создания плейлиста
                createVKPlaylist(playlistName);
            } else {
                Toast.makeText(getContext(), "Пожалуйста, введите название", Toast.LENGTH_SHORT).show();
            }
        });

        // остальной код...

        return view;
    }

    private void createVKPlaylist(String title) {
        String accessToken = "ВАШ_ТОКЕН"; // замените или получайте динамически
        String ownerId = "ВАШ_ID"; // ваш ID

        OkHttpClient client = new OkHttpClient();

        try {
            String encodedTitle = URLEncoder.encode(title, "UTF-8");
            String url = "https://api.vk.com/method/audio.createPlaylist" +
                    "?access_token=" + accessToken +
                    "&owner_id=" + ownerId +
                    "&title=" + encodedTitle +
                    "&v=5.95";

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }

                @Override public void onResponse(@NonNull okhttp3.Call call, @NonNull Response response) throws IOException {
                    String body = response.body().string();
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Ответ API: " + body, Toast.LENGTH_LONG).show()
                    );
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Ошибка при подготовке запроса", Toast.LENGTH_SHORT).show();
        }
    }
}
