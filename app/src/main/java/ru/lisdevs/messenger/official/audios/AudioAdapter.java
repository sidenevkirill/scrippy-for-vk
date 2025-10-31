package ru.lisdevs.messenger.official.audios;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Message;
import ru.lisdevs.messenger.utils.TokenManager;

public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {

    private List<ru.lisdevs.messenger.official.audios.Audio> audioList;
    private OnItemClickListener onItemClickListener;
    private Context context;
    private List<Message> availableChats; // Добавляем список чатов

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public AudioAdapter(List<ru.lisdevs.messenger.official.audios.Audio> audioList, Context context) {
        this.audioList = audioList;
        this.context = context;
        this.availableChats = new ArrayList<>();
    }

    // Метод для установки списка чатов
    public void setAvailableChats(List<Message> chats) {
        this.availableChats.clear();
        this.availableChats.addAll(chats);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ru.lisdevs.messenger.official.audios.Audio audio = audioList.get(position);

        holder.artistTextView.setText(audio.getArtist());
        holder.titleTextView.setText(audio.getTitle());

        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(position);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            showPopupMenu(v, audio, position);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return 0;
    }

    private void showPopupMenu(View view, ru.lisdevs.messenger.official.audios.Audio audio, int position) {
        PopupMenu popupMenu = new PopupMenu(context, view);
        popupMenu.inflate(R.menu.audio_item_menu);

        // Добавляем новую опцию в меню
        popupMenu.getMenu().add(0, R.id.menu_share_chat, 0, "Отправить в диалог");

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_copy_url) {
                copyAudioUrl(audio);
                return true;
            } else if (item.getItemId() == R.id.menu_share) {
                shareAudioUrl(audio);
                return true;
            } else if (item.getItemId() == R.id.menu_share_chat) {
                shareAudioToChat(audio);
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void shareAudioToChat(ru.lisdevs.messenger.official.audios.Audio audio) {
        if (availableChats.isEmpty()) {
            Toast.makeText(context, "Список диалогов пуст", Toast.LENGTH_SHORT).show();
            return;
        }

        ru.lisdevs.messenger.official.audios.ShareAudioBottomSheet bottomSheet = ru.lisdevs.messenger.official.audios.ShareAudioBottomSheet.newInstance(
                audio,
                new ArrayList<>(availableChats)
        );

        bottomSheet.setShareAudioListener((chat, audioToShare) -> {
            // Здесь реализуем отправку аудио в выбранный чат
            sendAudioToChat(chat, audioToShare);
        });

        if (context instanceof FragmentActivity) {
            bottomSheet.show(((FragmentActivity) context).getSupportFragmentManager(),
                    "share_audio_bottom_sheet");
        }
    }

    private void sendAudioToChat(Message chat, ru.lisdevs.messenger.official.audios.Audio audio) {
        // Реализация отправки аудио через VK API
        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null) {
            Toast.makeText(context, "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            return;
        }

        // Формируем сообщение с информацией об аудио
        String messageText = "Аудиозапись: " + audio.getArtist() + " - " + audio.getTitle() +
                "\nСсылка: " + audio.getUrl();

        String url = "https://api.vk.com/method/messages.send" +
                "?access_token=" + accessToken +
                "&v=5.131" +
                "&peer_id=" + chat.getPeerId() +
                "&message=" + URLEncoder.encode(messageText) +
                "&random_id=" + System.currentTimeMillis();

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (context != null) {
                    ((Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, "Ошибка отправки: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (context != null) {
                    ((Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, "Аудио отправлено в диалог",
                                    Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    // Остальные методы остаются без изменений
    private void copyAudioUrl(ru.lisdevs.messenger.official.audios.Audio audio) {
        String audioUrl = audio.getUrl();
        if (audioUrl != null && !audioUrl.isEmpty() && !audioUrl.equals("null")) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Audio URL", audioUrl);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "Ссылка на аудио скопирована", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Ссылка недоступна", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareAudioUrl(ru.lisdevs.messenger.official.audios.Audio audio) {
        String audioUrl = audio.getUrl();
        if (audioUrl != null && !audioUrl.isEmpty() && !audioUrl.equals("null")) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, audioUrl);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, audio.getArtist() + " - " + audio.getTitle());
            context.startActivity(Intent.createChooser(shareIntent, "Поделиться аудиозаписью"));
        } else {
            Toast.makeText(context, "Ссылка недоступна", Toast.LENGTH_SHORT).show();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView artistTextView, titleTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            artistTextView = itemView.findViewById(R.id.artistText);
            titleTextView = itemView.findViewById(R.id.titleText);
        }
    }
}