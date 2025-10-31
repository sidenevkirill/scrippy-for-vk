package ru.lisdevs.messenger.album;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.friends.PhotoViewerFragment;
import ru.lisdevs.messenger.friends.UserProfileFragment;
import ru.lisdevs.messenger.utils.TokenManager;
import ru.lisdevs.messenger.utils.VkAuthorizer;


public class PhotoTabsFragment extends Fragment {
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private ProfilePagerAdapter pagerAdapter;
    private Toolbar toolbar;
    private TextView textViewUserName, textViewUserId;
    private MaterialButton writeMessageButton;
    private MaterialButton friendActionButton;
    private MaterialButton friendsButton;
    private MaterialButton groupsButton;
    private ImageView profileAvatar;
    private long userId;
    private String userName;
    private String userFirstName;
    private String userLastName;
    private String userAvatarUrl;
    private OkHttpClient httpClient;
    private static final String API_VERSION = "5.131";
    private boolean isTestMode = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        loadUserDataFromVkAuthorizer();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_profile, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_share_profile) {
            shareProfile();
            return true;
        } else if (id == R.id.action_copy_link) {
            copyProfileLink();
            return true;
        } else if (id == R.id.action_copy_id) {
            copyUserId();
            return true;
        } else if (id == R.id.action_profile_options) {
            showProfileBottomSheet();
            return true;
        } else if (id == R.id.copyToken) {
            copyToken();
            return true;
        } else if (id == android.R.id.home) {
            requireActivity().onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photos_tabs, container, false);

        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);
        toolbar = view.findViewById(R.id.toolbar);
        textViewUserName = view.findViewById(R.id.user_name);
        writeMessageButton = view.findViewById(R.id.download);
        friendActionButton = view.findViewById(R.id.save);
        textViewUserId = view.findViewById(R.id.artistsNamesText);
        friendsButton = view.findViewById(R.id.friends_button);
        groupsButton = view.findViewById(R.id.groups_button);
        profileAvatar = view.findViewById(R.id.user_avatars);

        if (profileAvatar == null) {
            profileAvatar = view.findViewById(R.id.user_avatars);
        }

        if (profileAvatar == null) {
            Log.w("ProfileFragment", "Avatar ImageView not found in layout");
        }

        setupToolbar();
        setupButtonsForOwnProfile();
        setupFriendsButton();
        setupGroupsButton();
        setupViewPager();

        displayUserDataFromStorage();

        if (!isTestMode && TokenManager.getInstance(requireContext()).isTokenValid()) {
            loadUserProfileFromApi();
        } else if (isTestMode) {
            setTestAvatar();
        }

        return view;
    }

    private void setupToolbar() {
        if (toolbar != null && getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                activity.getSupportActionBar().setTitle("Мои фотографии");
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

            toolbar.setOnMenuItemClickListener(item -> {
                return onOptionsItemSelected(item);
            });
        }
    }

    private void setupFriendsButton() {
        if (friendsButton != null) {
            friendsButton.setOnClickListener(v -> {
                openMyFriendsFragment();
            });
        }
    }

    private void setupGroupsButton() {
        if (groupsButton != null) {
            groupsButton.setOnClickListener(v -> {
                openMyGroupsFragment();
            });
        }
    }

    private void openMyFriendsFragment() {
        MyFriendsFragment friendsFragment = MyFriendsFragment.newInstance(userId, userName);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, friendsFragment)
                .addToBackStack("my_friends_fragment")
                .commit();

        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle("Мои друзья");
            }
        }
    }

    private void openMyGroupsFragment() {
        MyGroupsFragment groupsFragment = MyGroupsFragment.newInstance(userId, userName);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.container, groupsFragment)
                .addToBackStack("my_groups_fragment")
                .commit();

        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle("Мои группы");
            }
        }
    }

    private void showProfileBottomSheet() {
        if (!isAdded()) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_vk_photos, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        TextView copyProfileLink = bottomSheetView.findViewById(R.id.copyButton);
        TextView copyUserId = bottomSheetView.findViewById(R.id.copyId);
        TextView shareProfile = bottomSheetView.findViewById(R.id.shareButton);
        TextView refreshProfile = bottomSheetView.findViewById(R.id.refreshButton);
        TextView openInBrowser = bottomSheetView.findViewById(R.id.openInBrowserButton);
        TextView copyToken = bottomSheetView.findViewById(R.id.copyToken);

        if (copyUserId != null) {
            copyUserId.setOnClickListener(v -> {
                copyUserId();
                bottomSheetDialog.dismiss();
            });
        }

        if (copyProfileLink != null) {
            copyProfileLink.setOnClickListener(v -> {
                copyProfileLink();
                bottomSheetDialog.dismiss();
            });
        }

        if (shareProfile != null) {
            shareProfile.setOnClickListener(v -> {
                shareProfile();
                bottomSheetDialog.dismiss();
            });
        }

        if (refreshProfile != null) {
            refreshProfile.setOnClickListener(v -> {
                refreshProfileData();
                bottomSheetDialog.dismiss();
            });
        }

        if (openInBrowser != null) {
            openInBrowser.setOnClickListener(v -> {
                openProfileInBrowser();
                bottomSheetDialog.dismiss();
            });
        }

        if (copyToken != null) {
            copyToken.setOnClickListener(v -> {
                copyToken();
                bottomSheetDialog.dismiss();
            });
        }

        bottomSheetDialog.show();
        bottomSheetDialog.getWindow().getAttributes().windowAnimations = R.style.BottomSheetAnimation;
    }

    private void shareProfile() {
        try {
            String profileUrl = "https://vk.ru/photos" + userId;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Мой профиль ВКонтакте");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Мой профиль ВКонтакте: " + profileUrl);
            startActivity(Intent.createChooser(shareIntent, "Поделиться профилем"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Не удалось поделиться профилем", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToken() {
        String token = TokenManager.getInstance(getContext()).getToken();
        copyToClipboard("Токен", token);
        showToast("Токен скопирован");
    }

    private void copyProfileLink() {
        copyToClipboard("Ссылка на профиль", "https://vk.ru/photos" + userId);
        showToast("Ссылка скопирована");
    }

    private void copyUserId() {
        copyToClipboard("ID пользователя", String.valueOf(userId));
        showToast("ID скопирован");
    }

    private void refreshProfileData() {
        if (!isTestMode && TokenManager.getInstance(requireContext()).isTokenValid()) {
            loadUserProfileFromApi();
            Toast.makeText(requireContext(), "Обновление профиля...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "Невозможно обновить профиль", Toast.LENGTH_SHORT).show();
        }
    }

    private void openProfileInBrowser() {
        try {
            String profileUrl = "https://vk.com/id" + userId;
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl));
            startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Не удалось открыть в браузере", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String label, String text) {
        if (!isAdded()) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
    }

    private void showToast(String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void loadUserDataFromVkAuthorizer() {
        VkAuthorizer.UserData userData = VkAuthorizer.getUserData(requireContext());

        if (userData != null) {
            try {
                userId = Long.parseLong(userData.userId);
                userName = userData.fullName != null ? userData.fullName : userData.userName;
                userAvatarUrl = userData.avatarUrl;

                Log.d("ProfileFragment", "Loaded from VkAuthorizer: ID=" + userId + ", Name=" + userName + ", Avatar=" + userAvatarUrl);
            } catch (NumberFormatException e) {
                Log.e("ProfileFragment", "Invalid user ID from VkAuthorizer: " + userData.userId);
                userId = 0;
                userName = "Неизвестный пользователь";
                userAvatarUrl = null;
            }
        } else {
            String tokenManagerUserId = TokenManager.getInstance(requireContext()).getUserId();
            String tokenManagerUserName = TokenManager.getInstance(requireContext()).getFullName();
            String tokenManagerAvatarUrl = TokenManager.getInstance(requireContext()).getPhotoUrl();

            if (tokenManagerUserId != null && !tokenManagerUserId.isEmpty()) {
                try {
                    userId = Long.parseLong(tokenManagerUserId);
                    userName = tokenManagerUserName != null ? tokenManagerUserName : "Пользователь";
                    userAvatarUrl = tokenManagerAvatarUrl;

                    VkAuthorizer.saveUserData(requireContext(), tokenManagerUserId,
                            extractUserName(tokenManagerUserName), tokenManagerUserName, tokenManagerAvatarUrl);

                    Log.d("ProfileFragment", "Loaded from TokenManager: ID=" + userId + ", Name=" + userName + ", Avatar=" + userAvatarUrl);
                } catch (NumberFormatException e) {
                    Log.e("ProfileFragment", "Invalid user ID from TokenManager: " + tokenManagerUserId);
                    userId = 0;
                    userName = "Неизвестный пользователь";
                    userAvatarUrl = null;
                }
            } else {
                userId = 0;
                userName = null;
                userAvatarUrl = null;
                Log.w("ProfileFragment", "No user data found, using test mode");
            }
        }

        isTestMode = userId == 123456789L || userId == 0 || userName == null;
    }

    private String extractUserName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return "user";
        }
        return fullName.toLowerCase().replace(" ", ".");
    }

    private void displayUserDataFromStorage() {
        if (textViewUserName != null && userName != null) {
            textViewUserName.setText(userName);
        } else if (textViewUserName != null) {
            textViewUserName.setText("Мой профиль");
        }

        if (textViewUserId != null) {
            textViewUserId.setText("@id" + userId);
        }

        loadAvatarFromStorage();

        if (userName != null) {
            updateToolbarTitle(userName);
        } else {
            updateToolbarTitle("Мой профиль");
        }
    }

    private void loadAvatarFromStorage() {
        if (profileAvatar == null) return;

        if (userAvatarUrl != null && !userAvatarUrl.isEmpty()) {
            loadAvatarFromUrl(userAvatarUrl);
            Log.d("ProfileFragment", "Avatar loaded from storage: " + userAvatarUrl);
        } else {
            setDefaultAvatar();
        }
    }

    private void setDefaultAvatar() {
        if (profileAvatar != null) {
            Glide.with(requireContext())
                    .load(R.drawable.default_avatar)
                    .circleCrop()
                    .into(profileAvatar);
        }
    }

    private void setTestAvatar() {
        if (profileAvatar != null) {
            String testAvatarUrl = "https://via.placeholder.com/200x200/4CAF50/FFFFFF?text=" +
                    (userName != null ? URLEncoder.encode(userName.substring(0, 1).toUpperCase()) : "U");

            Glide.with(requireContext())
                    .load(testAvatarUrl)
                    .placeholder(R.drawable.default_avatar)
                    .error(R.drawable.default_avatar)
                    .circleCrop()
                    .into(profileAvatar);

            Log.d("ProfileFragment", "Test avatar set: " + testAvatarUrl);
        }
    }

    private void loadUserProfileFromApi() {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            Log.w("ProfileFragment", "No access token available");
            return;
        }

        String url = "https://api.vk.com/method/users.get" +
                "?user_ids=" + userId +
                "&access_token=" + accessToken +
                "&fields=photo_200,photo_100,first_name,last_name" +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "KateMobileAndroid/56 lite-447 (Android 6.0; SDK 23; x86; Google Android SDK built for x86; en)")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("ProfileFragment", "Failed to load user profile from API: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        String errorMsg = error.getString("error_msg");
                        Log.e("ProfileFragment", "API error: " + errorMsg);
                        return;
                    }

                    JSONArray users = json.getJSONArray("response");
                    if (users.length() == 0) {
                        Log.w("ProfileFragment", "User not found in API response");
                        return;
                    }

                    JSONObject user = users.getJSONObject(0);
                    String firstName = user.getString("first_name");
                    String lastName = user.getString("last_name");
                    String fullName = firstName + " " + lastName;

                    String photoUrl = user.optString("photo_200",
                            user.optString("photo_100", null));

                    userName = fullName;
                    userFirstName = firstName;
                    userLastName = lastName;
                    userAvatarUrl = photoUrl;

                    VkAuthorizer.saveProfileData(requireContext(),
                            String.valueOf(userId), firstName, lastName, photoUrl);

                    requireActivity().runOnUiThread(() -> {
                        if (textViewUserName != null) {
                            textViewUserName.setText(fullName);
                        }
                        updateToolbarTitle(fullName);

                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            loadAvatarFromUrl(photoUrl);
                        } else {
                            setDefaultAvatar();
                        }
                    });

                    Log.d("ProfileFragment", "User profile updated from API: " + fullName + ", Avatar: " + photoUrl);

                } catch (Exception e) {
                    Log.e("ProfileFragment", "Error processing API response: " + e.getMessage());
                }
            }
        });
    }

    private void loadAvatarFromUrl(String avatarUrl) {
        if (profileAvatar == null) return;

        Glide.with(requireContext())
                .load(avatarUrl)
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .circleCrop()
                .override(200, 200)
                .into(profileAvatar);

        Log.d("ProfileFragment", "Avatar loaded from URL: " + avatarUrl);
    }

    private void updateToolbarTitle(String title) {
        if (toolbar != null && getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle(title);
            }
        }
    }

    private void setupButtonsForOwnProfile() {
        if (writeMessageButton != null) {
            writeMessageButton.setVisibility(View.GONE);
        }

        if (friendActionButton != null) {
            friendActionButton.setVisibility(View.GONE);
        }
    }

    private void setupViewPager() {
        pagerAdapter = new ProfilePagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("ФОТО");
                    break;
                case 1:
                    tab.setText("АЛЬБОМЫ");
                    break;
                case 2:
                    tab.setText("ГРУППЫ");
                    break;
                case 3:
                    tab.setText("ПОДАРКИ");
                    break;
            }
        }).attach();
    }

    public static class ProfilePagerAdapter extends FragmentStateAdapter {
        private final PhotoTabsFragment fragment;

        public ProfilePagerAdapter(PhotoTabsFragment fragment) {
            super(fragment);
            this.fragment = fragment;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return PhotosTabFragment.newInstance(fragment.userId, fragment.userName);
                case 1:
                    return AlbumsFragment.newInstance();
                case 2:
                    return MyGroupsFragment.newInstance(fragment.userId, fragment.userName);
                case 3:
                    return GiftsTabFragment.newInstance(fragment.userId, fragment.userName);
                default:
                    return PhotosTabFragment.newInstance(fragment.userId, fragment.userName);
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    public static class MyFriendsFragment extends Fragment {
        private RecyclerView recyclerViewFriends;
        private FriendsAdapter friendsAdapter;
        private List<FriendItem> friendList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;
        private TextView textViewFriendsCount;
        private ProgressBar progressBar;
        private Button retryButton;

        private long userId;
        private String userName;
        private OkHttpClient httpClient;
        private boolean isLoading = false;

        public static MyFriendsFragment newInstance(long userId, String userName) {
            MyFriendsFragment fragment = new MyFriendsFragment();
            Bundle args = new Bundle();
            args.putLong("user_id", userId);
            args.putString("user_name", userName);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Bundle args = getArguments();
            if (args != null) {
                userId = args.getLong("user_id");
                userName = args.getString("user_name");
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_groups_full, container, false);

            recyclerViewFriends = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            textViewFriendsCount = view.findViewById(R.id.count);
            progressBar = view.findViewById(R.id.progressBar);
            retryButton = view.findViewById(R.id.retryButton);

            setupUI();
            setupSwipeRefresh();
            setupRetryButton();
            setupToolbar();

            if (isTestAccount()) {
                loadTestFriends();
            } else {
                fetchUserFriends();
            }

            return view;
        }

        private void setupUI() {
            LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
            recyclerViewFriends.setLayoutManager(layoutManager);
            friendsAdapter = new FriendsAdapter(friendList);
            recyclerViewFriends.setAdapter(friendsAdapter);
        }

        private void setupToolbar() {
            if (getActivity() instanceof AppCompatActivity) {
                ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setDisplayHomeAsUpEnabled(true);
                    actionBar.setTitle("Мои друзья");

                    Toolbar toolbar = getActivity().findViewById(R.id.toolbar);
                    if (toolbar != null) {
                        toolbar.setNavigationOnClickListener(v -> {
                            getActivity().onBackPressed();
                        });
                    }
                }
            }
        }

        private void setupSwipeRefresh() {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (isTestAccount()) {
                    loadTestFriends();
                } else {
                    fetchUserFriends();
                }
            });
        }

        private void setupRetryButton() {
            retryButton.setOnClickListener(v -> {
                if (isTestAccount()) {
                    loadTestFriends();
                } else {
                    fetchUserFriends();
                }
            });
        }

        private boolean isTestAccount() {
            return userId == 123456789L || userId == 0 || userName == null;
        }

        private void loadTestFriends() {
            setLoadingState(true);
            List<FriendItem> testFriends = new ArrayList<>();

            testFriends.add(new FriendItem(
                    123456790L,
                    "Иван Иванов",
                    "https://via.placeholder.com/100x100/4ECDC4/FFFFFF?text=I",
                    "В сети",
                    true
            ));
            testFriends.add(new FriendItem(
                    123456791L,
                    "Мария Петрова",
                    "https://via.placeholder.com/100x100/45B7D1/FFFFFF?text=M",
                    "была в сети 2 часа назад",
                    false
            ));
            testFriends.add(new FriendItem(
                    123456792L,
                    "Алексей Сидоров",
                    "https://via.placeholder.com/100x100/96CEB4/FFFFFF?text=A",
                    "В сети",
                    true
            ));

            requireActivity().runOnUiThread(() -> {
                friendList.clear();
                friendList.addAll(testFriends);
                friendsAdapter.notifyDataSetChanged();
                setLoadingState(false);
                updateFriendsCountText();
                swipeRefreshLayout.setRefreshing(false);
            });
        }

        private void fetchUserFriends() {
            if (isLoading) return;

            String accessToken = TokenManager.getInstance(requireContext()).getToken();
            if (accessToken == null) {
                showErrorState("Требуется авторизация");
                return;
            }

            setLoadingState(true);
            hideErrorState();

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/friends.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("user_id", String.valueOf(userId))
                    .addQueryParameter("fields", "photo_100,online,last_seen")
                    .addQueryParameter("count", "100")
                    .addQueryParameter("v", "5.131")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "VKAndroidApp/1.0")
                    .build();

            isLoading = true;

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        isLoading = false;
                        setLoadingState(false);
                        showErrorState("Ошибка сети: " + e.getMessage());
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");
                                List<FriendItem> newFriendList = new ArrayList<>();

                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject friendObj = items.getJSONObject(i);
                                    FriendItem friendItem = parseFriendItem(friendObj);
                                    if (friendItem != null) {
                                        newFriendList.add(friendItem);
                                    }
                                }

                                requireActivity().runOnUiThread(() -> {
                                    isLoading = false;
                                    setLoadingState(false);
                                    swipeRefreshLayout.setRefreshing(false);

                                    friendList.clear();
                                    friendList.addAll(newFriendList);
                                    friendsAdapter.notifyDataSetChanged();
                                    updateFriendsCountText();

                                    if (friendList.isEmpty()) {
                                        showErrorState("У вас пока нет друзей");
                                    } else {
                                        showContentState();
                                    }
                                });
                            } else if (jsonObject.has("error")) {
                                handleApiError(jsonObject.getJSONObject("error"));
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                isLoading = false;
                                setLoadingState(false);
                                showErrorState("Ошибка обработки данных");
                            });
                        }
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            setLoadingState(false);
                            showErrorState("Ошибка сервера");
                        });
                    }
                }
            });
        }

        private FriendItem parseFriendItem(JSONObject friendObj) throws JSONException {
            long id = friendObj.getLong("id");
            String firstName = friendObj.getString("first_name");
            String lastName = friendObj.getString("last_name");
            String fullName = firstName + " " + lastName;

            String photoUrl = friendObj.optString("photo_100");
            boolean isOnline = friendObj.optInt("online", 0) == 1;

            String status = "В сети";
            if (!isOnline && friendObj.has("last_seen")) {
                JSONObject lastSeen = friendObj.getJSONObject("last_seen");
                long lastSeenTime = lastSeen.getLong("time");
                status = "был(а) в сети " + formatLastSeen(lastSeenTime);
            } else if (!isOnline) {
                status = "Не в сети";
            }

            return new FriendItem(id, fullName, photoUrl, status, isOnline);
        }

        private String formatLastSeen(long timestamp) {
            long currentTime = System.currentTimeMillis() / 1000;
            long diff = currentTime - timestamp;

            if (diff < 60) {
                return "только что";
            } else if (diff < 3600) {
                return (diff / 60) + " мин. назад";
            } else if (diff < 86400) {
                return (diff / 3600) + " ч. назад";
            } else {
                return (diff / 86400) + " дн. назад";
            }
        }

        private void handleApiError(JSONObject error) {
            int errorCode = error.optInt("error_code");
            String errorMsg = error.optString("error_msg");

            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                setLoadingState(false);

                switch (errorCode) {
                    case 5:
                        showErrorState("Ошибка авторизации");
                        break;
                    case 6:
                        showErrorState("Слишком много запросов");
                        break;
                    case 15:
                        showErrorState("Нет доступа к списку друзей");
                        break;
                    default:
                        showErrorState("Ошибка: " + errorMsg);
                }
            });
        }

        private void updateFriendsCountText() {
            Activity activity = getActivity();
            if (activity != null && textViewFriendsCount != null) {
                activity.runOnUiThread(() -> {
                    String countText = isTestAccount() ?
                            "Колл-во: " + friendList.size() :
                            "Колл-во: " + friendList.size();
                    textViewFriendsCount.setText(countText);
                });
            }
        }

        private void setLoadingState(boolean loading) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            recyclerViewFriends.setVisibility(loading ? View.GONE : View.VISIBLE);
            if (!loading) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }

        private void showErrorState(String message) {
            retryButton.setVisibility(View.VISIBLE);
            recyclerViewFriends.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        private void hideErrorState() {
            retryButton.setVisibility(View.GONE);
        }

        private void showContentState() {
            recyclerViewFriends.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        static class FriendItem {
            long id;
            String name;
            String photoUrl;
            String status;
            boolean isOnline;

            public FriendItem(long id, String name, String photoUrl, String status, boolean isOnline) {
                this.id = id;
                this.name = name;
                this.photoUrl = photoUrl;
                this.status = status;
                this.isOnline = isOnline;
            }
        }

        class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {
            private List<FriendItem> friends;

            public FriendsAdapter(List<FriendItem> friends) {
                this.friends = friends;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.list_item_friend, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                FriendItem item = friends.get(position);

                holder.nameText.setText(item.name);
                holder.statusText.setText(item.status);

                if (item.photoUrl != null && !item.photoUrl.isEmpty() && !item.photoUrl.equals("null")) {
                    Glide.with(requireContext())
                            .load(item.photoUrl)
                            .placeholder(R.drawable.img)
                            .error(R.drawable.img)
                            .circleCrop()
                            .into(holder.avatarImage);
                }

                if (item.isOnline) {
                    holder.onlineIndicator.setVisibility(View.VISIBLE);
                    holder.statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_500));
                } else {
                    holder.onlineIndicator.setVisibility(View.GONE);
                    holder.statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray));
                }

                holder.itemView.setOnClickListener(v -> {
                    openFriendProfile(item);
                });
            }

            @Override
            public int getItemCount() {
                return friends.size();
            }

            private void openFriendProfile(FriendItem friend) {
                UserProfileFragment fragment = new UserProfileFragment();
                Bundle args = new Bundle();
                args.putLong("friend_id", friend.id);
                args.putString("friend_name", friend.name);
                fragment.setArguments(args);

                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                                R.anim.slide_in_left, R.anim.slide_out_right)
                        .replace(R.id.container, fragment)
                        .addToBackStack("friend_profile")
                        .commit();
            }

            class ViewHolder extends RecyclerView.ViewHolder {
                ImageView avatarImage;
                TextView nameText, statusText;
                View onlineIndicator;

                public ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    avatarImage = itemView.findViewById(R.id.avatar_image);
                    nameText = itemView.findViewById(R.id.user);
                    statusText = itemView.findViewById(R.id.audio_count);
                    onlineIndicator = itemView.findViewById(R.id.online_indicator);
                }
            }
        }
    }

    public static class GiftsTabFragment extends Fragment {
        private static final String TAG = "GiftsTabFragment";

        private RecyclerView recyclerViewGifts;
        private GiftsAdapter giftsAdapter;
        private List<GiftItem> giftList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;
        private TextView textViewGiftsCount;
        private ProgressBar progressBar;
        private TextView errorTextView;
        private Button retryButton;

        private long userId;
        private String userName;
        private OkHttpClient httpClient;
        private boolean isLoading = false;

        // Кэш для имен пользователей
        private Map<String, String> userNamesCache = new HashMap<>();

        public static GiftsTabFragment newInstance(long userId, String userName) {
            GiftsTabFragment fragment = new GiftsTabFragment();
            Bundle args = new Bundle();
            args.putLong("user_id", userId);
            args.putString("user_name", userName);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Bundle args = getArguments();
            if (args != null) {
                userId = args.getLong("user_id");
                userName = args.getString("user_name");
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_gifts_tab, container, false);

            recyclerViewGifts = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            textViewGiftsCount = view.findViewById(R.id.count);
            progressBar = view.findViewById(R.id.progressBar);
            errorTextView = view.findViewById(R.id.emptyView);
            retryButton = view.findViewById(R.id.retryButton);

            setupUI();
            setupSwipeRefresh();
            setupRetryButton();

            if (isTestAccount()) {
                loadTestGifts();
            } else {
                fetchUserGifts();
            }

            return view;
        }

        private void setupUI() {
            GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 2);
            recyclerViewGifts.setLayoutManager(layoutManager);
            giftsAdapter = new GiftsAdapter(giftList);
            recyclerViewGifts.setAdapter(giftsAdapter);
        }

        private void setupSwipeRefresh() {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (isTestAccount()) {
                    loadTestGifts();
                } else {
                    fetchUserGifts();
                }
            });
        }

        private void setupRetryButton() {
            retryButton.setOnClickListener(v -> {
                if (isTestAccount()) {
                    loadTestGifts();
                } else {
                    fetchUserGifts();
                }
            });
        }

        private boolean isTestAccount() {
            return userId == 123456789L || userId == 0 || userName == null;
        }

        private void loadTestGifts() {
            setLoadingState(true);
            List<GiftItem> testGifts = new ArrayList<>();

            // Используем гарантированно работающие URL для тестирования
            testGifts.add(new GiftItem(
                    "1",
                    "https://via.placeholder.com/256x256/FF6B6B/FFFFFF?text=Gift+1",
                    "Красивый подарок",
                    "Анна Петрова",
                    System.currentTimeMillis() / 1000 - 86400,
                    "Сообщение к подарку"
            ));
            testGifts.add(new GiftItem(
                    "2",
                    "https://via.placeholder.com/256x256/4ECDC4/FFFFFF?text=Gift+2",
                    "Стильный подарок",
                    "Иван Сидоров",
                    System.currentTimeMillis() / 1000 - 172800,
                    "С наилучшими пожеланиями!"
            ));
            testGifts.add(new GiftItem(
                    "3",
                    "https://via.placeholder.com/256x256/45B7D1/FFFFFF?text=Gift+3",
                    "Эксклюзивный подарок",
                    "Мария Иванова",
                    System.currentTimeMillis() / 1000 - 259200,
                    "Любим и ценим!"
            ));
            testGifts.add(new GiftItem(
                    "4",
                    "https://via.placeholder.com/256x256/96CEB4/FFFFFF?text=Gift+4",
                    "Особенный подарок",
                    "Сергей Козлов",
                    System.currentTimeMillis() / 1000 - 345600,
                    "Поздравляю с праздником!"
            ));

            requireActivity().runOnUiThread(() -> {
                giftList.clear();
                giftList.addAll(testGifts);
                giftsAdapter.notifyDataSetChanged();
                setLoadingState(false);
                updateGiftsCountText();
                swipeRefreshLayout.setRefreshing(false);
            });
        }

        private void fetchUserGifts() {
            if (isLoading) return;

            String accessToken = TokenManager.getInstance(requireContext()).getToken();
            if (accessToken == null) {
                showErrorState("Требуется авторизация");
                return;
            }

            setLoadingState(true);
            hideErrorState();

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/gifts.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("user_id", String.valueOf(userId))
                    .addQueryParameter("count", "100")
                    .addQueryParameter("v", "5.131")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "VKAndroidApp/1.0")
                    .build();

            isLoading = true;

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        isLoading = false;
                        setLoadingState(false);
                        showErrorState("Ошибка сети: " + e.getMessage());
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");

                                // Сначала собираем все ID отправителей
                                Set<String> fromIds = new HashSet<>();
                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject giftObj = items.getJSONObject(i);
                                    String fromId = giftObj.optString("from_id");
                                    if (fromId != null && !fromId.isEmpty() && !fromId.equals("0")) {
                                        fromIds.add(fromId);
                                    }
                                }

                                // Загружаем имена пользователей
                                fetchUserNames(fromIds, items);

                            } else if (jsonObject.has("error")) {
                                handleApiError(jsonObject.getJSONObject("error"));
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                isLoading = false;
                                setLoadingState(false);
                                showErrorState("Ошибка обработки данных");
                            });
                        }
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            setLoadingState(false);
                            showErrorState("Ошибка сервера");
                        });
                    }
                }
            });
        }

        private void fetchUserNames(Set<String> userIds, JSONArray giftsArray) {
            if (userIds.isEmpty()) {
                try {
                    parseGiftsWithoutUserNames(giftsArray);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            String accessToken = TokenManager.getInstance(requireContext()).getToken();
            String userIdsString = TextUtils.join(",", userIds);

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/users.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("user_ids", userIdsString)
                    .addQueryParameter("fields", "first_name,last_name")
                    .addQueryParameter("v", "5.131")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    // Если не удалось получить имена, используем fallback
                    try {
                        parseGiftsWithoutUserNames(giftsArray);
                    } catch (JSONException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("response")) {
                                JSONArray users = json.getJSONArray("response");
                                for (int i = 0; i < users.length(); i++) {
                                    JSONObject user = users.getJSONObject(i);
                                    String userId = String.valueOf(user.getInt("id"));
                                    String firstName = user.optString("first_name", "");
                                    String lastName = user.optString("last_name", "");
                                    String fullName = firstName + " " + lastName;
                                    userNamesCache.put(userId, fullName);
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing user names", e);
                        }
                    }

                    // В любом случае парсим подарки
                    try {
                        parseGiftsWithUserNames(giftsArray);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        private void parseGiftsWithUserNames(JSONArray items) throws JSONException {
            List<GiftItem> newGiftList = new ArrayList<>();

            for (int i = 0; i < items.length(); i++) {
                JSONObject giftObj = items.getJSONObject(i);
                GiftItem giftItem = parseGiftItem(giftObj);
                if (giftItem != null) {
                    newGiftList.add(giftItem);
                }
            }

            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                setLoadingState(false);
                swipeRefreshLayout.setRefreshing(false);

                giftList.clear();
                giftList.addAll(newGiftList);
                giftsAdapter.notifyDataSetChanged();
                updateGiftsCountText();

                if (giftList.isEmpty()) {
                    showErrorState("Нет подарков");
                } else {
                    showContentState();
                }
            });
        }

        private void parseGiftsWithoutUserNames(JSONArray items) throws JSONException {
            List<GiftItem> newGiftList = new ArrayList<>();

            for (int i = 0; i < items.length(); i++) {
                JSONObject giftObj = items.getJSONObject(i);
                GiftItem giftItem = parseGiftItem(giftObj);
                if (giftItem != null) {
                    newGiftList.add(giftItem);
                }
            }

            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                setLoadingState(false);
                swipeRefreshLayout.setRefreshing(false);

                giftList.clear();
                giftList.addAll(newGiftList);
                giftsAdapter.notifyDataSetChanged();
                updateGiftsCountText();

                if (giftList.isEmpty()) {
                    showErrorState("Нет подарков");
                } else {
                    showContentState();
                }
            });
        }

        private GiftItem parseGiftItem(JSONObject giftObj) throws JSONException {
            String id = giftObj.optString("id");

            Log.d(TAG, "Parsing gift object keys: " + giftObj.toString());

            // Получаем все возможные поля для изображения
            String thumbUrl = null;

            // Пробуем все возможные поля, которые могут содержать URL изображения
            String[] possibleImageFields = {
                    "thumb_256", "thumb_128", "thumb_96", "thumb_48",
                    "photo_256", "photo_128", "photo_96", "photo_48",
                    "img_256", "img_128", "img_96", "img_48",
                    "image", "img", "photo", "thumbnail",
                    "thumb", "url"
            };

            for (String field : possibleImageFields) {
                if (giftObj.has(field)) {
                    String url = giftObj.optString(field);
                    if (url != null && !url.isEmpty() && !url.equals("null")) {
                        thumbUrl = url;
                        Log.d(TAG, "Found image URL in field '" + field + "': " + url);
                        break;
                    }
                }
            }

            // Если не нашли в основных полях, пробуем вложенные объекты
            if (thumbUrl == null && giftObj.has("gift")) {
                JSONObject gift = giftObj.getJSONObject("gift");
                for (String field : possibleImageFields) {
                    if (gift.has(field)) {
                        String url = gift.optString(field);
                        if (url != null && !url.isEmpty() && !url.equals("null")) {
                            thumbUrl = url;
                            Log.d(TAG, "Found image URL in gift." + field + ": " + url);
                            break;
                        }
                    }
                }
            }

            // Если все еще не нашли, пробуем массив images
            if (thumbUrl == null && giftObj.has("images")) {
                JSONArray images = giftObj.optJSONArray("images");
                if (images != null && images.length() > 0) {
                    // Берем последнее (наибольшее) изображение
                    JSONObject bestImage = images.getJSONObject(images.length() - 1);
                    thumbUrl = bestImage.optString("url");
                    Log.d(TAG, "Found image URL in images array: " + thumbUrl);
                }
            }

            String description = giftObj.optString("description");
            String fromId = giftObj.optString("from_id");
            long date = giftObj.optLong("date", 0);
            String message = giftObj.optString("message", "");

            // Если описание пустое, создаем стандартное
            if (description == null || description.isEmpty() || description.equals("null")) {
                description = "Подарок";
            }

            // Получаем имя отправителя из кэша или используем fallback
            String senderName = "Неизвестный";
            if (fromId != null && !fromId.isEmpty() && !fromId.equals("0")) {
                if (userNamesCache.containsKey(fromId)) {
                    senderName = userNamesCache.get(fromId);
                } else if (giftObj.has("user")) {
                    // Пробуем получить имя из объекта user, если он есть в ответе
                    JSONObject user = giftObj.getJSONObject("user");
                    String firstName = user.optString("first_name", "");
                    String lastName = user.optString("last_name", "");
                    if (!firstName.isEmpty() || !lastName.isEmpty()) {
                        senderName = firstName + " " + lastName;
                        userNamesCache.put(fromId, senderName);
                    } else {
                        senderName = "Пользователь " + fromId;
                    }
                } else {
                    senderName = "Пользователь " + fromId;
                }
            } else if (fromId != null && fromId.equals("0")) {
                senderName = "Анонимный отправитель";
            }

            Log.d(TAG, "Final gift data - ID: " + id + ", URL: " + thumbUrl + ", Desc: " + description + ", From: " + senderName);

            return new GiftItem(id, thumbUrl, description, senderName, date, message);
        }

        private void handleApiError(JSONObject error) {
            int errorCode = error.optInt("error_code");
            String errorMsg = error.optString("error_msg");

            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                setLoadingState(false);

                switch (errorCode) {
                    case 5:
                        showErrorState("Ошибка авторизации");
                        break;
                    case 6:
                        showErrorState("Слишком много запросов");
                        break;
                    case 15:
                        showErrorState("Нет доступа к подаркам пользователя");
                        break;
                    default:
                        showErrorState("Ошибка: " + errorMsg);
                }
            });
        }

        private void updateGiftsCountText() {
            Activity activity = getActivity();
            if (activity != null && textViewGiftsCount != null) {
                activity.runOnUiThread(() -> {
                    String countText = isTestAccount() ?
                            "Тестовые подарки: " + giftList.size() :
                            "Подарки: " + giftList.size();
                    textViewGiftsCount.setText(countText);
                });
            }
        }

        private void setLoadingState(boolean loading) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            recyclerViewGifts.setVisibility(loading ? View.GONE : View.VISIBLE);
            if (!loading) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }

        private void showErrorState(String message) {
            errorTextView.setText(message);
            errorTextView.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
            recyclerViewGifts.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        private void hideErrorState() {
            errorTextView.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
        }

        private void showContentState() {
            recyclerViewGifts.setVisibility(View.VISIBLE);
            errorTextView.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        static class GiftItem {
            String id;
            String thumbUrl;
            String description;
            String senderName;
            long date;
            String message;

            public GiftItem(String id, String thumbUrl, String description,
                            String senderName, long date, String message) {
                this.id = id;
                this.thumbUrl = thumbUrl;
                this.description = description;
                this.senderName = senderName;
                this.date = date;
                this.message = message;
            }
        }

        class GiftsAdapter extends RecyclerView.Adapter<GiftsAdapter.ViewHolder> {
            private List<GiftItem> gifts;

            public GiftsAdapter(List<GiftItem> gifts) {
                this.gifts = gifts;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_gift_tab, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                GiftItem item = gifts.get(position);

                // Добавляем логирование для диагностики
                Log.d(TAG, "Loading gift image: " + item.thumbUrl + " for gift: " + item.description);

                if (item.thumbUrl != null && !item.thumbUrl.isEmpty() && !item.thumbUrl.equals("null")) {
                    Glide.with(requireContext())
                            .load(item.thumbUrl)
                            .placeholder(R.drawable.gift_placeholder)
                            .error(R.drawable.gift_placeholder)
                            .addListener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                            Target<Drawable> target, boolean isFirstResource) {
                                    Log.e(TAG, "Image load failed: " + e + ", URL: " + item.thumbUrl);
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Drawable resource, Object model,
                                                               Target<Drawable> target, DataSource dataSource,
                                                               boolean isFirstResource) {
                                    Log.d(TAG, "Image loaded successfully: " + item.thumbUrl);
                                    return false;
                                }
                            })
                            .into(holder.giftImage);
                } else {
                    Log.w(TAG, "Empty or null image URL for gift: " + item.description);
                    holder.giftImage.setImageResource(R.drawable.gift_placeholder);
                }

                holder.descriptionText.setText(item.description != null ? item.description : "Подарок");

                // Отображаем имя отправителя
                if (item.senderName != null && !item.senderName.isEmpty()) {
                    holder.senderText.setText(item.senderName);
                    holder.senderText.setVisibility(View.VISIBLE);
                } else {
                    holder.senderText.setVisibility(View.GONE);
                }

                if (item.date > 0) {
                    holder.dateText.setText(formatDate(item.date));
                } else {
                    holder.dateText.setText("");
                }

                if (item.message != null && !item.message.isEmpty() && !item.message.equals("null")) {
                    holder.messageText.setText(item.message);
                    holder.messageText.setVisibility(View.VISIBLE);
                } else {
                    holder.messageText.setVisibility(View.GONE);
                }

                holder.itemView.setOnClickListener(v -> {
                    showGiftDetails(item);
                });
            }

            @Override
            public int getItemCount() {
                return gifts.size();
            }

            private String formatDate(long timestamp) {
                Date date = new Date(timestamp * 1000);
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                return sdf.format(date);
            }

            private void showGiftDetails(GiftItem gift) {
                // Создаем кастомный диалог с Material компонентами
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded);

                // Создаем кастомное view для диалога
                View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_gift_details, null);

                // Находим элементы в диалоге
                ImageView dialogGiftImage = dialogView.findViewById(R.id.dialogGiftImage);
                TextView dialogDescription = dialogView.findViewById(R.id.dialogDescription);
                TextView dialogSender = dialogView.findViewById(R.id.dialogSender);
                TextView dialogDate = dialogView.findViewById(R.id.dialogDate);
                TextView dialogMessage = dialogView.findViewById(R.id.dialogMessage);

                // Загружаем изображение подарка
                if (gift.thumbUrl != null && !gift.thumbUrl.isEmpty() && !gift.thumbUrl.equals("null")) {
                    Glide.with(requireContext())
                            .load(gift.thumbUrl)
                            .placeholder(R.drawable.gift_placeholder)
                            .error(R.drawable.gift_placeholder)
                            .into(dialogGiftImage);
                } else {
                    dialogGiftImage.setImageResource(R.drawable.gift_placeholder);
                }

                // Устанавливаем текст
                dialogDescription.setText(gift.description != null ? gift.description : "Подарок");
                dialogSender.setText(gift.senderName != null ? gift.senderName : "Неизвестный отправитель");
                dialogDate.setText(formatDate(gift.date));

                // Настраиваем сообщение (если есть)
                if (gift.message != null && !gift.message.isEmpty() && !gift.message.equals("null")) {
                    dialogMessage.setText(gift.message);
                    dialogMessage.setVisibility(View.VISIBLE);
                } else {
                    dialogMessage.setVisibility(View.GONE);
                }

                // Создаем диалог
                builder.setView(dialogView)
                        .setPositiveButton("Закрыть", (dialog, which) -> dialog.dismiss())
                        .setNeutralButton("Поделиться", (dialog, which) -> shareGift(gift));

                AlertDialog dialog = builder.create();
                dialog.show();

                // Настраиваем кнопки Material стиль
                Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);

                if (positiveButton != null) {
                    positiveButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
                }
                if (neutralButton != null) {
                    neutralButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
                }
            }

            private void shareGift(GiftItem gift) {
                String shareText = "Посмотрите на мой подарок: " +
                        (gift.description != null ? gift.description : "Подарок") +
                        (gift.senderName != null ? " от " + gift.senderName : "");

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                startActivity(Intent.createChooser(shareIntent, "Поделиться подарком"));
            }

            class ViewHolder extends RecyclerView.ViewHolder {
                ImageView giftImage;
                TextView descriptionText, senderText, dateText, messageText;

                public ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    giftImage = itemView.findViewById(R.id.giftImage);
                    descriptionText = itemView.findViewById(R.id.descriptionText);
                    senderText = itemView.findViewById(R.id.senderText);
                    dateText = itemView.findViewById(R.id.dateText);
                    messageText = itemView.findViewById(R.id.messageText);
                }
            }
        }
    }

    public static class MyGroupsFragment extends Fragment {
        private RecyclerView recyclerViewGroups;
        private GroupsAdapter groupsAdapter;
        private List<GroupItem> groupList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;
        private TextView textViewGroupsCount;
        private ProgressBar progressBar;
        private Button retryButton;

        private long userId;
        private String userName;
        private OkHttpClient httpClient;
        private boolean isLoading = false;

        public static MyGroupsFragment newInstance(long userId, String userName) {
            MyGroupsFragment fragment = new MyGroupsFragment();
            Bundle args = new Bundle();
            args.putLong("user_id", userId);
            args.putString("user_name", userName);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Bundle args = getArguments();
            if (args != null) {
                userId = args.getLong("user_id");
                userName = args.getString("user_name");
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_groups_full, container, false);

            recyclerViewGroups = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            textViewGroupsCount = view.findViewById(R.id.count);
            progressBar = view.findViewById(R.id.progressBar);
            retryButton = view.findViewById(R.id.retryButton);

            setupUI();
            setupSwipeRefresh();
            setupRetryButton();
            setupToolbar();

            if (isTestAccount()) {
                loadTestGroups();
            } else {
                fetchUserGroups();
            }

            return view;
        }

        private void setupUI() {
            LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
            recyclerViewGroups.setLayoutManager(layoutManager);
            groupsAdapter = new GroupsAdapter(groupList);
            recyclerViewGroups.setAdapter(groupsAdapter);
        }

        private void setupToolbar() {
            if (getActivity() instanceof AppCompatActivity) {
                ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setDisplayHomeAsUpEnabled(true);
                    actionBar.setTitle("Мои группы");

                    Toolbar toolbar = getActivity().findViewById(R.id.toolbar);
                    if (toolbar != null) {
                        toolbar.setNavigationOnClickListener(v -> {
                            getActivity().onBackPressed();
                        });
                    }
                }
            }
        }

        private void setupSwipeRefresh() {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (isTestAccount()) {
                    loadTestGroups();
                } else {
                    fetchUserGroups();
                }
            });
        }

        private void setupRetryButton() {
            retryButton.setOnClickListener(v -> {
                if (isTestAccount()) {
                    loadTestGroups();
                } else {
                    fetchUserGroups();
                }
            });
        }

        private boolean isTestAccount() {
            return userId == 123456789L || userId == 0 || userName == null;
        }

        private void loadTestGroups() {
            setLoadingState(true);
            List<GroupItem> testGroups = new ArrayList<>();

            testGroups.add(new GroupItem(
                    -123456790L,
                    "Технологии и программирование",
                    "https://via.placeholder.com/100x100/4ECDC4/FFFFFF?text=T",
                    "Сообщество разработчиков и IT-специалистов",
                    1500,
                    true
            ));
            testGroups.add(new GroupItem(
                    -123456791L,
                    "Музыка и искусство",
                    "https://via.placeholder.com/100x100/45B7D1/FFFFFF?text=M",
                    "Все о музыке, живописи и современном искусстве",
                    890,
                    false
            ));

            requireActivity().runOnUiThread(() -> {
                groupList.clear();
                groupList.addAll(testGroups);
                groupsAdapter.notifyDataSetChanged();
                setLoadingState(false);
                updateGroupsCountText();
                swipeRefreshLayout.setRefreshing(false);
            });
        }

        private void fetchUserGroups() {
            if (isLoading) return;

            String accessToken = TokenManager.getInstance(requireContext()).getToken();
            if (accessToken == null) {
                showErrorState("Требуется авторизация");
                return;
            }

            setLoadingState(true);
            hideErrorState();

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/groups.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("user_id", String.valueOf(userId))
                    .addQueryParameter("extended", "1")
                    .addQueryParameter("fields", "name,photo_100,description,members_count,is_closed")
                    .addQueryParameter("count", "100")
                    .addQueryParameter("v", "5.131")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "VKAndroidApp/1.0")
                    .build();

            isLoading = true;

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        isLoading = false;
                        setLoadingState(false);
                        showErrorState("Ошибка сети: " + e.getMessage());
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");
                                List<GroupItem> newGroupList = new ArrayList<>();

                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject groupObj = items.getJSONObject(i);
                                    GroupItem groupItem = parseGroupItem(groupObj);
                                    if (groupItem != null) {
                                        newGroupList.add(groupItem);
                                    }
                                }

                                requireActivity().runOnUiThread(() -> {
                                    isLoading = false;
                                    setLoadingState(false);
                                    swipeRefreshLayout.setRefreshing(false);

                                    groupList.clear();
                                    groupList.addAll(newGroupList);
                                    groupsAdapter.notifyDataSetChanged();
                                    updateGroupsCountText();

                                    if (groupList.isEmpty()) {
                                        showErrorState("Вы пока не состоите в группах");
                                    } else {
                                        showContentState();
                                    }
                                });
                            } else if (jsonObject.has("error")) {
                                handleApiError(jsonObject.getJSONObject("error"));
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                isLoading = false;
                                setLoadingState(false);
                                showErrorState("Ошибка обработки данных");
                            });
                        }
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            setLoadingState(false);
                            showErrorState("Ошибка сервера");
                        });
                    }
                }
            });
        }

        private GroupItem parseGroupItem(JSONObject groupObj) throws JSONException {
            long id = groupObj.getLong("id");
            String name = groupObj.getString("name");
            String photoUrl = groupObj.optString("photo_100");
            String description = groupObj.optString("description", "");
            int membersCount = groupObj.optInt("members_count", 0);
            boolean isClosed = groupObj.optInt("is_closed", 0) != 0;

            return new GroupItem(id, name, photoUrl, description, membersCount, isClosed);
        }

        private void handleApiError(JSONObject error) {
            int errorCode = error.optInt("error_code");
            String errorMsg = error.optString("error_msg");

            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                setLoadingState(false);

                switch (errorCode) {
                    case 5:
                        showErrorState("Ошибка авторизации");
                        break;
                    case 6:
                        showErrorState("Слишком много запросов");
                        break;
                    case 15:
                        showErrorState("Нет доступа к списку групп");
                        break;
                    case 18:
                        showErrorState("Страница удалена");
                        break;
                    default:
                        showErrorState("Ошибка: " + errorMsg);
                }
            });
        }

        private void updateGroupsCountText() {
            Activity activity = getActivity();
            if (activity != null && textViewGroupsCount != null) {
                activity.runOnUiThread(() -> {
                    String countText = isTestAccount() ?
                            "Колл-во: " + groupList.size() :
                            "Колл-во: " + groupList.size();
                    textViewGroupsCount.setText(countText);
                });
            }
        }

        private void setLoadingState(boolean loading) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            recyclerViewGroups.setVisibility(loading ? View.GONE : View.VISIBLE);
            if (!loading) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }

        private void showErrorState(String message) {
            retryButton.setVisibility(View.VISIBLE);
            recyclerViewGroups.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        private void hideErrorState() {
            retryButton.setVisibility(View.GONE);
        }

        private void showContentState() {
            recyclerViewGroups.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
        }

        static class GroupItem {
            long id;
            String name;
            String photoUrl;
            String description;
            int membersCount;
            boolean isClosed;

            public GroupItem(long id, String name, String photoUrl, String description,
                             int membersCount, boolean isClosed) {
                this.id = id;
                this.name = name;
                this.photoUrl = photoUrl;
                this.description = description;
                this.membersCount = membersCount;
                this.isClosed = isClosed;
            }
        }

        class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.ViewHolder> {
            private List<GroupItem> groups;

            public GroupsAdapter(List<GroupItem> groups) {
                this.groups = groups;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_group_new, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                GroupItem item = groups.get(position);

                holder.nameText.setText(item.name);

                if (item.membersCount > 0) {
                    holder.membersText.setText(formatMembersCount(item.membersCount));
                    holder.membersText.setVisibility(View.VISIBLE);
                } else {
                    holder.membersText.setVisibility(View.GONE);
                }

                if (item.isClosed) {
                    holder.statusText.setText("Закрытая группа");
                    holder.statusText.setVisibility(View.VISIBLE);
                } else {
                    holder.statusText.setText("Открытая группа");
                    holder.statusText.setVisibility(View.VISIBLE);
                }

                if (item.photoUrl != null && !item.photoUrl.isEmpty() && !item.photoUrl.equals("null")) {
                    Glide.with(requireContext())
                            .load(item.photoUrl)
                            .placeholder(R.drawable.img)
                            .error(R.drawable.img)
                            .circleCrop()
                            .into(holder.groupImage);
                }

                holder.itemView.setOnClickListener(v -> {
                    openGroupFragment(item);
                });
            }

            @Override
            public int getItemCount() {
                return groups.size();
            }

            private String formatMembersCount(int count) {
                if (count >= 1000000) {
                    return String.format(Locale.getDefault(), "%.1fM участников", count / 1000000.0);
                } else if (count >= 1000) {
                    return String.format(Locale.getDefault(), "%.1fK участников", count / 1000.0);
                } else {
                    return count + " участников";
                }
            }

            private void openGroupFragment(GroupItem group) {
                GroupDetailFragment groupDetailFragment = GroupDetailFragment.newInstance(
                        group.id,
                        group.name,
                        group.photoUrl,
                        group.description,
                        group.membersCount,
                        group.isClosed
                );

                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .setCustomAnimations(
                                R.anim.slide_in_right,
                                R.anim.slide_out_left,
                                R.anim.slide_in_left,
                                R.anim.slide_out_right
                        )
                        .replace(R.id.container, groupDetailFragment)
                        .addToBackStack("group_detail")
                        .commit();
            }

            class ViewHolder extends RecyclerView.ViewHolder {
                ImageView groupImage;
                TextView nameText, membersText, statusText;

                public ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    groupImage = itemView.findViewById(R.id.group_image);
                    nameText = itemView.findViewById(R.id.name_text);
                    membersText = itemView.findViewById(R.id.members_text);
                    statusText = itemView.findViewById(R.id.status_text);
                }
            }
        }
    }

    public static class GroupDetailFragment extends Fragment {
        private static final String ARG_GROUP_ID = "group_id";
        private static final String ARG_GROUP_NAME = "group_name";
        private static final String ARG_GROUP_PHOTO = "group_photo";
        private static final String ARG_GROUP_DESCRIPTION = "group_description";
        private static final String ARG_GROUP_MEMBERS_COUNT = "group_members_count";
        private static final String ARG_GROUP_IS_CLOSED = "group_is_closed";

        private long groupId;
        private String groupName;
        private String groupPhotoUrl;
        private String groupDescription;
        private int groupMembersCount;
        private boolean groupIsClosed;

        private ImageView groupAvatar;
        private TextView groupNameText;
        private TextView groupDescriptionText;
        private TextView groupMembersCountText;
        private TextView groupTypeText;
        private MaterialButton openInVkButton;
        private RecyclerView photosRecyclerView;
        private Toolbar toolbar;

        private List<String> groupPhotos = new ArrayList<>();
        private PhotosAdapter photosAdapter;

        public static GroupDetailFragment newInstance(long groupId, String groupName, String groupPhotoUrl,
                                                      String groupDescription, int groupMembersCount, boolean groupIsClosed) {
            GroupDetailFragment fragment = new GroupDetailFragment();
            Bundle args = new Bundle();
            args.putLong(ARG_GROUP_ID, groupId);
            args.putString(ARG_GROUP_NAME, groupName);
            args.putString(ARG_GROUP_PHOTO, groupPhotoUrl);
            args.putString(ARG_GROUP_DESCRIPTION, groupDescription);
            args.putInt(ARG_GROUP_MEMBERS_COUNT, groupMembersCount);
            args.putBoolean(ARG_GROUP_IS_CLOSED, groupIsClosed);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (getArguments() != null) {
                groupId = getArguments().getLong(ARG_GROUP_ID);
                groupName = getArguments().getString(ARG_GROUP_NAME);
                groupPhotoUrl = getArguments().getString(ARG_GROUP_PHOTO);
                groupDescription = getArguments().getString(ARG_GROUP_DESCRIPTION);
                groupMembersCount = getArguments().getInt(ARG_GROUP_MEMBERS_COUNT);
                groupIsClosed = getArguments().getBoolean(ARG_GROUP_IS_CLOSED);
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_group_detail, container, false);

            initViews(view);
            setupToolbar();
            setupPhotosRecyclerView();
            populateData();
            loadGroupPhotos();

            return view;
        }

        private void initViews(View view) {
            groupAvatar = view.findViewById(R.id.user_avatars);
            groupNameText = view.findViewById(R.id.user_name);
            groupDescriptionText = view.findViewById(R.id.artistsNamesText);
            groupMembersCountText = view.findViewById(R.id.user_status);
            groupTypeText = view.findViewById(R.id.group_type);
            openInVkButton = view.findViewById(R.id.open_in_vk_button);
            photosRecyclerView = view.findViewById(R.id.photos_recycler_view);
            toolbar = view.findViewById(R.id.toolbar);

            openInVkButton.setOnClickListener(v -> {
                openGroupInVk();
            });
        }

        private void setupToolbar() {
            if (getActivity() instanceof AppCompatActivity) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                activity.setSupportActionBar(toolbar);

                ActionBar actionBar = activity.getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setDisplayHomeAsUpEnabled(true);
                    actionBar.setDisplayShowHomeEnabled(true);
                    actionBar.setTitle(groupName);
                    actionBar.setDisplayShowTitleEnabled(true);
                }

                toolbar.setNavigationIcon(R.drawable.arrow_left_black);
                toolbar.setNavigationOnClickListener(v -> {
                    handleBackPress();
                });
            }
        }

        private void handleBackPress() {
            if (getActivity() != null) {
                FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                if (fragmentManager.getBackStackEntryCount() > 0) {
                    fragmentManager.popBackStack();
                } else {
                    getActivity().onBackPressed();
                }
            }
        }

        private void setupPhotosRecyclerView() {
            photosAdapter = new PhotosAdapter();
            GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
            photosRecyclerView.setLayoutManager(layoutManager);
            photosRecyclerView.setAdapter(photosAdapter);

            int spacingInPixels = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    2,
                    getResources().getDisplayMetrics()
            );
            photosRecyclerView.addItemDecoration(new GridSpacingItemDecoration(3, spacingInPixels, true));
            photosRecyclerView.setItemAnimator(null);
        }

        private void populateData() {
            groupNameText.setText(groupName);
            loadGroupAvatar();

            if (groupDescription != null && !groupDescription.isEmpty()) {
                groupDescriptionText.setText(groupDescription);
                groupDescriptionText.setVisibility(View.VISIBLE);
            } else {
                groupDescriptionText.setVisibility(View.GONE);
            }

            groupMembersCountText.setText(formatMembersCount(groupMembersCount));

            if (groupIsClosed) {
                groupTypeText.setText("Закрытая группа");
            } else {
                groupTypeText.setText("Открытая группа");
            }
        }

        private void loadGroupAvatar() {
            if (groupPhotoUrl != null && !groupPhotoUrl.isEmpty() && !groupPhotoUrl.equals("null")) {
                RequestOptions requestOptions = new RequestOptions()
                        .placeholder(R.drawable.img)
                        .error(R.drawable.img)
                        .circleCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .timeout(10000);

                Glide.with(requireContext())
                        .load(groupPhotoUrl)
                        .apply(requestOptions)
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                        Target<Drawable> target, boolean isFirstResource) {
                                Log.d("Glide", "Failed to load group avatar: " + (e != null ? e.getMessage() : "Unknown error"));
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Drawable resource, Object model,
                                                           Target<Drawable> target, DataSource dataSource,
                                                           boolean isFirstResource) {
                                return false;
                            }
                        })
                        .into(groupAvatar);
            } else {
                groupAvatar.setImageResource(R.drawable.img);
            }
        }

        private void loadGroupPhotos() {
            groupPhotos.clear();
            groupPhotos.add("https://sun9-64.userapi.com/s/v1/ig2/ofRVLbxNNsd6gVUMgWqMzlPZ4PlpQ3linuPVJFk-xUgVEYol6srkoOn2MFQR9PwZYKtZkYNGMo_qaMfnf5RlzC-D.jpg?quality=95&as=32x32,48x48,72x72,108x108,160x160,240x240,360x360,480x480,512x512&from=bu&cs=512x0");
            groupPhotos.add("https://picsum.photos/301/301");
            groupPhotos.add("https://picsum.photos/302/302");
            groupPhotos.add("https://picsum.photos/303/303");
            groupPhotos.add("https://picsum.photos/304/304");
            groupPhotos.add("https://picsum.photos/305/305");

            photosAdapter.setPhotos(groupPhotos);

            if (groupPhotos.isEmpty()) {
                photosRecyclerView.setVisibility(View.GONE);
            } else {
                photosRecyclerView.setVisibility(View.GONE);
            }
        }

        private String formatMembersCount(int count) {
            if (count >= 1000000) {
                return String.format(Locale.getDefault(), "%.1fM участников", count / 1000000.0);
            } else if (count >= 1000) {
                return String.format(Locale.getDefault(), "%.1fK участников", count / 1000.0);
            } else {
                return count + " участников";
            }
        }

        private void openGroupInVk() {
            try {
                String url = "https://vk.com/club" + Math.abs(groupId);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Не удалось открыть группу", Toast.LENGTH_SHORT).show();
            }
        }

        private void openPhotoFullscreen(String photoUrl) {
            Toast.makeText(requireContext(), "Открыть фото: " + photoUrl, Toast.LENGTH_SHORT).show();
        }

        private class PhotosAdapter extends RecyclerView.Adapter<PhotosAdapter.PhotoViewHolder> {
            private List<String> photos = new ArrayList<>();

            public void setPhotos(List<String> photos) {
                this.photos = photos;
                notifyDataSetChanged();
            }

            @NonNull
            @Override
            public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_photo, parent, false);
                return new PhotoViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
                holder.bind(photos.get(position));
            }

            @Override
            public int getItemCount() {
                return photos.size();
            }

            class PhotoViewHolder extends RecyclerView.ViewHolder {
                private ImageView photoImage;
                private TextView photoDescription;

                public PhotoViewHolder(@NonNull View itemView) {
                    super(itemView);
                    photoImage = itemView.findViewById(R.id.photo_image);
                    photoDescription = itemView.findViewById(R.id.photo_description);
                }

                public void bind(String photoUrl) {
                    photoDescription.setVisibility(View.GONE);

                    RequestOptions requestOptions = new RequestOptions()
                            .placeholder(R.drawable.circle_photo)
                            .error(R.drawable.circle_photo)
                            .centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .timeout(10000);

                    Glide.with(itemView.getContext())
                            .load(photoUrl)
                            .apply(requestOptions)
                            .listener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                            Target<Drawable> target, boolean isFirstResource) {
                                    Log.d("Glide", "Failed to load image: " + (e != null ? e.getMessage() : "Unknown error"));
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Drawable resource, Object model,
                                                               Target<Drawable> target, DataSource dataSource,
                                                               boolean isFirstResource) {
                                    return false;
                                }
                            })
                            .into(photoImage);

                    itemView.setOnClickListener(v -> {
                        openPhotoFullscreen(photoUrl);
                    });
                }
            }
        }

        public static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
            private int spanCount;
            private int spacing;
            private boolean includeEdge;

            public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
                this.spanCount = spanCount;
                this.spacing = spacing;
                this.includeEdge = includeEdge;
            }

            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                int column = position % spanCount;

                if (includeEdge) {
                    outRect.left = spacing - column * spacing / spanCount;
                    outRect.right = (column + 1) * spacing / spanCount;

                    if (position < spanCount) {
                        outRect.top = spacing;
                    }
                    outRect.bottom = spacing;
                } else {
                    outRect.left = column * spacing / spanCount;
                    outRect.right = spacing - (column + 1) * spacing / spanCount;
                    if (position >= spanCount) {
                        outRect.top = spacing;
                    }
                }
            }
        }
    }

    public static class PhotosTabFragment extends Fragment {
        private RecyclerView recyclerViewPhotos;
        private PhotosAdapter photosAdapter;
        private List<PhotoItem> photoList = new ArrayList<>();
        private SwipeRefreshLayout swipeRefreshLayout;
        private TextView textViewPhotosCount;
        private long userId;
        private String userName;
        private OkHttpClient httpClient;
        private int totalPhotosCount = 0;

        private final String[] TEST_PHOTOS = {
                "https://image.winudf.com/v2/image/c3dlZXQubWVzc2FnZXIudmtfc2NyZWVuc2hvdHNfMl80ODVjMjU1Yg/screen-2.webp?h=200&fakeurl=1&type=.webp",
                "https://sun9-29.userapi.com/s/v1/ig2/e1cpZokZGhsJ6k6XQ9zjSrKAXvW449mlHumlz_5sLfWFuDGZ5BwoJ5WULgvrZsXyUqcetp_1y62pfAZPO-JrzbDa.jpg",
                "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=500",
                "https://images.unsplash.com/photo-1529778873920-4da4926a72c2?w=500",
                "https://images.unsplash.com/photo-1518756131217-31eb79b20e8f?w=500"
        };

        public static PhotosTabFragment newInstance(long userId, String userName) {
            PhotosTabFragment fragment = new PhotosTabFragment();
            Bundle args = new Bundle();
            args.putLong("user_id", userId);
            args.putString("user_name", userName);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            Bundle args = getArguments();
            if (args != null) {
                userId = args.getLong("user_id");
                userName = args.getString("user_name");
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_photos, container, false);

            recyclerViewPhotos = view.findViewById(R.id.recyclerView);
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
            textViewPhotosCount = view.findViewById(R.id.count);

            setupUI();
            setupSwipeRefresh();

            if (isTestAccount()) {
                loadTestPhotos();
            } else {
                fetchUserPhotos();
                getPhotosCount();
            }

            return view;
        }

        private void setupUI() {
            GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
            recyclerViewPhotos.setLayoutManager(layoutManager);
            photosAdapter = new PhotosAdapter(photoList, this::openPhotoViewer);
            recyclerViewPhotos.setAdapter(photosAdapter);
        }

        private void setupSwipeRefresh() {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                totalPhotosCount = 0;
                updatePhotosCountText();
                if (isTestAccount()) {
                    loadTestPhotos();
                } else {
                    fetchUserPhotos();
                    getPhotosCount();
                }
            });
        }

        private boolean isTestAccount() {
            return userId == 123456789L || userId == 0 || userName == null;
        }

        private void loadTestPhotos() {
            swipeRefreshLayout.setRefreshing(true);
            List<PhotoItem> testPhotos = new ArrayList<>();
            Random random = new Random();

            for (int i = 0; i < TEST_PHOTOS.length; i++) {
                testPhotos.add(new PhotoItem(
                        i + 1,
                        userId,
                        TEST_PHOTOS[i],
                        System.currentTimeMillis() / 1000 - random.nextInt(1000000),
                        random.nextInt(1000),
                        random.nextInt(100),
                        "Мое фото " + (i + 1)
                ));
            }

            requireActivity().runOnUiThread(() -> {
                photoList.clear();
                photoList.addAll(testPhotos);
                photosAdapter.notifyDataSetChanged();
                swipeRefreshLayout.setRefreshing(false);
                totalPhotosCount = testPhotos.size();
                updatePhotosCountText();
            });
        }

        private void fetchUserPhotos() {
            String accessToken = TokenManager.getInstance(getContext()).getToken();
            if (accessToken == null) {
                swipeRefreshLayout.setRefreshing(false);
                return;
            }

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/photos.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("owner_id", String.valueOf(userId))
                    .addQueryParameter("album_id", "profile")
                    .addQueryParameter("count", "100")
                    .addQueryParameter("rev", "1")
                    .addQueryParameter("extended", "1")
                    .addQueryParameter("v", "5.131")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "VKAndroidApp/1.0")
                    .build();

            swipeRefreshLayout.setRefreshing(true);

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка загрузки фотографий", Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                JSONArray items = responseObj.getJSONArray("items");
                                List<PhotoItem> newPhotoList = new ArrayList<>();

                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject photoObj = items.getJSONObject(i);
                                    PhotoItem photoItem = parsePhotoItem(photoObj);
                                    if (photoItem != null) {
                                        newPhotoList.add(photoItem);
                                    }
                                }

                                requireActivity().runOnUiThread(() -> {
                                    photoList.clear();
                                    photoList.addAll(newPhotoList);
                                    photosAdapter.notifyDataSetChanged();
                                    swipeRefreshLayout.setRefreshing(false);
                                });
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                                swipeRefreshLayout.setRefreshing(false);
                            });
                        }
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Ошибка сервера", Toast.LENGTH_SHORT).show();
                            swipeRefreshLayout.setRefreshing(false);
                        });
                    }
                }
            });
        }

        private PhotoItem parsePhotoItem(JSONObject photoObj) throws JSONException {
            long id = photoObj.getLong("id");
            long ownerId = photoObj.getLong("owner_id");
            String photoUrl = getBestPhotoUrl(photoObj);
            if (photoUrl == null) return null;

            long date = photoObj.getLong("date");
            int likesCount = photoObj.has("likes") ? photoObj.getJSONObject("likes").getInt("count") : 0;
            int commentsCount = photoObj.has("comments") ? photoObj.getJSONObject("comments").getInt("count") : 0;
            String text = photoObj.optString("text", "");

            return new PhotoItem(id, ownerId, photoUrl, date, likesCount, commentsCount, text);
        }

        private String getBestPhotoUrl(JSONObject photoObj) throws JSONException {
            String[] sizes = {"x", "y", "z", "m", "s"};
            for (String size : sizes) {
                if (photoObj.has("photo_" + size)) {
                    return photoObj.getString("photo_" + size);
                }
            }
            if (photoObj.has("sizes")) {
                JSONArray sizesArray = photoObj.getJSONArray("sizes");
                for (int i = sizesArray.length() - 1; i >= 0; i--) {
                    JSONObject sizeObj = sizesArray.getJSONObject(i);
                    String type = sizeObj.getString("type");
                    if (type.equals("x") || type.equals("y") || type.equals("z")) {
                        return sizeObj.getString("url");
                    }
                }
                if (sizesArray.length() > 0) {
                    return sizesArray.getJSONObject(sizesArray.length() - 1).getString("url");
                }
            }
            return null;
        }

        private void getPhotosCount() {
            Context context = getContext();
            if (context == null) return;

            String accessToken = TokenManager.getInstance(context).getToken();
            if (accessToken == null || accessToken.isEmpty()) return;

            HttpUrl url = HttpUrl.parse("https://api.vk.com/method/photos.get")
                    .newBuilder()
                    .addQueryParameter("access_token", accessToken)
                    .addQueryParameter("owner_id", String.valueOf(userId))
                    .addQueryParameter("album_id", "profile")
                    .addQueryParameter("count", "0")
                    .addQueryParameter("v", "5.131")
                    .build();

            httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    updatePhotosCountText();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        if (response.isSuccessful()) {
                            String responseBody = response.body().string();
                            JSONObject jsonObject = new JSONObject(responseBody);
                            if (jsonObject.has("response")) {
                                JSONObject responseObj = jsonObject.getJSONObject("response");
                                int count = responseObj.optInt("count", 0);
                                totalPhotosCount = count;
                                updatePhotosCountText();
                            }
                        }
                    } catch (Exception e) {
                        updatePhotosCountText();
                    }
                }
            });
        }

        private void updatePhotosCountText() {
            Activity activity = getActivity();
            if (activity != null && textViewPhotosCount != null) {
                activity.runOnUiThread(() -> {
                    String countText = isTestAccount() ?
                            "Колл-во: " + totalPhotosCount :
                            "Колл-во: " + totalPhotosCount;
                    textViewPhotosCount.setText(countText);
                });
            }
        }

        private void openPhotoViewer(PhotoItem photoItem) {
            List<String> photoUrls = new ArrayList<>();
            int currentPosition = -1;

            for (int i = 0; i < photoList.size(); i++) {
                PhotoItem item = photoList.get(i);
                photoUrls.add(item.getPhotoUrl());
                if (item.getId() == photoItem.getId()) {
                    currentPosition = i;
                }
            }

            if (currentPosition == -1) currentPosition = 0;

            PhotoViewerFragment photoViewerFragment = PhotoViewerFragment.newInstance(
                    new ArrayList<>(photoUrls), currentPosition);

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.container, photoViewerFragment)
                    .addToBackStack("photo_viewer")
                    .commit();
        }

        public static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
            private int spanCount;
            private int spacing;
            private boolean includeEdge;

            public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
                this.spanCount = spanCount;
                this.spacing = spacing;
                this.includeEdge = includeEdge;
            }

            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                int column = position % spanCount;

                if (includeEdge) {
                    outRect.left = spacing - column * spacing / spanCount;
                    outRect.right = (column + 1) * spacing / spanCount;

                    if (position < spanCount) {
                        outRect.top = spacing;
                    }
                    outRect.bottom = spacing;
                } else {
                    outRect.left = column * spacing / spanCount;
                    outRect.right = spacing - (column + 1) * spacing / spanCount;
                    if (position >= spanCount) {
                        outRect.top = spacing;
                    }
                }
            }
        }

        static class PhotosAdapter extends RecyclerView.Adapter<PhotosAdapter.ViewHolder> {
            private List<PhotoItem> photos;
            private OnItemClickListener listener;

            public interface OnItemClickListener {
                void onItemClick(PhotoItem photo);
            }

            public PhotosAdapter(List<PhotoItem> photos, OnItemClickListener listener) {
                this.photos = photos;
                this.listener = listener;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_photo, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                PhotoItem photo = photos.get(position);
                Glide.with(holder.itemView.getContext())
                        .load(photo.getPhotoUrl())
                        .placeholder(R.drawable.img)
                        .error(R.drawable.img)
                        .centerCrop()
                        .into(holder.photoImageView);

                if (photo.getLikesCount() > 0) {
                    holder.likesTextView.setText(String.valueOf(photo.getLikesCount()));
                    holder.likesTextView.setVisibility(View.VISIBLE);
                } else {
                    holder.likesTextView.setVisibility(View.GONE);
                }

                holder.itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onItemClick(photo);
                });
            }

            @Override
            public int getItemCount() {
                return photos.size();
            }

            static class ViewHolder extends RecyclerView.ViewHolder {
                ImageView photoImageView;
                TextView likesTextView;

                ViewHolder(@NonNull View itemView) {
                    super(itemView);
                    photoImageView = itemView.findViewById(R.id.photo_image);
                    likesTextView = itemView.findViewById(R.id.photo_description);
                }
            }
        }
    }

    static class PhotoItem {
        private long id;
        private long ownerId;
        private String photoUrl;
        private long date;
        private int likesCount;
        private int commentsCount;
        private String text;

        public PhotoItem(long id, long ownerId, String photoUrl, long date,
                         int likesCount, int commentsCount, String text) {
            this.id = id;
            this.ownerId = ownerId;
            this.photoUrl = photoUrl;
            this.date = date;
            this.likesCount = likesCount;
            this.commentsCount = commentsCount;
            this.text = text;
        }

        public long getId() { return id; }
        public long getOwnerId() { return ownerId; }
        public String getPhotoUrl() { return photoUrl; }
        public long getDate() { return date; }
        public int getLikesCount() { return likesCount; }
        public int getCommentsCount() { return commentsCount; }
        public String getText() { return text; }
    }
}