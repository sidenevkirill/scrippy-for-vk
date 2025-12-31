package ru.lisdevs.messenger.about;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.Arrays;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.utils.CustomTabsHelper;

import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;

public class AboutFragment extends Fragment {

    private MaterialToolbar toolbar;
    private static final String CARD_NUMBER = "2204320312128889";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        toolbar = view.findViewById(R.id.toolbar);
        setupToolbar();

        LinearLayout menuContainer = view.findViewById(R.id.menu_container);

        // Добавляем карточку с описанием приложения
        // addAppInfoCard(menuContainer);

        // Создаем и добавляем элементы меню
        createMenuItems(menuContainer);
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
            toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        }
    }

    private void createMenuItems(LinearLayout container) {
        List<MenuItem> menuItems = Arrays.asList(
                new MenuItem(
                        "Наш сайт",
                        "Официальный сайт проекта",
                        R.drawable.public_24px,
                        "https://sidenevkirill.github.io/",
                        MenuItem.TYPE_EXTERNAL_LINK
                ),
                new MenuItem(
                        "Паблик ВКонтакте",
                        "Новости и обсуждения",
                        R.drawable.vk,
                        "https://vk.com/club231807504",
                        MenuItem.TYPE_EXTERNAL_LINK
                ),
                new MenuItem(
                        "Telegram канал",
                        "Оперативные уведомления",
                        R.drawable.telegram,
                        "https://t.me/railcinec",
                        MenuItem.TYPE_EXTERNAL_LINK
                ),
                new MenuItem(
                        "Разработчик",
                        "Сиденёв Кирилл",
                        R.drawable.account,
                        "https://t.me/lisdevs",
                        MenuItem.TYPE_EXTERNAL_LINK
                ),
                new MenuItem(
                        "Исходный код",
                        "Github",
                        R.drawable.github,
                        "https://github.com/sidenevkirill/scrippy",
                        MenuItem.TYPE_EXTERNAL_LINK
                ),
                new MenuItem(
                        "Ozon банк",
                        CARD_NUMBER,
                        R.drawable.heart_outline,
                        "copy_card",
                        MenuItem.TYPE_COPY_CARD
                )
        );

        for (MenuItem item : menuItems) {
            View menuItem = createMenuItemView(item);
            container.addView(menuItem);

            // Добавляем разделитель между элементами
            if (menuItems.indexOf(item) < menuItems.size() - 1) {
                container.addView(createDivider());
            }
        }
    }

    private View createMenuItemView(MenuItem item) {
        View view = LayoutInflater.from(getContext())
                .inflate(R.layout.item_about, null);

        ImageView icon = view.findViewById(R.id.item_icon);
        TextView title = view.findViewById(R.id.item_title);
        TextView description = view.findViewById(R.id.item_description);

        icon.setImageResource(item.getIconRes());
        title.setText(item.getTitle());
        description.setText(item.getDescription());

        // Устанавливаем обработчик клика в зависимости от типа элемента
        view.setOnClickListener(v -> handleMenuItemClick(item));

        // Для пункта "Поддержать проект" добавляем специальную обработку
        if (item.getType() == MenuItem.TYPE_COPY_CARD) {
            description.setTextColor(ContextCompat.getColor(requireContext(), R.color.group_name_color));

            // Добавляем иконку копирования из ресурсов в описание
            SpannableString spannable = new SpannableString(item.getDescription() + "  ");

            // Создаем ImageSpan для иконки копирования
            Drawable copyIcon = ContextCompat.getDrawable(requireContext(), R.drawable.content_copy);
            if (copyIcon != null) {
                copyIcon.setBounds(0, 0,
                        (int) description.getTextSize(),
                        (int) description.getTextSize());
                ImageSpan imageSpan = new ImageSpan(copyIcon, ImageSpan.ALIGN_BASELINE);

                // Добавляем ImageSpan в конец текста
                spannable.setSpan(imageSpan,
                        spannable.length() - 1, spannable.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            description.setText(spannable);
        }

        return view;
    }

    private void handleMenuItemClick(MenuItem item) {
        switch (item.getType()) {
            case MenuItem.TYPE_COPY_CARD:
                copyCardNumberToClipboard();
                break;
            case MenuItem.TYPE_EXTERNAL_LINK:
                openExternalLink(item.getUrl());
                break;
            case MenuItem.TYPE_DEFAULT_BROWSER:
                openUrlInDefaultBrowser(item.getUrl());
                break;
        }
    }

    private void copyCardNumberToClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Номер карты", CARD_NUMBER);
            clipboard.setPrimaryClip(clip);

            // Показываем красивый Toast
            Toast.makeText(requireContext(),
                    "Номер карты скопирован: " + CARD_NUMBER,
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Ошибка копирования",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Открывает ссылку через дефолтный браузер системы
     * Этот метод открывает ссылку в браузере, который пользователь выбрал как основной
     */
    private void openUrlInDefaultBrowser(String url) {
        try {
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }

            // Создаем intent с действием ACTION_VIEW
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

            // Флаг для открытия в новой задаче (новом окне браузера)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Проверяем, есть ли приложение, которое может обработать этот intent
            PackageManager packageManager = requireActivity().getPackageManager();
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent);
            } else {
                // Если нет дефолтного браузера, показываем сообщение
                showNoBrowserDialog();
            }
        } catch (ActivityNotFoundException e) {
            // Если активность не найдена (нет браузера)
            showNoBrowserDialog();
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.link_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Открывает внешнюю ссылку (альтернативный метод)
     */
    private void openExternalLink(String url) {
        try {
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

            // Устанавливаем флаги для открытия в дефолтном браузере
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Создаем Chooser для выбора браузера (опционально)
            String title = "Выберите браузер";
            Intent chooser = Intent.createChooser(intent, title);

            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(chooser);
            } else {
                showNoBrowserDialog();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.link_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Показывает диалог, если не найден браузер
     */
    private void showNoBrowserDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Браузер не найден")
                .setMessage("На устройстве не найден браузер. Установите браузер из Google Play.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Открыть Google Play", (dialog, which) -> {
                    // Открываем Google Play для установки браузера
                    openGooglePlayForBrowser();
                })
                .show();
    }

    /**
     * Открывает Google Play для поиска браузеров
     */
    private void openGooglePlayForBrowser() {
        try {
            // Intent для открытия Google Play с поиском браузеров
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://search?q=browser"));

            // Проверяем, установлен ли Google Play
            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // Если Google Play не установлен, открываем веб-версию
                Intent webIntent = new Intent(Intent.ACTION_VIEW);
                webIntent.setData(Uri.parse("https://play.google.com/store/search?q=browser&c=apps"));
                if (webIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                    startActivity(webIntent);
                }
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Не удалось открыть магазин приложений", Toast.LENGTH_SHORT).show();
        }
    }

    private View createDivider() {
        View divider = new View(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.divider_height));
        params.setMargins(
                getResources().getDimensionPixelSize(R.dimen.divider_margin_start),
                getResources().getDimensionPixelSize(R.dimen.divider_margin_vertical),
                getResources().getDimensionPixelSize(R.dimen.divider_margin_end),
                getResources().getDimensionPixelSize(R.dimen.divider_margin_vertical));
        divider.setLayoutParams(params);
        divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.dividers));
        return divider;
    }

    /**
     * Класс для представления пункта меню
     */
    private static class MenuItem {
        // Типы элементов меню
        public static final int TYPE_EXTERNAL_LINK = 1;
        public static final int TYPE_COPY_CARD = 2;
        public static final int TYPE_DEFAULT_BROWSER = 3;

        private final String title;
        private final String description;
        private final int iconRes;
        private final String url;
        private final int type;

        public MenuItem(String title, String description, int iconRes, String url, int type) {
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
            this.url = url;
            this.type = type;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public int getIconRes() {
            return iconRes;
        }

        public String getUrl() {
            return url;
        }

        public int getType() {
            return type;
        }
    }

    /**
     * Дополнительные утилиты для работы с браузерами
     */

    /**
     * Проверяет, установлен ли дефолтный браузер
     */
    private boolean isDefaultBrowserAvailable() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
        return intent.resolveActivity(requireActivity().getPackageManager()) != null;
    }

    /**
     * Получает список установленных браузеров
     */
    private List<ResolveInfo> getInstalledBrowsers() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
        PackageManager packageManager = requireActivity().getPackageManager();
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
    }

    /**
     * Открывает ссылку в Chrome, если он установлен
     */
    private void openInChromeIfAvailable(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage("com.android.chrome");

            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // Chrome не установлен, открываем через дефолтный браузер
                openUrlInDefaultBrowser(url);
            }
        } catch (Exception e) {
            openUrlInDefaultBrowser(url);
        }
    }

    /**
     * Открывает ссылку с поддержкой Custom Tabs (если доступно)
     */
    private void openWithCustomTabs(String url) {
        try {
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }

            // Проверяем, доступны ли Custom Tabs
            String packageName = CustomTabsHelper.getPackageNameToUse(requireContext());
            if (packageName != null) {
                // Используем Custom Tabs
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                CustomTabsIntent customTabsIntent = builder.build();
                customTabsIntent.launchUrl(requireContext(), Uri.parse(url));
            } else {
                // Используем обычный дефолтный браузер
                openUrlInDefaultBrowser(url);
            }
        } catch (Exception e) {
            openUrlInDefaultBrowser(url);
        }
    }
}