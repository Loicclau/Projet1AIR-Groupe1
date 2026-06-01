package com.tonnom.vostit.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class Note {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String titre;
    private String contenu;
    private String date;
    private String imagePath;

    public Note(String titre, String contenu, String date, String imagePath) {
        this.titre = titre;
        this.contenu = contenu;
        this.date = date;
        this.imagePath = imagePath;
    }

    // Getters
    public int getId() { return id; }
    public String getTitre() { return titre; }
    public String getContenu() { return contenu; }
    public String getDate() { return date; }
    public String getImagePath() { return imagePath; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setTitre(String titre) { this.titre = titre; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public void setDate(String date) { this.date = date; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
}