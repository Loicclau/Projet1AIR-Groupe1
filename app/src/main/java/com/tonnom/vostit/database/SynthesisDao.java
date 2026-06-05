package com.tonnom.vostit.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.tonnom.vostit.model.Synthesis;

import java.util.List;

@Dao
public interface SynthesisDao {
    @Insert
    long insert(Synthesis synthesis);

    @Query("SELECT * FROM syntheses WHERE subject = :subject ORDER BY timestamp DESC LIMIT 1")
    Synthesis getLatestForSubject(String subject);

    @Query("SELECT * FROM syntheses WHERE subject = :subject ORDER BY timestamp DESC")
    List<Synthesis> getAllForSubject(String subject);

    @Query("SELECT * FROM syntheses ORDER BY timestamp DESC")
    List<Synthesis> getAllSyntheses();

    @Query("SELECT * FROM syntheses WHERE id = :id")
    Synthesis getById(int id);

    @Query("DELETE FROM syntheses WHERE id = :id")
    void deleteById(int id);
}
