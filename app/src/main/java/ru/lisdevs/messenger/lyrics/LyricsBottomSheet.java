package ru.lisdevs.messenger.lyrics;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.api.Authorizer;
import ru.lisdevs.messenger.utils.TokenManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;

import java.util.Random;

public class LyricsBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_LYRICS_ID = "lyrics_id";
    private static final String ARG_TRACK_TITLE = "track_title";
    private static final String ARG_TRACK_ARTIST = "track_artist";
    private static final String TAG = "LyricsBottomSheet";

    private TextView lyricsTextView;
    private ProgressBar progressBar;
    private TextView titleTextView;
    private Button closeButton;
    private OkHttpClient client;

    public static LyricsBottomSheet newInstance(int lyricsId, String title, String artist) {
        LyricsBottomSheet fragment = new LyricsBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_LYRICS_ID, lyricsId);
        args.putString(ARG_TRACK_TITLE, title);
        args.putString(ARG_TRACK_ARTIST, artist);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_lyrics, container, false);

        client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();

        lyricsTextView = view.findViewById(R.id.tvLyrics);
        progressBar = view.findViewById(R.id.progressBar);
        titleTextView = view.findViewById(R.id.track_title);
        closeButton = view.findViewById(R.id.btnClose);

        return view;
    }

    @SuppressLint("StringFormatMatches")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            int lyricsId = args.getInt(ARG_LYRICS_ID);
            String title = args.getString(ARG_TRACK_TITLE);
            String artist = args.getString(ARG_TRACK_ARTIST);

            titleTextView.setText(getString(R.string.track_info_format, artist, title));
            loadLyrics(lyricsId);
        }

        closeButton.setOnClickListener(v -> dismiss());
    }

    private void loadLyrics(int lyricsId) {
        Log.d(TAG, "Loading lyrics for ID: " + lyricsId);

        if (lyricsId == 0) {
            lyricsTextView.setText("Текст песни недоступен");
            progressBar.setVisibility(View.GONE);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        lyricsTextView.setText("Загрузка текста...");

        tryApiRequest(lyricsId);
    }

    private void tryApiRequest(int lyricsId) {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null || accessToken.isEmpty()) {
            Log.d(TAG, "No access token, trying web directly");
            parseLyricsFromWeb(lyricsId);
            return;
        }

        String apiUrl = "https://api.vk.com/method/audio.getLyrics" +
                "?access_token=" + accessToken +
                "&lyrics_id=" + lyricsId +
                "&v=5.199";

        Request apiRequest = new Request.Builder()
                .url(apiUrl)
                .header("User-Agent", Authorizer.getKateUserAgent())
                .header("Accept", "application/json; charset=utf-8")
                .build();

        client.newCall(apiRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "API request failed: " + e.getMessage());
                parseLyricsFromWeb(lyricsId);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    Log.d(TAG, "API response code: " + response.code());

                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            String lyricsText = responseObj.optString("text", "");

                            requireActivity().runOnUiThread(() -> {
                                if (!lyricsText.isEmpty()) {
                                    displayLyrics(lyricsText);
                                } else {
                                    parseLyricsFromWeb(lyricsId);
                                }
                            });
                        } else {
                            parseLyricsFromWeb(lyricsId);
                        }
                    } else {
                        Log.d(TAG, "API returned error: " + response.code());
                        parseLyricsFromWeb(lyricsId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "API parsing error: " + e.getMessage());
                    parseLyricsFromWeb(lyricsId);
                }
            }
        });
    }

    private void parseLyricsFromWeb(int lyricsId) {
        Log.d(TAG, "Starting web parsing for lyrics ID: " + lyricsId);

        // Добавляем случайную задержку перед запросом
        try {
            Thread.sleep(1000 + new Random().nextInt(2000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String[] urlVariants = {
                "https://m.vk.com/audio?act=lyrics&lyrics_id=" + lyricsId,
                "https://vk.com/audio?act=lyrics&lyrics_id=" + lyricsId
        };

        for (int i = 0; i < urlVariants.length; i++) {
            final int attempt = i;
            Request webRequest = createWebRequest(urlVariants[i]);

            client.newCall(webRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Web request " + attempt + " failed: " + e.getMessage());
                    if (attempt == urlVariants.length - 1) {
                        showError("Ошибка сети");
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        Log.d(TAG, "Web response " + attempt + ": " + response.code());

                        if (response.isSuccessful()) {
                            String html = response.body().string();
                            Log.d(TAG, "HTML received, length: " + html.length());

                            // Проверяем, не содержит ли HTML сообщение об устаревшем браузере
                            if (html.contains("Устаревший браузер") ||
                                    html.contains("outdated_browser") ||
                                    html.contains("Браузер устарел")) {
                                Log.d(TAG, "Browser outdated message detected");
                                if (attempt == urlVariants.length - 1) {
                                    showError("Обновите браузер для отображения текста");
                                }
                                return;
                            }

                            String lyrics = extractLyricsFromHtml(html);

                            if (lyrics != null && !lyrics.isEmpty()) {
                                requireActivity().runOnUiThread(() -> displayLyrics(lyrics));
                                return;
                            }
                        }

                        if (attempt == urlVariants.length - 1) {
                            showError("Текст не найден");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Web response error: " + e.getMessage());
                        if (attempt == urlVariants.length - 1) {
                            showError("Ошибка обработки");
                        }
                    }
                }
            });
        }
    }

    private Request createWebRequest(String url) {
        // Случайный выбор User-Agent для избежания блокировки
        String[] userAgents = {
                "Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
                "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
                "Mozilla/5.0 (Linux; Android 10; VOG-L29) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
        };

        Random random = new Random();
        String userAgent = userAgents[random.nextInt(userAgents.length)];

        return new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Cache-Control", "max-age=0")
                .header("Referer", "https://vk.com/")
                .header("Origin", "https://vk.com")
                .build();
    }

    private String extractLyricsFromHtml(String html) {
        try {
            Log.d(TAG, "Trying to extract lyrics from HTML");

            // 1. Попробуем найти JSON с текстом песни
            Pattern jsonPattern = Pattern.compile("var\\s+lyricsData\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL);
            Matcher jsonMatcher = jsonPattern.matcher(html);

            if (jsonMatcher.find()) {
                String jsonStr = jsonMatcher.group(1);
                try {
                    JSONObject json = new JSONObject(jsonStr);
                    if (json.has("text")) {
                        String text = json.getString("text");
                        Log.d(TAG, "Found lyrics in lyricsData JSON");
                        return cleanAndFormatLyrics(text);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error: " + e.getMessage());
                }
            }

            // 2. Попробуем найти в window.lyricsData
            Pattern windowPattern = Pattern.compile("window\\.lyricsData\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL);
            Matcher windowMatcher = windowPattern.matcher(html);

            if (windowMatcher.find()) {
                String jsonStr = windowMatcher.group(1);
                try {
                    JSONObject json = new JSONObject(jsonStr);
                    if (json.has("text")) {
                        String text = json.getString("text");
                        Log.d(TAG, "Found lyrics in window.lyricsData");
                        return cleanAndFormatLyrics(text);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Window JSON parsing error: " + e.getMessage());
                }
            }

            // 3. Поиск в текстовых элементах
            String[] textPatterns = {
                    "<div[^>]*class=[\"'][^\"']*lyrics[^\"']*[\"'][^>]*>(.*?)</div>",
                    "<div[^>]*class=[\"'][^\"']*text[^\"']*[\"'][^>]*>(.*?)</div>",
                    "<pre[^>]*>(.*?)</pre>",
                    "<p[^>]*>(.*?)</p>",
                    "lyrics_text[^>]*>(.*?)<",
                    "<div[^>]*data-lyrics[^>]*>(.*?)</div>"
            };

            for (String patternStr : textPatterns) {
                Pattern pattern = Pattern.compile(patternStr, Pattern.DOTALL);
                Matcher matcher = pattern.matcher(html);

                if (matcher.find()) {
                    String foundText = matcher.group(1);
                    if (foundText.length() > 50 && !foundText.contains("Устаревший браузер")) {
                        Log.d(TAG, "Found lyrics in HTML element: " + foundText.substring(0, 50) + "...");
                        return cleanAndFormatLyrics(foundText);
                    }
                }
            }

            // 4. Поиск в JavaScript данных
            Pattern jsPattern = Pattern.compile("text:\\s*[\"'](.*?)[\"']", Pattern.DOTALL);
            Matcher jsMatcher = jsPattern.matcher(html);

            if (jsMatcher.find()) {
                String foundText = jsMatcher.group(1);
                if (foundText.length() > 50) {
                    Log.d(TAG, "Found lyrics in JS text property");
                    return cleanAndFormatLyrics(foundText);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error extracting lyrics: " + e.getMessage());
        }

        Log.d(TAG, "No lyrics found in HTML");
        return null;
    }

    private String cleanAndFormatLyrics(String rawText) {
        if (rawText == null) return null;

        // 1. Декодируем все escape последовательности
        String text = decodeAllEscapeSequences(rawText);

        // 2. Удаляем HTML теги
        text = text.replaceAll("<[^>]*>", "");

        // 3. Заменяем HTML entities
        text = text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replace("&#160;", " ");

        // 4. Обрабатываем переносы строк
        text = text.replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("\\/", "/");

        // 5. Декодируем числовые HTML entities
        text = decodeNumericEntities(text);

        // 6. Чистим форматирование
        text = text.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("(\\n\\s*){3,}", "\n\n")
                .replaceAll("^\\s+", "");

        return text;
    }

    private String decodeAllEscapeSequences(String text) {
        // Декодируем Unicode escape sequences (uXXXX)
        text = decodeUnicodeEscape(text);

        // Декодируем UTF-8 sequences (\xXX)
        text = decodeUtf8Escape(text);

        // Декодируем URL encoding (%XX)
        text = decodeUrlEncoding(text);

        return text;
    }

    private String decodeUnicodeEscape(String text) {
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            try {
                String hex = matcher.group(1);
                int codePoint = Integer.parseInt(hex, 16);
                if (Character.isValidCodePoint(codePoint)) {
                    String replacement = new String(Character.toChars(codePoint));
                    matcher.appendReplacement(sb, replacement);
                } else {
                    matcher.appendReplacement(sb, "?");
                }
            } catch (Exception e) {
                matcher.appendReplacement(sb, "?");
            }
        }
        return matcher.appendTail(sb).toString();
    }

    private String decodeUtf8Escape(String text) {
        Pattern pattern = Pattern.compile("\\\\x([0-9a-fA-F]{2})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            try {
                String hex = matcher.group(1);
                int codePoint = Integer.parseInt(hex, 16);
                matcher.appendReplacement(sb, String.valueOf((char) codePoint));
            } catch (Exception e) {
                matcher.appendReplacement(sb, "?");
            }
        }
        return matcher.appendTail(sb).toString();
    }

    private String decodeUrlEncoding(String text) {
        try {
            return URLDecoder.decode(text, "UTF-8");
        } catch (Exception e) {
            return text;
        }
    }

    private String decodeNumericEntities(String text) {
        // Декодируем числовые entities (&#1234;)
        Pattern numericPattern = Pattern.compile("&#(\\d+);");
        Matcher numericMatcher = numericPattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (numericMatcher.find()) {
            try {
                int codePoint = Integer.parseInt(numericMatcher.group(1));
                if (Character.isValidCodePoint(codePoint)) {
                    String replacement = new String(Character.toChars(codePoint));
                    numericMatcher.appendReplacement(sb, replacement);
                } else {
                    numericMatcher.appendReplacement(sb, "?");
                }
            } catch (Exception e) {
                numericMatcher.appendReplacement(sb, "?");
            }
        }
        text = numericMatcher.appendTail(sb).toString();

        // Декодируем шестнадцатеричные entities (&#x1F600;)
        Pattern hexPattern = Pattern.compile("&#x([0-9a-fA-F]+);");
        Matcher hexMatcher = hexPattern.matcher(text);
        sb = new StringBuffer();

        while (hexMatcher.find()) {
            try {
                int codePoint = Integer.parseInt(hexMatcher.group(1), 16);
                if (Character.isValidCodePoint(codePoint)) {
                    String replacement = new String(Character.toChars(codePoint));
                    hexMatcher.appendReplacement(sb, replacement);
                } else {
                    hexMatcher.appendReplacement(sb, "?");
                }
            } catch (Exception e) {
                hexMatcher.appendReplacement(sb, "?");
            }
        }
        return hexMatcher.appendTail(sb).toString();
    }

    private void showError(String message) {
        requireActivity().runOnUiThread(() -> {
            lyricsTextView.setText(message);
            progressBar.setVisibility(View.GONE);
        });
    }

    private void displayLyrics(String lyricsText) {
        requireActivity().runOnUiThread(() -> {
            if (lyricsText != null && !lyricsText.isEmpty() && !lyricsText.equals("null")) {
                lyricsTextView.setText(lyricsText);
            } else {
                lyricsTextView.setText("Текст песни недоступен");
            }
            progressBar.setVisibility(View.GONE);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
            if (dialog != null) {
                View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error expanding bottom sheet: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (client != null) {
            client.dispatcher().cancelAll();
        }
    }
}