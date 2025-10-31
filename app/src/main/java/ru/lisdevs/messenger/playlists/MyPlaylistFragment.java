package ru.lisdevs.messenger.playlists;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
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
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.utils.TokenManager;

public class MyPlaylistFragment extends Fragment {

    private String friendIdStr; // ID друга в виде строки
    private RecyclerView recyclerView;
    private MyPlaylistAdapter adapter;
    private TextView textViewDetails;
    private List<PlaylistItem> playlistData = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout; // добавляем

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            long friendIdLong = getArguments().getLong("friend_id");
            friendIdStr = String.valueOf(friendIdLong);
        }
        // Получаем текущий user_id
        fetchCurrentUserId();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.recyclerview, container, false);

        textViewDetails = view.findViewById(R.id.statusTextView);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new MyPlaylistAdapter(playlistData, item -> {
            Bundle args = new Bundle();
            args.putInt("playlist_id", item.getId());

            Fragment fragment = new PlaylistDetailsFragment();
            fragment.setArguments(args);

            // Используйте getActivity() или requireActivity() для получения менеджера
            FragmentManager fm = requireActivity().getSupportFragmentManager();

            fm.beginTransaction()
                    .replace(R.id.container, fragment)
                    .addToBackStack(null)
                    .commit();
        });
        recyclerView.setAdapter(adapter);

        // Инициализация SwipeRefreshLayout
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchUserPlaylists(); // обновляем список при свайпе вниз
        });

        // Получение данных из аргументов
        Bundle args = getArguments();
        if (args != null) {
            long friendIdLong = args.getLong("friend_id");
            friendIdStr = String.valueOf(friendIdLong); // уже есть из onCreate

            String friendName = args.getString("friend_name");
            textViewDetails.setText("ID: " + friendIdStr + "\nИмя: " + friendName);
            textViewDetails.setTextColor(Color.BLACK);

            if (getActivity() instanceof AppCompatActivity && friendName != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(friendName);
            }

            fetchUserPlaylists(); // первоначальная загрузка
        } else {
            textViewDetails.setText("Информация не найдена");
        }

        return view;
    }

    /**
     * Метод для получения текущего user_id через VK API.
     * После получения обновляет значение friendIdStr и вызывает загрузку плейлистов.
     */
    private void fetchCurrentUserId() {
        OkHttpClient client = new OkHttpClient();

        String accessToken = TokenManager.getInstance(getContext()).getToken();

        String url = "https://api.vk.com/method/users.get" +
                "?access_token=" + accessToken +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Ошибка сети при получении user_id", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String respStr = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(respStr);
                        if (jsonObject.has("response")) {
                            JSONArray responseArray = jsonObject.getJSONArray("response");
                            if (responseArray.length() > 0) {
                                JSONObject userObj = responseArray.getJSONObject(0);
                                int userId = userObj.optInt("id");
                                // Обновляем friendIdStr на текущий user_id
                                getActivity().runOnUiThread(() -> {
                                    friendIdStr = String.valueOf(userId);
                                    // После обновления id можно вызвать загрузку плейлистов
                                    fetchUserPlaylists();
                                });
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка ответа сервера", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void fetchUserPlaylists() {
        final int totalToFetch = 100; // желаемое количество плейлистов
        final int pageSize = 50; // размер страницы, можно увеличить до 100, если API позволяет
        fetchPlaylistsPage(0, totalToFetch, pageSize);
    }

    private void fetchPlaylistsPage(int offset, int totalToFetch, int pageSize) {
        OkHttpClient client = new OkHttpClient();

        String accessToken = TokenManager.getInstance(getContext()).getToken();

        String url = "https://api.vk.com/method/audio.getPlaylists" +
                "?owner_id=" + friendIdStr +
                "&access_token=" + accessToken +
                "&v=5.131" +
                "&count=" + pageSize +
                "&offset=" + offset;

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Ошибка сети", Toast.LENGTH_SHORT).show();
                    swipeRefreshLayout.setRefreshing(false);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String respStr = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(respStr);
                        if (jsonObject.has("response")) {
                            JSONObject responseObj = jsonObject.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");

                            List<PlaylistItem> playlists = new ArrayList<>();
                            for (int i=0; i<items.length(); i++) {
                                JSONObject playlistJson = items.getJSONObject(i);
                                String artistName = playlistJson.optString("artist", "");
                                String coverUrl = playlistJson.optString("cover_url", "");
                                PlaylistItem playlist = new PlaylistItem(
                                        playlistJson.optInt("id"),
                                        playlistJson.optString("title"),
                                        playlistJson.optString("owner_id"),
                                        artistName,
                                        coverUrl
                                );
                                playlists.add(playlist);
                            }

                            getActivity().runOnUiThread(() -> {
                                // Добавляем новые плейлисты к существующим
                                playlistData.addAll(playlists);
                                adapter.notifyDataSetChanged();
                                swipeRefreshLayout.setRefreshing(false);

                                // Если собрали меньше запрошенного количества - значит всё загружено
                                if (playlistData.size() < totalToFetch && items.length() > 0) {
                                    // Загружаем следующую страницу
                                    fetchPlaylistsPage(offset + pageSize, totalToFetch, pageSize);
                                }
                            });
                        } else {
                            getActivity().runOnUiThread(() -> {
                                swipeRefreshLayout.setRefreshing(false);
                                Toast.makeText(getContext(), "Нет данных", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        getActivity().runOnUiThread(() -> {
                            swipeRefreshLayout.setRefreshing(false);
                            Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    getActivity().runOnUiThread(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), "Ошибка ответа сервера", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    public static class PlaylistItem {
        private int id;
        private String title;
        private String ownerId; // или owner_name
        private String artist;
        private String coverUrl; // новое поле

        public PlaylistItem(int id, String title, String ownerId, String artist, String coverUrl) {
            this.id = id;
            this.title = title;
            this.ownerId= ownerId;
            this.artist= artist;
            this.coverUrl = coverUrl;
        }

        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getOwnerId() { return ownerId; }
        public String getArtist() { return artist; }
        public String getCoverUrl() { return coverUrl; }
    }
}