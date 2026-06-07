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
        GenerativeModel gm = new GenerativeModel(MODEL_NAME, apiKey);
        this.model = GenerativeModelFutures.from(gm);
    }

    public ListenableFuture<GenerateContentResponse> extractAndCleanText(Bitmap bitmap) {
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

    public ListenableFuture<GenerateContentResponse> synthesizeCourse(String fullText) {
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

        Content content = new Content.Builder()
                .addText(prompt)
                .build();

        return model.generateContent(content);
    }
}
