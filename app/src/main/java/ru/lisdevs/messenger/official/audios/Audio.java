package ru.lisdevs.messenger.official.audios;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public class Audio implements Parcelable {
    private String artist;
    private String title;
    private String url;
    private int duration;
    private int genreId;
    private long audioId;
    private long ownerId;
    private int lyricsId;
    private int albumId;

    // Конструкторы
    public Audio() {}

    public Audio(String artist, String title, String url) {
        this.artist = artist;
        this.title = title;
        this.url = url;
    }

    public Audio(String artist, String title, String url, int duration) {
        this.artist = artist;
        this.title = title;
        this.url = url;
        this.duration = duration;
    }

    // Геттеры и сеттеры
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public int getGenreId() { return genreId; }
    public void setGenreId(int genreId) { this.genreId = genreId; }

    public long getAudioId() { return audioId; }
    public void setAudioId(long audioId) { this.audioId = audioId; }

    public long getOwnerId() { return ownerId; }
    public void setOwnerId(long ownerId) { this.ownerId = ownerId; }

    public int getLyricsId() { return lyricsId; }
    public void setLyricsId(int lyricsId) { this.lyricsId = lyricsId; }

    public int getAlbumId() { return albumId; }
    public void setAlbumId(int albumId) { this.albumId = albumId; }

    // Parcelable implementation
    protected Audio(Parcel in) {
        artist = in.readString();
        title = in.readString();
        url = in.readString();
        duration = in.readInt();
        genreId = in.readInt();
        audioId = in.readLong();
        ownerId = in.readLong();
        lyricsId = in.readInt();
        albumId = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(artist);
        dest.writeString(title);
        dest.writeString(url);
        dest.writeInt(duration);
        dest.writeInt(genreId);
        dest.writeLong(audioId);
        dest.writeLong(ownerId);
        dest.writeInt(lyricsId);
        dest.writeInt(albumId);
    }

    @Override
    public int describeContents() {
        return 0;
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

    // equals и hashCode для корректной работы коллекций
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Audio audio = (Audio) o;
        return audioId == audio.audioId && ownerId == audio.ownerId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(audioId, ownerId);
    }

    @Override
    public String toString() {
        return "Audio{" +
                "artist='" + artist + '\'' +
                ", title='" + title + '\'' +
                ", url='" + (url != null ? "present" : "null") + '\'' +
                ", duration=" + duration +
                ", audioId=" + audioId +
                ", ownerId=" + ownerId +
                '}';
    }
}