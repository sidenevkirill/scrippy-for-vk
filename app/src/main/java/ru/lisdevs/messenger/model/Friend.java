package ru.lisdevs.messenger.model;

public class Friend {
    private int audioCount;
    public long id;
    public String firstName;
    public String lastName;
    public String screenName;
    public String photoUrl; // URL аватарки

    public Friend(long id, String firstName, String lastName, String screenName, String photoUrl) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.screenName = screenName;
        this.photoUrl = photoUrl;
    }

    public int getAudioCount() {
        return audioCount;
    }

    public void setAudioCount(int audioCount) {
        this.audioCount = audioCount;
    }

    // Геттеры и сеттеры
    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
}