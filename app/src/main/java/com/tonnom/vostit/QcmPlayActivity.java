package com.tonnom.vostit;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.tonnom.vostit.ai.GeminiHelper;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Synthesis;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QcmPlayActivity extends AppCompatActivity {

    private String subject, difficulty, mode;
    private int numQuestions;
    private List<Question> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private int score = 0;

    private TextView tvQuestionNumber, tvQuestionText;
    private RadioGroup rgOptions;
    private MaterialButton btnNext;
    private LinearProgressIndicator progressBar;
    private View loadingOverlay;

    private GeminiHelper geminiHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qcm_play);

        subject = getIntent().getStringExtra("SUBJECT");
        numQuestions = getIntent().getIntExtra("NUM_QUESTIONS", 5);
        difficulty = getIntent().getStringExtra("DIFFICULTY");
        mode = getIntent().getStringExtra("MODE");

        tvQuestionNumber = findViewById(R.id.tv_question_number);
        tvQuestionText = findViewById(R.id.tv_question_text);
        rgOptions = findViewById(R.id.rg_options);
        btnNext = findViewById(R.id.btn_next_question);
        progressBar = findViewById(R.id.progress_qcm);
        loadingOverlay = findViewById(R.id.loading_overlay);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar_play);
        toolbar.setNavigationOnClickListener(v -> finish());

        SessionManager sm = new SessionManager(this);
        geminiHelper = new GeminiHelper(BuildConfig.GEMINI_API_KEYS, BuildConfig.GROQ_API_KEY);

        generateQcm();

        btnNext.setOnClickListener(v -> handleNext());
    }

    private void generateQcm() {
        loadingOverlay.setVisibility(View.VISIBLE);
        
        com.tonnom.vostit.utils.CloudSyncHelper cloudSyncHelper = new com.tonnom.vostit.utils.CloudSyncHelper(this);
        cloudSyncHelper.fetchLatestSynthesis(subject, synthesis -> {
            if (synthesis == null) {
                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(this, "Erreur: Synthèse introuvable sur le Cloud", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }

            executor.execute(() -> {
                String prompt = buildPrompt(synthesis.getContent());
                com.google.common.util.concurrent.ListenableFuture<String> future = geminiHelper.generateWithGroq(prompt, "Tu es un expert en pédagogie qui génère des QCM au format JSON.");
                
                com.google.common.util.concurrent.Futures.addCallback(future, new com.google.common.util.concurrent.FutureCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        parseQuestions(result);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        runOnUiThread(() -> {
                            loadingOverlay.setVisibility(View.GONE);
                            Toast.makeText(QcmPlayActivity.this, "Erreur lors de la génération", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                }, executor);
            });
        });
    }

    private String buildPrompt(String content) {
        return "Génère un QCM de " + numQuestions + " questions au format JSON strict.\n" +
                "Difficulté : " + difficulty + ".\n" +
                "Mode : " + (mode.equals("Strict") ? "Uniquement basé sur le contenu fourni." : "Peut inclure des notions proches du sujet.") + "\n\n" +
                "CONTENU :\n" +
                content + "\n\n" +
                "FORMAT JSON ATTENDU (tableau d'objets) :\n" +
                "[\n" +
                "  {\n" +
                "    \"question\": \"Texte de la question\",\n" +
                "    \"options\": [\"Choix A\", \"Choix B\", \"Choix C\", \"Choix D\"],\n" +
                "    \"answer\": 0\n" +
                "  }\n" +
                "]\n" +
                "RETOURNE UNIQUEMENT LE JSON.";
    }

    private void parseQuestions(String jsonStr) {
        try {
            // Nettoyage du Markdown si présent
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7, jsonStr.lastIndexOf("```"));
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3, jsonStr.lastIndexOf("```"));
            }
            jsonStr = jsonStr.trim();

            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String qText = obj.getString("question");
                JSONArray optsArray = obj.getJSONArray("options");
                List<String> opts = new ArrayList<>();
                for (int j = 0; j < optsArray.length(); j++) opts.add(optsArray.getString(j));
                int ans = obj.getInt("answer");
                questions.add(new Question(qText, opts, ans));
            }

            runOnUiThread(() -> {
                loadingOverlay.setVisibility(View.GONE);
                if (questions.size() < numQuestions) {
                    Toast.makeText(this, "Synthèse trop courte, moins de questions générées.", Toast.LENGTH_LONG).show();
                }
                displayQuestion();
            });
        } catch (Exception e) {
            Log.e("QcmPlay", "Parsing error", e);
            runOnUiThread(() -> {
                loadingOverlay.setVisibility(View.GONE);
                Toast.makeText(this, "Erreur de format IA. Réessayez.", Toast.LENGTH_SHORT).show();
                finish();
            });
        }
    }

    private void displayQuestion() {
        if (currentQuestionIndex >= questions.size()) {
            showResult();
            return;
        }

        Question q = questions.get(currentQuestionIndex);
        tvQuestionNumber.setText("Question " + (currentQuestionIndex + 1) + "/" + questions.size());
        tvQuestionText.setText(q.text);
        progressBar.setProgress((int) (((float) (currentQuestionIndex + 1) / questions.size()) * 100));

        rgOptions.removeAllViews();
        for (int i = 0; i < q.options.size(); i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(q.options.get(i));
            rb.setId(i);
            rb.setPadding(16, 16, 16, 16);
            rgOptions.addView(rb);
        }

        rgOptions.setOnCheckedChangeListener((group, checkedId) -> btnNext.setEnabled(true));
        btnNext.setEnabled(false);
        btnNext.setText(currentQuestionIndex == questions.size() - 1 ? "Terminer" : "Valider");
    }

    private void handleNext() {
        int selectedId = rgOptions.getCheckedRadioButtonId();
        if (questions.get(currentQuestionIndex).correctAnswerIndex == selectedId) {
            score++;
        }

        currentQuestionIndex++;
        displayQuestion();
    }

    private void showResult() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("QCM Terminé")
                .setMessage("Votre score : " + score + "/" + questions.size())
                .setCancelable(false)
                .setPositiveButton("Fermer", (dialog, which) -> finish())
                .show();
    }

    private static class Question {
        String text;
        List<String> options;
        int correctAnswerIndex;

        Question(String text, List<String> options, int correctAnswerIndex) {
            this.text = text;
            this.options = options;
            this.correctAnswerIndex = correctAnswerIndex;
        }
    }
}
