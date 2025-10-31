package ru.lisdevs.messenger.dialog;

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

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.messages.AttachmentAdapter;
import ru.lisdevs.messenger.model.Attachment;
import ru.lisdevs.messenger.model.Message;
import androidx.recyclerview.widget.LinearLayoutManager;

public class DialogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_INCOMING = 0;
    private static final int TYPE_OUTGOING = 1;
    private static final int TYPE_SYSTEM = 2;

    private List<Message> messages;
    private String currentUserId;
    private boolean isSpecialUser;
    private OnPhotoClickListener onPhotoClickListener;

    public interface OnPhotoClickListener {
        void onPhotoClick(Message message, Attachment.Photo photo);
    }

    public DialogAdapter(List<Message> messages) {
        this.messages = messages;
        this.currentUserId = "current_user_id";
    }

    public DialogAdapter(List<Message> messages, String currentUserId, boolean isSpecialUser) {
        this.messages = messages;
        this.currentUserId = currentUserId;
        this.isSpecialUser = isSpecialUser;
    }

    public void setOnPhotoClickListener(OnPhotoClickListener listener) {
        this.onPhotoClickListener = listener;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void addMessages(List<Message> newMessages) {
        int startPosition = messages.size();
        messages.addAll(newMessages);
        notifyItemRangeInserted(startPosition, newMessages.size());
    }

    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);

        if (message.isSystemMessage()) {
            return TYPE_SYSTEM;
        } else if (message.isOutgoing()) {
            return TYPE_OUTGOING;
        } else {
            return TYPE_INCOMING;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        switch (viewType) {
            case TYPE_OUTGOING:
                View outgoingView = inflater.inflate(R.layout.item_message_outgoing, parent, false);
                return new OutgoingMessageViewHolder(outgoingView);

            case TYPE_SYSTEM:
                View systemView = inflater.inflate(R.layout.item_message_system, parent, false);
                return new SystemMessageViewHolder(systemView);

            case TYPE_INCOMING:
            default:
                View incomingView = inflater.inflate(R.layout.item_message_incoming, parent, false);
                return new IncomingMessageViewHolder(incomingView, isSpecialUser);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);

        switch (holder.getItemViewType()) {
            case TYPE_OUTGOING:
                ((OutgoingMessageViewHolder) holder).bind(message);
                break;
            case TYPE_INCOMING:
                ((IncomingMessageViewHolder) holder).bind(message);
                break;
            case TYPE_SYSTEM:
                ((SystemMessageViewHolder) holder).bind(message);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ViewHolder для исходящих сообщений (справа)
    class OutgoingMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView timeText;
        ImageView readStatusIcon;
        ImageView importantIcon;
        RecyclerView attachmentsRecyclerView;
        AttachmentAdapter attachmentAdapter;

        public OutgoingMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            readStatusIcon = itemView.findViewById(R.id.readStatusIcon);
            importantIcon = itemView.findViewById(R.id.importantIcon);

            // Инициализация RecyclerView для вложений
            attachmentsRecyclerView = itemView.findViewById(R.id.attachmentsRecyclerView);
            if (attachmentsRecyclerView != null) {
                LinearLayoutManager layoutManager = new LinearLayoutManager(itemView.getContext());
                layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
                attachmentsRecyclerView.setLayoutManager(layoutManager);
                attachmentAdapter = new AttachmentAdapter();
                attachmentsRecyclerView.setAdapter(attachmentAdapter);
            }
        }

        void bind(Message message) {
            // Устанавливаем текст сообщения
            messageText.setText(message.getDisplayText());
            timeText.setText(formatTime(message.getDate()));

            // Настройка статуса прочтения
            if (readStatusIcon != null) {
                switch (message.getReadStatus()) {
                    case Message.READ_STATUS_SENT:
                        readStatusIcon.setImageResource(R.drawable.ic_check_sent);
                        readStatusIcon.setColorFilter(Color.GRAY);
                        readStatusIcon.setVisibility(View.VISIBLE);
                        break;
                    case Message.READ_STATUS_READ:
                        readStatusIcon.setImageResource(R.drawable.ic_check_double_read);
                        readStatusIcon.setColorFilter(Color.parseColor("#4285FF"));
                        readStatusIcon.setVisibility(View.VISIBLE);
                        break;
                    default:
                        readStatusIcon.setVisibility(View.GONE);
                        break;
                }
            }

            // Важное сообщение
            if (importantIcon != null) {
                importantIcon.setVisibility(message.isImportant() ? View.VISIBLE : View.GONE);
            }

            // Обработка вложений
            if (attachmentsRecyclerView != null && attachmentAdapter != null) {
                if (message.hasAttachments()) {
                    attachmentsRecyclerView.setVisibility(View.VISIBLE);
                    attachmentAdapter.setAttachments(message.getAttachments());

                    // Устанавливаем обработчик кликов для фото
                    attachmentAdapter.setOnPhotoClickListener(new AttachmentAdapter.OnPhotoClickListener() {
                        @Override
                        public void onPhotoClick(Attachment.Photo photo, int position) {
                            if (onPhotoClickListener != null) {
                                onPhotoClickListener.onPhotoClick(message, photo);
                            }
                        }

                        @Override
                        public void onMultiplePhotosClick(List<String> photoUrls, int currentPosition) {
                            // Не используется в этом подходе
                        }
                    });

                    // Скрываем текст если есть только вложения без текста
                    if (message.getBody() == null || message.getBody().isEmpty()) {
                        messageText.setVisibility(View.GONE);
                    } else {
                        messageText.setVisibility(View.VISIBLE);
                    }
                } else {
                    attachmentsRecyclerView.setVisibility(View.GONE);
                    messageText.setVisibility(View.VISIBLE);
                }
            }

            // Устанавливаем цвет текста
            if (message.hasAttachments() && (message.getBody() == null || message.getBody().isEmpty())) {
                messageText.setTextColor(Color.GRAY);
            } else {
                messageText.setTextColor(Color.WHITE);
            }
        }
    }

    // ViewHolder для входящих сообщений (слева)
    class IncomingMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView timeText;
        TextView senderName;
        TextView avatarText;
        ImageView verifiedIcon;
        ImageView importantIcon;
        RecyclerView attachmentsRecyclerView;
        AttachmentAdapter attachmentAdapter;

        public IncomingMessageViewHolder(@NonNull View itemView, boolean isSpecialUser) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            senderName = itemView.findViewById(R.id.senderName);
            avatarText = itemView.findViewById(R.id.avatarText);
            verifiedIcon = itemView.findViewById(R.id.verifiedIcon);
            importantIcon = itemView.findViewById(R.id.importantIcon);

            // Инициализация RecyclerView для вложений
            attachmentsRecyclerView = itemView.findViewById(R.id.attachmentsRecyclerView);
            if (attachmentsRecyclerView != null) {
                LinearLayoutManager layoutManager = new LinearLayoutManager(itemView.getContext());
                layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
                attachmentsRecyclerView.setLayoutManager(layoutManager);
                attachmentAdapter = new AttachmentAdapter();
                attachmentsRecyclerView.setAdapter(attachmentAdapter);
            }

            if (verifiedIcon != null) {
                verifiedIcon.setVisibility(isSpecialUser ? View.VISIBLE : View.GONE);
            }
        }

        void bind(Message message) {
            messageText.setText(message.getDisplayText());
            timeText.setText(formatTime(message.getDate()));

            // Имя отправителя (только для групповых чатов)
            if (senderName != null) {
                senderName.setText(message.getSenderName());
                senderName.setVisibility(View.VISIBLE);
            }

            // Аватар
            if (avatarText != null) {
                String firstLetter = getFirstLetter(message.getSenderName());
                avatarText.setText(firstLetter);

                int color = getRandomColor(message.getSenderId());
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(color);
                avatarText.setBackground(drawable);
            }

            // Важное сообщение
            if (importantIcon != null) {
                importantIcon.setVisibility(message.isImportant() ? View.VISIBLE : View.GONE);
            }

            // Обработка вложений
            if (attachmentsRecyclerView != null && attachmentAdapter != null) {
                if (message.hasAttachments()) {
                    attachmentsRecyclerView.setVisibility(View.VISIBLE);
                    attachmentAdapter.setAttachments(message.getAttachments());

                    // Устанавливаем обработчик кликов для фото
                    attachmentAdapter.setOnPhotoClickListener(new AttachmentAdapter.OnPhotoClickListener() {
                        @Override
                        public void onPhotoClick(Attachment.Photo photo, int position) {
                            if (onPhotoClickListener != null) {
                                onPhotoClickListener.onPhotoClick(message, photo);
                            }
                        }

                        @Override
                        public void onMultiplePhotosClick(List<String> photoUrls, int currentPosition) {
                            // Не используется в этом подходе
                        }
                    });

                    // Скрываем текст если есть только вложения без текста
                    if (message.getBody() == null || message.getBody().isEmpty()) {
                        messageText.setVisibility(View.GONE);
                    } else {
                        messageText.setVisibility(View.VISIBLE);
                    }
                } else {
                    attachmentsRecyclerView.setVisibility(View.GONE);
                    messageText.setVisibility(View.VISIBLE);
                }
            }

            // Устанавливаем цвет текста
            if (message.hasAttachments() && (message.getBody() == null || message.getBody().isEmpty())) {
                messageText.setTextColor(Color.GRAY);
            } else {
                messageText.setTextColor(Color.BLACK);
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

        private int getRandomColor(String userId) {
            int[] colors = {
                    Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
                    Color.parseColor("#45B7D1"), Color.parseColor("#F9A826"),
                    Color.parseColor("#6A5ACD"), Color.parseColor("#FFA07A"),
                    Color.parseColor("#20B2AA"), Color.parseColor("#9370DB"),
                    Color.parseColor("#3CB371"), Color.parseColor("#FF4500")
            };
            int index = Math.abs(userId.hashCode()) % colors.length;
            return colors[index];
        }
    }

    // ViewHolder для системных сообщений
    static class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        TextView systemText;

        public SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            systemText = itemView.findViewById(R.id.systemText);
        }

        void bind(Message message) {
            systemText.setText(message.getBody());
        }
    }

    private static String formatTime(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(date);
    }
}