package com.tonnom.vostit.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.tonnom.vostit.model.Note;
import com.tonnom.vostit.model.NoteImage;

import java.util.List;

@Dao
public interface NoteDao {

    @Insert
    long insert(Note note);

    @Update
    void update(Note note);

    @Delete
    void delete(Note note);

    @Query("SELECT * FROM notes ORDER BY id DESC")
    List<Note> getAllNotes();

    @Query("SELECT * FROM notes WHERE subject = :subject ORDER BY id DESC")
    List<Note> getNotesBySubject(String subject);

    @Query("SELECT * FROM notes WHERE id = :id")
    Note getNoteById(int id);

    @Query("SELECT * FROM notes WHERE cloudId = :cloudId")
    Note getNoteByCloudId(String cloudId);

    @Insert
    void insertImage(NoteImage image);

    @Delete
    void deleteImage(NoteImage image);

    @Query("DELETE FROM note_images WHERE noteId = :noteId")
    void deleteImagesForNote(int noteId);

    @Query("SELECT * FROM note_images WHERE noteId = :noteId")
    List<NoteImage> getImagesForNote(int noteId);

    @Query("DELETE FROM notes WHERE id = :noteId")
    void deleteNoteById(int noteId);

    @Query("DELETE FROM note_images WHERE imagePath = :path")
    void deleteImageByPath(String path);
}