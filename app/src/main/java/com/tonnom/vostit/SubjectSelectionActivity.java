package com.tonnom.vostit;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

public class SubjectSelectionActivity extends AppCompatActivity {

    private final List<String> subjects = Arrays.asList(
            "Java",
            "UML",
            "Statistiques et systèmes stochastiques",
            "Sécurité des programmes",
            "SGBD",
            "BI",
            "DDRS"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_selection);

        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            new SessionManager(this).logout();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        findViewById(R.id.btn_view_syntheses).setOnClickListener(v -> {
            startActivity(new Intent(this, SynthesisListActivity.class));
        });

        RecyclerView recyclerView = findViewById(R.id.recycler_subjects);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new SubjectAdapter(subjects, subject -> {
            Intent intent = new Intent(SubjectSelectionActivity.this, MainActivity.class);
            intent.putExtra("SELECTED_SUBJECT", subject);
            startActivity(intent);
        }));
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
            holder.itemView.setOnClickListener(v -> listener.onSubjectClick(subject));
        }

        @Override
        public int getItemCount() {
            return subjects.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            ViewHolder(View view) {
                super(view);
                tvName = view.findViewById(R.id.tv_subject_name);
            }
        }
    }
}
