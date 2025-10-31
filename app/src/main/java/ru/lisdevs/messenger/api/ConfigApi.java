package ru.lisdevs.messenger.api;

import retrofit2.Call;
import retrofit2.http.GET;
import ru.lisdevs.messenger.api.model.AppConfig;

public interface ConfigApi {
    @GET("messenger.json")
    Call<AppConfig> getConfig();
}