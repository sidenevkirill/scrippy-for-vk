package ru.lisdevs.messenger.groups;

import static androidx.core.content.ContentProviderCompat.requireContext;
import static androidx.core.content.ContextCompat.startActivity;

import static ru.lisdevs.messenger.utils.TokenManager.*;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.dialog.DialogActivity;
import ru.lisdevs.messenger.utils.TokenManager;


public class GroupViewFragment extends Fragment implements GroupMenuBottomSheet.GroupMenuListener {

    private long groupId;
    private String groupName;
    private String groupDescription = ""; // Добавляем поле для хранения описания
    private Toolbar toolbar;
    private TextView textViewId;
    private TextView textViewName;
    private ShapeableImageView imageViewGroupAvatar;
    private ShapeableImageView toolbarAvatar;
    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbar;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private GroupPagerAdapter pagerAdapter;

    // Добавленные поля для отображения информации
    private TextView textViewGroupId;
    private TextView textViewMembersCount;
    private TextView textViewDescription;
    private MaterialButton subscribeButton;
    private MaterialButton writeMessageButton;
    private MaterialButton menuButton;

    // Новые поля для кнопок
    private MaterialButton groupsSumButton; // Кнопка с количеством участников
    private MaterialButton musicButton;     // Кнопка для музыки/звука
    private ImageView groupCoverImageView;

    // Состояние подписки
    private boolean isSubscribed = false;
    private boolean isLoadingSubscription = false;

    // Новые переменные для данных
    private int membersCount = 0;
    private int tracksCount = 0;
    private boolean isMusicSaved = false; // Состояние закладки для музыки

    private OkHttpClient httpClient;
    private static final String API_VERSION = "5.131";
    private boolean isTestMode = false;

    // SharedPreferences для сохранения закладок
    private SharedPreferences sharedPreferences;
    private static final String MUSIC_BOOKMARKS = "group_music_bookmarks";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Удалите эту строку, так как теперь используем BottomSheet
        // setHasOptionsMenu(true);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        Bundle args = getArguments();
        if (args != null) {
            groupId = args.getLong("group_id");
            groupName = args.getString("group_name");
            Log.d("GroupViewFragment", "Received groupId: " + groupId + ", groupName: " + groupName);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_group_view, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        textViewId = view.findViewById(R.id.textViewId);
        textViewName = view.findViewById(R.id.textViewName);
        imageViewGroupAvatar = view.findViewById(R.id.group_avatar);
        toolbarAvatar = view.findViewById(R.id.toolbar_avatar);
        appBarLayout = view.findViewById(R.id.appBarLayout);
        collapsingToolbar = view.findViewById(R.id.collapsingToolbar);
        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);
        menuButton = view.findViewById(R.id.menu);

        // Инициализация обложки группы
        groupCoverImageView = view.findViewById(R.id.group_cover);

        setupMenuButton();

        // Инициализация кнопки подписки
        subscribeButton = view.findViewById(R.id.check);

        // Инициализация кнопки "Написать сообщение"
        writeMessageButton = view.findViewById(R.id.btnWriteMessage);

        // Настройка кнопки "Написать сообщение"
        setupWriteMessageButton();

        // Инициализация новых TextView
        textViewGroupId = view.findViewById(R.id.artistsNamesText);
        textViewMembersCount = view.findViewById(R.id.textViewId);
        textViewDescription = view.findViewById(R.id.textViewDescription);

        // Настройка клика на "Подробнее"
        setupDescriptionClickListener();

        // Инициализация SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences("GroupPrefs", Context.MODE_PRIVATE);

        // Инициализация кнопки с количеством участников
        groupsSumButton = view.findViewById(R.id.groups_sum);

        // Инициализация кнопки музыки
        musicButton = view.findViewById(R.id.music);

        // Настройка кнопки музыки
        setupMusicButton();

        // Загрузка сохраненных закладок
        loadMusicBookmarkStatus();

        // Если нет отдельных TextView для ID и других данных, используем существующие
        if (textViewGroupId == null) {
            // Используем textViewId для отображения ID группы
            textViewGroupId = textViewId;
        }

        // Отображаем ID группы
        displayGroupId();

        // Установка названия группы
        if (textViewName != null && groupName != null) {
            textViewName.setText(groupName);
        }

        // Настройка кнопки подписки
        setupSubscribeButton();

        initAvatarAnimation();
        setupToolbar();
        setupViewPager();
        loadGroupInfo();
        checkSubscriptionStatus();

        // Загружаем количество аудиозаписей
        loadTracksCount();

        // Загружаем обложку группы
        loadGroupCover(groupId);

