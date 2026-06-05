package com.tonnom.vostit.utils;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SynthesisFormatter {

    public static SpannableStringBuilder format(String content, int primaryColor, int secondaryColor) {
        if (content == null) return new SpannableStringBuilder("");

        SpannableStringBuilder builder = new SpannableStringBuilder();
        String[] lines = content.split("\n");

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("# ")) {
                // Grand Titre
                int start = builder.length();
                builder.append(trimmedLine.substring(2)).append("\n\n");
                builder.setSpan(new RelativeSizeSpan(1.5f), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(primaryColor), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (trimmedLine.startsWith("## ")) {
                // Sous-titre
                int start = builder.length();
                builder.append(trimmedLine.substring(3)).append("\n\n");
                builder.setSpan(new RelativeSizeSpan(1.2f), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(secondaryColor), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ")) {
                // Point de liste
                int start = builder.length();
                String bulletText = trimmedLine.substring(2);
                builder.append(formatInlineStyles(bulletText)).append("\n");
                builder.setSpan(new BulletSpan(24), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new LeadingMarginSpan.Standard(16), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (trimmedLine.isEmpty()) {
                builder.append("\n");
            } else {
                // Texte normal
                builder.append(formatInlineStyles(trimmedLine)).append("\n");
            }
        }

        return builder;
    }

    private static SpannableStringBuilder formatInlineStyles(String text) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(text);

        // Gras: **texte**
        Pattern boldPattern = Pattern.compile("\\*\\*(.*?)\\*\\*");
        Matcher boldMatcher = boldPattern.matcher(ssb);
        while (boldMatcher.find()) {
            int start = boldMatcher.start();
            int end = boldMatcher.end();
            String content = boldMatcher.group(1);
            ssb.replace(start, end, content);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), start, start + content.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            boldMatcher = boldPattern.matcher(ssb);
        }

        return ssb;
    }
}
