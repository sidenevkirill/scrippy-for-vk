package ru.lisdevs.messenger.model;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.List;

public class StickerPack implements Parcelable {
    private int id;
    private String title;
    private List<Sticker> stickers;
    private int price;
    private String previewUrl;
    private String iconUrl;
    private boolean purchased;
    private boolean enabled; // Новое поле для управления включением/отключением пакета

    public StickerPack() {}

    protected StickerPack(Parcel in) {
        id = in.readInt();
        title = in.readString();
        stickers = in.createTypedArrayList(Sticker.CREATOR);
        price = in.readInt();
        previewUrl = in.readString();
        iconUrl = in.readString();
        purchased = in.readByte() != 0;
        enabled = in.readByte() != 0; // Чтение нового поля
    }

    public boolean containsSticker(int stickerId) {
        if (stickers != null) {
            for (Sticker sticker : stickers) {
                if (sticker.getId() == stickerId) {
                    return true;
                }
            }
        }
        return false;
    }

    public static final Creator<StickerPack> CREATOR = new Creator<StickerPack>() {
        @Override
        public StickerPack createFromParcel(Parcel in) {
            return new StickerPack(in);
        }

        @Override
        public StickerPack[] newArray(int size) {
            return new StickerPack[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(title);
        dest.writeTypedList(stickers);
        dest.writeInt(price);
        dest.writeString(previewUrl);
        dest.writeString(iconUrl);
        dest.writeByte((byte) (purchased ? 1 : 0));
        dest.writeByte((byte) (enabled ? 1 : 0)); // Запись нового поля
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<Sticker> getStickers() { return stickers; }
    public void setStickers(List<Sticker> stickers) { this.stickers = stickers; }

    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = price; }

    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public boolean isPurchased() { return purchased; }
    public void setPurchased(boolean purchased) { this.purchased = purchased; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    // Метод для обратной совместимости - если iconUrl не установлен, используем previewUrl
    public String getIconUrlSafe() {
        return iconUrl != null ? iconUrl : previewUrl;
    }

    // Метод для получения количества стикеров в пакете
    public int getStickerCount() {
        return stickers != null ? stickers.size() : 0;
    }

    // Метод для получения первого стикера в качестве превью
    public Sticker getFirstSticker() {
        if (stickers != null && !stickers.isEmpty()) {
            return stickers.get(0);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StickerPack that = (StickerPack) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return "StickerPack{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", stickerCount=" + getStickerCount() +
                ", price=" + price +
                ", purchased=" + purchased +
                ", enabled=" + enabled +
                '}';
    }
}