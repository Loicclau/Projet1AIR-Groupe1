package com.tonnom.vostit;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class ProfileActivity extends AppCompatActivity {

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

        setupThemeToggle(sessionManager);
        setupBottomNavigation();
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
