package ru.lisdevs.messenger.stories;

import java.io.Serializable;

public class StoryItem implements Serializable {
    private String id;
    private String mediaUrl;
    private String text;
    private long date;
    private int viewsCount;
    private boolean isExpired;

    public StoryItem(String id, String mediaUrl, String text, long date, int viewsCount, boolean isExpired) {
        this.id = id;
        this.mediaUrl = mediaUrl;
        this.text = text;
        this.date = date;
        this.viewsCount = viewsCount;
        this.isExpired = isExpired;
    }

    // Геттеры
    public String getId() { return id; }
    public String getMediaUrl() { return mediaUrl; }
    public String getText() { return text; }
    public long getDate() { return date; }
    public int getViewsCount() { return viewsCount; }
    public boolean isExpired() { return isExpired; }
}