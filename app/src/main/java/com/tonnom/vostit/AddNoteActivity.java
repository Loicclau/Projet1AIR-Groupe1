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

import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Note;
import com.tonnom.vostit.model.NoteImage;
import com.tonnom.vostit.utils.CloudSyncHelper;

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
    private View loadingOverlay;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Uri currentPhotoUri;
    private String currentPhotoPath;

    private ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (currentPhotoPath != null) {
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
        loadingOverlay = findViewById(R.id.loading_overlay);
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

        loadingOverlay.setVisibility(View.VISIBLE);

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
                
                // On récupère les nouvelles images à uploader
                List<String> newPaths = new ArrayList<>();
                for (String path : photoPaths) {
                    if (!existingPhotoPaths.contains(path)) {
                        if (!path.startsWith("http")) {
                            NoteDatabase.getInstance(this).noteDao().insertImage(new NoteImage(editingNoteId, path));
                        }
                        newPaths.add(path);
                    }
                }

                // Récupération de l'objet Note mis à jour (avec ses éventuelles URLs distantes déjà présentes)
                Note noteToUpload = NoteDatabase.getInstance(this).noteDao().getNoteById(editingNoteId);
                
                // Lancement de la synchro Cloud avec callback pour fermer l'activité proprement
                cloudSyncHelper.uploadNote(noteToUpload, newPaths, new CloudSyncHelper.SyncCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            loadingOverlay.setVisibility(View.GONE);
                            Toast.makeText(AddNoteActivity.this, "Note enregistrée et synchronisée !", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        runOnUiThread(() -> {
                            loadingOverlay.setVisibility(View.GONE);
                            Toast.makeText(AddNoteActivity.this, "Sauvegarde locale OK, mais erreur Cloud : " + e.getMessage(), Toast.LENGTH_LONG).show();
                            finish(); // On ferme quand même car la sauvegarde locale est réussie
                        });
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(this, "Erreur lors de la sauvegarde : " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
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
