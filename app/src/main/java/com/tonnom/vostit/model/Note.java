package com.tonnom.vostit.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class Note {

    @PrimaryKey(autoGenerate = true)
    private int id = 0;
    private String titre;
    private String contenu;
    private String date;
    private String imagePath;
    private String subject;
    private String author; // Username of the creator

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
}
