package com.tonnom.vostit;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Note;
import com.tonnom.vostit.ocr.OcrHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddNoteActivity extends AppCompatActivity {

    private EditText etTitre, etContenu;
    private ImageView ivPhoto;
    private Bitmap photoBitmap;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    photoBitmap = (Bitmap) result.getData().getExtras().get("data");
                    ivPhoto.setImageBitmap(photoBitmap);
                    ivPhoto.setVisibility(android.view.View.VISIBLE);

                    // OCR sur la photo
                    OcrHelper.extractText(photoBitmap, text -> {
                        runOnUiThread(() -> {
                            String current = etContenu.getText().toString();
                            etContenu.setText(current + "\n" + text);
                        });
                    });
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_note);

        etTitre = findViewById(R.id.et_titre);
        etContenu = findViewById(R.id.et_contenu);
        ivPhoto = findViewById(R.id.iv_photo);
        Button btnCamera = findViewById(R.id.btn_camera);
        Button btnSauvegarder = findViewById(R.id.btn_sauvegarder);

        btnCamera.setOnClickListener(v -> {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(intent);
        });

        btnSauvegarder.setOnClickListener(v -> sauvegarderNote());
    }

    private void sauvegarderNote() {
        String titre = etTitre.getText().toString().trim();
        String contenu = etContenu.getText().toString().trim();

        if (titre.isEmpty()) {
            etTitre.setError("Titre obligatoire");
            return;
        }

        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        Note note = new Note(titre, contenu, date, null);

        executor.execute(() -> {
            NoteDatabase.getInstance(this).noteDao().insert(note);
            runOnUiThread(() -> {
                Toast.makeText(this, "Note sauvegardée ✓", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}