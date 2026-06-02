package com.tonnom.vostit.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "note_images",
        foreignKeys = @ForeignKey(entity = Note.class,
                parentColumns = "id",
                childColumns = "noteId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("noteId")})
public class NoteImage {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private int noteId;
    private String imagePath;

    public NoteImage(int noteId, String imagePath) {
        this.noteId = noteId;
        this.imagePath = imagePath;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getNoteId() { return noteId; }
    public void setNoteId(int noteId) { this.noteId = noteId; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
}
