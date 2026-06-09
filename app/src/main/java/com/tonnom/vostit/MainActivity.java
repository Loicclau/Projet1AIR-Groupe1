package com.tonnom.vostit;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import com.tonnom.vostit.model.Synthesis;
import com.google.ai.client.generativeai.type.GenerateContentResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.tonnom.vostit.utils.CloudSyncHelper;
import com.tonnom.vostit.utils.PdfExportHelper;

public class MainActivity extends AppCompatActivity {

    private NoteAdapter adapter;
    private View emptyStateLayout;
    private View loadingOverlay;
    private View btnSynthesize;
    private String selectedSubject;
    private String selectedSpecialty;
    private String selectedYear;
    private SessionManager sessionManager;
    private CloudSyncHelper cloudSyncHelper;
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
            cloudSyncHelper = new CloudSyncHelper(this);
            // Utilisation hybride : Gemini pour OCR, Groq pour Synthèse
            geminiHelper = new GeminiHelper(BuildConfig.GEMINI_API_KEY, BuildConfig.GROQ_API_KEY);

            selectedSubject = getIntent().getStringExtra("SELECTED_SUBJECT");
            selectedSpecialty = getIntent().getStringExtra("SELECTED_SPECIALTY");
            selectedYear = getIntent().getStringExtra("SELECTED_YEAR");
            
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null && selectedSubject != null) {
                getSupportActionBar().setTitle(selectedSubject);
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            // Correction du bouton retour
            toolbar.setNavigationOnClickListener(v -> finish());

            RecyclerView recycler = findViewById(R.id.recycler_notes);
            recycler.setLayoutManager(new LinearLayoutManager(this));

            adapter = new NoteAdapter(new ArrayList<>(), note -> {
                Intent intent = new Intent(MainActivity.this, NoteDetailActivity.class);
                intent.putExtra("NOTE_ID", note.getId());
                if (note.getCloudId() != null) {
                    intent.putExtra("CLOUD_ID", note.getCloudId());
                }
                startActivity(intent);
            });
            recycler.setAdapter(adapter);

            emptyStateLayout = findViewById(R.id.layout_empty_state);
            loadingOverlay = findViewById(R.id.loading_overlay);

            findViewById(R.id.fab_add).setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, AddNoteActivity.class);
                intent.putExtra("SELECTED_SUBJECT", selectedSubject);
                startActivity(intent);
            });

            btnSynthesize = findViewById(R.id.btn_synthesize_modern);
            btnSynthesize.setOnClickListener(v -> startCourseSynthesis());

            chargerNotes();
        } catch (Throwable e) {
            Log.e("MainActivity", "Crash in onCreate", e);
            Toast.makeText(this, "Erreur fatale: " + e.toString(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_view_syntheses) {
            Intent intent = new Intent(this, SynthesisListActivity.class);
            intent.putExtra("SELECTED_SUBJECT", selectedSubject);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        chargerNotes();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cloudSyncHelper != null) {
            cloudSyncHelper.stopListening();
        }
    }

    private void chargerNotes() {
        // 1. Chargement local immédiat (cache de la dernière synchro)
        executor.execute(() -> {
            List<Note> initialNotes;
            if (selectedSubject != null) {
                initialNotes = NoteDatabase.getInstance(this).noteDao().getNotesBySubject(selectedSubject);
            } else {
                initialNotes = NoteDatabase.getInstance(this).noteDao().getAllNotes();
            }
            runOnUiThread(() -> {
                adapter.setNotes(initialNotes);
                emptyStateLayout.setVisibility(initialNotes.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });

        // 2. Branchement de l'écouteur Cloud en temps réel pour synchronisation
        cloudSyncHelper.fetchCloudNotes(selectedSubject, cloudNotes -> {
            executor.execute(() -> {
                // Synchronisation forcée avec le Cloud : Firebase est la source de vérité
                synchroniserLocalAvecCloud(cloudNotes);
                
                // On recharge depuis la base locale qui est maintenant synchro
                List<Note> syncedNotes;
                if (selectedSubject != null) {
                    syncedNotes = NoteDatabase.getInstance(this).noteDao().getNotesBySubject(selectedSubject);
                } else {
                    syncedNotes = NoteDatabase.getInstance(this).noteDao().getAllNotes();
                }

                runOnUiThread(() -> {
                    adapter.setNotes(syncedNotes);
                    emptyStateLayout.setVisibility(syncedNotes.isEmpty() ? View.VISIBLE : View.GONE);
                    Log.d("MainActivity", "Notes synchronisées : " + syncedNotes.size());
                    
                    // Mise à jour de l'état du bouton de synthèse
                    verifierEtatBoutonSynthese();
                });
            });
        });
    }

    private void verifierEtatBoutonSynthese() {
        if (selectedSubject == null) return;
        
        executor.execute(() -> {
            long latestNoteTs = NoteDatabase.getInstance(this).noteDao().getLatestNoteTimestampForSubject(selectedSubject);
            Synthesis latestLocal = NoteDatabase.getInstance(this).synthesisDao().getLatestForSubject(selectedSubject);

            cloudSyncHelper.fetchLatestSynthesis(selectedSubject, cloudSynthesis -> {
                Synthesis bestSynthesis = cloudSynthesis != null ? cloudSynthesis : latestLocal;
                
                runOnUiThread(() -> {
                    if (btnSynthesize instanceof ExtendedFloatingActionButton) {
                        ExtendedFloatingActionButton fab = (ExtendedFloatingActionButton) btnSynthesize;
                        if (bestSynthesis != null && bestSynthesis.getTimestamp() >= latestNoteTs) {
                            fab.setText("Voir le résumé Vostit");
                            fab.setIconResource(android.R.drawable.ic_menu_agenda);
                        } else {
                            fab.setText("Générer le résumé");
                            fab.setIconResource(R.drawable.ic_synthesis);
                        }
                    }
                });
            });
        });
    }

    private void synchroniserLocalAvecCloud(List<Note> cloudNotes) {
        NoteDatabase db = NoteDatabase.getInstance(this);
        
        // 1. Identifier les notes locales à supprimer (celles qui ne sont plus sur Firebase)
        List<Note> allLocal;
        if (selectedSubject != null) {
            allLocal = db.noteDao().getNotesBySubject(selectedSubject);
        } else {
            allLocal = db.noteDao().getAllNotes();
        }

        for (Note localNote : allLocal) {
            if (localNote.getCloudId() != null) {
                boolean existsInCloud = false;
                for (Note cNote : cloudNotes) {
                    if (localNote.getCloudId().equals(cNote.getCloudId())) {
                        existsInCloud = true;
                        break;
                    }
                }
                if (!existsInCloud) {
                    // Supprimer localement car n'existe plus sur Firebase
                    nettoyerImagesNote(localNote.getId());
                    db.noteDao().delete(localNote);
                    Log.d("MainActivity", "Note supprimée localement (absente du Cloud) : " + localNote.getTitre());
                }
            }
        }

        // 2. Mettre à jour ou insérer les notes du Cloud
        for (Note cNote : cloudNotes) {
            Note existing = db.noteDao().getNoteByCloudId(cNote.getCloudId());
            if (existing == null) {
                db.noteDao().insert(cNote);
            } else {
                existing.setTitre(cNote.getTitre());
                existing.setContenu(cNote.getContenu());
                existing.setDate(cNote.getDate());
                existing.setSubject(cNote.getSubject());
                existing.setAuthor(cNote.getAuthor());
                existing.setTimestamp(cNote.getTimestamp());
                db.noteDao().update(existing);
            }
        }
    }

    private void nettoyerImagesNote(int noteId) {
        List<NoteImage> images = NoteDatabase.getInstance(this).noteDao().getImagesForNote(noteId);
        for (NoteImage img : images) {
            try {
                java.io.File file = new java.io.File(img.getImagePath());
                if (file.exists()) {
                    boolean deleted = file.delete();
                    Log.d("MainActivity", "Fichier orphelin supprimé : " + img.getImagePath() + " (" + deleted + ")");
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Erreur suppression fichier", e);
            }
        }
        // Le CASCADE de Room s'occupera de la table note_images
    }

    private void startCourseSynthesis() {
        if (selectedSubject == null) {
            Toast.makeText(this, "Sélectionnez une matière pour la synthèse", Toast.LENGTH_SHORT).show();
            return;
        }

        // Vérification du quota
        if (sessionManager.getDailySynthesisCount() >= 10) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Quota atteint")
                    .setMessage("Vous avez atteint votre limite de 10 synthèses par jour. Revenez demain !")
                    .setPositiveButton("D'accord", null)
                    .show();
            return;
        }

        runOnUiThread(() -> {
            loadingOverlay.setVisibility(View.VISIBLE);
            // Optionnel : mettre à jour le texte du chargement si vous avez un TextView
            TextView tvLoading = loadingOverlay.findViewById(R.id.tv_loading_message);
            if (tvLoading != null) tvLoading.setText("Vostit génère votre synthèse...");

            btnSynthesize.setEnabled(false);
        });

        executor.execute(() -> {
            // 0. Vérifier s'il y a des notes pour cette matière
            List<Note> notes = NoteDatabase.getInstance(this).noteDao().getNotesBySubject(selectedSubject);
            if (notes.isEmpty()) {
                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    btnSynthesize.setEnabled(true);
                    Toast.makeText(this, "Ajoutez d'abord des notes pour générer un résumé.", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            // 1. Vérifier s'il existe une synthèse (Cloud ou Locale)
            Synthesis latestLocal = NoteDatabase.getInstance(this).synthesisDao().getLatestForSubject(selectedSubject);
            long latestNoteTs = NoteDatabase.getInstance(this).noteDao().getLatestNoteTimestampForSubject(selectedSubject);

            cloudSyncHelper.fetchLatestSynthesis(selectedSubject, cloudSynthesis -> {
                executor.execute(() -> {
                    Synthesis synthesisToUse = cloudSynthesis != null ? cloudSynthesis : latestLocal;

                    if (synthesisToUse != null && synthesisToUse.getTimestamp() >= latestNoteTs) {
                        // La synthèse est à jour, on l'affiche directement
                        if (cloudSynthesis != null && (latestLocal == null || cloudSynthesis.getTimestamp() > latestLocal.getTimestamp())) {
                            NoteDatabase.getInstance(this).synthesisDao().deleteBySubject(selectedSubject);
                            NoteDatabase.getInstance(this).synthesisDao().insert(cloudSynthesis);
                        }
                        
                        final Synthesis finalS = NoteDatabase.getInstance(this).synthesisDao().getLatestForSubject(selectedSubject);

                        runOnUiThread(() -> {
                            loadingOverlay.setVisibility(View.GONE);
                            btnSynthesize.setEnabled(true);
                            Intent intent = new Intent(MainActivity.this, SynthesisDetailActivity.class);
                            intent.putExtra("SYNTHESIS_ID", finalS.getId());
                            startActivity(intent);
                        });
                    } else {
                        // Générer une nouvelle synthèse car absente ou obsolète
                        genererNouvelleSynthese(notes);
                    }
                });
            });
        });
    }

    private void genererNouvelleSynthese(List<Note> notes) {
        StringBuilder fullContent = new StringBuilder();
        List<String> imagePaths = new ArrayList<>();

        for (Note note : notes) {
            fullContent.append("TITRE: ").append(note.getTitre()).append("\n");
            fullContent.append("CONTENU: ").append(note.getContenu()).append("\n\n");
            
            // Seul l'auteur voit ses propres images (locales)
            if (note.getAuthor() != null && note.getAuthor().equals(sessionManager.getUsername())) {
                List<NoteImage> images = NoteDatabase.getInstance(this).noteDao().getImagesForNote(note.getId());
                for (NoteImage img : images) {
                    imagePaths.add(img.getImagePath());
                }
            }
        }

        if (imagePaths.isEmpty()) {
            performSynthesis(fullContent.toString());
        } else {
            processImagesAndSynthesize(imagePaths, fullContent);
        }
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

            ListenableFuture<GenerateContentResponse> future = geminiHelper.extractAndCleanText(bitmap);
            Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    String extracted = result.getText();
                    if (extracted != null && !extracted.trim().isEmpty()) {
                        content.append("\n[Contenu Image] : ").append(extracted).append("\n");
                    }
                    if (processedCount.incrementAndGet() == totalImages) performSynthesis(content.toString());
                }

                @Override
                public void onFailure(@NonNull Throwable t) {
                    Log.e("MainActivity", "Erreur OCR image", t);
                    runOnUiThread(() -> {
                        if (t.getMessage() != null && t.getMessage().contains("404")) {
                            Toast.makeText(MainActivity.this, "Erreur 404 Gemini : Modèle non trouvé. Vérifiez votre clé API.", Toast.LENGTH_LONG).show();
                        }
                    });
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
        
        runOnUiThread(() -> Toast.makeText(this, "Génération de la synthèse (via Groq)...", Toast.LENGTH_SHORT).show());
        
        ListenableFuture<String> future = geminiHelper.synthesizeCourse(text);
        Futures.addCallback(future, new FutureCallback<String>() {
            @Override
            public void onSuccess(String finalTxt) {
                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    btnSynthesize.setEnabled(true);
                });
                if (finalTxt == null || finalTxt.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Erreur de génération.", Toast.LENGTH_LONG).show());
                } else {
                    // Sauvegarder dans la DB avant d'afficher
                    executor.execute(() -> {
                        Synthesis newSynthesis = new Synthesis(
                                selectedSubject, finalTxt, System.currentTimeMillis()
                        );
                        newSynthesis.setSpecialty(selectedSpecialty);
                        newSynthesis.setYear(selectedYear);
                        
                        // 1. Partager sur le Cloud pour les autres
                        cloudSyncHelper.uploadSynthesis(newSynthesis);
                        
                        // 2. Sauvegarder localement
                        NoteDatabase.getInstance(MainActivity.this).synthesisDao().deleteBySubject(selectedSubject);
                        long id = NoteDatabase.getInstance(MainActivity.this).synthesisDao().insert(newSynthesis);

                        // 3. Incrémenter le quota
                        sessionManager.incrementDailySynthesisCount();

                        runOnUiThread(() -> {
                            Intent intent = new Intent(MainActivity.this, SynthesisDetailActivity.class);
                            intent.putExtra("SYNTHESIS_ID", (int) id);
                            startActivity(intent);
                        });
                    });
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e("MainActivity", "Erreur Groq : " + t.getMessage());
                
                // On ne relance PAS en boucle infinie immédiatement si c'est une erreur critique
                String msg = t.getMessage() != null ? t.getMessage() : "";
                if (msg.contains("401") || msg.contains("403") || msg.contains("API key") || msg.contains("404")) {
                    runOnUiThread(() -> {
                        loadingOverlay.setVisibility(View.GONE);
                        btnSynthesize.setEnabled(true);
                        new AlertDialog.Builder(MainActivity.this, R.style.ModernDialog)
                            .setTitle("Problème de configuration")
                            .setMessage("La clé API Groq semble incorrecte ou le modèle est introuvable. Vérifiez local.properties.")
                            .setPositiveButton("Vérifier", null)
                            .show();
                    });
                    return;
                }

                // Sinon (surcharge), on attend 5 secondes au lieu de 3 pour être plus "calme"
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (!isFinishing()) {
                        Log.d("MainActivity", "Tentative de reconnexion au service...");
                        startCourseSynthesis();
                    }
                }, 5000);
            }
        }, executor);
    }

}
