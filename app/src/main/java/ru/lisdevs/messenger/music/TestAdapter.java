package ru.lisdevs.messenger.music;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.lisdevs.messenger.R;

public class TestAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private List<Audio> audioList;
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public TestAdapter(List<Audio> audioList) {
        this.audioList = audioList;
    }

    public void updateList(List<Audio> newList) {
        this.audioList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    // Метод для перемешивания списка
    public void shuffleList() {
        if (audioList != null && !audioList.isEmpty()) {
            Collections.shuffle(audioList);
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemCount() {
        return audioList.size() + 1; // 1 для header
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_audio, parent, false);
            return new AudioViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            int trackCount = audioList.size();
            ((HeaderViewHolder) holder).bind(this, trackCount);
        } else if (holder instanceof AudioViewHolder) {
            int actualPosition = position - 0; // смещение из-за header
            Audio audio = audioList.get(actualPosition);
            ((AudioViewHolder) holder).bind(audio);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(position);
            }
        });
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {

        LinearLayout shuffleButton;
        TextView trackCountText;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            shuffleButton = itemView.findViewById(R.id.shuffleButton);
            trackCountText = itemView.findViewById(R.id.trackCountText);
        }

        void bind(final TestAdapter adapter, int trackCount) {
            // Обработчик кнопки перемешивания
            shuffleButton.setOnClickListener(v -> adapter.shuffleList());
            // Обновляем отображение количества треков
            trackCountText.setText("Треков: " + trackCount);
        }
    }

    static class AudioViewHolder extends RecyclerView.ViewHolder {

        TextView artistText;
        TextView titleText;

        public AudioViewHolder(@NonNull View itemView) {
            super(itemView);
            artistText = itemView.findViewById(R.id.artistText);
            titleText = itemView.findViewById(R.id.titleText);
        }

        void bind(Audio audio) {
            artistText.setText(audio.getArtist());
            titleText.setText(audio.getTitle());
        }
    }
}