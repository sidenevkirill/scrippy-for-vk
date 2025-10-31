package ru.lisdevs.messenger.music;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;

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
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import okhttp3.ResponseBody;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.playlists.PlaylistTracksFragment;
import ru.lisdevs.messenger.playlists.VkPlaylistsFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;

/**
 * Фрагмент для отображения списка музыки из VK и управления воспроизведением.
 */
public class MusicListFragment extends Fragment {

    private RecyclerView recyclerView;
    private AudioAdapter adapter;
    private List<Audio> audioList = new ArrayList<>();
    private List<Audio> fullAudioList;

    // Пагинация
    private int currentOffset = 0;
    private static final int PAGE_SIZE = 200;
    private boolean isLoading = false;
    private boolean hasMoreItems = true;

    // Элементы мини-плеера
    private LinearLayout miniPlayerContainer;
    private TextView currentTrackText;
    private Button playPauseButton, buttonNext, buttonPrev;
    private ImageView imageView;
    private TextView textViewResult;

    private int currentTrackIndex = -1;
    private boolean isShuffleEnabled = false;

    private MediaPlayer mediaPlayer;
    private Toolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;

    private OnMenuClickListener menuClickListener;
    private OnItemClickListener listener;

    // Жанры
    private final Map<String, Integer> genres = new HashMap<String, Integer>() {{
        put("Все жанры", 0);
        put("Rock", 1);
        put("Pop", 2);
        put("Rap & Hip-Hop", 3);
        put("Easy Listening", 4);
        put("Dance & House", 5);
        put("Instrumental", 6);
        put("Metal", 7);
        put("Dubstep", 8);
        put("Jazz & Blues", 9);
        put("Drum & Bass", 10);
        put("Trance", 11);
        put("Chanson", 12);
        put("Ethnic", 13);
        put("Acoustic & Vocal", 14);
        put("Reggae", 15);
        put("Classical", 16);
        put("Indie Pop", 17);
        put("Other", 18);
        put("Speech", 19);
        put("Alternative", 21);
        put("Electropop & Disco", 22);
    }};

    private int currentGenreId = 0; // текущий выбранный жанр (0 - все)

    public void setOnMenuClickListener(OnMenuClickListener listener) {
        this.menuClickListener = listener;
    }

    public interface OnMenuClickListener {
        void onMenuClick(Audio audio);
    }

    public interface OnItemClickListener {
        void onItemClick(Audio audio);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public interface OnMusicControlListener {
        void onPlayAudio(String url, String title);
        void onTogglePlayPause();
        void onNext();
        void onPrevious();
    }

