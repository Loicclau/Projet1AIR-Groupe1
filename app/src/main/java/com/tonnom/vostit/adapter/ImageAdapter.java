package com.tonnom.vostit.adapter;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tonnom.vostit.FullScreenImageActivity;
import com.tonnom.vostit.R;
import com.tonnom.vostit.model.NoteImage;

import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {

    private final List<NoteImage> images;

    public ImageAdapter(List<NoteImage> images) {
        this.images = images;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_detail, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NoteImage image = images.get(position);
        if (image.getImagePath() != null) {
            // Utilisation de inSampleSize pour éviter les OutOfMemory tout en gardant une qualité correcte pour l'affichage
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2; 
            Bitmap bitmap = BitmapFactory.decodeFile(image.getImagePath(), options);
            holder.imageView.setImageBitmap(bitmap);
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), FullScreenImageActivity.class);
                intent.putExtra("IMAGE_PATH", image.getImagePath());
                v.getContext().startActivity(intent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ViewHolder(View view) {
            super(view);
            imageView = view.findViewById(R.id.iv_item_detail);
        }
    }
}
