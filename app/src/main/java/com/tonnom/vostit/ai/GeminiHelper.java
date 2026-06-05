package com.tonnom.vostit.ai;

import android.graphics.Bitmap;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.ListenableFuture;

public class GeminiHelper {
    private final GenerativeModelFutures model;
    private static final String MODEL_NAME = "gemini-2.5-flash";

    public GeminiHelper(String apiKey) {
        // Utilisation du SDK Android officiel pour éviter les conflits Apache HttpClient
        GenerativeModel gm = new GenerativeModel(MODEL_NAME, apiKey);
        this.model = GenerativeModelFutures.from(gm);
    }

    public ListenableFuture<GenerateContentResponse> extractTextFromImage(Bitmap bitmap) {
        String prompt = "Tu es un expert en OCR (Reconnaissance Optique de Caractères) spécialisé dans les notes de cours manuscrites et les documents imprimés.\n" +
                "\n" +
                "TACHE :\n" +
                "1. Extrais TOUT le texte lisible de cette image.\n" +
                "2. Nettoie le texte : corrige les fautes de frappe ou d'orthographe évidentes dues à une mauvaise lecture.\n" +
                "3. Reformule légèrement pour que les phrases soient grammaticalement correctes si le texte original est décousu, tout en restant STRICTEMENT fidèle au contenu.\n" +
                "4. Structure le texte proprement (titres, listes à puces si approprié).\n" +
                "5. Si le texte semble incohérent ou totalement illisible, réponds : \"[ERREUR: TEXTE ILLISIBLE]\".\n" +
                "\n" +
                "RETOURNE UNIQUEMENT LE TEXTE TRAITÉ.";

        Content content = new Content.Builder()
                .addImage(bitmap)
                .addText(prompt)
                .build();
        
        return model.generateContent(content);
    }

    public ListenableFuture<GenerateContentResponse> synthesizeCourse(String fullText) {
        String prompt = "Tu es un assistant de mise en forme pédagogique. Ton rôle est de réorganiser et structurer des notes de cours existantes, PAS d'en inventer le contenu.\n\n" +
                "RÈGLES STRICTES :\n" +
                "- Utilise UNIQUEMENT les informations présentes dans les notes fournies\n" +
                "- N'ajoute AUCUNE information, explication ou exemple qui ne vient pas des notes\n" +
                "- Si une même information apparaît dans plusieurs notes, fusionne-la en une seule entrée sans la modifier\n" +
                "- Reformule légèrement pour la lisibilité, mais reste fidèle au sens original\n" +
                "- Si une section ne peut pas être remplie avec le contenu des notes, indique : \"(non mentionné dans les notes)\"\n\n" +
                "Voici les notes de cours à organiser :\n\n" +
                "--- DÉBUT DES NOTES ---\n" +
                fullText + "\n" +
                "--- FIN DES NOTES ---\n\n" +
                "Restructure ces notes selon ce format :\n\n" +
                "# Résumé global du cours\n" +
                "(Synthèse rédigée uniquement à partir des notes, sans ajout)\n\n" +
                "# Points importants\n" +
                "(Liste des points clés présents dans les notes, dédoublonnés et ordonnés logiquement)\n\n" +
                "# Définitions\n" +
                "(Uniquement les définitions explicitement présentes dans les notes, format : **terme** : définition)\n\n" +
                "# Concepts clés à retenir\n" +
                "(Uniquement les concepts mentionnés dans les notes, regroupés et ordonnés)\n";

        Content content = new Content.Builder()
                .addText(prompt)
                .build();

        return model.generateContent(content);
    }
}
