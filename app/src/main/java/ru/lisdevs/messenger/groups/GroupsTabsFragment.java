package ru.lisdevs.messenger.groups;

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
import ru.lisdevs.messenger.search.GroupsSearchFragment;

public class GroupsTabsFragment extends Fragment {

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
        View view = inflater.inflate(R.layout.fragment_groups_tabs, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);

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
                    tab.setText("МОИ ГРУППЫ");
                    break;
                case 1:
                    tab.setText("ПОПУЛЯРНЫЕ");
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
                    return GroupsTabFragment.newInstance(GroupsTabFragment.TAB_TYPE_MY_GROUPS);
                case 1:
                    return GroupsTabFragment.newInstance(GroupsTabFragment.TAB_TYPE_ALL_GROUPS);
                    //return GroupsSearchFragment.newInstance();
                default:
                    return GroupsTabFragment.newInstance(GroupsTabFragment.TAB_TYPE_MY_GROUPS);
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.search_groups, menu); // Загружаем меню программно
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void setupToolbar() {
        if (toolbar != null && getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
            }

            toolbar.setNavigationOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            navigateToGroupsSearchFragment();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void navigateToGroupsSearchFragment() {
        GroupsSearchFragment groupsSearchFragment = new GroupsSearchFragment();
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
