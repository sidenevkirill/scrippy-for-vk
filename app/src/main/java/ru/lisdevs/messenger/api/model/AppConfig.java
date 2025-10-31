package ru.lisdevs.messenger.api.model;

import com.google.gson.annotations.SerializedName;

public class AppConfig {
    @SerializedName("active_activity")
    private String activeActivity;

    public String getActiveActivity() {
        return activeActivity;
    }
}
