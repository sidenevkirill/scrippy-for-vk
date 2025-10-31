package ru.lisdevs.messenger;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    private TextView textViewResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_token);

        textViewResult = findViewById(R.id.textViewResult);

        String token = getIntent().getStringExtra("access_token");
        if (token != null && !token.isEmpty()) {
            // Тест прошел, отображаем результат
            textViewResult.setText("Тест прошел: получен токен:\n" + token);
        } else {
            // Тест не прошел
            textViewResult.setText("Тест не прошел: токен не получен");
        }
    }
}
