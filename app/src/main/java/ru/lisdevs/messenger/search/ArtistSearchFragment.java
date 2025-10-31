package ru.lisdevs.messenger.search;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.artist.ArtistTracksFragment;
import ru.lisdevs.messenger.genre.GenreFragment;
import ru.lisdevs.messenger.utils.TokenManager;

public class ArtistSearchFragment extends Fragment {

    private RecyclerView recyclerView;
    private ArtistAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private AutoCompleteTextView searchInput;
    private ImageView searchButton;
    private List<String> searchHistory = new ArrayList<>();
    private SharedPreferences sharedPreferences;
    private String accessToken;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        accessToken = TokenManager.getInstance(requireContext()).getToken();
        sharedPreferences = requireContext().getSharedPreferences("ArtistSearchHistory", Context.MODE_PRIVATE);
        loadSearchHistory();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_artists_search, container, false);

        FloatingActionButton fabArtists = view.findViewById(R.id.fab_go_to_genre);
        fabArtists.setOnClickListener(v -> {
            try {
                Fragment genreFragment = new GenreFragment();
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.container, genreFragment)
                        .addToBackStack("genre")
                        .commit();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Ошибка перехода", Toast.LENGTH_SHORT).show();
            }
        });

        // Initialize views
        searchInput = view.findViewById(R.id.editTextSearch);
        searchButton = view.findViewById(R.id.buttonSearch);
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyView = view.findViewById(R.id.emptyView);

        // Setup search autocomplete
        ArrayAdapter<String> historyAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                searchHistory
        );
        searchInput.setAdapter(historyAdapter);
        searchInput.setThreshold(1);
        searchInput.setOnItemClickListener((parent, view1, position, id) -> {
            String query = (String) parent.getItemAtPosition(position);
            searchArtists(query);
        });

        // Search button click
        searchButton.setOnClickListener(v -> {
            String query = searchInput.getText().toString().trim();
            if (!query.isEmpty()) {
                searchArtists(query);
                addToSearchHistory(query);
            } else {
                showEmptyView("Введите запрос для поиска");
            }
        });

        // Handle keyboard search action
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = searchInput.getText().toString().trim();
                if (!query.isEmpty()) {
                    searchArtists(query);
                    addToSearchHistory(query);
                    return true;
                }
            }
            return false;
        });

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ArtistAdapter(new ArrayList<>(), artist -> {
            // Передаем ID исполнителя в другой фрагмент
            navigateToArtistTracks(artist);
        });
        recyclerView.setAdapter(adapter);

        // Back button
        FrameLayout backIcon = view.findViewById(R.id.buttonExit);
        backIcon.setOnClickListener(v -> requireActivity().onBackPressed());

        return view;
    }

    private void navigateToArtistTracks(VKArtist artist) {
        // Создаем фрагмент с передачей ID исполнителя
        Fragment fragment = ArtistTracksFragment.newInstance(
                artist.id,          // ID исполнителя
                artist.name,        // Имя исполнителя
                artist.photo        // Фото исполнителя
        );

        requireActivity().getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void loadSearchHistory() {
        String history = sharedPreferences.getString("artist_search_history", "");
        if (!history.isEmpty()) {
            searchHistory = new ArrayList<>(Arrays.asList(history.split(",")));
        }
    }

    private void addToSearchHistory(String query) {
        if (!searchHistory.contains(query)) {
            searchHistory.add(0, query);
            if (searchHistory.size() > 5) {
                searchHistory = searchHistory.subList(0, 5);
            }

            sharedPreferences.edit()
                    .putString("artist_search_history", TextUtils.join(",", searchHistory))
                    .apply();

            ArrayAdapter<String> adapter = (ArrayAdapter<String>) searchInput.getAdapter();
            adapter.clear();
            adapter.addAll(searchHistory);
            adapter.notifyDataSetChanged();
        }
    }

    private void searchArtists(String query) {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);

        showLoading();

        String encodedQuery;
        try {
            encodedQuery = URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            encodedQuery = query;
        }

        String url = "https://api.vk.com/method/audio.searchArtists" +
                "?access_token=" + accessToken +
                "&q=" + encodedQuery +
                "&count=50" +
                "&v=5.131"; // Обновлено до актуальной версии

        new OkHttpClient().newCall(new Request.Builder()
                        .url(url)
                        .header("User-Agent", Authorizer.getKateUserAgent())
                        .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        requireActivity().runOnUiThread(() ->
                                showEmptyView("Ошибка соединения: " + e.getMessage())
                        );
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            requireActivity().runOnUiThread(() ->
                                    showEmptyView("Ошибка сервера: " + response.code())
                            );
                            return;
                        }

                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("error")) {
                                JSONObject error = json.getJSONObject("error");
                                String errorMsg = error.getString("error_msg");
                                requireActivity().runOnUiThread(() -> showEmptyView("Ошибка API: " + errorMsg));
                                return;
                            }

                            JSONObject responseObj = json.getJSONObject("response");
                            List<VKArtist> artists = parseArtists(responseObj);
                            requireActivity().runOnUiThread(() -> {
                                if (artists.isEmpty()) {
                                    showEmptyView("Ничего не найдено");
                                } else {
                                    showResults(artists);
                                }
                            });

                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() ->
                                    showEmptyView("Ошибка обработки данных")
                            );
                        }
                    }
                });
    }

    private List<VKArtist> parseArtists(JSONObject response) throws JSONException {
        List<VKArtist> artists = new ArrayList<>();
        JSONArray items = response.getJSONArray("items");

        for (int i = 0; i < items.length(); i++) {
            JSONObject artistObj = items.getJSONObject(i);

            // Извлекаем ID исполнителя
            int artistId = artistObj.getInt("id");
            String artistName = artistObj.getString("name");
            String artistPhoto = artistObj.optString("photo", null);
            String artistDomain = artistObj.optString("domain", "");

            // Логируем для отладки
            Log.d("ArtistSearch", "Found artist: ID=" + artistId + ", Name=" + artistName);

            artists.add(new VKArtist(artistId, artistName, artistPhoto, artistDomain));
        }
        return artists;
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
    }

    private void showResults(List<VKArtist> artists) {
        progressBar.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        adapter.updateData(artists);
    }

    private void showEmptyView(String message) {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(message);
    }

    public static class VKArtist {
        public final int id;
        public final String name;
        public final String photo;
        public final String domain;

        public VKArtist(int id, String name, String photo, String domain) {
            this.id = id;
            this.name = name;
            this.photo = photo;
            this.domain = domain;
        }

        // Геттеры для удобства
        public int getId() { return id; }
        public String getName() { return name; }
        public String getPhoto() { return photo; }
        public String getDomain() { return domain; }
    }

    private static class ArtistAdapter extends RecyclerView.Adapter<ArtistAdapter.ViewHolder> {
        private List<VKArtist> artists;
        private final OnArtistClickListener listener;

        interface OnArtistClickListener {
            void onArtistClick(VKArtist artist);
        }

        ArtistAdapter(List<VKArtist> artists, OnArtistClickListener listener) {
            this.artists = artists;
            this.listener = listener;
        }

        void updateData(List<VKArtist> newArtists) {
            this.artists = newArtists;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_artist, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VKArtist artist = artists.get(position);
            holder.nameTextView.setText(artist.name);

            if (artist.photo != null && !artist.photo.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(artist.photo)
                        .placeholder(R.drawable.account_outline)
                        .circleCrop()
                        .into(holder.photoImageView);
            } else {
                holder.photoImageView.setImageResource(R.drawable.account_outline);
            }

            holder.itemView.setOnClickListener(v -> listener.onArtistClick(artist));
        }

        @Override
        public int getItemCount() {
            return artists.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView;
      //      TextView subtitleTextView; // Добавлено для отображения ID
            ImageView photoImageView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.artist_name);
                photoImageView = itemView.findViewById(R.id.artist_icon);

                // Если у вас есть TextView для подзаголовка
             //   subtitleTextView = itemView.findViewById(R.id.artist_subtitle);
            }
        }
    }
}