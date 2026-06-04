package com.tonnom.vostit.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.tonnom.vostit.model.Note;
import com.tonnom.vostit.model.NoteImage;
import com.tonnom.vostit.model.Synthesis;
import com.tonnom.vostit.model.User;

@Database(entities = {Note.class, NoteImage.class, User.class, Synthesis.class}, version = 5)
public abstract class NoteDatabase extends RoomDatabase {

    private static NoteDatabase instance;

    public abstract NoteDao noteDao();
    public abstract UserDao userDao();
    public abstract SynthesisDao synthesisDao();

    public static synchronized NoteDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    NoteDatabase.class,
                    "note_database"
            ).fallbackToDestructiveMigration().build();
        }
        return instance;
    }
}
