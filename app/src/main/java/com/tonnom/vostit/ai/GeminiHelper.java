package com.tonnom.vostit.ai;

import android.graphics.Bitmap;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiHelper {
    // For Gemini (OCR)
    private final List<GenerativeModelFutures> geminiModels = new java.util.ArrayList<>();
    private int currentKeyIndex = 0;
    private static final String GEMINI_MODEL_NAME = "gemini-2.5-flash";

    // For Groq (Synthesis)
    private final String groqApiKey;
    private final OkHttpClient groqClient;
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL_TEXT = "llama-3.3-70b-versatile";

    public GeminiHelper(String geminiApiKeys, String groqApiKey) {
        // Initialize Gemini with multiple keys for rotation
        if (geminiApiKeys != null && !geminiApiKeys.isEmpty()) {
            String[] keys = geminiApiKeys.split(",");
            for (String key : keys) {
                if (!key.trim().isEmpty()) {
                    GenerativeModel gm = new GenerativeModel(GEMINI_MODEL_NAME, key.trim());
                    geminiModels.add(GenerativeModelFutures.from(gm));
                }
            }
        }
        
        // Initialize Groq
        this.groqApiKey = groqApiKey;
        this.groqClient = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    /**
     * Get the next model in rotation
     */
    private GenerativeModelFutures getNextModel() {
        if (geminiModels.isEmpty()) return null;
        GenerativeModelFutures model = geminiModels.get(currentKeyIndex);
        currentKeyIndex = (currentKeyIndex + 1) % geminiModels.size();
        return model;
    }

    /**
     * OCR using Gemini with key rotation support
     */
    public ListenableFuture<GenerateContentResponse> extractAndCleanText(Bitmap bitmap) {
        GenerativeModelFutures model = getNextModel();
        if (model == null) return null;

        String prompt = "Tu es un expert en OCR (Reconnaissance Optique de Caractères) et en traitement de documents. " +
                "Ton objectif est d'extraire le texte de cette image (notes manuscrites ou imprimées), de le nettoyer et de le reformuler de manière structurée.\n\n" +
                "INSTRUCTIONS :\n" +
                "1. EXTRACTION : Lis tout le texte présent dans l'image.\n" +
                "2. NETTOYAGE : Corrige les fautes d'orthographe, de grammaire et les erreurs courantes d'OCR (lettres confondues).\n" +
                "3. REFORMULATION : Si le texte est fragmenté (tirets, bribes de phrases), reformule-le en paragraphes fluides ou en listes à puces claires tout en restant 100% fidèle au sens original.\n" +
                "4. STRUCTURE : Utilise du Markdown pour structurer (Titres #, gras **, listes -).\n" +
                "5. VÉRIFICATION : Si le texte est totalement illisible ou incohérent, renvoie exactement : \"[ERREUR: TEXTE ILLISIBLE]\".\n\n" +
                "RENVOIE UNIQUEMENT LE TEXTE TRAITÉ SANS COMMENTAIRE.";

        Content content = new Content.Builder()
                .addImage(bitmap)
                .addText(prompt)
                .build();

        return model.generateContent(content);
    }

    /**
     * OCR using Gemini with a specific index (for retries with different keys)
     */
    public ListenableFuture<GenerateContentResponse> extractWithSpecificKey(Bitmap bitmap, int keyIndex) {
        if (geminiModels.isEmpty() || keyIndex >= geminiModels.size()) return null;
        GenerativeModelFutures model = geminiModels.get(keyIndex);

        Content content = new Content.Builder()
                .addImage(bitmap)
                .addText("Tu es un expert en OCR. Extrais le texte de cette image de manière structurée. Si illisible, renvoie [ERREUR: TEXTE ILLISIBLE].")
                .build();

        return model.generateContent(content);
    }

    public int getApiKeyCount() {
        return geminiModels.size();
    }

    /**
     * Synthesis using Groq for better performance (speed).
     */
    /**
     * Nettoyage rapide du texte OCR via Groq.
     */
    public ListenableFuture<String> cleanOcrWithGroq(String rawText) {
        SettableFuture<String> settableFuture = SettableFuture.create();
        JSONObject json = new JSONObject();
        try {
            json.put("model", GROQ_MODEL_TEXT);
            JSONArray messages = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "Tu es un expert en correction d'OCR. Ta mission est de corriger les fautes de frappe et les erreurs de lecture (lettres confondues, chiffres à la place de lettres) dans le texte fourni. Le texte est en FRANÇAIS. Ne change pas le sens. Si le texte est du charabia total, essaie quand même de deviner les mots français les plus proches. Renvoie UNIQUEMENT le texte corrigé.");

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", "Texte à corriger :\n" + rawText);

            messages.put(systemMessage);
            messages.put(userMessage);
            json.put("messages", messages);
        } catch (Exception e) {
            settableFuture.setException(e);
            return settableFuture;
        }
        sendGroqRequest(json, settableFuture);
        return settableFuture;
    }

    public ListenableFuture<String> synthesizeCourse(String fullText) {
        SettableFuture<String> settableFuture = SettableFuture.create();

        String prompt = "Tu es un assistant de mise en forme pédagogique. Ton rôle est de réorganiser et structurer des notes de cours existantes, PAS d'en inventer le contenu.\n\n" +
                "RÈGLES STRICTES :\n" +
                "- Utilise UNIQUEMENT les informations présentes dans les notes fournies\n" +
                "- N'ajoute AUCUNE information, explication ou exemple qui ne vient pas des notes\n" +
                "- Fusionne les informations redondantes.\n" +
                "- Reformule pour la clarté et la fluidité.\n\n" +
                "Voici les notes de cours à organiser :\n\n" +
                "--- DÉBUT DES NOTES ---\n" +
                fullText + "\n" +
                "--- FIN DES NOTES ---\n\n" +
                "Restructure ces notes selon ce format :\n" +
                "# Résumé global du cours\n" +
                "# Points importants\n" +
                "# Définitions\n" +
                "# Concepts clés à retenir\n";

        JSONObject json = new JSONObject();
        try {
            json.put("model", GROQ_MODEL_TEXT);
            JSONArray messages = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "Tu es un assistant pédagogique expert.");

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);

            messages.put(systemMessage);
            messages.put(userMessage);
            json.put("messages", messages);

        } catch (Exception e) {
            settableFuture.setException(e);
            return settableFuture;
        }

        sendGroqRequest(json, settableFuture);
        return settableFuture;
    }

    private void sendGroqRequest(JSONObject json, SettableFuture<String> settableFuture) {
        RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer " + groqApiKey)
                .post(body)
                .build();

        groqClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                settableFuture.setException(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response res = response) {
                    if (!res.isSuccessful()) {
                        String errorBody = res.body() != null ? res.body().string() : "Empty body";
                        settableFuture.setException(new IOException("Groq error: " + res.code() + " " + errorBody));
                        return;
                    }

                    String responseData = res.body().string();
                    JSONObject jsonResponse = new JSONObject(responseData);
                    String aiContent = jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    settableFuture.set(aiContent);
                } catch (Exception e) {
                    settableFuture.setException(e);
                }
            }
        });
    }
}
