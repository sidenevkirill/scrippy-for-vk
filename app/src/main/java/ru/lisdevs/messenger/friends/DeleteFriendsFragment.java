package ru.lisdevs.messenger.friends;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.utils.TokenManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DeleteFriendsFragment extends Fragment {

    private static final String ACCESS_TOKEN = "ВАШ_ТОКЕН_ДОСТУПА"; // вставьте ваш токен
    private static final String API_VERSION = "5.131";

    private OkHttpClient client = new OkHttpClient();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_delete_friends, container, false);

        Button btnDeleteAll = view.findViewById(R.id.btnDeleteAllFriends);
        btnDeleteAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteAllFriends();
            }
        });

        return view;
    }

    private void deleteAllFriends() {
        getFriends();
    }

    private void getFriends() {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        String url = "https://api.vk.com/method/friends.get?access_token=" + accessToken +
                "&v=" + API_VERSION;

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                // Обработка ошибки
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String respStr = response.body().string();
                    try {
                        JSONObject json = new JSONObject(respStr);
                        JSONArray friendsArray = json.getJSONObject("response").getJSONArray("items");

                        List<Integer> friendIds = new ArrayList<>();
                        for (int i = 0; i < friendsArray.length(); i++) {
                            friendIds.add(friendsArray.getInt(i));
                        }

                        // Удаляем всех друзей
                        deleteFriends(friendIds);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void deleteFriends(List<Integer> friendIds) {
        for (int userId : friendIds) {
            deleteFriend(userId);
        }
    }

    private void deleteFriend(int userId) {
        String accessToken = TokenManager.getInstance(getContext()).getToken();
        String url = "https://api.vk.com/method/friends.delete?user_id=" + userId +
                "&access_token=" + accessToken + "&v=" + API_VERSION;

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                // Обработка ошибки удаления
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String respStr = response.body().string();
                    // Можно обработать ответ удаления
                    // Например, проверить успешность удаления
                }
            }
        });
    }
}
