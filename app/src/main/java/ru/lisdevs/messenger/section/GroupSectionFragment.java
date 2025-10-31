package ru.lisdevs.messenger.section;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.friends.FriendsFragment;
import ru.lisdevs.messenger.groups.GroupsFragment;

public class GroupSectionFragment extends Fragment {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_section, container, false);

        viewPager = view.findViewById(R.id.view_pager);
        tabLayout = view.findViewById(R.id.tabs);

        // Устанавливаем адаптер для ViewPager2
        viewPager.setAdapter(new TabsPagerAdapter(this));

        // Названия вкладок
        String[] tabTitles = new String[]{"Друзья", "Группы"};

        // Подключение TabLayout и ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();

        return view;
    }

    private class TabsPagerAdapter extends FragmentStateAdapter {

        public TabsPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new FriendsFragment();
                case 1:
                    return new GroupsFragment();
                default:
                    return new FriendsFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2; // Количество вкладок
        }
    }
}