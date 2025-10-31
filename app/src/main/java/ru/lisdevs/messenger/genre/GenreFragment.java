package ru.lisdevs.messenger.genre;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.algoritms.AlgoritmPlaylistsFragment;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

public class GenreFragment extends Fragment {
    private static final String API_VERSION = "5.131";
    private String accessToken;
    private RecyclerView recyclerView;
    private GenreAdapter adapter;
    private List<Audio> audioList = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView genreSelectionText;
    private Toolbar toolbar;

    // ID жанров ВКонтакте
    private final Map<String, Integer> genres = new HashMap<String, Integer>() {{
        put("Рок", 1);
        put("Поп", 2);
        put("Рэп & хип-хоп", 3);
        put("Легкое прослушивание", 4);
        put("Танец & хаус", 5);
        put("Инструментальная", 6);
        put("Металл", 7);
        put("Дабстеп", 8);
        put("Джаз & блюз", 9);
        put("Драм & бас", 10);
        put("Транс", 11);
        put("Шансон", 12);
        put("Этническая", 13);
        put("Акустика & вокал", 14);
        put("Регги", 15);
        put("Классическая", 16);
        put("Инди-поп", 17);
        put("Другая", 18);
        put("Разговорная", 19);
        put("Альтернативная", 21);
        put("Электропоп & дискотека", 22);
    }};

    public static GenreFragment newInstance() {
        GenreFragment fragment = new GenreFragment();
        Bundle args = new Bundle();

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        accessToken = TokenManager.getInstance(requireContext()).getToken();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_genre, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        progressBar = view.findViewById(R.id.progressBar);
        recyclerView = view.findViewById(R.id.recyclerView);
        genreSelectionText = view.findViewById(R.id.genreSpinner);

        setupRecyclerView();
        setupGenreSelection();
        loadFirstGenreTracks();

        return view;
    }

    private void loadFirstGenreTracks() {
        List<String> genreNames = new ArrayList<>(genres.keySet());
        Collections.sort(genreNames);

        if (!genreNames.isEmpty()) {
            String firstGenre = genreNames.get(0);
            genreSelectionText.setText(firstGenre);
            int genreId = genres.get(firstGenre);
            fetchPopularByGenre(genreId);
        }
    }

    private void setupRecyclerView() {
        adapter = new GenreAdapter(audioList, audio -> {
            playAudio(audio);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupGenreSelection() {
        genreSelectionText.setOnClickListener(v -> showGenreBottomSheet());
    }

    private void showGenreBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_genres, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        RecyclerView genresRecyclerView = bottomSheetView.findViewById(R.id.genresRecyclerView);
        List<String> genreNames = new ArrayList<>(genres.keySet());
        Collections.sort(genreNames);

        GenreSelectionAdapter genreAdapter = new GenreSelectionAdapter(genreNames, genre -> {
            genreSelectionText.setText(genre);
            int genreId = genres.get(genre);
            fetchPopularByGenre(genreId);
            bottomSheetDialog.dismiss();
        });

        genresRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        genresRecyclerView.setAdapter(genreAdapter);

        bottomSheetDialog.show();
    }

    private void fetchPopularByGenre(int genreId) {
        progressBar.setVisibility(View.VISIBLE);
        audioList.clear();
        adapter.notifyDataSetChanged();

        OkHttpClient client = new OkHttpClient();

        String url = "https://api.vk.com/method/audio.getPopular" +
                "?access_token=" + accessToken +
                "&genre_id=" + genreId +
                "&count=100" +
                "&auto_complete=1" +
                "&v=" + API_VERSION;

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Ошибка загрузки", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("error")) {
                        handleApiError(json.getJSONObject("error"));
                        return;
                    }

                    JSONArray items = json.getJSONArray("response");
                    List<Audio> newAudios = new ArrayList<>();

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject audioObj = items.getJSONObject(i);
                        Audio audio = parseAudio(audioObj);
                        newAudios.add(audio);
                    }

                    requireActivity().runOnUiThread(() -> {
                        audioList.clear();
                        audioList.addAll(newAudios);
                        adapter.notifyDataSetChanged();
                        progressBar.setVisibility(View.GONE);
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private String getUserAgent() {
        if (isAuthViaAuthActivity()) {
            return "VKAndroidApp/1.0";
        } else {
            try {
                return Authorizer.getKateUserAgent();
            } catch (Exception e) {
                // Fallback на стандартный User-Agent
                return "VKAndroidApp/1.0";
            }
        }
    }

    private boolean isAuthViaAuthActivity() {
        // Проверка через SharedPreferences
        SharedPreferences prefs = requireContext().getSharedPreferences("auth_prefs", Context.MODE_PRIVATE);
        String authType = prefs.getString("auth_type", null);

        if (authType != null) {
            return "AuthActivity".equals(authType);
        }

        // По умолчанию возвращаем true для совместимости
        return true;
    }

    private Audio parseAudio(JSONObject audioObj) throws JSONException {
        Audio audio = new Audio();
        audio.id = audioObj.getLong("id");
        audio.ownerId = audioObj.getLong("owner_id");
        audio.artist = audioObj.getString("artist");
        audio.title = audioObj.getString("title");
        audio.duration = audioObj.getInt("duration");
        audio.url = audioObj.getString("url");
        audio.albumId = audioObj.optLong("album_id");
        audio.genreId = audioObj.optInt("genre_id");
        return audio;
    }

    private void handleApiError(JSONObject errorObj) {
        String errorMsg = errorObj.optString("error_msg", "Неизвестная ошибка");
        requireActivity().runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(getContext(), "Ошибка: " + errorMsg, Toast.LENGTH_SHORT).show();
        });
    }

    private void playAudio(Audio audio) {
        Intent playIntent = new Intent(requireContext(), MusicPlayerService.class);
        playIntent.setAction(MusicPlayerService.ACTION_PLAY);
        playIntent.putExtra("URL", audio.url);
        playIntent.putExtra("TITLE", audio.title);
        playIntent.putExtra("ARTIST", audio.artist);
        requireContext().startService(playIntent);
    }

    static class Audio {
        long id;
        long ownerId;
        String artist;
        String title;
        int duration;
        String url;
        long albumId;
        int genreId;
    }

    static class GenreAdapter extends RecyclerView.Adapter<GenreAdapter.ViewHolder> {
        private List<Audio> audios;
        private OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(Audio audio);
        }

        GenreAdapter(List<Audio> audios, OnItemClickListener listener) {
            this.audios = audios;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_audio, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Audio audio = audios.get(position);
            holder.artistText.setText(audio.artist);
            holder.titleText.setText(audio.title);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(audio);
                }
            });
        }

        @Override
        public int getItemCount() {
            return audios.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView artistText;
            TextView titleText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                artistText = itemView.findViewById(R.id.artistText);
                titleText = itemView.findViewById(R.id.titleText);
            }
        }
    }

    static class GenreSelectionAdapter extends RecyclerView.Adapter<GenreSelectionAdapter.ViewHolder> {
        private List<String> genres;
        private OnGenreClickListener listener;

        interface OnGenreClickListener {
            void onGenreClick(String genre);
        }

        GenreSelectionAdapter(List<String> genres, OnGenreClickListener listener) {
            this.genres = genres;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_genre_selection, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String genre = genres.get(position);
            holder.genreText.setText(genre);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGenreClick(genre);
                }
            });
        }

        @Override
        public int getItemCount() {
            return genres.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView genreText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                genreText = itemView.findViewById(R.id.genreText);
            }
        }
    }
}