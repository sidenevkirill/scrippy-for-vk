package ru.lisdevs.messenger.documents;


import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Document;


public class DocumentsAdapter extends RecyclerView.Adapter<DocumentsAdapter.DocumentViewHolder> {

    private List<Document> documents;
    private OnDocumentClickListener listener;
    private OnImageClickListener imageClickListener;

    public interface OnDocumentClickListener {
        void onDocumentClick(Document document);
        void onDocumentDownload(Document document);
        void onDocumentShare(Document document);
        void onDocumentLongClick(Document document);
    }

    // Новый интерфейс для кликов на изображения
    public interface OnImageClickListener {
        void onImageClick(Document document);
    }

    public DocumentsAdapter(List<Document> documents, OnDocumentClickListener listener) {
        this.documents = documents;
        this.listener = listener;
    }

    // Сеттер для обработчика кликов на изображения
    public void setImageClickListener(OnImageClickListener listener) {
        this.imageClickListener = listener;
    }

    @NonNull
    @Override
    public DocumentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_document, parent, false);
        return new DocumentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DocumentViewHolder holder, int position) {
        Document document = documents.get(position);
        holder.bind(document, listener, imageClickListener);
    }

    @Override
    public int getItemCount() {
        return documents.size();
    }

    public void updateDocuments(List<Document> newDocuments) {
        documents.clear();
        documents.addAll(newDocuments);
        notifyDataSetChanged();
    }

    public void addDocument(Document document) {
        documents.add(document);
        notifyItemInserted(documents.size() - 1);
    }

    public void removeDocument(int position) {
        if (position >= 0 && position < documents.size()) {
            documents.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void clearDocuments() {
        documents.clear();
        notifyDataSetChanged();
    }

    public Document getDocument(int position) {
        if (position >= 0 && position < documents.size()) {
            return documents.get(position);
        }
        return null;
    }

    class DocumentViewHolder extends RecyclerView.ViewHolder {
        private ImageView icon;
        private TextView title;
        private TextView type;
        private TextView size;
        private TextView date;
        private ImageView downloadButton;
        private ImageView shareButton;
        private View divider;
        private ProgressBar downloadProgress;

        public DocumentViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.document_icon);
            title = itemView.findViewById(R.id.document_title);
            type = itemView.findViewById(R.id.document_type);
            size = itemView.findViewById(R.id.document_size);
            date = itemView.findViewById(R.id.document_date);
            downloadButton = itemView.findViewById(R.id.download_button);
            shareButton = itemView.findViewById(R.id.share_button);
            downloadProgress = itemView.findViewById(R.id.download_progress);
        }

        public void bind(Document document, OnDocumentClickListener documentListener, OnImageClickListener imageListener) {
            // Устанавливаем данные документа
            title.setText(document.getTitle());
            type.setText(document.getType());
            size.setText(document.getSize());

            // Форматируем дату
            String formattedDate = formatDate(document.getDate());
            date.setText(formattedDate);

            // Устанавливаем иконку в зависимости от типа файла
            setDocumentIcon(document.getExtension(), document.getType());

            // Обработчики кликов
            itemView.setOnClickListener(v -> {
                if (documentListener != null) {
                    documentListener.onDocumentClick(document);
                }
            });

            // Обработчик клика на иконку для изображений
            icon.setOnClickListener(v -> {
                if (isImageDocument(document)) {
                    // Если это изображение, открываем просмотр
                    if (imageListener != null) {
                        imageListener.onImageClick(document);
                    }
                } else {
                    // Если не изображение, открываем обычный диалог
                    if (documentListener != null) {
                        documentListener.onDocumentClick(document);
                    }
                }
            });

            downloadButton.setOnLongClickListener(v -> {
                if (documentListener != null) {
                    documentListener.onDocumentLongClick(document);
                    return true;
                }
                return false;
            });

            shareButton.setOnClickListener(v -> {
                if (documentListener != null) {
                    documentListener.onDocumentShare(document);
                }
            });
        }

        // Проверяем, является ли документ изображением
        private boolean isImageDocument(Document document) {
            if (document.getExtension() == null) return false;

            String ext = document.getExtension().toLowerCase();
            return ext.equals("jpg") || ext.equals("jpeg") ||
                    ext.equals("png") || ext.equals("gif") ||
                    ext.equals("bmp") || ext.equals("webp");
        }

        private String formatDate(long timestamp) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                return sdf.format(timestamp * 1000);
            } catch (Exception e) {
                return "Неизвестно";
            }
        }

        private void setDocumentIcon(String extension, String type) {
            int iconRes = R.drawable.ic_document;

            if (extension != null) {
                switch (extension.toLowerCase()) {
                    case "pdf":
                        iconRes = R.drawable.ic_document;
                        break;
                    case "doc":
                    case "docx":
                        iconRes = R.drawable.ic_document;
                        break;
                    case "xls":
                    case "xlsx":
                        iconRes = R.drawable.ic_document;
                        break;
                    case "ppt":
                    case "pptx":
                        iconRes = R.drawable.ic_document;
                        break;
                    case "txt":
                        iconRes = R.drawable.ic_document;
                        break;
                    case "zip":
                    case "rar":
                    case "7z":
                        iconRes = R.drawable.ic_document;
                        break;
                    case "jpg":
                    case "apk":
                        iconRes = R.drawable.apk_document_24px;
                        break;
                    case "jpeg":
                    case "png":
                        iconRes = R.drawable.image_white;
                        break;
                    case "gif":
                    case "bmp":
                        iconRes = R.drawable.image;
                        break;
                    case "mp3":
                    case "wav":
                    case "flac":
                        iconRes = R.drawable.video_outline;
                        break;
                    case "mp4":
                    case "avi":
                    case "mkv":
                        iconRes = R.drawable.video_outline;
                        break;
                    default:
                        if (type != null) {
                            if (type.toLowerCase().contains("текст") || type.toLowerCase().contains("text")) {
                                iconRes = R.drawable.ic_document;
                            } else if (type.toLowerCase().contains("архив") || type.toLowerCase().contains("archive")) {
                                iconRes = R.drawable.ic_document;
                            } else if (type.toLowerCase().contains("изображение") || type.toLowerCase().contains("image")) {
                                iconRes = R.drawable.image;
                            } else if (type.toLowerCase().contains("аудио") || type.toLowerCase().contains("audio")) {
                                iconRes = R.drawable.music_note_outline_white;
                            } else if (type.toLowerCase().contains("видео") || type.toLowerCase().contains("video")) {
                                iconRes = R.drawable.video_outline;
                            }
                        }
                        break;
                }
            }

            icon.setImageResource(iconRes);

            try {
                int backgroundColor = getBackgroundColorForType(extension);
                icon.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), backgroundColor));
            } catch (Exception e) {
                icon.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.document_icon_bg_default));
            }
        }

        private int getBackgroundColorForType(String extension) {
            if (extension == null) {
                return R.color.document_icon_bg_default;
            }

            switch (extension.toLowerCase()) {
                case "pdf":
                    return R.color.document_icon_bg_pdf;
                case "apk":
                    return R.color.document_icon_bg_apk;
                case "doc":
                case "docx":
                    return R.color.document_icon_bg_word;
                case "xls":
                case "xlsx":
                    return R.color.document_icon_bg_excel;
                case "ppt":
                case "pptx":
                    return R.color.document_icon_bg_powerpoint;
                case "zip":
                case "rar":
                case "7z":
                    return R.color.document_icon_bg_archive;
                case "jpg":
                    return R.color.document_icon_bg_image_blue;
                case "jpeg":
                case "png":
                case "gif":
                case "bmp":
                    return R.color.document_icon_bg_image;
                case "mp3":
                case "wav":
                case "flac":
                    return R.color.document_icon_bg_audio;
                case "mp4":
                case "avi":
                case "mkv":
                    return R.color.document_icon_bg_video;
                default:
                    return R.color.document_icon_bg_default;
            }
        }
    }
}