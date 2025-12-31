package ru.lisdevs.messenger.messages;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Attachment;
import ru.lisdevs.messenger.utils.AudioPlayerHelper;

public class AttachmentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_PHOTO = 0;
    private static final int TYPE_DOCUMENT = 1;
    private static final int TYPE_AUDIO = 2;
    private static final int TYPE_AUDIO_MESSAGE = 3;
    private static final int TYPE_STICKER = 4;
    private static final int TYPE_GRAFFITI = 5;

    private List<Attachment> attachments = new ArrayList<>();
    private OnPhotoClickListener onPhotoClickListener;
    private List<Attachment> currentMessageAttachments;

    private Context context;
    private int currentPlayingPosition = -1;
    private AudioPlayerHelper audioPlayerHelper;

    public void setCurrentMessageAttachments(List<Attachment> attachments) {
        this.currentMessageAttachments = attachments;
    }

    public interface OnPhotoClickListener {
        void onPhotoClick(Attachment.Photo photo, int position);
        void onMultiplePhotosClick(List<String> photoUrls, int currentPosition);
    }

    public void setOnPhotoClickListener(OnPhotoClickListener listener) {
        this.onPhotoClickListener = listener;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Attachment attachment = attachments.get(position);
        String type = attachment.getType();

        if ("doc".equals(type) && attachment.getDoc() != null && "graffiti".equals(attachment.getDoc().getType())) {
            return TYPE_GRAFFITI;
        }

        if ("graffiti".equals(type)) {
            return TYPE_GRAFFITI;
        }

        switch (type) {
            case "photo":
                return TYPE_PHOTO;
            case "doc":
                return TYPE_DOCUMENT;
            case "audio":
                return TYPE_AUDIO;
            case "audio_message":
                return TYPE_AUDIO_MESSAGE;
            case "sticker":
                return TYPE_STICKER;
            default:
                return TYPE_DOCUMENT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        audioPlayerHelper = AudioPlayerHelper.getInstance(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        switch (viewType) {
            case TYPE_PHOTO:
                View photoView = inflater.inflate(R.layout.item_attachment_photo, parent, false);
                return new PhotoViewHolder(photoView);
            case TYPE_DOCUMENT:
                View docView = inflater.inflate(R.layout.item_attachment_document, parent, false);
                return new DocumentViewHolder(docView);
            case TYPE_AUDIO:
                View audioView = inflater.inflate(R.layout.item_attachment_audio, parent, false);
                return new AudioViewHolder(audioView);
            case TYPE_AUDIO_MESSAGE:
                View audioMessageView = inflater.inflate(R.layout.item_attachment_audio_message, parent, false);
                return new AudioMessageViewHolder(audioMessageView);
            case TYPE_STICKER:
                View stickerView = inflater.inflate(R.layout.item_attachment_sticker, parent, false);
                return new StickerViewHolder(stickerView);
            case TYPE_GRAFFITI:
                View graffitiView = inflater.inflate(R.layout.item_attachment_graffiti, parent, false);
                return new GraffitiViewHolder(graffitiView);
            default:
                View defaultView = inflater.inflate(R.layout.item_attachment_document, parent, false);
                return new DocumentViewHolder(defaultView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Attachment attachment = attachments.get(position);

        switch (holder.getItemViewType()) {
            case TYPE_PHOTO:
                ((PhotoViewHolder) holder).bind(attachment.getPhoto(), position);
                break;
            case TYPE_DOCUMENT:
                ((DocumentViewHolder) holder).bind(attachment.getDoc(), position);
                break;
            case TYPE_AUDIO:
                ((AudioViewHolder) holder).bind(attachment.getAudio(), position);
                break;
            case TYPE_AUDIO_MESSAGE:
                ((AudioMessageViewHolder) holder).bind(attachment.getDoc(), position);
                break;
            case TYPE_STICKER:
                ((StickerViewHolder) holder).bind(attachment.getPhoto());
                break;
            case TYPE_GRAFFITI:
                ((GraffitiViewHolder) holder).bind(attachment);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return attachments.size();
    }

    public void updatePlayingState(int position, boolean isPlaying) {
        if (currentPlayingPosition != -1 && currentPlayingPosition != position) {
            notifyItemChanged(currentPlayingPosition);
        }

        currentPlayingPosition = isPlaying ? position : -1;
        if (position != -1) {
            notifyItemChanged(position);
        }
    }

    // ViewHolder для фотографий
    class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView captionText;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.attachmentImage);
            captionText = itemView.findViewById(R.id.captionText);

            imageView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Attachment attachment = attachments.get(position);
                    if (attachment.getPhoto() != null && onPhotoClickListener != null) {
                        onPhotoClickListener.onPhotoClick(attachment.getPhoto(), position);
                    }
                }
            });
        }

        void bind(Attachment.Photo photo, int position) {
            if (photo != null) {
                String imageUrl = photo.getPreviewUrl();
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Picasso.get()
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_photo_placeholder)
                            .error(R.drawable.ic_photo_error)
                            .into(imageView);
                } else {
                    imageView.setImageResource(R.drawable.ic_photo_error);
                }

                if (captionText != null) {
                    String caption = photo.getText();
                    if (caption != null && !caption.isEmpty()) {
                        captionText.setText(caption);
                        captionText.setVisibility(View.VISIBLE);
                    } else {
                        captionText.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    // ViewHolder для граффити
    class GraffitiViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView graffitiLabel;
        ProgressBar progressBar;

        public GraffitiViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.graffitiImage);
            graffitiLabel = itemView.findViewById(R.id.graffitiLabel);
            progressBar = itemView.findViewById(R.id.progressBar);

            imageView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Attachment attachment = attachments.get(position);
                    if (onPhotoClickListener != null) {
                        if (attachment.getPhoto() != null) {
                            onPhotoClickListener.onPhotoClick(attachment.getPhoto(), position);
                        }
                    }
                }
            });
        }

        void bind(Attachment attachment) {
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
            }

            if (graffitiLabel != null) {
                graffitiLabel.setVisibility(View.GONE);
                graffitiLabel.setText("😊 Стикер");
            }

            String imageUrl = null;

            if (attachment.getPhoto() != null) {
                imageUrl = attachment.getPhoto().getBestQualityUrl();
            }

            if ((imageUrl == null || imageUrl.isEmpty()) && attachment.getDoc() != null) {
                imageUrl = attachment.getDoc().getUrl();
            }

            ViewGroup.LayoutParams params = imageView.getLayoutParams();
            int targetSize = (int) (200 * itemView.getContext().getResources().getDisplayMetrics().density);
            params.width = targetSize;
            params.height = targetSize;
            imageView.setLayoutParams(params);

            if (imageUrl != null && !imageUrl.isEmpty()) {
                Picasso.get()
                        .load(imageUrl)
                        .resize(targetSize, targetSize)
                        .centerCrop()
                        .placeholder(R.drawable.ic_sticker_placeholder)
                        .error(R.drawable.ic_sticker_placeholder)
                        .into(imageView, new com.squareup.picasso.Callback() {
                            @Override
                            public void onSuccess() {
                                if (progressBar != null) {
                                    progressBar.setVisibility(View.GONE);
                                }
                            }

                            @Override
                            public void onError(Exception e) {
                                if (progressBar != null) {
                                    progressBar.setVisibility(View.GONE);
                                }
                                imageView.setImageResource(R.drawable.ic_sticker_placeholder);
                            }
                        });
            } else {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                imageView.setImageResource(R.drawable.ic_sticker_placeholder);
            }
        }
    }

    // ViewHolder для документов
    class DocumentViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        TextView titleText;
        TextView sizeText;
        TextView extText;

        public DocumentViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.docIcon);
            titleText = itemView.findViewById(R.id.docTitle);
            sizeText = itemView.findViewById(R.id.docSize);
            extText = itemView.findViewById(R.id.docExt);
        }

        void bind(Attachment.Document doc, int position) {
            if (doc != null) {
                if ("graffiti".equals(doc.getType())) {
                    itemView.setVisibility(View.GONE);
                    return;
                }

                itemView.setVisibility(View.VISIBLE);

                int iconRes = getDocumentIcon(doc.getExt(), doc.getType());
                iconView.setImageResource(iconRes);

                titleText.setText(doc.getTitle());
                sizeText.setText(doc.getFormattedSize());
                extText.setText(doc.getExt().toUpperCase());
            }
        }

        private int getDocumentIcon(String ext, String type) {
            if ("audio_message".equals(type)) {
                return R.drawable.circle_play;
            }

            switch (ext.toLowerCase()) {
                case "pdf":
                    return R.drawable.ic_pdf;
                case "doc":
                case "docx":
                    return R.drawable.ic_word;
                case "xls":
                case "xlsx":
                    return R.drawable.ic_excel;
                case "ppt":
                case "pptx":
                    return R.drawable.ic_powerpoint;
                case "zip":
                case "rar":
                case "7z":
                    return R.drawable.ic_archive;
                case "txt":
                    return R.drawable.ic_text;
                default:
                    return R.drawable.circle_document;
            }
        }
    }

    // ViewHolder для аудио
    class AudioViewHolder extends RecyclerView.ViewHolder {
        ImageView playButton;
        TextView titleText;
        TextView artistText;
        TextView durationText;
        ProgressBar progressBar;
        SeekBar seekBar;

        public AudioViewHolder(@NonNull View itemView) {
            super(itemView);
            playButton = itemView.findViewById(R.id.playIcon);
            titleText = itemView.findViewById(R.id.audioTitle);
            artistText = itemView.findViewById(R.id.audioArtist);
            durationText = itemView.findViewById(R.id.audioDuration);
            progressBar = itemView.findViewById(R.id.audioProgressBar);
            seekBar = itemView.findViewById(R.id.seekBar);

            playButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    // Используем currentMessageAttachments если есть, иначе attachments
                    List<Attachment> attachmentsToUse = currentMessageAttachments != null ?
                            currentMessageAttachments : attachments;
                    audioPlayerHelper.togglePlayPause(position, attachmentsToUse);
                }
            });

            // Обработка перемотки
            if (seekBar != null) {
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && audioPlayerHelper.isPlayingAtPosition(getAdapterPosition())) {
                            // Можно обновить время, но не отправлять в сервис пока не отпустим
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // Пауза при начале перемотки (опционально)
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION && audioPlayerHelper.isPlayingAtPosition(position)) {
                            // Отправляем команду перемотки в сервис
                            // audioPlayerHelper.seekTo(seekBar.getProgress());
                        }
                    }
                });
            }
        }

        void bind(Attachment.Audio audio, int position) {
            if (audio != null) {
                titleText.setText(audio.getTitle());
                artistText.setText(audio.getArtist());
                durationText.setText(audio.getFormattedDuration());

                boolean isPlaying = audioPlayerHelper.isPlayingAtPosition(position);
                updatePlayButton(isPlaying);

                // Обновляем прогресс-бар
                if (progressBar != null) {
                    progressBar.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
                }

                // Скрываем seekBar если это не поддерживается
                if (seekBar != null) {
                    seekBar.setVisibility(View.GONE);
                }
            }
        }

        private void updatePlayButton(boolean isPlaying) {
            playButton.setImageResource(isPlaying ? R.drawable.circle_pause : R.drawable.circle_play);
        }

        // Метод для обновления прогресса воспроизведения
        public void updateProgress(int progress, int duration) {
            if (seekBar != null) {
                seekBar.setMax(duration);
                seekBar.setProgress(progress);
            }

            // Обновляем отображение времени
            if (durationText != null) {
                int currentSeconds = progress / 1000;
                int totalSeconds = duration / 1000;
                String currentTime = formatTime(currentSeconds);
                String totalTime = formatTime(totalSeconds);
                durationText.setText(currentTime + " / " + totalTime);
            }
        }

        private String formatTime(int seconds) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return String.format("%d:%02d", minutes, secs);
        }
    }

    // ViewHolder для голосовых сообщений - ИСПРАВЛЕННЫЙ
    class AudioMessageViewHolder extends RecyclerView.ViewHolder {
        ImageView playButton;
        TextView titleText;
        TextView durationText;
        ProgressBar progressBar;
        SeekBar seekBar;
        TextView currentTimeText;

        public AudioMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            playButton = itemView.findViewById(R.id.btnPlayPause);
            titleText = itemView.findViewById(R.id.audioMessageTitle);
            durationText = itemView.findViewById(R.id.audioMessageDuration);
            progressBar = itemView.findViewById(R.id.audioProgressBar);
            seekBar = itemView.findViewById(R.id.seekBar);
            currentTimeText = itemView.findViewById(R.id.currentTime);

            playButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    // Используем currentMessageAttachments если есть, иначе attachments
                    List<Attachment> attachmentsToUse = currentMessageAttachments != null ?
                            currentMessageAttachments : attachments;
                    audioPlayerHelper.togglePlayPause(position, attachmentsToUse);
                }
            });

            // Обработка перемотки для голосовых сообщений
            if (seekBar != null) {
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && currentTimeText != null) {
                            int seconds = progress / 1000;
                            currentTimeText.setText(formatTime(seconds));
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // Можно поставить на паузу при начале перемотки
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION && audioPlayerHelper.isPlayingAtPosition(position)) {
                            // Отправляем команду перемотки
                            // audioPlayerHelper.seekTo(seekBar.getProgress());
                        }
                    }
                });
            }
        }

        void bind(Attachment.Document audioMessage, int position) {
            if (audioMessage != null) {
                titleText.setText(audioMessage.getTitle());

                int duration = audioMessage.getSize();
                String durationStr = formatTime(duration);
                durationText.setText(durationStr);

                if (currentTimeText != null) {
                    currentTimeText.setText("0:00");
                }

                boolean isPlaying = audioPlayerHelper.isPlayingAtPosition(position);
                updatePlayButton(isPlaying);

                // Обновляем прогресс-бар
                if (progressBar != null) {
                    progressBar.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
                }

                // Настраиваем seekBar
                if (seekBar != null) {
                    seekBar.setMax(duration * 1000); // конвертируем секунды в миллисекунды
                    seekBar.setProgress(0);
                    seekBar.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
                }
            }
        }

        private void updatePlayButton(boolean isPlaying) {
            playButton.setImageResource(isPlaying ? R.drawable.pause : R.drawable.play);
        }

        private String formatTime(int seconds) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return String.format("%d:%02d", minutes, secs);
        }

        // Метод для обновления прогресса воспроизведения
        public void updateProgress(int progress, int duration) {
            if (seekBar != null) {
                seekBar.setMax(duration);
                seekBar.setProgress(progress);
            }

            if (currentTimeText != null) {
                int currentSeconds = progress / 1000;
                currentTimeText.setText(formatTime(currentSeconds));
            }

            // Обновляем общую длительность если она изменилась
            if (durationText != null && duration > 0) {
                int totalSeconds = duration / 1000;
                durationText.setText(formatTime(totalSeconds));
            }
        }
    }

    // ViewHolder для стикеров
    static class StickerViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public StickerViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.stickerImage);
        }

        void bind(Attachment.Photo sticker) {
            if (sticker != null) {
                String imageUrl = sticker.getBestQualityUrl();
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Picasso.get()
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_sticker_placeholder)
                            .into(imageView);
                } else {
                    imageView.setImageResource(R.drawable.ic_sticker_placeholder);
                }
            }
        }
    }
}