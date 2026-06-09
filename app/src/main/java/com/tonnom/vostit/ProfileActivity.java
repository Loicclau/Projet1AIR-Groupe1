package com.tonnom.vostit;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.tonnom.vostit.database.NoteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        SessionManager sessionManager = new SessionManager(this);
        String username = sessionManager.getUsername();

        TextView tvGreeting = findViewById(R.id.tv_greeting);
        tvGreeting.setText("Bonjour " + (username != null ? username : "Utilisateur"));

        findViewById(R.id.btn_logout_profile).setOnClickListener(v -> {
            sessionManager.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        setupPreferences(sessionManager);
        setupThemeToggle(sessionManager);
        setupBottomNavigation();
    }

    private void setupPreferences(SessionManager sessionManager) {
        String[] specialties = {
                "Automatique et Systèmes Embarqués",
                "Informatique et Réseaux",
                "Mécanique",
                "Textile",
                "Cycle post-bac intégré"
        };
        String[] years = {"1ère année", "2ème année", "3ème année"};

        AutoCompleteTextView autoSpecialty = findViewById(R.id.auto_specialty);
        AutoCompleteTextView autoYear = findViewById(R.id.auto_year);

        ArrayAdapter<String> adapterSpec = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, specialties);
        autoSpecialty.setAdapter(adapterSpec);
        
        String currentSpec = sessionManager.getFavoriteSpecialty();
        if (currentSpec != null) {
            autoSpecialty.setText(currentSpec, false);
        }
        
        autoSpecialty.setOnItemClickListener((parent, view, position, id) -> {
            sessionManager.setFavoriteSpecialty(specialties[position]);
        });

        ArrayAdapter<String> adapterYear = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, years);
        autoYear.setAdapter(adapterYear);
        
        String currentYear = sessionManager.getFavoriteYear();
        if (currentYear != null) {
            autoYear.setText(currentYear, false);
        }
        
        autoYear.setOnItemClickListener((parent, view, position, id) -> {
            sessionManager.setFavoriteYear(years[position]);
        });
    }

    private void setupStatistics(SessionManager sessionManager, String username) {
        TextView tvNotesCount = findViewById(R.id.tv_stats_notes_count);
        TextView tvQuotaText = findViewById(R.id.tv_stats_synthesis_quota);
        LinearProgressIndicator progressQuota = findViewById(R.id.progress_synthesis_quota);

        // Quota synthèses (SessionManager)
        int currentQuota = sessionManager.getDailySynthesisCount();
        tvQuotaText.setText(currentQuota + "/10");
        progressQuota.setProgress(currentQuota);

        // Nombre de notes (Base de données)
        if (username != null) {
            executor.execute(() -> {
                int count = NoteDatabase.getInstance(this).noteDao().countNotesByUser(username);
                runOnUiThread(() -> tvNotesCount.setText(String.valueOf(count)));
            });
        }
    }

    private void setupThemeToggle(SessionManager sessionManager) {
        MaterialButtonToggleGroup toggleGroup = findViewById(R.id.toggle_theme);
        
        // Initialiser l'état du toggle
        if (sessionManager.isDarkMode()) {
            toggleGroup.check(R.id.btn_dark_mode);
        } else {
            toggleGroup.check(R.id.btn_light_mode);
        }

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_dark_mode) {
                    sessionManager.setDarkMode(true);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                } else if (checkedId == R.id.btn_light_mode) {
                    sessionManager.setDarkMode(false);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        SessionManager sessionManager = new SessionManager(this);
        setupStatistics(sessionManager, sessionManager.getUsername());

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_profile);
        }
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_subjects) {
                Intent intent = new Intent(this, SubjectSelectionActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_syntheses) {
                Intent intent = new Intent(this, SynthesisListActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            }
            return id == R.id.nav_profile;
        });
    }
}
