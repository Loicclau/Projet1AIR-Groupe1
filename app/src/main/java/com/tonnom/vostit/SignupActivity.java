package com.tonnom.vostit;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.User;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SignupActivity extends AppCompatActivity {

    private TextInputEditText etUsername, etPassword;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        etUsername = findViewById(R.id.et_signup_username);
        etPassword = findViewById(R.id.et_signup_password);
        Button btnSignup = findViewById(R.id.btn_signup);
        TextView tvLogin = findViewById(R.id.tv_go_to_login);

        btnSignup.setOnClickListener(v -> signupUser());
        tvLogin.setOnClickListener(v -> finish());
    }

    private void signupUser() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            User existing = NoteDatabase.getInstance(this).userDao().findByUsername(username);
            if (existing != null) {
                runOnUiThread(() -> Toast.makeText(this, "Ce nom d'utilisateur existe déjà", Toast.LENGTH_SHORT).show());
                return;
            }

            User newUser = new User(username, password);
            NoteDatabase.getInstance(this).userDao().insert(newUser);

            runOnUiThread(() -> {
                Toast.makeText(this, "Compte créé ! Connectez-vous", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
