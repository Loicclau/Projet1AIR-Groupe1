package com.tonnom.vostit;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.tonnom.vostit.adapter.NoteAdapter;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Note;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private NoteAdapter adapter;
    private View emptyStateLayout;
    private String selectedSubject;
    private SessionManager sessionManager;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);

        selectedSubject = getIntent().getStringExtra("SELECTED_SUBJECT");
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null && selectedSubject != null) {
            getSupportActionBar().setTitle(selectedSubject);
        }

        RecyclerView recycler = findViewById(R.id.recycler_notes);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NoteAdapter(new ArrayList<>(), note -> {
            Intent intent = new Intent(MainActivity.this, NoteDetailActivity.class);
            intent.putExtra("NOTE_ID", note.getId());
            startActivity(intent);
        });
        recycler.setAdapter(adapter);

        emptyStateLayout = findViewById(R.id.layout_empty_state);

        ExtendedFloatingActionButton fabAdd = findViewById(R.id.fab_add);
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddNoteActivity.class);
            intent.putExtra("SELECTED_SUBJECT", selectedSubject);
            startActivity(intent);
        });

        chargerNotes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        chargerNotes();
    }

    private void chargerNotes() {
        executor.execute(() -> {
            List<Note> notes;
            if (selectedSubject != null) {
                notes = NoteDatabase.getInstance(this).noteDao().getNotesBySubject(selectedSubject);
            } else {
                notes = NoteDatabase.getInstance(this).noteDao().getAllNotes();
            }
            runOnUiThread(() -> {
                adapter.setNotes(notes);
                emptyStateLayout.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }
}