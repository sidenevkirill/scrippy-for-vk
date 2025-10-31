package ru.lisdevs.messenger.messages;


import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.*;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.db.AutoResponseDBHelper;
import ru.lisdevs.messenger.model.AutoResponse;

import java.util.ArrayList;
import java.util.List;

public class AutoResponseActivity extends AppCompatActivity {
    private AutoResponseDBHelper dbHelper;
    private List<AutoResponse> responsesList;
    private AutoResponseAdapter adapter;
    private RecyclerView recyclerView;
    private FloatingActionButton fabAdd;
    private TextView emptyState;
    private Spinner categorySpinner;
    private List<String> categories;
    private String currentCategory = "Все";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_response);

        dbHelper = new AutoResponseDBHelper(this);

        initViews();
        setupCategorySpinner();
        loadResponses();

        // Проверяем обновления при запуске
        checkForUpdatesOnStart();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerViewResponses);
        fabAdd = findViewById(R.id.fabAdd);
        emptyState = findViewById(R.id.emptyState);
        categorySpinner = findViewById(R.id.categorySpinner);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        ImageView backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        ImageView resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(v -> showResetConfirmationDialog());

        ImageView updateButton = findViewById(R.id.updateButton);
        updateButton.setOnClickListener(v -> checkForUpdates());

        ImageView settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v -> showSettingsDialog());

        fabAdd.setOnClickListener(v -> showAddResponseDialog());
    }

    private void setupCategorySpinner() {
        categories = new ArrayList<>();
        categories.add("Все");
        categories.addAll(dbHelper.getCategories());

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(spinnerAdapter);

        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentCategory = categories.get(position);
                loadResponses();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadResponses() {
        if ("Все".equals(currentCategory)) {
            responsesList = dbHelper.getAllAutoResponses();
        } else {
            responsesList = dbHelper.getResponsesByCategory(currentCategory);
        }
        updateEmptyState();

        if (adapter == null) {
            adapter = new AutoResponseAdapter(responsesList, new AutoResponseAdapter.OnResponseActionListener() {
                @Override
                public void onEdit(AutoResponse response) {
                    if (!response.isPredefined()) {
                        showEditResponseDialog(response);
                    } else {
                        Toast.makeText(AutoResponseActivity.this,
                                "Предустановленные ответы нельзя редактировать",
                                Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onDelete(AutoResponse response) {
                    if (!response.isPredefined()) {
                        showDeleteConfirmationDialog(response);
                    } else {
                        Toast.makeText(AutoResponseActivity.this,
                                "Предустановленные ответы нельзя удалять",
                                Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onToggleActive(AutoResponse response) {
                    response.setActive(!response.isActive());
                    dbHelper.updateAutoResponse(response);
                    if (adapter != null) {
                        int position = responsesList.indexOf(response);
                        if (position != -1) {
                            adapter.notifyItemChanged(position);
                        }
                    }
                }
            });
            recyclerView.setAdapter(adapter);
        } else {
           // adapter.updateData(responsesList);
        }
    }

    private void updateEmptyState() {
        if (responsesList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void checkForUpdatesOnStart() {
        // Проверяем обновления только если давно не обновлялись (больше 24 часов)
        long lastUpdate = Long.parseLong(dbHelper.getConfig("last_update", "0"));
        long currentTime = System.currentTimeMillis();
        long hoursSinceUpdate = (currentTime - lastUpdate) / (1000 * 60 * 60);

        if (hoursSinceUpdate > 24) {
            checkForUpdates();
        }
    }

    private void checkForUpdates() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Проверка обновлений шаблонов...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Используем новый экземпляр DBHelper для этого вызова
        AutoResponseDBHelper updateDbHelper = new AutoResponseDBHelper(this);
        updateDbHelper.checkForUpdates(this, new AutoResponseDBHelper.UpdateCallback() {
            @Override
            public void onUpdateComplete(boolean success) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (success) {
                        Toast.makeText(AutoResponseActivity.this,
                                "Шаблоны успешно обновлены!", Toast.LENGTH_SHORT).show();
                        loadResponses();
                        setupCategorySpinner();
                    } else {
                        Toast.makeText(AutoResponseActivity.this,
                                "Обновление не требуется или произошла ошибка", Toast.LENGTH_SHORT).show();
                    }
                });
                // Закрываем временный DBHelper
                updateDbHelper.close();
            }
        });
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Настройки автоответов");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_auto_response_settings, null);
        EditText etJsonUrl = dialogView.findViewById(R.id.etJsonUrl);
        TextView tvLastUpdate = dialogView.findViewById(R.id.tvLastUpdate);
        TextView tvJsonVersion = dialogView.findViewById(R.id.tvJsonVersion);

        // Заполняем текущие значения
        etJsonUrl.setText(dbHelper.getJsonUrl());
        tvLastUpdate.setText("Последнее обновление: " + dbHelper.getLastUpdateTime());
        tvJsonVersion.setText("Версия JSON: " + dbHelper.getJsonVersion());

        builder.setView(dialogView);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String newUrl = etJsonUrl.getText().toString().trim();
            if (!newUrl.isEmpty() && !newUrl.equals(dbHelper.getJsonUrl())) {
                dbHelper.setJsonUrl(newUrl);
                Toast.makeText(this, "URL сохранен", Toast.LENGTH_SHORT).show();
                // Автоматически проверяем обновления после смены URL
                checkForUpdates();
            }
        });

        builder.setNegativeButton("Отмена", null);
        builder.setNeutralButton("Проверить обновления", (dialog, which) -> {
            checkForUpdates();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showAddResponseDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Добавить автоответ");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_auto_response, null);
        EditText etKeyword = dialogView.findViewById(R.id.etKeyword);
        EditText etResponse = dialogView.findViewById(R.id.etResponse);
        SwitchCompat switchActive = dialogView.findViewById(R.id.switchActive);
        Spinner categorySpinner = dialogView.findViewById(R.id.dialogCategorySpinner);

        // Настройка спиннера категорий для диалога
        List<String> dialogCategories = dbHelper.getCategories();
        if (!dialogCategories.contains("Общее")) {
            dialogCategories.add(0, "Общее");
        }

        ArrayAdapter<String> dialogSpinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, dialogCategories);
        dialogSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(dialogSpinnerAdapter);

        builder.setView(dialogView);

        builder.setPositiveButton("Добавить", (dialog, which) -> {
            String keyword = etKeyword.getText().toString().trim();
            String responseText = etResponse.getText().toString().trim();
            boolean isActive = switchActive.isChecked();
            String category = categorySpinner.getSelectedItem().toString();

            if (!keyword.isEmpty() && !responseText.isEmpty()) {
                AutoResponse autoResponse = new AutoResponse(keyword, responseText, isActive, category);
                long id = dbHelper.addAutoResponse(autoResponse);
                if (id != -1) {
                    loadResponses();
                    Toast.makeText(this, "Автоответ добавлен", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Ошибка при добавлении автоответа", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Отмена", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showEditResponseDialog(AutoResponse response) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Редактировать автоответ");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_auto_response, null);
        EditText etKeyword = dialogView.findViewById(R.id.etKeyword);
        EditText etResponse = dialogView.findViewById(R.id.etResponse);
        SwitchCompat switchActive = dialogView.findViewById(R.id.switchActive);
        Spinner categorySpinner = dialogView.findViewById(R.id.dialogCategorySpinner);

        etKeyword.setText(response.getKeyword());
        etResponse.setText(response.getResponse());
        switchActive.setChecked(response.isActive());

        // Настройка спиннера категорий
        List<String> dialogCategories = dbHelper.getCategories();
        if (!dialogCategories.contains(response.getCategory())) {
            dialogCategories.add(response.getCategory());
        }
        if (!dialogCategories.contains("Общее")) {
            dialogCategories.add(0, "Общее");
        }

        ArrayAdapter<String> dialogSpinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, dialogCategories);
        dialogSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(dialogSpinnerAdapter);

        // Устанавливаем текущую категорию
        int position = dialogCategories.indexOf(response.getCategory());
        if (position >= 0) {
            categorySpinner.setSelection(position);
        }

        builder.setView(dialogView);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String keyword = etKeyword.getText().toString().trim();
            String responseText = etResponse.getText().toString().trim();
            boolean isActive = switchActive.isChecked();
            String category = categorySpinner.getSelectedItem().toString();

            if (!keyword.isEmpty() && !responseText.isEmpty()) {
                response.setKeyword(keyword);
                response.setResponse(responseText);
                response.setActive(isActive);
                response.setCategory(category);

                int rowsAffected = dbHelper.updateAutoResponse(response);
                if (rowsAffected > 0) {
                    adapter.notifyItemChanged(responsesList.indexOf(response));
                    Toast.makeText(this, "Автоответ обновлен", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Ошибка при обновлении автоответа", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Отмена", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showDeleteConfirmationDialog(AutoResponse response) {
        new AlertDialog.Builder(this)
                .setTitle("Удаление автоответа")
                .setMessage("Вы уверены, что хотите удалить автоответ для ключевого слова \"" + response.getKeyword() + "\"?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    dbHelper.deleteAutoResponse(response.getId());
                    loadResponses();
                    Toast.makeText(this, "Автоответ удален", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showResetConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Сброс настроек")
                .setMessage("Вы уверены, что хотите сбросить все настройки к стандартным? Все пользовательские ответы будут удалены.")
                .setPositiveButton("Сбросить", (dialog, which) -> {
                    dbHelper.resetToDefault();
                    loadResponses();
                    setupCategorySpinner();
                    Toast.makeText(this, "Настройки сброшены к стандартным", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Обновляем список при возвращении на экран
        if (dbHelper != null) {
            loadResponses();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}