package com.tonnom.vostit.ai;

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

public class ClaudeHelper {

    private static final String API_KEY = "VOTRE_CLE_API_ICI";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final OkHttpClient client = new OkHttpClient();

    public interface ClaudeCallback {
        void onResult(String result);
        void onError(String error);
    }

    public static void organiserNotes(String notes, ClaudeCallback callback) {
        try {
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", "Voici des notes prises par un étudiant. Organise-les de façon claire et structurée, avec des titres et sections bien ordonnés :\n\n" + notes);

            JSONArray messages = new JSONArray();
            messages.put(message);

            JSONObject body = new JSONObject();
            body.put("model", "claude-sonnet-4-20250514");
            body.put("max_tokens", 2048);
            body.put("messages", messages);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                    .addHeader("x-api-key", API_KEY)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        String result = json.getJSONArray("content")
                                .getJSONObject(0)
                                .getString("text");
                        callback.onResult(result);
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }
}