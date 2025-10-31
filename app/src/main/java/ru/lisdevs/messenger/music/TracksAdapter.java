package ru.lisdevs.messenger.music;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Track;

// Адаптер для отображения списка треков в RecyclerView
public class TracksAdapter extends RecyclerView.Adapter<TracksAdapter.TrackViewHolder> {

    // Список треков, который отображается в RecyclerView
    private List<Track> tracks;

    // Конструктор инициализирует пустой список
    public TracksAdapter() {
        tracks = new ArrayList<>();
    }

    // Метод для обновления данных в адаптере
    public void setTracks(List<Track> newTracks) {
        tracks.clear(); // очищаем текущий список
        tracks.addAll(newTracks); // добавляем новые треки
        notifyDataSetChanged(); // уведомляем адаптер об изменениях
    }

    // Создает новый ViewHolder при необходимости
    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate layout элемента списка
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search, parent, false);
        return new TrackViewHolder(view);
    }

    // Привязывает данные к ViewHolder по позиции
    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Track track = tracks.get(position);
        holder.bind(track);
    }

    // Возвращает количество элементов в списке
    @Override
    public int getItemCount() {
        return tracks.size();
    }

    // Внутренний класс ViewHolder для элемента списка
    static class TrackViewHolder extends RecyclerView.ViewHolder {

        TextView artistTextView; // Для отображения имени артиста
        TextView titleTextView;  // Для отображения названия трека

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            artistTextView = itemView.findViewById(R.id.artistText);
            titleTextView = itemView.findViewById(R.id.titleText);
        }

        // Метод для привязки данных к элементам ViewHolder
        void bind(Track track) {
            artistTextView.setText(track.getArtist());
            titleTextView.setText(track.getTitle());
        }
    }
}
