package ru.lisdevs.messenger.model;

public class Track {
    public String artist;
    public String title;

    public Track(String artist, String title) {
        this.artist = artist;
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public String getTitle() {
        return title;
    }
}
