package ru.lisdevs.messenger.friends;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.music.Audio;
import ru.lisdevs.messenger.music.AudioAdapter;

public class FriendsAudio extends Fragment {

    private static List<Audio> userAudioList;
    private RecyclerView recyclerView;

    public static void setAudioList(List<Audio> audios) {
        userAudioList= audios;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user_friends1, container, false);

        // Получение аргументов
        Bundle args= getArguments();
        if (args != null) {
            long friendId= args.getLong("friend_id");
            String lastName= args.getString("last_name");
            String firstName= args.getString("first_name");
            String screenName= args.getString("screen_name");

            // Например, установить в TextView или другие элементы UI
        }

        // Отобразить список аудио
        if (userAudioList != null && !userAudioList.isEmpty()) {
            RecyclerView recyclerView= view.findViewById(R.id.recyclerView);
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            AudioAdapter adapter= new AudioAdapter(userAudioList);
            recyclerView.setAdapter(adapter);
        } else {
            // Обработка случая без аудио
        }

        return view;
    }
}
