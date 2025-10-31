package ru.lisdevs.messenger.video;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class VideoItem implements Parcelable {
    public String title;
    public String description;
    public String videoUrl;
    public String thumbnailUrl;
    public int duration;
    public int views;
    public long date;
    public int ownerId;
    public int videoId;
    public String accessKey;
    public boolean isProcessing;
    public boolean isLive;
    public boolean canEdit;
    public boolean canAdd;
    public boolean canComment;
    public boolean canRepost;
    public String platform;
    public int comments;
    public int likes;
    public int reposts;
    public String playerType;

    // Конструктор по умолчанию
    public VideoItem() {}

    // Основной конструктор
    public VideoItem(String title, String description, String videoUrl, String thumbnailUrl,
                     int duration, int views, long date) {
        this.title = title;
        this.description = description;
        this.videoUrl = videoUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
        this.views = views;
        this.date = date;
    }

    // Полный конструктор
    public VideoItem(String title, String description, String videoUrl, String thumbnailUrl,
                     int duration, int views, long date, int ownerId, int videoId, String accessKey,
                     boolean isProcessing, boolean isLive, boolean canEdit, boolean canAdd,
                     boolean canComment, boolean canRepost, String platform, int comments,
                     int likes, int reposts, String playerType) {
        this.title = title;
        this.description = description;
        this.videoUrl = videoUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
        this.views = views;
        this.date = date;
        this.ownerId = ownerId;
        this.videoId = videoId;
        this.accessKey = accessKey;
        this.isProcessing = isProcessing;
        this.isLive = isLive;
        this.canEdit = canEdit;
        this.canAdd = canAdd;
        this.canComment = canComment;
        this.canRepost = canRepost;
        this.platform = platform;
        this.comments = comments;
        this.likes = likes;
        this.reposts = reposts;
        this.playerType = playerType;
    }

    // Конструктор для Parcelable
    protected VideoItem(Parcel in) {
        title = in.readString();
        description = in.readString();
        videoUrl = in.readString();
        thumbnailUrl = in.readString();
        duration = in.readInt();
        views = in.readInt();
        date = in.readLong();
        ownerId = in.readInt();
        videoId = in.readInt();
        accessKey = in.readString();
        isProcessing = in.readByte() != 0;
        isLive = in.readByte() != 0;
        canEdit = in.readByte() != 0;
        canAdd = in.readByte() != 0;
        canComment = in.readByte() != 0;
        canRepost = in.readByte() != 0;
        platform = in.readString();
        comments = in.readInt();
        likes = in.readInt();
        reposts = in.readInt();
        playerType = in.readString();
    }

    public static final Creator<VideoItem> CREATOR = new Parcelable.Creator<VideoItem>() {
        @Override
        public VideoItem createFromParcel(Parcel in) {
            return new VideoItem(in);
        }

        @Override
        public VideoItem[] newArray(int size) {
            return new VideoItem[size];
        }
    };

    // Геттеры и сеттеры
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getViews() {
        return views;
    }

    public void setViews(int views) {
        this.views = views;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
    }

    public int getVideoId() {
        return videoId;
    }

    public void setVideoId(int videoId) {
        this.videoId = videoId;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public boolean isProcessing() {
        return isProcessing;
    }

    public void setProcessing(boolean processing) {
        isProcessing = processing;
    }

    public boolean isLive() {
        return isLive;
    }

    public void setLive(boolean live) {
        isLive = live;
    }

    public boolean canEdit() {
        return canEdit;
    }

    public void setCanEdit(boolean canEdit) {
        this.canEdit = canEdit;
    }

    public boolean canAdd() {
        return canAdd;
    }

    public void setCanAdd(boolean canAdd) {
        this.canAdd = canAdd;
    }

    public boolean canComment() {
        return canComment;
    }

    public void setCanComment(boolean canComment) {
        this.canComment = canComment;
    }

    public boolean canRepost() {
        return canRepost;
    }

    public void setCanRepost(boolean canRepost) {
        this.canRepost = canRepost;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public int getComments() {
        return comments;
    }

    public void setComments(int comments) {
        this.comments = comments;
    }

    public int getLikes() {
        return likes;
    }

    public void setLikes(int likes) {
        this.likes = likes;
    }

    public int getReposts() {
        return reposts;
    }

    public void setReposts(int reposts) {
        this.reposts = reposts;
    }

    public String getPlayerType() {
        return playerType;
    }

    public void setPlayerType(String playerType) {
        this.playerType = playerType;
    }

    // Вспомогательные методы
    public String getFormattedDuration() {
        if (duration <= 0) return "";

        int hours = duration / 3600;
        int minutes = (duration % 3600) / 60;
        int seconds = duration % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    public String getFormattedViews() {
        if (views >= 1000000) {
            return String.format(Locale.getDefault(), "%.1fM", views / 1000000.0);
        } else if (views >= 1000) {
            return String.format(Locale.getDefault(), "%.1fK", views / 1000.0);
        } else {
            return String.valueOf(views);
        }
    }

    public String getFormattedDate() {
        return formatDate(date);
    }

    private String formatDate(long timestamp) {
        Date date = new Date(timestamp * 1000);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        return sdf.format(date);
    }

    public boolean isValid() {
        return videoUrl != null && !videoUrl.isEmpty() && !videoUrl.equals("null");
    }

    public boolean hasThumbnail() {
        return thumbnailUrl != null && !thumbnailUrl.isEmpty();
    }

    public String getVkVideoLink() {
        if (ownerId != 0 && videoId != 0) {
            if (accessKey != null && !accessKey.isEmpty()) {
                return "https://vk.com/video" + ownerId + "_" + videoId + "?access_key=" + accessKey;
            } else {
                return "https://vk.com/video" + ownerId + "_" + videoId;
            }
        }
        return null;
    }

    // Методы для сравнения
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoItem videoItem = (VideoItem) o;
        return videoId == videoItem.videoId && ownerId == videoItem.ownerId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, videoId);
    }

    @Override
    public String toString() {
        return "VideoItem{" +
                "title='" + title + '\'' +
                ", videoId=" + videoId +
                ", ownerId=" + ownerId +
                ", views=" + views +
                ", duration=" + duration +
                '}';
    }

    // Parcelable методы
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeString(description);
        dest.writeString(videoUrl);
        dest.writeString(thumbnailUrl);
        dest.writeInt(duration);
        dest.writeInt(views);
        dest.writeLong(date);
        dest.writeInt(ownerId);
        dest.writeInt(videoId);
        dest.writeString(accessKey);
        dest.writeByte((byte) (isProcessing ? 1 : 0));
        dest.writeByte((byte) (isLive ? 1 : 0));
        dest.writeByte((byte) (canEdit ? 1 : 0));
        dest.writeByte((byte) (canAdd ? 1 : 0));
        dest.writeByte((byte) (canComment ? 1 : 0));
        dest.writeByte((byte) (canRepost ? 1 : 0));
        dest.writeString(platform);
        dest.writeInt(comments);
        dest.writeInt(likes);
        dest.writeInt(reposts);
        dest.writeString(playerType);
    }

    // Builder класс для удобного создания объектов
    public static class Builder {
        private final VideoItem videoItem;

        public Builder() {
            videoItem = new VideoItem();
        }

        public Builder setTitle(String title) {
            videoItem.title = title;
            return this;
        }

        public Builder setDescription(String description) {
            videoItem.description = description;
            return this;
        }

        public Builder setVideoUrl(String videoUrl) {
            videoItem.videoUrl = videoUrl;
            return this;
        }

        public Builder setThumbnailUrl(String thumbnailUrl) {
            videoItem.thumbnailUrl = thumbnailUrl;
            return this;
        }

        public Builder setDuration(int duration) {
            videoItem.duration = duration;
            return this;
        }

        public Builder setViews(int views) {
            videoItem.views = views;
            return this;
        }

        public Builder setDate(long date) {
            videoItem.date = date;
            return this;
        }

        public Builder setOwnerId(int ownerId) {
            videoItem.ownerId = ownerId;
            return this;
        }

        public Builder setVideoId(int videoId) {
            videoItem.videoId = videoId;
            return this;
        }

        public Builder setAccessKey(String accessKey) {
            videoItem.accessKey = accessKey;
            return this;
        }

        public VideoItem build() {
            return videoItem;
        }
    }

    // Метод для создания из JSON
    public static VideoItem fromJson(JSONObject json) throws JSONException {
        VideoItem item = new VideoItem();

        item.title = json.optString("title", "");
        item.description = json.optString("description", "");
        item.duration = json.optInt("duration", 0);
        item.views = json.optInt("views", 0);
        item.date = json.optLong("date", 0);
        item.ownerId = json.optInt("owner_id", 0);
        item.videoId = json.optInt("id", 0);
        item.accessKey = json.optString("access_key", "");
        item.isProcessing = json.optBoolean("processing", false);
        item.isLive = json.optBoolean("live", false);
        item.canEdit = json.optBoolean("can_edit", false);
        item.canAdd = json.optBoolean("can_add", false);
        item.canComment = json.optBoolean("can_comment", false);
        item.canRepost = json.optBoolean("can_repost", false);
        item.platform = json.optString("platform", "");
        item.comments = json.optInt("comments", 0);

        // Обработка лайков
        if (json.has("likes")) {
            JSONObject likes = json.getJSONObject("likes");
            item.likes = likes.optInt("count", 0);
        }

        // Обработка репостов
        if (json.has("reposts")) {
            JSONObject reposts = json.getJSONObject("reposts");
            item.reposts = reposts.optInt("count", 0);
        }

        // Получение URL видео
        item.videoUrl = getVideoUrlFromJson(json);

        // Получение превью
        item.thumbnailUrl = getThumbnailUrlFromJson(json);

        return item;
    }

    private static String getVideoUrlFromJson(JSONObject json) throws JSONException {
        // Приоритет: mp4_1080 -> mp4_720 -> mp4_480 -> mp4_360 -> player
        if (json.has("files")) {
            JSONObject files = json.getJSONObject("files");
            String[] qualities = {"mp4_1080", "mp4_720", "mp4_480", "mp4_360", "mp4_240"};
            for (String quality : qualities) {
                if (files.has(quality)) {
                    String url = files.optString(quality);
                    if (url != null && !url.isEmpty()) {
                        return url;
                    }
                }
            }
        }

        // Если файлы не найдены, используем player
        return json.optString("player", "");
    }

    private static String getThumbnailUrlFromJson(JSONObject json) throws JSONException {
        // Приоритет: image array -> photo_800 -> photo_640 -> first_frame_320
        if (json.has("image")) {
            JSONArray images = json.getJSONArray("image");
            for (int i = images.length() - 1; i >= 0; i--) {
                JSONObject image = images.getJSONObject(i);
                String url = image.optString("url");
                if (url != null && !url.isEmpty()) {
                    return url;
                }
            }
        }

        String[] photoSizes = {"photo_800", "photo_640", "photo_320", "first_frame_800", "first_frame_320"};
        for (String size : photoSizes) {
            if (json.has(size)) {
                String url = json.optString(size);
                if (url != null && !url.isEmpty()) {
                    return url;
                }
            }
        }

        return "";
    }
}