package ru.lisdevs.messenger.news;

public class AudioAttachment {
    private String artist;
    private String title;
    private String url;

    public AudioAttachment(String artist, String title, String url) {
        this.artist = artist;
        this.title = title;
        this.url = url;
    }

    // Геттеры
    public String getArtist() { return artist; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }

}
