package ru.lisdevs.messenger.friends;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.model.Friend;
import ru.lisdevs.messenger.utils.TokenManager;

public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.FriendViewHolder> {

    private List<Friend> friends;
    private OnFriendClickListener listener;
    private Context context;
    private Map<Integer, Integer> audioCountCache = new HashMap<>();
    private OkHttpClient client;

    public interface OnFriendClickListener {
        void onFriendClick(Friend friend);
    }

    public FriendAdapter(Context context, List<Friend> friends, OnFriendClickListener listener) {
        this.context = context.getApplicationContext();
        this.friends = friends;
        this.listener = listener;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        loadAudioCounts();
    }

    public void updateList(List<Friend> newFriends) {
        this.friends = newFriends;
        audioCountCache.clear();
        notifyDataSetChanged();
        loadAudioCounts();
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        Friend friend = friends.get(position);

        // Установка имени
        holder.nameTextView.setText(String.format("%s %s", friend.firstName, friend.lastName));

        // Загрузка аватарки или отображение инициала
        loadAvatarOrInitial(holder, friend);

        // Установка количества аудиозаписей
        Integer count = audioCountCache.get(friend.id);
        holder.audioCountTextView.setText(count != null ?
                context.getString(R.string.tracks_count, count) : context.getString(R.string.loading));

        // Обработчик клика
        holder.itemView.setOnClickListener(v -> {
            if (listener != null && holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                listener.onFriendClick(friends.get(holder.getAdapterPosition()));
            }
        });
    }

    private void loadAvatarOrInitial(FriendViewHolder holder, Friend friend) {
        if (friend.photoUrl != null && !friend.photoUrl.isEmpty()) {
            // Показываем ImageView, скрываем TextView с инициалами
            holder.avatarImageView.setVisibility(View.VISIBLE);
            holder.initialTextView.setVisibility(View.GONE);

            // Загружаем аватарку через Glide
            Glide.with(context)
                    .load(friend.photoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.circle_friend)
                    .error(R.drawable.circle_friend)
                    .into(holder.avatarImageView);
        } else {
            // Если нет аватарки, показываем инициал
            holder.avatarImageView.setVisibility(View.GONE);
            holder.initialTextView.setVisibility(View.VISIBLE);

            // Устанавливаем первую букву имени (если имя не пустое)
            String initial = "";
            if (friend.firstName != null && !friend.firstName.isEmpty()) {
                initial = friend.firstName.substring(0, 1).toUpperCase();
            }
            holder.initialTextView.setText(initial);

            // Устанавливаем случайный цвет фона для инициала
            int color = getRandomColorForInitial(friend.id);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            holder.initialTextView.setBackground(drawable);
        }
    }

    private int getRandomColorForInitial(long userId) {
        // Генерируем "случайный" цвет на основе ID пользователя для постоянства
        int[] colors = context.getResources().getIntArray(R.array.initial_colors);
        int index = (int) (userId % colors.length);
        return colors[index];
    }

    @Override
    public int getItemCount() {
        return friends != null ? friends.size() : 0;
    }

    private void loadAudioCounts() {
        for (int i = 0; i < friends.size(); i++) {
            Friend friend = friends.get(i);
            if (!audioCountCache.containsKey(friend.id)) {
                fetchAudioCount(friend.id, i);
            }
        }
    }

    private void fetchAudioCount(long userId, final int position) {
        String token = TokenManager.getInstance(context).getToken();
        if (token == null || token.isEmpty()) {
            Log.e("FriendAdapter", "Token is null or empty");
            return;
        }

        HttpUrl url = HttpUrl.parse("https://api.vk.com/method/audio.getCount")
                .newBuilder()
                .addQueryParameter("access_token", token)
                .addQueryParameter("owner_id", String.valueOf(userId))
                .addQueryParameter("v", "5.131")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("FriendAdapter", "Error fetching audio count", e);
                updateCount(userId, position, -1);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        updateCount(userId, position, -1);
                        return;
                    }

                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    int count = json.optInt("response", -1);
                    updateCount(userId, position, count);
                } catch (Exception e) {
                    Log.e("FriendAdapter", "Error parsing response", e);
                    updateCount(userId, position, -1);
                }
            }
        });
    }

    private void updateCount(long userId, int position, int count) {
        audioCountCache.put((int) userId, count);

        if (position >= 0 && position < getItemCount()) {
            if (context instanceof Activity) {
                ((Activity) context).runOnUiThread(() -> notifyItemChanged(position));
            }
        }
    }

    static class FriendViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImageView;
        TextView initialTextView;
        TextView nameTextView;
        TextView audioCountTextView;

        FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.avatar_image);
            initialTextView = itemView.findViewById(R.id.image_name);
            nameTextView = itemView.findViewById(R.id.user);
            audioCountTextView = itemView.findViewById(R.id.audio_count);
        }
    }
}