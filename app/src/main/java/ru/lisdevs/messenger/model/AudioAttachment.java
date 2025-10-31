package ru.lisdevs.messenger.model;

public class AudioAttachment extends Attachment {
    private int id;
    private int ownerId;
    private String artist;
    private String title;
    private int duration;
    private String url;
    private String album;

    @Override
    public String getType() {
        return "audio";
    }

    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }
}