package ru.lisdevs.messenger.official.audios;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.player.PlayerBottomSheetFragment;
import ru.lisdevs.messenger.service.HlsService;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;

import androidx.annotation.NonNull;

import java.net.URL;
import java.net.URLDecoder;

public class AudioInputFragment extends Fragment {

    private EditText audioUrlEditText;
    private Button playButton;
    private Button clearButton;
    private Button exampleButton;

    private HlsService hlsService;
    private boolean isServiceBound = false;

    // ServiceConnection для связи с HlsService
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            HlsService.LocalBinder binder = (HlsService.LocalBinder) service;
            hlsService = binder.getService();
            isServiceBound = true;
            updatePlayButtonState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            hlsService = null;
        }
    };

    public AudioInputFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_audio_input, container, false);

        initViews(view);
        setupListeners();
        bindHlsService();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbindHlsService();
    }

    private void bindHlsService() {
        Intent intent = new Intent(getContext(), HlsService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindHlsService() {
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    private void initViews(View view) {
        audioUrlEditText = view.findViewById(R.id.et_audio_url);
        playButton = view.findViewById(R.id.btn_play);
        clearButton = view.findViewById(R.id.btn_clear);
        exampleButton = view.findViewById(R.id.btn_example);

        // Изначально кнопка воспроизведения неактивна
        playButton.setEnabled(false);
    }

    private void setupListeners() {
        playButton.setOnClickListener(v -> playAudioFromUrl());
        clearButton.setOnClickListener(v -> clearInput());
        exampleButton.setOnClickListener(v -> insertExampleUrl());

        // Автоматическая проверка URL при изменении текста
        audioUrlEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateUrl(s.toString());
            }
        });
    }

    private void validateUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            playButton.setEnabled(false);
            playButton.setText("Воспроизвести");
            return;
        }

        boolean isValid = isValidUrl(url) && isSupportedFormat(url);
        playButton.setEnabled(isValid);
        playButton.setText(isValid ? "Воспроизвести" : "Некорректная ссылка");
    }

    private void updatePlayButtonState() {
        if (isServiceBound && hlsService != null && hlsService.isPlaying()) {
            playButton.setText("Пауза");
            playButton.setEnabled(true);
        } else {
            String currentText = playButton.getText().toString();
            if (!currentText.equals("Некорректная ссылка")) {
                playButton.setText("Воспроизвести");
            }
        }
    }

    private void playAudioFromUrl() {
        String audioUrl = audioUrlEditText.getText().toString().trim();

        if (TextUtils.isEmpty(audioUrl)) {
            showToast("Введите ссылку на аудио");
            return;
        }

        if (!isValidUrl(audioUrl)) {
            showToast("Некорректная ссылка");
            return;
        }

        if (!isSupportedFormat(audioUrl)) {
            showToast("Формат не поддерживается");
            return;
        }

        // Если сервис воспроизводит аудио, останавливаем его
        if (isServiceBound && hlsService != null && hlsService.isPlaying()) {
            pauseAudioPlayback();
            return;
        }

        // Показываем прогресс
        showProgress(true);

        // Создаем объект Audio для передачи в сервис
        Audio audio = createAudioFromUrl(audioUrl);

        // Запускаем воспроизведение
        startAudioPlayback(audio);
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.length() < 10) {
            return false;
        }

        try {
            new URL(url);
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSupportedFormat(String url) {
        String lowerUrl = url.toLowerCase();
        return lowerUrl.matches(".*\\.(m3u8|mp3|aac|wav|ogg|flac|m4a)$") ||
                lowerUrl.contains("stream") ||
                lowerUrl.contains("audio") ||
                lowerUrl.contains("radio");
    }

    private Audio createAudioFromUrl(String url) {
        Audio audio = new Audio();
        audio.setUrl(url);
        audio.setTitle(extractTitleFromUrl(url));
        audio.setArtist("Неизвестный исполнитель");
        audio.setDuration(0);
        audio.setStream(true); // Помечаем как потоковое аудио

        extractMetadataFromUrl(url, audio);
        return audio;
    }

    private String extractTitleFromUrl(String url) {
        try {
            URL uri = new URL(url);
            String path = uri.getPath();

            if (path == null || path.isEmpty()) {
                return "Аудио поток";
            }

            String[] parts = path.split("/");
            String lastPart = parts[parts.length - 1];

            // Удаляем параметры запроса
            if (lastPart.contains("?")) {
                lastPart = lastPart.substring(0, lastPart.indexOf("?"));
            }

            // Удаляем расширение файла
            if (lastPart.contains(".")) {
                lastPart = lastPart.substring(0, lastPart.lastIndexOf("."));
            }

            // Декодируем URL-encoded символы
            lastPart = URLDecoder.decode(lastPart, "UTF-8");

            // Заменяем специальные символы на пробелы
            lastPart = lastPart.replaceAll("[_\\-+]", " ").trim();

            return lastPart.isEmpty() ? "Аудио поток" : lastPart;

        } catch (Exception e) {
            return "Аудио поток";
        }
    }

    private void extractMetadataFromUrl(String url, Audio audio) {
        try {
            // Определяем тип контента по домену или пути
            if (url.contains("siren=1")) {
                audio.setTitle("Сирена");
                audio.setArtist("Экстренное оповещение");
            } else if (url.contains("radio")) {
                audio.setArtist("Радиостанция");
            } else if (url.contains("stream")) {
                audio.setArtist("Аудио поток");
            }

            // Дополнительные правила для специфичных доменов
            if (url.contains("vkuseraudio.net")) {
                audio.setArtist("VK Audio");
            }

        } catch (Exception e) {
            // Игнорируем ошибки извлечения метаданных
        }
    }

    private void startAudioPlayback(Audio audio) {
        try {
            // Используем статический метод HlsService для воспроизведения
            HlsService.playAudio(requireContext(), audio);

            showToast("Запуск воспроизведения...");
            openPlayerBottomSheet();

            // Обновляем состояние кнопки после небольшой задержки
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                updatePlayButtonState();
                showProgress(false);
            }, 1000);

        } catch (SecurityException e) {
            showToast("Ошибка доступа: " + e.getMessage());
            showProgress(false);
        } catch (Exception e) {
            showToast("Ошибка воспроизведения: " + e.getMessage());
            showProgress(false);
        }
    }

    private void pauseAudioPlayback() {
        try {
            HlsService.pause(requireContext());
            playButton.setText("Воспроизвести");
            showToast("Воспроизведение приостановлено");
        } catch (Exception e) {
            showToast("Ошибка при паузе: " + e.getMessage());
        }
    }

    private void openPlayerBottomSheet() {
        // Открываем нижнюю панель плеера, если она есть
        try {
            if (getActivity() != null) {
                PlayerBottomSheetFragment playerSheet = PlayerBottomSheetFragment.newInstance();
                playerSheet.show(getActivity().getSupportFragmentManager(), "player_sheet");
            }
        } catch (Exception e) {
            Log.d("AudioInput", "PlayerBottomSheetFragment not available: " + e.getMessage());
            // Игнорируем, если нижняя панель не реализована
        }
    }

    private void clearInput() {
        audioUrlEditText.setText("");
        showToast("Поле очищено");

        // Если аудио воспроизводится, останавливаем его
        if (isServiceBound && hlsService != null && hlsService.isPlaying()) {
            HlsService.stop(requireContext());
            playButton.setText("Воспроизвести");
        }
    }

    private void insertExampleUrl() {
        String exampleUrl = "https://psv4.vkuseraudio.net/s/v1/a2/DinfywgUh-V8SooAopuDg8155vwzIguWtQSebcQppMe1LTLjQlmKcZdYc3nQevxy4P3nSs8UQBsHRqadj1QY1s1OQPQ_GhjrkGChEpeVbyFTjpTS0c_yV3JI8ss_TzATz7Bmqrp-u8L4_-0AZ4lru2Km0Kc3935bSw/index.m3u8?siren=1";
        audioUrlEditText.setText(exampleUrl);
        showToast("Вставлен пример ссылки");
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showProgress(boolean show) {
        playButton.setEnabled(!show);
        playButton.setText(show ? "Загрузка..." : "Воспроизвести");
    }

    // Метод для программной установки URL
    public void setAudioUrl(String url) {
        if (audioUrlEditText != null) {
            audioUrlEditText.setText(url);
            validateUrl(url);
        }
    }

    // Метод для получения текущего URL
    public String getAudioUrl() {
        return audioUrlEditText != null ? audioUrlEditText.getText().toString().trim() : "";
    }

    // Метод для получения текущего аудио из сервиса
    public Audio getCurrentAudio() {
        if (isServiceBound && hlsService != null) {
            return hlsService.getCurrentAudio();
        }
        return null;
    }

    // Метод для проверки, воспроизводится ли аудио
    public boolean isPlaying() {
        return isServiceBound && hlsService != null && hlsService.isPlaying();
    }

    // Вложенный класс Audio (Parcelable) - остается без изменений
    public static class Audio implements android.os.Parcelable {
        private String id;
        private String title;
        private String artist;
        private String url;
        private int duration;
        private String album;
        private String coverUrl;
        private boolean isStream;

        public Audio() {
            // Конструктор по умолчанию
        }

        public Audio(String id, String title, String artist, String url, int duration) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.url = url;
            this.duration = duration;
        }

        protected Audio(android.os.Parcel in) {
            id = in.readString();
            title = in.readString();
            artist = in.readString();
            url = in.readString();
            duration = in.readInt();
            album = in.readString();
            coverUrl = in.readString();
            isStream = in.readByte() != 0;
        }

        public static final Creator<Audio> CREATOR = new Creator<Audio>() {
            @Override
            public Audio createFromParcel(android.os.Parcel in) {
                return new Audio(in);
            }

            @Override
            public Audio[] newArray(int size) {
                return new Audio[size];
            }
        };

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getArtist() { return artist; }
        public void setArtist(String artist) { this.artist = artist; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }

        public String getAlbum() { return album; }
        public void setAlbum(String album) { this.album = album; }

        public String getCoverUrl() { return coverUrl; }
        public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

        public boolean isStream() { return isStream; }
        public void setStream(boolean stream) { isStream = stream; }

        // Форматирование длительности в мм:сс
        public String getFormattedDuration() {
            int minutes = duration / 60;
            int seconds = duration % 60;
            return String.format("%d:%02d", minutes, seconds);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeString(id);
            dest.writeString(title);
            dest.writeString(artist);
            dest.writeString(url);
            dest.writeInt(duration);
            dest.writeString(album);
            dest.writeString(coverUrl);
            dest.writeByte((byte) (isStream ? 1 : 0));
        }

        @Override
        public String toString() {
            return "Audio{" +
                    "title='" + title + '\'' +
                    ", artist='" + artist + '\'' +
                    ", url='" + url + '\'' +
                    ", duration=" + duration +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Audio audio = (Audio) o;
            return url != null && url.equals(audio.url);
        }

        @Override
        public int hashCode() {
            return url != null ? url.hashCode() : 0;
        }
    }
}