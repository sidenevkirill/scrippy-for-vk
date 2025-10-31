package ru.lisdevs.messenger.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

public class Sticker implements Parcelable {
    private int id;
    private String name;
    private String imageUrl;
    private int width;
    private int height;
    private List<StickerImage> images;

    public Sticker() {}

    protected Sticker(Parcel in) {
        id = in.readInt();
        name = in.readString();
        imageUrl = in.readString();
        width = in.readInt();
        height = in.readInt();
        images = in.createTypedArrayList(StickerImage.CREATOR);
    }

    public static final Creator<Sticker> CREATOR = new Creator<Sticker>() {
        @Override
        public Sticker createFromParcel(Parcel in) {
            return new Sticker(in);
        }

        @Override
        public Sticker[] newArray(int size) {
            return new Sticker[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(name);
        dest.writeString(imageUrl);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeTypedList(images);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public List<StickerImage> getImages() { return images; }
    public void setImages(List<StickerImage> images) { this.images = images; }

    /**
     * Возвращает оптимальный URL изображения для отображения
     * Приоритеты:
     * 1. Из списка images с оптимальным размером
     * 2. Основной imageUrl
     * 3. Пустая строка (для эмодзи стикеров)
     */
    public String getOptimalImageUrl() {
        return getOptimalImageUrl(128); // Размер по умолчанию
    }

    /**
     * Возвращает оптимальный URL изображения для указанного размера
     * @param targetSize целевой размер в пикселях
     */
    public String getOptimalImageUrl(int targetSize) {
        // Если есть список images, ищем оптимальное изображение
        if (images != null && !images.isEmpty()) {
            StickerImage optimalImage = findOptimalImage(targetSize);
            if (optimalImage != null && optimalImage.getUrl() != null) {
                return cleanImageUrl(optimalImage.getUrl());
            }
        }

        // Если нет images, используем основной imageUrl
        if (imageUrl != null && !imageUrl.isEmpty()) {
            return cleanImageUrl(imageUrl);
        }

        // Для эмодзи стикеров возвращаем пустую строку
        return "";
    }

    /**
     * Находит оптимальное изображение из списка для целевого размера
     */
    private StickerImage findOptimalImage(int targetSize) {
        if (images == null || images.isEmpty()) {
            return null;
        }

        StickerImage closest = null;
        int minDiff = Integer.MAX_VALUE;

        for (StickerImage image : images) {
            if (image.getUrl() == null || image.getUrl().isEmpty()) {
                continue;
            }

            // Предпочтение отдаем изображениям, которые больше или равны целевому размеру
            int size = Math.max(image.getWidth(), image.getHeight());
            int diff = Math.abs(size - targetSize);

            // Если нашли идеальное совпадение, возвращаем сразу
            if (size >= targetSize && diff < minDiff) {
                closest = image;
                minDiff = diff;
            }
        }

        // Если не нашли подходящего изображения, берем самое большое
        if (closest == null) {
            closest = getLargestImage();
        }

        return closest;
    }

    /**
     * Возвращает самое большое изображение из списка
     */
    private StickerImage getLargestImage() {
        if (images == null || images.isEmpty()) {
            return null;
        }

        StickerImage largest = images.get(0);
        for (StickerImage image : images) {
            int currentSize = largest.getWidth() * largest.getHeight();
            int newSize = image.getWidth() * image.getHeight();
            if (newSize > currentSize) {
                largest = image;
            }
        }
        return largest;
    }

    /**
     * Очищает URL от ненужных параметров
     */
    private String cleanImageUrl(String url) {
        if (url == null) return "";

        // Удаляем параметры размера если они есть
        if (url.contains("?size=")) {
            return url.substring(0, url.indexOf("?size="));
        }

        // Удаляем другие ненужные параметры
        if (url.contains("?") && (url.contains("width=") || url.contains("height="))) {
            return url.substring(0, url.indexOf("?"));
        }

        return url;
    }

    /**
     * Проверяет, является ли стикер эмодзи (не имеет изображения)
     */
    public boolean isEmojiSticker() {
        String url = getOptimalImageUrl();
        return url == null || url.isEmpty();
    }

    /**
     * Возвращает эмодзи из названия стикера
     * Формат названия: "Emoji 😊"
     */
    public String getEmojiFromName() {
        if (name != null && name.startsWith("Emoji") && name.length() > 6) {
            return name.substring(6).trim();
        }
        return "😊"; // Fallback эмодзи
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sticker sticker = (Sticker) o;
        return id == sticker.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return "Sticker{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", width=" + width +
                ", height=" + height +
                ", imagesCount=" + (images != null ? images.size() : 0) +
                '}';
    }
}