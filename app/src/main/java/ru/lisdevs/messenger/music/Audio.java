package ru.lisdevs.messenger.music;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

@SuppressLint("ParcelCreator")
public class Audio extends ru.lisdevs.messenger.official.audios.Audio implements Parcelable {
    private String artist;
    private String title;
    private String url;
    private int duration;
    private String coverUrl;
    private int albumId;
    private long ownerId;
    private long audioId;
    private long lyricsId;
    private long id;
    private int genreId;
    private String cacheKey;
    private String albumTitle;

    public Audio(String artist, String title, String url) {
        this.artist = artist;
        this.title = title;
        this.url = url;
    }

    protected Audio(Parcel in) {
        artist = in.readString();
        title = in.readString();
        url = in.readString();
        duration = in.readInt();
        coverUrl = in.readString();
        albumId = in.readInt();
        ownerId = in.readLong();
        audioId = in.readLong();
        lyricsId = in.readLong();
        id = in.readLong();
        genreId = in.readInt();
        cacheKey = in.readString();
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

    public Audio() {
    }


    public String getAlbumTitle() { return albumTitle; }
    public void setAlbumTitle(String albumTitle) { this.albumTitle = albumTitle; }

    public String getArtist() {
        return artist;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public int getAlbumId() {
        return albumId;
    }

    public void setAlbumId(int albumId) {
        this.albumId = albumId;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(long ownerId) {
        this.ownerId = ownerId;
    }

    public long getAudioId() {
        return audioId;
    }

    public void setAudioId(long audioId) {
        this.audioId = audioId;
    }

    public int getLyricsId() {
        return (int) lyricsId;
    }

    public void setLyricsId(long lyricsId) {
        this.lyricsId = lyricsId;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getGenreId() {
        return genreId;
    }

    public void setGenreId(int genreId) {
        this.genreId = genreId;
    }

    public void updateInfo(String artist, String title) {
        this.artist = artist;
        this.title = title;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(artist);
        dest.writeString(title);
        dest.writeString(url);
        dest.writeInt(duration);
        dest.writeString(coverUrl);
        dest.writeInt(albumId);
        dest.writeLong(ownerId);
        dest.writeLong(audioId);
        dest.writeLong(lyricsId);
        dest.writeLong(id);
        dest.writeInt(genreId);
        dest.writeString(cacheKey);
    }
}