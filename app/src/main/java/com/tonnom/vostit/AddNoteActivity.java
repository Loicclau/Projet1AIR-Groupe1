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
import android.widget.Button;
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
import com.tonnom.vostit.ocr.OcrHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
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
    private String selectedSubject;
    private SessionManager sessionManager;
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

                        // OCR sur la photo (Charger le bitmap proprement)
                        Bitmap fullSizeBitmap = loadLowResBitmap(currentPhotoPath, 1080);
                        if (fullSizeBitmap != null) {
                            OcrHelper.extractText(fullSizeBitmap, 0, jsonResponse -> {
                                runOnUiThread(() -> {
                                    try {
                                        JSONObject json = new JSONObject(jsonResponse);
                                        if ("success".equals(json.getString("status"))) {
                                            String text = json.getString("text");
                                            String current = etContenu.getText().toString();
                                            etContenu.setText(current + "\n" + text);
                                        } else if ("empty".equals(json.getString("status"))) {
                                            Toast.makeText(this, "Aucun texte détecté", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(this, "Erreur OCR", Toast.LENGTH_SHORT).show();
                                        }
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                });
                            });
                        }
                    }
                }
            });

    private Bitmap loadLowResBitmap(String path, int targetWidth) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        int srcWidth = options.outWidth;
        int sampleSize = 1;
        while (srcWidth / 2 >= targetWidth) {
            srcWidth /= 2;
            sampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeFile(path, options);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_note);

        sessionManager = new SessionManager(this);

        selectedSubject = getIntent().getStringExtra("SELECTED_SUBJECT");
        
        Toolbar toolbar = findViewById(R.id.toolbar_add_note);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (selectedSubject != null) {
                getSupportActionBar().setTitle("Nouveau : " + selectedSubject);
            } else {
                getSupportActionBar().setTitle("Nouvelle Note");
            }
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etTitre = findViewById(R.id.et_titre);
        etContenu = findViewById(R.id.et_contenu);
        recyclerPhotos = findViewById(R.id.recycler_add_photos);
        
        photoAdapter = new PhotoPreviewAdapter(photoPaths);
        recyclerPhotos.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerPhotos.setAdapter(photoAdapter);

        Button btnCamera = findViewById(R.id.btn_camera);
        Button btnSauvegarder = findViewById(R.id.btn_sauvegarder);

        btnCamera.setOnClickListener(v -> {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File photoFile = createPhotoFile();
            if (photoFile != null) {
                currentPhotoPath = photoFile.getAbsolutePath();
                currentPhotoUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                cameraLauncher.launch(intent);
            }
        });

        btnSauvegarder.setOnClickListener(v -> sauvegarderNote());
    }

    private File createPhotoFile() {
        String fileName = "IMG_" + UUID.randomUUID().toString() + ".jpg";
        return new File(getFilesDir(), fileName);
    }

    private String saveBitmapToFile(Bitmap bitmap) {
        String fileName = "IMG_" + UUID.randomUUID().toString() + ".jpg";
        File file = new File(getFilesDir(), fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
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

        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        Note note = new Note();
        note.setTitre(titre);
        note.setContenu(contenu);
        note.setDate(date);
        note.setSubject(selectedSubject);
        note.setAuthor(sessionManager.getUsername());

        executor.execute(() -> {
            long noteId = NoteDatabase.getInstance(this).noteDao().insert(note);
            
            // Sauvegarder les liens vers les images
            for (String path : photoPaths) {
                NoteImage noteImage = new NoteImage((int) noteId, path);
                NoteDatabase.getInstance(this).noteDao().insertImage(noteImage);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "Note sauvegardée ✓", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private static class PhotoPreviewAdapter extends RecyclerView.Adapter<PhotoPreviewAdapter.ViewHolder> {
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
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4; // Preview low res for list
            Bitmap bitmap = BitmapFactory.decodeFile(path, options);
            holder.ivPreview.setImageBitmap(bitmap);
        }

        @Override
        public int getItemCount() {
            return paths.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPreview;
            ViewHolder(View view) {
                super(view);
                ivPreview = view.findViewById(R.id.iv_preview);
            }
        }
    }
}
