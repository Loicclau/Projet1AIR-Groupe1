package com.tonnom.vostit.ocr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONException;
import org.json.JSONObject;

public class OcrHelper {

    private static final String TAG = "OcrHelper";

    public interface OcrCallback {
        void onResult(String jsonResponse);
    }

    public static void extractText(Bitmap bitmap, int rotationDegrees, OcrCallback callback) {
        if (bitmap == null || bitmap.isRecycled()) {
            callback.onResult(createJsonResponse("error", 0, 0, "OCR_ERROR: bitmap invalide."));
            return;
        }

        // --- ÉTAPE 1 : PRÉTRAITEMENT DE L'IMAGE ---
        
        // 2. REDIMENSIONNEMENT : Si largeur < 1080px, upscaler à 1080px.
        Bitmap processedBitmap = scaleBitmapIfNeeded(bitmap);

        // 3 & 4. NIVEAUX DE GRIS & CONTRASTE
        processedBitmap = applyGrayscaleAndContrast(processedBitmap, 1.5f);

        // --- ÉTAPE 2 : CONFIGURATION ML KIT ---
        // Note: La rotation (1) est gérée ici lors de la création de InputImage
        InputImage image = InputImage.fromBitmap(processedBitmap, rotationDegrees);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(result -> {
                    String rawText = result.getText();
                    int rawCharCount = rawText.length();

                    // --- ÉTAPE 3 : NETTOYAGE DU TEXTE ---
                    String cleanedText = cleanText(rawText);
                    int cleanedCharCount = cleanedText.length();

                    Log.d(TAG, "OCR Success. Raw chars: " + rawCharCount + ", Cleaned chars: " + cleanedCharCount);

                    if (cleanedText.isEmpty()) {
                        callback.onResult(createJsonResponse("empty", rawCharCount, 0, "OCR_EMPTY: aucun texte détecté après prétraitement."));
                    } else {
                        callback.onResult(createJsonResponse("success", rawCharCount, cleanedCharCount, cleanedText));
                    }
                    recognizer.close();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "OCR Failure", e);
                    callback.onResult(createJsonResponse("error", 0, 0, "OCR_FAILURE: " + e.getMessage()));
                    recognizer.close();
                });
    }

    private static Bitmap scaleBitmapIfNeeded(Bitmap bitmap) {
        if (bitmap.getWidth() < 1080) {
            float ratio = 1080f / bitmap.getWidth();
            int newHeight = (int) (bitmap.getHeight() * ratio);
            return Bitmap.createScaledBitmap(bitmap, 1080, newHeight, true);
        }
        return bitmap;
    }

    private static Bitmap applyGrayscaleAndContrast(Bitmap src, float contrast) {
        Bitmap bitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        // ColorMatrix pour niveaux de gris (saturation 0)
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);

        // ColorMatrix pour le contraste
        float scale = contrast;
        float translate = (-.5f * scale + .5f) * 255f;
        float[] array = new float[] {
                scale, 0, 0, 0, translate,
                0, scale, 0, 0, translate,
                0, 0, scale, 0, translate,
                0, 0, 0, 1, 0
        };
        ColorMatrix contrastMatrix = new ColorMatrix(array);
        
        // Combiner les matrices
        cm.postConcat(contrastMatrix);

        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return bitmap;
    }

    private static String cleanText(String text) {
        if (text == null) return "";

        // 1. Normaliser les fins de ligne
        String cleaned = text.replace("\r", "");

        // 3. Fusionner les mots coupés en fin de ligne (ex: "impor-\ntant" -> "important")
        cleaned = cleaned.replaceAll("(?m)(\\w+)-\\n(\\w+)", "$1$2");

        // 1. Supprimer les lignes vides multiples (garder max 1 saut de ligne)
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");

        // 2. Supprimer les caractères parasites isolés
        cleaned = cleaned.replaceAll("(?m)^[|—~]\\s*$", "");

        // 4. Normaliser les espaces multiples
        cleaned = cleaned.replaceAll("[ ]{2,}", " ");
        
        return cleaned.trim();
    }

    private static String createJsonResponse(String status, int rawCount, int cleanedCount, String text) {
        JSONObject json = new JSONObject();
        try {
            json.put("status", status);
            json.put("raw_char_count", rawCount);
            json.put("cleaned_char_count", cleanedCount);
            json.put("text", text);
        } catch (JSONException e) {
            Log.e(TAG, "JSON Creation error", e);
        }
        return json.toString();
    }
}
