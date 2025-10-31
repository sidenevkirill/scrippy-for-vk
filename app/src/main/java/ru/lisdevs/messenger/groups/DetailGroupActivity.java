package ru.lisdevs.messenger.groups;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ru.lisdevs.messenger.R;

public class DetailGroupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_group_details);

        TextView textView = findViewById(R.id.textViewName);

        // Получаем данные из Intent
        long groupId = getIntent().getLongExtra("group_id", -1);
        String groupName = getIntent().getStringExtra("group_name");

        // Отображаем информацию (можно расширить)
        textView.setText("ID группы: " + groupId + "\nНазвание: " + groupName);

        // Можно добавить дополнительные детали или функционал по необходимости
    }
}
