package com.tonnom.vostit;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.button.MaterialButton;

import java.io.File;

public class FullScreenImageActivity extends AppCompatActivity {

    private static final String TAG = "FullScreenImage";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
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
            
            if (isFinishing() || isDestroyed()) return;

            if (imagePath == null || imagePath.isEmpty()) {
                Log.e(TAG, "URI d'image nulle ou vide");
                Toast.makeText(this, "Erreur: Chemin d'image invalide", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            File file = new File(imagePath);
            if (!file.exists()) {
                Log.e(TAG, "Le fichier n'existe pas : " + imagePath);
                Toast.makeText(this, "Fichier image introuvable", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            
            Glide.with(this)
                    .load(file)
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // Images locales, pas besoin de cache disk supplémentaire
                    .skipMemoryCache(false)
                    .placeholder(R.drawable.ic_launcher_background) // Placeholder
                    .error(R.drawable.ic_launcher_foreground) // Error image
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Glide: Échec du chargement de l'image", e);
                            runOnUiThread(() -> {
                                if (progressBar != null) progressBar.setVisibility(View.GONE);
                                Toast.makeText(FullScreenImageActivity.this, "Image corrompue ou illisible", Toast.LENGTH_SHORT).show();
                            });
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            runOnUiThread(() -> {
                                if (progressBar != null) progressBar.setVisibility(View.GONE);
                            });
                            return false;
                        }
                    })
                    .into(photoView);

            if (btnClose != null) {
                btnClose.setOnClickListener(v -> finish());
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur fatale dans FullScreenImageActivity", e);
            Toast.makeText(this, "Erreur lors de l'ouverture de l'image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void finish() {
        super.finish();
        // Animation fluide de sortie
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
