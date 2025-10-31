package ru.lisdevs.messenger.model;


public class AutoResponse {
    private int id;
    private String keyword;
    private String response;
    private boolean isActive;
    private String category;
    private boolean isPredefined;

    public AutoResponse() {}

    public AutoResponse(String keyword, String response, boolean isActive) {
        this.keyword = keyword;
        this.response = response;
        this.isActive = isActive;
        this.category = "Общее";
        this.isPredefined = false;
    }

    public AutoResponse(String keyword, String response, boolean isActive, String category) {
        this.keyword = keyword;
        this.response = response;
        this.isActive = isActive;
        this.category = category;
        this.isPredefined = false;
    }

    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isPredefined() { return isPredefined; }
    public void setPredefined(boolean predefined) { isPredefined = predefined; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AutoResponse that = (AutoResponse) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}