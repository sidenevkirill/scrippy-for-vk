package ru.lisdevs.messenger.official.audios;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.utils.CircleTransform;

public class ShareToFriendsBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_AUDIO = "audio";
    private static final String ARG_FRIENDS = "friends";
    private static final String TAG = "ShareToFriendsBottomSheet";

    private Audio audio;
    private List<AudioListFragment.Friend> friends;
    private ShareAudioListener shareAudioListener;

    public interface ShareAudioListener {
        void onShareToFriend(AudioListFragment.Friend friend, Audio audio);
    }

    public static ShareToFriendsBottomSheet newInstance(Audio audio, List<AudioListFragment.Friend> friends) {
        ShareToFriendsBottomSheet fragment = new ShareToFriendsBottomSheet();
        Bundle args = new Bundle();
        args.putParcelable(ARG_AUDIO, audio);

        // Сохраняем как Serializable
        if (friends != null) {
            args.putSerializable(ARG_FRIENDS, new ArrayList<>(friends));
        } else {
            args.putSerializable(ARG_FRIENDS, new ArrayList<AudioListFragment.Friend>());
        }

        fragment.setArguments(args);
        return fragment;
    }

    public void setShareAudioListener(ShareAudioListener listener) {
        this.shareAudioListener = listener;
    }

    @SuppressLint("LongLogTag")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        friends = new ArrayList<>();

        if (getArguments() != null) {
            audio = getArguments().getParcelable(ARG_AUDIO);

            // Извлекаем как Serializable
            Serializable serializableList = getArguments().getSerializable(ARG_FRIENDS);
            if (serializableList instanceof ArrayList) {
                ArrayList<?> list = (ArrayList<?>) serializableList;
                for (Object item : list) {
                    if (item instanceof AudioListFragment.Friend) {
                        friends.add((AudioListFragment.Friend) item);
                    }
                }
                Log.d(TAG, "Loaded " + friends.size() + " friends");
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_share_friends, container, false);
        setupRecyclerView(view);
        return view;
    }

    private void setupRecyclerView(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewFriends);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        FriendsAdapter adapter = new FriendsAdapter(friends, friend -> {
            if (shareAudioListener != null) {
                shareAudioListener.onShareToFriend(friend, audio);
            }
            dismiss();
        });

        recyclerView.setAdapter(adapter);
    }

    private static class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {

        private List<AudioListFragment.Friend> friends;
        private OnFriendClickListener listener;

        public interface OnFriendClickListener {
            void onFriendClick(AudioListFragment.Friend friend);
        }

        public FriendsAdapter(List<AudioListFragment.Friend> friends, OnFriendClickListener listener) {
            this.friends = friends != null ? new ArrayList<>(friends) : new ArrayList<>();
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friend, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AudioListFragment.Friend friend = friends.get(position);
            holder.bind(friend);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFriendClick(friend);
                }
            });
        }

        @Override
        public int getItemCount() {
            return friends.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView avatarImageView;
            TextView nameTextView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                avatarImageView = itemView.findViewById(R.id.avatar_image);
                nameTextView = itemView.findViewById(R.id.user);
            }

            public void bind(AudioListFragment.Friend friend) {
                nameTextView.setText(friend.getName());

                // Загрузка аватарки с обработкой ошибок
                if (friend.getPhotoUrl() != null && !friend.getPhotoUrl().isEmpty()) {
                    Log.d("FriendsAdapter", "Loading avatar for: " + friend.getName() + ", URL: " + friend.getPhotoUrl());

                    Picasso.get()
                            .load(friend.getPhotoUrl())
                            .placeholder(R.drawable.circle_friend)
                            .error(R.drawable.circle_friend)
                            .resize(100, 100)
                            .centerCrop()
                            .transform(new CircleTransform()) // Добавляем закругление
                            .into(avatarImageView, new Callback() {
                                @Override
                                public void onSuccess() {
                                    Log.d("Picasso", "Avatar loaded successfully for: " + friend.getName());
                                }

                                @Override
                                public void onError(Exception e) {
                                    Log.e("Picasso", "Error loading avatar for: " + friend.getName(), e);
                                    avatarImageView.setImageResource(R.drawable.circle_friend);
                                }
                            });
                } else {
                    Log.d("FriendsAdapter", "No avatar URL for: " + friend.getName());
                    avatarImageView.setImageResource(R.drawable.circle_friend);
                }
            }
        }
    }
}