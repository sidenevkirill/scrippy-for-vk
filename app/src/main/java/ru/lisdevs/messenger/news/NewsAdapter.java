package ru.lisdevs.messenger.news;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.NewsPost;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {

    private List<NewsPost> posts;

    public NewsAdapter(List<NewsPost> posts) {
        this.posts = posts;
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view= LayoutInflater.from(parent.getContext()).inflate(R.layout.item_news_post, parent,false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        holder.bind(posts.get(position));
    }

    @Override
    public int getItemCount() { return posts.size(); }

    static class NewsViewHolder extends RecyclerView.ViewHolder {

        TextView tvText, tvDate;

        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText= itemView.findViewById(R.id.textText);
            tvDate= itemView.findViewById(R.id.dateText);
        }

        void bind(NewsPost post){
            tvText.setText(post.getText());
            tvDate.setText(post.getDate());
        }
    }
}