package com.tonnom.vostit.ocr;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class OcrHelper {

    public interface OcrCallback {
        void onResult(String text);
    }

    public static void extractText(Bitmap bitmap, OcrCallback callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(result -> callback.onResult(result.getText()))
                .addOnFailureListener(e -> callback.onResult(""));
    }
}