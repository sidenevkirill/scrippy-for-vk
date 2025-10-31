package ru.lisdevs.messenger.search;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.music.LocalMusicFragment;


public class SearchPageFragment extends Fragment {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private Toolbar toolbar;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_page_search, container, false);

        viewPager = view.findViewById(R.id.view_pager);
        tabLayout = view.findViewById(R.id.tabs);

        // Устанавливаем адаптер для ViewPager2
        viewPager.setAdapter(new TabsPagerAdapter(this));

        // Названия вкладок
        String[] tabTitles = new String[]{"ТРЕКИ", "АЛЬБОМЫ"};

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
                    return new MusicSearchFragment();
                case 2:
                    return new AlbumSearchFragment();
                default:
                    return new AlbumSearchFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2; // Количество вкладок
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_local, menu);
        Log.d("MyFragment", "onCreateOptionsMenu");
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Log.d("MyFragment", "onOptionsItemSelected: " + item.getItemId());
        if (item.getItemId() == R.id.action_show_local) {
            Toast.makeText(getContext(), "Переход выполнен", Toast.LENGTH_SHORT).show();

            // Используйте getChildFragmentManager() если внутри другого фрагмента
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, new LocalMusicFragment())
                    .addToBackStack(null)
                    .commit();

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
