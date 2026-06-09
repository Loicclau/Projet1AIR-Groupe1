package com.tonnom.vostit.ai;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

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

/**
 * Hybrid Helper: Uses Gemini for OCR (Image) and Groq for Synthesis (Text).
 */
public class GeminiHelper {
    // For Gemini (OCR)
    private final GenerativeModelFutures geminiModel;
    private static final String GEMINI_MODEL_NAME = "gemini-1.5-flash";

    // For Groq (Synthesis)
    private final String groqApiKey;
    private final OkHttpClient groqClient;
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL_TEXT = "llama-3.3-70b-versatile";

    public GeminiHelper(String geminiApiKey, String groqApiKey) {
        // Initialize Gemini
        GenerativeModel gm = new GenerativeModel(GEMINI_MODEL_NAME, geminiApiKey);
        this.geminiModel = GenerativeModelFutures.from(gm);

        // Initialize Groq
        this.groqApiKey = groqApiKey;
        this.groqClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public ListenableFuture<String> synthesizeCourse(String fullText) {
        SettableFuture<String> settableFuture = SettableFuture.create();
        
        JSONObject json = new JSONObject();
        try {
            json.put("model", GROQ_MODEL_TEXT);
            JSONArray messages = new JSONArray();
            
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "Tu es un assistant pédagogique expert. Résume les notes fournies de manière structurée avec Titres (#), Points importants (-) et Définitions.");
            
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", "Voici les notes à synthétiser :\n\n" + fullText);
            
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
