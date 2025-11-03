package ru.lisdevs.messenger.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import ru.lisdevs.messenger.model.Sticker;
import ru.lisdevs.messenger.model.StickerImage;
import ru.lisdevs.messenger.model.StickerPack;


import java.util.Random;
import java.util.Set;

public class StickerManager {
    private static final String TAG = "StickerManager";
    private static StickerManager instance;
    private Context context;
    private SharedPreferences preferences;

    // Ключи для SharedPreferences
    private static final String PREFS_NAME = "sticker_packs_prefs";
    private static final String PREF_ENABLED_PACKS = "enabled_sticker_packs";

    // Базовые стикеры (эмодзи) как fallback
    private List<Sticker> fallbackStickers;

    // Общее количество пакетов (300)
    private static final int TOTAL_PACKS = 300;

    public static StickerManager getInstance(Context context) {
        if (instance == null) {
            instance = new StickerManager(context);
        }
        return instance;
    }

    private StickerManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initFallbackStickers();
    }

    public List<StickerPack> getPurchasedStickerPacks() {
        List<StickerPack> packs = new ArrayList<>();

        // Добавляем пакеты со 2 по 300 (исключаем первый пакет)
        for (int i = 2; i <= TOTAL_PACKS; i++) {
            StickerPack pack = getStickerPack(i);
            if (pack != null) {
                packs.add(pack);
            }
        }

        return packs;
    }

    /**
     * Получает только включенные стикерпаки
     */
    public List<StickerPack> getEnabledStickerPacks(Context context) {
        List<StickerPack> enabledPacks = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        try {
            String enabledPacksJson = prefs.getString(PREF_ENABLED_PACKS, "{}");
            JSONObject jsonObject = new JSONObject(enabledPacksJson);

            // Начинаем с пакета 2 (исключаем первый пакет)
            for (int i = 2; i <= TOTAL_PACKS; i++) {
                StickerPack pack = getStickerPack(i);
                if (pack != null) {
                    // Проверяем сохраненное состояние
                    boolean isEnabled = jsonObject.optBoolean("pack_" + i, true);
                    pack.setEnabled(isEnabled);

                    if (isEnabled) {
                        enabledPacks.add(pack);
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error reading enabled packs", e);
            // В случае ошибки возвращаем все пакеты как включенные (без первого)
            return getPurchasedStickerPacks();
        }

        // Если нет включенных пакетов, добавляем базовый пакет (второй)
        if (enabledPacks.isEmpty()) {
            StickerPack basicPack = getStickerPack(2);
            if (basicPack != null) {
                basicPack.setEnabled(true);
                enabledPacks.add(basicPack);
                // Сохраняем состояние базового пакета как включенного
                saveStickerPackState(context, 2, true);
            }
        }

        return enabledPacks;
    }

    /**
     * Получает все стикерпаки с их текущим состоянием (включен/отключен)
     */
    public List<StickerPack> getAllStickerPacksWithState(Context context) {
        List<StickerPack> allPacks = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        try {
            String enabledPacksJson = prefs.getString(PREF_ENABLED_PACKS, "{}");
            JSONObject jsonObject = new JSONObject(enabledPacksJson);

            // Начинаем с пакета 2 (исключаем первый пакет)
            for (int i = 2; i <= TOTAL_PACKS; i++) {
                StickerPack pack = getStickerPack(i);
                if (pack != null) {
                    // Устанавливаем состояние пакета
                    boolean isEnabled = jsonObject.optBoolean("pack_" + i, true);
                    pack.setEnabled(isEnabled);
                    allPacks.add(pack);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error reading pack states", e);
            // В случае ошибки возвращаем все пакеты как включенные (без первого)
            List<StickerPack> purchasedPacks = getPurchasedStickerPacks();
            for (StickerPack pack : purchasedPacks) {
                pack.setEnabled(true);
            }
            return purchasedPacks;
        }

        return allPacks;
    }

    /**
     * Сохраняет состояние стикерпака
     */
    public void saveStickerPackState(Context context, int packId, boolean enabled) {
        // Игнорируем первый пакет
        if (packId == 1) {
            Log.d(TAG, "Ignoring save for pack 1 (disabled)");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        try {
            // Получаем текущий JSON
            String enabledPacksJson = prefs.getString(PREF_ENABLED_PACKS, "{}");
            JSONObject jsonObject = new JSONObject(enabledPacksJson);

            // Обновляем состояние пакета
            jsonObject.put("pack_" + packId, enabled);

            // Сохраняем обратно
            prefs.edit()
                    .putString(PREF_ENABLED_PACKS, jsonObject.toString())
                    .apply();

            Log.d(TAG, "Saved pack state: pack_" + packId + " = " + enabled);
        } catch (JSONException e) {
            Log.e(TAG, "Error saving pack state", e);
        }
    }

    /**
     * Сохраняет состояния всех пакетов
     */
    public void saveAllStickerPackStates(Context context, List<StickerPack> packs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        try {
            JSONObject jsonObject = new JSONObject();

            // Сохраняем состояния всех пакетов (игнорируем первый)
            for (StickerPack pack : packs) {
                if (pack != null && pack.getId() != 1) {
                    jsonObject.put("pack_" + pack.getId(), pack.isEnabled());
                }
            }

            // Сохраняем JSON
            prefs.edit()
                    .putString(PREF_ENABLED_PACKS, jsonObject.toString())
                    .apply();

            Log.d(TAG, "Saved all pack states, total: " + packs.size());
        } catch (JSONException e) {
            Log.e(TAG, "Error saving all pack states", e);
        }
    }

    /**
     * Получает список ID включенных пакетов
     */
    public Set<Integer> getEnabledPackIds(Context context) {
        Set<Integer> enabledPackIds = new HashSet<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        try {
            String enabledPacksJson = prefs.getString(PREF_ENABLED_PACKS, "{}");
            JSONObject jsonObject = new JSONObject(enabledPacksJson);

            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (jsonObject.getBoolean(key)) {
                    // Извлекаем ID из ключа "pack_1", "pack_2" и т.д.
                    if (key.startsWith("pack_")) {
                        try {
                            int packId = Integer.parseInt(key.substring(5));
                            // Игнорируем первый пакет
                            if (packId != 1) {
                                enabledPackIds.add(packId);
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Invalid pack ID format: " + key);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error reading enabled pack IDs", e);
        }

        Log.d(TAG, "Enabled pack IDs: " + enabledPackIds);
        return enabledPackIds;
    }

    /**
     * Проверяет, включен ли конкретный пакет
     */
    public boolean isStickerPackEnabled(Context context, int packId) {
        // Первый пакет всегда отключен
        if (packId == 1) {
            return false;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        try {
            String enabledPacksJson = prefs.getString(PREF_ENABLED_PACKS, "{}");
            JSONObject jsonObject = new JSONObject(enabledPacksJson);
            return jsonObject.optBoolean("pack_" + packId, true);
        } catch (JSONException e) {
            Log.e(TAG, "Error checking pack state", e);
            return true; // По умолчанию включен
        }
    }

    public List<Sticker> getAllPurchasedStickers() {
        List<Sticker> allStickers = new ArrayList<>();

        for (StickerPack pack : getPurchasedStickerPacks()) {
            if (pack != null && pack.getStickers() != null) {
                for (Sticker sticker : pack.getStickers()) {
                    // Очищаем URL стикеров при загрузке
                    if (sticker != null) {
                        cleanStickerUrls(sticker);
                        allStickers.add(sticker);
                    }
                }
            }
        }

        return allStickers;
    }

    public List<Sticker> getEnabledStickers(Context context) {
        List<Sticker> enabledStickers = new ArrayList<>();

        for (StickerPack pack : getEnabledStickerPacks(context)) {
            if (pack != null && pack.getStickers() != null) {
                for (Sticker sticker : pack.getStickers()) {
                    cleanStickerUrls(sticker);
                    enabledStickers.add(sticker);
                }
            }
        }

        return enabledStickers;
    }

    public StickerPack getStickerPack(int packId) {
        // Игнорируем первый пакет
        if (packId == 1) {
            return null;
        }

        if (packId < 2 || packId > TOTAL_PACKS) return null;

        StickerPack pack = new StickerPack();
        pack.setId(packId);
        pack.setTitle(getPackTitle(packId));
        pack.setStickers(createStickersForPack(packId));
        return pack;
    }

    private String getPackTitle(int packId) {
        String[] packTitles = {
                "Базовые эмодзи", // packId = 1 (игнорируется)
                "Кот Персик", "Еда и напитки", "Спорт", "Путешествия",
                "Технологии", "Музыка", "Искусство", "Наука", "Природа",
                "Праздники", "Хэллоуин", "Рождество", "Новый год", "День рождения",
                "Любовь", "Дружба", "Семья", "Работа", "Учеба",
                "Космос", "Фэнтези", "Супергерои", "Аниме", "Игры",
                "Мемы", "Интернет", "Соцсети", "Фильмы", "Сериалы",
                "Книги", "Комиксы", "Мультфильмы", "Дисней", "Марвел",
                "Звездные войны", "Гарри Поттер", "Властелин колец", "Игра престолов", "Стражи галактики",
                "Машины", "Мотоциклы", "Самолеты", "Корабли", "Поезда",
                "Города", "Страны", "Флаги", "Достопримечательности", "Культуры",
                // Продолжение до 100
                "Эмоции", "Жесты", "Лица", "Действия", "Предметы",
                "Одежда", "Обувь", "Аксессуары", "Украшения", "Красота",
                "Здоровье", "Медицина", "Фитнес", "Йога", "Танцы",
                "Пение", "Инструменты", "Оркестр", "Рок", "Поп",
                "Джаз", "Классика", "Рэп", "Электроника", "Диско",
                "Живопись", "Скульптура", "Фотография", "Архитектура", "Дизайн",
                "Мода", "Стиль", "Тренды", "Винтаж", "Авангард",
                "Математика", "Физика", "Химия", "Биология", "Астрономия",
                "Геология", "Метеорология", "Экология", "Ботаника", "Зоология",
                "Океан", "Горы", "Леса", "Пустыни", "Вулканы",
                // 101-150
                "Весна", "Лето", "Осень", "Зима", "Погода",
                "Дождь", "Снег", "Солнце", "Облака", "Радуга",
                "Утро", "День", "Вечер", "Ночь", "Время",
                "Часы", "Календарь", "Праздник", "Юбилей", "Свадьба",
                "Выпускной", "Карнавал", "Фестиваль", "Концерт", "Выставка",
                "Театр", "Кино", "Цирк", "Парк", "Пляж",
                "Горы", "Море", "Озеро", "Река", "Водопад",
                "Остров", "Пещера", "Каньон", "Джунгли", "Саванна",
                "Арктика", "Антарктика", "Тропики", "Субтропики", "Умеренный климат",
                "Континенты", "Океаны", "Моря", "Заливы", "Проливы",
                // 151-200
                "Планеты", "Звезды", "Галактики", "Созвездия", "Кометы",
                "Астероиды", "Черные дыры", "НЛО", "Инопланетяне", "Ракеты",
                "Спутники", "Телескопы", "Обсерватории", "Космонавты", "Луноходы",
                "Драконы", "Единороги", "Феи", "Волшебники", "Вампиры",
                "Оборотни", "Зомби", "Привидения", "Рыцари", "Замки",
                "Суперсилы", "Бэтмен", "Супермен", "Человек-паук", "Железный человек",
                "Халк", "Тор", "Капитан Америка", "Черная вдова", "Стражи",
                "Наруто", "Ван Пис", "Атака титанов", "Блич", "Ванпанчмен",
                "Марио", "Зельда", "Покемоны", "Майнкрафт", "ГТА",
                // 201-250
                "Дота", "КС", "Варкрафт", "Скайрим", "Фоллаут",
                "Смайлики", "Данк мемы", "Коты", "Собаки", "Жабры",
                "Пеппа", "Свинка Пеппа", "Маша и Медведь", "Фиксики", "Смешарики",
                "Губка Боб", "Том и Джерри", "Микки Маус", "Дональд Дак", "Гуфи",
                "Аладдин", "Король Лев", "Русалочка", "Красавица и Чудовище", "Холодное сердце",
                "Тачки", "История игрушек", "В поисках Немо", "Суперсемейка", "Головоломка",
                "Зверополис", "Моана", "Коко", "Райя", "Энканто",
                "Формула 1", "НАСКАР", "Ралли", "Мото ГП", "Драг рейсинг",
                "Яхты", "Парусники", "Круизные лайнеры", "Подлодки", "Авианосцы",
                // 251-300
                "Поезда", "Метро", "Трамваи", "Автобусы", "Такси",
                "Велосипеды", "Самокаты", "Скейты", "Ролики", "Гироскутеры",
                "Нью-Йорк", "Париж", "Лондон", "Токио", "Сидней",
                "Рим", "Венеция", "Барселона", "Амстердам", "Прага",
                "Дубай", "Сингапур", "Гонконг", "Шанхай", "Сеул",
                "Каир", "Кейптаун", "Найроби", "Лагос", "Аккра",
                "Россия", "США", "Китай", "Япония", "Германия",
                "Франция", "Великобритания", "Италия", "Испания", "Канада",
                "Эйфелева башня", "Колизей", "Пирамиды", "Статуя Свободы", "Биг Бен",
                "Тадж-Махал", "Великая Китайская стена", "Сиднейская опера", "Петра", "Мачу-Пикчу",
                "Японская", "Китайская", "Индийская", "Мексиканская", "Итальянская"
        };

        // Смещаем индекс на 1, так как первый пакет игнорируется
        if (packId >= 1 && packId <= packTitles.length) {
            return packTitles[packId - 1];
        }
        return "Стикерпак " + packId;
    }

    private List<Sticker> createStickersForPack(int packId) {
        List<Sticker> stickers = new ArrayList<>();
        String[] stickerNames = getStickerNamesForPack(packId);
        int startNumber = (packId - 1) * 50 + 1; // Каждый пакет имеет 50 стикеров

        for (int i = 0; i < stickerNames.length; i++) {
            Sticker sticker = new Sticker();
            sticker.setId(startNumber + i);
            sticker.setName(stickerNames[i]);

            // Все пакеты используют URL (первый пакет исключен)
            sticker.setImageUrl("https://vk.com/sticker/1-" + (startNumber + i) + "-256b");
            sticker.setWidth(128);
            sticker.setHeight(128);

            List<StickerImage> images = new ArrayList<>();
            StickerImage image = new StickerImage("https://vk.com/sticker/1-" + (startNumber + i) + "-256b", 128, 128);
            images.add(image);
            sticker.setImages(images);

            stickers.add(sticker);
        }

        return stickers;
    }

    private String[] getStickerNamesForPack(int packId) {
        // Для первых 50 пакетов используем конкретные названия
        if (packId >= 2 && packId <= 50) {
            switch (packId) {
                case 2: return new String[]{
                        "Котик", "Собачка", "Медвежонок", "Лисичка", "Зайчик", "Енот", "Панда", "Тигр", "Лев", "Слон",
                        "Жираф", "Обезьяна", "Пингвин", "Сова", "Ежик", "Белка", "Хомяк", "Черепаха", "Лягушка", "Дельфин",
                        "Кит", "Акула", "Осьминог", "Медуза", "Бабочка", "Пчелка", "Божья коровка", "Гусеница", "Улитка", "Паучок",
                        "Кролик", "Волк", "Норка", "Выдра", "Барсук", "Лось", "Олень", "Кабан", "Кенгуру", "Коала",
                        "Попугай", "Воробей", "Голубь", "Ворона", "Сорока", "Сокол", "Орел", "Лебедь", "Фламинго", "Пеликан"
                };
                case 3: return new String[]{
                        "Пицца", "Бургер", "Суши", "Мороженое", "Торт", "Кофе", "Чай", "Сок", "Коктейль", "Попкорн",
                        "Шоколад", "Конфеты", "Пончик", "Блины", "Сыр", "Хлеб", "Фрукты", "Овощи", "Салат", "Суп",
                        "Паста", "Пицца", "Стейк", "Рыба", "Курица", "Яйца", "Молоко", "Йогурт", "Смузи", "Лимонад",
                        "Пирог", "Печенье", "Кекс", "Вафли", "Мед", "Джем", "Соус", "Специи", "Соль", "Перец",
                        "Вино", "Пиво", "Шампанское", "Коктейль", "Содовая", "Энергетик", "Вода", "Сок", "Молоко", "Кофе"
                };
                case 4: return new String[]{
                        "Футбол", "Баскетбол", "Волейбол", "Теннис", "Бейсбол", "Хоккей", "Гольф", "Бокс", "Борьба", "Плавание",
                        "Бег", "Прыжки", "Метание", "Велоспорт", "Автоспорт", "Мотоспорт", "Серфинг", "Сноуборд", "Лыжи", "Коньки",
                        "Гимнастика", "Йога", "Фитнес", "Тяжелая атлетика", "Пауэрлифтинг", "Кроссфит", "Скалолазание", "Парашют", "Дайвинг", "Альпинизм",
                        "Фехтование", "Стрельба", "Стрельба из лука", "Дартс", "Бильярд", "Шахматы", "Шашки", "Покер", "Блэкджек", "Рулетка",
                        "Бадминтон", "Сквош", "Регби", "Крикет", "Хоккей на траве", "Керлинг", "Боулинг", "Дартс", "Гандбол", "Водное поло"
                };
                case 5: return new String[]{
                        "Футбол", "Баскетбол", "Волейбол", "Теннис", "Бейсбол", "Хоккей", "Гольф", "Бокс", "Борьба", "Плавание",
                        "Бег", "Прыжки", "Метание", "Велоспорт", "Автоспорт", "Мотоспорт", "Серфинг", "Сноуборд", "Лыжи", "Коньки",
                        "Гимнастика", "Йога", "Фитнес", "Тяжелая атлетика", "Пауэрлифтинг", "Кроссфит", "Скалолазание", "Парашют", "Дайвинг", "Альпинизм",
                        "Фехтование", "Стрельба", "Стрельба из лука", "Дартс", "Бильярд", "Шахматы", "Шашки", "Покер", "Блэкджек", "Рулетка",
                        "Бадминтон", "Сквош", "Регби", "Крикет", "Хоккей на траве", "Керлинг", "Боулинг", "Дартс", "Гандбол", "Водное поло"
                };
                // Добавьте остальные конкретные пакеты по аналогии...
                default: return generateStickerNamesForCategory(packId);
            }
        } else {
            // Для пакетов с 51 по 300 генерируем названия на основе категории
            return generateStickerNamesForCategory(packId);
        }
    }

    private String[] generateStickerNamesForCategory(int packId) {
        String[] stickerNames = new String[50];
        String category = getPackTitle(packId);

        for (int i = 0; i < 50; i++) {
            stickerNames[i] = category + " " + (i + 1);
        }
        return stickerNames;
    }

    private String[] generateDefaultStickers(int packId) {
        String[] defaultStickers = new String[50];
        for (int i = 0; i < 50; i++) {
            defaultStickers[i] = "Стикер " + (i + 1) + " пакета " + packId;
        }
        return defaultStickers;
    }

    private void cleanStickerUrls(Sticker sticker) {
        // Очищаем основной imageUrl
        if (sticker.getImageUrl() != null && sticker.getImageUrl().contains("?size=")) {
            String cleanUrl = sticker.getImageUrl().substring(0, sticker.getImageUrl().indexOf("?size="));
            sticker.setImageUrl(cleanUrl);
        }

        // Очищаем URLs в images
        if (sticker.getImages() != null) {
            for (StickerImage image : sticker.getImages()) {
                if (image != null && image.getUrl() != null && image.getUrl().contains("?size=")) {
                    String cleanUrl = image.getUrl().substring(0, image.getUrl().indexOf("?size="));
                    image.setUrl(cleanUrl);
                }
            }
        }
    }

    private void initFallbackStickers() {
        fallbackStickers = new ArrayList<>();

        // Создаем простые эмодзи как fallback
        String[] emojiStickers = {"😊", "😂", "❤️", "👍", "😍", "🔥", "🎉", "🙏", "😢", "😡"};

        for (int i = 0; i < emojiStickers.length; i++) {
            Sticker sticker = new Sticker();
            sticker.setId(i + 1000);
            sticker.setName("Emoji " + emojiStickers[i]);
            sticker.setImageUrl(""); // Пустой URL - будем использовать текст
            sticker.setWidth(128);
            sticker.setHeight(128);

            // Создаем пустой список images
            sticker.setImages(new ArrayList<>());

            fallbackStickers.add(sticker);
        }
    }

    public List<Sticker> getFallbackStickers() {
        return fallbackStickers;
    }

    public boolean areAllStickersBroken(List<Sticker> stickers) {
        if (stickers == null || stickers.isEmpty()) return true;

        // Простая проверка - если у большинства стикеров URL содержат "size=128", вероятно они сломаны
        int brokenCount = 0;
        for (Sticker sticker : stickers) {
            if (sticker.getImageUrl() != null &&
                    (sticker.getImageUrl().contains("?size=128") ||
                            sticker.getImageUrl().contains("userapi.com"))) {
                brokenCount++;
            }
        }
        return brokenCount > stickers.size() / 2;
    }

    // Новый метод для получения количества пакетов (без первого)
    public int getStickerPackCount() {
        return TOTAL_PACKS - 1; // 300 - 1 исключенный пакет
    }

    // Новый метод для получения пакета по индексу
    public StickerPack getStickerPackByIndex(int index) {
        if (index < 0 || index >= getStickerPackCount()) return null;
        // Смещаем индекс на +2, так как первый пакет (id=1) исключен
        return getStickerPack(index + 2);
    }

    // Метод для получения общего количества стикеров
    public int getTotalStickerCount() {
        return getStickerPackCount() * 50; // 299 пакетов * 50 стикеров
    }

    // Метод для проверки, существует ли пакет
    public boolean isPackExists(int packId) {
        return packId >= 2 && packId <= TOTAL_PACKS;
    }

    // Метод для получения случайного стикера из включенных пакетов
    public Sticker getRandomSticker(Context context) {
        List<Sticker> enabledStickers = getEnabledStickers(context);
        if (enabledStickers.isEmpty()) {
            // Если нет включенных стикеров, возвращаем случайный fallback
            if (!fallbackStickers.isEmpty()) {
                return fallbackStickers.get(new Random().nextInt(fallbackStickers.size()));
            }
            return null;
        }
        return enabledStickers.get(new Random().nextInt(enabledStickers.size()));
    }
}