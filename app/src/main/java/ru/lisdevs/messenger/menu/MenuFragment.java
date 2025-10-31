package ru.lisdevs.messenger.menu;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

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
import ru.lisdevs.messenger.about.AboutFragment;
import ru.lisdevs.messenger.algoritms.AlgoritmPlaylistsFragment;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.genre.GenreFragment;
import ru.lisdevs.messenger.groups.GroupsTabsFragment;
import ru.lisdevs.messenger.local.SaveMusicFragment;
import ru.lisdevs.messenger.local.SaveSectionFragment;
import ru.lisdevs.messenger.messages.MessagesFragment;
import ru.lisdevs.messenger.music.MusicTabsFragment;
import ru.lisdevs.messenger.music.MyMusicPlaylistsFragment;
import ru.lisdevs.messenger.music.RecommendationFragment;
import ru.lisdevs.messenger.newsfeed.NewsFeedFragment;
import ru.lisdevs.messenger.official.audios.AudioInputFragment;
import ru.lisdevs.messenger.official.audios.RecomendationAudioListFragment;
import ru.lisdevs.messenger.search.MusicSearchFragment;
import ru.lisdevs.messenger.settings.SettingsFragment;
import ru.lisdevs.messenger.utils.TokenManager;

public class MenuFragment extends Fragment {

    private TextView profileNameTextView;
    private ImageView profileAvatar;
    private String userId;
    private ImageView specialIcon;
    private OkHttpClient client = new OkHttpClient();
    private String userFirstName = "";
    private String userLastName = "";
    private boolean isPremiumUser = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Получаем ID пользователя из TokenManager
        userId = TokenManager.getInstance(requireContext()).getUserId();

        // Инициализируем элементы профиля
        profileNameTextView = view.findViewById(R.id.toolbar_title);
        profileAvatar = view.findViewById(R.id.profile_avatar);
        specialIcon = view.findViewById(R.id.special_icon);

        // Добавляем обработчик нажатия на аватар
        //profileAvatar.setOnClickListener(v -> showProfileBottomSheet());

        // Загружаем данные пользователя
        if (userId != null) {
            loadUserProfile(userId);
            checkSpecialUser(userId);
        } else {
            profileNameTextView.setText("Гость");
        }

        LinearLayout menuContainer = view.findViewById(R.id.menu_container);

        // Создаем элементы меню
        List<MenuItem> menuItems = new ArrayList<>();
        menuItems.add(new MenuItem("Музыка", R.drawable.my, MusicTabsFragment.class));
        //menuItems.add(new MenuItem("Рекомендации", R.drawable.rss, RecommendationFragment.class));
        //menuItems.add(new MenuItem("Поиск музыки", R.drawable.searc, MusicSearchFragment.class));
       // menuItems.add(new MenuItem("Жанры", R.drawable.star, GenreFragment.class));
       // menuItems.add(new MenuItem("Алгоритмы", R.drawable.compass_outline, AlgoritmPlaylistsFragment.class));
        //menuItems.add(new MenuItem("Загрузки", R.drawable.save_black, SaveSectionFragment.class));
        menuItems.add(new MenuItem("Группы", R.drawable.group_24px, GroupsTabsFragment.class));
        menuItems.add(new MenuItem("Лента", R.drawable.rss, NewsFeedFragment.class));
        //menuItems.add(new MenuItem("Сообщения", R.drawable.chat_outline, MessagesFragment.class));
        //menuItems.add(new MenuItem("Открыть трек", R.drawable.link_variant, AudioInputFragment.class));
        //menuItems.add(new MenuItem("Лента", R.drawable.rss, VKPostsFragment.class));
        //menuItems.add(new MenuItem("Группы", R.drawable.account_outline, GroupsSearchFragment.class));
        menuItems.add(new MenuItem("Настройки", R.drawable.ic_vector_outline_settings, SettingsFragment.class));
        menuItems.add(new MenuItem("О приложении", R.drawable.information_outline, AboutFragment.class));
        menuItems.add(new MenuItem("Закрыть", R.drawable.close, null));

