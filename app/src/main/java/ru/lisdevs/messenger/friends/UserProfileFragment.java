package ru.lisdevs.messenger.friends;


import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.gift.GiftsFragment;
import ru.lisdevs.messenger.dialog.DialogActivity;
import ru.lisdevs.messenger.official.audios.Audio;
import ru.lisdevs.messenger.player.PlayerBottomSheetFragment;
import ru.lisdevs.messenger.player.VideoPlayerActivity;
import ru.lisdevs.messenger.service.MusicPlayerService;
import ru.lisdevs.messenger.utils.Authorizer;
import ru.lisdevs.messenger.utils.TokenManager;
import ru.lisdevs.messenger.video.VideoItem;

public class UserProfileFragment extends Fragment {
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private ProfilePagerAdapter pagerAdapter;
    private Toolbar toolbar;
    private TextView textViewUserName, textViewUserId, textViewUserStatus;
    private ShapeableImageView imageViewUserAvatar;
    private ImageView checkVerif;
    private MaterialButton writeMessageButton;
    private MaterialButton friendActionButton;
    private long friendId;
    private String friendName;
    private OkHttpClient httpClient;
    private static final String API_VERSION = "5.131";
    private boolean isFriend = true;
    private ShapeableImageView toolbarAvatar;
    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbar;
    private MaterialButton friendsButton; // Кнопка для друзей
    private MaterialButton groupsButton;  // Кнопка для групп
    private MaterialButton musicButton;
    private MaterialButton friendsSumButton;
    private MaterialButton groupsSumButton;
    private MaterialButton musicSumButton;
    private MaterialButton followersSumButton;
    private MaterialButton menuButton;

    private int friendsCount = 0;
    private int groupsCount = 0;
    private int musicCount = 0;
    private int followersCount = 0;

    // URL для JSON файла со специальными пользователями
    private static final String DONATION_URL = "https://boosty.to/sidenyov.kirill/donate";
    private static final String SPECIAL_USERS_URL = "https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/special_donat_users.json";
    private Map<Long, String> specialUsersMap = new HashMap<>();
    private Map<Long, String> specialUserNamesMap = new HashMap<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        Bundle args = getArguments();
        if (args != null) {
            friendId = args.getLong("friend_id");
            friendName = args.getString("friend_name");
        }

