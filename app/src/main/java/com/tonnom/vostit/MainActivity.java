package com.tonnom.vostit;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
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

import com.tonnom.vostit.utils.CloudSyncHelper;
import com.tonnom.vostit.utils.PdfExportHelper;

public class MainActivity extends AppCompatActivity {

    private NoteAdapter adapter;
    private View emptyStateLayout;
    private View loadingOverlay;
    private View btnSynthesize;
    private String selectedSubject;
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
            // Utilisation de la clé sécurisée depuis BuildConfig
            geminiHelper = new GeminiHelper(BuildConfig.GEMINI_API_KEY);

            selectedSubject = getIntent().getStringExtra("SELECTED_SUBJECT");
            
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
            android.util.Log.e("MainActivity", "Crash in onCreate", e);
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
        executor.execute(() -> {
            // 1. Chargement local immédiat
            List<Note> localNotes;
            if (selectedSubject != null) {
                localNotes = NoteDatabase.getInstance(this).noteDao().getNotesBySubject(selectedSubject);
            } else {
                localNotes = NoteDatabase.getInstance(this).noteDao().getAllNotes();
            }
            
            runOnUiThread(() -> {
                adapter.setNotes(localNotes);
                emptyStateLayout.setVisibility(localNotes.isEmpty() ? View.VISIBLE : View.GONE);
            });

            // 2. Branchement de l'écouteur Cloud en temps réel
            if (selectedSubject != null) {
                cloudSyncHelper.fetchCloudNotes(selectedSubject, cloudNotes -> {
                    // 3. Persistance locale des notes du cloud pour éviter la disparition
                    sauvegarderCloudEnLocal(cloudNotes);
                    
                    runOnUiThread(() -> {
                        // Fusion intelligente pour l'affichage
                        List<Note> allNotes = mergeNotes(localNotes, cloudNotes);
                        adapter.setNotes(allNotes);
                        emptyStateLayout.setVisibility(allNotes.isEmpty() ? View.VISIBLE : View.GONE);
                    });
                });
            }
        });
    }

    private void sauvegarderCloudEnLocal(List<Note> cloudNotes) {
        executor.execute(() -> {
            for (Note cNote : cloudNotes) {
                Note existing = NoteDatabase.getInstance(this).noteDao().getNoteByCloudId(cNote.getCloudId());
                if (existing == null) {
                    // Vérifier si une note avec le même titre existe déjà sans cloudId
                    List<Note> sameTitle = NoteDatabase.getInstance(this).noteDao().getNotesBySubject(cNote.getSubject());
                    boolean found = false;
                    for (Note n : sameTitle) {
                        if (n.getTitre().equals(cNote.getTitre()) && n.getCloudId() == null) {
                            n.setCloudId(cNote.getCloudId());
                            n.setRemoteUrlsString(cNote.getRemoteUrlsString());
                            NoteDatabase.getInstance(this).noteDao().update(n);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        NoteDatabase.getInstance(this).noteDao().insert(cNote);
                    }
                } else {
                    // Mise à jour de la note locale existante
                    existing.setTitre(cNote.getTitre());
                    existing.setContenu(cNote.getContenu());
                    existing.setRemoteUrlsString(cNote.getRemoteUrlsString());
                    NoteDatabase.getInstance(this).noteDao().update(existing);
                }
            }
        });
    }

    private List<Note> mergeNotes(List<Note> local, List<Note> cloud) {
        List<Note> combined = new ArrayList<>(local);
        for (Note cNote : cloud) {
            boolean alreadyExists = false;
            for (Note lNote : local) {
                // Comparaison par Cloud ID ou contenu pour identifier les mêmes notes
                if ((lNote.getCloudId() != null && lNote.getCloudId().equals(cNote.getCloudId())) ||
                    (lNote.getTitre().equals(cNote.getTitre()) && lNote.getAuthor().equals(cNote.getAuthor()))) {
                    alreadyExists = true;
                    // On peut mettre à jour l'ID Cloud local s'il manquait
                    if (lNote.getCloudId() == null) lNote.setCloudId(cNote.getCloudId());
                    break;
                }
            }
            if (!alreadyExists) {
                combined.add(cNote);
            }
        }
        return combined;
    }

    private void startCourseSynthesis() {
        if (selectedSubject == null) {
            Toast.makeText(this, "Sélectionnez une matière pour la synthèse", Toast.LENGTH_SHORT).show();
            return;
        }

        runOnUiThread(() -> {
            loadingOverlay.setVisibility(View.VISIBLE);
            btnSynthesize.setEnabled(false);
        });

        executor.execute(() -> {
            // Vérifier si une synthèse existe déjà localement
            com.tonnom.vostit.model.Synthesis existing = NoteDatabase.getInstance(this).synthesisDao().getLatestForSubject(selectedSubject);

            if (existing != null) {
                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    btnSynthesize.setEnabled(true);
                    Toast.makeText(this, "Chargement de la synthèse sauvegardée", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(MainActivity.this, SynthesisDetailActivity.class);
                    intent.putExtra("SYNTHESIS_ID", existing.getId());
                    startActivity(intent);
                });
                return;
            }

            // Si aucune synthèse, on lance le processus IA
            List<Note> notes = NoteDatabase.getInstance(this).noteDao().getNotesBySubject(selectedSubject);
            if (notes.isEmpty()) {
                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    btnSynthesize.setEnabled(true);
                    Toast.makeText(this, "Aucune note à synthétiser", Toast.LENGTH_SHORT).show();
                });
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
                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    btnSynthesize.setEnabled(true);
                });
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
                            long id = NoteDatabase.getInstance(MainActivity.this).synthesisDao().insert(newSynthesis);
                            runOnUiThread(() -> {
                                Intent intent = new Intent(MainActivity.this, SynthesisDetailActivity.class);
                                intent.putExtra("SYNTHESIS_ID", (int) id);
                                startActivity(intent);
                            });
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Erreur lors de la lecture de la réponse IA.", Toast.LENGTH_LONG).show());
                }
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    btnSynthesize.setEnabled(true);
                });
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

}
