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

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Arrays;
import java.util.List;

public class SubjectSelectionActivity extends AppCompatActivity {

    private final List<String> specialties = Arrays.asList(
            "Automatique et Systèmes Embarqués",
            "Informatique et Réseaux",
            "Textile",
            "Mécanique",
            "Cycle post-bac intégré"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_selection);

        RecyclerView recyclerView = findViewById(R.id.recycler_subjects);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new SpecialtyAdapter(specialties, specialty -> {
            Intent intent = new Intent(SubjectSelectionActivity.this, YearSelectionActivity.class);
            intent.putExtra("SELECTED_SPECIALTY", specialty);
            startActivity(intent);
        }));

        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_subjects);
        }
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_syntheses) {
                Intent intent = new Intent(this, SynthesisListActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_profile) {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_qcm) {
                Intent intent = new Intent(this, QcmSetupActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_subjects) {
                return true;
            }
            return false;
        });
    }

    private static class SpecialtyAdapter extends RecyclerView.Adapter<SpecialtyAdapter.ViewHolder> {
        private final List<String> specialties;
        private final OnSpecialtyClickListener listener;

        interface OnSpecialtyClickListener {
            void onSpecialtyClick(String specialty);
        }

        SpecialtyAdapter(List<String> specialties, OnSpecialtyClickListener listener) {
            this.specialties = specialties;
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
            String specialty = specialties.get(position);
            holder.tvName.setText(specialty);
            
            // Set specialty icon
            int iconRes;
            switch (specialty) {
                case "Informatique et Réseaux":
                    iconRes = R.drawable.ic_computer;
                    break;
                case "Mécanique":
                    iconRes = R.drawable.ic_gear;
                    break;
                case "Automatique et Systèmes Embarqués":
                    iconRes = R.drawable.ic_robot;
                    break;
                case "Textile":
                    iconRes = R.drawable.ic_textile;
                    break;
                case "Cycle post-bac intégré":
                    iconRes = R.drawable.ic_lab;
                    break;
                default:
                    iconRes = android.R.drawable.ic_menu_agenda;
            }
            holder.ivIcon.setImageResource(iconRes);
            
            holder.itemView.setOnClickListener(v -> listener.onSpecialtyClick(specialty));
        }

        @Override
        public int getItemCount() {
            return specialties.size();
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
