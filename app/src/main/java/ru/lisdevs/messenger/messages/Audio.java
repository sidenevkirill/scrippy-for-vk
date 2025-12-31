package ru.lisdevs.messenger.messages;


import android.os.Parcel;
import android.os.Parcelable;

public class Audio implements Parcelable {
    private long id;
    private long ownerId;
    private String artist;
    private String title;
    private long duration;
    private String url;
    private String album;
    private String coverUrl;
    private String accessKey;
    private boolean isExplicit;

    // Конструкторы
    public Audio() {
    }

    public Audio(String artist, String title, String url) {
        this.artist = artist;
        this.title = title;
        this.url = url;
    }

    public Audio(long id, long ownerId, String artist, String title,
                 long duration, String url) {
        this.id = id;
        this.ownerId = ownerId;
        this.artist = artist;
        this.title = title;
        this.duration = duration;
        this.url = url;
    }

    public Audio(String artist, String title, String url, long duration) {
        this.artist = artist;
        this.title = title;
        this.url = url;
        this.duration = duration;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(long ownerId) {
        this.ownerId = ownerId;
    }

    public String getArtist() {
        return artist != null ? artist : "";
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getUrl() {
        return url != null ? url : "";
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAlbum() {
        return album != null ? album : "";
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getCoverUrl() {
        return coverUrl != null ? coverUrl : "";
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getAccessKey() {
        return accessKey != null ? accessKey : "";
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public boolean isExplicit() {
        return isExplicit;
    }

    public void setExplicit(boolean explicit) {
        isExplicit = explicit;
    }

    // Форматированная длительность
    public String getFormattedDuration() {
        if (duration <= 0) return "0:00";

        long minutes = duration / 60;
        long seconds = duration % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // Проверка валидности
    public boolean isValid() {
        return url != null && !url.isEmpty();
    }

    // Полное название
    public String getFullTitle() {
        if (artist != null && !artist.isEmpty() && title != null && !title.isEmpty()) {
            return artist + " - " + title;
        } else if (title != null && !title.isEmpty()) {
            return title;
        } else {
            return "Unknown Track";
        }
    }

    @Override
    public String toString() {
        return "Audio{" +
                "id=" + id +
                ", ownerId=" + ownerId +
                ", artist='" + artist + '\'' +
                ", title='" + title + '\'' +
                ", duration=" + duration +
                ", url='" + (url != null ? url.substring(0, Math.min(url.length(), 50)) + "..." : "null") + '\'' +
                '}';
    }

    // Parcelable implementation
    protected Audio(Parcel in) {
        id = in.readLong();
        ownerId = in.readLong();
        artist = in.readString();
        title = in.readString();
        duration = in.readLong();
        url = in.readString();
        album = in.readString();
        coverUrl = in.readString();
        accessKey = in.readString();
        isExplicit = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeLong(ownerId);
        dest.writeString(artist);
        dest.writeString(title);
        dest.writeLong(duration);
        dest.writeString(url);
        dest.writeString(album);
        dest.writeString(coverUrl);
        dest.writeString(accessKey);
        dest.writeByte((byte) (isExplicit ? 1 : 0));
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Audio audio = (Audio) o;

        // Сравниваем по ID если они есть
        if (id != 0 && audio.id != 0) {
            return id == audio.id && ownerId == audio.ownerId;
        }

        // Иначе сравниваем по URL
        if (url != null && audio.url != null) {
            return url.equals(audio.url);
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (int) (ownerId ^ (ownerId >>> 32));
        result = 31 * result + (artist != null ? artist.hashCode() : 0);
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + (url != null ? url.hashCode() : 0);
        return result;
    }
}