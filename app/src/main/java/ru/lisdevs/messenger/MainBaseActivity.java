package ru.lisdevs.messenger;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import ru.lisdevs.messenger.api.ConfigApi;
import ru.lisdevs.messenger.api.model.AppConfig;
import ru.lisdevs.messenger.auth.AuthActivity;
import ru.lisdevs.messenger.auth.AuthLocalActivity;

public class MainBaseActivity extends AppCompatActivity {
    private static final String CONFIG_URL = "https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/";
    private static final String ACTIVITY_A = "ActivityLogin";
    private static final String ACTIVITY_B = "ActivityLocal";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_base);

        checkConfigAndRedirect();
    }

    private void checkConfigAndRedirect() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(CONFIG_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ConfigApi api = retrofit.create(ConfigApi.class);

        api.getConfig().enqueue(new Callback<AppConfig>() {
            @Override
            public void onResponse(Call<AppConfig> call, Response<AppConfig> response) {
                if (response.isSuccessful() && response.body() != null) {
                    redirectToActivity(response.body().getActiveActivity());
                } else {
                    startDefaultActivity();
                }
            }

            @Override
            public void onFailure(Call<AppConfig> call, Throwable t) {
                startDefaultActivity();
            }
        });
    }

    private void redirectToActivity(String activeActivity) {
        Class<?> targetActivity;

        switch (activeActivity) {
            case ACTIVITY_A:
                targetActivity = AuthActivity.class;
                break;
            case ACTIVITY_B:
                targetActivity = AuthLocalActivity.class;
                break;
            default:
                targetActivity = AuthLocalActivity.class;
        }

        Intent intent = new Intent(this, targetActivity);
        startActivity(intent);
        finish();
    }

    private void startDefaultActivity() {
        startActivity(new Intent(this, AuthLocalActivity.class));
        finish();
    }
}