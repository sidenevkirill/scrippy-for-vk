package ru.lisdevs.messenger.model;

public class NewsPost {
    private String postId;
    private String text;
    private String date;

    public NewsPost(String postId, String text, String date) {
        this.postId = postId;
        this.text = text;
        this.date = date;
    }

    public String getPostId() { return postId; }
    public String getText() { return text; }
    public String getDate() { return date; }
}