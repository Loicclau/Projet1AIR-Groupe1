package com.tonnom.vostit;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Note;
import com.tonnom.vostit.model.NoteImage;
import com.tonnom.vostit.ocr.OcrHelper;

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

    private ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bitmap photoBitmap = (Bitmap) result.getData().getExtras().get("data");
                    
                    // Sauvegarder l'image sur le disque
                    String path = saveBitmapToFile(photoBitmap);
                    if (path != null) {
                        photoPaths.add(path);
                        photoAdapter.notifyDataSetChanged();
                        recyclerPhotos.setVisibility(View.VISIBLE);
                    }

                    // OCR sur la photo
                    OcrHelper.extractText(photoBitmap, text -> {
                        runOnUiThread(() -> {
                            String current = etContenu.getText().toString();
                            etContenu.setText(current + "\n" + text);
                        });
                    });
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_note);

        sessionManager = new SessionManager(this);

        selectedSubject = getIntent().getStringExtra("SELECTED_SUBJECT");
        if (selectedSubject != null) {
            setTitle("Ajouter : " + selectedSubject);
        }

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
            cameraLauncher.launch(intent);
        });

        btnSauvegarder.setOnClickListener(v -> sauvegarderNote());
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
            holder.ivPreview.setImageBitmap(BitmapFactory.decodeFile(paths.get(position)));
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
