package ru.lisdevs.messenger.posts;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.imageview.ShapeableImageView;
import com.squareup.picasso.Picasso;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.lisdevs.messenger.R;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.PostViewHolder> {
    private List<Post> posts;
    private AudioClickListener audioClickListener;
    private Context context;
    private SparseBooleanArray collapsedStates = new SparseBooleanArray();
    private static final int MAX_COLLAPSED_LINES = 5;
    private static final int ANIMATION_DURATION = 200;

    public interface AudioClickListener {
        void onAudioClick(AudioAttachment audio, int position);
        void onPlayPauseClick(AudioAttachment audio, int position);
    }

    public FeedAdapter(List<Post> posts, AudioClickListener listener, Context context) {
        this.posts = posts;
        this.audioClickListener = listener;
        this.context = context;
    }

    @Override
    public PostViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feed, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(PostViewHolder holder, int position) {
        Post post = posts.get(position);

        // Управление видимостью текста поста с обработкой ссылок
        if (post.text != null && !post.text.trim().isEmpty()) {
            holder.postText.setVisibility(View.VISIBLE);

            // Добавляем название группы в начало текста, если есть ссылка
            String fullText = post.text;
            if (post.author instanceof Group && hasGroupLink(post.text, (Group) post.author)) {
                fullText = "[" + post.author.getName() + "]\n" + post.text;
            }

            setTextWithLinks(holder.postText, fullText);
            setupTextExpansion(holder, position, fullText);
        } else {
            holder.postText.setVisibility(View.GONE);
            holder.expandCollapseButton.setVisibility(View.GONE);
        }

        if (post.author != null) {
            holder.authorName.setText(post.author.getName());
            Picasso.get().load(post.author.getPhoto()).into(holder.authorAvatar);
        }

        holder.postDate.setText(formatDate(post.date));
        holder.likeCount.setText(formatCount(post.likesCount));
        holder.commentCount.setText(formatCount(post.commentsCount));
        holder.shareCount.setText(formatCount(post.repostsCount));

        holder.audioContainer.setVisibility(View.GONE);
        holder.postImage.setVisibility(View.GONE);

        if (post.attachments != null) {
            for (Attachment attachment : post.attachments) {
                if (attachment instanceof AudioAttachment) {
                    setupAudioAttachment(holder, (AudioAttachment) attachment, position);
                    holder.audioContainer.setVisibility(View.VISIBLE);
                } else if (attachment instanceof PhotoAttachment) {
                    setupPhotoAttachment(holder, (PhotoAttachment) attachment);
                    holder.postImage.setVisibility(View.VISIBLE);
                }
            }
        }

        holder.menuButton.setOnClickListener(v -> {
            showBottomSheetMenu(post, position);
        });
    }

    // Проверяет, содержит ли текст ссылку на конкретную группу
    private boolean hasGroupLink(String text, Group group) {
        if (text == null || group == null) return false;

        // Паттерн для поиска ссылок на группы ВК
        Pattern groupPattern = Pattern.compile(
                "https?:\\/\\/(?:www\\.)?vk\\.com\\/(?:club|public)" + Math.abs(group.id),
                Pattern.CASE_INSENSITIVE
        );

        return groupPattern.matcher(text).find();
    }

    private void setTextWithLinks(TextView textView, String text) {
        SpannableString spannableString = new SpannableString(text);

        // Обработка URL и названий групп в квадратных скобках
        Pattern urlPattern = Pattern.compile(
                "(https?://)?(www\\.)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/[^\\s]*)?|\\[([^\\]]+)\\]",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = urlPattern.matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String matchedText = text.substring(start, end);

            // Обработка названий групп в формате [GroupName]
            if (matchedText.startsWith("[") && matchedText.endsWith("]")) {
                String groupName = matchedText.substring(1, matchedText.length() - 1);

                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        Toast.makeText(context, "Группа: " + groupName, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setColor(ContextCompat.getColor(context, R.color.group_name_color));
                        ds.setUnderlineText(false);
                        ds.setTypeface(Typeface.DEFAULT_BOLD);
                    }
                };

                spannableString.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // Обработка обычных URL
            else {
                String url = matchedText;
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }

                String finalUrl = url;
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        openUrl(finalUrl);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setColor(ContextCompat.getColor(context, R.color.group_name_color));
                        ds.setUnderlineText(true);
                    }
                };

                spannableString.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        textView.setText(spannableString);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void setupTextExpansion(PostViewHolder holder, int position, String text) {
        holder.postText.post(() -> {
            int lineCount = holder.postText.getLineCount();
            boolean isCollapsible = lineCount > MAX_COLLAPSED_LINES;

            if (isCollapsible) {
                if (collapsedStates.get(position, true)) {
                    holder.postText.setMaxLines(MAX_COLLAPSED_LINES);
                    holder.expandCollapseButton.setVisibility(View.VISIBLE);
                    holder.expandCollapseButton.setText("Показать полностью");
                } else {
                    holder.postText.setMaxLines(Integer.MAX_VALUE);
                    holder.expandCollapseButton.setVisibility(View.VISIBLE);
                    holder.expandCollapseButton.setText("Свернуть");
                }

                holder.expandCollapseButton.setOnClickListener(v -> {
                    boolean isCollapsed = !collapsedStates.get(position, true);
                    collapsedStates.put(position, isCollapsed);

                    TransitionManager.beginDelayedTransition(
                            (ViewGroup) holder.itemView,
                            new AutoTransition().setDuration(ANIMATION_DURATION)
                    );

                    if (isCollapsed) {
                        holder.postText.setMaxLines(MAX_COLLAPSED_LINES);
                        holder.expandCollapseButton.setText("Показать полностью");
                    } else {
                        holder.postText.setMaxLines(Integer.MAX_VALUE);
                        holder.expandCollapseButton.setText("Свернуть");
                    }
                });
            } else {
                holder.expandCollapseButton.setVisibility(View.GONE);
                holder.postText.setMaxLines(Integer.MAX_VALUE);
                collapsedStates.put(position, false);
            }
        });
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupPhotoAttachment(PostViewHolder holder, PhotoAttachment photo) {
        String bestQualityUrl = getBestPhotoUrl(photo.sizes);
        if (bestQualityUrl != null) {
            Picasso.get()
                    .load(bestQualityUrl)
                    .placeholder(R.drawable.img)
                    .error(R.drawable.img)
                    .into(holder.postImage);
        }
    }

    private String getBestPhotoUrl(Map<String, String> sizes) {
        String[] sizePriority = {"w", "z", "y", "x", "m", "s"};
        for (String size : sizePriority) {
            if (sizes.containsKey(size)) {
                return sizes.get(size);
            }
        }
        return null;
    }

    private void setupAudioAttachment(PostViewHolder holder, AudioAttachment audio, int position) {
        holder.audioTitle.setText(audio.title);
        holder.audioArtist.setText(audio.artist);
        holder.audioDuration.setText(formatDuration(audio.duration));

        if (audioClickListener != null) {
            boolean isPlaying = false;
            if (audioClickListener instanceof GroupPostsFragment) {
                isPlaying = ((GroupPostsFragment) audioClickListener).isCurrentPlaying(position, audio.url);
            }

            holder.playPauseButton.setImageResource(
                    isPlaying ? R.drawable.circle_audio : R.drawable.circle_audio);

            holder.playPauseButton.setOnClickListener(v -> {
                audioClickListener.onPlayPauseClick(audio, position);
            });

            holder.audioContainer.setOnClickListener(v -> {
                audioClickListener.onAudioClick(audio, position);
            });
        }
    }

    private void showBottomSheetMenu(Post post, int position) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context, R.style.BottomSheetDialog);
        View bottomSheetView = LayoutInflater.from(context)
                .inflate(R.layout.bottom_sheet_menu, null);

        bottomSheetDialog.setContentView(bottomSheetView);

        TextView shareOption = bottomSheetView.findViewById(R.id.option_share);
        TextView saveOption = bottomSheetView.findViewById(R.id.option_save);
        TextView reportOption = bottomSheetView.findViewById(R.id.option_report);
        TextView copyLinkOption = bottomSheetView.findViewById(R.id.option_copy_link);

        String postLink = "https://vk.com/wall" + post.author.getId() + "_" + post.id;

        copyLinkOption.setVisibility(View.VISIBLE);
        copyLinkOption.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Ссылка на пост", postLink);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        shareOption.setOnClickListener(v -> {
            sharePost(post);
            bottomSheetDialog.dismiss();
        });

        saveOption.setOnClickListener(v -> {
            savePost(post);
            bottomSheetDialog.dismiss();
        });

        reportOption.setOnClickListener(v -> {
            reportPost(post);
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void sharePost(Post post) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, post.text);
        context.startActivity(Intent.createChooser(shareIntent, "Поделиться постом"));
    }

    private void savePost(Post post) {
        Toast.makeText(context, "Пост сохранен", Toast.LENGTH_SHORT).show();
    }

    private void reportPost(Post post) {
        Toast.makeText(context, "Жалоба отправлена", Toast.LENGTH_SHORT).show();
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp * 1000));
    }

    private String formatDuration(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private String formatCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 10000) {
            return String.format(Locale.getDefault(), "%.1fк", count / 1000.0);
        } else {
            return String.format(Locale.getDefault(), "%dк", count / 1000);
        }
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView authorAvatar;
        TextView authorName, postDate, postText;
        TextView likeCount, commentCount, shareCount;
        LinearLayout audioContainer;
        TextView audioTitle, audioArtist, audioDuration;
        ImageButton playPauseButton;
        ShapeableImageView postImage;
        ImageView menuButton;
        Button expandCollapseButton;

        public PostViewHolder(View itemView) {
            super(itemView);
            authorAvatar = itemView.findViewById(R.id.author_avatar);
            authorName = itemView.findViewById(R.id.author_name);
            postDate = itemView.findViewById(R.id.post_date);
            postText = itemView.findViewById(R.id.post_text);
            likeCount = itemView.findViewById(R.id.like_count);
            commentCount = itemView.findViewById(R.id.comment_count);
            shareCount = itemView.findViewById(R.id.share_count);
            audioContainer = itemView.findViewById(R.id.audio_container);
            audioTitle = itemView.findViewById(R.id.audio_title);
            audioArtist = itemView.findViewById(R.id.audio_artist);
            audioDuration = itemView.findViewById(R.id.audio_duration);
            playPauseButton = itemView.findViewById(R.id.play_pause_button);
            postImage = itemView.findViewById(R.id.post_image);
            menuButton = itemView.findViewById(R.id.menu_button);
            expandCollapseButton = itemView.findViewById(R.id.expand_collapse_button);
        }
    }
}