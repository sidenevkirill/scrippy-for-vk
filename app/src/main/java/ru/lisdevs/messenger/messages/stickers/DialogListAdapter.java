package ru.lisdevs.messenger.messages.stickers;

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

public class DialogListAdapter extends RecyclerView.Adapter<DialogListAdapter.ViewHolder> {

    private List<Message> dialogs;
    private final OnDialogClickListener listener;

    public interface OnDialogClickListener {
        void onDialogClick(Message dialog);
    }

    public DialogListAdapter(List<Message> dialogs, OnDialogClickListener listener) {
        this.dialogs = dialogs != null ? dialogs : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dialog_share, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message dialog = dialogs.get(position);
        holder.bind(dialog, listener);
    }

    @Override
    public int getItemCount() {
        return dialogs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView dialogName;
        private final TextView lastMessage;
        private final TextView unreadBadge;
        private final View dialogContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            dialogName = itemView.findViewById(R.id.dialog_name);
            lastMessage = itemView.findViewById(R.id.last_message);
            unreadBadge = itemView.findViewById(R.id.unread_badge);
            dialogContainer = itemView.findViewById(R.id.dialog_container);
        }

        public void bind(Message dialog, OnDialogClickListener listener) {
            dialogName.setText(dialog.getSenderName());
            lastMessage.setText(dialog.getDisplayText()); // Используем метод из вашего класса

            // Показываем бейдж непрочитанных сообщений
            if (dialog.getUnreadCount() > 0) {
                unreadBadge.setVisibility(View.VISIBLE);
                unreadBadge.setText(String.valueOf(dialog.getUnreadCount()));
            } else {
                unreadBadge.setVisibility(View.GONE);
            }

            dialogContainer.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDialogClick(dialog);
                }
            });
        }
    }
}