        // Добавляем элементы в меню
        for (int i = 0; i < menuItems.size(); i++) {
            MenuItem item = menuItems.get(i);
            View menuItemView = createMenuItemView(item);
            menuContainer.addView(menuItemView);
        }
    }

    private void checkSpecialUser(String userId) {
        String specialUsersUrl = "https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/special_users.json";

        Request request = new Request.Builder()
                .url(specialUsersUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    specialIcon.setVisibility(View.GONE);
                    isPremiumUser = false;
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("special_users")) {
                        JSONArray specialUsers = json.getJSONArray("special_users");
                        boolean isSpecialUser = false;

                        for (int i = 0; i < specialUsers.length(); i++) {
                            String specialUserId = specialUsers.getString(i);
                            if (specialUserId.equals(userId)) {
                                isSpecialUser = true;
                                break;
                            }
                        }

                        boolean finalIsSpecialUser = isSpecialUser;
                        requireActivity().runOnUiThread(() -> {
                            isPremiumUser = finalIsSpecialUser;
                            if (finalIsSpecialUser) {
                                specialIcon.setVisibility(View.VISIBLE);
                                animateSpecialIcon();
                            } else {
                                specialIcon.setVisibility(View.GONE);
                            }
                        });
                    }
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        specialIcon.setVisibility(View.GONE);
                        isPremiumUser = false;
                    });
                }
            }
        });
    }

    private void animateSpecialIcon() {
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                0.8f, 1.2f, 0.8f, 1.2f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleAnimation.setDuration(500);
        scaleAnimation.setRepeatCount(1);
        scaleAnimation.setRepeatMode(Animation.REVERSE);
        specialIcon.startAnimation(scaleAnimation);
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void loadUserProfile(String userId) {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            profileNameTextView.setText("Не авторизован");
            return;
        }

        String url = "https://api.vk.com/method/users.get" +
                "?user_ids=" + userId +
                "&access_token=" + accessToken +
                "&fields=photo_100" +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() ->
                        profileNameTextView.setText("Ошибка соединения"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        String errorMsg = error.getString("error_msg");
                        requireActivity().runOnUiThread(() ->
                                profileNameTextView.setText("Ошибка: " + errorMsg));
                        return;
                    }

                    JSONArray users = json.getJSONArray("response");
                    if (users.length() == 0) {
                        requireActivity().runOnUiThread(() ->
                                profileNameTextView.setText("Пользователь не найден"));
                        return;
                    }

                    JSONObject user = users.getJSONObject(0);
                    userFirstName = user.getString("first_name");
                    userLastName = user.getString("last_name");
                    String fullName = userFirstName + " " + userLastName;
                    String photoUrl = user.optString("photo_100", null);

                    requireActivity().runOnUiThread(() -> {
                        profileNameTextView.setText(fullName);
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(requireContext())
                                    .load(photoUrl)
                                    .placeholder(R.drawable.default_avatar)
                                    .error(R.drawable.default_avatar)
                                    .circleCrop()
                                    .into(profileAvatar);
                        }
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() ->
                            profileNameTextView.setText("Ошибка загрузки"));
                }
            }
        });
    }


    private View createMenuItemView(MenuItem item) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_menu, null);

        ImageView icon = view.findViewById(R.id.item_icon);
        TextView text = view.findViewById(R.id.item_text);

        icon.setImageResource(item.getIconRes());
        text.setText(item.getTitle());

        view.setOnClickListener(v -> {
            if (item.getFragmentClass() == null) {
                closeApplication();
            } else {
                navigateToFragment(item.getFragmentClass());
            }
        });

        return view;
    }

    private void closeApplication() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Закрыть приложение")
                .setMessage("Вы уверены, что хотите выйти?")
                .setPositiveButton("Да", (dialog, which) -> {
                    requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    requireActivity().finish();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void navigateToFragment(Class<? extends Fragment> fragmentClass) {
        try {
            Fragment fragment = fragmentClass.newInstance();
            FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
            transaction.replace(R.id.container, fragment);
            transaction.addToBackStack(null);
            transaction.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class MenuItem {
        private final String title;
        private final int iconRes;
        private final Class<? extends Fragment> fragmentClass;

        public MenuItem(String title, int iconRes, Class<? extends Fragment> fragmentClass) {
            this.title = title;
            this.iconRes = iconRes;
            this.fragmentClass = fragmentClass;
        }

        public String getTitle() {
            return title;
        }

        public int getIconRes() {
            return iconRes;
        }

        public Class<? extends Fragment> getFragmentClass() {
            return fragmentClass;
        }
    }
}