package ru.lisdevs.messenger.music;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Audio;
import ru.lisdevs.messenger.model.Message;
import ru.lisdevs.messenger.official.audios.ShareAudioBottomSheet;
import ru.lisdevs.messenger.utils.TokenManager;

public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.AudioViewHolder> {

    private Context context;
    private List<Message> availableChats = new ArrayList<>();

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private List<Audio> audioList;
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    private MusicListFragment.OnMenuClickListener menuClickListener;

    public void setOnMenuClickListener(MusicListFragment.OnMenuClickListener listener) {
        this.menuClickListener = listener;
    }

    public AudioAdapter(List<Audio> audioList) {
        this.audioList = audioList;
    }

    public void updateList(List<Audio> newList) {
        this.audioList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AudioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_audio, parent, false);
        return new AudioViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AudioViewHolder holder, int position) {
        Audio audio = audioList.get(position);

        // Форматируем длительность
        String formattedDuration = formatDuration(audio.getDuration());

        // Объединяем артиста и длительность в одну строку
        String artistWithDuration = String.format("%s • %s", audio.getArtist(), formattedDuration);

        // Устанавливаем текст
        holder.artistText.setText(artistWithDuration);
        holder.titleText.setText(audio.getTitle());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(position);
            }
        });

        holder.downloadButton.setOnClickListener(v -> {
            if (menuClickListener != null) {
                menuClickListener.onMenuClick(audio);
            }
        });
    }

    private void shareAudioToChat(ru.lisdevs.messenger.official.audios.Audio audio) {
        if (availableChats.isEmpty()) {
            Toast.makeText(context, "Список диалогов пуст", Toast.LENGTH_SHORT).show();
            return;
        }

        ShareAudioBottomSheet bottomSheet = ShareAudioBottomSheet.newInstance(
                audio,
                new ArrayList<>(availableChats)
        );

        bottomSheet.setShareAudioListener((chat, audioToShare) -> {
            sendAudioToChat(chat, audioToShare);
        });

        if (context instanceof FragmentActivity) {
            bottomSheet.show(((FragmentActivity) context).getSupportFragmentManager(),
                    "share_audio_bottom_sheet");
        }
    }

    private void sendAudioToChat(Message chat, ru.lisdevs.messenger.official.audios.Audio audio) {
        String accessToken = TokenManager.getInstance(context).getToken();
        if (accessToken == null) {
            Toast.makeText(context, "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            return;
        }

        String messageText = "Аудиозапись: " + audio.getArtist() + " - " + audio.getTitle() +
                "\nСсылка: " + audio.getUrl();

        try {
            String url = "https://api.vk.com/method/messages.send" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&peer_id=" + chat.getPeerId() +
                    "&message=" + URLEncoder.encode(messageText, "UTF-8") +
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
        } catch (Exception e) {
            Toast.makeText(context, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Метод для форматирования длительности (секунды -> минуты:секунды)
    private String formatDuration(int durationInSeconds) {
        int minutes = durationInSeconds / 60;
        int seconds = durationInSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    @Override
    public int getItemCount() {
        return audioList.size();
    }

    static class AudioViewHolder extends RecyclerView.ViewHolder {
        TextView artistText;
        TextView titleText;
        ImageView downloadButton;

        public AudioViewHolder(@NonNull View itemView) {
            super(itemView);
            artistText = itemView.findViewById(R.id.artistText);
            titleText = itemView.findViewById(R.id.titleText);
            downloadButton = itemView.findViewById(R.id.downloadButton);
        }
    }
}