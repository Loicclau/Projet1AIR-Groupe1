package com.tonnom.vostit;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Synthesis;
import com.tonnom.vostit.utils.PdfExportHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SynthesisListActivity extends AppCompatActivity {

    private SynthesisAdapter adapter;
    private View emptyState;
    private String filterSubject;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_synthesis_list);

        filterSubject = getIntent().getStringExtra("SELECTED_SUBJECT");

        Toolbar toolbar = findViewById(R.id.toolbar_synthesis);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        RecyclerView recyclerView = findViewById(R.id.recycler_syntheses);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new SynthesisAdapter(new ArrayList<>(), this::showSynthesisDetails);
        recyclerView.setAdapter(adapter);

        emptyState = findViewById(R.id.layout_empty_syntheses);

        loadSyntheses();
        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSyntheses();
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_syntheses);
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
            } else if (id == R.id.nav_profile) {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_syntheses) {
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        filterSubject = intent.getStringExtra("SELECTED_SUBJECT");
        loadSyntheses();
    }

    private void loadSyntheses() {
        executor.execute(() -> {
            List<Synthesis> list;
            if (filterSubject != null) {
                list = NoteDatabase.getInstance(this).synthesisDao().getAllForSubject(filterSubject);
            } else {
                list = NoteDatabase.getInstance(this).synthesisDao().getAllSyntheses();
            }
            runOnUiThread(() -> {
                adapter.setData(list);
                emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showSynthesisDetails(Synthesis synthesis) {
        Intent intent = new Intent(this, SynthesisDetailActivity.class);
        intent.putExtra("SYNTHESIS_ID", synthesis.getId());
        startActivity(intent);
    }

    private static class SynthesisAdapter extends RecyclerView.Adapter<SynthesisAdapter.ViewHolder> {
        private final List<Synthesis> syntheses;
        private final OnItemClickListener listener;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        interface OnItemClickListener {
            void onItemClick(Synthesis synthesis);
        }

        SynthesisAdapter(List<Synthesis> syntheses, OnItemClickListener listener) {
            this.syntheses = syntheses;
            this.listener = listener;
        }

        void setData(List<Synthesis> newList) {
            this.syntheses.clear();
            this.syntheses.addAll(newList);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_synthesis, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Synthesis s = syntheses.get(position);
            holder.tvSubject.setText(s.getSubject());
            holder.tvDate.setText(dateFormat.format(new Date(s.getTimestamp())));
            holder.tvPreview.setText(s.getContent());

            if (s.getYear() != null) {
                holder.tvInfo.setVisibility(View.VISIBLE);
                holder.tvInfo.setText(s.getYear());
            } else {
                holder.tvInfo.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> listener.onItemClick(s));
        }

        @Override
        public int getItemCount() {
            return syntheses.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvSubject, tvDate, tvPreview, tvInfo;
            ViewHolder(View view) {
                super(view);
                tvSubject = view.findViewById(R.id.tv_synthesis_subject);
                tvDate = view.findViewById(R.id.tv_synthesis_date);
                tvPreview = view.findViewById(R.id.tv_synthesis_preview);
                tvInfo = view.findViewById(R.id.tv_synthesis_info);
            }
        }
    }
}
