package ru.lisdevs.messenger.model;

import android.os.Parcel;
import android.os.Parcelable;

public class Playlist implements Parcelable {
    private int id;
    private String title;
    private long ownerId;
    private String photoUrl;
    private int count;
    private String accessKey;
    private boolean isFollowing;
    private String description;

    public Playlist(int id, String title, long ownerId, String photoUrl, int count, String accessKey, boolean isFollowing, String description) {
        this.id = id;
        this.title = title;
        this.ownerId = ownerId;
        this.photoUrl = photoUrl;
    }

    // Геттеры и сеттеры
    public int getId() { return id; }
    public String getTitle() { return title; }
    public long getOwnerId() { return ownerId; }
    public String getPhotoUrl() { return photoUrl; }
    public int getCount() { return count; }
    public String getAccessKey() { return accessKey; }
    public boolean isFollowing() { return isFollowing; }
    public String getDescription() { return description; }

    // Parcelable implementation
    protected Playlist(Parcel in) {
        id = in.readInt();
        title = in.readString();
        ownerId = in.readLong();
        photoUrl = in.readString();
        count = in.readInt();
        accessKey = in.readString();
        isFollowing = in.readByte() != 0;
        description = in.readString();
    }

    public static final Creator<Playlist> CREATOR = new Creator<Playlist>() {
        @Override
        public Playlist createFromParcel(Parcel in) {
            return new Playlist(in);
        }

        @Override
        public Playlist[] newArray(int size) {
            return new Playlist[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(title);
        dest.writeLong(ownerId);
        dest.writeString(photoUrl);
        dest.writeInt(count);
        dest.writeString(accessKey);
        dest.writeByte((byte) (isFollowing ? 1 : 0));
        dest.writeString(description);
    }
}