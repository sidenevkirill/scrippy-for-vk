package ru.lisdevs.messenger.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Message implements Parcelable {
    // Константы статусов прочтения
    public static final int READ_STATUS_SENT = 1; // Отправлено (одна серая галочка)
    public static final int READ_STATUS_READ = 2; // Прочитано (две синие галочки)
    public static final int READ_STATUS_INCOMING = 3; // Входящее (без галочек)
    public static final int READ_STATUS_INCOMING_LEGACY = 0; // Входящее сообщение (для обратной совместимости)

    // Константы типов сообщений
    public static final int TYPE_TEXT = 1;
    public static final int TYPE_ATTACHMENT = 2;
    public static final int TYPE_FORWARDED = 3;
    public static final int TYPE_SYSTEM = 4;
    public static final int TYPE_TYPING = 5;

    // Поля сообщения
    private String messageId;
    private String senderId;
    private String senderName;
    private String body;
    private long date;
    private String avatarUrl;
    private int readStatus = READ_STATUS_INCOMING;
    private boolean outgoing;
    private boolean isImportant;
    private String peerId;
    private boolean systemMessage = false;
    private List<Attachment> attachments;
    private String previewText;
    private boolean hasForwardedMessages;
    private int forwardedMessagesCount;
    private int unreadCount;

    // Конструктор по умолчанию
    public Message() {
        this.readStatus = READ_STATUS_INCOMING;
        this.attachments = new ArrayList<>();
        this.unreadCount = 0;
    }

    // Базовый конструктор
    public Message(String senderId, String body) {
        this();
        this.senderId = senderId;
        this.body = body;
    }

    // Полный конструктор
    public Message(String senderId, String senderName, String body, long date, String avatarUrl) {
        this();
        this.senderId = senderId;
        this.senderName = senderName;
        this.body = body;
        this.date = date;
        this.avatarUrl = avatarUrl;
    }

    // Конструктор для Parcelable
    protected Message(Parcel in) {
        messageId = in.readString();
        senderId = in.readString();
        senderName = in.readString();
        body = in.readString();
        date = in.readLong();
        avatarUrl = in.readString();
        readStatus = in.readInt();
        outgoing = in.readByte() != 0;
        isImportant = in.readByte() != 0;
        peerId = in.readString();
        systemMessage = in.readByte() != 0;
        attachments = in.createTypedArrayList(Attachment.CREATOR);
        previewText = in.readString();
        hasForwardedMessages = in.readByte() != 0;
        forwardedMessagesCount = in.readInt();
        unreadCount = in.readInt();
    }

    // Parcelable CREATOR
    public static final Creator<Message> CREATOR = new Creator<Message>() {
        @Override
        public Message createFromParcel(Parcel in) {
            return new Message(in);
        }

        @Override
        public Message[] newArray(int size) {
            return new Message[size];
        }
    };

    // Реализация Parcelable
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(messageId);
        dest.writeString(senderId);
        dest.writeString(senderName);
        dest.writeString(body);
        dest.writeLong(date);
        dest.writeString(avatarUrl);
        dest.writeInt(readStatus);
        dest.writeByte((byte) (outgoing ? 1 : 0));
        dest.writeByte((byte) (isImportant ? 1 : 0));
        dest.writeString(peerId);
        dest.writeByte((byte) (systemMessage ? 1 : 0));
        dest.writeTypedList(attachments);
        dest.writeString(previewText);
        dest.writeByte((byte) (hasForwardedMessages ? 1 : 0));
        dest.writeInt(forwardedMessagesCount);
        dest.writeInt(unreadCount);
    }

    // Геттеры и сеттеры
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public int getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(int readStatus) {
        this.readStatus = readStatus;
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public void setOutgoing(boolean outgoing) {
        this.outgoing = outgoing;
    }

    public boolean isImportant() {
        return isImportant;
    }

    public void setImportant(boolean important) {
        isImportant = important;
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    public boolean isSystemMessage() {
        return systemMessage;
    }

    public void setSystemMessage(boolean systemMessage) {
        this.systemMessage = systemMessage;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public String getPreviewText() {
        if (body == null || body.isEmpty()) {
            return "(вложение)";
        }
        return body.length() > 100 ? body.substring(0, 100) + "..." : body;
    }

    public void setPreviewText(String previewText) {
        this.previewText = previewText;
    }

    public boolean hasForwardedMessages() {
        return hasForwardedMessages;
    }

    public void setHasForwardedMessages(boolean hasForwardedMessages) {
        this.hasForwardedMessages = hasForwardedMessages;
    }

    public int getForwardedMessagesCount() {
        return forwardedMessagesCount;
    }

    public void setForwardedMessagesCount(int forwardedMessagesCount) {
        this.forwardedMessagesCount = forwardedMessagesCount;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    // Методы для работы со статусами
    public boolean isRead() {
        return readStatus == READ_STATUS_READ;
    }

    public boolean isSent() {
        return readStatus == READ_STATUS_SENT;
    }

    public boolean isIncoming() {
        return readStatus == READ_STATUS_INCOMING || readStatus == READ_STATUS_INCOMING_LEGACY;
    }

    public void markAsRead() {
        this.readStatus = READ_STATUS_READ;
    }

    public void markAsSent() {
        this.readStatus = READ_STATUS_SENT;
    }

    public void markAsIncoming() {
        this.readStatus = READ_STATUS_INCOMING;
    }

    // Метод для получения текстового представления статуса
    public String getReadStatusText() {
        if (!outgoing) {
            return ""; // Для входящих сообщений не показываем статус
        }

        switch (readStatus) {
            case READ_STATUS_SENT:
                return "✓"; // Отправлено
            case READ_STATUS_READ:
                return "✓✓"; // Прочитано
            default:
                return ""; // В процессе отправки или входящее
        }
    }

    // Методы для работы с вложениями
    public void addAttachment(Attachment attachment) {
        if (this.attachments == null) {
            this.attachments = new ArrayList<>();
        }
        this.attachments.add(attachment);
    }

    public void addAttachments(List<Attachment> attachments) {
        if (this.attachments == null) {
            this.attachments = new ArrayList<>();
        }
        this.attachments.addAll(attachments);
    }

    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    public boolean hasPhotos() {
        if (attachments == null) return false;
        for (Attachment attachment : attachments) {
            if ("photo".equals(attachment.getType())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasDocuments() {
        if (attachments == null) return false;
        for (Attachment attachment : attachments) {
            if ("doc".equals(attachment.getType())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAudio() {
        if (attachments == null) return false;
        for (Attachment attachment : attachments) {
            if ("audio".equals(attachment.getType())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAudioMessages() {
        if (attachments == null) return false;
        for (Attachment attachment : attachments) {
            if ("audio_message".equals(attachment.getType())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasStickers() {
        if (attachments == null) return false;
        for (Attachment attachment : attachments) {
            if ("sticker".equals(attachment.getType())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasVideo() {
        if (attachments == null) return false;
        for (Attachment attachment : attachments) {
            if ("video".equals(attachment.getType())) {
                return true;
            }
        }
        return false;
    }

    public int getAttachmentCount() {
        return attachments != null ? attachments.size() : 0;
    }

    public int getPhotoCount() {
        if (attachments == null) return 0;
        int count = 0;
        for (Attachment attachment : attachments) {
            if ("photo".equals(attachment.getType())) {
                count++;
            }
        }
        return count;
    }

    public int getDocumentCount() {
        if (attachments == null) return 0;
        int count = 0;
        for (Attachment attachment : attachments) {
            if ("doc".equals(attachment.getType())) {
                count++;
            }
        }
        return count;
    }

    public int getAudioCount() {
        if (attachments == null) return 0;
        int count = 0;
        for (Attachment attachment : attachments) {
            if ("audio".equals(attachment.getType())) {
                count++;
            }
        }
        return count;
    }

    public int getAudioMessageCount() {
        if (attachments == null) return 0;
        int count = 0;
        for (Attachment attachment : attachments) {
            if ("audio_message".equals(attachment.getType())) {
                count++;
            }
        }
        return count;
    }

    public int getStickerCount() {
        if (attachments == null) return 0;
        int count = 0;
        for (Attachment attachment : attachments) {
            if ("sticker".equals(attachment.getType())) {
                count++;
            }
        }
        return count;
    }

    public int getVideoCount() {
        if (attachments == null) return 0;
        int count = 0;
        for (Attachment attachment : attachments) {
            if ("video".equals(attachment.getType())) {
                count++;
            }
        }
        return count;
    }

    // Получение первого attachment указанного типа
    public Attachment getFirstAttachmentOfType(String type) {
        if (attachments == null) return null;

        for (Attachment attachment : attachments) {
            if (type.equals(attachment.getType())) {
                return attachment;
            }
        }
        return null;
    }

    // Получение всех attachments указанного типа
    public List<Attachment> getAttachmentsOfType(String type) {
        if (attachments == null) return new ArrayList<>();

        List<Attachment> result = new ArrayList<>();
        for (Attachment attachment : attachments) {
            if (type.equals(attachment.getType())) {
                result.add(attachment);
            }
        }
        return result;
    }

    // Методы для отображения текста
    public String getDisplayText() {
        // Для системных сообщений возвращаем тело как есть
        if (systemMessage) {
            return body != null ? body : "";
        }

        // Для пересланных сообщений
        if (hasForwardedMessages) {
            return "📩 Пересланные сообщения (" + forwardedMessagesCount + ")";
        }

        // Если есть текст сообщения
        if (body != null && !body.trim().isEmpty()) {
            return body;
        }

        // Если есть preview текст
        if (previewText != null && !previewText.trim().isEmpty()) {
            return previewText;
        }

        // Если есть вложения, генерируем описание
        if (hasAttachments()) {
            return generateAttachmentPreview();
        }

        // Если это сообщение о наборе текста
        if (isTypingMessage()) {
            return "печатает...";
        }

        // Для пустых сообщений
        return "(сообщение)";
    }

    private String generateAttachmentPreview() {
        if (attachments == null || attachments.isEmpty()) {
            return "(вложение)";
        }

        StringBuilder preview = new StringBuilder();
        int photoCount = getPhotoCount();
        int docCount = getDocumentCount();
        int audioCount = getAudioCount();
        int audioMessageCount = getAudioMessageCount();
        int stickerCount = getStickerCount();
        int videoCount = getVideoCount();
        int otherCount = getAttachmentCount() - photoCount - docCount - audioCount - audioMessageCount - stickerCount - videoCount;

        List<String> parts = new ArrayList<>();
        if (photoCount > 0) parts.add("📷 " + photoCount);
        if (docCount > 0) parts.add("📎 " + docCount);
        if (audioCount > 0) parts.add("🎵 " + audioCount);
        if (audioMessageCount > 0) parts.add("🎤 " + audioMessageCount);
        if (stickerCount > 0) parts.add("😊 " + stickerCount);
        if (videoCount > 0) parts.add("🎬 " + videoCount);
        if (otherCount > 0) parts.add("📁 " + otherCount);

        if (parts.isEmpty()) {
            return "(вложение)";
        }

        return String.join(" • ", parts);
    }

    public String getShortDisplayText(int maxLength) {
        String displayText = getDisplayText();
        if (displayText.length() <= maxLength) {
            return displayText;
        }
        return displayText.substring(0, maxLength - 3) + "...";
    }

    public String getNotificationText() {
        if (systemMessage) {
            return body != null ? body : "Системное сообщение";
        }

        String sender = senderName != null ? senderName : "Неизвестный";
        String text = getDisplayText();

        return sender + ": " + text;
    }

    // Вспомогательные методы
    public boolean isTypingMessage() {
        return systemMessage && body != null && body.contains("печатает");
    }

    public boolean isGroupDialog() {
        return peerId != null && peerId.startsWith("chat");
    }

    public String getDialogId() {
        return peerId != null ? peerId : senderId;
    }

    public boolean hasUnread() {
        return unreadCount > 0;
    }

    public void incrementUnreadCount() {
        this.unreadCount++;
    }

    public void resetUnreadCount() {
        this.unreadCount = 0;
    }

    public boolean isAttachmentOnly() {
        return (body == null || body.isEmpty()) && hasAttachments();
    }

    public boolean isServiceMessage() {
        return systemMessage ||
                (body != null && (body.startsWith("//system:") || body.contains("присоединился")));
    }

    public String getUniqueKey() {
        if (messageId != null) {
            return messageId;
        }
        return senderId + "_" + date + "_" + (body != null ? body.hashCode() : "0");
    }

    public int getMessageType() {
        if (systemMessage) {
            if (body != null && body.contains("печатает")) {
                return TYPE_TYPING;
            }
            return TYPE_SYSTEM;
        }
        if (hasForwardedMessages) {
            return TYPE_FORWARDED;
        }
        if (hasAttachments() && (body == null || body.isEmpty())) {
            return TYPE_ATTACHMENT;
        }
        return TYPE_TEXT;
    }

    public String getMessageTypeEmoji() {
        if (systemMessage) {
            return "⚙️";
        }
        if (hasForwardedMessages) {
            return "📩";
        }
        if (hasAttachments()) {
            if (hasPhotos()) return "📷";
            if (hasVideo()) return "🎬";
            if (hasAudio()) return "🎵";
            if (hasAudioMessages()) return "🎤";
            if (hasDocuments()) return "📎";
            if (hasStickers()) return "😊";
            return "📁";
        }
        return "💬";
    }

    public String getReadStatusEmoji() {
        if (!outgoing) {
            return "";
        }

        switch (readStatus) {
            case READ_STATUS_SENT:
                return "✓";
            case READ_STATUS_READ:
                return "✓✓";
            default:
                return "🕒";
        }
    }

    public int getReadStatusColor(Context context) {
        if (!outgoing) {
            return ContextCompat.getColor(context, android.R.color.darker_gray);
        }

        switch (readStatus) {
            case READ_STATUS_SENT:
                return ContextCompat.getColor(context, android.R.color.darker_gray);
            case READ_STATUS_READ:
                return ContextCompat.getColor(context, android.R.color.holo_blue_dark);
            default:
                return ContextCompat.getColor(context, android.R.color.darker_gray);
        }
    }

    public boolean hasDisplayableContent() {
        return (body != null && !body.trim().isEmpty()) ||
                hasAttachments() ||
                hasForwardedMessages ||
                systemMessage;
    }

    public String getTooltipText() {
        StringBuilder tooltip = new StringBuilder();

        if (senderName != null) {
            tooltip.append("От: ").append(senderName).append("\n");
        }

        tooltip.append("Время: ").append(getFormattedDate()).append("\n");

        if (hasAttachments()) {
            tooltip.append("Вложения: ").append(getAttachmentCount()).append("\n");
        }

        if (outgoing) {
            tooltip.append("Статус: ").append(getReadStatusText());
        }

        return tooltip.toString();
    }

    // Методы для работы с датами
    public String getFormattedTime() {
        if (date == 0) return "";

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(date));
    }

    public String getFormattedDate() {
        if (date == 0) return "";

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(date));
    }

    public String getFormattedDateOnly() {
        if (date == 0) return "";

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(date));
    }

    public boolean isToday() {
        if (date == 0) return false;

        java.util.Date messageDate = new java.util.Date(date);
        java.util.Date today = new java.util.Date();

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
        return sdf.format(messageDate).equals(sdf.format(today));
    }

    public boolean isYesterday() {
        if (date == 0) return false;

        java.util.Date messageDate = new java.util.Date(date);
        java.util.Date yesterday = new java.util.Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
        return sdf.format(messageDate).equals(sdf.format(yesterday));
    }

    public String getRelativeTime() {
        if (date == 0) return "";

        long now = System.currentTimeMillis();
        long diff = now - date;

        if (diff < 60 * 1000) {
            return "только что";
        } else if (diff < 60 * 60 * 1000) {
            long minutes = diff / (60 * 1000);
            return minutes + " " + getMinutesText((int) minutes) + " назад";
        } else if (diff < 24 * 60 * 60 * 1000) {
            long hours = diff / (60 * 60 * 1000);
            return hours + " " + getHoursText((int) hours) + " назад";
        } else if (isToday()) {
            return getFormattedTime();
        } else if (isYesterday()) {
            return "вчера " + getFormattedTime();
        } else {
            return getFormattedDate();
        }
    }

    private String getMinutesText(int minutes) {
        if (minutes % 10 == 1 && minutes % 100 != 11) {
            return "минуту";
        } else if (minutes % 10 >= 2 && minutes % 10 <= 4 && (minutes % 100 < 10 || minutes % 100 >= 20)) {
            return "минуты";
        } else {
            return "минут";
        }
    }

    private String getHoursText(int hours) {
        if (hours % 10 == 1 && hours % 100 != 11) {
            return "час";
        } else if (hours % 10 >= 2 && hours % 10 <= 4 && (hours % 100 < 10 || hours % 100 >= 20)) {
            return "часа";
        } else {
            return "часов";
        }
    }

    // Методы для копирования сообщений
    public Message copyWithNewText(String newText) {
        return new Builder()
                .setMessageId(messageId)
                .setSenderId(senderId)
                .setSenderName(senderName)
                .setBody(newText)
                .setDate(date)
                .setAvatarUrl(avatarUrl)
                .setReadStatus(readStatus)
                .setOutgoing(outgoing)
                .setImportant(isImportant)
                .setPeerId(peerId)
                .setSystemMessage(systemMessage)
                .setAttachments(new ArrayList<>(attachments))
                .setPreviewText(previewText)
                .setHasForwardedMessages(hasForwardedMessages)
                .setForwardedMessagesCount(forwardedMessagesCount)
                .setUnreadCount(unreadCount)
                .build();
    }

    public Message createPreviewCopy() {
        Message preview = new Message();
        preview.setMessageId(messageId);
        preview.setSenderId(senderId);
        preview.setSenderName(senderName);
        preview.setBody(getPreviewText());
        preview.setDate(date);
        preview.setAvatarUrl(avatarUrl);
        preview.setReadStatus(readStatus);
        preview.setOutgoing(outgoing);
        preview.setUnreadCount(unreadCount);
        return preview;
    }

    // Методы сравнения
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Message message = (Message) o;

        if (date != message.date) return false;
        if (readStatus != message.readStatus) return false;
        if (outgoing != message.outgoing) return false;
        if (isImportant != message.isImportant) return false;
        if (systemMessage != message.systemMessage) return false;
        if (hasForwardedMessages != message.hasForwardedMessages) return false;
        if (forwardedMessagesCount != message.forwardedMessagesCount) return false;
        if (unreadCount != message.unreadCount) return false;
        if (messageId != null ? !messageId.equals(message.messageId) : message.messageId != null) return false;
        if (senderId != null ? !senderId.equals(message.senderId) : message.senderId != null) return false;
        if (senderName != null ? !senderName.equals(message.senderName) : message.senderName != null) return false;
        if (body != null ? !body.equals(message.body) : message.body != null) return false;
        if (avatarUrl != null ? !avatarUrl.equals(message.avatarUrl) : message.avatarUrl != null) return false;
        if (peerId != null ? !peerId.equals(message.peerId) : message.peerId != null) return false;
        if (attachments != null ? !attachments.equals(message.attachments) : message.attachments != null) return false;
        return previewText != null ? previewText.equals(message.previewText) : message.previewText == null;
    }

    @Override
    public int hashCode() {
        int result = messageId != null ? messageId.hashCode() : 0;
        result = 31 * result + (senderId != null ? senderId.hashCode() : 0);
        result = 31 * result + (senderName != null ? senderName.hashCode() : 0);
        result = 31 * result + (body != null ? body.hashCode() : 0);
        result = 31 * result + (int) (date ^ (date >>> 32));
        result = 31 * result + (avatarUrl != null ? avatarUrl.hashCode() : 0);
        result = 31 * result + readStatus;
        result = 31 * result + (outgoing ? 1 : 0);
        result = 31 * result + (isImportant ? 1 : 0);
        result = 31 * result + (peerId != null ? peerId.hashCode() : 0);
        result = 31 * result + (systemMessage ? 1 : 0);
        result = 31 * result + (attachments != null ? attachments.hashCode() : 0);
        result = 31 * result + (previewText != null ? previewText.hashCode() : 0);
        result = 31 * result + (hasForwardedMessages ? 1 : 0);
        result = 31 * result + forwardedMessagesCount;
        result = 31 * result + unreadCount;
        return result;
    }

    @Override
    public String toString() {
        return "Message{" +
                "messageId='" + messageId + '\'' +
                ", senderId='" + senderId + '\'' +
                ", senderName='" + senderName + '\'' +
                ", body='" + body + '\'' +
                ", date=" + date +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", readStatus=" + readStatus +
                ", outgoing=" + outgoing +
                ", isImportant=" + isImportant +
                ", peerId='" + peerId + '\'' +
                ", systemMessage=" + systemMessage +
                ", attachments=" + (attachments != null ? attachments.size() : 0) +
                ", previewText='" + previewText + '\'' +
                ", hasForwardedMessages=" + hasForwardedMessages +
                ", forwardedMessagesCount=" + forwardedMessagesCount +
                ", unreadCount=" + unreadCount +
                '}';
    }

    // Builder pattern
    public static class Builder {
        private Message message;

        public Builder() {
            message = new Message();
        }

        public Builder setMessageId(String messageId) {
            message.messageId = messageId;
            return this;
        }

        public Builder setSenderId(String senderId) {
            message.senderId = senderId;
            return this;
        }

        public Builder setSenderName(String senderName) {
            message.senderName = senderName;
            return this;
        }

        public Builder setBody(String body) {
            message.body = body;
            return this;
        }

        public Builder setDate(long date) {
            message.date = date;
            return this;
        }

        public Builder setAvatarUrl(String avatarUrl) {
            message.avatarUrl = avatarUrl;
            return this;
        }

        public Builder setReadStatus(int readStatus) {
            message.readStatus = readStatus;
            return this;
        }

        public Builder setOutgoing(boolean outgoing) {
            message.outgoing = outgoing;
            return this;
        }

        public Builder setImportant(boolean important) {
            message.isImportant = important;
            return this;
        }

        public Builder setPeerId(String peerId) {
            message.peerId = peerId;
            return this;
        }

        public Builder setSystemMessage(boolean systemMessage) {
            message.systemMessage = systemMessage;
            return this;
        }

        public Builder setAttachments(List<Attachment> attachments) {
            message.attachments = attachments;
            return this;
        }

        public Builder addAttachment(Attachment attachment) {
            if (message.attachments == null) {
                message.attachments = new ArrayList<>();
            }
            message.attachments.add(attachment);
            return this;
        }

        public Builder setPreviewText(String previewText) {
            message.previewText = previewText;
            return this;
        }

        public Builder setHasForwardedMessages(boolean hasForwardedMessages) {
            message.hasForwardedMessages = hasForwardedMessages;
            return this;
        }

        public Builder setForwardedMessagesCount(int forwardedMessagesCount) {
            message.forwardedMessagesCount = forwardedMessagesCount;
            return this;
        }

        public Builder setUnreadCount(int unreadCount) {
            message.unreadCount = unreadCount;
            return this;
        }

        public Message build() {
            return message;
        }
    }

    // Статические методы-хелперы
    public static Message createTextMessage(String senderId, String senderName, String text, long date, String peerId) {
        return new Builder()
                .setSenderId(senderId)
                .setSenderName(senderName)
                .setBody(text)
                .setDate(date)
                .setPeerId(peerId)
                .build();
    }

    public static Message createIncomingMessage(String senderId, String senderName, String text, long date) {
        return new Builder()
                .setSenderId(senderId)
                .setSenderName(senderName)
                .setBody(text)
                .setDate(date)
                .setReadStatus(READ_STATUS_INCOMING)
                .setOutgoing(false)
                .build();
    }

    public static Message createOutgoingMessage(String senderId, String senderName, String text, long date) {
        return new Builder()
                .setSenderId(senderId)
                .setSenderName(senderName)
                .setBody(text)
                .setDate(date)
                .setReadStatus(READ_STATUS_SENT)
                .setOutgoing(true)
                .build();
    }

    public static Message createSystemMessage(String text, long date) {
        return new Builder()
                .setSenderId("system")
                .setSenderName("System")
                .setBody(text)
                .setDate(date)
                .setSystemMessage(true)
                .build();
    }

    public static Message createErrorMessage(String senderId, String text, long date) {
        return new Builder()
                .setSenderId(senderId)
                .setBody(text)
                .setDate(date)
                .setSystemMessage(true)
                .setImportant(true)
                .build();
    }

    public static Message createTypingMessage(String senderId, String senderName) {
        return new Builder()
                .setSenderId(senderId)
                .setSenderName(senderName)
                .setBody("печатает...")
                .setDate(System.currentTimeMillis())
                .setSystemMessage(true)
                .build();
    }

    public static Message createReadReceipt(String messageId, long date) {
        return new Builder()
                .setMessageId(messageId + "_read")
                .setBody("//system:read")
                .setDate(date)
                .setSystemMessage(true)
                .build();
    }

    public static Message createAttachmentMessage(String senderId, String senderName, long date, String peerId, List<Attachment> attachments) {
        return new Builder()
                .setSenderId(senderId)
                .setSenderName(senderName)
                .setDate(date)
                .setPeerId(peerId)
                .setAttachments(attachments)
                .setPreviewText(generateAttachmentPreview(attachments))
                .build();
    }

    public static Message createForwardedMessage(String senderId, String senderName, long date, int forwardedCount) {
        return new Builder()
                .setSenderId(senderId)
                .setSenderName(senderName)
                .setDate(date)
                .setHasForwardedMessages(true)
                .setForwardedMessagesCount(forwardedCount)
                .setPreviewText("📩 Пересланные сообщения (" + forwardedCount + ")")
                .build();
    }

    public static Message createDialogMessage(String peerId, String dialogName, String lastMessage, long date, int unreadCount) {
        return new Builder()
                .setPeerId(peerId)
                .setSenderName(dialogName)
                .setBody(lastMessage)
                .setDate(date)
                .setUnreadCount(unreadCount)
                .build();
    }

    private static String generateAttachmentPreview(List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "(вложение)";
        }

        int photoCount = 0;
        int docCount = 0;
        int audioCount = 0;
        int audioMessageCount = 0;
        int stickerCount = 0;
        int videoCount = 0;
        int otherCount = 0;

        for (Attachment attachment : attachments) {
            switch (attachment.getType()) {
                case "photo":
                    photoCount++;
                    break;
                case "doc":
                    docCount++;
                    break;
                case "audio":
                    audioCount++;
                    break;
                case "audio_message":
                    audioMessageCount++;
                    break;
                case "sticker":
                    stickerCount++;
                    break;
                case "video":
                    videoCount++;
                    break;
                default:
                    otherCount++;
                    break;
            }
        }

        List<String> parts = new ArrayList<>();
        if (photoCount > 0) parts.add("📷 " + photoCount);
        if (docCount > 0) parts.add("📎 " + docCount);
        if (audioCount > 0) parts.add("🎵 " + audioCount);
        if (audioMessageCount > 0) parts.add("🎤 " + audioMessageCount);
        if (stickerCount > 0) parts.add("😊 " + stickerCount);
        if (videoCount > 0) parts.add("🎬 " + videoCount);
        if (otherCount > 0) parts.add("📁 " + otherCount);

        return String.join(" • ", parts);
    }

    // Компараторы для сортировки
    public static Comparator<Message> getDateComparator() {
        return (m1, m2) -> Long.compare(m1.getDate(), m2.getDate());
    }

    public static Comparator<Message> getDateComparatorDesc() {
        return (m1, m2) -> Long.compare(m2.getDate(), m1.getDate());
    }

    public static Comparator<Message> getUnreadComparator() {
        return (m1, m2) -> Integer.compare(m2.getUnreadCount(), m1.getUnreadCount());
    }
}