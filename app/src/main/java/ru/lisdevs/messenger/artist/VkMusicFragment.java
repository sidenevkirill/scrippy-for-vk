package ru.lisdevs.messenger.artist;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.music.Audio;
import ru.lisdevs.messenger.music.AudioAdapter;
import ru.lisdevs.messenger.utils.TokenManager;

public class VkMusicFragment extends Fragment {
    private static final String TAG = "VkMusicFragment";
    private AudioAdapter adapter;
    private ProgressBar progressBar;
    private EditText searchInput;
    private ImageView searchButton;
    private List<Audio> audioList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_album_search, container, false);

        // Инициализация UI
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        searchInput = view.findViewById(R.id.editTextSearch);
        searchButton = view.findViewById(R.id.buttonSearch);

        // Настройка адаптера
        adapter = new AudioAdapter(audioList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // Обработчики кликов
        adapter.setOnItemClickListener(position -> {
            Audio audio = audioList.get(position);
            // Воспроизведение трека
            playAudio(audio.getUrl());
        });

        adapter.setOnMenuClickListener(audio -> {
            // Обработка меню (скачивание)
            downloadAudio(audio);
        });

        searchButton.setOnClickListener(v -> {
            String query = searchInput.getText().toString().trim();
            if (!query.isEmpty()) {
                searchMusic(query);
            }
        });

        return view;
    }

    private void searchMusic(String artistName) {
        showLoading(true);
        audioList.clear();
        adapter.notifyDataSetChanged();

        // Шаг 1: Поиск ID артиста
        String searchUrl = "https://api.vk.com/method/audio.searchArtists" +
                "?access_token=" + getVkToken() +
                "&q=" + Uri.encode(artistName) +
                "&count=1" +
                "&v=5.131";

        JsonObjectRequest artistRequest = new JsonObjectRequest(
                Request.Method.GET, searchUrl, null,
                response -> {
                    try {
                        JSONArray artists = response.getJSONObject("response")
                                .getJSONArray("items");
                        if (artists.length() > 0) {
                            String artistId = artists.getJSONObject(0).getString("id");
                            getArtistTracks(artistId);
                        } else {
                            showError("Артист не найден");
                        }
                    } catch (JSONException e) {
                        showError("Ошибка обработки данных");
                    }
                },
                error -> showError(error.getMessage())
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", Authorizer.getKateUserAgent());
                return headers;
            }
        };

        Volley.newRequestQueue(requireContext()).add(artistRequest);
    }

    private void getArtistTracks(String artistId) {
        String tracksUrl = "https://api.vk.com/method/audio.getAudiosByArtist" +
                "?access_token=" + getVkToken() +
                "&artist_id=" + artistId +
                "&count=200" +
                "&v=5.131";

        JsonObjectRequest tracksRequest = new JsonObjectRequest(
                Request.Method.GET, tracksUrl, null,
                response -> {
                    try {
                        JSONArray tracks = response.getJSONObject("response")
                                .getJSONArray("items");
                        //parseTracks(tracks);
                        showLoading(false);
                    } catch (JSONException e) {
                        showError("Ошибка обработки треков");
                    }
                },
                error -> showError(error.getMessage())
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", Authorizer.getKateUserAgent());
                return headers;
            }
        };

        Volley.newRequestQueue(requireContext()).add(tracksRequest);
    }


    private void playAudio(String url) {
        // Реализация воспроизведения
    }

    private void downloadAudio(Audio audio) {
        // Реализация скачивания
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        showLoading(false);
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private String getVkToken() {
        // Ваша реализация получения токена
        return TokenManager.getInstance(getContext()).getToken();
    }
}