package ru.lisdevs.messenger.news;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

import ru.lisdevs.messenger.R;

public class PostsAdapter extends RecyclerView.Adapter<PostsAdapter.PostViewHolder> {

    private List<PostItem> posts;
    private Context context;

    public PostsAdapter(List<PostItem> posts) {
        this.posts = posts;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        PostItem post = posts.get(position);
        holder.textPost.setText(post.getText());
        holder.datePost.setText(post.getDate());

        holder.groupName.setText(post.getGroupName() != null ? post.getGroupName() : "Ваш пост");

        // Предположим, что у каждого поста есть поле coverImageUrl (или подобное)
        String coverImageUrl = post.getCoverImageUrl();

        if (coverImageUrl != null && !coverImageUrl.isEmpty()) {
            holder.imageCover.setVisibility(View.VISIBLE);
            // Используем Glide для загрузки изображения
            Glide.with(context)
                    .load(coverImageUrl)
                    .placeholder(R.drawable.ic_launcher_background) // ваш placeholder
                    .error(R.drawable.ic_launcher_background) // изображение при ошибке
                    .into(holder.imageCover);
        } else {
            holder.imageCover.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        TextView textPost;
        TextView datePost;
        ShapeableImageView imageCover;
        TextView groupName;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            groupName= itemView.findViewById(R.id.author_name);
            textPost= itemView.findViewById(R.id.post_text);
            datePost= itemView.findViewById(R.id.post_date);
            imageCover= itemView.findViewById(R.id.imageCover);
        }
    }
}