        checkFriendshipStatus();
        loadSpecialUsers();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_user_new, container, false);

        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);
        toolbar = view.findViewById(R.id.toolbar);
        textViewUserName = view.findViewById(R.id.user_name);
        textViewUserStatus = view.findViewById(R.id.user_status);
        imageViewUserAvatar = view.findViewById(R.id.user_avatars);
        writeMessageButton = view.findViewById(R.id.download);
        friendActionButton = view.findViewById(R.id.save);
        checkVerif = view.findViewById(R.id.verified_icon);
        friendsButton = view.findViewById(R.id.friends_button);
        groupsButton = view.findViewById(R.id.groups_button);

        textViewUserId = view.findViewById(R.id.artistsNamesText);
        textViewUserId.setText("@id" + String.valueOf(friendId));

        friendsSumButton = view.findViewById(R.id.friends_sum);
        groupsSumButton = view.findViewById(R.id.groups_sum);
        musicSumButton = view.findViewById(R.id.music_sum);
        musicButton = view.findViewById(R.id.music);
        followersSumButton = view.findViewById(R.id.followers_sum);

        menuButton = view.findViewById(R.id.menu);
        menuButton.setOnClickListener(v -> showProfileBottomSheet());

        // Загружаем счетчики
        loadFriendsCount();
        loadGroupsCount();
        loadMusicCount();

        setupMusicButton();

        initAvatarAnimation(view);
        loadUserAvatar();

        setupToolbar();
        setupUserInfo();
        setupWriteMessageButton();
        setupFriendActionButton();
        setupViewPager();
        setupVerifiedIconClick();
        setupAvatarClickListener();
        setupFriendsButton();
        setupGroupsButton();
        loadFollowersCount();

        loadUserInfo();

        return view;
    }

    private void showProfileBottomSheet() {
        if (!isAdded()) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_vk_profile, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        TextView copyProfileLink = bottomSheetView.findViewById(R.id.copyButton);
        TextView copyUserId = bottomSheetView.findViewById(R.id.copyId);

        copyUserId.setOnClickListener(v -> {
            copyToClipboard("ID пользователя", String.valueOf(friendId));
            bottomSheetDialog.dismiss();
            showToast("ID скопирован");
        });

        copyProfileLink.setOnClickListener(v -> {
            copyToClipboard("Ссылка на профиль", "https://vk.com/id" + friendId);
            bottomSheetDialog.dismiss();
            showToast("Ссылка скопирована");
        });

        bottomSheetDialog.show();
    }

    private void copyToClipboard(String label, String text) {
        if (!isAdded()) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
    }

    private void showToast(String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }


    private void loadFriendsCount() {
        if (isTestAccount()) {
            friendsCount = 3; // Тестовое значение
            updateFriendsButtonText();
            return;
        }

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) return;

        String url = "https://api.vk.com/method/friends.get" +
                "?access_token=" + accessToken +
                "&user_id=" + friendId +
                "&count=0" + // Только счетчик, без списка друзей
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "VKAndroidApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                setDefaultFriendsCount();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);

                    if (json.has("response")) {
                        JSONObject responseObj = json.getJSONObject("response");
                        friendsCount = responseObj.optInt("count", 0);
                    } else if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        int errorCode = error.optInt("error_code", 0);

                        if (errorCode == 15) { // Доступ запрещен
                            friendsCount = -1; // Специальное значение для недоступного счетчика
                        } else {
                            setDefaultFriendsCount();
                        }
                    } else {
                        setDefaultFriendsCount();
                    }

                    requireActivity().runOnUiThread(() -> updateFriendsButtonText());

                } catch (JSONException e) {
                    setDefaultFriendsCount();
                    requireActivity().runOnUiThread(() -> updateFriendsButtonText());
                }
            }
        });
    }

    private void loadGroupsCount() {
        if (isTestAccount()) {
            groupsCount = 5; // Тестовое значение
            updateGroupsButtonText();
            return;
        }

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) return;

        String url = "https://api.vk.com/method/groups.get" +
                "?access_token=" + accessToken +
                "&user_id=" + friendId +
                "&extended=0" + // Только счетчик
                "&count=0" +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "VKAndroidApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                setDefaultGroupsCount();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);

                    if (json.has("response")) {
                        JSONObject responseObj = json.getJSONObject("response");
                        groupsCount = responseObj.optInt("count", 0);
                    } else if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        int errorCode = error.optInt("error_code", 0);

                        if (errorCode == 15 || errorCode == 18) { // Доступ запрещен или страница удалена
                            groupsCount = -1; // Специальное значение для недоступного счетчика
                        } else {
                            setDefaultGroupsCount();
                        }
                    } else {
                        setDefaultGroupsCount();
                    }

                    requireActivity().runOnUiThread(() -> updateGroupsButtonText());

                } catch (JSONException e) {
                    setDefaultGroupsCount();
                    requireActivity().runOnUiThread(() -> updateGroupsButtonText());
                }
            }
        });
    }

    private void setDefaultFriendsCount() {
        friendsCount = 0;
    }

    private void setDefaultGroupsCount() {
        groupsCount = 0;
    }

    private void setDefaultMusicCount() {
        musicCount = 0;
    }

    private void updateMusicButtonText() {
        if (musicSumButton == null) return;

        String text;
        if (musicCount == -1) {
            text = "Музыка\nзакрыта";
            musicSumButton.setAlpha(0.5f);
        } else if (musicCount == -2) {
            text = "Музыка\nзакрыта";
            musicSumButton.setAlpha(0.5f);
        } else if (musicCount == 0) {
            text = "Музыка\n0";
            musicSumButton.setAlpha(1f);
        } else {
            text = "Музыка\n" + formatCount(musicCount);
            musicSumButton.setAlpha(1f);
        }

        musicSumButton.setText(text);
    }

    private void loadMusicCount() {
        if (isTestAccount()) {
            musicCount = 8; // Тестовое значение
            updateMusicButtonText();
            return;
        }

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            setDefaultMusicCount();
            return;
        }

        String url = "https://api.vk.com/method/audio.get" +
                "?access_token=" + accessToken +
                "&owner_id=" + friendId +
                "&count=0" + // Только счетчик, без списка аудио
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {

                setDefaultMusicCount();
                requireActivity().runOnUiThread(() -> updateMusicButtonText());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);

                    if (json.has("response")) {
                        JSONObject responseObj = json.getJSONObject("response");
                        musicCount = responseObj.optInt("count", 0);

                    } else if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        int errorCode = error.optInt("error_code", 0);
                        String errorMsg = error.optString("error_msg", "");

                        if (errorCode == 15 || errorCode == 201) {
                            // 15 - доступ запрещен, 201 - доступ к аудио запрещен
                            musicCount = -1; // Специальное значение для недоступного счетчика
                        } else if (errorCode == 7) {
                            // 7 - разрешение denied (для текущего пользователя)
                            musicCount = -2; // Другое специальное значение
                        } else {
                            setDefaultMusicCount();
                        }
                    } else {
                        setDefaultMusicCount();
                    }

                    requireActivity().runOnUiThread(() -> updateMusicButtonText());

                } catch (JSONException e) {
                    setDefaultMusicCount();
                    requireActivity().runOnUiThread(() -> updateMusicButtonText());
                }
            }
        });
    }

    private String getUserAgent() {
        if (isAuthViaAuthActivity()) {
            return "VKAndroidApp/1.0";
        } else {
            try {
                return ru.lisdevs.messenger.api.Authorizer.getKateUserAgent();
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

    private void updateFriendsButtonText() {
        if (friendsSumButton == null) return;

        String text;
        if (friendsCount == -1) {
            text = "Друзья\nнедоступно";
        } else if (friendsCount == 0) {
            text = "Друзья\n0";
        } else {
            text = "Друзья\n" + formatCount(friendsCount);
        }

        friendsSumButton.setText(text);
    }

    private void updateGroupsButtonText() {
        if (groupsSumButton == null) return;

        String text;
        if (groupsCount == -1) {
            text = "Группы\nнедоступно";
        } else if (groupsCount == 0) {
            text = "Группы\n0";
        } else {
            text = "Группы\n" + formatCount(groupsCount);
        }

        groupsSumButton.setText(text);
    }

    private void updateFollowersButtonText() {
        if (followersSumButton == null) return;

        String text;
        if (followersCount == -1) {
            text = "Подписчик\nнедоступно";
            followersSumButton.setAlpha(0.5f);
        } else if (followersCount == 0) {
            text = "Подписчик\n0";
            followersSumButton.setAlpha(1f);
        } else {
            text = "Подписчик\n" + formatCount(followersCount);
            followersSumButton.setAlpha(1f);
        }

        followersSumButton.setText(text);
    }

    private void loadFollowersCount() {

        String accessToken = TokenManager.getInstance(requireContext()).getToken();

        String url = "https://api.vk.com/method/users.getFollowers" +
                "?access_token=" + accessToken +
                "&user_id=" + friendId +
                "&count=0" + // Только счетчик
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "VKAndroidApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {

                followersCount = 0;
                requireActivity().runOnUiThread(() -> updateFollowersButtonText());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);

                    if (json.has("response")) {
                        JSONObject responseObj = json.getJSONObject("response");
                        followersCount = responseObj.optInt("count", 0);

                    } else if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        int errorCode = error.optInt("error_code", 0);

                        if (errorCode == 15) { // Доступ запрещен
                            followersCount = -1;
                        } else {
                            followersCount = 0;
                        }
                    } else {
                        followersCount = 0;
                    }

                    requireActivity().runOnUiThread(() -> updateFollowersButtonText());

                } catch (JSONException e) {
                    followersCount = 0;
                    requireActivity().runOnUiThread(() -> updateFollowersButtonText());
                }
            }
        });
    }

    private String formatCount(int count) {
        if (count >= 1000000) {
            return String.format(Locale.getDefault(), "%.1fM", count / 1000000.0);
        } else if (count >= 1000) {
            return String.format(Locale.getDefault(), "%.1fK", count / 1000.0);
        } else {
            return String.valueOf(count);
        }
    }

    private void setupFriendsButton() {
        if (friendsSumButton != null) {
            friendsSumButton.setOnClickListener(v -> {
                openFriendsFragment();
            });
        }
    }

    private void setupGroupsButton() {
        if (groupsSumButton != null) {
            groupsSumButton.setOnClickListener(v -> {
                openGroupsFragment();
            });
        }
    }

    private void openFriendsFragment() {
        // Создаем фрагмент друзей
        FriendsTabFragment friendsFragment = FriendsTabFragment.newInstance(friendId, friendName);

        // Заменяем текущий фрагмент на фрагмент друзей
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, friendsFragment)
                .addToBackStack("friends_fragment")
                .commit();

        // Меняем заголовок тулбара
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle("Друзья " + friendName);
            }
        }
    }

    private void openGroupsFragment() {
        // Создаем фрагмент групп
        GroupsTabFragment groupsFragment = GroupsTabFragment.newInstance(friendId, friendName);

        // Заменяем текущий фрагмент на фрагмент групп
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, groupsFragment)
                .addToBackStack("groups_fragment")
                .commit();

        // Меняем заголовок тулбара
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle("Группы " + friendName);
            }
        }
    }


    private void setupMusicButton() {
        if (musicSumButton != null) {
            musicSumButton.setOnClickListener(v -> {
                openFriendAudiosFragment();
            });
        }
    }

    private void openFriendAudiosFragment() {
        // Создаем фрагмент с аудио друзей
        AudioTabFragment audioFragment = AudioTabFragment.newInstance(friendId, friendName);

        // Заменяем текущий фрагмент на фрагмент с аудио
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, audioFragment)
                .addToBackStack("audio_fragment")
                .commit();

        // Меняем заголовок тулбара
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle("Аудио " + friendName);
            }
        }
    }


    private void initAvatarAnimation(View view) {
        toolbarAvatar = view.findViewById(R.id.toolbar_avatar);
        appBarLayout = view.findViewById(R.id.appBarLayout);
        collapsingToolbar = view.findViewById(R.id.collapsingToolbar);

        // Слушатель для анимации аватарки
        appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                handleAvatarAnimation(verticalOffset);
            }
        });

        // Установите ту же аватарку в toolbar
        if (imageViewUserAvatar.getDrawable() != null) {
            toolbarAvatar.setImageDrawable(imageViewUserAvatar.getDrawable());
        }
    }

    private void handleAvatarAnimation(int verticalOffset) {
        // Максимальное смещение (когда toolbar полностью свернут)
        int maxOffset = appBarLayout.getTotalScrollRange();

        // Прогресс анимации (0 - развернут, 1 - свернут)
        float progress = Math.abs(verticalOffset) / (float) maxOffset;
        progress = Math.min(progress, 1f);

        // Анимация появления/исчезновения аватарки в toolbar
        if (progress > 0.3f) {
            // Показываем аватарку в toolbar
            toolbarAvatar.setVisibility(View.VISIBLE);
            float alpha = (progress - 0.3f) / 0.7f; // Плавное появление
            toolbarAvatar.setAlpha(alpha);

            // Одновременно скрываем большую аватарку
            if (imageViewUserAvatar != null) {
                float avatarAlpha = 1f - (progress - 0.3f) / 0.7f;
                imageViewUserAvatar.setAlpha(Math.max(avatarAlpha, 0f));
            }
        } else {
            // Скрываем аватарку в toolbar
            toolbarAvatar.setVisibility(View.INVISIBLE);
            toolbarAvatar.setAlpha(0f);

            // Показываем большую аватарку
            if (imageViewUserAvatar != null) {
                imageViewUserAvatar.setAlpha(1f);
            }
        }

        // Дополнительные анимации для улучшения эффекта
        animateContentOnScroll(progress);
    }

    private void animateContentOnScroll(float progress) {
        // Анимация для других элементов при скролле
        View linearLayout = getView().findViewById(R.id.linear);
        View playersArtist = getView().findViewById(R.id.players_artist);

        if (linearLayout != null && playersArtist != null) {
            if (progress > 0.5f) {
                // Плавное исчезновение контента при скролле
                float contentAlpha = 1f - (progress - 0.5f) / 0.5f;
                linearLayout.setAlpha(Math.max(contentAlpha, 0f));
                playersArtist.setAlpha(Math.max(contentAlpha, 0f));
            } else {
                linearLayout.setAlpha(1f);
                playersArtist.setAlpha(1f);
            }
        }
    }

    private void loadUserAvatar() {
        // Загрузите аватарку пользователя в оба ImageView
        String avatarUrl = "URL_аватарки_пользователя"; // Замените на реальный URL

        Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(imageViewUserAvatar);

        Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(imageViewUserAvatar);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_user_profile, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_qr_code) {
            showQRCodeDialog();
            return true;
        } else if (id == R.id.menu_share_profile) {
            shareProfile();
            return true;
        } else if (id == R.id.menu_copy_link) {
            copyProfileLink();
            return true;
        } else if (id == android.R.id.home) {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showQRCodeDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded);

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_qr_code, null);

        ImageView qrCodeImage = dialogView.findViewById(R.id.qrCodeImage);
        MaterialButton shareButton = dialogView.findViewById(R.id.shareQrButton);
        MaterialButton saveButton = dialogView.findViewById(R.id.saveQrButton);
        MaterialButton closeButton = dialogView.findViewById(R.id.closeQrButton);


        String profileUrl = "https://vk.com/id" + friendId;
        Bitmap qrCodeBitmap = generateQRCode(profileUrl);
        if (qrCodeBitmap != null) {
            qrCodeImage.setImageBitmap(qrCodeBitmap);
        } else {
            qrCodeImage.setImageResource(R.drawable.img);
            Toast.makeText(requireContext(), "Не удалось сгенерировать QR-код", Toast.LENGTH_SHORT).show();
        }

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        shareButton.setOnClickListener(v -> {
            shareQRCode(qrCodeBitmap);
            dialog.dismiss();
        });

        saveButton.setOnClickListener(v -> {
            saveQRCode(qrCodeBitmap);
            dialog.dismiss();
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private Bitmap generateQRCode(String text) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = new MultiFormatWriter().encode(
                    text,
                    BarcodeFormat.QR_CODE,
                    500,
                    500,
                    hints
            );

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            return bitmap;
        } catch (Exception e) {
            Log.e("QRCode", "Error generating QR code", e);
            return null;
        }
    }

    private void shareQRCode(Bitmap qrCodeBitmap) {
        if (qrCodeBitmap == null) {
            Toast.makeText(requireContext(), "QR-код не доступен для分享", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File cachePath = new File(requireContext().getCacheDir(), "images");
            cachePath.mkdirs();
            File file = new File(cachePath, "qr_code_" + friendId + ".png");
            FileOutputStream stream = new FileOutputStream(file);
            qrCodeBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "Профиль " + friendName + " в ВК: https://vk.com/id" + friendId);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Поделиться QR-кодом"));

        } catch (IOException e) {
            Log.e("QRCode", "Error sharing QR code", e);
            Toast.makeText(requireContext(), "Ошибка при分享 QR-кода", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveQRCode(Bitmap qrCodeBitmap) {
        if (qrCodeBitmap == null) {
            Toast.makeText(requireContext(), "QR-код не доступен для сохранения", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
            return;
        }

        String fileName = "QR_code_" + friendName + "_" + friendId + ".png";
        String savedImageURL = MediaStore.Images.Media.insertImage(
                requireContext().getContentResolver(),
                qrCodeBitmap,
                fileName,
                "QR-код профиля " + friendName
        );

        if (savedImageURL != null) {
            Toast.makeText(requireContext(), "QR-код сохранен в галерею", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "Ошибка сохранения QR-кода", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareProfile() {
        String shareText = "Профиль " + friendName + " в ВК: https://vk.com/id" + friendId;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Профиль " + friendName);

        startActivity(Intent.createChooser(shareIntent, "Поделиться профилем"));
    }

    private void copyProfileLink() {
        String profileLink = "https://vk.com/id" + friendId;

        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Профиль ВК", profileLink);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(requireContext(), "Ссылка скопирована", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            new Handler().postDelayed(() -> {
                Bitmap qrCode = generateQRCode("https://vk.com/id" + friendId);
                if (qrCode != null) {
                    saveQRCode(qrCode);
                }
            }, 500);
        } else {
            Toast.makeText(requireContext(), "Разрешение на сохранение отклонено", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupAvatarClickListener() {
        if (imageViewUserAvatar != null) {
            imageViewUserAvatar.setOnClickListener(v -> {
                if (isSpecialUser()) {
                    showSpecialUserDialog();
                } else {
                    showDefaultAvatarDialog();
                }
            });
        }
    }

    private void showSpecialUserDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded);
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_special_user, null);

        ImageView avatarImage = dialogView.findViewById(R.id.specialUserAvatar);
        TextView userNameText = dialogView.findViewById(R.id.specialUserName);
        TextView originalNameText = dialogView.findViewById(R.id.originalUserName);
        TextView messageText = dialogView.findViewById(R.id.specialUserMessage);
        MaterialButton okButton = dialogView.findViewById(R.id.okButton);

        String specialAvatarUrl = specialUsersMap.get(friendId);
        if (specialAvatarUrl != null && !specialAvatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(specialAvatarUrl)
                  //  .placeholder(R.drawable.img)
                    .error(R.drawable.img)
                    .circleCrop()
                    .into(avatarImage);
        }

        String specialName = specialUserNamesMap.get(friendId);
        if (specialName != null && !specialName.isEmpty()) {
            userNameText.setText(specialName);
            originalNameText.setText((friendName != null ? friendName : "Пользователь"));
            originalNameText.setVisibility(View.VISIBLE);
        } else {
            userNameText.setText(friendName != null ? friendName : "Пользователь");
            originalNameText.setVisibility(View.GONE);
        }

        messageText.setText("Этот пользователь поддержал развитие проекта!");

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        okButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showDefaultAvatarDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded);
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_default_avatar, null);

        ImageView avatarImage = dialogView.findViewById(R.id.defaultAvatar);
        TextView userNameText = dialogView.findViewById(R.id.defaultUserName);
        MaterialButton okButton = dialogView.findViewById(R.id.okButton);

        if (imageViewUserAvatar.getDrawable() != null) {
            avatarImage.setImageDrawable(imageViewUserAvatar.getDrawable());
        }

        userNameText.setText(friendName != null ? friendName : "Пользователь");

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        okButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void setupVerifiedIconClick() {
        if (checkVerif != null) {
            checkVerif.setOnClickListener(v -> {
                if (isSpecialUser()) {
                    showDonationDialog();
                }
            });
            checkVerif.setClickable(true);
        }
    }

    private void showDonationDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded);
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_donation, null);

        TextView titleText = dialogView.findViewById(R.id.dialogTitle);
        TextView messageText = dialogView.findViewById(R.id.dialogMessage);
        MaterialButton supportButton = dialogView.findViewById(R.id.supportButton);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancelButton);

        titleText.setText("Этот пользователь поддержал проект");

        String specialName = specialUserNamesMap.get(friendId);
        if (specialName != null && !specialName.isEmpty()) {
            messageText.setText(specialName + " поддержал(а) нас! Вы тоже можете помочь развитию проекта.");
        } else {
            messageText.setText("Этот пользователь поддержал нас! Вы тоже можете помочь развитию проекта.");
        }

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        supportButton.setOnClickListener(v -> {
            openDonationLink();
            dialog.dismiss();
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        supportButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.blue_500));
        supportButton.setTextColor(Color.WHITE);
        cancelButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white));
        cancelButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
    }

    private void openDonationLink() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(DONATION_URL));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show();
            Log.e("UserProfileFragment", "Error opening donation link", e);
        }
    }

    private void loadSpecialUsers() {
        Request request = new Request.Builder()
                .url(SPECIAL_USERS_URL)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("UserProfileFragment", "Failed to load special users", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    parseSpecialUsers(responseBody);
                }
            }
        });
    }

    private void parseSpecialUsers(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            JSONArray usersArray = json.getJSONArray("special_users");

            specialUsersMap.clear();
            specialUserNamesMap.clear();

            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject user = usersArray.getJSONObject(i);
                long userId = user.getLong("id");
                String avatarUrl = user.optString("avatar_url", "");
                String userName = user.optString("name", "");

                if (!avatarUrl.isEmpty()) {
                    specialUsersMap.put(userId, avatarUrl);
                }

                if (!userName.isEmpty()) {
                    specialUserNamesMap.put(userId, userName);
                }
            }

            Log.d("UserProfileFragment", "Loaded " + specialUsersMap.size() + " special users");
            Log.d("UserProfileFragment", "Loaded " + specialUserNamesMap.size() + " special names");

            if (specialUsersMap.containsKey(friendId) && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        loadSpecialUserAvatar();
                    }
                });
            }

        } catch (JSONException e) {
            Log.e("UserProfileFragment", "Error parsing special users JSON", e);
        }
    }

    private void loadSpecialUserAvatar() {
        String specialAvatarUrl = specialUsersMap.get(friendId);
        if (specialAvatarUrl != null && !specialAvatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(specialAvatarUrl)
                    .placeholder(R.drawable.img)
                    .error(R.drawable.img)
                    .circleCrop()
                    .into(checkVerif);
            Log.d("UserProfileFragment", "Loaded special avatar for user: " + friendId);
        }
    }

    private boolean isSpecialUser() {
        return specialUsersMap.containsKey(friendId);
    }

    private void loadUserInfo() {
        if (isTestAccount()) {
            setupTestUserInfo();
            return;
        }

        if (isSpecialUser()) {
            loadSpecialUserAvatar();
        }

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) return;

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/users.get")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("user_ids", String.valueOf(friendId))
                .addQueryParameter("fields", "photo_200,online,last_seen,status")
                .addQueryParameter("v", API_VERSION)
                .build();

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("UserProfileFragment", "Failed to load user info", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (response.isSuccessful() && isAdded()) {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("response")) {
                            JSONArray users = json.getJSONArray("response");
                            if (users.length() > 0) {
                                JSONObject user = users.getJSONObject(0);
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        if (isAdded()) {
                                            updateUserInfo(user);
                                        }
                                    });
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("UserProfileFragment", "Error parsing user info", e);
                }
            }
        });
    }

    private void updateUserInfo(JSONObject user) {
        try {
            boolean isOnline = user.getInt("online") == 1;
            String statusText;

            if (isOnline) {
                statusText = "в сети";
                textViewUserStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_500));
            } else {
                if (user.has("last_seen")) {
                    JSONObject lastSeen = user.getJSONObject("last_seen");
                    long lastSeenTime = lastSeen.getLong("time") * 1000; // Convert to milliseconds
                    statusText = "был(а) " + formatLastSeenTime(lastSeenTime);
                } else {
                    statusText = "не в сети";
                }
                textViewUserStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
            }

            textViewUserStatus.setText(statusText);
            textViewUserStatus.setVisibility(View.VISIBLE);

            if (user.has("photo_200")) {
                String avatarUrl = user.getString("photo_200");
                if (avatarUrl != null && !avatarUrl.isEmpty() && !avatarUrl.equals("null")) {
                    Glide.with(this)
                            .load(avatarUrl)
                            .placeholder(R.drawable.img)
                            .error(R.drawable.img)
                            .circleCrop()
                            .into(imageViewUserAvatar);
                }
            }

            String firstName = user.getString("first_name");
            String lastName = user.getString("last_name");
            textViewUserName.setText(firstName + " " + lastName);

            if (user.has("status") && !user.isNull("status")) {
                String userStatus = user.getString("status");
                // Можно добавить отображение текстового статуса
            }

            if (isSpecialUser()) {
                textViewUserName.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
            }

        } catch (JSONException e) {
            Log.e("UserProfileFragment", "Error updating user info", e);
        }
    }

    private String formatLastSeenTime(long timestamp) {
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) {
            return "только что";
        } else if (minutes < 60) {
            return formatTimeAgo(minutes, "минуту", "минуты", "минут");
        } else if (hours < 24) {
            return formatTimeAgo(hours, "час", "часа", "часов");
        } else if (days < 7) {
            return formatTimeAgo(days, "день", "дня", "дней");
        } else {
            // Для времени больше недели показываем конкретную дату
            return formatExactDate(timestamp);
        }
    }

    private String formatTimeAgo(long value, String singular, String few, String many) {
        if (value == 1) {
            return value + " " + singular + " назад";
        } else if (value >= 2 && value <= 4) {
            return value + " " + few + " назад";
        } else {
            return value + " " + many + " назад";
        }
    }

    private String formatExactDate(long timestamp) {
        Date date = new Date(timestamp);
        Date currentDate = new Date();

        // Если в этом году, показываем без года
        SimpleDateFormat currentYearFormat = new SimpleDateFormat("d MMMM", new Locale("ru"));
        SimpleDateFormat otherYearFormat = new SimpleDateFormat("d MMMM yyyy", new Locale("ru"));

        Calendar calDate = Calendar.getInstance();
        calDate.setTime(date);
        Calendar calCurrent = Calendar.getInstance();
        calCurrent.setTime(currentDate);

        if (calDate.get(Calendar.YEAR) == calCurrent.get(Calendar.YEAR)) {
            return currentYearFormat.format(date);
        } else {
            return otherYearFormat.format(date);
        }
    }

    private void setupTestUserInfo() {
        textViewUserStatus.setText("В сети");
        textViewUserStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_500));
        textViewUserStatus.setVisibility(View.VISIBLE);

        if (isSpecialUser()) {
            loadSpecialUserAvatar();
        } else {
            Glide.with(this)
                    .load("https://via.placeholder.com/200x200/4ECDC4/FFFFFF?text=" + (friendName != null && !friendName.isEmpty() ? friendName.charAt(0) : "U"))
                    .placeholder(R.drawable.img)
                    .error(R.drawable.img)
                    .circleCrop()
                    .into(imageViewUserAvatar);
        }
    }

    private void setupFriendActionButton() {
        if (friendActionButton != null) {
            updateFriendActionButton();

            friendActionButton.setOnClickListener(v -> {
                if (isFriend) {
                    removeFromFriends();
                } else {
                    addToFriends();
                }
            });

            friendActionButton.setBackgroundColor(Color.TRANSPARENT);
            friendActionButton.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            friendActionButton.setElevation(0f);
            friendActionButton.setStateListAnimator(null);
        }
    }

    private void updateFriendActionButton() {
        if (friendActionButton != null && isAdded()) {
            if (isFriend) {
                friendActionButton.setText("В друзьях");
                friendActionButton.setIconResource(R.drawable.group_24px);
                friendActionButton.setIconTint(ColorStateList.valueOf(getResources().getColor(R.color.black)));
                friendActionButton.setTextColor(getResources().getColor(R.color.black));
            } else {
                friendActionButton.setText("Добавить");
                friendActionButton.setIconResource(R.drawable.plus);
                friendActionButton.setIconTint(ColorStateList.valueOf(getResources().getColor(R.color.gray)));
                friendActionButton.setTextColor(getResources().getColor(R.color.gray));
            }

            friendActionButton.setBackgroundColor(Color.TRANSPARENT);
            friendActionButton.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        }
    }

    private void setupWriteMessageButton() {
        if (writeMessageButton != null) {
            writeMessageButton.setOnClickListener(v -> {
                openDialogWithUser();
            });
        }
    }

    private void openDialogWithUser() {
        if (isTestAccount()) {
            openDemoDialog();
        } else {
            openRealDialog();
        }
    }

    private void openDemoDialog() {
        if (!isAdded()) return;

        Intent intent = new Intent(getActivity(), DialogActivity.class);
        intent.putExtra("userId", String.valueOf(friendId));
        intent.putExtra("userName", friendName != null ? friendName : "Демо пользователь");
        intent.putExtra("peerId", String.valueOf(friendId));
        intent.putExtra("isSpecialUser", false);
        intent.putExtra("is_test_mode", true);

        startActivity(intent);

        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    private void openRealDialog() {
        if (!isAdded()) return;

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            Toast.makeText(getContext(), "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(getContext());
        progressDialog.setMessage("Открытие диалога...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String url = "https://api.vk.com/method/messages.getConversationsById" +
                "?access_token=" + accessToken +
                "&v=" + API_VERSION +
                "&peer_ids=" + friendId;

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();
                        if (isAdded()) {
                            Toast.makeText(getContext(), "Ошибка сети", Toast.LENGTH_SHORT).show();
                            openWithBasicData();
                        }
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> progressDialog.dismiss());
                }

                if (response.isSuccessful() && isAdded()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            JSONArray items = responseObj.getJSONArray("items");

                            if (items.length() > 0) {
                                JSONObject conversation = items.getJSONObject(0);
                                JSONObject peer = conversation.getJSONObject("peer");
                                String peerId = String.valueOf(peer.getInt("id"));
                                openDialogActivity(peerId);
                            } else {
                                openDialogActivity(String.valueOf(friendId));
                            }
                        } else {
                            openWithBasicData();
                        }
                    } catch (Exception e) {
                        Log.e("UserProfileFragment", "Error parsing conversation data", e);
                        openWithBasicData();
                    }
                } else if (isAdded()) {
                    openWithBasicData();
                }
            }
        });
    }

    private void openWithBasicData() {
        if (isAdded()) {
            openDialogActivity(String.valueOf(friendId));
        }
    }

    private void openDialogActivity(String peerId) {
        if (!isAdded()) return;

        Intent intent = new Intent(getActivity(), DialogActivity.class);
        intent.putExtra("userId", String.valueOf(friendId));
        intent.putExtra("userName", friendName != null ? friendName : "Пользователь");
        intent.putExtra("peerId", peerId);
        intent.putExtra("isSpecialUser", false);
        intent.putExtra("is_test_mode", isTestAccount());

        startActivity(intent);

        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    private void setupViewPager() {
        pagerAdapter = new ProfilePagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("ФОТО");
                    break;
                case 2:
                    tab.setText("ИСТОРИИ");
                    break;
                case 3:
                    tab.setText("ДРУЗЬЯ");
                    break;
                case 1:
                    tab.setText("ПОДАРКИ");
                    break;
                case 4:
                    tab.setText("ВИДЕО");
                    break;
                case 5:
                    tab.setText("ПОДАРКИ");
                    break;
            }
        }).attach();
    }

    private void setupToolbar() {
        if (toolbar != null && getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                activity.getSupportActionBar().setTitle(friendName);
            }

            toolbar.setNavigationOnClickListener(v -> {
                if (getActivity() != null) {
                    FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                    if (fragmentManager.getBackStackEntryCount() > 0) {
                        fragmentManager.popBackStack();
                    } else {
                        getActivity().onBackPressed();
                    }
                }
            });
        }
    }

    private void setupUserInfo() {
        if (textViewUserName != null && friendName != null) {
            textViewUserName.setText(friendName);
        }
    }

    private void checkFriendshipStatus() {
        if (isTestAccount()) {
            isFriend = true;
            updateFriendActionButton();
            return;
        }

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) return;

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/friends.get")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("user_id", String.valueOf(friendId))
                .addQueryParameter("v", API_VERSION)
                .build();

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("UserProfileFragment", "Failed to check friendship status", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (response.isSuccessful() && isAdded()) {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            isFriend = responseObj.getInt("count") > 0;
                        } else if (json.has("error")) {
                            isFriend = false;
                        }
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (isAdded()) {
                                    updateFriendActionButton();
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e("UserProfileFragment", "Error parsing friendship status", e);
                }
            }
        });
    }

    private void addToFriends() {
        if (isTestAccount()) {
            if (isAdded()) {
                Toast.makeText(getContext(), "Демо-режим: Пользователь добавлен в друзья", Toast.LENGTH_SHORT).show();
            }
            isFriend = true;
            updateFriendActionButton();
            return;
        }

        if (!isAdded()) return;

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            Toast.makeText(getContext(), "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            return;
        }

        friendActionButton.setEnabled(false);
        friendActionButton.setText("Добавление...");
        friendActionButton.setIconResource(R.drawable.loading_animation);

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/friends.add")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("user_id", String.valueOf(friendId))
                .addQueryParameter("v", API_VERSION)
                .build();

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded()) {
                            friendActionButton.setEnabled(true);
                            updateFriendActionButton();
                            Toast.makeText(getContext(), "Ошибка добавления в друзья", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (isAdded()) {
                                friendActionButton.setEnabled(true);

                                if (json.has("response")) {
                                    int result = 0;
                                    try {
                                        result = json.getInt("response");
                                    } catch (JSONException e) {
                                        Log.e("UserProfileFragment", "Error parsing response", e);
                                    }
                                    if (result == 1 || result == 2) {
                                        isFriend = true;
                                        updateFriendActionButton();
                                        Toast.makeText(getContext(), "Пользователь добавлен в друзья", Toast.LENGTH_SHORT).show();
                                    } else {
                                        updateFriendActionButton();
                                        Toast.makeText(getContext(), "Не удалось добавить в друзья", Toast.LENGTH_SHORT).show();
                                    }
                                } else if (json.has("error")) {
                                    JSONObject error = null;
                                    try {
                                        error = json.getJSONObject("error");
                                    } catch (JSONException e) {
                                        Log.e("UserProfileFragment", "Error parsing error", e);
                                    }
                                    String errorMsg = "Неизвестная ошибка";
                                    if (error != null) {
                                        errorMsg = error.optString("error_msg", "Неизвестная ошибка");
                                    }
                                    updateFriendActionButton();
                                    Toast.makeText(getContext(), "Ошибка: " + errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (isAdded()) {
                                friendActionButton.setEnabled(true);
                                updateFriendActionButton();
                                Toast.makeText(getContext(), "Ошибка обработки ответа", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }
        });
    }

    private void removeFromFriends() {
        if (!isAdded()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Удаление из друзей")
                .setMessage("Вы уверены, что хотите удалить " + friendName + " из друзей?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    performRemoveFromFriends();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void performRemoveFromFriends() {
        if (isTestAccount()) {
            if (isAdded()) {
                Toast.makeText(getContext(), "Демо-режим: Пользователь удален из друзей", Toast.LENGTH_SHORT).show();
            }
            isFriend = false;
            updateFriendActionButton();
            return;
        }

        if (!isAdded()) return;

        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            Toast.makeText(getContext(), "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            return;
        }

        friendActionButton.setEnabled(false);
        friendActionButton.setText("Удаление...");
        friendActionButton.setIconResource(R.drawable.loading_animation);

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/friends.delete")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("user_id", String.valueOf(friendId))
                .addQueryParameter("v", API_VERSION)
                .build();

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded()) {
                            friendActionButton.setEnabled(true);
                            updateFriendActionButton();
                            Toast.makeText(getContext(), "Ошибка удаления из друзей", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (isAdded()) {
                                friendActionButton.setEnabled(true);

                                if (json.has("response")) {
                                    JSONObject responseObj = null;
                                    try {
                                        responseObj = json.getJSONObject("response");
                                    } catch (JSONException e) {
                                        Log.e("UserProfileFragment", "Error parsing response", e);
                                    }
                                    int success = 0;
                                    if (responseObj != null) {
                                        success = responseObj.optInt("success", 0);
                                    }
                                    if (success == 1) {
                                        isFriend = false;
                                        updateFriendActionButton();
                                        Toast.makeText(getContext(), "Пользователь удален из друзей", Toast.LENGTH_SHORT).show();
                                    } else {
                                        updateFriendActionButton();
                                        Toast.makeText(getContext(), "Не удалось удалить из друзей", Toast.LENGTH_SHORT).show();
                                    }
                                } else if (json.has("error")) {
                                    JSONObject error = null;
                                    try {
                                        error = json.getJSONObject("error");
                                    } catch (JSONException e) {
                                        Log.e("UserProfileFragment", "Error parsing error", e);
                                    }
                                    String errorMsg = "Неизвестная ошибка";
                                    if (error != null) {
                                        errorMsg = error.optString("error_msg", "Неизвестная ошибка");
                                    }
                                    updateFriendActionButton();
                                    Toast.makeText(getContext(), "Ошибка: " + errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (isAdded()) {
                                friendActionButton.setEnabled(true);
                                updateFriendActionButton();
                                Toast.makeText(getContext(), "Ошибка обработки ответа", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }
        });
    }

    private void setupGiftsButton(View view) {
        //MaterialButton giftsButton = view.findViewById(R.id.gift);
        //if (giftsButton != null) {
        //    giftsButton.setOnClickListener(v -> openGiftsFragment());
       // }
    }

    private void openGiftsFragment() {
        // Создаем фрагмент подарков
        GiftsFragment giftsFragment = GiftsFragment.newInstance(friendId, friendName);

        // Заменяем текущий фрагмент на фрагмент подарков
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,  // enter
                        R.anim.slide_out_left,  // exit
                        R.anim.slide_in_left,   // popEnter
                        R.anim.slide_out_right  // popExit
                )
                .replace(R.id.container, giftsFragment) // Замените R.id.container на ваш контейнер
                .addToBackStack("gifts_fragment") // Добавляем в back stack для возврата
                .commit();

        // Меняем заголовок тулбара
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle("Подарки " + friendName);
            }
        }}

    private boolean isTestAccount() {
        return friendId == 123456789L || friendId == 0 || friendName == null;
    }

    public static class ProfilePagerAdapter extends FragmentStateAdapter {
        private final UserProfileFragment fragment;

        public ProfilePagerAdapter(UserProfileFragment fragment) {
            super(fragment);
            this.fragment = fragment;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return PhotosTabFragment.newInstance(fragment.friendId, fragment.friendName);
                case 2:
                    return AudioTabFragment.newInstance(fragment.friendId, fragment.friendName);
                case 3:
                    return FriendsTabFragment.newInstance(fragment.friendId, fragment.friendName);
                case 1:
                    return GiftsTabFragment.newInstance(fragment.friendId, fragment.friendName);
                case 4:
                    return VideoTabFragment.newInstance(fragment.friendId, fragment.friendName);
                default:
                    return PhotosTabFragment.newInstance(fragment.friendId, fragment.friendName);
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    // Фрагмент для вкладки с аудиозаписями
    public static class AudioTabFragment extends Fragment {
        private RecyclerView recyclerViewAudios;
        private AudioAdapter audioAdapter;
        private List<Audio> audioList = new ArrayList<>();
        private List<Audio> filteredAudioList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;
        private TextView textViewAudiosCount;
        private ProgressBar progressBar;
        private Toolbar toolbar;
        private LinearLayout emptyStateLayout;
        private TextView emptyStateText;

        private long friendId;
        private String friendName;
        private OkHttpClient httpClient;

        private boolean isLoading = false;
        private String currentQuery = "";

        public static AudioTabFragment newInstance(long friendId, String friendName) {
            AudioTabFragment fragment = new AudioTabFragment();
            Bundle args = new Bundle();
            args.putLong("friend_id", friendId);
            args.putString("friend_name", friendName);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Bundle args = getArguments();
            if (args != null) {
                friendId = args.getLong("friend_id");
                friendName = args.getString("friend_name");
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_friends_music, container, false);

            // Инициализация Toolbar
            toolbar = view.findViewById(R.id.toolbar);
            setupToolbar();

            recyclerViewAudios = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            textViewAudiosCount = view.findViewById(R.id.count);
            progressBar = view.findViewById(R.id.progressBar);

            // Инициализация элементов для пустого состояния
            emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
            emptyStateText = view.findViewById(R.id.emptyStateText);

            // Если нет этих элементов в макете, создадим их программно
            if (emptyStateLayout == null) {
                setupEmptyStateProgrammatically(view);
            }

            setupUI();
            setupSwipeRefresh();
            updateAudiosCountText();

            if (isTestAccount()) {
                loadTestAudios();
            } else {
                fetchUserAudios();
            }

            return view;
        }

        private void setupEmptyStateProgrammatically(View view) {
            emptyStateLayout = new LinearLayout(getContext());
            emptyStateLayout.setOrientation(LinearLayout.VERTICAL);
            emptyStateLayout.setGravity(Gravity.CENTER);
            emptyStateLayout.setVisibility(View.GONE);

            emptyStateText = new TextView(getContext());
            emptyStateText.setTextSize(16);
            emptyStateText.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
            emptyStateText.setGravity(Gravity.CENTER);
            emptyStateText.setPadding(50, 100, 50, 0);
            emptyStateText.setLineSpacing(0, 1.2f);

            emptyStateLayout.addView(emptyStateText);

            // Добавляем в корневой View
            if (view instanceof ViewGroup) {
                ViewGroup rootView = (ViewGroup) view;
                rootView.addView(emptyStateLayout);
            }
        }

        private void setupToolbar() {
            if (toolbar != null && getActivity() instanceof AppCompatActivity) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                activity.setSupportActionBar(toolbar);

                if (activity.getSupportActionBar() != null) {
                    activity.getSupportActionBar().setTitle(friendName);
                    activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                }

                toolbar.setNavigationOnClickListener(v -> {
                    if (getActivity() != null) {
                        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                        if (fragmentManager.getBackStackEntryCount() > 0) {
                            fragmentManager.popBackStack();
                        } else {
                            getActivity().onBackPressed();
                        }
                    }
                });
            }
        }

        private void setupUI() {
            LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
            recyclerViewAudios.setLayoutManager(layoutManager);

            audioAdapter = new AudioAdapter(filteredAudioList, requireContext());
            recyclerViewAudios.setAdapter(audioAdapter);

            audioAdapter.setOnItemClickListener(position -> {
                if (position >= 0 && position < filteredAudioList.size()) {
                    Audio audio = filteredAudioList.get(position);
                    playAudio(audio, position);
                }
            });
        }

        private void setupSwipeRefresh() {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (isTestAccount()) {
                    loadTestAudios();
                } else {
                    fetchUserAudios();
                }
            });
        }

        private boolean isTestAccount() {
            return friendId == 123456789L || friendId == 0 || friendName == null;
        }

        private void loadTestAudios() {
            setLoadingState(true);
            List<Audio> testAudios = new ArrayList<>();

            testAudios.add(createTestAudio("12345", "Test Artist 1", "Test Song 1", 180));
            testAudios.add(createTestAudio("12346", "Test Artist 2", "Test Song 2", 240));
            testAudios.add(createTestAudio("12347", "Test Artist 3", "Test Song 3", 200));
            testAudios.add(createTestAudio("12348", "Test Artist 4", "Test Song 4", 300));
            testAudios.add(createTestAudio("12349", "Test Artist 5", "Test Song 5", 150));

            requireActivity().runOnUiThread(() -> {
                audioList.clear();
                audioList.addAll(testAudios);
                resetSearch();
                setLoadingState(false);
                updateAudiosCountText();
            });
        }

        private Audio createTestAudio(String id, String artist, String title, int duration) {
            Audio audio = new Audio(artist, title, "https://test.com/audio" + id + ".mp3");
            audio.setAudioId(Long.parseLong(id));
            audio.setOwnerId(friendId);
            audio.setDuration(duration);
            return audio;
        }

        private void fetchUserAudios() {
            if (isLoading) return;

            String accessToken = TokenManager.getInstance(requireContext()).getToken();
            if (accessToken == null) {
                showErrorState("Требуется авторизация");
                return;
            }

            setLoadingState(true);

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/audio.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("owner_id", String.valueOf(friendId))
                    .addQueryParameter("count", "100")
                    .addQueryParameter("v", "5.131")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", getUserAgent())
                    .build();

            isLoading = true;

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        isLoading = false;
                        setLoadingState(false);
                        showErrorState("Ошибка сети: " + e.getMessage());
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");
                                List<Audio> newAudioList = new ArrayList<>();

                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject audioObj = items.getJSONObject(i);
                                    Audio audio = parseAudioItem(audioObj);
                                    if (audio != null) {
                                        newAudioList.add(audio);
                                    }
                                }

                                requireActivity().runOnUiThread(() -> {
                                    isLoading = false;
                                    swipeRefreshLayout.setRefreshing(false);

                                    audioList.clear();
                                    audioList.addAll(newAudioList);
                                    resetSearch();
                                    updateAudiosCountText();

                                    if (audioList.isEmpty()) {
                                        showEmptyState("У пользователя нет аудиозаписей");
                                    } else {
                                        showContentState();
                                    }
                                });
                            } else if (jsonObject.has("error")) {
                                handleApiError(jsonObject.getJSONObject("error"));
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                isLoading = false;
                                setLoadingState(false);
                                showErrorState("Ошибка обработки данных");
                            });
                        }
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            setLoadingState(false);
                            showErrorState("Ошибка сервера");
                        });
                    }
                }
            });
        }

        private Audio parseAudioItem(JSONObject audioObj) throws JSONException {
            String artist = audioObj.getString("artist");
            String title = audioObj.getString("title");
            String url = audioObj.getString("url");
            int duration = audioObj.getInt("duration");
            long id = audioObj.getLong("id");
            long ownerId = audioObj.getLong("owner_id");

            if (url != null && !url.isEmpty() && !url.equals("null")) {
                Audio audio = new Audio(artist, title, url);
                audio.setDuration(duration);
                audio.setAudioId(id);
                audio.setOwnerId(ownerId);
                return audio;
            }
            return null;
        }

        private void handleApiError(JSONObject error) {
            int errorCode = error.optInt("error_code");
            String errorMsg = error.optString("error_msg");

            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                setLoadingState(false);

                switch (errorCode) {
                    case 5:
                        showErrorState("Ошибка авторизации");
                        break;
                    case 6:
                        showErrorState("Слишком много запросов");
                        break;
                    case 15:
                        showPrivateAudiosState();
                        break;
                    case 7:
                    case 18:
                        showPrivateAudiosState();
                        break;
                    default:
                        showErrorState("Ошибка: " + errorMsg);
                }
            });
        }

        private String getUserAgent() {
            if (isAuthViaAuthActivity()) {
                return "VKAndroidApp/1.0";
            } else {
                try {
                    return ru.lisdevs.messenger.api.Authorizer.getKateUserAgent();
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

        private void showPrivateAudiosState() {
            if (emptyStateLayout != null && emptyStateText != null) {
                String privateMessage = "Аудиозаписи пользователя " +
                        (friendName != null ? friendName : "") + " закрыты\n\n" +
                        "Пользователь ограничил доступ к своим аудиозаписям в настройках приватности";

                emptyStateText.setText(privateMessage);

                emptyStateLayout.setVisibility(View.VISIBLE);
                recyclerViewAudios.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);

                if (textViewAudiosCount != null) {
                    textViewAudiosCount.setText("Аудиозаписи недоступны");
                }
            } else {
                showErrorState("Аудиозаписи пользователя закрыты");
            }
        }

        private void showErrorState(String message) {
            if (emptyStateLayout != null && emptyStateText != null) {
                emptyStateText.setText(message);
                emptyStateLayout.setVisibility(View.VISIBLE);
            }
            recyclerViewAudios.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
        }

        private void showEmptyState(String message) {
            if (emptyStateLayout != null && emptyStateText != null) {
                emptyStateText.setText(message);
                emptyStateLayout.setVisibility(View.VISIBLE);
                recyclerViewAudios.setVisibility(View.GONE);
            } else {
                showErrorState(message);
            }
        }

        private void showContentState() {
            if (emptyStateLayout != null) {
                emptyStateLayout.setVisibility(View.GONE);
            }
            recyclerViewAudios.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
        }

        private void setLoadingState(boolean loading) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            recyclerViewAudios.setVisibility(loading ? View.GONE : View.VISIBLE);
            if (emptyStateLayout != null) {
                emptyStateLayout.setVisibility(View.GONE);
            }
            if (!loading) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }

        private void resetSearch() {
            filteredAudioList.clear();
            filteredAudioList.addAll(audioList);
            if (audioAdapter != null) {
                audioAdapter.updateAudioList(filteredAudioList);
            }
        }

        private void playAudio(Audio audio, int position) {
            if (audio.getUrl() == null || audio.getUrl().isEmpty()) {
                Toast.makeText(getContext(), "Трек недоступен для прослушивания", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(getContext(), MusicPlayerService.class);
            intent.setAction(MusicPlayerService.ACTION_PLAY);
            intent.putExtra("AUDIO", audio);
            intent.putExtra("POSITION", position);

            ArrayList<Audio> playlist = new ArrayList<>(filteredAudioList);
            intent.putParcelableArrayListExtra("PLAYLIST", playlist);

            ContextCompat.startForegroundService(requireContext(), intent);
            showPlayerFragment(audio);
        }

        private void showPlayerFragment(Audio audio) {
            PlayerBottomSheetFragment playerFragment = new PlayerBottomSheetFragment();
            playerFragment.show(getParentFragmentManager(), "player");
        }

        private void updateAudiosCountText() {
            Activity activity = getActivity();
            if (activity != null && textViewAudiosCount != null) {
                activity.runOnUiThread(() -> {
                    String countText = isTestAccount() ?
                            "Тестовые аудиозаписи: " + audioList.size() :
                            "Треки: " + audioList.size();
                    textViewAudiosCount.setText(countText);
                });
            }
        }

        static class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {
            private List<Audio> audioList;
            private OnItemClickListener onItemClickListener;
            private Context context;

            public interface OnItemClickListener {
                void onItemClick(int position);
            }

            public AudioAdapter(List<Audio> audioList, Context context) {
                this.audioList = new ArrayList<>(audioList);
                this.context = context;
            }

            public void updateAudioList(List<Audio> newAudioList) {
                this.audioList.clear();
                this.audioList.addAll(newAudioList);
                notifyDataSetChanged();
            }

            public void setOnItemClickListener(OnItemClickListener listener) {
                this.onItemClickListener = listener;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_audio_new, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                Audio audio = audioList.get(position);

                holder.artistTextView.setText(audio.getArtist());
                holder.titleTextView.setText(audio.getTitle());

                holder.itemView.setOnClickListener(v -> {
                    if (onItemClickListener != null) {
                        onItemClickListener.onItemClick(position);
                    }
                });

                if (holder.menuButton != null) {
                    holder.menuButton.setOnClickListener(v -> {
                        showPopupMenu(v, audio);
                    });
                }
            }

            @Override
            public int getItemCount() {
                return audioList.size();
            }

            private String formatDuration(int duration) {
                int minutes = duration / 60;
                int seconds = duration % 60;
                return String.format("%d:%02d", minutes, seconds);
            }

            private void showPopupMenu(View view, Audio audio) {
                PopupMenu popupMenu = new PopupMenu(context, view);
                popupMenu.inflate(R.menu.audio_item_menu);

                popupMenu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.menu_copy_url) {
                        copyAudioUrl(audio);
                        return true;
                    } else if (item.getItemId() == R.id.menu_share) {
                        shareAudioUrl(audio);
                        return true;
                    }
                    return false;
                });

                popupMenu.show();
            }

            private void copyAudioUrl(Audio audio) {
                String audioUrl = audio.getUrl();
                if (audioUrl != null && !audioUrl.isEmpty() && !audioUrl.equals("null")) {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Audio URL", audioUrl);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "Ссылка на аудио скопирована", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Ссылка недоступна", Toast.LENGTH_SHORT).show();
                }
            }

            private void shareAudioUrl(Audio audio) {
                String audioUrl = audio.getUrl();
                if (audioUrl != null && !audioUrl.isEmpty() && !audioUrl.equals("null")) {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, audioUrl);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, audio.getArtist() + " - " + audio.getTitle());
                    context.startActivity(Intent.createChooser(shareIntent, "Поделиться аудиозаписью"));
                } else {
                    Toast.makeText(context, "Ссылка недоступна", Toast.LENGTH_SHORT).show();
                }
            }

            static class ViewHolder extends RecyclerView.ViewHolder {
                TextView artistTextView, titleTextView, durationTextView;
                ImageView menuButton;

                public ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    artistTextView = itemView.findViewById(R.id.titleText);
                    titleTextView = itemView.findViewById(R.id.artistText);
                    menuButton = itemView.findViewById(R.id.menu_button);
                }
            }
        }
    }

    // Фрагмент для вкладки с фотографиями
    public static class PhotosTabFragment extends Fragment {
        private RecyclerView recyclerViewPhotos;
        private PhotosAdapter photosAdapter;
        private List<PhotoItem> photoList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;
        private TextView textViewPhotosCount;
        private long friendId;
        private String friendName;
        private OkHttpClient httpClient;
        private int totalPhotosCount = 0;

        private final String[] TEST_PHOTOS = {
                "https://image.winudf.com/v2/image/c3dlZXQubWVzc2FnZXIudmtfc2NyZWVuc2hvdHNfMl80ODVjMjU1Yg/screen-2.webp?h=200&fakeurl=1&type=.webp",
                "https://sun9-29.userapi.com/s/v1/ig2/e1cpZokZGhsJ6k6XQ9zjSrKAXvW449mlHumlz_5sLfWFuDGZ5BwoJ5WULgvrZsXyUqcetp_1y62pfAZPO-JrzbDa.jpg",
                "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=500",
                "https://images.unsplash.com/photo-1529778873920-4da4926a72c2?w=500",
                "https://images.unsplash.com/photo-1518756131217-31eb79b20e8f?w=500",
                "https://images.unsplash.com/photo-1551963831-b3b1ca40c98e?w=500",
                "https://images.unsplash.com/photo-1470093851219-69951fcbb533?w=500",
                "https://images.unsplash.com/photo-1501854140801-50d01698950b?w=500",
                "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=500",
                "https://images.unsplash.com/photo-1505144808419-1957a94ca61e?w=500"
        };

        public static PhotosTabFragment newInstance(long friendId, String friendName) {
            PhotosTabFragment fragment = new PhotosTabFragment();
            Bundle args = new Bundle();
            args.putLong("friend_id", friendId);
            args.putString("friend_name", friendName);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Bundle args = getArguments();
            if (args != null) {
                friendId = args.getLong("friend_id");
                friendName = args.getString("friend_name");
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_photos, container, false);

            recyclerViewPhotos = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            textViewPhotosCount = view.findViewById(R.id.count);

            setupUI();
            setupSwipeRefresh();

            if (isTestAccount()) {
                loadTestPhotos();
            } else {
                fetchUserPhotos();
                getPhotosCount();
            }

            return view;
        }

        private void setupUI() {
            GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
            recyclerViewPhotos.setLayoutManager(layoutManager);
            photosAdapter = new PhotosAdapter(photoList, this::openPhotoViewer);
            recyclerViewPhotos.setAdapter(photosAdapter);
        }

        private void setupSwipeRefresh() {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                totalPhotosCount = 0;
                updatePhotosCountText();
                if (isTestAccount()) {
                    loadTestPhotos();
                } else {
                    fetchUserPhotos();
                    getPhotosCount();
                }
            });
        }

        private boolean isTestAccount() {
            return friendId == 123456789L || friendId == 0 || friendName == null;
        }

        private void loadTestPhotos() {
            swipeRefreshLayout.setRefreshing(true);
            List<PhotoItem> testPhotos = new ArrayList<>();
            Random random = new Random();

            for (int i = 0; i < TEST_PHOTOS.length; i++) {
                testPhotos.add(new PhotoItem(
                        i + 1,
                        friendId,
                        TEST_PHOTOS[i],
                        System.currentTimeMillis() / 1000 - random.nextInt(1000000),
                        random.nextInt(1000),
                        random.nextInt(100),
                        "Тестовое фото " + (i + 1)
                ));
            }

            requireActivity().runOnUiThread(() -> {
                photoList.clear();
                photoList.addAll(testPhotos);
                photosAdapter.notifyDataSetChanged();
                swipeRefreshLayout.setRefreshing(false);
                totalPhotosCount = testPhotos.size();
                updatePhotosCountText();
            });
        }

        private void fetchUserPhotos() {
            String accessToken = TokenManager.getInstance(getContext()).getToken();
            if (accessToken == null) {
                swipeRefreshLayout.setRefreshing(false);
                return;
            }

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/photos.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("owner_id", String.valueOf(friendId))
                    .addQueryParameter("album_id", "profile")
                    .addQueryParameter("count", "100")
                    .addQueryParameter("rev", "1")
                    .addQueryParameter("extended", "1")
                    .addQueryParameter("v", "5.131")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", Authorizer.getKateUserAgent())
                    .build();

            swipeRefreshLayout.setRefreshing(true);

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка загрузки фотографий", Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");
                                List<PhotoItem> newPhotoList = new ArrayList<>();

                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject photoObj = items.getJSONObject(i);
                                    PhotoItem photoItem = parsePhotoItem(photoObj);
                                    if (photoItem != null) {
                                        newPhotoList.add(photoItem);
                                    }
                                }

                                requireActivity().runOnUiThread(() -> {
                                    photoList.clear();
                                    photoList.addAll(newPhotoList);
                                    photosAdapter.notifyDataSetChanged();
                                    swipeRefreshLayout.setRefreshing(false);
                                });
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                                swipeRefreshLayout.setRefreshing(false);
                            });
                        }
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Ошибка сервера", Toast.LENGTH_SHORT).show();
                            swipeRefreshLayout.setRefreshing(false);
                        });
                    }
                }
            });
        }

        private PhotoItem parsePhotoItem(JSONObject photoObj) throws JSONException {
            long id = photoObj.getLong("id");
            long ownerId = photoObj.getLong("owner_id");
            String photoUrl = getBestPhotoUrl(photoObj);
            if (photoUrl == null) return null;

            long date = photoObj.getLong("date");
            int likesCount = photoObj.has("likes") ? photoObj.getJSONObject("likes").getInt("count") : 0;
            int commentsCount = photoObj.has("comments") ? photoObj.getJSONObject("comments").getInt("count") : 0;
            String text = photoObj.optString("text", "");

            return new PhotoItem(id, ownerId, photoUrl, date, likesCount, commentsCount, text);
        }

        private String getBestPhotoUrl(JSONObject photoObj) throws JSONException {
            String[] sizes = {"x", "y", "z", "m", "s"};
            for (String size : sizes) {
                if (photoObj.has("photo_" + size)) {
                    return photoObj.getString("photo_" + size);
                }
            }
            if (photoObj.has("sizes")) {
                JSONArray sizesArray = photoObj.getJSONArray("sizes");
                for (int i = sizesArray.length() - 1; i >= 0; i--) {
                    JSONObject sizeObj = sizesArray.getJSONObject(i);
                    String type = sizeObj.getString("type");
                    if (type.equals("x") || type.equals("y") || type.equals("z")) {
                        return sizeObj.getString("url");
                    }
                }
                if (sizesArray.length() > 0) {
                    return sizesArray.getJSONObject(sizesArray.length() - 1).getString("url");
                }
            }
            return null;
        }

        private void getPhotosCount() {
            Context context = getContext();
            if (context == null) return;

            String accessToken = TokenManager.getInstance(context).getToken();
            if (accessToken == null || accessToken.isEmpty()) return;

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/photos.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("owner_id", String.valueOf(friendId))
                    .addQueryParameter("album_id", "profile")
                    .addQueryParameter("count", "0")
                    .addQueryParameter("v", "5.131")
                    .build();

            httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    updatePhotosCountText();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        if (response.isSuccessful()) {
                            String responseBody = response.body().string();
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                int count = responseObj.optInt("count", 0);
                                totalPhotosCount = count;
                                updatePhotosCountText();
                            }
                        }
                    } catch (Exception e) {
                        updatePhotosCountText();
                    }
                }
            });
        }

        private void updatePhotosCountText() {
            Activity activity = getActivity();
            if (activity != null && textViewPhotosCount != null) {
                activity.runOnUiThread(() -> {
                    String countText = isTestAccount() ?
                            "Тестовые фотографии: " + totalPhotosCount :
                            "Фотографии: " + totalPhotosCount;
                    textViewPhotosCount.setText(countText);
                });
            }
        }

        private void openPhotoViewer(PhotoItem photoItem) {
            List<String> photoUrls = new ArrayList<>();
            int currentPosition = -1;

            for (int i = 0; i < photoList.size(); i++) {
                PhotoItem item = photoList.get(i);
                photoUrls.add(item.getPhotoUrl());
                if (item.getId() == photoItem.getId()) {
                    currentPosition = i;
                }
            }

            if (currentPosition == -1) currentPosition = 0;

            PhotoViewerFragment photoViewerFragment = PhotoViewerFragment.newInstance(
                    new ArrayList<>(photoUrls), currentPosition);

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.container, photoViewerFragment)
                    .addToBackStack("friend_details")
                    .commit();
        }

        static class PhotosAdapter extends RecyclerView.Adapter<PhotosAdapter.ViewHolder> {
            private List<PhotoItem> photos;
            private OnItemClickListener listener;

            public interface OnItemClickListener {
                void onItemClick(PhotoItem photo);
            }

            public PhotosAdapter(List<PhotoItem> photos, OnItemClickListener listener) {
                this.photos = photos;
                this.listener = listener;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_photo, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                PhotoItem photo = photos.get(position);
                Glide.with(holder.itemView.getContext())
                        .load(photo.getPhotoUrl())
                        .placeholder(R.drawable.img)
                        .error(R.drawable.img)
                        .centerCrop()
                        .into(holder.photoImageView);

                if (photo.getLikesCount() > 0) {
                    holder.likesTextView.setText(String.valueOf(photo.getLikesCount()));
                    holder.likesTextView.setVisibility(View.VISIBLE);
                } else {
                    holder.likesTextView.setVisibility(View.GONE);
                }

                holder.itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onItemClick(photo);
                });
            }

            @Override
            public int getItemCount() {
                return photos.size();
            }

            static class ViewHolder extends RecyclerView.ViewHolder {
                ImageView photoImageView;
                TextView likesTextView;

                ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    photoImageView = itemView.findViewById(R.id.photo_image);
                    likesTextView = itemView.findViewById(R.id.photo_description);
                }
            }
        }
    }

    // Фрагмент для вкладки с видео
    public static class VideoTabFragment extends Fragment {
        private static final String TAG = "VideoTabFragment";

        private RecyclerView recyclerViewVideos;
        private VideoAdapter videoAdapter;
        private List<VideoItem> videoList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;
        private TextView textViewVideosCount;
        private ProgressBar progressBar;
        private TextView errorTextView;
        private Button retryButton;

        private long friendId;
        private String friendName;
        private OkHttpClient httpClient;
        private boolean isLoading = false;

        // ExoPlayer для превью
        private SimpleExoPlayer previewPlayer;
        private int currentPreviewPosition = -1;

        public static VideoTabFragment newInstance(long friendId, String friendName) {
            VideoTabFragment fragment = new VideoTabFragment();
            Bundle args = new Bundle();
            args.putLong("friend_id", friendId);
            args.putString("friend_name", friendName);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Bundle args = getArguments();
            if (args != null) {
                friendId = args.getLong("friend_id");
                friendName = args.getString("friend_name");
            }

            // Инициализация ExoPlayer для превью
            initializePreviewPlayer();
        }

        private void initializePreviewPlayer() {
            if (previewPlayer == null) {
                previewPlayer = new SimpleExoPlayer.Builder(requireContext()).build();
                previewPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
                previewPlayer.setVolume(0f); // Без звука для превью
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_videos_tab, container, false);

            recyclerViewVideos = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            textViewVideosCount = view.findViewById(R.id.count);
            progressBar = view.findViewById(R.id.progressBar);
            errorTextView = view.findViewById(R.id.emptyView);
            retryButton = view.findViewById(R.id.retryButton);

            setupUI();
            setupSwipeRefresh();
            setupRetryButton();

            if (isTestAccount()) {
                loadTestVideos();
            } else {
                fetchUserVideos();
            }

            return view;
        }

        private void setupUI() {
            LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
            recyclerViewVideos.setLayoutManager(layoutManager);
            videoAdapter = new VideoAdapter(videoList);
            recyclerViewVideos.setAdapter(videoAdapter);

            // Обработка скролла для управления превью
            recyclerViewVideos.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        autoPlayVisibleVideo();
                    } else {
                        stopPreview();
                    }
                }

                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (!recyclerView.canScrollVertically(1)) {
                        // Достигнут конец списка
                        if (!isLoading && !isTestAccount()) {
                            // loadMoreVideos(); // Раскомментируйте если нужна пагинация
                        }
                    }
                }
            });
        }

        private void setupSwipeRefresh() {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (isTestAccount()) {
                    loadTestVideos();
                } else {
                    fetchUserVideos();
                }
            });
        }

        private void setupRetryButton() {
            retryButton.setOnClickListener(v -> {
                if (isTestAccount()) {
                    loadTestVideos();
                } else {
                    fetchUserVideos();
                }
            });
        }

        private boolean isTestAccount() {
            return friendId == 123456789L || friendId == 0 || friendName == null;
        }

        private void autoPlayVisibleVideo() {
            if (videoList.isEmpty() || previewPlayer == null) return;

            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerViewVideos.getLayoutManager();
            if (layoutManager == null) return;

            int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
            int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();

            // Ищем первый полностью видимый элемент
            for (int i = firstVisiblePosition; i <= lastVisiblePosition; i++) {
                View view = layoutManager.findViewByPosition(i);
                if (view != null && isViewFullyVisible(view)) {
                    playPreviewAtPosition(i);
                    break;
                }
            }
        }

        private boolean isViewFullyVisible(View view) {
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
            int top = view.getTop() - params.topMargin;
            int bottom = view.getBottom() + params.bottomMargin;
            int height = recyclerViewVideos.getHeight();

            return top >= 0 && bottom <= height;
        }

        private void playPreviewAtPosition(int position) {
            if (position == currentPreviewPosition) return;

            stopPreview();
            currentPreviewPosition = position;

            VideoItem videoItem = videoList.get(position);
            if (videoItem.videoUrl != null && !videoItem.videoUrl.isEmpty()) {
                try {
                    MediaItem mediaItem = MediaItem.fromUri(videoItem.videoUrl);
                    previewPlayer.setMediaItem(mediaItem);
                    previewPlayer.prepare();
                    previewPlayer.play();

                    // Присоединяем PlayerView к текущему элементу
                    VideoAdapter.ViewHolder holder = (VideoAdapter.ViewHolder) recyclerViewVideos.findViewHolderForAdapterPosition(position);
                    if (holder != null) {
                        holder.bindPreviewPlayer(previewPlayer);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error playing preview", e);
                }
            }
        }

        private void stopPreview() {
            if (previewPlayer != null) {
                previewPlayer.stop();
                previewPlayer.clearMediaItems();
            }
            currentPreviewPosition = -1;

            // Отключаем все PlayerView
            for (int i = 0; i < recyclerViewVideos.getChildCount(); i++) {
                View view = recyclerViewVideos.getChildAt(i);
                VideoAdapter.ViewHolder holder = (VideoAdapter.ViewHolder) recyclerViewVideos.getChildViewHolder(view);
                if (holder != null) {
                    holder.recycle();
                }
            }
        }

        private void loadTestVideos() {
            setLoadingState(true);
            List<VideoItem> testVideos = new ArrayList<>();

            testVideos.add(new VideoItem(
                    "Тестовое видео 1",
                    "Описание тестового видео 1",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    "https://images.unsplash.com/photo-1611162617474-5b21e879e113?w=500",
                    180,
                    1500,
                    System.currentTimeMillis() / 1000
            ));
            testVideos.add(new VideoItem(
                    "Тестовое видео 2",
                    "Описание тестового видео 2",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    "https://images.unsplash.com/photo-1611162616305-c69b3fa7fbe0?w=500",
                    240,
                    2500,
                    System.currentTimeMillis() / 1000 - 10000
            ));

            requireActivity().runOnUiThread(() -> {
                videoList.clear();
                videoList.addAll(testVideos);
                videoAdapter.notifyDataSetChanged();
                setLoadingState(false);
                updateVideosCountText();
            });
        }

        private void fetchUserVideos() {
            if (isLoading) return;

            String accessToken = TokenManager.getInstance(requireContext()).getToken();
            if (accessToken == null) {
                showErrorState("Требуется авторизация");
                return;
            }

            setLoadingState(true);
            hideErrorState();

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/video.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("owner_id", String.valueOf(friendId))
                    .addQueryParameter("count", "100")
                    .addQueryParameter("extended", "1")
                    .addQueryParameter("v", "5.131")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "VKAndroidApp/1.0")
                    .build();

            isLoading = true;

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        isLoading = false;
                        setLoadingState(false);
                        showErrorState("Ошибка сети: " + e.getMessage());
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");
                                List<VideoItem> newVideoList = new ArrayList<>();

                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject videoObj = items.getJSONObject(i);
                                    VideoItem videoItem = parseVideoItem(videoObj);
                                    if (videoItem != null) {
                                        newVideoList.add(videoItem);
                                    }
                                }

                                requireActivity().runOnUiThread(() -> {
                                    isLoading = false;
                                    setLoadingState(false);
                                    swipeRefreshLayout.setRefreshing(false);

                                    videoList.clear();
                                    videoList.addAll(newVideoList);
                                    videoAdapter.notifyDataSetChanged();
                                    updateVideosCountText();

                                    if (videoList.isEmpty()) {
                                        showErrorState("Нет доступных видео");
                                    } else {
                                        showContentState();
                                    }
                                });
                            } else if (jsonObject.has("error")) {
                                handleApiError(jsonObject.getJSONObject("error"));
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                isLoading = false;
                                setLoadingState(false);
                                showErrorState("Ошибка обработки данных");
                            });
                        }
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            setLoadingState(false);
                            showErrorState("Ошибка сервера");
                        });
                    }
                }
            });
        }

        private VideoItem parseVideoItem(JSONObject videoObj) throws JSONException {
            String title = videoObj.optString("title");
            String description = videoObj.optString("description");
            String playerUrl = videoObj.optString("player");
            String thumbUrl = getBestThumbnail(videoObj);
            int duration = videoObj.optInt("duration", 0);
            int views = videoObj.optInt("views", 0);
            long date = videoObj.optLong("date", 0);

            if (playerUrl != null && !playerUrl.isEmpty() && !playerUrl.equals("null")) {
                return new VideoItem(title, description, playerUrl, thumbUrl, duration, views, date);
            }
            return null;
        }

        private String getBestThumbnail(JSONObject videoObj) throws JSONException {
            if (videoObj.has("image")) {
                JSONArray images = videoObj.getJSONArray("image");
                for (int i = images.length() - 1; i >= 0; i--) {
                    JSONObject image = images.getJSONObject(i);
                    String url = image.optString("url");
                    if (url != null && !url.isEmpty()) {
                        return url;
                    }
                }
            }

            String[] photoSizes = {"photo_800", "photo_640", "photo_320", "first_frame_800", "first_frame_320"};
            for (String size : photoSizes) {
                if (videoObj.has(size)) {
                    String url = videoObj.optString(size);
                    if (url != null && !url.isEmpty()) {
                        return url;
                    }
                }
            }

            return null;
        }

        private void handleApiError(JSONObject error) {
            int errorCode = error.optInt("error_code");
            String errorMsg = error.optString("error_msg");

            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                setLoadingState(false);

                switch (errorCode) {
                    case 5:
                        showErrorState("Ошибка авторизации");
                        break;
                    case 6:
                        showErrorState("Слишком много запросов");
                        break;
                    case 15:
                        showErrorState("Нет доступа к видео пользователя");
                        break;
                    default:
                        showErrorState("Ошибка: " + errorMsg);
                }
            });
        }

        private void updateVideosCountText() {
            Activity activity = getActivity();
            if (activity != null && textViewVideosCount != null) {
                activity.runOnUiThread(() -> {
                    String countText = isTestAccount() ?
                            "Тестовые видео: " + videoList.size() :
                            "Видео: " + videoList.size();
                    textViewVideosCount.setText(countText);
                });
            }
        }

        private void setLoadingState(boolean loading) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            recyclerViewVideos.setVisibility(loading ? View.GONE : View.VISIBLE);
            if (!loading) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }

        private void showErrorState(String message) {
            errorTextView.setText(message);
            errorTextView.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
            recyclerViewVideos.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        private void hideErrorState() {
            errorTextView.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
        }

        private void showContentState() {
            recyclerViewVideos.setVisibility(View.VISIBLE);
            errorTextView.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        @Override
        public void onPause() {
            super.onPause();
            stopPreview();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (previewPlayer != null) {
                previewPlayer.release();
                previewPlayer = null;
            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == 1002 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Разрешение получено", Toast.LENGTH_SHORT).show();
            }
        }

        // Класс VideoItem с Parcelable
        // VideoAdapter класс
        class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.ViewHolder> {
            private List<VideoItem> videos;

            public VideoAdapter(List<VideoItem> videos) {
                this.videos = videos;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_video_exoplayer, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                VideoItem item = videos.get(position);
                holder.bind(item, position);
            }

            @Override
            public void onViewRecycled(@NonNull ViewHolder holder) {
                super.onViewRecycled(holder);
                holder.recycle();
            }

            @Override
            public int getItemCount() {
                return videos.size();
            }

            private String formatDuration(int seconds) {
                int minutes = seconds / 60;
                int hours = minutes / 60;
                minutes = minutes % 60;
                seconds = seconds % 60;

                if (hours > 0) {
                    return String.format("%d:%02d:%02d", hours, minutes, seconds);
                } else {
                    return String.format("%d:%02d", minutes, seconds);
                }
            }

            private String formatViews(int views) {
                if (views >= 1000000) {
                    return String.format(Locale.getDefault(), "%.1fM просмотров", views / 1000000.0);
                } else if (views >= 1000) {
                    return String.format(Locale.getDefault(), "%.1fK просмотров", views / 1000.0);
                } else {
                    return views + " просмотров";
                }
            }

            class ViewHolder extends RecyclerView.ViewHolder {
                TextView titleText, descriptionText, durationText, viewsText;
                PlayerView playerView;
                ImageView thumbnailPlaceholder;
                ProgressBar videoProgress;
                ImageButton playButton;
                Button downloadButton;
                FrameLayout playerContainer;

                public ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    titleText = itemView.findViewById(R.id.textTitle);
                    descriptionText = itemView.findViewById(R.id.textDescription);
                    durationText = itemView.findViewById(R.id.durationText);
                    viewsText = itemView.findViewById(R.id.viewsText);
                    playerView = itemView.findViewById(R.id.playerView);
                    thumbnailPlaceholder = itemView.findViewById(R.id.thumbnailPlaceholder);
                    videoProgress = itemView.findViewById(R.id.videoProgress);
                    playButton = itemView.findViewById(R.id.playButton);
                    downloadButton = itemView.findViewById(R.id.buttonDownload);
                    playerContainer = itemView.findViewById(R.id.playerContainer);

                    // Настройка PlayerView
                    playerView.setUseController(false);
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                }

                public void bind(VideoItem item, int position) {
                    titleText.setText(item.title);
                    descriptionText.setText(item.description);

                    if (item.duration > 0) {
                        durationText.setText(formatDuration(item.duration));
                        durationText.setVisibility(View.VISIBLE);
                    } else {
                        durationText.setVisibility(View.GONE);
                    }

                    if (item.views > 0) {
                        viewsText.setText(formatViews(item.views));
                        viewsText.setVisibility(View.VISIBLE);
                    } else {
                        viewsText.setVisibility(View.GONE);
                    }

                    // Загрузка превью
                    if (item.thumbnailUrl != null && !item.thumbnailUrl.isEmpty()) {
                        Glide.with(requireContext())
                                .load(item.thumbnailUrl)
                                .placeholder(R.drawable.img)
                                .error(R.drawable.img)
                                .into(thumbnailPlaceholder);
                    }

                    // Обработчик клика на весь элемент для полноэкранного воспроизведения
                    itemView.setOnClickListener(v -> {
                        VideoPlayerActivity.start(requireContext(), item, videoList);
                    });

                    // Кнопка воспроизведения в списке
                    playButton.setOnClickListener(v -> {
                        if (getAdapterPosition() == currentPreviewPosition && previewPlayer != null && previewPlayer.isPlaying()) {
                            stopPreview();
                        } else {
                            playPreviewAtPosition(getAdapterPosition());
                        }
                    });

                    downloadButton.setOnClickListener(v -> {
                        downloadVideo(item.videoUrl, item.title);
                    });

                    // Показываем плейсхолдер по умолчанию
                    showThumbnail();
                }

                public void bindPreviewPlayer(SimpleExoPlayer player) {
                    playerView.setPlayer(player);
                    playerView.setVisibility(View.VISIBLE);
                    thumbnailPlaceholder.setVisibility(View.GONE);
                    playButton.setVisibility(View.GONE);
                }

                public void recycle() {
                    if (playerView != null) {
                        playerView.setPlayer(null);
                    }
                    showThumbnail();
                }

                private void showThumbnail() {
                    playerView.setVisibility(View.GONE);
                    thumbnailPlaceholder.setVisibility(View.VISIBLE);
                    playButton.setVisibility(View.VISIBLE);
                }
            }

            private void downloadVideo(String urlStr, String title) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
                    return;
                }

                try {
                    Uri uri = Uri.parse(urlStr);
                    DownloadManager.Request request = new DownloadManager.Request(uri);
                    request.setTitle(title);
                    request.setDescription("Скачивание видео");
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                    String fileName = title.replaceAll("[^a-zA-Z0-9а-яА-Я\\s]", "") + ".mp4";
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                    DownloadManager manager = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
                    if (manager != null) {
                        manager.enqueue(request);
                        Toast.makeText(requireContext(), "Начато скачивание: " + title, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Ошибка при скачивании", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // Фрагмент для вкладки с друзьями
    public static class FriendsTabFragment extends Fragment {
        private RecyclerView recyclerViewFriends;
        private FriendsAdapter friendsAdapter;
        private List<FriendItem> friendList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;
        private TextView textViewFriendsCount;
        private ProgressBar progressBar;
        private TextView errorTextView;
        private Button retryButton;
        private Toolbar toolbar;

        private long friendId;
        private String friendName;
        private OkHttpClient httpClient;
        private boolean isLoading = false;

        public static FriendsTabFragment newInstance(long friendId, String friendName) {
            FriendsTabFragment fragment = new FriendsTabFragment();
            Bundle args = new Bundle();
            args.putLong("friend_id", friendId);
            args.putString("friend_name", friendName);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Bundle args = getArguments();
            if (args != null) {
                friendId = args.getLong("friend_id");
                friendName = args.getString("friend_name");
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_friends_tab_new, container, false);

            toolbar = view.findViewById(R.id.toolbar);
            recyclerViewFriends = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            textViewFriendsCount = view.findViewById(R.id.count);
            progressBar = view.findViewById(R.id.progressBar);
            errorTextView = view.findViewById(R.id.emptyView);
            retryButton = view.findViewById(R.id.retryButton);

            setupUI();
            setupSwipeRefresh();
            setupRetryButton();
            setupToolbar();

            if (isTestAccount()) {
                loadTestFriends();
            } else {
                fetchUserFriends();
            }

            return view;
        }

        private void setupToolbar() {
            if (toolbar != null && getActivity() instanceof AppCompatActivity) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                activity.setSupportActionBar(toolbar);
                if (activity.getSupportActionBar() != null) {
                    activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                    activity.getSupportActionBar().setTitle(friendName);
                }

                toolbar.setNavigationOnClickListener(v -> {
                    if (getActivity() != null) {
                        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                        if (fragmentManager.getBackStackEntryCount() > 0) {
                            fragmentManager.popBackStack();
                        } else {
                            getActivity().onBackPressed();
                        }
                    }
                });
            }
        }

        private void setupUI() {
            LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
            recyclerViewFriends.setLayoutManager(layoutManager);
            friendsAdapter = new FriendsAdapter(friendList);
            recyclerViewFriends.setAdapter(friendsAdapter);
        }

        private void setupSwipeRefresh() {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (isTestAccount()) {
                    loadTestFriends();
                } else {
                    fetchUserFriends();
                }
            });
        }

        private void setupRetryButton() {
            retryButton.setOnClickListener(v -> {
                if (isTestAccount()) {
                    loadTestFriends();
                } else {
                    fetchUserFriends();
                }
            });
        }

        private boolean isTestAccount() {
            return friendId == 123456789L || friendId == 0 || friendName == null;
        }

        private void loadTestFriends() {
            setLoadingState(true);
            List<FriendItem> testFriends = new ArrayList<>();

            testFriends.add(new FriendItem(
                    123456790L,
                    "Иван Иванов",
                    "https://via.placeholder.com/100x100/4ECDC4/FFFFFF?text=I",
                    "В сети",
                    true
            ));
            testFriends.add(new FriendItem(
                    123456791L,
                    "Мария Петрова",
                    "https://via.placeholder.com/100x100/45B7D1/FFFFFF?text=M",
                    "была в сети 2 часа назад",
                    false
            ));
            testFriends.add(new FriendItem(
                    123456792L,
                    "Алексей Сидоров",
                    "https://via.placeholder.com/100x100/96CEB4/FFFFFF?text=A",
                    "В сети",
                    true
            ));

            requireActivity().runOnUiThread(() -> {
                friendList.clear();
                friendList.addAll(testFriends);
                friendsAdapter.notifyDataSetChanged();
                setLoadingState(false);
                updateFriendsCountText();
                swipeRefreshLayout.setRefreshing(false);
            });
        }

        private void fetchUserFriends() {
            if (isLoading) return;

            String accessToken = TokenManager.getInstance(requireContext()).getToken();
            if (accessToken == null) {
                showErrorState("Требуется авторизация");
                return;
            }

            setLoadingState(true);
            hideErrorState();

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/friends.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("user_id", String.valueOf(friendId))
                    .addQueryParameter("fields", "photo_100,online,last_seen")
                    .addQueryParameter("count", "100")
                    .addQueryParameter("v", "5.131")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "VKAndroidApp/1.0")
                    .build();

            isLoading = true;

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        isLoading = false;
                        setLoadingState(false);
                        showErrorState("Ошибка сети: " + e.getMessage());
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");
                                List<FriendItem> newFriendList = new ArrayList<>();

                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject friendObj = items.getJSONObject(i);
                                    FriendItem friendItem = parseFriendItem(friendObj);
                                    if (friendItem != null) {
                                        newFriendList.add(friendItem);
                                    }
                                }

                                 requireActivity().runOnUiThread(() -> {
                                    isLoading = false;
                                    setLoadingState(false);
                                    swipeRefreshLayout.setRefreshing(false);

                                    friendList.clear();
                                    friendList.addAll(newFriendList);
                                    friendsAdapter.notifyDataSetChanged();
                                    updateFriendsCountText();

                                    if (friendList.isEmpty()) {
                                        showErrorState("Нет друзей");
                                    } else {
                                        showContentState();
                                    }
                                });
                            } else if (jsonObject.has("error")) {
                                handleApiError(jsonObject.getJSONObject("error"));
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                isLoading = false;
                                setLoadingState(false);
                                showErrorState("Ошибка обработки данных");
                            });
                        }
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            setLoadingState(false);
                            showErrorState("Ошибка сервера");
                        });
                    }
                }
            });
        }

        private FriendItem parseFriendItem(JSONObject friendObj) throws JSONException {
            long id = friendObj.getLong("id");
            String firstName = friendObj.getString("first_name");
            String lastName = friendObj.getString("last_name");
            String fullName = firstName + " " + lastName;

            String photoUrl = friendObj.optString("photo_100");
            boolean isOnline = friendObj.optInt("online", 0) == 1;

            String status = "В сети";
            if (!isOnline && friendObj.has("last_seen")) {
                JSONObject lastSeen = friendObj.getJSONObject("last_seen");
                long lastSeenTime = lastSeen.getLong("time");
                status = "был(а) в сети " + formatLastSeen(lastSeenTime);
            } else if (!isOnline) {
                status = "Не в сети";
            }

            return new FriendItem(id, fullName, photoUrl, status, isOnline);
        }

        private String formatLastSeen(long timestamp) {
            long currentTime = System.currentTimeMillis() / 1000;
            long diff = currentTime - timestamp;

            if (diff < 60) {
                return "только что";
            } else if (diff < 3600) {
                return (diff / 60) + " мин. назад";
            } else if (diff < 86400) {
                return (diff / 3600) + " ч. назад";
            } else {
                return (diff / 86400) + " дн. назад";
            }
        }

        private void handleApiError(JSONObject error) {
            int errorCode = error.optInt("error_code");
            String errorMsg = error.optString("error_msg");

            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                setLoadingState(false);

                switch (errorCode) {
                    case 5:
                        showErrorState("Ошибка авторизации");
                        break;
                    case 6:
                        showErrorState("Слишком много запросов");
                        break;
                    case 15:
                        showErrorState("Нет доступа к списку друзей");
                        break;
                    default:
                        showErrorState("Ошибка: " + errorMsg);
                }
            });
        }

        private void updateFriendsCountText() {
            Activity activity = getActivity();
            if (activity != null && textViewFriendsCount != null) {
                activity.runOnUiThread(() -> {
                    String countText = isTestAccount() ?
                            "Тестовые друзья: " + friendList.size() :
                            "Друзья: " + friendList.size();
                    textViewFriendsCount.setText(countText);
                });
            }
        }

        private void setLoadingState(boolean loading) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            recyclerViewFriends.setVisibility(loading ? View.GONE : View.VISIBLE);
            if (!loading) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }

        private void showErrorState(String message) {
            errorTextView.setText(message);
            errorTextView.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
            recyclerViewFriends.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        private void hideErrorState() {
            errorTextView.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
        }

        private void showContentState() {
            recyclerViewFriends.setVisibility(View.VISIBLE);
            errorTextView.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        static class FriendItem {
            long id;
            String name;
            String photoUrl;
            String status;
            boolean isOnline;

            public FriendItem(long id, String name, String photoUrl, String status, boolean isOnline) {
                this.id = id;
                this.name = name;
                this.photoUrl = photoUrl;
                this.status = status;
                this.isOnline = isOnline;
            }
        }

        class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {
            private List<FriendItem> friends;

            public FriendsAdapter(List<FriendItem> friends) {
                this.friends = friends;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.list_item_friend, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                FriendItem item = friends.get(position);

                holder.nameText.setText(item.name);
                holder.statusText.setText(item.status);

                if (item.photoUrl != null && !item.photoUrl.isEmpty() && !item.photoUrl.equals("null")) {
                    Glide.with(requireContext())
                            .load(item.photoUrl)
                            .placeholder(R.drawable.img)
                            .error(R.drawable.img)
                            .circleCrop()
                            .into(holder.avatarImage);
                }

                // Показываем индикатор онлайн-статуса
                if (item.isOnline) {
                    holder.onlineIndicator.setVisibility(View.VISIBLE);
                    holder.statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_500));
                } else {
                    holder.onlineIndicator.setVisibility(View.GONE);
                    holder.statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
                }

                holder.itemView.setOnClickListener(v -> {
                    openFriendProfile(item);
                });
            }

            @Override
            public int getItemCount() {
                return friends.size();
            }

            private void openFriendProfile(FriendItem friend) {
                // Открываем профиль друга
                UserProfileFragment fragment = new UserProfileFragment();
                Bundle args = new Bundle();
                args.putLong("friend_id", friend.id);
                args.putString("friend_name", friend.name);
                fragment.setArguments(args);

                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                                R.anim.slide_in_left, R.anim.slide_out_right)
                        .replace(R.id.container, fragment)
                        .addToBackStack("friend_profile")
                        .commit();
            }

            class ViewHolder extends RecyclerView.ViewHolder {
                ImageView avatarImage;
                TextView nameText, statusText;
                View onlineIndicator;

                public ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    avatarImage = itemView.findViewById(R.id.avatar_image);
                    nameText = itemView.findViewById(R.id.user);
                    statusText = itemView.findViewById(R.id.audio_count);
                    onlineIndicator = itemView.findViewById(R.id.online_indicator);
                }
            }
        }
    }

    // Фрагмент для вкладки с группами
    public static class GroupsTabFragment extends Fragment {
        private RecyclerView recyclerViewGroups;
        private GroupsAdapter groupsAdapter;
        private List<GroupItem> groupList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;
        private TextView textViewGroupsCount;
        private ProgressBar progressBar;
        private TextView errorTextView;
        private Button retryButton;
        private Toolbar toolbar;

        private long friendId;
        private String friendName;
        private OkHttpClient httpClient;
        private boolean isLoading = false;

        public static GroupsTabFragment newInstance(long friendId, String friendName) {
            GroupsTabFragment fragment = new GroupsTabFragment();
            Bundle args = new Bundle();
            args.putLong("friend_id", friendId);
            args.putString("friend_name", friendName);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Bundle args = getArguments();
            if (args != null) {
                friendId = args.getLong("friend_id");
                friendName = args.getString("friend_name");
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_groups_tab, container, false);

            toolbar = view.findViewById(R.id.toolbar);
            recyclerViewGroups = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            textViewGroupsCount = view.findViewById(R.id.count);
            progressBar = view.findViewById(R.id.progressBar);
            errorTextView = view.findViewById(R.id.emptyView);
            retryButton = view.findViewById(R.id.retryButton);

            setupUI();
            setupSwipeRefresh();
            setupRetryButton();
            setupToolbar();

            if (isTestAccount()) {
                loadTestGroups();
            } else {
                fetchUserGroups();
            }

            return view;
        }

        private void setupToolbar() {
            if (toolbar != null && getActivity() instanceof AppCompatActivity) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                activity.setSupportActionBar(toolbar);
                if (activity.getSupportActionBar() != null) {
                    activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                    activity.getSupportActionBar().setTitle(friendName);
                }

                toolbar.setNavigationOnClickListener(v -> {
                    if (getActivity() != null) {
                        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                        if (fragmentManager.getBackStackEntryCount() > 0) {
                            fragmentManager.popBackStack();
                        } else {
                            getActivity().onBackPressed();
                        }
                    }
                });
            }
        }

        private void setupUI() {
            LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
            recyclerViewGroups.setLayoutManager(layoutManager);
            groupsAdapter = new GroupsAdapter(groupList);
            recyclerViewGroups.setAdapter(groupsAdapter);
        }

        private void setupSwipeRefresh() {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (isTestAccount()) {
                    loadTestGroups();
                } else {
                    fetchUserGroups();
                }
            });
        }

        private void setupRetryButton() {
            retryButton.setOnClickListener(v -> {
                if (isTestAccount()) {
                    loadTestGroups();
                } else {
                    fetchUserGroups();
                }
            });
        }

        private boolean isTestAccount() {
            return friendId == 123456789L || friendId == 0 || friendName == null;
        }

        private void loadTestGroups() {
            setLoadingState(true);
            List<GroupItem> testGroups = new ArrayList<>();

            testGroups.add(new GroupItem(
                    -123456790L,
                    "Технологии и программирование",
                    "https://via.placeholder.com/100x100/4ECDC4/FFFFFF?text=T",
                    "Технологии",
                    1500,
                    true
            ));
            testGroups.add(new GroupItem(
                    -123456791L,
                    "Музыка и искусство",
                    "https://via.placeholder.com/100x100/45B7D1/FFFFFF?text=M",
                    "Искусство",
                    890,
                    false
            ));
            testGroups.add(new GroupItem(
                    -123456792L,
                    "Спорт и здоровье",
                    "https://via.placeholder.com/100x100/96CEB4/FFFFFF?text=S",
                    "Спорт",
                    2300,
                    true
            ));

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        groupList.clear();
                        groupList.addAll(testGroups);
                        groupsAdapter.notifyDataSetChanged();
                        setLoadingState(false);
                        updateGroupsCountText();
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
        }

        private void fetchUserGroups() {
            if (isLoading) return;

            String accessToken = TokenManager.getInstance(requireContext()).getToken();
            if (accessToken == null) {
                showErrorState("Требуется авторизация");
                return;
            }

            setLoadingState(true);
            hideErrorState();

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/groups.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("user_id", String.valueOf(friendId))
                    .addQueryParameter("extended", "1")
                    .addQueryParameter("fields", "name,photo_100,description,members_count,is_closed")
                    .addQueryParameter("count", "100")
                    .addQueryParameter("v", "5.131")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "VKAndroidApp/1.0")
                    .build();

            isLoading = true;

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (isAdded()) {
                                isLoading = false;
                                setLoadingState(false);
                                showErrorState("Ошибка сети: " + e.getMessage());
                            }
                        });
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");
                                List<GroupItem> newGroupList = new ArrayList<>();

                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject groupObj = items.getJSONObject(i);
                                    GroupItem groupItem = parseGroupItem(groupObj);
                                    if (groupItem != null) {
                                        newGroupList.add(groupItem);
                                    }
                                }

                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        if (isAdded()) {
                                            isLoading = false;
                                            setLoadingState(false);
                                            swipeRefreshLayout.setRefreshing(false);

                                            groupList.clear();
                                            groupList.addAll(newGroupList);
                                            groupsAdapter.notifyDataSetChanged();
                                            updateGroupsCountText();

                                            if (groupList.isEmpty()) {
                                                showErrorState("Нет групп");
                                            } else {
                                                showContentState();
                                            }
                                        }
                                    });
                                }
                            } else if (jsonObject.has("error")) {
                                handleApiError(jsonObject.getJSONObject("error"));
                            }
                        } catch (JSONException e) {
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (isAdded()) {
                                        isLoading = false;
                                        setLoadingState(false);
                                        showErrorState("Ошибка обработки данных");
                                    }
                                });
                            }
                        }
                    } else {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (isAdded()) {
                                    isLoading = false;
                                    setLoadingState(false);
                                    showErrorState("Ошибка сервера");
                                }
                            });
                        }
                    }
                }
            });
        }

        private GroupItem parseGroupItem(JSONObject groupObj) throws JSONException {
            long id = groupObj.getLong("id");
            String name = groupObj.getString("name");
            String photoUrl = groupObj.optString("photo_100");
            String description = groupObj.optString("description", "");
            int membersCount = groupObj.optInt("members_count", 0);
            boolean isClosed = groupObj.optInt("is_closed", 0) != 0;

            return new GroupItem(id, name, photoUrl, description, membersCount, isClosed);
        }

        private void handleApiError(JSONObject error) {
            int errorCode = error.optInt("error_code");
            String errorMsg = error.optString("error_msg");

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        isLoading = false;
                        setLoadingState(false);

                        switch (errorCode) {
                            case 5:
                                showErrorState("Ошибка авторизации");
                                break;
                            case 6:
                                showErrorState("Слишком много запросов");
                                break;
                            case 15:
                                showErrorState("Нет доступа к списку групп");
                                break;
                            case 18:
                                showErrorState("Страница удалена");
                                break;
                            default:
                                showErrorState("Ошибка: " + errorMsg);
                        }
                    }
                });
            }
        }

        private void updateGroupsCountText() {
            Activity activity = getActivity();
            if (activity != null && textViewGroupsCount != null) {
                activity.runOnUiThread(() -> {
                    if (isAdded()) {
                        String countText = isTestAccount() ?
                                "Тестовые группы: " + groupList.size() :
                                "Группы: " + groupList.size();
                        textViewGroupsCount.setText(countText);
                    }
                });
            }
        }

        private void setLoadingState(boolean loading) {
            if (!isAdded()) return;

            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            recyclerViewGroups.setVisibility(loading ? View.GONE : View.VISIBLE);
            if (!loading) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }

        private void showErrorState(String message) {
            if (!isAdded()) return;

            errorTextView.setText(message);
            errorTextView.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
            recyclerViewGroups.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        private void hideErrorState() {
            if (!isAdded()) return;

            errorTextView.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
        }

        private void showContentState() {
            if (!isAdded()) return;

            recyclerViewGroups.setVisibility(View.VISIBLE);
            errorTextView.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        static class GroupItem {
            long id;
            String name;
            String photoUrl;
            String description;
            int membersCount;
            boolean isClosed;

            public GroupItem(long id, String name, String photoUrl, String description,
                             int membersCount, boolean isClosed) {
                this.id = id;
                this.name = name;
                this.photoUrl = photoUrl;
                this.description = description;
                this.membersCount = membersCount;
                this.isClosed = isClosed;
            }
        }

        class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.ViewHolder> {
            private List<GroupItem> groups;

            public GroupsAdapter(List<GroupItem> groups) {
                this.groups = groups;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_group_new, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                GroupItem item = groups.get(position);

                holder.nameText.setText(item.name);

                if (item.membersCount > 0) {
                    holder.membersText.setText(formatMembersCount(item.membersCount));
                    holder.membersText.setVisibility(View.VISIBLE);
                } else {
                    holder.membersText.setVisibility(View.GONE);
                }

                if (item.isClosed) {
                    holder.statusText.setText("Закрытая группа");
                    holder.statusText.setVisibility(View.VISIBLE);
                } else {
                    holder.statusText.setText("Открытая группа");
                    holder.statusText.setVisibility(View.VISIBLE);
                }

                if (item.photoUrl != null && !item.photoUrl.isEmpty() && !item.photoUrl.equals("null")) {
                    Glide.with(requireContext())
                            .load(item.photoUrl)
                            .placeholder(R.drawable.img)
                            .error(R.drawable.img)
                            .circleCrop()
                            .into(holder.groupImage);
                }

                holder.itemView.setOnClickListener(v -> {
                    openGroupDetails(item);
                });
            }

            @Override
            public int getItemCount() {
                return groups.size();
            }

            private String formatMembersCount(int count) {
                if (count >= 1000000) {
                    return String.format(Locale.getDefault(), "%.1fM участников", count / 1000000.0);
                } else if (count >= 1000) {
                    return String.format(Locale.getDefault(), "%.1fK участников", count / 1000.0);
                } else {
                    return count + " участников";
                }
            }

            private void openGroupDetails(GroupItem group) {
                if (!isAdded()) return;

                AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                builder.setTitle(group.name)
                        .setMessage("Описание: " + (group.description != null && !group.description.isEmpty() ?
                                group.description : "Нет описания") +
                                "\nУчастников: " + group.membersCount +
                                "\nТип: " + (group.isClosed ? "Закрытая группа" : "Открытая группа"))
                        .setPositiveButton("OK", null)
                        .setNeutralButton("Открыть в ВК", (dialog, which) -> {
                            openGroupInVk(group.id);
                        })
                        .show();
            }

            private void openGroupInVk(long groupId) {
                try {
                    String url = "https://vk.com/club" + Math.abs(groupId);
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Не удалось открыть группу", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            class ViewHolder extends RecyclerView.ViewHolder {
                ImageView groupImage;
                TextView nameText, membersText, statusText;

                public ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    groupImage = itemView.findViewById(R.id.group_image);
                    nameText = itemView.findViewById(R.id.name_text);
                    membersText = itemView.findViewById(R.id.members_text);
                    statusText = itemView.findViewById(R.id.status_text);
                }
            }
        }
    }

    // Фрагмент для вкладки с подарками
    public static class GiftsTabFragment extends Fragment {
        private static final String TAG = "GiftsTabFragment";

        private RecyclerView recyclerViewGifts;
        private GiftsAdapter giftsAdapter;
        private List<GiftItem> giftList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;
        private TextView textViewGiftsCount;
        private ProgressBar progressBar;
        private TextView errorTextView;
        private Button retryButton;

        private long friendId;
        private String friendName;
        private OkHttpClient httpClient;
        private boolean isLoading = false;

        // Кэш для имен пользователей
        private Map<String, String> userNamesCache = new HashMap<>();

        public static GiftsTabFragment newInstance(long friendId, String friendName) {
            GiftsTabFragment fragment = new GiftsTabFragment();
            Bundle args = new Bundle();
            args.putLong("friend_id", friendId);
            args.putString("friend_name", friendName);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Bundle args = getArguments();
            if (args != null) {
                friendId = args.getLong("friend_id");
                friendName = args.getString("friend_name");
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_gifts_tab, container, false);

            recyclerViewGifts = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            textViewGiftsCount = view.findViewById(R.id.count);
            progressBar = view.findViewById(R.id.progressBar);
            errorTextView = view.findViewById(R.id.emptyView);
            retryButton = view.findViewById(R.id.retryButton);

            setupUI();
            setupSwipeRefresh();
            setupRetryButton();

            if (isTestAccount()) {
                loadTestGifts();
            } else {
                fetchUserGifts();
            }

            return view;
        }

        private void setupUI() {
            GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 2);
            recyclerViewGifts.setLayoutManager(layoutManager);
            giftsAdapter = new GiftsAdapter(giftList);
            recyclerViewGifts.setAdapter(giftsAdapter);
        }

        private void setupSwipeRefresh() {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (isTestAccount()) {
                    loadTestGifts();
                } else {
                    fetchUserGifts();
                }
            });
        }

        private void setupRetryButton() {
            retryButton.setOnClickListener(v -> {
                if (isTestAccount()) {
                    loadTestGifts();
                } else {
                    fetchUserGifts();
                }
            });
        }

        private boolean isTestAccount() {
            return friendId == 123456789L || friendId == 0 || friendName == null;
        }

        private void loadTestGifts() {
            setLoadingState(true);
            List<GiftItem> testGifts = new ArrayList<>();

            // Используем гарантированно работающие URL для тестирования
            testGifts.add(new GiftItem(
                    "1",
                    "https://via.placeholder.com/256x256/FF6B6B/FFFFFF?text=Gift+1",
                    "Красивый подарок",
                    "Анна Петрова",
                    System.currentTimeMillis() / 1000 - 86400,
                    "Сообщение к подарку"
            ));
            testGifts.add(new GiftItem(
                    "2",
                    "https://via.placeholder.com/256x256/4ECDC4/FFFFFF?text=Gift+2",
                    "Стильный подарок",
                    "Иван Сидоров",
                    System.currentTimeMillis() / 1000 - 172800,
                    "С наилучшими пожеланиями!"
            ));
            testGifts.add(new GiftItem(
                    "3",
                    "https://via.placeholder.com/256x256/45B7D1/FFFFFF?text=Gift+3",
                    "Эксклюзивный подарок",
                    "Мария Иванова",
                    System.currentTimeMillis() / 1000 - 259200,
                    "Любим и ценим!"
            ));

            requireActivity().runOnUiThread(() -> {
                giftList.clear();
                giftList.addAll(testGifts);
                giftsAdapter.notifyDataSetChanged();
                setLoadingState(false);
                updateGiftsCountText();
                swipeRefreshLayout.setRefreshing(false);
            });
        }

        private void fetchUserGifts() {
            if (isLoading) return;

            String accessToken = TokenManager.getInstance(requireContext()).getToken();
            if (accessToken == null) {
                showErrorState("Требуется авторизация");
                return;
            }

            setLoadingState(true);
            hideErrorState();

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/gifts.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("user_id", String.valueOf(friendId))
                    .addQueryParameter("count", "100")
                    .addQueryParameter("v", "5.131")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "VKAndroidApp/1.0")
                    .build();

            isLoading = true;

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        isLoading = false;
                        setLoadingState(false);
                        showErrorState("Ошибка сети: " + e.getMessage());
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");

                                // Сначала собираем все ID отправителей
                                Set<String> fromIds = new HashSet<>();
                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject giftObj = items.getJSONObject(i);
                                    String fromId = giftObj.optString("from_id");
                                    if (fromId != null && !fromId.isEmpty() && !fromId.equals("0")) {
                                        fromIds.add(fromId);
                                    }
                                }

                                // Загружаем имена пользователей
                                fetchUserNames(fromIds, items);

                            } else if (jsonObject.has("error")) {
                                handleApiError(jsonObject.getJSONObject("error"));
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                isLoading = false;
                                setLoadingState(false);
                                showErrorState("Ошибка обработки данных");
                            });
                        }
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            setLoadingState(false);
                            showErrorState("Ошибка сервера");
                        });
                    }
                }
            });
        }

        private void fetchUserNames(Set<String> userIds, JSONArray giftsArray) {
            if (userIds.isEmpty()) {
                try {
                    parseGiftsWithoutUserNames(giftsArray);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            String accessToken = TokenManager.getInstance(requireContext()).getToken();
            String userIdsString = TextUtils.join(",", userIds);

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/users.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("user_ids", userIdsString)
                    .addQueryParameter("fields", "first_name,last_name")
                    .addQueryParameter("v", "5.131")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    // Если не удалось получить имена, используем fallback
                    try {
                        parseGiftsWithoutUserNames(giftsArray);
                    } catch (JSONException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("response")) {
                                JSONArray users = json.getJSONArray("response");
                                for (int i = 0; i < users.length(); i++) {
                                    JSONObject user = users.getJSONObject(i);
                                    String userId = String.valueOf(user.getInt("id"));
                                    String firstName = user.optString("first_name", "");
                                    String lastName = user.optString("last_name", "");
                                    String fullName = firstName + " " + lastName;
                                    userNamesCache.put(userId, fullName);
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing user names", e);
                        }
                    }

                    // В любом случае парсим подарки
                    try {
                        parseGiftsWithUserNames(giftsArray);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        private void parseGiftsWithUserNames(JSONArray items) throws JSONException {
            List<GiftItem> newGiftList = new ArrayList<>();

            for (int i = 0; i < items.length(); i++) {
                JSONObject giftObj = items.getJSONObject(i);
                GiftItem giftItem = parseGiftItem(giftObj);
                if (giftItem != null) {
                    newGiftList.add(giftItem);
                }
            }

            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                setLoadingState(false);
                swipeRefreshLayout.setRefreshing(false);

                giftList.clear();
                giftList.addAll(newGiftList);
                giftsAdapter.notifyDataSetChanged();
                updateGiftsCountText();

                if (giftList.isEmpty()) {
                    showErrorState("Нет подарков");
                } else {
                    showContentState();
                }
            });
        }

        private void parseGiftsWithoutUserNames(JSONArray items) throws JSONException {
            List<GiftItem> newGiftList = new ArrayList<>();

            for (int i = 0; i < items.length(); i++) {
                JSONObject giftObj = items.getJSONObject(i);
                GiftItem giftItem = parseGiftItem(giftObj);
                if (giftItem != null) {
                    newGiftList.add(giftItem);
                }
            }

            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                setLoadingState(false);
                swipeRefreshLayout.setRefreshing(false);

                giftList.clear();
                giftList.addAll(newGiftList);
                giftsAdapter.notifyDataSetChanged();
                updateGiftsCountText();

                if (giftList.isEmpty()) {
                    showErrorState("Нет подарков");
                } else {
                    showContentState();
                }
            });
        }

        private GiftItem parseGiftItem(JSONObject giftObj) throws JSONException {
            String id = giftObj.optString("id");

            Log.d(TAG, "Parsing gift object keys: " + giftObj.toString());

            // Получаем все возможные поля для изображения
            String thumbUrl = null;

            // Пробуем все возможные поля, которые могут содержать URL изображения
            String[] possibleImageFields = {
                    "thumb_256", "thumb_128", "thumb_96", "thumb_48",
                    "photo_256", "photo_128", "photo_96", "photo_48",
                    "img_256", "img_128", "img_96", "img_48",
                    "image", "img", "photo", "thumbnail",
                    "thumb", "url"
            };

            for (String field : possibleImageFields) {
                if (giftObj.has(field)) {
                    String url = giftObj.optString(field);
                    if (url != null && !url.isEmpty() && !url.equals("null")) {
                        thumbUrl = url;
                        Log.d(TAG, "Found image URL in field '" + field + "': " + url);
                        break;
                    }
                }
            }

            // Если не нашли в основных полях, пробуем вложенные объекты
            if (thumbUrl == null && giftObj.has("gift")) {
                JSONObject gift = giftObj.getJSONObject("gift");
                for (String field : possibleImageFields) {
                    if (gift.has(field)) {
                        String url = gift.optString(field);
                        if (url != null && !url.isEmpty() && !url.equals("null")) {
                            thumbUrl = url;
                            Log.d(TAG, "Found image URL in gift." + field + ": " + url);
                            break;
                        }
                    }
                }
            }

            // Если все еще не нашли, пробуем массив images
            if (thumbUrl == null && giftObj.has("images")) {
                JSONArray images = giftObj.optJSONArray("images");
                if (images != null && images.length() > 0) {
                    // Берем последнее (наибольшее) изображение
                    JSONObject bestImage = images.getJSONObject(images.length() - 1);
                    thumbUrl = bestImage.optString("url");
                    Log.d(TAG, "Found image URL in images array: " + thumbUrl);
                }
            }

            String description = giftObj.optString("description");
            String fromId = giftObj.optString("from_id");
            long date = giftObj.optLong("date", 0);
            String message = giftObj.optString("message", "");

            // Если описание пустое, создаем стандартное
            if (description == null || description.isEmpty() || description.equals("null")) {
                description = "Подарок";
            }

            // Получаем имя отправителя из кэша или используем fallback
            String senderName = "Неизвестный";
            if (fromId != null && !fromId.isEmpty() && !fromId.equals("0")) {
                if (userNamesCache.containsKey(fromId)) {
                    senderName = userNamesCache.get(fromId);
                } else if (giftObj.has("user")) {
                    // Пробуем получить имя из объекта user, если он есть в ответе
                    JSONObject user = giftObj.getJSONObject("user");
                    String firstName = user.optString("first_name", "");
                    String lastName = user.optString("last_name", "");
                    if (!firstName.isEmpty() || !lastName.isEmpty()) {
                        senderName = firstName + " " + lastName;
                        userNamesCache.put(fromId, senderName);
                    } else {
                        senderName = "Пользователь " + fromId;
                    }
                } else {
                    senderName = "Пользователь " + fromId;
                }
            } else if (fromId != null && fromId.equals("0")) {
                senderName = "Анонимный отправитель";
            }

            Log.d(TAG, "Final gift data - ID: " + id + ", URL: " + thumbUrl + ", Desc: " + description + ", From: " + senderName);

            return new GiftItem(id, thumbUrl, description, senderName, date, message);
        }

        private void handleApiError(JSONObject error) {
            int errorCode = error.optInt("error_code");
            String errorMsg = error.optString("error_msg");

            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                setLoadingState(false);

                switch (errorCode) {
                    case 5:
                        showErrorState("Ошибка авторизации");
                        break;
                    case 6:
                        showErrorState("Слишком много запросов");
                        break;
                    case 15:
                        showErrorState("Нет доступа к подаркам пользователя");
                        break;
                    default:
                        showErrorState("Ошибка: " + errorMsg);
                }
            });
        }

        private void updateGiftsCountText() {
            Activity activity = getActivity();
            if (activity != null && textViewGiftsCount != null) {
                activity.runOnUiThread(() -> {
                    String countText = isTestAccount() ?
                            "Тестовые подарки: " + giftList.size() :
                            "Подарки: " + giftList.size();
                    textViewGiftsCount.setText(countText);
                });
            }
        }

        private void setLoadingState(boolean loading) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            recyclerViewGifts.setVisibility(loading ? View.GONE : View.VISIBLE);
            if (!loading) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }

        private void showErrorState(String message) {
            errorTextView.setText(message);
            errorTextView.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
            recyclerViewGifts.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        private void hideErrorState() {
            errorTextView.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
        }

        private void showContentState() {
            recyclerViewGifts.setVisibility(View.VISIBLE);
            errorTextView.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        static class GiftItem {
            String id;
            String thumbUrl;
            String description;
            String senderName;
            long date;
            String message;

            public GiftItem(String id, String thumbUrl, String description,
                            String senderName, long date, String message) {
                this.id = id;
                this.thumbUrl = thumbUrl;
                this.description = description;
                this.senderName = senderName;
                this.date = date;
                this.message = message;
            }
        }

        class GiftsAdapter extends RecyclerView.Adapter<GiftsAdapter.ViewHolder> {
            private List<GiftItem> gifts;

            public GiftsAdapter(List<GiftItem> gifts) {
                this.gifts = gifts;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_gift_tab, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                GiftItem item = gifts.get(position);

                // Добавляем логирование для диагностики
                Log.d(TAG, "Loading gift image: " + item.thumbUrl + " for gift: " + item.description);

                if (item.thumbUrl != null && !item.thumbUrl.isEmpty() && !item.thumbUrl.equals("null")) {
                    Glide.with(requireContext())
                            .load(item.thumbUrl)
                            .placeholder(R.drawable.gift_placeholder)
                            .error(R.drawable.gift_placeholder)
                            .addListener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                            Target<Drawable> target, boolean isFirstResource) {
                                    Log.e(TAG, "Image load failed: " + e + ", URL: " + item.thumbUrl);
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Drawable resource, Object model,
                                                               Target<Drawable> target, DataSource dataSource,
                                                               boolean isFirstResource) {
                                    Log.d(TAG, "Image loaded successfully: " + item.thumbUrl);
                                    return false;
                                }
                            })
                            .into(holder.giftImage);
                } else {
                    Log.w(TAG, "Empty or null image URL for gift: " + item.description);
                    holder.giftImage.setImageResource(R.drawable.gift_placeholder);
                }

                holder.descriptionText.setText(item.description != null ? item.description : "Подарок");

                // Отображаем имя отправителя
                if (item.senderName != null && !item.senderName.isEmpty()) {
                    holder.senderText.setText(item.senderName);
                    holder.senderText.setVisibility(View.VISIBLE);
                } else {
                    holder.senderText.setVisibility(View.GONE);
                }

                if (item.date > 0) {
                    holder.dateText.setText(formatDate(item.date));
                } else {
                    holder.dateText.setText("");
                }

                if (item.message != null && !item.message.isEmpty() && !item.message.equals("null")) {
                    holder.messageText.setText(item.message);
                    holder.messageText.setVisibility(View.VISIBLE);
                } else {
                    holder.messageText.setVisibility(View.GONE);
                }

                holder.itemView.setOnClickListener(v -> {
                    showGiftDetails(item);
                });
            }

            @Override
            public int getItemCount() {
                return gifts.size();
            }

            private String formatDate(long timestamp) {
                Date date = new Date(timestamp * 1000);
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                return sdf.format(date);
            }

            private void showGiftDetails(GiftItem gift) {
                // Создаем кастомный диалог с Material компонентами
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded);

                // Создаем кастомное view для диалога
                View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_gift_details, null);

                // Находим элементы в диалоге
                ImageView dialogGiftImage = dialogView.findViewById(R.id.dialogGiftImage);
                TextView dialogDescription = dialogView.findViewById(R.id.dialogDescription);
                TextView dialogSender = dialogView.findViewById(R.id.dialogSender);
                TextView dialogDate = dialogView.findViewById(R.id.dialogDate);
                TextView dialogMessage = dialogView.findViewById(R.id.dialogMessage);

                // Загружаем изображение подарка
                if (gift.thumbUrl != null && !gift.thumbUrl.isEmpty() && !gift.thumbUrl.equals("null")) {
                    Glide.with(requireContext())
                            .load(gift.thumbUrl)
                            .placeholder(R.drawable.gift_placeholder)
                            .error(R.drawable.gift_placeholder)
                            .into(dialogGiftImage);
                } else {
                    dialogGiftImage.setImageResource(R.drawable.gift_placeholder);
                }

                // Устанавливаем текст
                dialogDescription.setText(gift.description != null ? gift.description : "Подарок");
                dialogSender.setText(gift.senderName != null ? gift.senderName : "Неизвестный отправитель");
                dialogDate.setText(formatDate(gift.date));

                // Настраиваем сообщение (если есть)
                if (gift.message != null && !gift.message.isEmpty() && !gift.message.equals("null")) {
                    dialogMessage.setText(gift.message);
                    dialogMessage.setVisibility(View.VISIBLE);
                } else {
                    dialogMessage.setVisibility(View.GONE);
                }

                // Создаем диалог
                builder.setView(dialogView)
                        .setPositiveButton("Закрыть", (dialog, which) -> dialog.dismiss())
                        .setNeutralButton("Поделиться", (dialog, which) -> shareGift(gift));

                AlertDialog dialog = builder.create();
                dialog.show();

                // Настраиваем кнопки Material стиль
                Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);

                if (positiveButton != null) {
                    positiveButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
                }
                if (neutralButton != null) {
                    neutralButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
                }
            }

            private void shareGift(GiftItem gift) {
                String shareText = "Посмотрите на мой подарок: " +
                        (gift.description != null ? gift.description : "Подарок") +
                        (gift.senderName != null ? " от " + gift.senderName : "");

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                startActivity(Intent.createChooser(shareIntent, "Поделиться подарком"));
            }

            class ViewHolder extends RecyclerView.ViewHolder {
                ImageView giftImage;
                TextView descriptionText, senderText, dateText, messageText;

                public ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    giftImage = itemView.findViewById(R.id.giftImage);
                    descriptionText = itemView.findViewById(R.id.descriptionText);
                    senderText = itemView.findViewById(R.id.senderText);
                    dateText = itemView.findViewById(R.id.dateText);
                    messageText = itemView.findViewById(R.id.messageText);
                }
            }
        }
    }

    // Классы данных
    static class PhotoItem {
        private long id;
        private long ownerId;
        private String photoUrl;
        private long date;
        private int likesCount;
        private int commentsCount;
        private String text;

        public PhotoItem(long id, long ownerId, String photoUrl, long date,
                         int likesCount, int commentsCount, String text) {
            this.id = id;
            this.ownerId = ownerId;
            this.photoUrl = photoUrl;
            this.date = date;
            this.likesCount = likesCount;
            this.commentsCount = commentsCount;
            this.text = text;
        }

        public long getId() { return id; }
        public long getOwnerId() { return ownerId; }
        public String getPhotoUrl() { return photoUrl; }
        public long getDate() { return date; }
        public int getLikesCount() { return likesCount; }
        public int getCommentsCount() { return commentsCount; }
        public String getText() { return text; }
    }
}