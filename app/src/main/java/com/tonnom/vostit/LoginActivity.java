package com.tonnom.vostit;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.textfield.TextInputEditText;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.User;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etUsername, etPassword;
    private SessionManager sessionManager;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sessionManager = new SessionManager(this);
        // Appliquer le thème dès le démarrage
        if (sessionManager.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, SubjectSelectionActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_login_username);
        etPassword = findViewById(R.id.et_login_password);
        Button btnLogin = findViewById(R.id.btn_login);
        TextView tvSignup = findViewById(R.id.tv_go_to_signup);

        btnLogin.setOnClickListener(v -> loginUser());
        tvSignup.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
    }

    private void loginUser() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            User user = NoteDatabase.getInstance(this).userDao().login(username, password);
            runOnUiThread(() -> {
                if (user != null) {
                    sessionManager.createLoginSession(username);
                    startActivity(new Intent(this, SubjectSelectionActivity.class));
                    finish();
                } else {
                    Toast.makeText(this, "Identifiants incorrects", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
