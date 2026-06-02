package com.tonnom.vostit;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class FullScreenImageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_image);

        ImageView imageView = findViewById(R.id.iv_full_screen);
        ImageButton btnClose = findViewById(R.id.btn_close_full);

        String imagePath = getIntent().getStringExtra("IMAGE_PATH");
        if (imagePath != null) {
            imageView.setImageBitmap(BitmapFactory.decodeFile(imagePath));
        } else {
            finish();
        }

        btnClose.setOnClickListener(v -> finish());
    }
}
