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

import java.util.Arrays;
import java.util.List;

public class YearSelectionActivity extends AppCompatActivity {

    private String selectedSpecialty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_year_selection);

        selectedSpecialty = getIntent().getStringExtra("SELECTED_SPECIALTY");
        TextView tvSubtitle = findViewById(R.id.tv_subtitle);
        tvSubtitle.setText("Spécialité : " + selectedSpecialty);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        List<String> displayYears = getFullYearNames(selectedSpecialty);

        RecyclerView recyclerView = findViewById(R.id.recycler_years);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new YearAdapter(displayYears, year -> {
            Intent intent = new Intent(YearSelectionActivity.this, SubjectListActivity.class);
            intent.putExtra("SELECTED_SPECIALTY", selectedSpecialty);
            intent.putExtra("SELECTED_YEAR", year);
            startActivity(intent);
        }));
    }

    private List<String> getFullYearNames(String specialty) {
        if ("Mécanique".equals(specialty)) {
            return Arrays.asList(
                    "Mécanique 1ère année",
                    "Mécanique 2ème année",
                    "Mécanique 3ème année"
            );
        }

        if ("Cycle post-bac intégré".equals(specialty)) {
            return Arrays.asList(
                    "Cycle post-bac intégré - 1ère année",
                    "Cycle post-bac intégré - 2ème année"
            );
        }

        return Arrays.asList(
                specialty + " - 1ère année",
                specialty + " - 2ème année",
                specialty + " - 3ème année"
        );
    }

    private static class YearAdapter extends RecyclerView.Adapter<YearAdapter.ViewHolder> {
        private final List<String> years;
        private final OnYearClickListener listener;

        interface OnYearClickListener {
            void onYearClick(String year);
        }

        YearAdapter(List<String> years, OnYearClickListener listener) {
            this.years = years;
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
            String year = years.get(position);
            holder.tvName.setText(year);
            holder.ivIcon.setImageResource(android.R.drawable.ic_menu_today);
            holder.itemView.setOnClickListener(v -> listener.onYearClick(year));
        }

        @Override
        public int getItemCount() {
            return years.size();
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
