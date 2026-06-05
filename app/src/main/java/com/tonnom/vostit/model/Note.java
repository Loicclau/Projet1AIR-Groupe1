package com.tonnom.vostit.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import java.util.List;

@Entity(tableName = "notes")
public class Note {

    @PrimaryKey(autoGenerate = true)
    private int id = 0;
    private String titre;
    private String contenu;
    private String date;
    private String imagePath; // Keep for local fallback/backwards compatibility
    private String subject;
    private String author; // Username of the creator
    private String cloudId; // Unique ID for Firebase sync
    private String remoteUrlsString; // URLs cloud séparées par des virgules

    @Ignore
    private List<String> remoteImageUrls;

    public Note() {
    }

    // Getters et Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getCloudId() { return cloudId; }
    public void setCloudId(String cloudId) { this.cloudId = cloudId; }

    public String getRemoteUrlsString() { return remoteUrlsString; }
    public void setRemoteUrlsString(String remoteUrlsString) { this.remoteUrlsString = remoteUrlsString; }

    public List<String> getRemoteImageUrls() {
        if (remoteImageUrls == null) {
            if (remoteUrlsString != null && !remoteUrlsString.isEmpty()) {
                remoteImageUrls = new java.util.ArrayList<>(java.util.Arrays.asList(remoteUrlsString.split(",")));
            } else {
                remoteImageUrls = new java.util.ArrayList<>();
            }
        }
        return remoteImageUrls;
    }
    public void setRemoteImageUrls(List<String> remoteImageUrls) {
        this.remoteImageUrls = remoteImageUrls;
        if (remoteImageUrls != null) {
            this.remoteUrlsString = android.text.TextUtils.join(",", remoteImageUrls);
        }
    }
}
