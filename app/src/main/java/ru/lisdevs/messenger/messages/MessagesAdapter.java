package ru.lisdevs.messenger.messages;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Message;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MessageViewHolder> {

    private List<Message> messages;
    private Random random = new Random();
    private SpecialUserChecker specialUserChecker;

    interface SpecialUserChecker {
        boolean isSpecialUser(String userId);
    }

    public MessagesAdapter(List<Message> messages, SpecialUserChecker specialUserChecker) {
        this.messages = messages;
        this.specialUserChecker = specialUserChecker;
    }

    public MessagesAdapter(List<Message> messages) {
        this.messages = messages;
        this.specialUserChecker = userId -> false;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public void setSpecialUserChecker(SpecialUserChecker specialUserChecker) {
        this.specialUserChecker = specialUserChecker;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.bind(message, specialUserChecker);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView textSender;
        TextView textBody;
        TextView textDate;
        TextView avatarTextView;
        ImageView verifiedIcon;
        private Random random = new Random();

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textSender = itemView.findViewById(R.id.textSender);
            textBody = itemView.findViewById(R.id.textBody);
            textDate = itemView.findViewById(R.id.textDate);
            avatarTextView = itemView.findViewById(R.id.avatarTextView);
            verifiedIcon = itemView.findViewById(R.id.verified_icon);
        }

        void bind(Message message, SpecialUserChecker specialUserChecker) {
            textSender.setText(message.getSenderName());

            if (message.getBody().isEmpty()) {
                textBody.setText("(вложение)");
                textBody.setTextColor(Color.GRAY);
            } else {
                textBody.setText(message.getBody());
                textBody.setTextColor(Color.BLACK);
            }

            textDate.setText(formatDate(message.getDate()));

            String firstLetter = getFirstLetter(message.getSenderName());
            avatarTextView.setText(firstLetter);

            int color = getRandomColor();
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            avatarTextView.setBackground(drawable);

            if (verifiedIcon != null) {
                if (specialUserChecker.isSpecialUser(message.getSenderId())) {
                    verifiedIcon.setVisibility(View.VISIBLE);
                    verifiedIcon.setImageResource(R.drawable.check_verif);
                } else {
                    verifiedIcon.setVisibility(View.GONE);
                }
            }
        }

        private String getFirstLetter(String name) {
            if (!TextUtils.isEmpty(name)) {
                String[] nameParts = name.split(" ");
                if (nameParts.length > 0) {
                    return nameParts[0].substring(0, 1).toUpperCase();
                }
                return name.substring(0, 1).toUpperCase();
            }
            return "?";
        }

        private int getRandomColor() {
            int[] colors = {
                    Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
                    Color.parseColor("#45B7D1"), Color.parseColor("#F9A826"),
                    Color.parseColor("#6A5ACD"), Color.parseColor("#FFA07A"),
                    Color.parseColor("#20B2AA"), Color.parseColor("#9370DB"),
                    Color.parseColor("#3CB371"), Color.parseColor("#FF4500")
            };
            return colors[random.nextInt(colors.length)];
        }

        private String formatDate(long timestamp) {
            Date date = new Date(timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault());
            return sdf.format(date);
        }
    }
}