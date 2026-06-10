package com.tonnom.vostit;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Synthesis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QcmSetupActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String selectedSpecialty;
    private String selectedYear;
    private String selectedSubject;
    private int numQuestions = 5;
    private String difficulty = "Moyen";
    private String genMode = "Strict";

    private AutoCompleteTextView autoSpecialty, autoYear, autoSubject;
    private View cardQcmSettings, cardNoSynthesis;
    private TextView tvNumQuestionsLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qcm_setup);

        autoSpecialty = findViewById(R.id.auto_specialty);
        autoYear = findViewById(R.id.auto_year);
        autoSubject = findViewById(R.id.auto_subject);
        cardQcmSettings = findViewById(R.id.card_qcm_settings);
        cardNoSynthesis = findViewById(R.id.card_no_synthesis);
        tvNumQuestionsLabel = findViewById(R.id.tv_num_questions_label);

        setupSpinners();
        setupSettings();
        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_qcm);
        }
    }

    private void setupSpinners() {
        String[] specialties = {
                "Automatique et Systèmes Embarqués",
                "Informatique et Réseaux",
                "Mécanique",
                "Textile",
                "Cycle post-bac intégré"
        };
        
        NoFilterAdapter adapterSpec = new NoFilterAdapter(this, android.R.layout.simple_dropdown_item_1line, specialties);
        autoSpecialty.setAdapter(adapterSpec);
        autoSpecialty.setOnItemClickListener((parent, view, position, id) -> {
            selectedSpecialty = adapterSpec.getItem(position);
            updateYearSpinner();
            checkSynthesis();
        });

        autoYear.setOnItemClickListener((parent, view, position, id) -> {
            selectedYear = (String) parent.getItemAtPosition(position);
            updateSubjectSpinner();
            checkSynthesis();
        });

        autoSubject.setOnItemClickListener((parent, view, position, id) -> {
            selectedSubject = (String) parent.getItemAtPosition(position);
            checkSynthesis();
        });

        // Pre-fill from session if available
        SessionManager sm = new SessionManager(this);
        String favSpec = sm.getFavoriteSpecialty();
        if (favSpec != null) {
            autoSpecialty.setText(favSpec, false);
            selectedSpecialty = favSpec;
            updateYearSpinner();
            
            String favYear = sm.getFavoriteYear();
            if (favYear != null) {
                autoYear.setText(favYear, false);
                selectedYear = favYear;
                updateSubjectSpinner();
            }
        }
    }

    private void updateYearSpinner() {
        String[] years = {"1ère année", "2ème année", "3ème année"};
        if ("Cycle post-bac intégré".equals(selectedSpecialty)) {
            years = new String[]{"1ère année", "2ème année"};
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, years);
        autoYear.setAdapter(adapter);
        autoYear.setText("", false);
        selectedYear = null;
        autoSubject.setAdapter(null);
        autoSubject.setText("", false);
        selectedSubject = null;
    }

    private void updateSubjectSpinner() {
        if (selectedSpecialty == null || selectedYear == null) return;
        
        List<String> subjects = getSubjectsFor(selectedSpecialty, selectedYear);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, subjects);
        autoSubject.setAdapter(adapter);
        autoSubject.setText("", false);
        selectedSubject = null;
    }

    private List<String> getSubjectsFor(String specialty, String year) {
        // Mock data from SubjectListActivity
        if ("Automatique et Systèmes Embarqués".equals(specialty)) {
            if (year.contains("1ère")) return Arrays.asList("Machines tournantes", "Unix", "SysML", "Systèmes discrets", "ANCS", "AOO Python", "Introduction à Python");
            if (year.contains("2ème")) return Arrays.asList("Logique floue", "CEM", "Low-Tech", "Systèmes embarqués", "Acquisition de données", "Automatique programmable", "Systèmes non linéaires", "Physique appliquée");
            if (year.contains("3ème")) return Arrays.asList("DDE", "Commandes multi-modèles", "Diagnostic", "Imagerie non conventionnelle", "Surveillance", "Traitement d'image et vision", "Optimisation des paramètres");
        } else if ("Mécanique".equals(specialty)) {
            if (year.contains("1ère")) return Arrays.asList("Mécanique générale", "Métrologie dimensionnelle", "CAO", "Identification et asservissement", "Caractérisation des matériaux", "Mécanique des fluides", "Fabrication additive");
            if (year.contains("2ème")) return Arrays.asList("CAO", "Caractérisation des matériaux", "Étude des systèmes", "Fabrication", "Vibrations", "Simulation numérique", "Automatisme");
            if (year.contains("3ème")) return Arrays.asList("Coupe et optimisation", "Fabrication avancée", "Outils numériques pour la mécanique", "Modélisation de descente", "Rétroconception", "Management", "Solides déformables");
        } else if ("Textile".equals(specialty)) {
            if (year.contains("1ère")) return Arrays.asList("Filature", "Identification et asservissement", "Métrologie", "Thermodynamique", "MSD", "Tissage", "Chimie organique et polymères", "Maille", "Qualité");
            if (year.contains("2ème")) return Arrays.asList("Ennoblissement", "Tissage", "Automatisme", "TP Métrologie", "Fils et filature", "Maille", "Plastiques et composites");
            if (year.contains("3ème")) return Arrays.asList("CFAO", "Maille", "Filage", "Qualité et confection", "Management", "Gestion et organisation", "Supply Chain", "Textile intelligent");
        } else if ("Informatique et Réseaux".equals(specialty)) {
            if (year.contains("1ère")) return Arrays.asList("Systèmes d'exploitation", "Unix base", "AOO Java", "BI", "SGBD", "Modélisation UML", "Statistiques et systèmes stochastiques");
            if (year.contains("2ème")) return Arrays.asList("Robotique", "Fouille de données", "Théorie des langages", "Computer Graphics", "Cloud Computing", "Réseaux", "Compilation", "Cryptographie", "Initiation à la recherche", "Écoconception", "Analyse des risques et prévention");
            if (year.contains("3ème")) return Arrays.asList("Algorithmes distribués", "NoSQL", "Architecture Big Data", "Mainframe", "Programmation iOS", "Moversys", "Programmation Android", "Programmation fonctionnelle", "Temps réel", "Applications n-tiers", "Mobilité et réseaux");
        } else if ("Cycle post-bac intégré".equals(specialty)) {
            if (year.contains("1ère")) return Arrays.asList("Régimes variables", "Transformation de la matière", "Chimie organique", "Mécanique du point", "EC Informatique", "Mécanique générale", "Électrocinétique");
            if (year.contains("2ème")) return Arrays.asList("Optique géométrique", "Électromagnétisme", "Techniques d'impression", "Algèbre linéaire et applications", "Cinétique", "Oscillateurs", "Matériaux et recyclage");
        }
        return new ArrayList<>();
    }

    private void checkSynthesis() {
        if (selectedSubject == null) {
            cardQcmSettings.setVisibility(View.GONE);
            cardNoSynthesis.setVisibility(View.GONE);
            return;
        }

        com.tonnom.vostit.utils.CloudSyncHelper cloudSyncHelper = new com.tonnom.vostit.utils.CloudSyncHelper(this);
        cloudSyncHelper.fetchLatestSynthesis(selectedSubject, synthesis -> {
            runOnUiThread(() -> {
                if (synthesis != null) {
                    cardQcmSettings.setVisibility(View.VISIBLE);
                    cardNoSynthesis.setVisibility(View.GONE);
                } else {
                    cardQcmSettings.setVisibility(View.GONE);
                    cardNoSynthesis.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void setupSettings() {
        Slider slider = findViewById(R.id.slider_num_questions);
        slider.addOnChangeListener((s, value, fromUser) -> {
            numQuestions = (int) value;
            tvNumQuestionsLabel.setText("Nombre de questions : " + numQuestions);
        });

        MaterialButtonToggleGroup toggleDifficulty = findViewById(R.id.toggle_difficulty);
        toggleDifficulty.check(R.id.btn_diff_medium);
        toggleDifficulty.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_diff_easy) difficulty = "Facile";
                else if (checkedId == R.id.btn_diff_medium) difficulty = "Moyen";
                else if (checkedId == R.id.btn_diff_hard) difficulty = "Difficile";
            }
        });

        MaterialButtonToggleGroup toggleMode = findViewById(R.id.toggle_gen_mode);
        toggleMode.check(R.id.btn_mode_strict);
        toggleMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_mode_strict) genMode = "Strict";
                else if (checkedId == R.id.btn_mode_extended) genMode = "Étendu";
            }
        });

        findViewById(R.id.btn_start_qcm).setOnClickListener(v -> startQcm());
    }

    private void startQcm() {
        if (selectedSubject == null) return;
        
        Intent intent = new Intent(this, QcmPlayActivity.class);
        intent.putExtra("SUBJECT", selectedSubject);
        intent.putExtra("NUM_QUESTIONS", numQuestions);
        intent.putExtra("DIFFICULTY", difficulty);
        intent.putExtra("MODE", genMode);
        startActivity(intent);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_subjects) {
                startActivity(new Intent(this, SubjectSelectionActivity.class).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_syntheses) {
                startActivity(new Intent(this, SynthesisListActivity.class).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_qcm) {
                return true;
            }
            return false;
        });
    }

    private static class NoFilterAdapter extends ArrayAdapter<String> {
        private final String[] items;
        public NoFilterAdapter(Context context, int resource, String[] objects) {
            super(context, resource, objects);
            this.items = objects;
        }
        @NonNull @Override public Filter getFilter() {
            return new Filter() {
                @Override protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = items;
                    results.count = items.length;
                    return results;
                }
                @Override protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
            };
        }
    }
}
