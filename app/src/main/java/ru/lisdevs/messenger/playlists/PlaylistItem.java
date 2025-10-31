package ru.lisdevs.messenger.playlists;

public class PlaylistItem {
    private int id;
    private String title;
    private String ownerId;
    private String artist;
    private String coverUrl;

    public PlaylistItem(int id, String title, String ownerId, String artist, String coverUrl) {
        this.id = id;
        this.title = title;
        this.ownerId = ownerId;
        this.artist = artist;
        this.coverUrl = coverUrl;
    }

    // Геттеры
    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getArtist() {
        return artist;
    }

    public String getCoverUrl() {
        return coverUrl;
    }
}
