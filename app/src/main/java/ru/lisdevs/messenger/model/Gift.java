package ru.lisdevs.messenger.model;


public class Gift {
    private int id;
    private int stickerId;
    private String url;
    private String description;
    private int price;
    private boolean isFree;
    private String senderName;
    private long date;
    private String message;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getStickerId() { return stickerId; }
    public void setStickerId(int stickerId) { this.stickerId = stickerId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = price; }

    public boolean getIsFree() { return isFree; }
    public void setIsFree(boolean isFree) { this.isFree = isFree; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}