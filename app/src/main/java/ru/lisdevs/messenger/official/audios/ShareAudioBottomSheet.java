package ru.lisdevs.messenger.official.audios;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.ArrayList;
import java.util.List;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Message;

import android.util.Log;

public class ShareAudioBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_AUDIO = "audio";
    private static final String ARG_CHAT_LIST = "chat_list";
    private static final String TAG = "ShareAudioBottomSheet";

    private Audio audio;
    private List<Message> chatList;
    private ChatSelectionAdapter adapter;
    private ShareAudioListener shareAudioListener;

    public interface ShareAudioListener {
        void onChatSelected(Message chat, Audio audio);
    }

    public static ShareAudioBottomSheet newInstance(Audio audio, ArrayList<Message> chatList) {
        ShareAudioBottomSheet fragment = new ShareAudioBottomSheet();
        Bundle args = new Bundle();
        args.putParcelable(ARG_AUDIO, audio);

        // Safe casting with type checking
        if (chatList != null) {
            args.putParcelableArrayList(ARG_CHAT_LIST, new ArrayList<>(chatList));
        } else {
            args.putParcelableArrayList(ARG_CHAT_LIST, new ArrayList<>());
        }

        fragment.setArguments(args);
        return fragment;
    }

    public void setShareAudioListener(ShareAudioListener listener) {
        this.shareAudioListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        chatList = new ArrayList<>();

        if (getArguments() != null) {
            audio = getArguments().getParcelable(ARG_AUDIO);

            // Safe extraction with type safety
            ArrayList<Message> parcelableList = getArguments().getParcelableArrayList(ARG_CHAT_LIST);
            if (parcelableList != null) {
                chatList.addAll(parcelableList);
                Log.d(TAG, "Loaded " + chatList.size() + " chats");

                // Логирование для отладки
                for (int i = 0; i < chatList.size(); i++) {
                    Message chat = chatList.get(i);
                    Log.d(TAG, "Chat " + i + ": " +
                            "senderName='" + chat.getSenderName() + "' " +
                            "senderId='" + chat.getSenderId() + "' " +
                            "peerId='" + chat.getPeerId() + "' " +
                            "body='" + chat.getBody() + "'");
                }
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_share_audio, container, false);
        initViews(view);
        return view;
    }

    private void initViews(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewChats);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        adapter = new ChatSelectionAdapter(chatList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnChatClickListener(chat -> {
            if (shareAudioListener != null) {
                shareAudioListener.onChatSelected(chat, audio);
            }
            dismiss();
        });

        btnCancel.setOnClickListener(v -> dismiss());
    }

    // Nested adapter class
    public static class ChatSelectionAdapter extends RecyclerView.Adapter<ChatSelectionAdapter.ChatViewHolder> {

        private List<Message> chatList;
        private OnChatClickListener onChatClickListener;

        public interface OnChatClickListener {
            void onChatClick(Message chat);
        }

        public ChatSelectionAdapter(List<Message> chatList) {
            this.chatList = chatList != null ? new ArrayList<>(chatList) : new ArrayList<>();
        }

        public void setOnChatClickListener(OnChatClickListener listener) {
            this.onChatClickListener = listener;
        }

        @NonNull
        @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_selection, parent, false);
            return new ChatViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            Message chat = chatList.get(position);
            holder.bind(chat);

            holder.itemView.setOnClickListener(v -> {
                if (onChatClickListener != null) {
                    onChatClickListener.onChatClick(chat);
                }
            });
        }

        @Override
        public int getItemCount() {
            return chatList.size();
        }

        public void updateChatList(List<Message> newChatList) {
            this.chatList = newChatList != null ? new ArrayList<>(newChatList) : new ArrayList<>();
            notifyDataSetChanged();
        }

        static class ChatViewHolder extends RecyclerView.ViewHolder {
            private TextView chatName;
            private TextView lastMessage;

            public ChatViewHolder(@NonNull View itemView) {
                super(itemView);
                chatName = itemView.findViewById(R.id.chatName);
                lastMessage = itemView.findViewById(R.id.lastMessage);
            }

            void bind(Message chat) {
                // Улучшенная логика определения названия диалога
                String displayName = getDisplayName(chat);
                String messageText = getMessageText(chat);

                chatName.setText(displayName);
                lastMessage.setText(messageText);

                // Логирование для отладки
                Log.d("ChatSelectionAdapter", "Displaying chat: " + displayName);
            }

            private String getDisplayName(Message chat) {
                // Приоритет 1: senderName
                if (chat.getSenderName() != null && !chat.getSenderName().trim().isEmpty()) {
                    return chat.getSenderName();
                }

                // Приоритет 2: peerId (если это ID диалога)
                if (chat.getPeerId() != null && !chat.getPeerId().trim().isEmpty()) {
                    return "Диалог " + chat.getPeerId();
                }

                // Приоритет 3: senderId
                if (chat.getSenderId() != null && !chat.getSenderId().trim().isEmpty()) {
                    return "Пользователь " + chat.getSenderId();
                }

                // Приоритет 4: системное сообщение
                if (chat.isSystemMessage()) {
                    return "Системное сообщение";
                }

                // Запасной вариант
                return "Безымянный диалог";
            }

            private String getMessageText(Message chat) {
                if (chat.getBody() != null && !chat.getBody().trim().isEmpty()) {
                    return chat.getBody();
                }

                if (chat.isSystemMessage()) {
                    return "Системное уведомление";
                }

                return "Нет сообщений";
            }
        }
    }
}