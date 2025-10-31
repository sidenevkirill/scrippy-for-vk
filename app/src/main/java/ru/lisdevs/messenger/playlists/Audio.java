package ru.lisdevs.messenger.playlists;

import android.os.Parcel;
import android.os.Parcelable;

public class Audio extends ru.lisdevs.messenger.music.Audio implements Parcelable {
    private String artist;
    private String title;
    private String url;
    private long id;
    private long ownerId;
    private int genreId;
    private int duration;
    private String coverUrl;

    // Конструкторы
    public Audio(String artist, String title, String url, String genreId) {
        this.artist = artist;
        this.title = title;
        this.url = url;
        this.duration = this.duration;
        this.coverUrl = coverUrl;
    }

    // Геттеры и сеттеры
    public String getArtist() { return artist; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public long getId() { return id; }
    public long getOwnerId() { return ownerId; }
    public int getGenreId() { return genreId; }

    public void setId(long id) { this.id = id; }
    public void setOwnerId(long ownerId) { this.ownerId = ownerId; }
    public void setGenreId(int genreId) { this.genreId = genreId; }

    public int getDuration() {
        return duration;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    // Parcelable implementation
    protected Audio(Parcel in) {
        artist = in.readString();
        title = in.readString();
        url = in.readString();
        id = in.readLong();
        ownerId = in.readLong();
        genreId = in.readInt();
    }

    public static final Creator<Audio> CREATOR = new Creator<Audio>() {
        @Override
        public Audio createFromParcel(Parcel in) {
            return new Audio(in);
        }

        @Override
        public Audio[] newArray(int size) {
            return new Audio[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(artist);
        dest.writeString(title);
        dest.writeString(url);
        dest.writeLong(id);
        dest.writeLong(ownerId);
        dest.writeInt(genreId);
    }
}