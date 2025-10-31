package ru.lisdevs.messenger.messages;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Attachment;


public class AttachmentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_PHOTO = 0;
    private static final int TYPE_DOCUMENT = 1;
    private static final int TYPE_AUDIO = 2;
    private static final int TYPE_AUDIO_MESSAGE = 3;
    private static final int TYPE_STICKER = 4;

    private List<Attachment> attachments = new ArrayList<>();
    private OnPhotoClickListener onPhotoClickListener;

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
        switch (attachment.getType()) {
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
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

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
                ((PhotoViewHolder) holder).bind(attachment.getPhoto());
                break;
            case TYPE_DOCUMENT:
                ((DocumentViewHolder) holder).bind(attachment.getDoc());
                break;
            case TYPE_AUDIO:
                ((AudioViewHolder) holder).bind(attachment.getAudio());
                break;
            case TYPE_AUDIO_MESSAGE:
                ((AudioMessageViewHolder) holder).bind(attachment.getDoc());
                break;
            case TYPE_STICKER:
                ((StickerViewHolder) holder).bind(attachment.getPhoto());
                break;
        }
    }

    @Override
    public int getItemCount() {
        return attachments.size();
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

        void bind(Attachment.Photo photo) {
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

    // ViewHolder для документов
    static class DocumentViewHolder extends RecyclerView.ViewHolder {
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

        void bind(Attachment.Document doc) {
            if (doc != null) {
                // Устанавливаем иконку в зависимости от типа документа
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
    static class AudioViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        TextView titleText;
        TextView artistText;
        TextView durationText;

        public AudioViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.audioIcon);
            titleText = itemView.findViewById(R.id.audioTitle);
            artistText = itemView.findViewById(R.id.audioArtist);
            durationText = itemView.findViewById(R.id.audioDuration);
        }

        void bind(Attachment.Audio audio) {
            if (audio != null) {
                iconView.setImageResource(R.drawable.ic_audio);
                titleText.setText(audio.getTitle());
                artistText.setText(audio.getArtist());
                durationText.setText(audio.getFormattedDuration());
            }
        }
    }

    // ViewHolder для голосовых сообщений
    static class AudioMessageViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        TextView titleText;
        TextView durationText;

        public AudioMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.audioMessageIcon);
            titleText = itemView.findViewById(R.id.audioMessageTitle);
            durationText = itemView.findViewById(R.id.audioMessageDuration);
        }

        void bind(Attachment.Document audioMessage) {
            if (audioMessage != null) {
                iconView.setImageResource(R.drawable.circle_play);
                titleText.setText(audioMessage.getTitle());

                // Для голосовых сообщений размер - это длительность в секундах
                int duration = audioMessage.getSize();
                int minutes = duration / 60;
                int seconds = duration % 60;
                String durationStr = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
                durationText.setText(durationStr);
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