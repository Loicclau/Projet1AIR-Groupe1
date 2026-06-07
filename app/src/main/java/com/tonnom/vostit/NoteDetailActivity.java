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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoteDetailActivity extends AppCompatActivity {

    private TextView tvTitre, tvDate, tvContenu, tvImagesLabel;
    private RecyclerView recyclerImages;
    private int noteId;
    private String cloudId;
    private String selectedSubjectFromNote;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        noteId = getIntent().getIntExtra("NOTE_ID", -1);
        cloudId = getIntent().getStringExtra("CLOUD_ID");

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

        findViewById(R.id.btn_edit_note).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, AddNoteActivity.class);
            intent.putExtra("NOTE_ID", noteId);
            intent.putExtra("SELECTED_SUBJECT", selectedSubjectFromNote);
            startActivity(intent);
        });

        findViewById(R.id.btn_delete_note).setOnClickListener(v -> confirmerSuppressionNote());

        recyclerImages.setLayoutManager(new LinearLayoutManager(this));
        recyclerImages.setNestedScrollingEnabled(false);

        if (noteId != -1) {
            chargerDetailNote(noteId);
        } else if (cloudId != null) {
            chargerDetailNoteParCloudId(cloudId);
        } else {
            finish();
        }
    }

    private void chargerDetailNote(int noteId) {
        executor.execute(() -> {
            Note note = NoteDatabase.getInstance(this).noteDao().getNoteById(noteId);
            afficherNote(note);
        });
    }

    private void chargerDetailNoteParCloudId(String cloudId) {
        executor.execute(() -> {
            Note note = NoteDatabase.getInstance(this).noteDao().getNoteByCloudId(cloudId);
            afficherNote(note);
        });
    }

    private void afficherNote(Note note) {
        if (note == null) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Note introuvable", Toast.LENGTH_SHORT).show();
                finish();
            });
            return;
        }
        
        // Seul l'auteur voit ses propres images (locales)
        SessionManager sessionManager = new SessionManager(this);
        boolean isAuthor = note.getAuthor() != null && note.getAuthor().equals(sessionManager.getUsername());
        
        List<NoteImage> localImages = isAuthor ? 
                NoteDatabase.getInstance(this).noteDao().getImagesForNote(note.getId()) : 
                new ArrayList<>();

        runOnUiThread(() -> {
            selectedSubjectFromNote = note.getSubject();
            tvTitre.setText(note.getTitre());
            tvDate.setText(note.getDate());
            tvContenu.setText(note.getContenu());
            
            List<String> allImagePaths = new ArrayList<>();
            for (NoteImage img : localImages) {
                allImagePaths.add(img.getImagePath());
            }

            if (allImagePaths.isEmpty()) {
                tvImagesLabel.setVisibility(View.GONE);
                recyclerImages.setVisibility(View.GONE);
            } else {
                tvImagesLabel.setVisibility(View.VISIBLE);
                recyclerImages.setVisibility(View.VISIBLE);
                recyclerImages.setAdapter(new ImageAdapter(allImagePaths, path -> {
                    confirmerSuppressionImage(path, note.getId());
                }));
            }
        });
    }

    private void confirmerSuppressionNote() {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer la note")
                .setMessage("Voulez-vous supprimer cette note ? (Elle sera également supprimée du Cloud)")
                .setPositiveButton("Supprimer", (dialog, which) -> supprimerNote(noteId))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void supprimerNote(int noteId) {
        executor.execute(() -> {
            Note note = NoteDatabase.getInstance(this).noteDao().getNoteById(noteId);
            if (note != null && note.getCloudId() != null) {
                // Supprimer de Firebase également
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("notes")
                        .document(note.getCloudId())
                        .delete()
                        .addOnSuccessListener(aVoid -> android.util.Log.d("NoteDetail", "Note supprimée de Firebase"));
            }

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

    private void confirmerSuppressionImage(String imagePath, int noteId) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer l'image")
                .setMessage("Voulez-vous supprimer cette image définitivement ?")
                .setPositiveButton("Supprimer", (dialog, which) -> supprimerImage(imagePath, noteId))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void supprimerImage(String imagePath, int noteId) {
        executor.execute(() -> {
            NoteDatabase.getInstance(this).noteDao().deleteImageByPath(imagePath);
            File file = new File(imagePath);
            if (file.exists()) file.delete();
            chargerDetailNote(noteId);
        });
    }
}
