package ru.lisdevs.messenger.messages;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.dialog.DialogActivity;
import ru.lisdevs.messenger.model.Attachment;;

public class AttachmentsFragment extends Fragment {

    private static final String TAG = "AttachmentsFragment";
    private static final String ARG_ATTACHMENTS = "attachments";
    private static final String ARG_USER_NAME = "user_name";

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private List<Attachment> allAttachments;
    private String userName;

    private ProgressBar progressBar;
    private TextView emptyState;

    public static AttachmentsFragment newInstance(List<Attachment> attachments, String userName) {
        AttachmentsFragment fragment = new AttachmentsFragment();
        Bundle args = new Bundle();

        ArrayList<Parcelable> parcelableAttachments = new ArrayList<>();
        for (Attachment attachment : attachments) {
            parcelableAttachments.add(attachment);
        }
        args.putParcelableArrayList(ARG_ATTACHMENTS, parcelableAttachments);
        args.putString(ARG_USER_NAME, userName);

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            List<Parcelable> parcelableList = getArguments().getParcelableArrayList(ARG_ATTACHMENTS);
            allAttachments = new ArrayList<>();
            if (parcelableList != null) {
                for (Parcelable parcelable : parcelableList) {
                    if (parcelable instanceof Attachment) {
                        allAttachments.add((Attachment) parcelable);
                    }
                }
            }
            userName = getArguments().getString(ARG_USER_NAME, "Собеседник");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_attachments_pager, container, false);
        initViews(view);
        setupViewPager();
        return view;
    }

    private void initViews(View view) {
        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);
        progressBar = view.findViewById(R.id.progressBar);
        emptyState = view.findViewById(R.id.emptyState);

        // Настройка тулбара
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        TextView toolbarTitle = view.findViewById(R.id.toolbarTitle);

        if (toolbarTitle != null) {
            toolbarTitle.setText("Вложения");
        }

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> navigateBack());
        }
    }

    private void setupViewPager() {
        if (allAttachments == null || allAttachments.isEmpty()) {
            showEmptyState();
            return;
        }

        showLoading();

        // Группируем вложения по типам
        Map<String, List<Attachment>> groupedAttachments = groupAttachmentsByType(allAttachments);

        // Создаем список фрагментов для ViewPager
        List<Fragment> fragments = new ArrayList<>();
        List<String> tabTitles = new ArrayList<>();

        // Добавляем вкладки в определенном порядке
        String[] sectionOrder = {"photo", "audio", "audio_message", "doc", "video", "sticker", "other"};

        for (String sectionType : sectionOrder) {
            List<Attachment> sectionAttachments = groupedAttachments.get(sectionType);
            if (sectionAttachments != null && !sectionAttachments.isEmpty()) {
                fragments.add(AttachmentTabFragment.newInstance(sectionAttachments, sectionType));
                tabTitles.add(getSectionTitle(sectionType) + " (" + sectionAttachments.size() + ")");
            }
        }

        if (fragments.isEmpty()) {
            showEmptyState();
            return;
        }

        // Настраиваем адаптер ViewPager
        AttachmentsPagerAdapter adapter = new AttachmentsPagerAdapter(this, fragments);
        viewPager.setAdapter(adapter);

        // Настраиваем TabLayout
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(tabTitles.get(position));
        }).attach();

        hideLoading();
        viewPager.setVisibility(View.VISIBLE);
        tabLayout.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
    }

    private Map<String, List<Attachment>> groupAttachmentsByType(List<Attachment> attachments) {
        Map<String, List<Attachment>> grouped = new HashMap<>();

        for (Attachment attachment : attachments) {
            String type = attachment.getType();
            if (type == null) {
                type = "other";
            }

            if (!grouped.containsKey(type)) {
                grouped.put(type, new ArrayList<>());
            }
            grouped.get(type).add(attachment);
        }

        return grouped;
    }

    private String getSectionTitle(String sectionType) {
        switch (sectionType) {
            case "photo":
                return "Фото";
            case "audio":
                return "Аудио";
            case "audio_message":
                return "Голосовые";
            case "doc":
                return "Документы";
            case "sticker":
                return "Стикеры";
            case "video":
                return "Видео";
            case "other":
                return "Прочие";
            default:
                return sectionType;
        }
    }

    private void navigateBack() {
        if (getActivity() == null) return;

        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
            if (getActivity() != null) {
                getActivity().overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            }
        } else {
            if (getActivity() != null) {
                getActivity().finish();
                getActivity().overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            }
        }
    }

    private void showLoading() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        viewPager.setVisibility(View.GONE);
        tabLayout.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void hideLoading() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void showEmptyState() {
        viewPager.setVisibility(View.GONE);
        tabLayout.setVisibility(View.GONE);
        if (emptyState != null) {
            emptyState.setVisibility(View.VISIBLE);
            emptyState.setText("В этом диалоге нет вложений");
        }
        hideLoading();
    }

    // Адаптер для ViewPager
    public static class AttachmentsPagerAdapter extends FragmentStateAdapter {
        private final List<Fragment> fragments;

        public AttachmentsPagerAdapter(@NonNull Fragment fragment, List<Fragment> fragments) {
            super(fragment);
            this.fragments = fragments;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return fragments.get(position);
        }

        @Override
        public int getItemCount() {
            return fragments.size();
        }
    }

    // Фрагмент для отдельной вкладки
    public static class AttachmentTabFragment extends Fragment {

        private static final String ARG_ATTACHMENTS = "attachments";
        private static final String ARG_TYPE = "type";

        private RecyclerView recyclerView;
        private List<Attachment> attachments;
        private String attachmentType;
        private AttachmentTabAdapter adapter;

        public static AttachmentTabFragment newInstance(List<Attachment> attachments, String type) {
            AttachmentTabFragment fragment = new AttachmentTabFragment();
            Bundle args = new Bundle();

            ArrayList<Parcelable> parcelableAttachments = new ArrayList<>();
            for (Attachment attachment : attachments) {
                parcelableAttachments.add(attachment);
            }
            args.putParcelableArrayList(ARG_ATTACHMENTS, parcelableAttachments);
            args.putString(ARG_TYPE, type);

            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            if (getArguments() != null) {
                List<Parcelable> parcelableList = getArguments().getParcelableArrayList(ARG_ATTACHMENTS);
                attachments = new ArrayList<>();
                if (parcelableList != null) {
                    for (Parcelable parcelable : parcelableList) {
                        if (parcelable instanceof Attachment) {
                            attachments.add((Attachment) parcelable);
                        }
                    }
                }
                attachmentType = getArguments().getString(ARG_TYPE, "other");
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_attachment_tab, container, false); // Исправлено название layout
            initViews(view);
            setupRecyclerView();
            return view;
        }

        private void initViews(View view) {
            recyclerView = view.findViewById(R.id.recyclerView);
        }

        private void setupRecyclerView() {
            // Настраиваем LayoutManager в зависимости от типа вложений
            RecyclerView.LayoutManager layoutManager;

            switch (attachmentType) {
                case "photo":
                case "sticker":
                    // Для фото и стикеров используем сетку
                    GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
                    layoutManager = gridLayoutManager;
                    break;
                case "audio":
                case "audio_message":
                case "doc":
                case "video":
                case "other":
                default:
                    // Для остальных - линейный список
                    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
                    layoutManager = linearLayoutManager;
                    break;
            }

            recyclerView.setLayoutManager(layoutManager);

            adapter = new AttachmentTabAdapter(attachments, attachmentType, new AttachmentTabAdapter.OnAttachmentClickListener() {
                @Override
                public void onAttachmentClick(Attachment attachment, int position) {
                    openAttachment(attachment);
                }
            });

            recyclerView.setAdapter(adapter);
        }

        private void openAttachment(Attachment attachment) {
            if (attachment == null) return;

            String type = attachment.getType();
            if (type == null) return;

            switch (type) {
                case "photo":
                    openPhoto(attachment.getPhoto());
                    break;
                case "audio":
                    playAudio(attachment.getAudio());
                    break;
                case "audio_message":
                    playAudioMessage(attachment.getDoc());
                    break;
                case "doc":
                    openDocument(attachment.getDoc());
                    break;
                case "sticker":
                    openSticker(attachment.getPhoto());
                    break;
                case "video":
                    openVideo(attachment);
                    break;
                default:
                    showUnsupportedAttachmentDialog(attachment);
                    break;
            }
        }

        private void openPhoto(Attachment.Photo photo) {
            if (photo == null || photo.getSizes() == null || photo.getSizes().isEmpty()) {
                Toast.makeText(getContext(), "Не удалось открыть фото", Toast.LENGTH_SHORT).show();
                return;
            }

            String bestUrl = photo.getBestQualityUrl();
            if (bestUrl == null || bestUrl.isEmpty()) {
                Toast.makeText(getContext(), "Неверный URL фото", Toast.LENGTH_SHORT).show();
                return;
            }

            // Создаем список из одного фото для просмотрщика
            List<String> photoUrls = new ArrayList<>();
            photoUrls.add(bestUrl);

            // Открываем просмотрщик фото
            if (getActivity() instanceof DialogActivity) {
                ((DialogActivity) getActivity()).showPhotoViewer(photoUrls, 0);
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(bestUrl), "image/*");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Не удалось открыть фото", Toast.LENGTH_SHORT).show();
                }
            }
        }

        private void playAudio(Attachment.Audio audio) {
            if (audio == null || audio.getUrl() == null || audio.getUrl().isEmpty()) {
                Toast.makeText(getContext(), "Не удалось воспроизвести аудио", Toast.LENGTH_SHORT).show();
                return;
            }

            String info = "Исполнитель: " + audio.getArtist() + "\n" +
                    "Название: " + audio.getTitle() + "\n" +
                    "Длительность: " + audio.getFormattedDuration() + "\n\n" +
                    "Воспроизведение через внешний плеер...";

            new AlertDialog.Builder(getContext())
                    .setTitle("🎵 Аудиозапись")
                    .setMessage(info)
                    .setPositiveButton("Воспроизвести", (dialog, which) -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.parse(audio.getUrl()), "audio/*");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(getContext(), "Не удалось воспроизвести аудио", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }

        private void playAudioMessage(Attachment.Document audioMessage) {
            if (audioMessage == null || audioMessage.getUrl() == null || audioMessage.getUrl().isEmpty()) {
                Toast.makeText(getContext(), "Не удалось воспроизвести голосовое сообщение", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(getContext())
                    .setTitle("🎤 Голосовое сообщение")
                    .setMessage("Длительность: " + formatDuration((int) audioMessage.getSize()) + "\n\n" +
                            "Воспроизведение через внешний плеер...")
                    .setPositiveButton("Воспроизвести", (dialog, which) -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.parse(audioMessage.getUrl()), "audio/*");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(getContext(), "Не удалось воспроизвести голосовое сообщение", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }

        private void openDocument(Attachment.Document doc) {
            if (doc == null || doc.getUrl() == null || doc.getUrl().isEmpty()) {
                Toast.makeText(getContext(), "Не удалось открыть документ", Toast.LENGTH_SHORT).show();
                return;
            }

            String info = "Название: " + doc.getTitle() + "\n" +
                    "Тип: " + doc.getExt().toUpperCase() + "\n" +
                    "Размер: " + doc.getFormattedSize() + "\n\n" +
                    "Открытие через внешнее приложение...";

            new AlertDialog.Builder(getContext())
                    .setTitle("📎 Документ")
                    .setMessage(info)
                    .setPositiveButton("Открыть", (dialog, which) -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        String mimeType = getMimeTypeForExtension(doc.getExt());
                        intent.setDataAndType(Uri.parse(doc.getUrl()), mimeType);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            try {
                                intent.setDataAndType(Uri.parse(doc.getUrl()), "*/*");
                                startActivity(intent);
                            } catch (Exception e2) {
                                Toast.makeText(getContext(), "Не удалось открыть документ", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }

        private void openSticker(Attachment.Photo sticker) {
            if (sticker == null) {
                Toast.makeText(getContext(), "Не удалось открыть стикер", Toast.LENGTH_SHORT).show();
                return;
            }
            openPhoto(sticker);
        }

        private void openVideo(Attachment attachment) {
            Toast.makeText(getContext(), "Воспроизведение видео недоступно в этой версии", Toast.LENGTH_SHORT).show();
        }

        private void showUnsupportedAttachmentDialog(Attachment attachment) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Вложение")
                    .setMessage("Тип вложения '" + attachment.getType() + "' не поддерживается для просмотра в этой версии приложения.")
                    .setPositiveButton("OK", null)
                    .show();
        }

        private String formatDuration(int seconds) {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            return String.format("%d:%02d", minutes, remainingSeconds);
        }

        private String getMimeTypeForExtension(String ext) {
            if (ext == null) return "*/*";

            switch (ext.toLowerCase()) {
                case "pdf": return "application/pdf";
                case "doc": case "docx": return "application/msword";
                case "xls": case "xlsx": return "application/vnd.ms-excel";
                case "ppt": case "pptx": return "application/vnd.ms-powerpoint";
                case "txt": return "text/plain";
                case "zip": return "application/zip";
                case "jpg": case "jpeg": return "image/jpeg";
                case "png": return "image/png";
                case "gif": return "image/gif";
                case "mp3": return "audio/mpeg";
                case "mp4": return "video/mp4";
                default: return "*/*";
            }
        }
    }

    // Адаптер для RecyclerView внутри вкладки
    public static class AttachmentTabAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_PHOTO = 0;
        private static final int TYPE_AUDIO = 1;
        private static final int TYPE_DOCUMENT = 2;
        private static final int TYPE_OTHER = 3;

        private List<Attachment> attachments;
        private String attachmentType;
        private OnAttachmentClickListener listener;

        public interface OnAttachmentClickListener {
            void onAttachmentClick(Attachment attachment, int position);
        }

        public AttachmentTabAdapter(List<Attachment> attachments, String attachmentType, OnAttachmentClickListener listener) {
            this.attachments = attachments;
            this.attachmentType = attachmentType;
            this.listener = listener;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            switch (viewType) {
                case TYPE_PHOTO:
                    View photoView = inflater.inflate(R.layout.item_attachment_photo_new, parent, false);
                    return new PhotoViewHolder(photoView);
                case TYPE_AUDIO:
                    View audioView = inflater.inflate(R.layout.item_attachment_audio, parent, false);
                    return new AudioViewHolder(audioView);
                case TYPE_DOCUMENT:
                    View docView = inflater.inflate(R.layout.item_attachment_document, parent, false);
                    return new DocumentViewHolder(docView);
                default:
                    View otherView = inflater.inflate(R.layout.item_attachment_other, parent, false);
                    return new OtherViewHolder(otherView);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Attachment attachment = attachments.get(position);

            switch (holder.getItemViewType()) {
                case TYPE_PHOTO:
                    ((PhotoViewHolder) holder).bind(attachment);
                    break;
                case TYPE_AUDIO:
                    ((AudioViewHolder) holder).bind(attachment);
                    break;
                case TYPE_DOCUMENT:
                    ((DocumentViewHolder) holder).bind(attachment);
                    break;
                case TYPE_OTHER:
                    ((OtherViewHolder) holder).bind(attachment);
                    break;
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAttachmentClick(attachment, position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return attachments.size();
        }

        @Override
        public int getItemViewType(int position) {
            Attachment attachment = attachments.get(position);
            String type = attachment.getType();

            switch (type) {
                case "photo":
                case "sticker":
                    return TYPE_PHOTO;
                case "audio":
                case "audio_message":
                    return TYPE_AUDIO;
                case "doc":
                    return TYPE_DOCUMENT;
                default:
                    return TYPE_OTHER;
            }
        }

        // ViewHolder для фото и стикеров
        class PhotoViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView stickerLabel;

            PhotoViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.attachmentImage);
                stickerLabel = itemView.findViewById(R.id.captionText);
            }

            void bind(Attachment attachment) {
                if (attachment.getPhoto() != null) {
                    String previewUrl = attachment.getPhoto().getPreviewUrl();
                    if (previewUrl != null) {
                        Glide.with(itemView.getContext())
                                .load(previewUrl)
                                .placeholder(R.drawable.img)
                                .into(imageView);
                    }
                }

                // Показываем метку для стикеров
                if ("sticker".equals(attachment.getType()) && stickerLabel != null) {
                    stickerLabel.setVisibility(View.VISIBLE);
                    stickerLabel.setText("Стикер");
                } else if (stickerLabel != null) {
                    stickerLabel.setVisibility(View.GONE);
                }
            }
        }

        // ViewHolder для аудио
        class AudioViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView artist;
            TextView duration;
            ImageView icon;

            AudioViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.audio_title);
                artist = itemView.findViewById(R.id.audio_artist);
                duration = itemView.findViewById(R.id.audio_duration);
                icon = itemView.findViewById(R.id.audio_icon);
            }

            void bind(Attachment attachment) {
                if (attachment.getAudio() != null) {
                    Attachment.Audio audio = attachment.getAudio();
                    if (title != null) title.setText(audio.getTitle());
                    if (artist != null) artist.setText(audio.getArtist());
                    if (duration != null) duration.setText(audio.getFormattedDuration());
                    if (icon != null) icon.setImageResource(R.drawable.ic_audio);
                } else if (attachment.getDoc() != null && "audio_message".equals(attachment.getType())) {
                    // Для голосовых сообщений
                    if (title != null) title.setText("Голосовое сообщение");
                    if (artist != null) artist.setText("Аудио сообщение");
                    if (duration != null) duration.setText(formatDuration((int) attachment.getDoc().getSize()));
                    if (icon != null) icon.setImageResource(R.drawable.microphone);
                }
            }
        }

        // ViewHolder для документов
        class DocumentViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView size;
            TextView extension;
            ImageView icon;

            DocumentViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.docTitle);
                size = itemView.findViewById(R.id.docSize);
                extension = itemView.findViewById(R.id.docExt);
                icon = itemView.findViewById(R.id.docIcon);
            }

            void bind(Attachment attachment) {
                if (attachment.getDoc() != null) {
                    Attachment.Document doc = attachment.getDoc();
                    if (title != null) title.setText(doc.getTitle());
                    if (size != null) size.setText(doc.getFormattedSize());
                    if (extension != null) extension.setText(doc.getExt().toUpperCase());

                    // Устанавливаем иконку в зависимости от типа документа
                    if (icon != null) {
                        int iconRes = getDocumentIcon(doc.getExt());
                        icon.setImageResource(iconRes);
                    }
                }
            }

            private int getDocumentIcon(String ext) {
                if (ext == null) return R.drawable.ic_document;

                switch (ext.toLowerCase()) {
                    case "pdf": return R.drawable.ic_pdf;
                    case "doc": case "docx": return R.drawable.ic_word;
                    case "xls": case "xlsx": return R.drawable.ic_excel;
                    case "ppt": case "pptx": return R.drawable.ic_powerpoint;
                    case "txt": return R.drawable.ic_text;
                    case "zip": case "rar": return R.drawable.ic_archive;
                    default: return R.drawable.ic_document;
                }
            }
        }

        // ViewHolder для других типов - ИСПРАВЛЕННАЯ ВЕРСИЯ
        class OtherViewHolder extends RecyclerView.ViewHolder {
            TextView type;
            TextView title;
            ImageView icon;

            OtherViewHolder(@NonNull View itemView) {
                super(itemView);
                type = itemView.findViewById(R.id.attachment_type);
                title = itemView.findViewById(R.id.attachment_title);
                icon = itemView.findViewById(R.id.attachment_icon);
            }

            void bind(Attachment attachment) {
                if (type != null) {
                    type.setText("Тип: " + attachment.getType());
                }

                if (title != null) {
                    String titleText = getTitleForAttachment(attachment);
                    title.setText(titleText);
                }

                if (icon != null) {
                    int iconRes = getIconForAttachmentType(attachment.getType());
                    icon.setImageResource(iconRes);
                }
            }

            private String getTitleForAttachment(Attachment attachment) {
                switch (attachment.getType()) {
                    case "video":
                        return "Видеозапись";
                    case "link":
                        return "Ссылка";
                    case "poll":
                        return "Опрос";
                    case "wall":
                        return "Запись на стене";
                    case "wall_reply":
                        return "Комментарий на стене";
                    case "gift":
                        return "Подарок";
                    case "market":
                        return "Товар";
                    case "market_album":
                        return "Подборка товаров";
                    default:
                        return "Неизвестное вложение";
                }
            }

            private int getIconForAttachmentType(String type) {
                switch (type) {
                    case "video":
                        return R.drawable.circle_video_ic;
                    case "link":
                        return R.drawable.link_variant;
                    case "poll":
                        return R.drawable.phone_outline;
                    case "wall":
                        return R.drawable.rss;
                    case "wall_reply":
                        return R.drawable.comment_outline;
                    case "gift":
                        return R.drawable.gift_outline;
                    case "market":
                    case "market_album":
                        return R.drawable.call_made;
                    default:
                        return R.drawable.information_outline;
                }
            }
        }

        private static String formatDuration(int seconds) {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            return String.format("%d:%02d", minutes, remainingSeconds);
        }
    }
}