package ru.lisdevs.messenger.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;
import java.util.Locale;

// Базовый класс для вложений
public class Attachment implements Parcelable {
    private String type;
    private Photo photo;
    private Document doc;
    private Audio audio;

    public Attachment() {}

    protected Attachment(Parcel in) {
        type = in.readString();
        photo = in.readParcelable(Photo.class.getClassLoader());
        doc = in.readParcelable(Document.class.getClassLoader());
        audio = in.readParcelable(Audio.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(type);
        dest.writeParcelable(photo, flags);
        dest.writeParcelable(doc, flags);
        dest.writeParcelable(audio, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Attachment> CREATOR = new Creator<Attachment>() {
        @Override
        public Attachment createFromParcel(Parcel in) {
            return new Attachment(in);
        }

        @Override
        public Attachment[] newArray(int size) {
            return new Attachment[size];
        }
    };

    // Геттеры и сеттеры
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Photo getPhoto() { return photo; }
    public void setPhoto(Photo photo) { this.photo = photo; }
    public Document getDoc() { return doc; }
    public void setDoc(Document doc) { this.doc = doc; }
    public Audio getAudio() { return audio; }
    public void setAudio(Audio audio) { this.audio = audio; }

    public static class Photo implements Parcelable {
        private List<Size> sizes;
        private String text;

        public Photo() {}

        protected Photo(Parcel in) {
            sizes = in.createTypedArrayList(Size.CREATOR);
            text = in.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeTypedList(sizes);
            dest.writeString(text);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Photo> CREATOR = new Creator<Photo>() {
            @Override
            public Photo createFromParcel(Parcel in) {
                return new Photo(in);
            }

            @Override
            public Photo[] newArray(int size) {
                return new Photo[size];
            }
        };

        public List<Size> getSizes() { return sizes; }
        public void setSizes(List<Size> sizes) { this.sizes = sizes; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getBestQualityUrl() {
            if (sizes == null || sizes.isEmpty()) return null;

            // Ищем размеры по убыванию качества
            String[] preferredSizes = {"w", "z", "y", "x", "m", "s"};
            for (String sizeType : preferredSizes) {
                for (Size size : sizes) {
                    if (sizeType.equals(size.getType())) {
                        return size.getUrl();
                    }
                }
            }

            // Если не нашли предпочтительные, берем первую доступную
            return sizes.get(0).getUrl();
        }

        public String getPreviewUrl() {
            if (sizes == null || sizes.isEmpty()) return null;

            // Идеальные размеры для превью (в порядке приоритета)
            String[] idealPreviewSizes = {"m", "p", "q", "x", "y", "o", "s"};

            // Сначала ищем идеальные размеры
            for (String sizeType : idealPreviewSizes) {
                for (Size size : sizes) {
                    if (sizeType.equals(size.getType())) {
                        return size.getUrl();
                    }
                }
            }

            // Если не нашли идеальные, ищем размер с оптимальными параметрами
            Size bestPreview = findOptimalPreviewSize();
            if (bestPreview != null) {
                return bestPreview.getUrl();
            }

            // Если ничего не подошло, берем размер среднего качества
            return getMediumQualityUrl();
        }

        private Size findOptimalPreviewSize() {
            Size bestSize = null;
            int bestScore = 0;

            for (Size size : sizes) {
                int score = calculatePreviewScore(size);
                if (score > bestScore) {
                    bestScore = score;
                    bestSize = size;
                }
            }

            return bestSize;
        }

        private int calculatePreviewScore(Size size) {
            int width = size.getWidth();
            int height = size.getHeight();
            int maxDimension = Math.max(width, height);
            int score = 0;

            // Баллы за размер (оптимально 400-600 пикселей)
            if (maxDimension >= 300 && maxDimension <= 700) {
                score += 100;
            } else if (maxDimension >= 200 && maxDimension <= 800) {
                score += 50;
            } else if (maxDimension > 800) {
                score -= 20; // Штраф за слишком большой размер
            }

            // Баллы за соотношение сторон (близкое к стандартному)
            double aspectRatio = (double) Math.max(width, height) / Math.min(width, height);
            if (aspectRatio <= 1.5) {
                score += 30;
            }

            // Баллы за известные типы размеров
            String type = size.getType();
            if ("m".equals(type) || "p".equals(type) || "q".equals(type)) {
                score += 40;
            } else if ("x".equals(type) || "y".equals(type)) {
                score += 20;
            }

            return score;
        }

        public String getMediumQualityUrl() {
            if (sizes == null || sizes.isEmpty()) return null;

            // Приоритеты для среднего качества
            String[] mediumSizes = {"x", "y", "m", "p", "q", "o"};

            for (String sizeType : mediumSizes) {
                for (Size size : sizes) {
                    if (sizeType.equals(size.getType())) {
                        return size.getUrl();
                    }
                }
            }

            return sizes.get(sizes.size() / 2).getUrl(); // Берем средний размер из списка
        }
    }

    public static class Size implements Parcelable {
        private String type;
        private String url;
        private int width;
        private int height;

        public Size() {}

        protected Size(Parcel in) {
            type = in.readString();
            url = in.readString();
            width = in.readInt();
            height = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(type);
            dest.writeString(url);
            dest.writeInt(width);
            dest.writeInt(height);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Size> CREATOR = new Creator<Size>() {
            @Override
            public Size createFromParcel(Parcel in) {
                return new Size(in);
            }

            @Override
            public Size[] newArray(int size) {
                return new Size[size];
            }
        };

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
    }

    public static class Document implements Parcelable {
        private String id;
        private String title;
        private String ext;
        private String url;
        private int size;
        private String type;

        public Document() {}

        protected Document(Parcel in) {
            id = in.readString();
            title = in.readString();
            ext = in.readString();
            url = in.readString();
            size = in.readInt();
            type = in.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(id);
            dest.writeString(title);
            dest.writeString(ext);
            dest.writeString(url);
            dest.writeInt(size);
            dest.writeString(type);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Document> CREATOR = new Creator<Document>() {
            @Override
            public Document createFromParcel(Parcel in) {
                return new Document(in);
            }

            @Override
            public Document[] newArray(int size) {
                return new Document[size];
            }
        };

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getExt() { return ext; }
        public void setExt(String ext) { this.ext = ext; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getFormattedSize() {
            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
            } else {
                return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
            }
        }
    }

    public static class Audio implements Parcelable {
        private String artist;
        private String title;
        private int duration;
        private String url;

        public Audio() {}

        protected Audio(Parcel in) {
            artist = in.readString();
            title = in.readString();
            duration = in.readInt();
            url = in.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(artist);
            dest.writeString(title);
            dest.writeInt(duration);
            dest.writeString(url);
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

        public String getArtist() { return artist; }
        public void setArtist(String artist) { this.artist = artist; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getFormattedDuration() {
            int minutes = duration / 60;
            int seconds = duration % 60;
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }

        public String getDisplayTitle() {
            if (artist != null && title != null) {
                return artist + " - " + title;
            } else if (title != null) {
                return title;
            } else {
                return "Аудиозапись";
            }
        }
    }
}