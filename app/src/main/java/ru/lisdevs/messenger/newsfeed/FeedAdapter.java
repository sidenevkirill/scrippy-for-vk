package ru.lisdevs.messenger.newsfeed;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.lisdevs.messenger.R;


public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.PostViewHolder> {

    private List<Post> posts;
    private AudioClickListener audioClickListener;
    private GroupClickListener groupClickListener;
    private PhotoClickListener photoClickListener;
    private PhotoGroupClickListener photoGroupClickListener;
    private Context context;
    private SimpleDateFormat dateFormat;

    public FeedAdapter(List<Post> posts, AudioClickListener audioClickListener, Context context) {
        this.posts = posts != null ? posts : new ArrayList<>();
        this.audioClickListener = audioClickListener;
        this.context = context;
        this.dateFormat = new SimpleDateFormat("dd MMM yyyy в HH:mm", new Locale("ru"));
    }

    public interface AudioClickListener {
        void onAudioClick(AudioAttachment audio, int position);
        void onPlayPauseClick(AudioAttachment audio, int position);
    }

    public interface GroupClickListener {
        void onGroupClick(long groupId, String groupName);
    }

    // Новые интерфейсы для кликов на фото
    public interface PhotoClickListener {
        void onPhotoClick(List<String> photoUrls, int position);
    }

    public interface PhotoGroupClickListener {
        void onPhotoGroupClick(List<String> photoUrls, int position);
    }

    // Сеттеры для обработчиков
    public void setGroupClickListener(GroupClickListener listener) {
        this.groupClickListener = listener;
    }

    public void setPhotoClickListener(PhotoClickListener listener) {
        this.photoClickListener = listener;
    }

    public void setPhotoGroupClickListener(PhotoGroupClickListener listener) {
        this.photoGroupClickListener = listener;
    }

    public interface Author {
        int getId();
        String getName();
        String getPhoto();
        default String getType() { return "user"; }
    }

    public interface Attachment {}

    public static class Post {
        public int id;
        public int sourceId;
        public long date;
        public String text;
        public Author author;
        public int likesCount;
        public int commentsCount;
        public int repostsCount;
        public int viewsCount;
        public List<Attachment> attachments;
        public List<Post> copyHistory;

        public Post() {
            attachments = new ArrayList<>();
            copyHistory = new ArrayList<>();
        }

        public long getGroupId() {
            return sourceId < 0 ? Math.abs(sourceId) : 0;
        }

        public String getGroupName() {
            return author != null ? author.getName() : "";
        }

        public boolean isGroupPost() {
            return sourceId < 0;
        }

        // Метод для получения всех URL фото из поста
        public List<String> getAllPhotoUrls() {
            List<String> urls = new ArrayList<>();
            if (attachments != null) {
                for (Attachment attachment : attachments) {
                    if (attachment instanceof PhotoAttachment) {
                        urls.add(((PhotoAttachment) attachment).url);
                    } else if (attachment instanceof PhotoGroupAttachment) {
                        PhotoGroupAttachment group = (PhotoGroupAttachment) attachment;
                        for (PhotoAttachment photo : group.photos) {
                            urls.add(photo.url);
                        }
                    }
                }
            }
            return urls;
        }
    }

    public static class PhotoAttachment implements Attachment {
        public String url;
        public int width;
        public int height;
        public String text;
    }

    public static class PhotoGroupAttachment implements Attachment {
        public List<PhotoAttachment> photos;

        public PhotoGroupAttachment(List<PhotoAttachment> photos) {
            this.photos = photos;
        }

        // Метод для получения URL всех фото в группе
        public List<String> getPhotoUrls() {
            List<String> urls = new ArrayList<>();
            for (PhotoAttachment photo : photos) {
                urls.add(photo.url);
            }
            return urls;
        }
    }

    public static class AudioAttachment implements Attachment {
        public int id;
        public int ownerId;
        public String artist;
        public String title;
        public int duration;
        public String url;
    }

    public static class VideoAttachment implements Attachment {
        public int id;
        public int ownerId;
        public String title;
        public int duration;
        public int views;
        public String previewUrl;
    }

    public static class LinkAttachment implements Attachment {
        public String url;
        public String title;
        public String description;
        public String previewUrl;
    }

    public static class PlaylistAttachment implements Attachment {
        public int id;
        public int ownerId;
        public String title;
        public String description;
        public int count;
        public String photoUrl;
        public String ownerName;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news_feed, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        holder.bind(post, position);
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    public void updatePosts(List<Post> newPosts) {
        this.posts = newPosts != null ? newPosts : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addPosts(List<Post> newPosts) {
        if (newPosts != null && !newPosts.isEmpty()) {
            int startPosition = posts.size();
            posts.addAll(newPosts);
            notifyItemRangeInserted(startPosition, newPosts.size());
        }
    }

    public void clearPosts() {
        posts.clear();
        notifyDataSetChanged();
    }

    class PostViewHolder extends RecyclerView.ViewHolder {

        private ImageView authorAvatar;
        private TextView authorName;
        private TextView postDate;
        private TextView postText;
        private LinearLayout attachmentsLayout;
        private LinearLayout repostLayout;
        private TextView likesCount;
        private TextView commentsCount;
        private TextView repostsCount;
        private TextView viewsCount;
        private ImageButton likeButton;
        private ImageButton commentButton;
        private ImageButton repostButton;
        private ProgressBar audioProgress;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);

            authorAvatar = itemView.findViewById(R.id.author_avatar);
            authorName = itemView.findViewById(R.id.author_name);
            postDate = itemView.findViewById(R.id.post_date);
            postText = itemView.findViewById(R.id.post_text);
            attachmentsLayout = itemView.findViewById(R.id.attachments_layout);
            repostLayout = itemView.findViewById(R.id.repost_layout);
            likesCount = itemView.findViewById(R.id.likes_count);
            commentsCount = itemView.findViewById(R.id.comments_count);
            repostsCount = itemView.findViewById(R.id.reposts_count);
            viewsCount = itemView.findViewById(R.id.views_count);
            likeButton = itemView.findViewById(R.id.like_button);
            commentButton = itemView.findViewById(R.id.comment_button);
            repostButton = itemView.findViewById(R.id.repost_button);
            audioProgress = itemView.findViewById(R.id.audio_progress);
        }

        public void bind(Post post, int position) {
            // Автор поста
            if (post.author != null) {
                Glide.with(context)
                        .load(post.author.getPhoto())
                        .circleCrop()
                        .into(authorAvatar);

                authorName.setText(post.author.getName());
            }

            // Дата поста
            if (post.date > 0) {
                String dateString = dateFormat.format(new Date(post.date * 1000));
                postDate.setText(dateString);
            }

            // Текст поста
            if (!TextUtils.isEmpty(post.text)) {
                postText.setVisibility(View.VISIBLE);
                postText.setText(post.text);
            } else {
                postText.setVisibility(View.GONE);
            }

            // Обработчики кликов на группу
            authorAvatar.setOnClickListener(v -> {
                if (post.isGroupPost() && groupClickListener != null) {
                    groupClickListener.onGroupClick(post.getGroupId(), post.getGroupName());
                }
            });

            authorName.setOnClickListener(v -> {
                if (post.isGroupPost() && groupClickListener != null) {
                    groupClickListener.onGroupClick(post.getGroupId(), post.getGroupName());
                }
            });

            // Визуальная обратная связь для кликабельных элементов
            if (post.isGroupPost()) {
                authorAvatar.setClickable(true);
                authorName.setClickable(true);
                authorName.setTextColor(ContextCompat.getColor(context, R.color.black));
            } else {
                authorAvatar.setClickable(false);
                authorName.setClickable(false);
                authorName.setTextColor(ContextCompat.getColor(context, R.color.black));
            }

            // Вложения
            attachmentsLayout.removeAllViews();
            if (post.attachments != null && !post.attachments.isEmpty()) {
                for (Attachment attachment : post.attachments) {
                    View attachmentView = createAttachmentView(attachment, position, post);
                    if (attachmentView != null) {
                        attachmentsLayout.addView(attachmentView);
                    }
                }
                attachmentsLayout.setVisibility(View.VISIBLE);
            } else {
                attachmentsLayout.setVisibility(View.GONE);
            }

            // Репосты
            repostLayout.removeAllViews();
            if (post.copyHistory != null && !post.copyHistory.isEmpty()) {
                for (Post repost : post.copyHistory) {
                    View repostView = createRepostView(repost);
                    if (repostView != null) {
                        repostLayout.addView(repostView);
                    }
                }
                repostLayout.setVisibility(View.VISIBLE);
            } else {
                repostLayout.setVisibility(View.GONE);
            }

            // Статистика
            likesCount.setText(formatCount(post.likesCount));
            commentsCount.setText(formatCount(post.commentsCount));
            repostsCount.setText(formatCount(post.repostsCount));

            if (post.viewsCount > 0) {
                viewsCount.setVisibility(View.VISIBLE);
                viewsCount.setText(formatViews(post.viewsCount));
            } else {
                viewsCount.setVisibility(View.GONE);
            }

            // Кнопки действий
            likeButton.setSelected(post.likesCount > 0);
            likeButton.setOnClickListener(v -> toggleLike(post, position));

            commentButton.setOnClickListener(v -> openComments(post));
            repostButton.setOnClickListener(v -> repost(post));
        }

        private View createAttachmentView(Attachment attachment, int position, Post post) {
            if (attachment instanceof PhotoAttachment) {
                return createSinglePhotoView((PhotoAttachment) attachment, position, post);
            } else if (attachment instanceof PhotoGroupAttachment) {
                PhotoGroupAttachment group = (PhotoGroupAttachment) attachment;
                if (group.photos.size() == 1) {
                    return createSinglePhotoView(group.photos.get(0), position, post);
                } else {
                    return createPhotoGroupView(group, position, post);
                }
            } else if (attachment instanceof AudioAttachment) {
                return createAudioView((AudioAttachment) attachment, position);
            } else if (attachment instanceof VideoAttachment) {
                return createVideoView((VideoAttachment) attachment);
            } else if (attachment instanceof LinkAttachment) {
                return createLinkView((LinkAttachment) attachment);
            } else if (attachment instanceof PlaylistAttachment) {
                return createPlaylistView((PlaylistAttachment) attachment);
            }
            return null;
        }

        private View createSinglePhotoView(PhotoAttachment photo, int position, Post post) {
            View view = LayoutInflater.from(context)
                    .inflate(R.layout.item_single_photo_attachment, attachmentsLayout, false);

            ImageView photoView = view.findViewById(R.id.photo_image);
            TextView photoText = view.findViewById(R.id.photo_text);

            Glide.with(context)
                    .load(photo.url)
                    .into(photoView);

            if (!TextUtils.isEmpty(photo.text)) {
                photoText.setVisibility(View.VISIBLE);
                photoText.setText(photo.text);
            } else {
                photoText.setVisibility(View.GONE);
            }

            // Обработчик клика на одиночное фото
            photoView.setOnClickListener(v -> {
                if (photoClickListener != null) {
                    // Получаем все фото из поста для просмотра в галерее
                    List<String> allPhotoUrls = post.getAllPhotoUrls();
                    if (!allPhotoUrls.isEmpty()) {
                        // Находим индекс текущего фото в общем списке
                        int photoIndex = allPhotoUrls.indexOf(photo.url);
                        if (photoIndex == -1) photoIndex = 0;
                        photoClickListener.onPhotoClick(allPhotoUrls, photoIndex);
                    }
                }
            });

            return view;
        }

        private View createPhotoGroupView(PhotoGroupAttachment photoGroup, int position, Post post) {
            View view = LayoutInflater.from(context)
                    .inflate(R.layout.item_photo_group_attachment, attachmentsLayout, false);

            ViewPager2 viewPager = view.findViewById(R.id.photo_view_pager);
            TextView pageIndicator = view.findViewById(R.id.page_indicator);
            TextView groupText = view.findViewById(R.id.group_text);

            // Настройка ViewPager2
            PhotoPagerAdapter pagerAdapter = new PhotoPagerAdapter(photoGroup.photos);
            viewPager.setAdapter(pagerAdapter);

            // Индикатор страниц
            if (photoGroup.photos.size() > 1) {
                pageIndicator.setVisibility(View.VISIBLE);
                pageIndicator.setText("1/" + photoGroup.photos.size());

                viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        super.onPageSelected(position);
                        pageIndicator.setText((position + 1) + "/" + photoGroup.photos.size());
                    }
                });
            } else {
                pageIndicator.setVisibility(View.GONE);
            }

            // Текст для группы фото
            if (!photoGroup.photos.isEmpty() && !TextUtils.isEmpty(photoGroup.photos.get(0).text)) {
                groupText.setVisibility(View.VISIBLE);
                groupText.setText(photoGroup.photos.get(0).text);
            } else {
                groupText.setVisibility(View.GONE);
            }

            // Обработчик клика на группу фото
            viewPager.setOnTouchListener(new View.OnTouchListener() {
                private GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        if (photoGroupClickListener != null) {
                            List<String> photoUrls = photoGroup.getPhotoUrls();
                            int currentItem = viewPager.getCurrentItem();
                            photoGroupClickListener.onPhotoGroupClick(photoUrls, currentItem);
                        }
                        return true;
                    }
                });

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    gestureDetector.onTouchEvent(event);
                    return false;
                }
            });

            return view;
        }

        // Остальные методы createAudioView, createVideoView и т.д. остаются без изменений
        private View createAudioView(AudioAttachment audio, int position) {
            View view = LayoutInflater.from(context)
                    .inflate(R.layout.item_audio_attachment, attachmentsLayout, false);

            TextView audioTitle = view.findViewById(R.id.audio_title);
            TextView audioArtist = view.findViewById(R.id.audio_artist);
            TextView audioDuration = view.findViewById(R.id.audio_duration);
            ImageButton playButton = view.findViewById(R.id.play_button);
            ProgressBar progressBar = view.findViewById(R.id.audio_progress);

            String title = !TextUtils.isEmpty(audio.title) ? audio.title : "Без названия";
            String artist = !TextUtils.isEmpty(audio.artist) ? audio.artist : "Неизвестный исполнитель";

            audioTitle.setText(title);
            audioArtist.setText(artist);

            int minutes = audio.duration / 60;
            int seconds = audio.duration % 60;
            String durationText = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
            audioDuration.setText(durationText);

            boolean isPlaying = false;
            if (audioClickListener instanceof NewsFeedFragment) {
                NewsFeedFragment fragment = (NewsFeedFragment) audioClickListener;
                isPlaying = fragment.isCurrentPlaying(position, audio.url);
            }

            playButton.setSelected(isPlaying);
            progressBar.setVisibility(isPlaying ? View.VISIBLE : View.GONE);

            playButton.setOnClickListener(v -> {
                if (audioClickListener != null) {
                    audioClickListener.onPlayPauseClick(audio, position);
                }
            });

            view.setOnClickListener(v -> {
                if (audioClickListener != null) {
                    audioClickListener.onAudioClick(audio, position);
                }
            });

            return view;
        }

        private View createVideoView(VideoAttachment video) {
            View view = LayoutInflater.from(context)
                    .inflate(R.layout.item_video_attachment, attachmentsLayout, false);

            ImageView videoPreview = view.findViewById(R.id.video_preview);
            TextView videoTitle = view.findViewById(R.id.video_title);
            TextView videoDuration = view.findViewById(R.id.video_duration);
            TextView videoViews = view.findViewById(R.id.video_views);

            if (!TextUtils.isEmpty(video.previewUrl)) {
                Glide.with(context)
                        .load(video.previewUrl)
                        .into(videoPreview);
            }

            videoTitle.setText(!TextUtils.isEmpty(video.title) ? video.title : "Видео");

            if (video.duration > 0) {
                int minutes = video.duration / 60;
                int seconds = video.duration % 60;
                String durationText = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
                videoDuration.setText(durationText);
                videoDuration.setVisibility(View.VISIBLE);
            } else {
                videoDuration.setVisibility(View.GONE);
            }

            if (video.views > 0) {
                videoViews.setText(formatViews(video.views));
                videoViews.setVisibility(View.VISIBLE);
            } else {
                videoViews.setVisibility(View.GONE);
            }

            return view;
        }

        private View createLinkView(LinkAttachment link) {
            View view = LayoutInflater.from(context)
                    .inflate(R.layout.item_link_attachment, attachmentsLayout, false);

            ImageView linkPreview = view.findViewById(R.id.link_preview);
            TextView linkTitle = view.findViewById(R.id.link_title);
            TextView linkDescription = view.findViewById(R.id.link_description);
            TextView linkUrl = view.findViewById(R.id.link_url);

            if (!TextUtils.isEmpty(link.previewUrl)) {
                Glide.with(context)
                        .load(link.previewUrl)
                        .into(linkPreview);
            }

            linkTitle.setText(!TextUtils.isEmpty(link.title) ? link.title : "Ссылка");

            if (!TextUtils.isEmpty(link.description)) {
                linkDescription.setVisibility(View.VISIBLE);
                linkDescription.setText(link.description);
            } else {
                linkDescription.setVisibility(View.GONE);
            }

            if (!TextUtils.isEmpty(link.url)) {
                linkUrl.setVisibility(View.VISIBLE);
                linkUrl.setText(link.url);
            } else {
                linkUrl.setVisibility(View.GONE);
            }

            return view;
        }

        private View createPlaylistView(PlaylistAttachment playlist) {
            View view = LayoutInflater.from(context)
                    .inflate(R.layout.item_playlist_attachment, attachmentsLayout, false);

            ImageView playlistImage = view.findViewById(R.id.playlist_image);
            TextView playlistTitle = view.findViewById(R.id.playlist_title);
            TextView playlistOwner = view.findViewById(R.id.playlist_owner);
            TextView playlistCount = view.findViewById(R.id.playlist_count);
            TextView playlistDescription = view.findViewById(R.id.playlist_description);

            if (!TextUtils.isEmpty(playlist.photoUrl)) {
                Glide.with(context)
                        .load(playlist.photoUrl)
                        .into(playlistImage);
            } else {
                playlistImage.setImageResource(R.drawable.circle_playlist);
            }

            playlistTitle.setText(!TextUtils.isEmpty(playlist.title) ? playlist.title : "Плейлист");

            if (!TextUtils.isEmpty(playlist.ownerName)) {
                playlistOwner.setVisibility(View.VISIBLE);
                playlistOwner.setText(playlist.ownerName);
            } else {
                playlistOwner.setVisibility(View.GONE);
            }

            if (playlist.count > 0) {
                playlistCount.setVisibility(View.VISIBLE);
                String countText = playlist.count + " " + getTrackWord(playlist.count);
                playlistCount.setText(countText);
            } else {
                playlistCount.setVisibility(View.GONE);
            }

            if (!TextUtils.isEmpty(playlist.description)) {
                playlistDescription.setVisibility(View.VISIBLE);
                playlistDescription.setText(playlist.description);
            } else {
                playlistDescription.setVisibility(View.GONE);
            }

            view.setOnClickListener(v -> openPlaylist(playlist));

            return view;
        }

        private View createRepostView(Post repost) {
            View view = LayoutInflater.from(context)
                    .inflate(R.layout.item_repost, repostLayout, false);

            TextView repostAuthor = view.findViewById(R.id.repost_author);
            TextView repostText = view.findViewById(R.id.repost_text);
            LinearLayout repostAttachments = view.findViewById(R.id.repost_attachments);

            if (repost.author != null) {
                String authorText = "Репост: " + repost.author.getName();
                SpannableString spannable = new SpannableString(authorText);
                spannable.setSpan(
                        new ForegroundColorSpan(ContextCompat.getColor(context, R.color.circle_video)),
                        0,
                        7,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                repostAuthor.setText(spannable);
            }

            if (!TextUtils.isEmpty(repost.text)) {
                repostText.setVisibility(View.VISIBLE);
                repostText.setText(repost.text);
            } else {
                repostText.setVisibility(View.GONE);
            }

            repostAttachments.removeAllViews();
            if (repost.attachments != null && !repost.attachments.isEmpty()) {
                for (Attachment attachment : repost.attachments) {
                    View attachmentView = createAttachmentView(attachment, -1, repost);
                    if (attachmentView != null) {
                        repostAttachments.addView(attachmentView);
                    }
                }
                repostAttachments.setVisibility(View.VISIBLE);
            } else {
                repostAttachments.setVisibility(View.GONE);
            }

            return view;
        }

        private String getTrackWord(int count) {
            if (count % 10 == 1 && count % 100 != 11) {
                return "трек";
            } else if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20)) {
                return "трека";
            } else {
                return "треков";
            }
        }

        private String formatCount(int count) {
            if (count == 0) return "";
            if (count < 1000) return String.valueOf(count);
            if (count < 1000000) return String.format(Locale.getDefault(), "%.1fK", count / 1000.0);
            return String.format(Locale.getDefault(), "%.1fM", count / 1000000.0);
        }

        private String formatViews(int views) {
            if (views < 1000) return views + "";
            if (views < 1000000) return String.format(Locale.getDefault(), "%.1fK", views / 1000.0);
            return String.format(Locale.getDefault(), "%.1fM", views / 1000000.0);
        }

        private void toggleLike(Post post, int position) {
            boolean isLiked = !likeButton.isSelected();
            likeButton.setSelected(isLiked);

            if (isLiked) {
                post.likesCount++;
            } else {
                post.likesCount--;
            }

            likesCount.setText(formatCount(post.likesCount));
        }

        private void openComments(Post post) {
            Toast.makeText(context, "Открытие комментариев", Toast.LENGTH_SHORT).show();
        }

        private void repost(Post post) {
            Toast.makeText(context, "Репост записи", Toast.LENGTH_SHORT).show();
        }

        private void openPlaylist(PlaylistAttachment playlist) {
            Toast.makeText(context, "Открытие плейлиста: " + playlist.title, Toast.LENGTH_SHORT).show();
        }
    }

    public void updateAudioState(int position, boolean isPlaying) {
        if (position >= 0 && position < posts.size()) {
            notifyItemChanged(position);
        }
    }

    public void updateAudioProgress(int position, int progress) {
        if (position >= 0 && position < posts.size()) {
            PostViewHolder holder = (PostViewHolder) ((RecyclerView)
                    ((NewsFeedFragment) audioClickListener).getView()
                            .findViewById(R.id.recyclerView))
                    .findViewHolderForAdapterPosition(position);

            if (holder != null && holder.audioProgress != null) {
                holder.audioProgress.setProgress(progress);
            }
        }
    }
}