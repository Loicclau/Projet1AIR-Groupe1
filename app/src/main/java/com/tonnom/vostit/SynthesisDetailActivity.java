package com.tonnom.vostit;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Synthesis;
import com.tonnom.vostit.utils.PdfExportHelper;
import com.tonnom.vostit.utils.SynthesisFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SynthesisDetailActivity extends AppCompatActivity {

    private int synthesisId;
    private Synthesis currentSynthesis;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private View loadingOverlay;
    private TextView tvContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_synthesis_detail);

        synthesisId = getIntent().getIntExtra("SYNTHESIS_ID", -1);

        Toolbar toolbar = findViewById(R.id.toolbar_detail);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        tvContent = findViewById(R.id.tv_synthesis_content);
        loadingOverlay = findViewById(R.id.loading_overlay);

        findViewById(R.id.btn_export_pdf).setOnClickListener(v -> exportToPdf());
        findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());

        loadSynthesis();
    }

    private void loadSynthesis() {
        showLoading(true);
        executor.execute(() -> {
            currentSynthesis = NoteDatabase.getInstance(this).synthesisDao().getById(synthesisId);
            runOnUiThread(() -> {
                showLoading(false);
                if (currentSynthesis != null) {
                    displaySynthesis();
                } else {
                    Toast.makeText(this, "Synthèse introuvable", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        });
    }

    private void displaySynthesis() {
        TextView tvTitle = findViewById(R.id.tv_synthesis_title);
        TextView tvDate = findViewById(R.id.tv_synthesis_date);

        tvTitle.setText(currentSynthesis.getSubject());
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy 'à' HH:mm", Locale.getDefault());
        tvDate.setText("Généré le " + sdf.format(new Date(currentSynthesis.getTimestamp())));

        int primaryColor = ContextCompat.getColor(this, R.color.vostit_primary);
        int secondaryColor = ContextCompat.getColor(this, R.color.vostit_secondary);

        tvContent.setText(SynthesisFormatter.format(currentSynthesis.getContent(), primaryColor, secondaryColor));
        
        // Animation d'apparition
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(800);
        findViewById(R.id.card_synthesis).startAnimation(fadeIn);
    }

    private void exportToPdf() {
        showLoading(true);
        executor.execute(() -> {
            PdfExportHelper.exportToPdf(this, "Synthèse " + currentSynthesis.getSubject(), currentSynthesis.getContent());
            runOnUiThread(() -> {
                showLoading(false);
                Toast.makeText(this, "Exportation terminée", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer la synthèse")
                .setMessage("Voulez-vous vraiment supprimer cette synthèse intelligente ?")
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    deleteSynthesis();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void deleteSynthesis() {
        showLoading(true);
        executor.execute(() -> {
            NoteDatabase.getInstance(this).synthesisDao().deleteById(synthesisId);
            runOnUiThread(() -> {
                showLoading(false);
                Toast.makeText(this, "Synthèse supprimée", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
