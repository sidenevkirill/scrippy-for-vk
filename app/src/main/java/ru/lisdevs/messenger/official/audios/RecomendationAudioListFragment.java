package ru.lisdevs.messenger.official.audios;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.auth.AuthActivity;
import ru.lisdevs.messenger.player.PlayerBottomSheetFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;
import ru.lisdevs.messenger.utils.VkAuthorizer;

public class RecomendationAudioListFragment extends Fragment {

    private RecyclerView recyclerView;
    private ru.lisdevs.messenger.official.audios.AudioAdapter adapter;
    private List<ru.lisdevs.messenger.official.audios.Audio> audioList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView errorTextView;
    private Button retryButton;

    private TokenManager tokenManager;
    private OkHttpClient client;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_audio_list, container, false);
        initViews(view);
        initHttpClient();
        initTokenManager();
        setupRecyclerView();
        checkAuthAndLoadAudio();
        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        progressBar = view.findViewById(R.id.progressBar);
        errorTextView = view.findViewById(R.id.errorTextView);
        retryButton = view.findViewById(R.id.retryButton);

        swipeRefreshLayout.setOnRefreshListener(this::loadAudio);
        retryButton.setOnClickListener(v -> checkAuthAndLoadAudio());
    }

    private void initHttpClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private void initTokenManager() {
        tokenManager = TokenManager.getInstance(requireContext());
    }

    private void setupRecyclerView() {
        // Передаем контекст в адаптер
        adapter = new AudioAdapter(audioList, requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(position -> {
            ru.lisdevs.messenger.official.audios.Audio audio = audioList.get(position);
            playAudio(audio, position);
        });
    }

    private void checkAuthAndLoadAudio() {
        if (tokenManager.isTokenValid()) {
            loadAudio();
        } else {
            showErrorState("Требуется авторизация");
            navigateToAuth();
        }
    }

    private void loadAudio() {
        String accessToken = tokenManager.getToken();
        if (accessToken == null || accessToken.isEmpty()) {
            showErrorState("Токен не найден");
            return;
        }

        setLoadingState(true);
        hideErrorState();

        String url = "https://api.vk.com/method/audio.getRecommendations" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&count=200" +  // Количество треков для загрузки
                "&owner_id=" + tokenManager.getUserId();  // ID текущего пользователя

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", VkAuthorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    setLoadingState(false);
                    showErrorState("Ошибка сети: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";

                requireActivity().runOnUiThread(() -> {
                    setLoadingState(false);
                    swipeRefreshLayout.setRefreshing(false);

                    try {
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            if (responseObj.has("items")) {
                                JSONArray items = responseObj.getJSONArray("items");
                                List<ru.lisdevs.messenger.official.audios.Audio> newAudioList = parseAudioItems(items);
                                updateAudioList(newAudioList);
                            } else {
                                showErrorState("Нет аудиозаписей в ответе");
                            }
                        } else if (json.has("error")) {
                            handleApiError(json.getJSONObject("error"));
                        } else {
                            showErrorState("Неизвестный формат ответа");
                        }
                    } catch (JSONException e) {
                        showErrorState("Ошибка обработки данных");
                        Log.e("AudioListFragment", "JSON error: " + e.getMessage());
                    }
                });
            }
        });
    }

    private List<ru.lisdevs.messenger.official.audios.Audio> parseAudioItems(JSONArray items) throws JSONException {
        List<ru.lisdevs.messenger.official.audios.Audio> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);

            String artist = item.optString("artist", "Unknown Artist");
            String title = item.optString("title", "Unknown Title");
            String url = item.optString("url");
            int duration = item.optInt("duration", 0);
            int genreId = item.optInt("genre_id", 0);
            long id = item.optLong("id", 0);
            long ownerId = item.optLong("owner_id", 0);

            // Проверяем, что трек доступен для прослушивания
            if (url != null && !url.isEmpty() && !url.equals("null")) {
                ru.lisdevs.messenger.official.audios.Audio audio = new ru.lisdevs.messenger.official.audios.Audio(artist, title, url);
                audio.setDuration(duration);
                audio.setGenreId(genreId);
                audio.setAudioId(id);
                audio.setOwnerId(ownerId);
                result.add(audio);
            }
        }
        return result;
    }

    private void updateAudioList(List<ru.lisdevs.messenger.official.audios.Audio> newAudioList) {
        if (newAudioList.isEmpty()) {
            showErrorState("Нет доступных аудиозаписей");
            return;
        }

        audioList.clear();
        audioList.addAll(newAudioList);
        adapter.notifyDataSetChanged();
        showContentState();
    }

    private void handleApiError(JSONObject error) {
        int errorCode = error.optInt("error_code");
        String errorMsg = error.optString("error_msg");

        switch (errorCode) {
            case 5: // Invalid access token
                showErrorState("Ошибка авторизации");
                tokenManager.clearAuthData();
                navigateToAuth();
                break;
            case 6: // Too many requests
                showErrorState("Слишком много запросов. Попробуйте позже");
                break;
            case 15: // Access denied
                showErrorState("Нет доступа к аудиозаписям");
                break;
            default:
                showErrorState("Ошибка API: " + errorMsg);
        }
    }

    private void playAudio(ru.lisdevs.messenger.official.audios.Audio audio, int position) {
        if (audio.getUrl() == null || audio.getUrl().isEmpty()) {
            Toast.makeText(getContext(), "Трек недоступен для прослушивания", Toast.LENGTH_SHORT).show();
            return;
        }

        // Запуск сервиса воспроизведения
        Intent intent = new Intent(getContext(), MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_PLAY);
        intent.putExtra("AUDIO", audio);
        intent.putExtra("POSITION", position);

        ArrayList<ru.lisdevs.messenger.official.audios.Audio> playlist = new ArrayList<>(audioList);
        intent.putParcelableArrayListExtra("PLAYLIST", playlist);

        ContextCompat.startForegroundService(requireContext(), intent);

        // Показ плеера (если используете BottomSheet)
        showPlayerFragment(audio);
    }

    private void showPlayerFragment(Audio audio) {
        // Реализация показа плеера
        PlayerBottomSheetFragment playerFragment = new PlayerBottomSheetFragment();
        playerFragment.show(getParentFragmentManager(), "player");
    }

    private void navigateToAuth() {
        Intent intent = new Intent(getContext(), AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void setLoadingState(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(loading ? View.GONE : View.VISIBLE);
    }

    private void showErrorState(String message) {
        errorTextView.setText(message);
        errorTextView.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    private void hideErrorState() {
        errorTextView.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
    }

    private void showContentState() {
        recyclerView.setVisibility(View.VISIBLE);
        errorTextView.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }
}