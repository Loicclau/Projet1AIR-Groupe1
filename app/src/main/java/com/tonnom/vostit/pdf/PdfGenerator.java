package com.tonnom.vostit.pdf;

import android.content.Context;
import android.os.Environment;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PdfGenerator {

    public interface PdfCallback {
        void onSuccess(String filePath);
        void onError(String error);
    }

    public static void genererPdf(Context context, String contenu, PdfCallback callback) {
        try {
            String date = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "VOST-IT_" + date + ".pdf";

            File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir != null && !dir.exists()) dir.mkdirs();

            String filePath = dir.getAbsolutePath() + "/" + fileName;

            PdfWriter writer = new PdfWriter(filePath);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Titre
            document.add(new Paragraph("VOST-IT — Notes organisées")
                    .setBold()
                    .setFontSize(20));

            document.add(new Paragraph("Généré le : " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()))
                    .setFontSize(10));

            document.add(new LineSeparator(new SolidLine()));
            document.add(new Paragraph("\n"));

            // Contenu
            document.add(new Paragraph(contenu).setFontSize(12));

            document.close();
            callback.onSuccess(filePath);

        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }
}