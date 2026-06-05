package com.tonnom.vostit.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.tonnom.vostit.FullScreenImageActivity;
import com.tonnom.vostit.R;

import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {

    private final List<String> imagePaths;
    private final OnImageLongClickListener longClickListener;

    public interface OnImageLongClickListener {
        void onImageLongClick(String path);
    }

    public ImageAdapter(List<String> imagePaths, OnImageLongClickListener longClickListener) {
        this.imagePaths = imagePaths;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_detail, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String path = imagePaths.get(position);
        
        // Glide gère intelligemment les fichiers locaux et les URLs distantes
        Glide.with(holder.imageView.getContext())
                .load(path)
                .placeholder(R.drawable.ic_launcher_foreground) // Placeholder par défaut
                .centerCrop()
                .into(holder.imageView);
            
        holder.itemView.setOnClickListener(v -> {
            android.app.Activity activity = (android.app.Activity) v.getContext();
            Intent intent = new Intent(activity, FullScreenImageActivity.class);
            intent.putExtra("IMAGE_PATH", path);
            activity.startActivity(intent);
            // Animation fluide d'entrée
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        if (longClickListener != null) {
            holder.itemView.setOnLongClickListener(v -> {
                longClickListener.onImageLongClick(path);
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return imagePaths.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ViewHolder(View view) {
            super(view);
            imageView = view.findViewById(R.id.iv_item_detail);
        }
    }
}
