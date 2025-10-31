package ru.lisdevs.messenger.messages;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Message;

public class ChatSelectionAdapter extends RecyclerView.Adapter<ChatSelectionAdapter.ChatViewHolder> {

    private List<Message> chatList;
    private OnChatClickListener onChatClickListener;

    public interface OnChatClickListener {
        void onChatClick(Message chat);
    }

    public ChatSelectionAdapter(List<Message> chatList) {
        // Add null safety for the list
        this.chatList = chatList != null ? chatList : new ArrayList<>();
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
        // Fixed: Return the actual list size
        return chatList.size();
    }

    // Optional: Method to update the list safely
    public void updateChatList(List<Message> newChatList) {
        this.chatList = newChatList != null ? newChatList : new ArrayList<>();
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
            chatName.setText(chat.getSenderName());
            lastMessage.setText(chat.getBody());
        }
    }
}