package ru.lisdevs.messenger.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import ru.lisdevs.messenger.model.AutoResponse;

import android.content.ContentValues;
import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AutoResponseDBHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "auto_responses.db";
    private static final int DATABASE_VERSION = 3;

    public static final String TABLE_RESPONSES = "auto_responses";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_KEYWORD = "keyword";
    public static final String COLUMN_RESPONSE = "response";
    public static final String COLUMN_IS_ACTIVE = "is_active";
    public static final String COLUMN_CATEGORY = "category";
    public static final String COLUMN_IS_PREDEFINED = "is_predefined";

    private static final String TABLE_CONFIG = "app_config";
    private static final String COLUMN_CONFIG_KEY = "config_key";
    private static final String COLUMN_CONFIG_VALUE = "config_value";

    private static final String TABLE_CREATE_RESPONSES =
            "CREATE TABLE " + TABLE_RESPONSES + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_KEYWORD + " TEXT NOT NULL, " +
                    COLUMN_RESPONSE + " TEXT NOT NULL, " +
                    COLUMN_IS_ACTIVE + " INTEGER DEFAULT 1, " +
                    COLUMN_CATEGORY + " TEXT DEFAULT 'Общее', " +
                    COLUMN_IS_PREDEFINED + " INTEGER DEFAULT 0);";

    private static final String TABLE_CREATE_CONFIG =
            "CREATE TABLE " + TABLE_CONFIG + " (" +
                    COLUMN_CONFIG_KEY + " TEXT PRIMARY KEY, " +
                    COLUMN_CONFIG_VALUE + " TEXT);";

    // Используем synchronized для предотвращения race conditions
    private static final Object lock = new Object();

    public AutoResponseDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        // Отключаем WAL mode чтобы избежать проблем с блокировками
        setWriteAheadLoggingEnabled(false);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            db.execSQL(TABLE_CREATE_RESPONSES);
            db.execSQL(TABLE_CREATE_CONFIG);

            // Сохраняем настройки по умолчанию
            saveConfig(db, "json_url", "https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/auto_responses.json");
            saveConfig(db, "last_update", "0");
            saveConfig(db, "json_version", "0");

            // Добавляем базовые шаблоны на случай если JSON недоступен
            insertDefaultTemplates(db);
        } catch (Exception e) {
            Log.e("AutoResponseDBHelper", "Error in onCreate: " + e.getMessage());
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE " + TABLE_RESPONSES + " ADD COLUMN " + COLUMN_CATEGORY + " TEXT DEFAULT 'Общее'");
                db.execSQL("ALTER TABLE " + TABLE_RESPONSES + " ADD COLUMN " + COLUMN_IS_PREDEFINED + " INTEGER DEFAULT 0");
            }
            if (oldVersion < 3) {
                db.execSQL(TABLE_CREATE_CONFIG);
                saveConfig(db, "json_url", "https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/auto_responses.json");
                saveConfig(db, "last_update", "0");
                saveConfig(db, "json_version", "0");
            }
        } catch (Exception e) {
            Log.e("AutoResponseDBHelper", "Error in onUpgrade: " + e.getMessage());
        }
    }

    private void insertDefaultTemplates(SQLiteDatabase db) {
        List<AutoResponse> defaultResponses = getDefaultResponses();

        for (AutoResponse response : defaultResponses) {
            try {
                ContentValues values = new ContentValues();
                values.put(COLUMN_KEYWORD, response.getKeyword());
                values.put(COLUMN_RESPONSE, response.getResponse());
                values.put(COLUMN_IS_ACTIVE, response.isActive() ? 1 : 0);
                values.put(COLUMN_CATEGORY, response.getCategory());
                values.put(COLUMN_IS_PREDEFINED, 1);

                db.insert(TABLE_RESPONSES, null, values);
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error inserting default template: " + e.getMessage());
            }
        }
    }

    private List<AutoResponse> getDefaultResponses() {
        List<AutoResponse> responses = new ArrayList<>();

        //* Приветствия
        responses.add(new AutoResponse("привет", "Привет! 😊 Рад тебя видеть!", true, "Приветствия"));
        responses.add(new AutoResponse("здравствуйте", "Здравствуйте! Чем могу помочь?", true, "Приветствия"));
        responses.add(new AutoResponse("добрый день", "Добрый день! Как ваши дела?", true, "Приветствия"));
        responses.add(new AutoResponse("хай", "Хай! 👋 Как настроение?", true, "Приветствия"));

        // Общение
        responses.add(new AutoResponse("как дела", "Всё отлично, спасибо! А у вас как?", true, "Общение"));
        responses.add(new AutoResponse("как ты", "Всё хорошо, работаю как часы! ⚡", true, "Общение"));
        responses.add(new AutoResponse("че как", "Нормально! Чем занимаешься?", true, "Общение"));

        // Помощь
        responses.add(new AutoResponse("помощь", "Я здесь чтобы помочь! Задайте ваш вопрос 📝", true, "Помощь"));
        responses.add(new AutoResponse("help", "I'm here to help! Ask me anything 📝", true, "Помощь"));
        responses.add(new AutoResponse("что ты можешь", "Могу отвечать на вопросы, поддерживать беседу и помогать с информацией!", true, "Помощь"));

        // Благодарности
        responses.add(new AutoResponse("спасибо", "Пожалуйста! Всегда рад помочь! 😊", true, "Благодарности"));
        responses.add(new AutoResponse("благодарю", "И вам спасибо за обращение! 🙏", true, "Благодарности"));
        responses.add(new AutoResponse("thanks", "You're welcome! 😊", true, "Благодарности"));

        // Прощания
        //responses.add(new AutoResponse("пока", "До свидания! Хорошего дня! 👋", true, "Прощания"));
        responses.add(new AutoResponse("до свидания", "До свидания! Буду рад пообщаться снова!", true, "Прощания"));
        responses.add(new AutoResponse("goodbye", "Goodbye! Have a nice day! 👋", true, "Прощания"));

        return responses;
    }

    // Метод для загрузки шаблонов из JSON
    public boolean loadTemplatesFromJson(String jsonUrl, Context context) {
        synchronized (lock) {
            SQLiteDatabase db = null;
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(jsonUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Android; AutoResponse App)")
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String jsonString = response.body().string();
                    Log.d("AutoResponseDBHelper", "JSON loaded successfully: " + jsonString.length() + " chars");

                    db = getWritableDatabase();
                    return parseAndSaveJsonTemplates(db, jsonString, context);
                } else {
                    Log.e("AutoResponseDBHelper", "HTTP error: " + response.code());
                }
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error loading JSON templates: " + e.getMessage());
            } finally {
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing database: " + e.getMessage());
                    }
                }
            }
            return false;
        }
    }

    private boolean parseAndSaveJsonTemplates(SQLiteDatabase db, String jsonString, Context context) {
        synchronized (lock) {
            try {
                JSONObject json = new JSONObject(jsonString);
                int jsonVersion = json.optInt("version", 1);
                String lastUpdated = json.optString("last_updated", "");

                // Проверяем, нужно ли обновлять
                int currentVersion = Integer.parseInt(getConfig(db, "json_version", "0"));
                if (jsonVersion <= currentVersion) {
                    Log.d("AutoResponseDBHelper", "JSON version not changed: " + jsonVersion);
                    return false; // Версия не изменилась
                }

                JSONArray categories = json.getJSONArray("categories");

                try {
                    db.beginTransaction();

                    // Удаляем старые предустановленные ответы
                    db.delete(TABLE_RESPONSES, COLUMN_IS_PREDEFINED + " = ?", new String[]{"1"});

                    // Добавляем новые из JSON
                    int totalAdded = 0;
                    for (int i = 0; i < categories.length(); i++) {
                        JSONObject category = categories.getJSONObject(i);
                        String categoryName = category.getString("name");
                        JSONArray responses = category.getJSONArray("responses");

                        for (int j = 0; j < responses.length(); j++) {
                            JSONObject response = responses.getJSONObject(j);
                            String keyword = response.getString("keyword");
                            String responseText = response.getString("response");
                            boolean active = response.optBoolean("active", true);

                            ContentValues values = new ContentValues();
                            values.put(COLUMN_KEYWORD, keyword);
                            values.put(COLUMN_RESPONSE, responseText);
                            values.put(COLUMN_IS_ACTIVE, active ? 1 : 0);
                            values.put(COLUMN_CATEGORY, categoryName);
                            values.put(COLUMN_IS_PREDEFINED, 1);

                            db.insert(TABLE_RESPONSES, null, values);
                            totalAdded++;
                        }
                    }

                    db.setTransactionSuccessful();

                    // Сохраняем новую версию
                    saveConfig(db, "json_version", String.valueOf(jsonVersion));
                    saveConfig(db, "last_update", String.valueOf(System.currentTimeMillis()));

                    Log.d("AutoResponseDBHelper", "Successfully loaded " + totalAdded + " templates from JSON");
                    return true;

                } finally {
                    try {
                        db.endTransaction();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error ending transaction: " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error parsing JSON templates: " + e.getMessage());
                return false;
            }
        }
    }

    // Методы для работы с конфигурацией
    private void saveConfig(SQLiteDatabase db, String key, String value) {
        synchronized (lock) {
            try {
                ContentValues values = new ContentValues();
                values.put(COLUMN_CONFIG_KEY, key);
                values.put(COLUMN_CONFIG_VALUE, value);
                db.insertWithOnConflict(TABLE_CONFIG, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error saving config: " + e.getMessage());
            }
        }
    }

    private void saveConfig(String key, String value) {
        synchronized (lock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                saveConfig(db, key, value);
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error saving config: " + e.getMessage());
            } finally {
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing database: " + e.getMessage());
                    }
                }
            }
        }
    }

    private String getConfig(SQLiteDatabase db, String key, String defaultValue) {
        synchronized (lock) {
            Cursor cursor = null;
            try {
                cursor = db.query(TABLE_CONFIG,
                        new String[]{COLUMN_CONFIG_VALUE},
                        COLUMN_CONFIG_KEY + " = ?",
                        new String[]{key}, null, null, null);

                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONFIG_VALUE));
                }
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error getting config: " + e.getMessage());
            } finally {
                if (cursor != null) {
                    try {
                        cursor.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing cursor: " + e.getMessage());
                    }
                }
            }
            return defaultValue;
        }
    }

    public String getConfig(String key, String defaultValue) {
        synchronized (lock) {
            SQLiteDatabase db = null;
            try {
                db = getReadableDatabase();
                return getConfig(db, key, defaultValue);
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error getting config: " + e.getMessage());
                return defaultValue;
            } finally {
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing database: " + e.getMessage());
                    }
                }
            }
        }
    }

    public String getJsonUrl() {
        return getConfig("json_url", "https://raw.githubusercontent.com/sidenevkirill/Sidenevkirill.github.io/refs/heads/master/auto_responses.json");
    }

    public void setJsonUrl(String url) {
        saveConfig("json_url", url);
    }

    public String getLastUpdateTime() {
        long timestamp = Long.parseLong(getConfig("last_update", "0"));
        if (timestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
        return "Никогда";
    }

    public int getJsonVersion() {
        return Integer.parseInt(getConfig("json_version", "0"));
    }

    // Автоматическая проверка обновлений
    public void checkForUpdates(Context context, UpdateCallback callback) {
        new Thread(() -> {
            boolean success = loadTemplatesFromJson(getJsonUrl(), context);
            if (callback != null) {
                callback.onUpdateComplete(success);
            }
        }).start();
    }

    public interface UpdateCallback {
        void onUpdateComplete(boolean success);
    }

    // Основные методы работы с автоответами
    public long addAutoResponse(AutoResponse response) {
        synchronized (lock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(COLUMN_KEYWORD, response.getKeyword());
                values.put(COLUMN_RESPONSE, response.getResponse());
                values.put(COLUMN_IS_ACTIVE, response.isActive() ? 1 : 0);
                values.put(COLUMN_CATEGORY, response.getCategory());
                values.put(COLUMN_IS_PREDEFINED, 0); // Пользовательские ответы

                return db.insert(TABLE_RESPONSES, null, values);
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error adding auto response: " + e.getMessage());
                return -1;
            } finally {
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing database: " + e.getMessage());
                    }
                }
            }
        }
    }

    public List<AutoResponse> getAllAutoResponses() {
        synchronized (lock) {
            SQLiteDatabase db = null;
            Cursor cursor = null;
            List<AutoResponse> responses = new ArrayList<>();

            try {
                db = getReadableDatabase();
                cursor = db.query(TABLE_RESPONSES,
                        null, null, null, null, null, COLUMN_CATEGORY + ", " + COLUMN_ID + " DESC");

                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        AutoResponse response = createAutoResponseFromCursor(cursor);
                        responses.add(response);
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error getting all responses: " + e.getMessage());
            } finally {
                if (cursor != null) {
                    try {
                        cursor.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing cursor: " + e.getMessage());
                    }
                }
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing database: " + e.getMessage());
                    }
                }
            }
            return responses;
        }
    }

    public List<AutoResponse> getActiveAutoResponses() {
        synchronized (lock) {
            SQLiteDatabase db = null;
            Cursor cursor = null;
            List<AutoResponse> responses = new ArrayList<>();

            try {
                db = getReadableDatabase();
                String selection = COLUMN_IS_ACTIVE + " = ?";
                String[] selectionArgs = {"1"};

                cursor = db.query(TABLE_RESPONSES,
                        null, selection, selectionArgs, null, null, COLUMN_CATEGORY + ", " + COLUMN_ID + " DESC");

                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        AutoResponse response = createAutoResponseFromCursor(cursor);
                        responses.add(response);
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error getting active responses: " + e.getMessage());
            } finally {
                if (cursor != null) {
                    try {
                        cursor.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing cursor: " + e.getMessage());
                    }
                }
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing database: " + e.getMessage());
                    }
                }
            }
            return responses;
        }
    }

    public List<String> getCategories() {
        synchronized (lock) {
            SQLiteDatabase db = null;
            Cursor cursor = null;
            List<String> categories = new ArrayList<>();

            try {
                db = getReadableDatabase();
                cursor = db.query(true, TABLE_RESPONSES,
                        new String[]{COLUMN_CATEGORY},
                        null, null, null, null,
                        COLUMN_CATEGORY + " ASC", null);

                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String category = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY));
                        if (!categories.contains(category)) {
                            categories.add(category);
                        }
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error getting categories: " + e.getMessage());
            } finally {
                if (cursor != null) {
                    try {
                        cursor.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing cursor: " + e.getMessage());
                    }
                }
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing database: " + e.getMessage());
                    }
                }
            }
            return categories;
        }
    }

    public List<AutoResponse> getResponsesByCategory(String category) {
        synchronized (lock) {
            SQLiteDatabase db = null;
            Cursor cursor = null;
            List<AutoResponse> responses = new ArrayList<>();

            try {
                db = getReadableDatabase();
                String selection = COLUMN_CATEGORY + " = ?";
                String[] selectionArgs = {category};

                cursor = db.query(TABLE_RESPONSES,
                        null, selection, selectionArgs, null, null, COLUMN_ID + " DESC");

                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        AutoResponse response = createAutoResponseFromCursor(cursor);
                        responses.add(response);
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error getting responses by category: " + e.getMessage());
            } finally {
                if (cursor != null) {
                    try {
                        cursor.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing cursor: " + e.getMessage());
                    }
                }
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing database: " + e.getMessage());
                    }
                }
            }
            return responses;
        }
    }

    public int updateAutoResponse(AutoResponse response) {
        synchronized (lock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(COLUMN_KEYWORD, response.getKeyword());
                values.put(COLUMN_RESPONSE, response.getResponse());
                values.put(COLUMN_IS_ACTIVE, response.isActive() ? 1 : 0);
                values.put(COLUMN_CATEGORY, response.getCategory());

                int rowsAffected = db.update(TABLE_RESPONSES, values,
                        COLUMN_ID + " = ?",
                        new String[]{String.valueOf(response.getId())});
                return rowsAffected;
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error updating auto response: " + e.getMessage());
                return 0;
            } finally {
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing database: " + e.getMessage());
                    }
                }
            }
        }
    }

    public void deleteAutoResponse(int id) {
        synchronized (lock) {
            SQLiteDatabase db = null;
            Cursor cursor = null;

            try {
                db = getWritableDatabase();

                // Проверяем, не является ли ответ предустановленным
                cursor = db.query(TABLE_RESPONSES,
                        new String[]{COLUMN_IS_PREDEFINED},
                        COLUMN_ID + " = ?",
                        new String[]{String.valueOf(id)}, null, null, null);

                boolean isPredefined = false;
                if (cursor != null && cursor.moveToFirst()) {
                    isPredefined = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_PREDEFINED)) == 1;
                }

                // Удаляем только пользовательские ответы
                if (!isPredefined) {
                    db.delete(TABLE_RESPONSES, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
                }
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error deleting auto response: " + e.getMessage());
            } finally {
                if (cursor != null) {
                    try {
                        cursor.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing cursor: " + e.getMessage());
                    }
                }
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing database: " + e.getMessage());
                    }
                }
            }
        }
    }

    public void resetToDefault() {
        synchronized (lock) {
            SQLiteDatabase db = null;
            try {
                db = getWritableDatabase();

                // Удаляем все пользовательские ответы
                db.delete(TABLE_RESPONSES, COLUMN_IS_PREDEFINED + " = ?", new String[]{"0"});

                // Сбрасываем все предустановленные ответы в активное состояние
                ContentValues values = new ContentValues();
                values.put(COLUMN_IS_ACTIVE, 1);
                db.update(TABLE_RESPONSES, values, COLUMN_IS_PREDEFINED + " = ?", new String[]{"1"});

            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error resetting to default: " + e.getMessage());
            } finally {
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        Log.e("AutoResponseDBHelper", "Error closing database: " + e.getMessage());
                    }
                }
            }
        }
    }

    public String findMatchingResponse(String message) {
        synchronized (lock) {
            if (message == null || message.isEmpty()) return null;

            List<AutoResponse> activeResponses = getActiveAutoResponses();
            String lowerMessage = message.toLowerCase();

            for (AutoResponse response : activeResponses) {
                if (lowerMessage.contains(response.getKeyword().toLowerCase())) {
                    return response.getResponse();
                }
            }
            return null;
        }
    }

    private AutoResponse createAutoResponseFromCursor(Cursor cursor) {
        AutoResponse response = new AutoResponse();
        response.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)));
        response.setKeyword(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_KEYWORD)));
        response.setResponse(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RESPONSE)));
        response.setActive(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_ACTIVE)) == 1);
        response.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)));
        response.setPredefined(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_PREDEFINED)) == 1);
        return response;
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // Отключаем WAL mode для избежания проблем с блокировками
        try {
            db.disableWriteAheadLogging();
        } catch (Exception e) {
            Log.e("AutoResponseDBHelper", "Error disabling WAL: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                super.close();
            } catch (Exception e) {
                Log.e("AutoResponseDBHelper", "Error closing database helper: " + e.getMessage());
            }
        }
    }
}