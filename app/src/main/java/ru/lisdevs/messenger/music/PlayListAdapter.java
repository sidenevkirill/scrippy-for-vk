package ru.lisdevs.messenger.music;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import ru.lisdevs.messenger.R;

public class PlayListAdapter extends RecyclerView.Adapter<PlayListAdapter.AudioViewHolder> {

    private List<Audio> audioList;

    public PlayListAdapter(List<Audio> audioList) {
        this.audioList = audioList;
    }

    @NonNull
    @Override
    public AudioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlists, parent, false);
        return new AudioViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AudioViewHolder holder, int position) {
        Audio audio = audioList.get(position);
        holder.artistText.setText(audio.getArtist());
        holder.titleText.setText(audio.getTitle());
        // Можно добавить обработчик клика для проигрыша
        holder.itemView.setOnClickListener(v -> {
            // Реализуйте проигрывание по URL или другое действие
            Toast.makeText(v.getContext(), "Проигрываем: " + audio.getTitle(), Toast.LENGTH_SHORT).show();
            // Например, запустите MediaPlayer с audio.getUrl()
        });
    }

    @Override
    public int getItemCount() {
        return audioList.size();
    }

    static class AudioViewHolder extends RecyclerView.ViewHolder {
        TextView artistText;
        TextView titleText;

        public AudioViewHolder(@NonNull View itemView) {
            super(itemView);
            artistText = itemView.findViewById(R.id.artistText);
            titleText = itemView.findViewById(R.id.titleText);
        }
    }
}