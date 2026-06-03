package com.tonnom.vostit;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tonnom.vostit.adapter.ImageAdapter;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Note;
import com.tonnom.vostit.model.NoteImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoteDetailActivity extends AppCompatActivity {

    private TextView tvTitre, tvDate, tvContenu, tvImagesLabel;
    private RecyclerView recyclerImages;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

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
                    setTitle(note.getTitre());

                    if (!images.isEmpty()) {
                        tvImagesLabel.setVisibility(View.VISIBLE);
                        recyclerImages.setAdapter(new ImageAdapter(images));
                    }
                }
            });
        });
    }
}
