package ru.lisdevs.messenger.model;


public class Document {
    private String id;
    private String title;
    private String type;
    private String size;
    private String url;
    private long date;
    private String extension;

    public Document(String id, String title, String type, String size, String url, long date, String extension) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.size = size;
        this.url = url;
        this.date = date;
        this.extension = extension;
    }

    // Getters and setters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public String getSize() { return size; }
    public String getUrl() { return url; }
    public long getDate() { return date; }
    public String getExtension() { return extension; }
}