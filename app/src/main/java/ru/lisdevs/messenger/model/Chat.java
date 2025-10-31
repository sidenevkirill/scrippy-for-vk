package ru.lisdevs.messenger.model;

public class Chat {
    private String userName;
    private String lastMessage;
    private String time;
    private int avatarRes;
    private int unreadCount;
    private boolean isOnline;

    public Chat(String userName, String lastMessage, String time, int avatarRes) {
        this.userName = userName;
        this.lastMessage = lastMessage;
        this.time = time;
        this.avatarRes = avatarRes;
        this.unreadCount = 0;
        this.isOnline = false;
    }

    public Chat(String userName, String lastMessage, String time, int avatarRes, int unreadCount, boolean isOnline) {
        this.userName = userName;
        this.lastMessage = lastMessage;
        this.time = time;
        this.avatarRes = avatarRes;
        this.unreadCount = unreadCount;
        this.isOnline = isOnline;
    }

    // Геттеры и сеттеры
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public int getAvatarRes() { return avatarRes; }
    public void setAvatarRes(int avatarRes) { this.avatarRes = avatarRes; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
}