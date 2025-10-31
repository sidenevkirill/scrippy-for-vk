package ru.lisdevs.messenger.section;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.music.MyMusicFragment;
import ru.lisdevs.messenger.music.RecommendationFragment;
import ru.lisdevs.messenger.playlists.VkPlaylistsFragment;
import ru.lisdevs.messenger.search.MusicSearchFragment;
import ru.lisdevs.messenger.subscription.SubscriptionFragment;

public class SectionFragment extends Fragment {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private Toolbar toolbar;
    private SearchView searchView;
    private MenuItem searchMenuItem;
    private MyMusicFragment myMusicFragment;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_section, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        //toolbar.inflateMenu(R.menu.menu_home);

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_show_unlock) {
                navigateToFragment(new SubscriptionFragment());
                return true;
            }
            if (item.getItemId() == R.id.action_show_save) {
                navigateToFragment(new VkPlaylistsFragment());
                return true;
            }
            if (item.getItemId() == R.id.action_show_friends) {
               // navigateToFragment(new MyPlaylistsFragment());
                navigateToFragment(new VkPlaylistsFragment());
                return true;
            }
            if (item.getItemId() == R.id.action_show_search) {
                navigateToFragment(new MusicSearchFragment());
                return true;
            }
            return false;
        });

        viewPager = view.findViewById(R.id.view_pager);
        tabLayout = view.findViewById(R.id.tabs);

        // Используем getChildFragmentManager() для вложенных фрагментов
        viewPager.setAdapter(new TabsPagerAdapter(this));

        String[] tabTitles = new String[]{"Мои треки", "Рекомендации"};
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();

        // Получаем ссылку на MyMusicFragment
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateCurrentFragmentReference();
            }
        });

        return view;
    }

    private void updateCurrentFragmentReference() {
        Fragment fragment = getChildFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (fragment instanceof MyMusicFragment) {
            myMusicFragment = (MyMusicFragment) fragment;
        } else {
            myMusicFragment = null;
        }
    }

    private void setupSearchView() {
        searchView.setQueryHint("Поиск треков...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (myMusicFragment != null) {
                    myMusicFragment.filterTracks(newText);
                }
                return true;
            }
        });

        searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (myMusicFragment != null) {
                    myMusicFragment.resetSearch();
                }
                return true;
            }
        });
    }

    private void navigateToFragment(Fragment fragment) {
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
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
                    return new MyMusicFragment();
                case 2:
                    return new ru.lisdevs.messenger.local.LocalMusicFragment();
                case 1:
                    return new RecommendationFragment();
                default:
                    return new RecommendationFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}