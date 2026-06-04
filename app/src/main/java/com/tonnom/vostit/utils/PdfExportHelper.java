package com.tonnom.vostit.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class PdfExportHelper {

    private static final int PAGE_WIDTH = 595; // A4 width in points
    private static final int PAGE_HEIGHT = 842; // A4 height in points
    private static final int MARGIN = 40;

    public static void exportToPdf(Context context, String title, String content) {
        PdfDocument document = new PdfDocument();
        
        TextPaint textPaint = new TextPaint();
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(14);
        textPaint.setColor(Color.BLACK);

        Paint titlePaint = new Paint();
        titlePaint.setAntiAlias(true);
        titlePaint.setTextSize(20);
        titlePaint.setFakeBoldText(true);
        titlePaint.setColor(Color.BLACK);

        int contentWidth = PAGE_WIDTH - 2 * MARGIN;
        StaticLayout staticLayout = StaticLayout.Builder.obtain(content, 0, content.length(), textPaint, contentWidth)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, 1.2f)
                .setIncludePad(false)
                .build();

        int lineCount = staticLayout.getLineCount();
        int currentLine = 0;
        int pageNumber = 1;

        while (currentLine < lineCount) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int startY = MARGIN;
            if (pageNumber == 1) {
                canvas.drawText(title, MARGIN, startY + 20, titlePaint);
                startY += 60;
            }

            int startLine = currentLine;
            int pageBottomLimit = PAGE_HEIGHT - MARGIN;
            
            // On calcule combien de lignes rentrent dans cette page
            int topOfFirstLineOnPage = staticLayout.getLineTop(startLine);
            
            while (currentLine < lineCount) {
                int lineBottomRelativeToPageTop = staticLayout.getLineBottom(currentLine) - topOfFirstLineOnPage;
                if (startY + lineBottomRelativeToPageTop > pageBottomLimit) {
                    // Si même la première ligne de la page ne rentre pas (cas extrême), on la force quand même
                    if (currentLine == startLine) {
                        currentLine++;
                    }
                    break;
                }
                currentLine++;
            }

            canvas.save();
            canvas.translate(MARGIN, startY);
            
            int clipTop = staticLayout.getLineTop(startLine);
            int clipBottom = staticLayout.getLineBottom(currentLine - 1);
            
            canvas.clipRect(0, 0, contentWidth, clipBottom - clipTop);
            canvas.translate(0, -clipTop);
            staticLayout.draw(canvas);
            
            canvas.restore();
            document.finishPage(page);
            pageNumber++;
            
            // Protection contre boucle infinie (au cas où currentLine n'avancerait pas)
            if (currentLine == startLine) break;
        }

        String fileName = "Synthese_" + title.replaceAll("\\s+", "_") + "_" + System.currentTimeMillis() + ".pdf";

        try {
            Uri pdfUri = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                pdfUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (pdfUri != null) {
                    try (OutputStream os = context.getContentResolver().openOutputStream(pdfUri)) {
                        document.writeTo(os);
                    }
                }
            } else {
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    document.writeTo(fos);
                }
                pdfUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            }

            if (pdfUri != null) {
                Uri finalUri = pdfUri;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Toast.makeText(context, "PDF prêt dans Downloads", Toast.LENGTH_SHORT).show();
                    openPdf(context, finalUri);
                }, 500);
            }

        } catch (IOException e) {
            e.printStackTrace();
            new Handler(Looper.getMainLooper()).post(() -> 
                Toast.makeText(context, "Erreur lors de la création du PDF", Toast.LENGTH_SHORT).show()
            );
        } finally {
            document.close();
        }
    }

    private static void openPdf(Context context, Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Aucune application trouvée pour ouvrir le PDF", Toast.LENGTH_LONG).show();
        }
    }
}
