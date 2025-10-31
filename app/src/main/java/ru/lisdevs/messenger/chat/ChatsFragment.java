package ru.lisdevs.messenger.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Chat;

public class ChatsFragment extends Fragment {

    private ListView chatsListView;
    private ArrayList<Chat> chatsList;
    private ChatAdapter adapter;

    public ChatsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_chats, container, false);

        initViews(view);
        setupChats();
        setupListView();

        return view;
    }

    private void initViews(View view) {
        chatsListView = view.findViewById(R.id.chatsListView);
    }

    private void setupChats() {
        chatsList = new ArrayList<>();

        // Добавляем тестовые чаты
        chatsList.add(new Chat("Анна Петрова", "Привет! Как дела?", "12:30", R.drawable.accoun_oval));
        chatsList.add(new Chat("Иван Сидоров", "Жду ответа на вопрос", "11:45", R.drawable.accoun_oval));
        chatsList.add(new Chat("Мария Иванова", "Спасибо за помощь!", "10:20", R.drawable.accoun_oval));
        chatsList.add(new Chat("Алексей Комаров", "Когда встретимся?", "09:15", R.drawable.accoun_oval));
        chatsList.add(new Chat("Поддержка ВК", "Ваш вопрос решен", "Вчера", R.drawable.accoun_oval));
        chatsList.add(new Chat("Дмитрий Волков", "Отправил документы", "Вчера", R.drawable.accoun_oval));
        chatsList.add(new Chat("Елена Смирнова", "Завтра в 18:00?", "Пн", R.drawable.accoun_oval));
    }

    private void setupListView() {
        adapter = new ChatAdapter(getActivity(), chatsList);
        chatsListView.setAdapter(adapter);

        // Обработчик нажатия на чат
        chatsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Chat selectedChat = chatsList.get(position);
                openChat(selectedChat);
            }
        });
    }

    private void openChat(Chat chat) {
        Intent intent = new Intent(getActivity(), ChatActivity.class);
        intent.putExtra("chatName", chat.getUserName());
        intent.putExtra("avatarRes", chat.getAvatarRes());
        startActivity(intent);

        // Можно добавить анимацию перехода
        getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    // Метод для обновления списка чатов
    public void updateChats(ArrayList<Chat> newChats) {
        chatsList.clear();
        chatsList.addAll(newChats);
        adapter.notifyDataSetChanged();
    }

    // Метод для добавления нового чата
    public void addChat(Chat newChat) {
        chatsList.add(0, newChat); // Добавляем в начало списка
        adapter.notifyDataSetChanged();
    }

    // Метод для поиска чатов
    public void filterChats(String query) {
        if (query.isEmpty()) {
            adapter.updateList(chatsList);
        } else {
            ArrayList<Chat> filteredList = new ArrayList<>();
            for (Chat chat : chatsList) {
                if (chat.getUserName().toLowerCase().contains(query.toLowerCase()) ||
                        chat.getLastMessage().toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(chat);
                }
            }
            adapter.updateList(filteredList);
        }
    }
}