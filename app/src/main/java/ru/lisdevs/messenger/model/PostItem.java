package ru.lisdevs.messenger.model;

import java.util.List;

import ru.lisdevs.messenger.news.AudioAttachment;

public class PostItem {
    private String postId;
    private String text; // или message, content и т.п.
    private String date;
    private List<ru.lisdevs.messenger.news.AudioAttachment> audioAttachments;
    private String coverImageUrl;
    private String groupName;

    // Конструктор
    public PostItem(String postId, String text, String date, List<AudioAttachment> audioAttachments, String coverImageUrl, String groupName) {
        this.postId= postId;
        this.text= text;
        this.date= date;
        this.audioAttachments= audioAttachments;
        this.coverImageUrl= coverImageUrl;
        this.groupName= groupName;
    }

    // Геттеры
    public String getText() {
        return text;
    }

    public String getDate() {
        return date;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl= coverImageUrl;
    }

    // Другие геттеры и сеттеры
}