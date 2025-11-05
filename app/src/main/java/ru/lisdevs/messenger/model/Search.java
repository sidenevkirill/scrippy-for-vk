package ru.lisdevs.messenger.model;


import android.os.Parcel;
import android.os.Parcelable;

public class Search implements Parcelable {
    private String artist;
    private String title;
    private String url;
    private long ownerId;
    private long audioId;

    public Search() {}

    // Геттеры и сеттеры
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public long getOwnerId() { return ownerId; }
    public void setOwnerId(long ownerId) { this.ownerId = ownerId; }

    public long getAudioId() { return audioId; }
    public void setAudioId(long audioId) { this.audioId = audioId; }

    // Parcelable реализация
    protected Search(Parcel in) {
        artist = in.readString();
        title = in.readString();
        url = in.readString();
        ownerId = in.readLong();
        audioId = in.readLong();
    }

    public static final Creator<Search> CREATOR = new Creator<Search>() {
        @Override
        public Search createFromParcel(Parcel in) {
            return new Search(in);
        }

        @Override
        public Search[] newArray(int size) {
            return new Search[size];
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
        dest.writeLong(ownerId);
        dest.writeLong(audioId);
    }
}