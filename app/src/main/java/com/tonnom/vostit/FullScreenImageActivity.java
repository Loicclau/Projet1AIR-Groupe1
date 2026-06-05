package com.tonnom.vostit;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.button.MaterialButton;

public class FullScreenImageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Mode plein écran immersif
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                
        setContentView(R.layout.activity_full_screen_image);

        PhotoView photoView = findViewById(R.id.iv_full_screen);
        MaterialButton btnClose = findViewById(R.id.btn_close_full);
        ProgressBar progressBar = findViewById(R.id.loading_progress);
        
        String imagePath = getIntent().getStringExtra("IMAGE_PATH");
        
        if (imagePath != null && !imagePath.isEmpty()) {
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            
            Glide.with(this)
                    .load(imagePath)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            Toast.makeText(FullScreenImageActivity.this, "Échec du chargement de l'image", Toast.LENGTH_SHORT).show();
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(photoView);
        } else {
            Toast.makeText(this, "Chemin d'image invalide", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }
    }

    @Override
    public void finish() {
        super.finish();
        // Animation fluide de sortie
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