    private OnMusicControlListener controlListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnMusicControlListener) {
            controlListener = (OnMusicControlListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnMusicControlListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_music, container, false);

        textViewResult = view.findViewById(R.id.count);
        getAudioCount();

        fullAudioList = new ArrayList<>();
        audioList = new ArrayList<>();

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            currentOffset = 0;
            hasMoreItems = true;
            SharedPreferences prefs = requireContext().getSharedPreferences("VK", Context.MODE_PRIVATE);
            String accessToken = prefs.getString("access_token", null);
            if (accessToken != null) {
                fetchAudio(accessToken, 0, false);
            } else {
                Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_LONG).show();
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        ImageView imageView1 = view.findViewById(R.id.shuffle);
        imageView1.setOnClickListener(v -> shuffleAudioList());

        ImageView sort = view.findViewById(R.id.sort);
        sort.setOnClickListener(v -> showGenreSelectionBottomSheet());

        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AudioAdapter(audioList);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if (!isLoading && hasMoreItems && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0 && totalItemCount >= PAGE_SIZE) {
                        loadMoreAudio();
                    }
                }
            }
        });

        adapter.setOnItemClickListener(position -> {
            if (position >= 0 && position < audioList.size()) {
                Audio selectedAudio = audioList.get(position);
                String url = selectedAudio.getUrl();

                if (url != null && !url.isEmpty()) {
                    Intent intent = new Intent(getContext(), MusicPlayerService.class);
                    intent.setAction("PLAY");
                    intent.putExtra("URL", url);
                    ContextCompat.startForegroundService(getContext(), intent);
                } else {
                    Toast.makeText(getContext(), "Нет доступного URL для этого трека", Toast.LENGTH_SHORT).show();
                }
            }
        });

        adapter.setOnMenuClickListener(audio -> showBottomSheet(audio));

        SharedPreferences prefs = requireContext().getSharedPreferences("VK", Context.MODE_PRIVATE);
        String accessToken = prefs.getString("access_token", null);
        if (accessToken != null) {
            fetchAudio(accessToken, 0, false);
        } else {
            Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_LONG).show();
        }

        toolbar = view.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_search);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_show_friends) {
                VkPlaylistsFragment groupsFragment = new VkPlaylistsFragment();
                FragmentManager fragmentManager = getParentFragmentManager();
                FragmentTransaction transaction = fragmentManager.beginTransaction();
                transaction.replace(R.id.container, groupsFragment);
                transaction.addToBackStack(null);
                transaction.commit();
                return true;
            }
            return false;
        });

        return view;
    }

    private void loadMoreAudio() {
        if (isLoading || !hasMoreItems) return;

        isLoading = true;
        currentOffset += PAGE_SIZE;

        SharedPreferences prefs = requireContext().getSharedPreferences("VK", Context.MODE_PRIVATE);
        String accessToken = prefs.getString("access_token", null);
        if (accessToken != null) {
            fetchAudio(accessToken, currentOffset, true);
        }
    }

    private void fetchAudio(String accessToken, int offset, boolean isLoadMore) {
        if (!isLoadMore && swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }

        String url = "https://api.vk.com/method/audio.get?access_token=" + accessToken +
                "&v=5.131&offset=" + offset + "&count=" + PAGE_SIZE;

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                isLoading = false;
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Ошибка загрузки музыки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (swipeRefreshLayout != null)
                        swipeRefreshLayout.setRefreshing(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                isLoading = false;
                String body = response.body().string();

                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(body);

                        if (jsonObject.has("error")) {
                            JSONObject errorObj = jsonObject.getJSONObject("error");
                            int errorCode = errorObj.optInt("error_code");
                            String errorMsg = errorObj.optString("error_msg");
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Ошибка API (" + errorCode + "): " + errorMsg, Toast.LENGTH_LONG).show();
                                    if (swipeRefreshLayout != null)
                                        swipeRefreshLayout.setRefreshing(false);
                                });
                            }
                            return;
                        }

                        if (jsonObject.has("response")) {
                            JSONObject responseObj = jsonObject.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");
                            List<Audio> tempList = new ArrayList<>();

                            for (int i = 0; i < items.length(); i++) {
                                JSONObject trackObj = items.getJSONObject(i);
                                String artist = trackObj.optString("artist");
                                String titleStr = trackObj.optString("title");
                                String urlAudio = trackObj.optString("url");
                                int genreId = trackObj.optInt("genre_id", 0);

                                Audio audio = new Audio(artist, titleStr, urlAudio);
                                audio.setGenreId(genreId);
                                tempList.add(audio);
                            }

                            int totalCount = responseObj.optInt("count", 0);
                            hasMoreItems = (offset + items.length()) < totalCount;

                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                if (!isLoadMore) {
                                    fullAudioList.clear();
                                    audioList.clear();
                                }

                                fullAudioList.addAll(tempList);
                                audioList.addAll(tempList);

                                adapter.notifyDataSetChanged();

                                if (swipeRefreshLayout != null)
                                    swipeRefreshLayout.setRefreshing(false);
                            });
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_LONG).show();
                                if (swipeRefreshLayout != null)
                                    swipeRefreshLayout.setRefreshing(false);
                            });
                        }
                    }
                }
            }
        });
    }

    private void shuffleAudioList() {
        if (audioList != null && !audioList.isEmpty()) {
            Collections.shuffle(audioList);
            adapter.notifyDataSetChanged();
        }
    }

    private void getAudioCount() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/audio.getCount")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("owner_id", TokenManager.getInstance(getContext()).getUserId())
                .addQueryParameter("v", "5.131")
                .build();

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            textViewResult.setText("Ошибка сети: " + e.getMessage())
                    );
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    ResponseBody responseBody = response.body();
                    if (responseBody != null) {
                        String bodyString = responseBody.string();
                        try {
                            JSONObject jsonObject = new JSONObject(bodyString);
                            Object responseObj = jsonObject.get("response");
                            if (responseObj instanceof JSONObject) {
                                int count = ((JSONObject) responseObj).getInt("count");
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() ->
                                            textViewResult.setText("Треки: " + count)
                                    );
                                }
                            } else if (responseObj instanceof Integer) {
                                int count = (Integer) responseObj;
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() ->
                                            textViewResult.setText("Треки: " + count)
                                    );
                                }
                            } else {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() ->
                                            textViewResult.setText("Неожиданный формат ответа")
                                    );
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() ->
                                        textViewResult.setText("Ошибка парсинга данных")
                                );
                            }
                        }
                    }
                }
            }
        });
    }

    private void showBottomSheet(Audio audio) {
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_audio, null);
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(getContext());
        bottomSheetDialog.setContentView(view);

        Button downloadBtn = view.findViewById(R.id.buttonDownload);
        downloadBtn.setOnClickListener(v ->{
            startDownload(getContext(), audio.getUrl(), audio.getArtist() + " - " + audio.getTitle() + ".mp3");
            bottomSheetDialog.dismiss();
        });


        Button goToAlbumBtn = view.findViewById(R.id.buttonAlbum);
        if (audio.getAlbumId() != 0 && audio.getOwnerId() != 0) {
            goToAlbumBtn.setVisibility(View.VISIBLE);
            goToAlbumBtn.setOnClickListener(v -> {
                navigateToAlbum(audio.getOwnerId(), audio.getAlbumId());
                bottomSheetDialog.dismiss();
            });
        } else {
            goToAlbumBtn.setVisibility(View.GONE);
        }

        bottomSheetDialog.show();
    }

    private void showGenreSelectionBottomSheet() {
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_genres, null);
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        bottomSheetDialog.setContentView(view);

        RecyclerView genresRecyclerView = view.findViewById(R.id.genresRecyclerView);
        genresRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        List<String> genreNames = new ArrayList<>(genres.keySet());
        Collections.sort(genreNames);

        GenreAdapter genreAdapter = new GenreAdapter(genreNames);
        genresRecyclerView.setAdapter(genreAdapter);

        genreAdapter.setOnItemClickListener(genreName -> {
            int genreId = genres.get(genreName);
            if (genreId != currentGenreId) {
                currentGenreId = genreId;
                filterByGenre(genreId);
            }
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void filterByGenre(int genreId) {
        if (genreId == 0) {
            audioList.clear();
            audioList.addAll(fullAudioList);
            adapter.notifyDataSetChanged();
            return;
        }

        List<Audio> filteredList = new ArrayList<>();
        for (Audio audio : fullAudioList) {
            if (audio.getGenreId() == genreId) {
                filteredList.add(audio);
            }
        }

        audioList.clear();
        audioList.addAll(filteredList);
        adapter.notifyDataSetChanged();
    }

    private void navigateToAlbum(long ownerId, int albumId) {
        PlaylistTracksFragment fragment = new PlaylistTracksFragment();
        Bundle args = new Bundle();
        args.putInt("playlist_id", albumId);
        args.putString("owner_id", String.valueOf(ownerId));
        args.putString("title", "Альбом");
        fragment.setArguments(args);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack("album_tracks")
                .commit();
    }

    static void startDownload(Context context, String url, String filename) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(url);

        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setTitle("Скачивание трека");
        request.setDescription(filename);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, filename);

        if (downloadManager != null) {
            downloadManager.enqueue(request);
            Toast.makeText(context, "Начато скачивание", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Не удалось начать скачивание", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_search, menu);
    }

    private static class GenreAdapter extends RecyclerView.Adapter<GenreAdapter.ViewHolder> {
        private List<String> genreNames;
        private OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(String genreName);
        }

        GenreAdapter(List<String> genreNames) {
            this.genreNames = genreNames;
        }

        void setOnItemClickListener(OnItemClickListener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_genre, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.genreName.setText(genreNames.get(position));
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(genreNames.get(position));
                }
            });
        }

        @Override
        public int getItemCount() {
            return genreNames.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView genreName;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                genreName = itemView.findViewById(R.id.genreName);
            }
        }
    }
}