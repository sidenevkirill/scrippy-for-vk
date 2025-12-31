package ru.lisdevs.messenger.utils;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.widget.Toast;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;

import ru.lisdevs.messenger.messages.Audio;
import ru.lisdevs.messenger.model.Attachment;
import ru.lisdevs.messenger.service.MusicPlayerService;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Parcelable;
import android.util.Log;
import android.widget.Toast;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.List;

public class AudioPlayerHelper {
    private static final String TAG = "AudioPlayerHelper";
    private static AudioPlayerHelper instance;
    private Context context;
    private int currentPlayingPosition = -1;
    private List<Attachment> currentAttachments = new ArrayList<>();

    private AudioPlayerHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized AudioPlayerHelper getInstance(Context context) {
        if (instance == null) {
            instance = new AudioPlayerHelper(context);
        }
        return instance;
    }

    public void setCurrentAttachments(List<Attachment> attachments) {
        this.currentAttachments.clear();
        if (attachments != null) {
            this.currentAttachments.addAll(attachments);
        }
    }

    public void playAudio(Attachment.Audio audio, int position, List<Attachment> attachments) {
        try {
            Log.d(TAG, "playAudio called: position=" + position);

            if (audio == null || audio.getUrl() == null || audio.getUrl().isEmpty()) {
                Toast.makeText(context, "Аудио недоступно", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "Audio URL: " + audio.getUrl());
            Log.d(TAG, "Audio title: " + audio.getTitle());
            Log.d(TAG, "Audio artist: " + audio.getArtist());

            // Останавливаем предыдущее воспроизведение
            if (currentPlayingPosition != -1) {
                sendStopBroadcast(currentPlayingPosition);
            }

            // Обновляем текущий трек
            currentPlayingPosition = position;
            setCurrentAttachments(attachments);

            // Создаем объект Audio для сервиса
            Audio serviceAudio = new Audio(
                    audio.getArtist() != null ? audio.getArtist() : "Unknown Artist",
                    audio.getTitle() != null ? audio.getTitle() : "Unknown Track",
                    audio.getUrl()
            );

            if (audio.getDuration() > 0) {
                serviceAudio.setDuration(audio.getDuration());
            }

            // Создаем плейлист из всех аудио в attachments
            ArrayList<Audio> playlist = new ArrayList<>();
            for (Attachment attachment : attachments) {
                if (attachment.getAudio() != null && attachment.getAudio().getUrl() != null) {
                    Audio audioItem = new Audio(
                            attachment.getAudio().getArtist(),
                            attachment.getAudio().getTitle(),
                            attachment.getAudio().getUrl()
                    );
                    if (attachment.getAudio().getDuration() > 0) {
                        audioItem.setDuration(attachment.getAudio().getDuration());
                    }
                    playlist.add(audioItem);
                }
            }

            // Находим позицию текущего аудио в плейлисте
            int playlistPosition = 0;
            for (int i = 0; i < playlist.size(); i++) {
                if (playlist.get(i).getUrl().equals(audio.getUrl())) {
                    playlistPosition = i;
                    break;
                }
            }

            // Запускаем сервис
            Intent intent = new Intent(context, MusicPlayerService.class);
            intent.setAction(MusicPlayerService.ACTION_PLAY);

            // КОРРЕКТНЫЙ СПОСОБ: создаем ArrayList<Parcelable>
            ArrayList<Parcelable> parcelablePlaylist = new ArrayList<>();
            parcelablePlaylist.addAll(playlist);
            intent.putParcelableArrayListExtra("PLAYLIST", parcelablePlaylist);

            intent.putExtra("POSITION", playlistPosition);
            intent.putExtra("URL", serviceAudio.getUrl());
            intent.putExtra("TITLE", serviceAudio.getTitle());
            intent.putExtra("ARTIST", serviceAudio.getArtist());

            if (serviceAudio.getDuration() > 0) {
                intent.putExtra("DURATION", (int) serviceAudio.getDuration());
            }

            Log.d(TAG, "Starting service with playlist size: " + playlist.size() + ", position: " + playlistPosition);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }

            sendPlayBroadcast(position);

        } catch (Exception e) {
            Log.e(TAG, "Error playing audio", e);
            Toast.makeText(context, "Ошибка воспроизведения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void playAudioMessage(Attachment.Document audioMessage, int position, List<Attachment> attachments) {
        try {
            Log.d(TAG, "playAudioMessage called: position=" + position);

            if (audioMessage == null || audioMessage.getUrl() == null || audioMessage.getUrl().isEmpty()) {
                Toast.makeText(context, "Голосовое сообщение недоступно", Toast.LENGTH_SHORT).show();
                return;
            }

            // Останавливаем предыдущее воспроизведение
            if (currentPlayingPosition != -1) {
                sendStopBroadcast(currentPlayingPosition);
            }

            // Обновляем текущий трек
            currentPlayingPosition = position;
            setCurrentAttachments(attachments);

            // Создаем объект Audio для сервиса
            Audio serviceAudio = new Audio(
                    "Голосовое сообщение",
                    audioMessage.getTitle() != null ? audioMessage.getTitle() : "Voice Message",
                    audioMessage.getUrl()
            );

            if (audioMessage.getSize() > 0) {
                serviceAudio.setDuration(audioMessage.getSize());
            }

            // Создаем плейлист из всех голосовых сообщений в attachments
            ArrayList<Audio> playlist = new ArrayList<>();
            for (Attachment attachment : attachments) {
                if (attachment.getDoc() != null && "audio_message".equals(attachment.getDoc().getType())) {
                    Audio audioItem = new Audio(
                            "Голосовое сообщение",
                            attachment.getDoc().getTitle(),
                            attachment.getDoc().getUrl()
                    );
                    if (attachment.getDoc().getSize() > 0) {
                        audioItem.setDuration(attachment.getDoc().getSize());
                    }
                    playlist.add(audioItem);
                }
            }

            // Находим позицию текущего сообщения в плейлисте
            int playlistPosition = 0;
            for (int i = 0; i < playlist.size(); i++) {
                if (playlist.get(i).getUrl().equals(audioMessage.getUrl())) {
                    playlistPosition = i;
                    break;
                }
            }

            // Запускаем сервис
            Intent intent = new Intent(context, MusicPlayerService.class);
            intent.setAction(MusicPlayerService.ACTION_PLAY);

            // КОРРЕКТНЫЙ СПОСОБ: создаем ArrayList<Parcelable>
            ArrayList<Parcelable> parcelablePlaylist = new ArrayList<>();
            parcelablePlaylist.addAll(playlist);
            intent.putParcelableArrayListExtra("PLAYLIST", parcelablePlaylist);

            intent.putExtra("POSITION", playlistPosition);
            intent.putExtra("URL", serviceAudio.getUrl());
            intent.putExtra("TITLE", serviceAudio.getTitle());
            intent.putExtra("ARTIST", serviceAudio.getArtist());

            if (serviceAudio.getDuration() > 0) {
                intent.putExtra("DURATION", (int) serviceAudio.getDuration());
            }

            Log.d(TAG, "Starting service for audio message");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }

            sendPlayBroadcast(position);

        } catch (Exception e) {
            Log.e(TAG, "Error playing audio message", e);
            Toast.makeText(context, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show();
        }
    }

    public void togglePlayPause(int position, List<Attachment> attachments) {
        Log.d(TAG, "togglePlayPause: position=" + position + ", currentPlayingPosition=" + currentPlayingPosition);

        if (currentPlayingPosition == position && isPlaying()) {
            // Если кликнули на текущий трек и он играет, ставим на паузу
            pause();
        } else {
            // Если кликнули на другой трек или текущий на паузе, начинаем воспроизведение
            if (position >= 0 && position < attachments.size()) {
                Attachment attachment = attachments.get(position);
                if (attachment != null) {
                    if (attachment.getAudio() != null) {
                        playAudio(attachment.getAudio(), position, attachments);
                    } else if (attachment.getDoc() != null && "audio_message".equals(attachment.getDoc().getType())) {
                        playAudioMessage(attachment.getDoc(), position, attachments);
                    } else {
                        Log.e(TAG, "No audio or audio_message found at position: " + position);
                    }
                }
            }
        }
    }

    public void pause() {
        Log.d(TAG, "pause called");
        Intent intent = new Intent(context, MusicPlayerService.class);
        intent.setAction(MusicPlayerService.ACTION_PAUSE);
        context.startService(intent);
        sendStopBroadcast(currentPlayingPosition);
        currentPlayingPosition = -1;
    }

    public void stopPlayback() {
        Log.d(TAG, "stopPlayback called");
        if (currentPlayingPosition != -1) {
            Intent intent = new Intent(context, MusicPlayerService.class);
            intent.setAction(MusicPlayerService.ACTION_STOP);
            context.startService(intent);
            sendStopBroadcast(currentPlayingPosition);
            currentPlayingPosition = -1;
            currentAttachments.clear();
        }
    }

    public boolean isPlaying() {
        // Вам нужно отслеживать состояние воспроизведения через BroadcastReceiver
        // или через связь с сервисом
        return currentPlayingPosition != -1;
    }

    private void sendPlayBroadcast(int position) {
        Log.d(TAG, "Sending PLAY broadcast for position: " + position);
        Intent intent = new Intent("AUDIO_PLAYBACK_STATE");
        intent.putExtra("position", position);
        intent.putExtra("is_playing", true);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private void sendStopBroadcast(int position) {
        Log.d(TAG, "Sending STOP broadcast for position: " + position);
        Intent intent = new Intent("AUDIO_PLAYBACK_STATE");
        intent.putExtra("position", position);
        intent.putExtra("is_playing", false);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public int getCurrentPlayingPosition() {
        return currentPlayingPosition;
    }

    public boolean isPlayingAtPosition(int position) {
        return currentPlayingPosition == position;
    }
}