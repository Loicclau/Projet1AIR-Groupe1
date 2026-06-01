package com.tonnom.vostit;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.tonnom.vostit.adapter.NoteAdapter;
import com.tonnom.vostit.ai.ClaudeHelper;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Note;
import com.tonnom.vostit.pdf.PdfGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private NoteAdapter adapter;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView recycler = findViewById(R.id.recycler_notes);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NoteAdapter(new ArrayList<>(), note -> {
            // clic sur une note → ouvrir pour éditer (à implémenter plus tard)
        });
        recycler.setAdapter(adapter);

        FloatingActionButton fabAdd = findViewById(R.id.fab_add);
        fabAdd.setOnClickListener(v -> {
            startActivity(new Intent(this, AddNoteActivity.class));
        });

        FloatingActionButton fabPdf = findViewById(R.id.fab_pdf);
        fabPdf.setOnClickListener(v -> genererPdfAvecIA());

        chargerNotes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        chargerNotes();
    }

    private void chargerNotes() {
        executor.execute(() -> {
            List<Note> notes = NoteDatabase.getInstance(this).noteDao().getAllNotes();
            runOnUiThread(() -> adapter.setNotes(notes));
        });
    }

    private void genererPdfAvecIA() {
        executor.execute(() -> {
            List<Note> notes = NoteDatabase.getInstance(this).noteDao().getAllNotes();

            if (notes.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "Aucune note à exporter", Toast.LENGTH_SHORT).show());
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (Note note : notes) {
                sb.append("## ").append(note.getTitre()).append("\n");
                sb.append(note.getContenu()).append("\n\n");
            }

            runOnUiThread(() -> Toast.makeText(this, "Envoi à l'IA...", Toast.LENGTH_SHORT).show());

            ClaudeHelper.organiserNotes(sb.toString(), new ClaudeHelper.ClaudeCallback() {
                @Override
                public void onResult(String result) {
                    PdfGenerator.genererPdf(MainActivity.this, result, new PdfGenerator.PdfCallback() {
                        @Override
                        public void onSuccess(String filePath) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                    "PDF généré : " + filePath, Toast.LENGTH_LONG).show());
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                    "Erreur PDF : " + error, Toast.LENGTH_LONG).show());
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Erreur IA : " + error, Toast.LENGTH_LONG).show());
                }
            });
        });
    }
}