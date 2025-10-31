package ru.lisdevs.messenger.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Chat;

public class ChatAdapter extends ArrayAdapter<Chat> {

    private Context context;
    private ArrayList<Chat> chats;

    public ChatAdapter(Context context, ArrayList<Chat> chats) {
        super(context, 0, chats);
        this.context = context;
        this.chats = chats;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(
                    R.layout.item_chat, parent, false);

            holder = new ViewHolder();
            holder.avatar = convertView.findViewById(R.id.avatar);
            holder.userName = convertView.findViewById(R.id.userName);
            holder.lastMessage = convertView.findViewById(R.id.lastMessage);
            holder.time = convertView.findViewById(R.id.time);
            holder.unreadBadge = convertView.findViewById(R.id.unreadBadge);
            holder.unreadCount = convertView.findViewById(R.id.unreadCount);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Chat chat = getItem(position);

        if (chat != null) {
            holder.avatar.setImageResource(chat.getAvatarRes());
            holder.userName.setText(chat.getUserName());
            holder.lastMessage.setText(chat.getLastMessage());
            holder.time.setText(chat.getTime());

            // Показываем/скрываем бейдж непрочитанных
            if (chat.getUnreadCount() > 0) {
                holder.unreadBadge.setVisibility(View.VISIBLE);
                holder.unreadCount.setText(String.valueOf(chat.getUnreadCount()));
            } else {
                holder.unreadBadge.setVisibility(View.GONE);
            }
        }

        return convertView;
    }

    // Метод для обновления списка
    public void updateList(ArrayList<Chat> newChats) {
        clear();
        addAll(newChats);
        notifyDataSetChanged();
    }

    private static class ViewHolder {
        ImageView avatar;
        TextView userName;
        TextView lastMessage;
        TextView time;
        View unreadBadge;
        TextView unreadCount;
    }
}