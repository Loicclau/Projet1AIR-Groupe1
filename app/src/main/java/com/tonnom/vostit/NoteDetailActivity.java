package com.tonnom.vostit;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tonnom.vostit.adapter.ImageAdapter;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Note;
import com.tonnom.vostit.model.NoteImage;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoteDetailActivity extends AppCompatActivity {

    private TextView tvTitre, tvDate, tvContenu, tvImagesLabel;
    private RecyclerView recyclerImages;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        tvTitre = findViewById(R.id.tv_detail_titre);
        tvDate = findViewById(R.id.tv_detail_date);
        tvContenu = findViewById(R.id.tv_detail_contenu);
        tvImagesLabel = findViewById(R.id.tv_images_label);
        recyclerImages = findViewById(R.id.recycler_detail_images);

        Toolbar toolbar = findViewById(R.id.toolbar_detail);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btn_delete_note).setOnClickListener(v -> confirmerSuppressionNote());

        recyclerImages.setLayoutManager(new LinearLayoutManager(this));
        recyclerImages.setNestedScrollingEnabled(false);

        int noteId = getIntent().getIntExtra("NOTE_ID", -1);
        if (noteId != -1) {
            chargerDetailNote(noteId);
        } else {
            finish();
        }
    }

    private void chargerDetailNote(int noteId) {
        executor.execute(() -> {
            Note note = NoteDatabase.getInstance(this).noteDao().getNoteById(noteId);
            List<NoteImage> images = NoteDatabase.getInstance(this).noteDao().getImagesForNote(noteId);

            runOnUiThread(() -> {
                if (note != null) {
                    tvTitre.setText(note.getTitre());
                    tvDate.setText(note.getDate());
                    tvContenu.setText(note.getContenu());
                    
                    if (images.isEmpty()) {
                        tvImagesLabel.setVisibility(View.GONE);
                        recyclerImages.setVisibility(View.GONE);
                    } else {
                        tvImagesLabel.setVisibility(View.VISIBLE);
                        recyclerImages.setVisibility(View.VISIBLE);
                        recyclerImages.setAdapter(new ImageAdapter(images, image -> confirmerSuppressionImage(image, noteId)));
                    }
                }
            });
        });
    }

    private void confirmerSuppressionNote() {
        int noteId = getIntent().getIntExtra("NOTE_ID", -1);
        new AlertDialog.Builder(this)
                .setTitle("Supprimer la note")
                .setMessage("Voulez-vous supprimer cette note et toutes ses images ?")
                .setPositiveButton("Supprimer", (dialog, which) -> supprimerNote(noteId))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void supprimerNote(int noteId) {
        executor.execute(() -> {
            List<NoteImage> images = NoteDatabase.getInstance(this).noteDao().getImagesForNote(noteId);
            for (NoteImage img : images) {
                File file = new File(img.getImagePath());
                if (file.exists()) file.delete();
            }
            NoteDatabase.getInstance(this).noteDao().deleteImagesForNote(noteId);
            NoteDatabase.getInstance(this).noteDao().deleteNoteById(noteId);
            
            runOnUiThread(() -> {
                Toast.makeText(this, "Note supprimée", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void confirmerSuppressionImage(NoteImage image, int noteId) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer l'image")
                .setMessage("Voulez-vous supprimer cette image définitivement ?")
                .setPositiveButton("Supprimer", (dialog, which) -> supprimerImage(image, noteId))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void supprimerImage(NoteImage image, int noteId) {
        executor.execute(() -> {
            NoteDatabase.getInstance(this).noteDao().deleteImage(image);
            File file = new File(image.getImagePath());
            if (file.exists()) file.delete();
            chargerDetailNote(noteId);
        });
    }
}
