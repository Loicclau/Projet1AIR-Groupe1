package com.tonnom.vostit;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tonnom.vostit.ai.GeminiHelper;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Note;
import com.tonnom.vostit.model.NoteImage;
import com.tonnom.vostit.utils.CloudSyncHelper;
import com.tonnom.vostit.utils.PdfExportHelper;
import android.view.Menu;
import android.view.MenuItem;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import androidx.appcompat.app.AlertDialog;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddNoteActivity extends AppCompatActivity {

    private EditText etTitre, etContenu;
    private RecyclerView recyclerPhotos;
    private PhotoPreviewAdapter photoAdapter;
    private List<String> photoPaths = new ArrayList<>();
    private List<String> existingPhotoPaths = new ArrayList<>();
    private int editingNoteId = -1;
    private String selectedSubject;
    private SessionManager sessionManager;
    private CloudSyncHelper cloudSyncHelper;
    private GeminiHelper geminiHelper;
    private View loadingOverlay;
    private TextView tvLoadingMessage;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Uri currentPhotoUri;
    private String currentPhotoPath;

    private ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (currentPhotoPath != null) {
                        processImageForOCR(currentPhotoPath);
                        photoPaths.add(currentPhotoPath);
                        photoAdapter.notifyDataSetChanged();
                        recyclerPhotos.setVisibility(View.VISIBLE);
                    }
                }
            });

    private ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImage = result.getData().getData();
                    if (selectedImage != null) {
                        String path = copyUriToInternalStorage(selectedImage);
                        if (path != null) {
                            processImageForOCR(path);
                            photoPaths.add(path);
                            photoAdapter.notifyDataSetChanged();
                            recyclerPhotos.setVisibility(View.VISIBLE);
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_note);

        sessionManager = new SessionManager(this);
        cloudSyncHelper = new CloudSyncHelper(this);
        geminiHelper = new GeminiHelper(BuildConfig.GEMINI_API_KEY);
        loadingOverlay = findViewById(R.id.loading_overlay);
        
        // Recherche dynamique du TextView de chargement
        View textChild = loadingOverlay.findViewById(android.R.id.text1);
        if (textChild instanceof TextView) {
            tvLoadingMessage = (TextView) textChild;
        } else {
            // Fallback : on cherche n'importe quel TextView dans l'overlay
            findLoadingTextView(loadingOverlay);
        }

        selectedSubject = getIntent().getStringExtra("SELECTED_SUBJECT");
        editingNoteId = getIntent().getIntExtra("NOTE_ID", -1);
        
        Toolbar toolbar = findViewById(R.id.toolbar_add_note);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (editingNoteId != -1) {
                getSupportActionBar().setTitle("Modifier la note");
            } else {
                getSupportActionBar().setTitle(selectedSubject != null ? "Nouveau : " + selectedSubject : "Nouvelle Note");
            }
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etTitre = findViewById(R.id.et_titre);
        etContenu = findViewById(R.id.et_contenu);
        recyclerPhotos = findViewById(R.id.recycler_add_photos);
        
        photoAdapter = new PhotoPreviewAdapter(photoPaths);
        recyclerPhotos.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerPhotos.setAdapter(photoAdapter);

        if (editingNoteId != -1) {
            chargerDonneesNote();
        }

        findViewById(R.id.btn_camera).setOnClickListener(v -> openCamera());
        
        View btnGallery = findViewById(R.id.btn_gallery);
        if (btnGallery != null) {
            btnGallery.setOnClickListener(v -> openGallery());
        }

        findViewById(R.id.btn_sauvegarder).setOnClickListener(v -> sauvegarderNote());
    }

    private void findLoadingTextView(View view) {
        if (view instanceof TextView) {
            tvLoadingMessage = (TextView) view;
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                findLoadingTextView(vg.getChildAt(i));
                if (tvLoadingMessage != null) break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_add_note, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_share) {
            shareNote();
            return true;
        } else if (id == R.id.action_export_pdf) {
            exportNoteToPdf();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareNote() {
        String content = etContenu.getText().toString();
        if (content.isEmpty()) {
            Toast.makeText(this, "Rien à partager", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, etTitre.getText().toString());
        intent.putExtra(Intent.EXTRA_TEXT, content);
        startActivity(Intent.createChooser(intent, "Partager via"));
    }

    private void exportNoteToPdf() {
        String titre = etTitre.getText().toString();
        String contenu = etContenu.getText().toString();
        if (titre.isEmpty() || contenu.isEmpty()) {
            Toast.makeText(this, "Titre et contenu requis pour le PDF", Toast.LENGTH_SHORT).show();
            return;
        }
        PdfExportHelper.exportToPdf(this, titre, contenu);
    }

    private void chargerDonneesNote() {
        executor.execute(() -> {
            Note note = NoteDatabase.getInstance(this).noteDao().getNoteById(editingNoteId);
            List<NoteImage> images = NoteDatabase.getInstance(this).noteDao().getImagesForNote(editingNoteId);
            
            runOnUiThread(() -> {
                if (note != null) {
                    etTitre.setText(note.getTitre());
                    etContenu.setText(note.getContenu());
                    for (NoteImage img : images) {
                        photoPaths.add(img.getImagePath());
                        existingPhotoPaths.add(img.getImagePath());
                    }
                    if (!photoPaths.isEmpty()) {
                        recyclerPhotos.setVisibility(View.VISIBLE);
                        photoAdapter.notifyDataSetChanged();
                    }
                }
            });
        });
    }

    private void processImageForOCR(String path) {
        showLoading("Analyse de l'image par l'IA...");
        
        executor.execute(() -> {
            Bitmap bitmap = loadResizedBitmap(path, 1024);
            if (bitmap == null) {
                hideLoading();
                return;
            }

            ListenableFuture<GenerateContentResponse> future = geminiHelper.extractTextFromImage(bitmap);
            Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    runOnUiThread(() -> {
                        hideLoading();
                        String extractedText = result.getText();
                        if (extractedText != null && !extractedText.contains("[ERREUR: TEXTE ILLISIBLE]")) {
                            showOCRPreviewDialog(extractedText);
                        } else {
                            Toast.makeText(AddNoteActivity.this, "Impossible de lire le texte de l'image.", Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void onFailure(Throwable t) {
                    runOnUiThread(() -> {
                        hideLoading();
                        Toast.makeText(AddNoteActivity.this, "Erreur OCR : " + t.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }, executor);
        });
    }

    private void showOCRPreviewDialog(String text) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ocr_preview, null);
        EditText etResult = dialogView.findViewById(R.id.et_ocr_result);
        etResult.setText(text);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        dialogView.findViewById(R.id.btn_ocr_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_ocr_confirm).setOnClickListener(v -> {
            String finalText = etResult.getText().toString().trim();
            if (!finalText.isEmpty()) {
                String currentContent = etContenu.getText().toString();
                if (!currentContent.isEmpty()) currentContent += "\n\n";
                etContenu.setText(currentContent + finalText);
                new AlertDialog.Builder(this)
                        .setTitle("Texte ajouté !")
                        .setMessage("Le texte a été inséré dans votre note. Souhaitez-vous conserver l'image originale dans la note ?")
                        .setPositiveButton("Conserver", null)
                        .setNegativeButton("Supprimer l'image", (d, w) -> {
                            if (!photoPaths.isEmpty()) {
                                photoPaths.remove(photoPaths.size() - 1);
                                photoAdapter.notifyDataSetChanged();
                                if (photoPaths.isEmpty()) recyclerPhotos.setVisibility(View.GONE);
                            }
                        })
                        .show();
            }
            dialog.dismiss();
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private void showLoading(String message) {
        runOnUiThread(() -> {
            if (tvLoadingMessage != null) tvLoadingMessage.setText(message);
            loadingOverlay.setVisibility(View.VISIBLE);
        });
    }

    private void hideLoading() {
        runOnUiThread(() -> loadingOverlay.setVisibility(View.GONE));
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = createPhotoFile();
        if (photoFile != null) {
            currentPhotoPath = photoFile.getAbsolutePath();
            currentPhotoUri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    photoFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cameraLauncher.launch(intent);
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private File createPhotoFile() {
        String fileName = "IMG_" + UUID.randomUUID().toString() + ".jpg";
        return new File(getFilesDir(), fileName);
    }

    private String copyUriToInternalStorage(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            File file = createPhotoFile();
            FileOutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void sauvegarderNote() {
        String titre = etTitre.getText().toString().trim();
        String contenu = etContenu.getText().toString().trim();

        if (titre.isEmpty()) {
            etTitre.setError("Titre obligatoire");
            return;
        }

        showLoading("Sauvegarde en cours...");

        executor.execute(() -> {
            try {
                Note note;
                boolean isNew = (editingNoteId == -1);
                
                if (!isNew) {
                    note = NoteDatabase.getInstance(this).noteDao().getNoteById(editingNoteId);
                    note.setTitre(titre);
                    note.setContenu(contenu);
                    NoteDatabase.getInstance(this).noteDao().update(note);
                } else {
                    String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
                    note = new Note();
                    note.setTitre(titre);
                    note.setContenu(contenu);
                    note.setDate(date);
                    note.setSubject(selectedSubject);
                    note.setAuthor(sessionManager.getUsername());
                    editingNoteId = (int) NoteDatabase.getInstance(this).noteDao().insert(note);
                    note.setId(editingNoteId);
                }
                
                // On récupère les nouvelles images à uploader (optionnel maintenant)
                List<String> newPaths = new ArrayList<>();
                for (String path : photoPaths) {
                    if (!existingPhotoPaths.contains(path)) {
                        if (!path.startsWith("http")) {
                            NoteDatabase.getInstance(this).noteDao().insertImage(new NoteImage(editingNoteId, path));
                        }
                        newPaths.add(path);
                    }
                }

                Note noteToUpload = NoteDatabase.getInstance(this).noteDao().getNoteById(editingNoteId);
                
                cloudSyncHelper.uploadNote(noteToUpload, newPaths, new CloudSyncHelper.SyncCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            hideLoading();
                            Toast.makeText(AddNoteActivity.this, "Note enregistrée et synchronisée !", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        runOnUiThread(() -> {
                            hideLoading();
                            Toast.makeText(AddNoteActivity.this, "Sauvegarde locale OK, mais erreur Cloud : " + e.getMessage(), Toast.LENGTH_LONG).show();
                            finish();
                        });
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(this, "Erreur lors de la sauvegarde : " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
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

    private class PhotoPreviewAdapter extends RecyclerView.Adapter<PhotoPreviewAdapter.ViewHolder> {
        private List<String> paths;

        PhotoPreviewAdapter(List<String> paths) {
            this.paths = paths;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_preview, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String path = paths.get(position);
            Bitmap bitmap = BitmapFactory.decodeFile(path, new BitmapFactory.Options() {{ inSampleSize = 4; }});
            holder.ivPreview.setImageBitmap(bitmap);
            holder.btnRemove.setOnClickListener(v -> {
                paths.remove(position);
                notifyDataSetChanged();
                if (paths.isEmpty()) recyclerPhotos.setVisibility(View.GONE);
            });
        }

        @Override
        public int getItemCount() {
            return paths.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPreview;
            View btnRemove;
            ViewHolder(View view) {
                super(view);
                ivPreview = view.findViewById(R.id.iv_preview);
                btnRemove = view.findViewById(R.id.btn_remove_photo);
            }
        }
    }
}
