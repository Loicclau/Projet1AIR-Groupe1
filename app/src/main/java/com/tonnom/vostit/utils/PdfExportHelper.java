package com.tonnom.vostit.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.tonnom.vostit.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PdfExportHelper {

    private static final int PAGE_WIDTH = 595; // A4 width in points
    private static final int PAGE_HEIGHT = 842; // A4 height in points
    private static final int MARGIN = 50;
    
    // Couleurs modernes basées sur l'app
    private static final int COLOR_PRIMARY = Color.parseColor("#4F46E5");
    private static final int COLOR_SECONDARY = Color.parseColor("#10B981");
    private static final int COLOR_TEXT = Color.parseColor("#1E293B");
    private static final int COLOR_TEXT_LIGHT = Color.parseColor("#64748B");
    private static final int COLOR_DIVIDER = Color.parseColor("#E2E8F0");
    private static final int COLOR_CARD_BG = Color.parseColor("#F8FAFC");

    public static void exportToPdf(Context context, String subject, String content) {
        PdfDocument document = new PdfDocument();
        
        // Configuration des styles de texte
        TextPaint normalPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        normalPaint.setTextSize(11);
        normalPaint.setColor(COLOR_TEXT);
        normalPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        TextPaint boldPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        boldPaint.setTextSize(11);
        boldPaint.setColor(COLOR_TEXT);
        boldPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextSize(24);
        titlePaint.setColor(COLOR_PRIMARY);
        titlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        TextPaint subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subtitlePaint.setTextSize(14);
        subtitlePaint.setColor(COLOR_SECONDARY);
        subtitlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        TextPaint footerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setTextSize(9);
        footerPaint.setColor(COLOR_TEXT_LIGHT);

        int contentWidth = PAGE_WIDTH - 2 * MARGIN;
        
        // Analyse du contenu pour pagination
        String[] lines = content.split("\n");
        int yPosition = MARGIN;
        int pageNumber = 1;
        
        PdfDocument.Page page = startNewPage(document, pageNumber);
        Canvas canvas = page.getCanvas();
        
        // En-tête de la première page
        drawHeader(canvas, subject);
        yPosition += 80;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                yPosition += 10;
                continue;
            }

            TextPaint currentPaint = normalPaint;
            int xOffset = MARGIN;
            boolean isTitle = false;
            boolean isSubtitle = false;

            if (trimmedLine.startsWith("# ")) {
                currentPaint = titlePaint;
                trimmedLine = trimmedLine.substring(2);
                yPosition += 15;
                isTitle = true;
            } else if (trimmedLine.startsWith("## ")) {
                currentPaint = subtitlePaint;
                trimmedLine = trimmedLine.substring(3);
                yPosition += 10;
                isSubtitle = true;
            } else if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ")) {
                trimmedLine = "• " + trimmedLine.substring(2);
                xOffset += 15;
            }

            // Gestion de l'inline bold (**text**)
            // Pour le PDF simple on va dessiner ligne par ligne, si c'est du riche on utilise StaticLayout
            StaticLayout layout = createLayout(trimmedLine, currentPaint, contentWidth - (xOffset - MARGIN));
            
            // Vérifier si on doit changer de page
            if (yPosition + layout.getHeight() > PAGE_HEIGHT - MARGIN - 30) {
                document.finishPage(page);
                pageNumber++;
                page = startNewPage(document, pageNumber);
                canvas = page.getCanvas();
                yPosition = MARGIN + 20;
            }

            // Dessiner un fond pour les titres de section (facultatif mais élégant)
            if (isSubtitle) {
                Paint bgPaint = new Paint();
                bgPaint.setColor(COLOR_CARD_BG);
                canvas.drawRoundRect(new RectF(MARGIN - 5, yPosition - 5, PAGE_WIDTH - MARGIN + 5, yPosition + layout.getHeight() + 5), 8, 8, bgPaint);
            }

            canvas.save();
            canvas.translate(xOffset, yPosition);
            layout.draw(canvas);
            canvas.restore();
            
            yPosition += layout.getHeight() + (isTitle ? 15 : 5);
        }

        document.finishPage(page);

        // Finalisation et sauvegarde
        String fileName = "Synthese_" + subject.replaceAll("\\s+", "_") + "_" + System.currentTimeMillis() + ".pdf";
        saveAndOpenPdf(context, document, fileName);
    }

    private static PdfDocument.Page startNewPage(PdfDocument document, int pageNumber) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        
        Canvas canvas = page.getCanvas();
        // Pied de page
        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setTextSize(9);
        footerPaint.setColor(COLOR_TEXT_LIGHT);
        String footerText = "VOST-IT - Document généré par IA - Page " + pageNumber;
        canvas.drawText(footerText, MARGIN, PAGE_HEIGHT - 25, footerPaint);
        
        Paint linePaint = new Paint();
        linePaint.setColor(COLOR_DIVIDER);
        linePaint.setStrokeWidth(1);
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 35, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 35, linePaint);
        
        return page;
    }

    private static void drawHeader(Canvas canvas, String subject) {
        Paint primaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        primaryPaint.setColor(COLOR_PRIMARY);
        primaryPaint.setTextSize(12);
        primaryPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        
        canvas.drawText("VOST-IT", MARGIN, MARGIN + 10, primaryPaint);
        
        Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        datePaint.setColor(COLOR_TEXT_LIGHT);
        datePaint.setTextSize(9);
        String date = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(new Date());
        canvas.drawText(date, PAGE_WIDTH - MARGIN - datePaint.measureText(date), MARGIN + 10, datePaint);
        
        Paint linePaint = new Paint();
        linePaint.setColor(COLOR_PRIMARY);
        linePaint.setStrokeWidth(2);
        canvas.drawLine(MARGIN, MARGIN + 25, PAGE_WIDTH - MARGIN, MARGIN + 25, linePaint);
        
        Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(COLOR_TEXT);
        subPaint.setTextSize(18);
        subPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        canvas.drawText("Synthèse de cours : " + subject, MARGIN, MARGIN + 55, subPaint);
    }

    private static StaticLayout createLayout(String text, TextPaint paint, int width) {
        // Simple version: no complex inline bold for now to avoid rendering issues in PDF
        // but we ensure the text wraps correctly.
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, 1.4f)
                .setIncludePad(false)
                .build();
    }

    private static void saveAndOpenPdf(Context context, PdfDocument document, String fileName) {
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
                    Toast.makeText(context, "PDF exporté avec succès", Toast.LENGTH_SHORT).show();
                    openPdf(context, finalUri);
                }, 500);
            }

        } catch (IOException e) {
            e.printStackTrace();
            new Handler(Looper.getMainLooper()).post(() -> 
                Toast.makeText(context, "Erreur lors de l'exportation", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Besoin d'un lecteur PDF", Toast.LENGTH_LONG).show();
        }
    }
}
