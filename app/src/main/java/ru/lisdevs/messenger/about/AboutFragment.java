package ru.lisdevs.messenger.about;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.Arrays;
import java.util.List;

import ru.lisdevs.messenger.R;

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
                        "https://sidenevkirill.github.io/"
                ),
                new MenuItem(
                        "Паблик ВКонтакте",
                        "Новости и обсуждения",
                        R.drawable.vk,
                        "https://vk.com/club231807504"
                ),
                new MenuItem(
                        "Telegram канал",
                        "Оперативные уведомления",
                        R.drawable.telegram,
                        "https://t.me/railcinec"
                ),
                new MenuItem(
                        "Разработчик",
                        "Сиденёв Кирилл",
                        R.drawable.account,
                        "https://t.me/lisdevs"
                ),
                new MenuItem(
                        "Исходный код",
                        "Github",
                        R.drawable.github,
                        "https://github.com/sidenevkirill/scrippy"
                ),
                new MenuItem(
                        "Ozon банк",
                        CARD_NUMBER,
                        R.drawable.heart_outline,
                        "copy_card"
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

        // Для пункта "Поддержать проект" добавляем специальную обработку
        if ("copy_card".equals(item.getUrl())) {
            view.setOnClickListener(v -> copyCardNumberToClipboard());

            // Добавляем визуальный индикатор что это можно копировать
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
        } else {
            view.setOnClickListener(v -> openWebsite(item.getUrl()));
        }

        return view;
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

    private void openWebsite(String url) {
        try {
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), R.string.no_browser, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.link_error, Toast.LENGTH_SHORT).show();
        }
    }

    private static class MenuItem {
        private final String title;
        private final String description;
        private final int iconRes;
        private final String url;

        public MenuItem(String title, String description, int iconRes, String url) {
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
            this.url = url;
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
    }
}