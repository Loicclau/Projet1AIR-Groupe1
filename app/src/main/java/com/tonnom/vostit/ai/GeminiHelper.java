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
    // For Gemini (OCR + Structuration)
    private final List<GenerativeModelFutures> geminiModels = new java.util.ArrayList<>();
    private int currentKeyIndex = 0;
    private static final String GEMINI_MODEL_NAME = "gemini-2.5-flash"; // Gemini 1.5 Flash for OCR

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
     * Gemini Prompt for OCR, Cleanup and Structuring
     */
    private String getOcrPrompt() {
        return "Tu es un expert en OCR (Reconnaissance Optique de Caractères) et en traitement de documents. " +
                "Ton objectif est d'extraire le texte de cette image (notes manuscrites ou imprimées), de le nettoyer et de le reformuler de manière structurée.\n\n" +
                "INSTRUCTIONS :\n" +
                "1. EXTRACTION : Détecte et lis tout le contenu textuel de l'image avec précision.\n" +
                "2. NETTOYAGE : Corrige les fautes d'orthographe, de grammaire et les erreurs de lecture (lettres confondues, symboles mal interprétés).\n" +
                "3. REFORMULATION : Reformule les bribes de phrases ou les notes fragmentées en contenu fluide et cohérent tout en restant 100% fidèle au sens original.\n" +
                "4. STRUCTURE : Organise le résultat avec du Markdown (Titres #, gras **, listes à puces -) pour une lecture claire.\n" +
                "5. VÉRIFICATION : Si l'image ne contient aucun texte ou est totalement illisible, renvoie exactement : \"[ERREUR: TEXTE ILLISIBLE]\".\n\n" +
                "RENVOIE UNIQUEMENT LE TEXTE TRAITÉ SANS AUCUN COMMENTAIRE PRÉLIMINAIRE OU FINAL.";
    }

    /**
     * OCR using Gemini with key rotation support
     */
    public ListenableFuture<GenerateContentResponse> extractAndCleanText(Bitmap bitmap) {
        GenerativeModelFutures model = getNextModel();
        if (model == null) return null;

        Content content = new Content.Builder()
                .addImage(bitmap)
                .addText(getOcrPrompt())
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
                .addText(getOcrPrompt())
                .build();

        return model.generateContent(content);
    }

    public int getApiKeyCount() {
        return geminiModels.size();
    }

    /**
     * Synthesis using Groq for better performance (speed).
     */
    public ListenableFuture<String> synthesizeCourse(String fullText) {
        String prompt = "Tu es un assistant de mise en forme pédagogique expert. Ton rôle est de réorganiser et synthétiser des notes de cours existantes pour en faire un résumé d'étude parfait.\n\n" +
                "RÈGLES STRICTES :\n" +
                "- Utilise UNIQUEMENT les informations présentes dans les notes fournies.\n" +
                "- N'ajoute AUCUNE connaissance extérieure.\n" +
                "- Fusionne les informations redondantes.\n" +
                "- Structure de manière logique et pédagogique.\n\n" +
                "Voici les notes de cours à transformer :\n\n" +
                "--- DÉBUT DES NOTES ---\n" +
                fullText + "\n" +
                "--- FIN DES NOTES ---\n\n" +
                "Produis une synthèse structurée en Markdown avec :\n" +
                "# Résumé global du cours\n" +
                "## Points importants (listes)\n" +
                "## Définitions clés\n" +
                "## Concepts à retenir\n";

        return generateWithGroq(prompt, "Tu es un assistant pédagogique expert qui crée des synthèses claires et structurées à partir de notes de cours.");
    }

    /**
     * Generic Groq request
     */
    public ListenableFuture<String> generateWithGroq(String prompt, String systemRole) {
        SettableFuture<String> settableFuture = SettableFuture.create();

        JSONObject json = new JSONObject();
        try {
            json.put("model", GROQ_MODEL_TEXT);
            JSONArray messages = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemRole);

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
