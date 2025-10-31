package ru.lisdevs.messenger.posts;

import java.util.List;
import java.util.Map;

// Post.java
public class Post {
    public int id;
    public long date;
    public String text;
    public Author author;
    public List<Attachment> attachments;
    public int likesCount;
    public int commentsCount;
    public int repostsCount;
}

// Author.java
interface Author {
    String getName();
    String getPhoto();
    int getId();
}

// User.java
class User implements Author {
    public int id;
    public String firstName;
    public String lastName;
    public String photo100;

    public User(int id, String firstName, String lastName, String photo100) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.photo100 = photo100;
    }

    @Override
    public String getName() {
        return firstName + " " + lastName;
    }

    @Override
    public String getPhoto() {
        return photo100;
    }


    @Override
    public int getId() {
        return id;
    }
}

// Group.java
class Group implements Author {
    public int id;
    public String name;
    public String photo100;

    public Group(int id, String name, String photo100) {
        this.id = id;
        this.name = name;
        this.photo100 = photo100;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPhoto() {
        return photo100;
    }

    @Override
    public int getId() {
        return id;
    }
}

// Attachment.java
interface Attachment {
    String getType();
}

// PhotoAttachment.java
class PhotoAttachment implements Attachment {
    public int id;
    public int albumId;
    public int ownerId;
    public String text;
    public Map<String, String> sizes;

    @Override
    public String getType() {
        return "photo";
    }
}

// AudioAttachment.java
class AudioAttachment implements Attachment {
    public int id;
    public int ownerId;
    public String artist;
    public String title;
    public int duration;
    public String url;

    @Override
    public String getType() {
        return "audio";
    }
}

// PlaylistAttachment.java
class PlaylistAttachment implements Attachment {
    public int id;
    public int ownerId;
    public String title;
    public String description;
    public int count;
    public PhotoAttachment photo;
    public String accessKey;

    @Override
    public String getType() {
        return "playlist";
    }
}