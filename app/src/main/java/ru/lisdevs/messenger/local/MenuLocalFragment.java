package ru.lisdevs.messenger.local;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.about.AboutFragment;
import ru.lisdevs.messenger.genre.GenreFragment;
import ru.lisdevs.messenger.offline.OfflineMusicFragment;
import ru.lisdevs.messenger.settings.SettingsFragment;

public class MenuLocalFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_menu_local, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout menuContainer = view.findViewById(R.id.menu_container);

        // Создаем элементы меню
        List<MenuItem> menuItems = new ArrayList<>();
        menuItems.add(new MenuItem("Главная", R.drawable.home, OfflineMusicFragment.class));
        menuItems.add(new MenuItem("Плейлисты", R.drawable.ic_playlist, LocalPlaylistsFragment.class));
        menuItems.add(new MenuItem("Настройки", R.drawable.ic_vector_outline_settings, SettingsFragment.class));
        menuItems.add(new MenuItem("О приложении", R.drawable.information_outline, AboutFragment.class));
        menuItems.add(new MenuItem("Закрыть", R.drawable.close, GenreFragment.class));

        // Добавляем элементы в меню
        for (MenuItem item : menuItems) {
            View menuItemView = createMenuItemView(item);
            menuContainer.addView(menuItemView);
        }
    }

    private View createMenuItemView(MenuItem item) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_menu, null);

        ImageView icon = view.findViewById(R.id.item_icon);
        TextView text = view.findViewById(R.id.item_text);

        icon.setImageResource(item.getIconRes());
        text.setText(item.getTitle());

        view.setOnClickListener(v -> navigateToFragment(item.getFragmentClass()));

        return view;
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
