package com.tonnom.vostit;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SubjectListActivity extends AppCompatActivity {

    private String specialty;
    private String year;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_list);

        specialty = getIntent().getStringExtra("SELECTED_SPECIALTY");
        year = getIntent().getStringExtra("SELECTED_YEAR");

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        TextView tvSubtitle = findViewById(R.id.tv_subtitle);
        tvSubtitle.setText("Cours - " + year);

        List<String> subjects = getSubjectsFor(specialty, year);

        RecyclerView recyclerView = findViewById(R.id.recycler_subjects);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new SubjectAdapter(subjects, subject -> {
            Intent intent = new Intent(SubjectListActivity.this, MainActivity.class);
            intent.putExtra("SELECTED_SUBJECT", subject);
            startActivity(intent);
        }));
    }

    private List<String> getSubjectsFor(String specialty, String year) {
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

    private static class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.ViewHolder> {
        private final List<String> subjects;
        private final OnSubjectClickListener listener;

        interface OnSubjectClickListener {
            void onSubjectClick(String subject);
        }

        SubjectAdapter(List<String> subjects, OnSubjectClickListener listener) {
            this.subjects = subjects;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subject, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String subject = subjects.get(position);
            holder.tvName.setText(subject);
            holder.ivIcon.setImageResource(android.R.drawable.ic_menu_agenda);
            holder.itemView.setOnClickListener(v -> listener.onSubjectClick(subject));
        }

        @Override
        public int getItemCount() {
            return subjects.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            ImageView ivIcon;
            ViewHolder(View view) {
                super(view);
                tvName = view.findViewById(R.id.tv_subject_name);
                ivIcon = view.findViewById(R.id.iv_subject_icon);
            }
        }
    }
}
