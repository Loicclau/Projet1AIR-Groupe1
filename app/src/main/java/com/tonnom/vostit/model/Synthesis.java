package com.tonnom.vostit.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "syntheses")
public class Synthesis {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String subject;
    private String content;
    private long timestamp;
    private String specialty;
    private String year;

    public Synthesis(String subject, String content, long timestamp) {
        this.subject = subject;
        this.content = content;
        this.timestamp = timestamp;
    }

    @Ignore
    public Synthesis(String subject, String content, long timestamp, String specialty, String year) {
        this.subject = subject;
        this.content = content;
        this.timestamp = timestamp;
        this.specialty = specialty;
        this.year = year;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
}
