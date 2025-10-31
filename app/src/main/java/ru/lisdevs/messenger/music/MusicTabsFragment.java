package ru.lisdevs.messenger.music;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.algoritms.AlgoritmPlaylistsFragment;
import ru.lisdevs.messenger.genre.GenreFragment;
import ru.lisdevs.messenger.groups.GroupsTabFragment;
import ru.lisdevs.messenger.local.SaveMusicFragment;
import ru.lisdevs.messenger.local.SaveSectionFragment;
import ru.lisdevs.messenger.playlists.VkPlaylistsFragment;
import ru.lisdevs.messenger.search.GroupsSearchFragment;
import ru.lisdevs.messenger.search.MusicSearchFragment;


public class MusicTabsFragment extends Fragment {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private GroupsPagerAdapter pagerAdapter;
    private Toolbar toolbar;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_music_tabs, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        viewPager = view.findViewById(R.id.view_pager);
        tabLayout = view.findViewById(R.id.tabs);

        setupToolbar();
        setupViewPager();
        setupTabs();

        return view;
    }

    private void setupViewPager() {
        pagerAdapter = new GroupsPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
    }

    private void setupTabs() {
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("МОИ ТРЕКИ");
                    break;
                case 1:
                    tab.setText("РЕКОМЕНДАЦИИ");
                    break;
                case 2:
                    tab.setText("ПЛЕЙЛИСТЫ");
                    break;
                case 3:
                    tab.setText("АЛГОРИТМЫ");
                    break;
                case 4:
                    tab.setText("ЖАНРЫ");
                    break;
            }
        }).attach();
    }

    // Вложенный класс адаптера для ViewPager
    public static class GroupsPagerAdapter extends FragmentStateAdapter {

        public GroupsPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return MyMusicPlaylistsFragment.newInstance();
                case 1:
                    return RecommendationFragment.newInstance();
                case 2:
                    return VkPlaylistsFragment.newInstance();
                case 3:
                    return AlgoritmPlaylistsFragment.newInstance();
                case 4:
                    return GenreFragment.newInstance();
                default:
                    return MyMusicPlaylistsFragment.newInstance();
            }
        }

        @Override
        public int getItemCount() {
            return 5;
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.search, menu); // Загружаем меню программно
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void setupToolbar() {
        if (toolbar != null && getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
            }


        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            navigateToGroupsSearchFragment();
            return true;
        }

         if (item.getItemId() == R.id.action_show_playlists) {
             navigateToSaveFragment();
            return true;
        }

         if (item.getItemId() == R.id.action_show_genre) {
             navigateToGenreFragment();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void navigateToGroupsSearchFragment() {
        MusicSearchFragment groupsSearchFragment = new MusicSearchFragment();
        Bundle args = new Bundle();
        // args.putString("GROUP_ID", String.valueOf(friendId));
        groupsSearchFragment.setArguments(args);

        // Используем getChildFragmentManager() для вложенных фрагментов
        // или getParentFragmentManager() в зависимости от структуры
        FragmentManager fragmentManager = getParentFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.container, groupsSearchFragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void navigateToSaveFragment() {
        SaveSectionFragment groupsSearchFragment = new SaveSectionFragment();
        Bundle args = new Bundle();
        // args.putString("GROUP_ID", String.valueOf(friendId));
        groupsSearchFragment.setArguments(args);

        // Используем getChildFragmentManager() для вложенных фрагментов
        // или getParentFragmentManager() в зависимости от структуры
        FragmentManager fragmentManager = getParentFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.container, groupsSearchFragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void navigateToGenreFragment() {
        GenreFragment groupsSearchFragment = new GenreFragment();
        Bundle args = new Bundle();
        // args.putString("GROUP_ID", String.valueOf(friendId));
        groupsSearchFragment.setArguments(args);

        // Используем getChildFragmentManager() для вложенных фрагментов
        // или getParentFragmentManager() в зависимости от структуры
        FragmentManager fragmentManager = getParentFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.container, groupsSearchFragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

}

