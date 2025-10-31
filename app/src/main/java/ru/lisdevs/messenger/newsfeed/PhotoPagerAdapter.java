package ru.lisdevs.messenger.newsfeed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

import ru.lisdevs.messenger.R;

public class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.PhotoViewHolder> {

    private List<FeedAdapter.PhotoAttachment> photos;

    public PhotoPagerAdapter(List<FeedAdapter.PhotoAttachment> photos) {
        this.photos = photos;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo_pager, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        FeedAdapter.PhotoAttachment photo = photos.get(position);
        holder.bind(photo);
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView photoView;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            photoView = itemView.findViewById(R.id.photo_image);
        }

        void bind(FeedAdapter.PhotoAttachment photo) {
            Glide.with(itemView.getContext())
                    .load(photo.url)
                    .into(photoView);

            // Устанавливаем соотношение сторон
            if (photo.width > 0 && photo.height > 0) {
                ViewGroup.LayoutParams params = photoView.getLayoutParams();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                photoView.setLayoutParams(params);
                photoView.setAdjustViewBounds(true);
            }
        }
    }
}