package ru.lisdevs.messenger.model;


import android.os.Parcel;
import android.os.Parcelable;

public class StickerImage implements Parcelable {
    private String url;
    private int width;
    private int height;

    public StickerImage() {}

    public StickerImage(String url, int width, int height) {
        this.url = url;
        this.width = width;
        this.height = height;
    }

    protected StickerImage(Parcel in) {
        url = in.readString();
        width = in.readInt();
        height = in.readInt();
    }

    public static final Creator<StickerImage> CREATOR = new Creator<StickerImage>() {
        @Override
        public StickerImage createFromParcel(Parcel in) {
            return new StickerImage(in);
        }

        @Override
        public StickerImage[] newArray(int size) {
            return new StickerImage[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(url);
        dest.writeInt(width);
        dest.writeInt(height);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // Геттеры и сеттеры
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    @Override
    public String toString() {
        return "StickerImage{" +
                "url='" + url + '\'' +
                ", width=" + width +
                ", height=" + height +
                '}';
    }
}