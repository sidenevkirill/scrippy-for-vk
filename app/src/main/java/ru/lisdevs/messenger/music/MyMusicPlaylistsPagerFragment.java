package ru.lisdevs.messenger.music;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.BaseActivity;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.local.SaveMusicFragment;
import ru.lisdevs.messenger.lyrics.LyricsBottomSheet;
import ru.lisdevs.messenger.playlists.VkPlaylistsFragment;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.TokenManager;

public class MyMusicPlaylistsPagerFragment extends Fragment {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private MusicPagerAdapter pagerAdapter;
    private Toolbar toolbar;
    private TextView textViewResult;
    private ImageView shuffleButton;
    private ImageView sortButton;
    private ImageView playButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pager, container, false);

        initViews(view);
        setupViewPager();
        setupTabLayout();
        setupToolbar();

        return view;
    }

    private void initViews(View view) {
        viewPager = view.findViewById(R.id.view_pager);
        tabLayout = view.findViewById(R.id.tabs);
        toolbar = view.findViewById(R.id.toolbar);
        textViewResult = view.findViewById(R.id.count);
        shuffleButton = view.findViewById(R.id.shuffle);
        sortButton = view.findViewById(R.id.sort);
        playButton = view.findViewById(R.id.add_to_playlist);

        shuffleButton.setOnClickListener(v -> shuffleAudioList());
        sortButton.setOnClickListener(v -> showSortDialog());
        playButton.setOnClickListener(v -> shufflePlayAudioList());
    }

    private void setupViewPager() {
        pagerAdapter = new MusicPagerAdapter(getChildFragmentManager(), getLifecycle());
        viewPager.setAdapter(pagerAdapter);
    }

    private void setupTabLayout() {
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Мои аудио");
                    break;
                case 1:
                    tab.setText("Рекомендации");
                    break;
            }
        }).attach();
    }

    private void setupToolbar() {
        toolbar.inflateMenu(R.menu.main_menu);

        MenuItem searchMenuItem = toolbar.getMenu().findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchMenuItem.getActionView();

        searchView.setQueryHint("Поиск по трекам");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterTracks(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    resetSearch();
                }
                return true;
            }
        });

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_search) {
                return true;
            } else if (item.getItemId() == R.id.action_show_friends) {
                navigateToFriends();
                return true;
            } else if (item.getItemId() == R.id.action_show_save) {
                navigateToSave();
                return true;
            }
            return false;
        });
    }

    private void shuffleAudioList() {
        BaseAudioFragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            currentFragment.shuffleAudioList();
        }
    }

    private void shufflePlayAudioList() {
        BaseAudioFragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            currentFragment.shufflePlayAudioList();
        }
    }

    private void showSortDialog() {
        BaseAudioFragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            currentFragment.showSortDialog();
        }
    }

    private void filterTracks(String query) {
        BaseAudioFragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            currentFragment.filterTracks(query);
        }
    }

    private void resetSearch() {
        BaseAudioFragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            currentFragment.resetSearch();
        }
    }

    private BaseAudioFragment getCurrentFragment() {
        if (pagerAdapter != null && viewPager != null) {
            try {
                Fragment fragment = pagerAdapter.createFragment(viewPager.getCurrentItem());
                if (fragment instanceof BaseAudioFragment) {
                    return (BaseAudioFragment) fragment;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void updateCountText(String text) {
        if (getActivity() != null && textViewResult != null) {
            getActivity().runOnUiThread(() -> textViewResult.setText(text));
        }
    }

    private void navigateToFriends() {
        VkPlaylistsFragment vkPlaylistsFragment = new VkPlaylistsFragment();
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, vkPlaylistsFragment)
                .addToBackStack("my_music")
                .commit();
    }

    private void navigateToSave() {
        SaveMusicFragment saveMusicFragment = new SaveMusicFragment();
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, saveMusicFragment)
                .addToBackStack("my_music")
                .commit();
    }

    // Базовый класс для фрагментов с аудио
    public static abstract class BaseAudioFragment extends Fragment {
        protected RecyclerView recyclerView;
        protected AudioAdapter adapter;
        protected List<Audio> audioList = new ArrayList<>();
        protected List<Audio> fullAudioList = new ArrayList<>();
        protected SwipeRefreshLayout swipeRefreshLayout;
        protected OkHttpClient okHttpClient;
        protected int totalCount = 0;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            okHttpClient = new OkHttpClient();
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            if (okHttpClient != null) {
                okHttpClient.dispatcher().cancelAll();
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.activity_friends, container, false);

            recyclerView = view.findViewById(R.id.friendsRecyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

            setupRecyclerView();
            setupSwipeRefresh();

            loadAudio();

            return view;
        }

        protected void setupRecyclerView() {
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new AudioAdapter(audioList);
            recyclerView.setAdapter(adapter);

            adapter.setOnItemClickListener(position -> {
                if (position >= 0 && position < audioList.size()) {
                    playTrack(position);
                }
            });

            adapter.setOnMenuClickListener(this::showBottomSheet);
        }

        protected void setupSwipeRefresh() {
            swipeRefreshLayout.setOnRefreshListener(this::loadAudio);
        }

        protected abstract void loadAudio();

        protected void playTrack(int position) {
            if (position < 0 || position >= audioList.size()) return;

            Audio audio = audioList.get(position);
            if (audio.getUrl() == null || audio.getUrl().isEmpty()) {
                Toast.makeText(getContext(), "Трек недоступен", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(getContext(), MusicPlayerService.class);
            intent.setAction("PLAY");
            intent.putExtra("URL", audio.getUrl());
            intent.putExtra("TITLE", audio.getTitle());
            intent.putExtra("ARTIST", audio.getArtist());
            intent.putExtra("DURATION", audio.getDuration());
            ContextCompat.startForegroundService(requireContext(), intent);
        }

        protected void shuffleAudioList() {
            if (!audioList.isEmpty()) {
                Collections.shuffle(audioList);
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                Toast.makeText(getContext(), "Треки перемешаны", Toast.LENGTH_SHORT).show();
                updateCountText();
            }
        }

        protected void shufflePlayAudioList() {
            if (audioList.isEmpty()) {
                Toast.makeText(getContext(), "Нет треков для воспроизведения", Toast.LENGTH_SHORT).show();
                return;
            }

            List<Audio> shuffledList = new ArrayList<>(audioList);
            Collections.shuffle(shuffledList);

            Audio randomAudio = shuffledList.get(0);
            playTrackDirectly(randomAudio);
            Toast.makeText(getContext(), "Воспроизведение случайного трека", Toast.LENGTH_SHORT).show();
        }

        protected void playTrackDirectly(Audio audio) {
            if (audio.getUrl() == null || audio.getUrl().isEmpty()) {
                Toast.makeText(getContext(), "Трек недоступен", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(getContext(), MusicPlayerService.class);
            intent.setAction("PLAY");
            intent.putExtra("URL", audio.getUrl());
            intent.putExtra("TITLE", audio.getTitle());
            intent.putExtra("ARTIST", audio.getArtist());
            intent.putExtra("DURATION", audio.getDuration());
            ContextCompat.startForegroundService(requireContext(), intent);
        }

        protected void showSortDialog() {
            CharSequence[] items = {
                    createItemWithIcon("По названию (А-Я)", R.drawable.sort_alphabetical_ascending),
                    createItemWithIcon("По исполнителю", R.drawable.sort_variant),
                    createItemWithIcon("По длительности", R.drawable.sort_clock_ascending_outline),
                    createItemWithIcon("По дате добавления", R.drawable.sort_calendar_ascending)
            };

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Сортировка треков")
                    .setItems(items, (dialog, which) -> {
                        handleSortSelection(which);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }

        private SpannableString createItemWithIcon(String text, @DrawableRes int iconRes) {
            SpannableString spannableString = new SpannableString("   " + text);
            Drawable icon = ContextCompat.getDrawable(requireContext(), iconRes);
            if (icon != null) {
                icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                ImageSpan imageSpan = new ImageSpan(icon, ImageSpan.ALIGN_BASELINE);
                spannableString.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spannableString;
        }

        private void handleSortSelection(int which) {
            switch (which) {
                case 0:
                    sortByTitle();
                    break;
                case 1:
                    sortByArtist();
                    break;
                case 2:
                    sortByDuration();
                    break;
                case 3:
                    break;
            }
        }

        protected void sortByTitle() {
            Collections.sort(audioList, (a1, a2) ->
                    a1.getTitle().compareToIgnoreCase(a2.getTitle()));
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }

        protected void sortByArtist() {
            Collections.sort(audioList, (a1, a2) ->
                    a1.getArtist().compareToIgnoreCase(a2.getArtist()));
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }

        protected void sortByDuration() {
            Collections.sort(audioList, (a1, a2) ->
                    Long.compare(a1.getDuration(), a2.getDuration()));
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }

        protected void filterTracks(String query) {
            List<Audio> filteredList = new ArrayList<>();
            String lowerCaseQuery = query.toLowerCase();

            for (Audio audio : fullAudioList) {
                if (audio.getTitle().toLowerCase().contains(lowerCaseQuery) ||
                        audio.getArtist().toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(audio);
                }
            }

            audioList.clear();
            audioList.addAll(filteredList);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updateCountText();

            // if (filteredList.isEmpty() && !query.isEmpty()) {
            //    Toast.makeText(getContext(), "Ничего не найдено", Toast.LENGTH_SHORT).show();
           // }
        }

        protected void resetSearch() {
            audioList.clear();
            audioList.addAll(fullAudioList);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updateCountText();
        }

        protected abstract void updateCountText();

        protected void showBottomSheet(Audio audio) {
            if (!isAdded() || getContext() == null) return;

            View view = getLayoutInflater().inflate(R.layout.bottom_sheet_audio, null);
            BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
            dialog.setContentView(view);

            view.findViewById(R.id.buttonSave).setOnClickListener(v -> {
                saveTrack(audio);
                dialog.dismiss();
            });

            view.findViewById(R.id.buttonDownload).setOnClickListener(v -> {
                downloadTrack(audio);
                dialog.dismiss();
            });

            view.findViewById(R.id.buttonAddToPlaylist).setOnClickListener(v -> {
                showAddToPlaylistDialog(audio);
                dialog.dismiss();
            });

            view.findViewById(R.id.buttonEdit).setOnClickListener(v -> {
                showEditBottomSheet(audio);
                dialog.dismiss();
            });

            view.findViewById(R.id.buttonSearch).setOnClickListener(v -> {
                searchArtist(audio.getArtist());
                dialog.dismiss();
            });

            view.findViewById(R.id.buttonAlbum).setOnClickListener(v -> {
                deleteTrack(audio);
                dialog.dismiss();
            });

            view.findViewById(R.id.buttonCopy).setOnClickListener(v -> {
                copyAudioLink(audio);
                dialog.dismiss();
            });

            view.findViewById(R.id.buttonShare).setOnClickListener(v -> {
                shareAudioLink(audio);
                dialog.dismiss();
            });

            view.findViewById(R.id.buttonOpenLyrics).setOnClickListener(v -> {
                if (audio.getLyricsId() > 0) {
                    showLyrics(audio);
                } else {
                    Toast.makeText(getContext(), "Текст песни недоступен", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            });

            dialog.show();
        }

        protected void saveTrack(Audio audio) {
            String fileName = audio.getArtist() + " - " + audio.getTitle() + ".mp3";
            fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "");

            DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(audio.getUrl()))
                            .setTitle(fileName)
                            .setDescription("Скачивание " + fileName)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setAllowedOverMetered(true)
                            .setAllowedOverRoaming(true);

                    dm.enqueue(request);
                    Toast.makeText(getContext(), "Скачивание начато: " + fileName, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getContext(), "Сервис скачивания недоступен", Toast.LENGTH_SHORT).show();
            }
        }

        protected void downloadTrack(Audio audio) {
            String fileName = audio.getArtist() + " - " + audio.getTitle() + ".mp3";
            DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(new DownloadManager.Request(Uri.parse(audio.getUrl()))
                        .setTitle(fileName)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED));
                Toast.makeText(getContext(), "Скачивание начато", Toast.LENGTH_SHORT).show();
            }
        }

        protected void showAddToPlaylistDialog(Audio audio) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Добавить в плейлист")
                    .setMessage("Выберите плейлист для добавления трека")
                    .setPositiveButton("Продолжить", (dialog, which) -> {})
                    .setNegativeButton("Отмена", null)
                    .show();
        }

        protected void showEditBottomSheet(Audio audio) {
            if (!isAdded() || getContext() == null) return;

            View view = getLayoutInflater().inflate(R.layout.dialog_edit_track, null);
            BottomSheetDialog editDialog = new BottomSheetDialog(requireContext());
            editDialog.setContentView(view);

            EditText editArtist = view.findViewById(R.id.editArtist);
            EditText editTitle = view.findViewById(R.id.editTitle);
            Button saveButton = view.findViewById(R.id.buttonSave);

            editArtist.setText(audio.getArtist());
            editTitle.setText(audio.getTitle());

            saveButton.setOnClickListener(v -> {
                String newArtist = editArtist.getText().toString().trim();
                String newTitle = editTitle.getText().toString().trim();

                if (!newArtist.isEmpty() && !newTitle.isEmpty()) {
                    editTrack(audio, newArtist, newTitle);
                    editDialog.dismiss();
                } else {
                    Toast.makeText(getContext(), "Заполните все поля", Toast.LENGTH_SHORT).show();
                }
            });

            editDialog.show();
        }

        protected void editTrack(Audio audio, String newArtist, String newTitle) {
            String accessToken = TokenManager.getInstance(getContext()).getToken();
            if (accessToken == null) {
                showToast("Токен не найден");
                return;
            }

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/audio.edit")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("owner_id", String.valueOf(audio.getOwnerId()))
                    .addQueryParameter("audio_id", String.valueOf(audio.getAudioId()))
                    .addQueryParameter("artist", newArtist)
                    .addQueryParameter("title", newTitle)
                    .addQueryParameter("v", "5.131")
                    .build();

            new OkHttpClient().newCall(new Request.Builder()
                            .url(url)
                            .header("User-Agent", Authorizer.getKateUserAgent())
                            .build())
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            showToast("Ошибка сети");
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            try {
                                JSONObject json = new JSONObject(response.body().string());
                                if (json.has("response")) {
                                    requireActivity().runOnUiThread(() -> {
                                        showToast("Трек успешно изменен");
                                        loadAudio();
                                    });
                                } else if (json.has("error")) {
                                    JSONObject error = json.getJSONObject("error");
                                    showToast("Ошибка: " + error.optString("error_msg"));
                                }
                            } catch (JSONException e) {
                                showToast("Ошибка обработки ответа");
                            }
                        }
                    });
        }

        protected void searchArtist(String artistName) {
            Intent searchIntent = new Intent(getActivity(), BaseActivity.class);
            searchIntent.putExtra("search_query", artistName);
            searchIntent.putExtra("fragment_to_load", "music_search");
            searchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(searchIntent);
        }

        protected void deleteTrack(Audio audio) {
            String accessToken = TokenManager.getInstance(getContext()).getToken();
            String url = "https://api.vk.com/method/audio.delete" +
                    "?access_token=" + accessToken +
                    "&owner_id=" + audio.getOwnerId() +
                    "&audio_id=" + audio.getAudioId() +
                    "&v=5.131";

            new OkHttpClient().newCall(new Request.Builder()
                            .url(url)
                            .header("User-Agent", Authorizer.getKateUserAgent())
                            .build())
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            requireActivity().runOnUiThread(() ->
                                    showToast("Ошибка сети: " + e.getMessage()));
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            try {
                                String responseBody = response.body().string();
                                JSONObject json = new JSONObject(responseBody);

                                if (json.has("response")) {
                                    int result = json.getInt("response");
                                    if (result == 1) {
                                        requireActivity().runOnUiThread(() -> {
                                            showToast("Трек удален");
                                            loadAudio();
                                        });
                                    } else {
                                        requireActivity().runOnUiThread(() ->
                                                showToast("Не удалось удалить трек"));
                                    }
                                } else if (json.has("error")) {
                                    JSONObject error = json.getJSONObject("error");
                                    String errorMsg = error.optString("error_msg", "Неизвестная ошибка");
                                    int errorCode = error.optInt("error_code", 0);

                                    requireActivity().runOnUiThread(() ->
                                            showToast("Ошибка (" + errorCode + "): " + errorMsg));
                                }
                            } catch (JSONException e) {
                                requireActivity().runOnUiThread(() ->
                                        showToast("Ошибка обработки ответа"));
                            } finally {
                                response.close();
                            }
                        }
                    });
        }

        protected void copyAudioLink(Audio audio) {
            String link = audio.getUrl();
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Audio Link", link);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Ссылка скопирована", Toast.LENGTH_SHORT).show();
        }

        protected void shareAudioLink(Audio audio) {
            String shareText = audio.getArtist() + " - " + audio.getTitle() + "\n\n" +
                    "Ссылка: " + audio.getUrl() + "\n\n" +
                    "Поделено через Моё Приложение";

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Послушай этот трек");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

            try {
                startActivity(Intent.createChooser(shareIntent, "Поделиться треком"));
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Ошибка при открытии sharing", Toast.LENGTH_SHORT).show();
            }
        }

        protected void showLyrics(Audio audio) {
            if (audio.getLyricsId() > 0) {
                LyricsBottomSheet bottomSheet = LyricsBottomSheet.newInstance(
                        audio.getLyricsId(),
                        audio.getTitle(),
                        audio.getArtist()
                );
                bottomSheet.show(getParentFragmentManager(), "lyrics_bottom_sheet");
            } else {
                showToast("Текст песни недоступен для этого трека");
            }
        }

        protected void showToast(String message) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
            }
        }

        protected List<Audio> parseAudioItems(JSONArray items) throws JSONException {
            List<Audio> result = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject track = items.getJSONObject(i);
                String artist = track.optString("artist", "Unknown Artist");
                String title = track.optString("title", "Unknown Title");
                String url = track.optString("url");
                int genreId = track.optInt("genre_id", 0);
                long ownerId = track.optLong("owner_id", 0);
                long audioId = track.optLong("id", 0);
                int lyricsId = track.optInt("lyrics_id", 0);
                int duration = track.optInt("duration", 0);

                if (url != null && !url.isEmpty()) {
                    Audio audio = new Audio(artist, title, url);
                    audio.setGenreId(genreId);
                    audio.setOwnerId(ownerId);
                    audio.setAudioId(audioId);
                    audio.setLyricsId(lyricsId);
                    audio.setDuration(duration);
                    result.add(audio);
                }
            }
            return result;
        }
    }

    // Фрагмент для моих аудио
    public static class MyAudioFragment extends BaseAudioFragment {
        private static final int PAGE_SIZE = 200;
        private int currentOffset = 0;
        private boolean isLoading = false;
        private boolean hasMoreItems = true;

        @Override
        protected void loadAudio() {
            currentOffset = 0;
            hasMoreItems = true;
            isLoading = true;
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(true);
            }

            String accessToken = TokenManager.getInstance(getContext()).getToken();
            if (accessToken != null) {
                fetchAudio(accessToken, 0, false);
            } else {
                Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_LONG).show();
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                isLoading = false;
            }
        }

        private void fetchAudio(String accessToken, int offset, boolean isLoadMore) {
            String url = "https://api.vk.com/method/audio.get" +
                    "?access_token=" + accessToken +
                    "&offset=" + offset +
                    "&count=" + PAGE_SIZE +
                    "&v=5.131";

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", Authorizer.getKateUserAgent())
                    .build();

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        isLoading = false;
                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                        Toast.makeText(getContext(), "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);

                        if (json.has("error")) {
                            requireActivity().runOnUiThread(() -> {
                                isLoading = false;
                                if (swipeRefreshLayout != null) {
                                    swipeRefreshLayout.setRefreshing(false);
                                }
                            });
                            return;
                        }

                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");
                            List<Audio> newList = parseAudioItems(items);

                            totalCount = responseObj.optInt("count", 0);
                            hasMoreItems = (offset + items.length()) < totalCount;
                            currentOffset = offset + items.length();

                            requireActivity().runOnUiThread(() -> {
                                isLoading = false;
                                if (swipeRefreshLayout != null) {
                                    swipeRefreshLayout.setRefreshing(false);
                                }

                                if (!isLoadMore) {
                                    fullAudioList.clear();
                                    audioList.clear();
                                }

                                fullAudioList.addAll(newList);
                                audioList.addAll(newList);

                                if (adapter != null) {
                                    adapter.notifyDataSetChanged();
                                }
                                updateCountText();
                            });
                        }
                    } catch (JSONException e) {
                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            if (swipeRefreshLayout != null) {
                                swipeRefreshLayout.setRefreshing(false);
                            }
                            Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_LONG).show();
                        });
                    }
                }
            });
        }

        @Override
        protected void updateCountText() {
            String text = "Мои треки: " + audioList.size() + " из " + totalCount;
            if (getParentFragment() instanceof MyMusicPlaylistsPagerFragment) {
                ((MyMusicPlaylistsPagerFragment) getParentFragment()).updateCountText(text);
            }
        }
    }

    // Фрагмент для рекомендаций
    public static class RecommendationsFragment extends BaseAudioFragment {

        @Override
        protected void loadAudio() {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(true);
            }
            String accessToken = TokenManager.getInstance(getContext()).getToken();

            if (accessToken != null) {
                fetchRecAudio(accessToken);
            } else {
                Toast.makeText(getContext(), "Токен не найден", Toast.LENGTH_LONG).show();
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        }

        private void fetchRecAudio(String accessToken) {
            String url = "https://api.vk.com/method/audio.getRecommendations?access_token=" + accessToken + "&v=5.131";

            new OkHttpClient().newCall(new Request.Builder()
                            .url(url)
                            .header("User-Agent", Authorizer.getKateUserAgent())
                            .build())
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                if (swipeRefreshLayout != null) {
                                    swipeRefreshLayout.setRefreshing(false);
                                }
                            });
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            try {
                                String body = response.body().string();
                                JSONObject json = new JSONObject(body);
                                if (json.has("response")) {
                                    JSONArray items = json.getJSONObject("response").getJSONArray("items");
                                    List<Audio> newList = parseAudioItems(items);
                                    requireActivity().runOnUiThread(() -> {
                                        fullAudioList.clear();
                                        audioList.clear();
                                        fullAudioList.addAll(newList);
                                        audioList.addAll(newList);
                                        if (adapter != null) {
                                            adapter.notifyDataSetChanged();
                                        }
                                        updateCountText();
                                        if (swipeRefreshLayout != null) {
                                            swipeRefreshLayout.setRefreshing(false);
                                        }
                                    });
                                } else if (json.has("error")) {
                                    showApiError(json.getJSONObject("error"));
                                    requireActivity().runOnUiThread(() -> {
                                        if (swipeRefreshLayout != null) {
                                            swipeRefreshLayout.setRefreshing(false);
                                        }
                                    });
                                }
                            } catch (JSONException e) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_LONG).show();
                                    if (swipeRefreshLayout != null) {
                                        swipeRefreshLayout.setRefreshing(false);
                                    }
                                });
                            }
                        }
                    });
        }

        private void showApiError(JSONObject error) {
            if (!isAdded() || getActivity() == null) return;

            int code = error.optInt("error_code");
            String msg = error.optString("error_msg");
            getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "Ошибка API (" + code + "): " + msg, Toast.LENGTH_LONG).show()
            );
        }

        @Override
        protected void updateCountText() {
            String text = "Рекомендации: " + audioList.size();
            if (getParentFragment() instanceof MyMusicPlaylistsPagerFragment) {
                ((MyMusicPlaylistsPagerFragment) getParentFragment()).updateCountText(text);
            }
        }
    }

    // Адаптер для ViewPager
    public static class MusicPagerAdapter extends FragmentStateAdapter {

        public MusicPagerAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
            super(fragmentManager, lifecycle);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new MyAudioFragment();
                case 1:
                    return new RecommendationsFragment();
                default:
                    return new MyAudioFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}