package com.tonnom.vostit;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.tonnom.vostit.adapter.NoteAdapter;
import com.tonnom.vostit.ai.GeminiHelper;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Note;
import com.tonnom.vostit.model.NoteImage;
import com.google.ai.client.generativeai.type.GenerateContentResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.tonnom.vostit.utils.PdfExportHelper;

public class MainActivity extends AppCompatActivity {

    private NoteAdapter adapter;
    private View emptyStateLayout;
    private String selectedSubject;
    private SessionManager sessionManager;
    private ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setPriority(Thread.MIN_PRIORITY); // Réduit l'impact sur les performances du système
        return t;
    });
    private GeminiHelper geminiHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            sessionManager = new SessionManager(this);
            // Utilisation de la clé sécurisée depuis BuildConfig
            geminiHelper = new GeminiHelper(BuildConfig.GEMINI_API_KEY);

            selectedSubject = getIntent().getStringExtra("SELECTED_SUBJECT");
            
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null && selectedSubject != null) {
                getSupportActionBar().setTitle(selectedSubject);
            }

            RecyclerView recycler = findViewById(R.id.recycler_notes);
            recycler.setLayoutManager(new LinearLayoutManager(this));

            adapter = new NoteAdapter(new ArrayList<>(), note -> {
                Intent intent = new Intent(MainActivity.this, NoteDetailActivity.class);
                intent.putExtra("NOTE_ID", note.getId());
                startActivity(intent);
            });
            recycler.setAdapter(adapter);

            emptyStateLayout = findViewById(R.id.layout_empty_state);

            ExtendedFloatingActionButton fabAdd = findViewById(R.id.fab_add);
            fabAdd.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, AddNoteActivity.class);
                intent.putExtra("SELECTED_SUBJECT", selectedSubject);
                startActivity(intent);
            });

            FloatingActionButton fabSynthesize = findViewById(R.id.fab_synthesize);
            fabSynthesize.setOnClickListener(v -> startCourseSynthesis());

            chargerNotes();
        } catch (Throwable e) {
            android.util.Log.e("MainActivity", "Crash in onCreate", e);
            Toast.makeText(this, "Erreur fatale: " + e.toString(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        chargerNotes();
    }

    private void chargerNotes() {
        executor.execute(() -> {
            List<Note> notes;
            if (selectedSubject != null) {
                notes = NoteDatabase.getInstance(this).noteDao().getNotesBySubject(selectedSubject);
            } else {
                notes = NoteDatabase.getInstance(this).noteDao().getAllNotes();
            }
            runOnUiThread(() -> {
                adapter.setNotes(notes);
                emptyStateLayout.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void startCourseSynthesis() {
        if (selectedSubject == null) {
            Toast.makeText(this, "Sélectionnez une matière pour la synthèse", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Vérification de la synthèse existante...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            // Vérifier si une synthèse existe déjà localement
            com.tonnom.vostit.model.Synthesis existing = NoteDatabase.getInstance(this).synthesisDao().getLatestForSubject(selectedSubject);

            if (existing != null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Chargement de la synthèse sauvegardée", Toast.LENGTH_SHORT).show();
                    showSynthesisDialog(existing.getContent());
                });
                return;
            }

            // Si aucune synthèse, on lance le processus IA
            List<Note> notes = NoteDatabase.getInstance(this).noteDao().getNotesBySubject(selectedSubject);
            if (notes.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "Aucune note à synthétiser", Toast.LENGTH_SHORT).show());
                return;
            }

            StringBuilder fullContent = new StringBuilder();
            List<String> imagePaths = new ArrayList<>();

            for (Note note : notes) {
                fullContent.append("NOTE: ").append(note.getTitre()).append("\n");
                fullContent.append(note.getContenu()).append("\n\n");
                List<NoteImage> images = NoteDatabase.getInstance(this).noteDao().getImagesForNote(note.getId());
                for (NoteImage img : images) {
                    imagePaths.add(img.getImagePath());
                }
            }

            if (imagePaths.isEmpty()) {
                performSynthesis(fullContent.toString());
            } else {
                processImagesAndSynthesize(imagePaths, fullContent);
            }
        });
    }

    private void processImagesAndSynthesize(List<String> paths, StringBuilder content) {
        AtomicInteger processedCount = new AtomicInteger(0);
        int totalImages = paths.size();

        for (String path : paths) {
            Bitmap bitmap = loadResizedBitmap(path, 1024);
            if (bitmap == null) {
                if (processedCount.incrementAndGet() == totalImages) performSynthesis(content.toString());
                continue;
            }

            ListenableFuture<GenerateContentResponse> future = geminiHelper.extractTextFromImage(bitmap);
            Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    try {
                        String extracted = result.getText();
                        if (extracted != null) {
                            content.append("\n[Contenu Image] : ").append(extracted).append("\n");
                        }
                    } catch (Exception e) {
                        content.append("\n[Erreur extraction image]\n");
                    }
                    if (processedCount.incrementAndGet() == totalImages) performSynthesis(content.toString());
                }

                @Override
                public void onFailure(Throwable t) {
                    if (processedCount.incrementAndGet() == totalImages) performSynthesis(content.toString());
                }
            }, executor);
        }
    }

    private Bitmap loadResizedBitmap(String path, int maxSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;

        if (width > maxSize || height > maxSize) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= maxSize && (halfWidth / inSampleSize) >= maxSize) {
                inSampleSize *= 2;
            }
        }

        options.inSampleSize = inSampleSize;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    private void performSynthesis(String text) {
        if (geminiHelper == null) return;
        
        runOnUiThread(() -> Toast.makeText(this, "Génération de la synthèse...", Toast.LENGTH_SHORT).show());
        
        ListenableFuture<GenerateContentResponse> future = geminiHelper.synthesizeCourse(text);
        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                try {
                    String finalTxt = result.getText();
                    if (finalTxt == null || finalTxt.isEmpty()) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "L'IA a bloqué le contenu pour des raisons de sécurité.", Toast.LENGTH_LONG).show());
                    } else {
                        // Sauvegarder dans la DB avant d'afficher
                        executor.execute(() -> {
                            com.tonnom.vostit.model.Synthesis newSynthesis = new com.tonnom.vostit.model.Synthesis(
                                    selectedSubject, finalTxt, System.currentTimeMillis()
                            );
                            NoteDatabase.getInstance(MainActivity.this).synthesisDao().insert(newSynthesis);
                        });

                        runOnUiThread(() -> showSynthesisDialog(finalTxt));
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Erreur lors de la lecture de la réponse IA.", Toast.LENGTH_LONG).show());
                }
            }

            @Override
            public void onFailure(Throwable t) {
                t.printStackTrace();
                runOnUiThread(() -> {
                    String simpleName = t.getClass().getSimpleName();
                    String msg = t.getMessage();
                    String finalError = simpleName + " : " + (msg != null ? msg : "Pas de message");

                    if (finalError.contains("API_KEY_INVALID") || finalError.contains("403")) {
                        finalError = "CLÉ API NON RECONNUE.\n\nAssurez-vous que votre clé commence par 'AIzaSy'.";
                    }

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Diagnostic Gemini")
                            .setMessage(finalError)
                            .setPositiveButton("Compris", null)
                            .show();
                });
            }
        }, executor);
    }

    private void showSynthesisDialog(String synthesis) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_synthesis, null);
        TextView tvContent = dialogView.findViewById(R.id.tv_synthesis_content);
        tvContent.setText(synthesis);

        View btnDownload = dialogView.findViewById(R.id.btn_download_pdf);
        btnDownload.setOnClickListener(v -> {
            Toast.makeText(this, "Génération du PDF...", Toast.LENGTH_SHORT).show();
            executor.execute(() -> {
                PdfExportHelper.exportToPdf(this, "Synthèse " + (selectedSubject != null ? selectedSubject : ""), synthesis);
            });
        });

        new AlertDialog.Builder(this)
                .setTitle("Synthèse de " + (selectedSubject != null ? selectedSubject : "cours"))
                .setView(dialogView)
                .setPositiveButton("OK", null)
                .show();
    }
}