        return view;
    }

    // Метод для настройки клика на "Подробнее"
    private void setupDescriptionClickListener() {
        if (textViewDescription != null) {
            textViewDescription.setOnClickListener(v -> {
                // Проверяем, есть ли описание
                if (TextUtils.isEmpty(groupDescription)) {
                    Toast.makeText(requireContext(),
                            "Описание отсутствует",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Показываем BottomSheet с описанием
                showDescriptionBottomSheet(groupDescription);
            });

            // Делаем текст кликабельным
            textViewDescription.setTextColor(ContextCompat.getColor(requireContext(), R.color.background_dark));
            textViewDescription.setPaintFlags(textViewDescription.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

            // Добавляем эффект при нажатии
            //textViewDescription.setBackgroundResource(android.R.drawable.list_selector_background);
        }
    }

    // Метод для показа BottomSheet с описанием
    private void showDescriptionBottomSheet(String description) {
        if (getChildFragmentManager() != null) {
            GroupDescriptionBottomSheet bottomSheet =
                    GroupDescriptionBottomSheet.newInstance(groupName, description);
            bottomSheet.show(getChildFragmentManager(), "GroupDescriptionBottomSheet");
        }
    }

    private void setupMusicButton() {
        if (musicButton == null) return;

        musicButton.setOnClickListener(v -> {
            toggleMusicBookmark();
        });

        updateMusicButton();
    }

    private void toggleMusicBookmark() {
        isMusicSaved = !isMusicSaved;

        // Сохраняем состояние в SharedPreferences
        saveMusicBookmarkStatus();

        // Обновляем кнопку
        updateMusicButton();

        // Показываем сообщение
        String message = isMusicSaved ?
                "Добавлено в закладки" :
                "Удалено из закладок";
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();

        // Если добавлено в закладки, можно выполнить дополнительные действия
        if (isMusicSaved) {
            saveMusicToBookmarks();
        }
    }

    private void updateMusicButton() {
        if (musicButton != null) {
            // Формируем текст с количеством треков
            String text = "Избранное";
            if (tracksCount > 0) {
                text = "Закладки\n" + formatNumber(tracksCount);
            } else {
                text = "Закладки\nНет аудио";
            }

            musicButton.setText(text);

            // Меняем иконку в зависимости от состояния закладки
            int iconRes = isMusicSaved ?
                    R.drawable.star :  // Иконка для сохраненного
                    R.drawable.star;     // Иконка по умолчанию

            musicButton.setIconResource(iconRes);

            // Меняем цвет иконки для сохраненного состояния
            int iconColor = isMusicSaved ?
                    ContextCompat.getColor(requireContext(), R.color.black) : // Цвет для сохраненного
                    ContextCompat.getColor(requireContext(), R.color.gray);           // Стандартный цвет

            musicButton.setIconTint(ColorStateList.valueOf(iconColor));
        }
    }

    private void saveMusicBookmarkStatus() {
        String key = "music_bookmark_" + groupId;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, isMusicSaved);
        editor.apply();
    }

    private void loadMusicBookmarkStatus() {
        String key = "music_bookmark_" + groupId;
        isMusicSaved = sharedPreferences.getBoolean(key, false);
    }

    private void saveMusicToBookmarks() {
        // Создаем объект с данными о музыке группы
        JSONObject musicData = new JSONObject();
        try {
            musicData.put("groupId", groupId);
            musicData.put("groupName", groupName);
            musicData.put("tracksCount", tracksCount);
            musicData.put("savedDate", System.currentTimeMillis());

            // Получаем список текущих закладок
            String bookmarksJson = sharedPreferences.getString(MUSIC_BOOKMARKS, "{}");
            JSONObject allBookmarks = new JSONObject(bookmarksJson);

            // Добавляем текущую группу
            allBookmarks.put(String.valueOf(groupId), musicData);

            // Сохраняем обратно
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(MUSIC_BOOKMARKS, allBookmarks.toString());
            editor.apply();

        } catch (JSONException e) {
            Log.e("GroupViewFragment", "Error saving music bookmark", e);
        }
    }

    private void loadTracksCount() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken == null) {
            updateMusicButton();
            return;
        }

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/audio.get")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("owner_id", "-" + Math.abs(groupId))
                .addQueryParameter("count", "0") // Получаем только количество
                .addQueryParameter("v", API_VERSION)
                .build();

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tracksCount = 0;
                        updateMusicButton();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("response")) {
                        JSONObject responseObj = json.getJSONObject("response");
                        tracksCount = responseObj.optInt("count", 0);
                    }
                } catch (Exception e) {
                    Log.e("GroupViewFragment", "Error loading tracks count", e);
                    tracksCount = 0;
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        updateMusicButton();
                    });
                }
            }
        });
    }

    private void updateGroupsSumButton() {
        if (groupsSumButton != null) {
            String text = "Подписчики\n" + formatNumber(membersCount);
            groupsSumButton.setText(text);
        }
    }

    private void setupMenuButton() {
        if (menuButton == null) return;

        menuButton.setOnClickListener(v -> showGroupMenuBottomSheet());

        // Настройка внешнего вида кнопки
        menuButton.setIconResource(R.drawable.dots_horizontal);
        menuButton.setIconTint(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.black)));
        menuButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
        menuButton.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.bg_tab)));
    }

    private void showGroupMenuBottomSheet() {
        try {
            GroupMenuBottomSheet bottomSheet = GroupMenuBottomSheet.newInstance(groupId, groupName);
            bottomSheet.setGroupMenuListener(this);
            bottomSheet.show(getChildFragmentManager(), "GroupMenuBottomSheet");
        } catch (Exception e) {
            Log.e("GroupViewFragment", "Error showing bottom sheet", e);
            // Fallback: используем старое меню ActionBar
            showFallbackMenu();
        }
    }

    private void showFallbackMenu() {
        // Старый код для меню ActionBar как fallback
        android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(requireContext(), menuButton);
        popupMenu.inflate(R.menu.menu_groups);

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_share_profile) {
                shareGroup();
                return true;
            } else if (id == R.id.menu_copy_link) {
                copyGroupLink();
                return true;
            } else if (id == R.id.menu_copy_id) {
                copyGroupId();
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void setupWriteMessageButton() {
        if (writeMessageButton == null) return;

        writeMessageButton.setOnClickListener(v -> {
            if (isTestMode) {
                // Режим демо - показываем тестовый диалог
                openTestDialogWithGroup();
            } else {
                // Режим реального приложения - открываем диалог с группой
                openDialogWithGroup();
            }
        });

        // Установка иконки и стиля
        writeMessageButton.setIconResource(R.drawable.chat_outline);
    }

    // Реализация методов интерфейса GroupMenuListener
    @Override
    public void onShareGroup() {
        shareGroup();
    }

    @Override
    public void onCopyLink() {
        copyGroupLink();
    }

    @Override
    public void onCopyId() {
        copyGroupId();
    }

    @Override
    public void onGroupSettings() {
        // Дополнительная функциональность - можно добавить позже
        Toast.makeText(requireContext(), "Настройки группы временно недоступны", Toast.LENGTH_SHORT).show();
    }

    private void openDialogWithGroup() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken == null) {
            Toast.makeText(requireContext(), "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            return;
        }

        // Для групп в VK API peer_id = -group_id
        String peerId = "-" + Math.abs(groupId);

        // Запускаем DialogActivity с информацией о группе
        Intent intent = new Intent(getActivity(), DialogActivity.class);
        intent.putExtra("userId", String.valueOf(groupId));
        intent.putExtra("userName", groupName != null ? groupName : "Группа");
        intent.putExtra("peerId", peerId);
        intent.putExtra("isSpecialUser", false); // Группы не являются верифицированными пользователями

        startActivity(intent);
    }

    private void openTestDialogWithGroup() {
        // В тестовом режиме создаем демо-диалог с группой
        String peerId = "-" + Math.abs(groupId);

        Intent intent = new Intent(getActivity(), DialogActivity.class);
        intent.putExtra("userId", String.valueOf(groupId));
        intent.putExtra("userName", groupName != null ? groupName + " (Демо)" : "Группа (Демо)");
        intent.putExtra("peerId", peerId);
        intent.putExtra("isSpecialUser", false);
        intent.putExtra("is_test_mode", true);

        startActivity(intent);

        Toast.makeText(getActivity(), "Демо-режим: открывается чат с группой", Toast.LENGTH_SHORT).show();
    }

    private void setupSubscribeButton() {
        if (subscribeButton == null) return;

        subscribeButton.setOnClickListener(v -> {
            if (isLoadingSubscription) return;

            if (isSubscribed) {
                // Если уже подписан - отписываемся
                unsubscribeFromGroup();
            } else {
                // Если не подписан - подписываемся
                subscribeToGroup();
            }
        });

        // Установка начального состояния
        updateSubscribeButtonState();
    }

    private void updateSubscribeButtonState() {
        if (subscribeButton == null) return;

        if (isLoadingSubscription) {
            subscribeButton.setText("Загрузка...");
            subscribeButton.setEnabled(false);
            subscribeButton.setIconResource(R.drawable.loading_animation); // Нужно добавить эту иконку
        } else if (isSubscribed) {
            subscribeButton.setText("Подписаны");
            subscribeButton.setIconResource(R.drawable.check_verif);
        } else {
            subscribeButton.setText("Подписаться");
        }
    }

    private void displayGroupId() {
        // Форматируем ID для отображения
        String displayText;

        // Отображаем только "@название" без цифр
        if (groupName != null && !groupName.isEmpty()) {
            // Форматируем название: добавляем @ и убираем пробелы
            displayText = "@" + formatGroupName(groupName);
        } else {
            // Если название недоступно, показываем ID
            if (groupId > 0) {
                displayText = "@club" + groupId;
            } else if (groupId < 0) {
                long absoluteId = Math.abs(groupId);
                displayText = "@club" + absoluteId;
            } else {
                displayText = "@unknown";
            }
        }

        // Отображаем текст
        if (textViewGroupId != null) {
            textViewGroupId.setText(displayText);
            // Можно сделать текст выделенным
            textViewGroupId.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
            textViewGroupId.setTypeface(null, Typeface.BOLD);

            // Делаем текст кликабельным для копирования
            textViewGroupId.setOnClickListener(v -> copyGroupHandle(displayText));
        }

        // Также отображаем в существующем textViewId если это разные View
        if (textViewId != null && textViewId != textViewGroupId) {
            textViewId.setText(displayText);
        }

        // Логируем для отладки
        Log.d("GroupViewFragment", "Displaying group handle: " + displayText);
    }

    // Вспомогательный метод для форматирования названия группы
    private String formatGroupName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        // Убираем лишние пробелы
        name = name.trim();

        // Более сложная обработка - сохраняем только буквы, цифры и подчеркивания
        return name.toLowerCase()
                .replaceAll("[^а-яa-z0-9_]", "") // Удаляем все, кроме букв, цифр и подчеркиваний
                .replaceAll("\\s+", "_"); // Заменяем пробелы на подчеркивания
    }

    // Метод для копирования handle группы
    private void copyGroupHandle(String handle) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Группа", handle);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(requireContext(), "Никнейм скопирован: " + handle, Toast.LENGTH_SHORT).show();
    }

    // Проверка статуса подписки
    private void checkSubscriptionStatus() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken == null) {
            Log.e("GroupViewFragment", "Access token is null for subscription check");
            return;
        }

        isLoadingSubscription = true;
        updateSubscribeButtonState();

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/groups.isMember")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("group_id", String.valueOf(Math.abs(groupId)))
                .addQueryParameter("user_id", TokenManager.getInstance(getContext()).getUserId())
                .addQueryParameter("extended", "0")
                .addQueryParameter("v", API_VERSION)
                .build();

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("GroupViewFragment", "Failed to check subscription status", e);
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        isLoadingSubscription = false;
                        updateSubscribeButtonState();
                        // В случае ошибки показываем как "не подписан"
                        isSubscribed = false;
                        updateSubscribeButtonState();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try {
                    String responseBody = response.body().string();
                    Log.d("GroupViewFragment", "Subscription check response: " + responseBody);

                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("response")) {
                        int result = json.getInt("response");
                        isSubscribed = (result == 1);

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                isLoadingSubscription = false;
                                updateSubscribeButtonState();
                                Log.d("GroupViewFragment", "Subscription status: " + (isSubscribed ? "subscribed" : "not subscribed"));
                            });
                        }
                    } else if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        Log.e("GroupViewFragment", "API Error checking subscription: " + error.toString());
                        // В случае ошибки API тоже показываем как "не подписан"
                        isSubscribed = false;

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                isLoadingSubscription = false;
                                updateSubscribeButtonState();
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e("GroupViewFragment", "Error parsing subscription response", e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            isLoadingSubscription = false;
                            isSubscribed = false;
                            updateSubscribeButtonState();
                        });
                    }
                }
            }
        });
    }

    // Подписаться на группу
    private void subscribeToGroup() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken == null) {
            Toast.makeText(requireContext(), "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            return;
        }

        isLoadingSubscription = true;
        updateSubscribeButtonState();

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/groups.join")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("group_id", String.valueOf(Math.abs(groupId)))
                .addQueryParameter("v", API_VERSION)
                .build();

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("GroupViewFragment", "Failed to subscribe to group", e);
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        isLoadingSubscription = false;
                        updateSubscribeButtonState();
                        Toast.makeText(requireContext(), "Ошибка подписки", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try {
                    String responseBody = response.body().string();
                    Log.d("GroupViewFragment", "Subscribe response: " + responseBody);

                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("response")) {
                        int result = json.getInt("response");
                        if (result == 1) {
                            isSubscribed = true;

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    isLoadingSubscription = false;
                                    updateSubscribeButtonState();
                                    Toast.makeText(requireContext(), "Подписались на группу", Toast.LENGTH_SHORT).show();
                                    // Обновляем количество участников
                                    incrementMembersCount();
                                });
                            }
                        }
                    } else if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        String errorMsg = error.optString("error_msg", "Ошибка подписки");

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                isLoadingSubscription = false;
                                updateSubscribeButtonState();
                                Toast.makeText(requireContext(), "Ошибка: " + errorMsg, Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e("GroupViewFragment", "Error parsing subscribe response", e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            isLoadingSubscription = false;
                            updateSubscribeButtonState();
                            Toast.makeText(requireContext(), "Ошибка подписки", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    // Отписаться от группы
    private void unsubscribeFromGroup() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken == null) {
            Toast.makeText(requireContext(), "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            return;
        }

        isLoadingSubscription = true;
        updateSubscribeButtonState();

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/groups.leave")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("group_id", String.valueOf(Math.abs(groupId)))
                .addQueryParameter("v", API_VERSION)
                .build();

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("GroupViewFragment", "Failed to unsubscribe from group", e);
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        isLoadingSubscription = false;
                        updateSubscribeButtonState();
                        Toast.makeText(requireContext(), "Ошибка отписки", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try {
                    String responseBody = response.body().string();
                    Log.d("GroupViewFragment", "Unsubscribe response: " + responseBody);

                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("response")) {
                        int result = json.getInt("response");
                        if (result == 1) {
                            isSubscribed = false;

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    isLoadingSubscription = false;
                                    updateSubscribeButtonState();
                                    Toast.makeText(requireContext(), "Отписались от группы", Toast.LENGTH_SHORT).show();
                                    // Обновляем количество участников
                                    decrementMembersCount();
                                });
                            }
                        }
                    } else if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        String errorMsg = error.optString("error_msg", "Ошибка отписки");

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                isLoadingSubscription = false;
                                updateSubscribeButtonState();
                                Toast.makeText(requireContext(), "Ошибка: " + errorMsg, Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e("GroupViewFragment", "Error parsing unsubscribe response", e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            isLoadingSubscription = false;
                            updateSubscribeButtonState();
                            Toast.makeText(requireContext(), "Ошибка отписки", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    // Увеличить счетчик участников при подписке
    private void incrementMembersCount() {
        membersCount++;
        if (groupsSumButton != null) {
            updateGroupsSumButton();
        }
    }

    // Уменьшить счетчик участников при отписке
    private void decrementMembersCount() {
        membersCount = Math.max(0, membersCount - 1);
        if (groupsSumButton != null) {
            updateGroupsSumButton();
        }
    }

    // Обновленный метод updateGroupInfo для обработки screen_name
    private void updateGroupInfo(JSONObject group) {
        try {
            // Обновление аватарки группы
            if (group.has("photo_200")) {
                String avatarUrl = group.getString("photo_200");
                if (avatarUrl != null && !avatarUrl.isEmpty() && !avatarUrl.equals("null")) {
                    Glide.with(this)
                            .load(avatarUrl)
                            .placeholder(R.drawable.default_avatar)
                            .error(R.drawable.default_avatar)
                            .circleCrop()
                            .into(imageViewGroupAvatar);

                    // Также устанавливаем аватарку в toolbar
                    Glide.with(this)
                            .load(avatarUrl)
                            .placeholder(R.drawable.default_avatar)
                            .error(R.drawable.default_avatar)
                            .circleCrop()
                            .into(toolbarAvatar);
                }
            }

            // Обновление названия группы
            if (group.has("name")) {
                String updatedName = group.getString("name");
                if (!updatedName.equals(groupName)) {
                    groupName = updatedName;
                    if (textViewName != null) {
                        textViewName.setText(groupName);
                    }

                    // Обновляем заголовок тулбара
                    if (getActivity() instanceof AppCompatActivity) {
                        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
                        if (actionBar != null) {
                            actionBar.setTitle(groupName);
                        }
                    }
                }
            }

            // Обновление короткого имени (screen_name) если оно есть
            String screenName = null;
            if (group.has("screen_name")) {
                screenName = group.getString("screen_name");
                if (screenName != null && !screenName.isEmpty() && !screenName.equals("null")) {
                    // Отображаем screen_name с @
                    String displayText = "@" + screenName;
                    if (textViewGroupId != null) {
                        textViewGroupId.setText(displayText);
                        textViewGroupId.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
                        textViewGroupId.setTypeface(null, Typeface.BOLD);
                    }
                } else {
                    // Если screen_name нет, показываем форматированное имя
                    displayGroupId();
                }
            } else {
                // Если screen_name отсутствует, показываем форматированное имя
                displayGroupId();
            }

            // Отображение описания группы
            if (group.has("description") && textViewDescription != null) {
                String description = group.getString("description");
                if (description != null && !description.isEmpty() && !description.equals("null")) {
                    // Сохраняем описание в переменную класса
                    groupDescription = description;

                    // Показываем "Подробнее" как кликабельный текст
                    textViewDescription.setText("Подробнее");
                    textViewDescription.setVisibility(View.VISIBLE);

                    // Добавляем иконку стрелки справа (если есть)
                    //textViewDescription.setCompoundDrawablesWithIntrinsicBounds(
                    //        0, 0, R.drawable.chevron_down, 0);
                    textViewDescription.setCompoundDrawablePadding(8);

                } else {
                    // Если описания нет, показываем соответствующий текст
                    textViewDescription.setText("Нет описания");
                    textViewDescription.setVisibility(View.VISIBLE);
                    textViewDescription.setOnClickListener(null);
                    textViewDescription.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
                    textViewDescription.setPaintFlags(0);
                    textViewDescription.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                    groupDescription = "";
                }
            } else {
                // Если поле description отсутствует в ответе API
                if (textViewDescription != null) {
                    textViewDescription.setText("Нет описания");
                    textViewDescription.setVisibility(View.VISIBLE);
                    textViewDescription.setOnClickListener(null);
                    textViewDescription.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
                    textViewDescription.setPaintFlags(0);
                    textViewDescription.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                    groupDescription = "";
                }
            }

            // Отображение количества участников
            if (group.has("members_count") && textViewMembersCount != null) {
                membersCount = group.getInt("members_count");
                String membersText = "Участники: " + formatNumber(membersCount);
                textViewMembersCount.setText(membersText);
                textViewMembersCount.setVisibility(View.VISIBLE);

                // Обновляем кнопку с количеством участников
                updateGroupsSumButton();
            }

            // Отображение статуса
            if (group.has("status")) {
                String status = group.getString("status");
                if (status != null && !status.isEmpty() && !status.equals("null")) {
                    Log.d("GroupViewFragment", "Group status: " + status);
                }
            }

            // Проверяем наличие обложки в API
            if (group.has("cover")) {
                JSONObject cover = group.getJSONObject("cover");
                if (cover.has("images")) {
                    JSONArray images = cover.getJSONArray("images");
                    if (images.length() > 0) {
                        // Берем самую большую обложку
                        JSONObject largestImage = null;
                        for (int i = 0; i < images.length(); i++) {
                            JSONObject image = images.getJSONObject(i);
                            if (largestImage == null ||
                                    image.getInt("width") > largestImage.getInt("width")) {
                                largestImage = image;
                            }
                        }

                        if (largestImage != null && largestImage.has("url")) {
                            String coverUrl = largestImage.getString("url");
                            displayGroupCover(coverUrl);
                        }
                    }
                }
            }

        } catch (JSONException e) {
            Log.e("GroupViewFragment", "Error updating group info", e);
            showBasicGroupInfo();
        }
    }

    private void initAvatarAnimation() {
        // Слушатель для анимации аватарки
        appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            handleAvatarAnimation(verticalOffset);
        });

        // Установите ту же аватарку в toolbar
        if (imageViewGroupAvatar.getDrawable() != null) {
            toolbarAvatar.setImageDrawable(imageViewGroupAvatar.getDrawable());
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
            if (imageViewGroupAvatar != null) {
                float avatarAlpha = 1f - (progress - 0.3f) / 0.7f;
                imageViewGroupAvatar.setAlpha(Math.max(avatarAlpha, 0f));
            }
        } else {
            // Скрываем аватарку в toolbar
            toolbarAvatar.setVisibility(View.INVISIBLE);
            toolbarAvatar.setAlpha(0f);

            // Показываем большую аватарку
            if (imageViewGroupAvatar != null) {
                imageViewGroupAvatar.setAlpha(1f);
            }
        }
    }

    private void setupToolbar() {
        if (toolbar != null && getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                activity.getSupportActionBar().setTitle(groupName);
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

    private void setupViewPager() {
        pagerAdapter = new GroupPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("ПОСТЫ");
                    break;
                case 1:
                    tab.setText("МУЗЫКА");
                    break;
            }
        }).attach();
    }

    private void loadGroupInfo() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken == null) {
            Log.e("GroupViewFragment", "Access token is null");
            return;
        }

        // Для групп используем отрицательный ID в запросе
        long apiGroupId = groupId;
        if (groupId > 0) {
            apiGroupId = -groupId; // VK API ожидает отрицательный ID для групп
        }

        Log.d("GroupViewFragment", "Loading info for group ID: " + apiGroupId);

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/groups.getById")
                .newBuilder()
                .addQueryParameter("access_token", accessToken)
                .addQueryParameter("group_id", String.valueOf(Math.abs(groupId))) // Используем абсолютное значение
                .addQueryParameter("fields", "photo_200,description,status,members_count,screen_name,cover")
                .addQueryParameter("v", API_VERSION)
                .build();

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("GroupViewFragment", "Failed to load group info", e);
                // Показываем базовую информацию даже при ошибке
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        showBasicGroupInfo();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (response.isSuccessful() && isAdded()) {
                        String responseBody = response.body().string();
                        Log.d("GroupViewFragment", "API Response: " + responseBody);

                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("response")) {
                            JSONArray groups = json.getJSONArray("response");
                            if (groups.length() > 0) {
                                JSONObject group = groups.getJSONObject(0);
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        if (isAdded()) {
                                            updateGroupInfo(group);
                                        }
                                    });
                                }
                            } else {
                                Log.e("GroupViewFragment", "Empty response array");
                                showBasicGroupInfo();
                            }
                        } else if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            Log.e("GroupViewFragment", "API Error: " + error.toString());
                            showBasicGroupInfo();
                        }
                    } else {
                        Log.e("GroupViewFragment", "Response not successful: " + response.code());
                        showBasicGroupInfo();
                    }
                } catch (Exception e) {
                    Log.e("GroupViewFragment", "Error parsing group info", e);
                    showBasicGroupInfo();
                }
            }
        });
    }

    private void showBasicGroupInfo() {
        // Отображаем базовую информацию, даже если не удалось загрузить с API
        if (textViewName != null && groupName != null) {
            textViewName.setText(groupName);
        }

        // Показываем ID
        displayGroupId();

        // Показываем сообщение об отсутствии дополнительной информации
        if (textViewDescription != null) {
            textViewDescription.setText("Не удалось загрузить дополнительную информацию");
            textViewDescription.setOnClickListener(null);
            textViewDescription.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
            textViewDescription.setPaintFlags(0);
        }
    }

    private String formatNumber(int number) {
        if (number >= 1000000) {
            return String.format(Locale.getDefault(), "%.1fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format(Locale.getDefault(), "%.1fK", number / 1000.0);
        } else {
            return String.valueOf(number);
        }
    }

    // Существующие методы...
    private void copyGroupId() {
        String groupIdText = String.valueOf(groupId);

        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("ID группы", groupIdText);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(requireContext(), "ID группы скопирован: " + groupIdText, Toast.LENGTH_SHORT).show();
    }

    private void shareGroup() {
        String shareText = "Группа " + groupName + " (ID: " + groupId + ") в ВК: https://vk.com/club" + Math.abs(groupId);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Группа " + groupName);

        startActivity(Intent.createChooser(shareIntent, "Поделиться группой"));
    }

    private void copyGroupLink() {
        String groupLink = "https://vk.com/club" + Math.abs(groupId);

        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Группа ВК", groupLink);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(requireContext(), "Ссылка скопирована: " + groupLink, Toast.LENGTH_SHORT).show();
    }

    // =================== МЕТОДЫ ДЛЯ ЗАГРУЗКИ ОБЛОЖКИ ГРУППЫ ===================

    private void loadGroupCover(long groupId) {
        if (isTestMode) {
            // В тестовом режиме используем демо-обложку
            setDemoGroupCover();
            return;
        }

        String accessToken = TokenManager.getInstance(getContext()).getToken();
        if (accessToken == null) {
            Log.e("GroupViewFragment", "Access token is null");
            return;
        }

        // Параллельно попробуем получить через веб-страницу
        loadGroupCoverFromWebPage(groupId);
    }

    // Метод для загрузки обложки через веб-страницу
    private void loadGroupCoverFromWebPage(long groupId) {
        String groupUrl = "https://vk.com/club" + Math.abs(groupId);

        httpClient.newCall(new Request.Builder().url(groupUrl).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("GroupViewFragment", "Failed to load group webpage", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded()) return;

                try {
                    String html = response.body().string();

                    // Ищем обложку в HTML
                    String coverUrl = findCoverInHtml(html);

                    if (coverUrl != null && !coverUrl.isEmpty()) {
                        runOnUiThread(() -> displayGroupCover(coverUrl));
                    } else {
                        // Если не нашли, используем дефолтную
                        runOnUiThread(() -> setDefaultGroupCover());
                    }
                } catch (Exception e) {
                    Log.e("GroupViewFragment", "Error parsing webpage", e);
                    runOnUiThread(() -> setDefaultGroupCover());
                }
            }
        });
    }

    // Метод для поиска обложки в HTML
    private String findCoverInHtml(String html) {
        // Паттерн 1: Ищем по стилю background-image
        Pattern pattern1 = Pattern.compile("<div[^>]*class=[\"'][^\"']*cover[\"'][^>]*>");
        Matcher matcher1 = pattern1.matcher(html);

        if (matcher1.find()) {
            int start = matcher1.start();
            int end = html.indexOf("</div>", start);
            if (end != -1) {
                String divContent = html.substring(start, end);

                // Ищем background-image в этом div
                Pattern bgPattern = Pattern.compile("background-image:\\s*url\\(['\"]?([^'\")]*)['\"]?\\)");
                Matcher bgMatcher = bgPattern.matcher(divContent);

                if (bgMatcher.find()) {
                    String url = bgMatcher.group(1);
                    return cleanUrl(url);
                }
            }
        }

        // Паттерн 2: Ищем meta-тег с обложкой
        Pattern pattern2 = Pattern.compile("<meta[^>]*property=[\"']og:image[\"'][^>]*content=[\"']([^\"']*)[\"']");
        Matcher matcher2 = pattern2.matcher(html);

        if (matcher2.find()) {
            return cleanUrl(matcher2.group(1));
        }

        // Паттерн 3: Ищем любую картинку с классом "cover"
        Pattern pattern3 = Pattern.compile("<img[^>]*class=[\"'][^\"']*cover[\"'][^>]*src=[\"']([^\"']*)[\"']");
        Matcher matcher3 = pattern3.matcher(html);

        if (matcher3.find()) {
            return cleanUrl(matcher3.group(1));
        }

        // Паттерн 4: Прямой поиск обложки из примера HTML
        Pattern pattern4 = Pattern.compile("background-image:\\s*url\\(&quot;([^&]*?)&quot;\\)");
        Matcher matcher4 = pattern4.matcher(html);

        if (matcher4.find()) {
            String url = matcher4.group(1);
            url = url.replace("&amp;", "&");
            return cleanUrl(url);
        }

        return null;
    }

    // Метод для очистки URL
    private String cleanUrl(String url) {
        if (url == null) return null;

        // Убираем HTML-сущности
        url = url.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">");

        // Убираем лишние кавычки
        url = url.trim();
        if (url.startsWith("\"") && url.endsWith("\"")) {
            url = url.substring(1, url.length() - 1);
        } else if (url.startsWith("'") && url.endsWith("'")) {
            url = url.substring(1, url.length() - 1);
        }

        // Проверяем, что это полный URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // Добавляем протокол если нужно
            if (url.startsWith("//")) {
                url = "https:" + url;
            } else if (url.startsWith("/")) {
                url = "https://vk.com" + url;
            }
        }

        return url;
    }

    // Метод для отображения обложки
    private void displayGroupCover(String coverUrl) {
        if (!isAdded() || getActivity() == null || groupCoverImageView == null) return;

        getActivity().runOnUiThread(() -> {
            if (coverUrl != null && !coverUrl.isEmpty()) {
                Glide.with(this)
                        .load(coverUrl)
                        .placeholder(R.drawable.default_cover)
                        .error(R.drawable.default_cover)
                        .into(groupCoverImageView);

                Log.d("GroupViewFragment", "Group cover loaded: " + coverUrl);
            } else {
                setDefaultGroupCover();
            }
        });
    }

    // Метод для установки дефолтной обложки
    private void setDefaultGroupCover() {
        if (!isAdded() || getActivity() == null || groupCoverImageView == null) return;

        getActivity().runOnUiThread(() -> {
            if (groupCoverImageView != null) {
                groupCoverImageView.setImageResource(R.drawable.default_cover);
            }
        });
    }

    // Метод для демо-обложки
    private void setDemoGroupCover() {
        if (!isAdded() || getActivity() == null || groupCoverImageView == null) return;

        getActivity().runOnUiThread(() -> {
            if (groupCoverImageView != null) {
                // Используем цветной градиент для демо
                groupCoverImageView.setBackgroundResource(R.drawable.demo_cover_gradient);
                groupCoverImageView.setImageResource(0); // Очищаем изображение
            }
        });
    }

    // Вспомогательный метод для запуска кода в UI потоке
    private void runOnUiThread(Runnable action) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(action);
        }
    }

    public static GroupViewFragment newInstance(long groupId, String groupName) {
        GroupViewFragment fragment = new GroupViewFragment();
        Bundle args = new Bundle();
        args.putLong("group_id", groupId);
        args.putString("group_name", groupName);
        fragment.setArguments(args);
        return fragment;
    }

    public static class GroupPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {
        private final GroupViewFragment fragment;

        public GroupPagerAdapter(GroupViewFragment fragment) {
            super(fragment);
            this.fragment = fragment;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return GroupPostsFragment.newInstance(fragment.groupId, fragment.groupName);
                case 1:
                    return GroupDetailsFragment.newInstance(fragment.groupId, fragment.groupName);
                default:
                    return GroupPostsFragment.newInstance(fragment.groupId, fragment.groupName);
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}