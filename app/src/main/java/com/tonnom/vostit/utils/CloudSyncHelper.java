package com.tonnom.vostit.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.tonnom.vostit.SessionManager;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Note;
import com.tonnom.vostit.model.Synthesis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CloudSyncHelper {

    private static final String TAG = "CloudSyncHelper";
    private final FirebaseFirestore db;
    private final SessionManager sessionManager;
    private final Context context;
    private ListenerRegistration registration;

    public interface SyncCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public CloudSyncHelper(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.sessionManager = new SessionManager(context);
        
        Log.d(TAG, "CloudSyncHelper initialisé.");
        ensureFirebaseAuth();
    }

    private void ensureFirebaseAuth() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.d(TAG, "Aucun utilisateur Firebase. Tentative de connexion anonyme...");
            FirebaseAuth.getInstance().signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        if (authResult.getUser() != null) {
                            Log.d(TAG, "Connexion anonyme Firebase réussie : " + authResult.getUser().getUid());
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Échec de la connexion anonyme Firebase", e));
        } else {
            Log.d(TAG, "Utilisateur Firebase déjà connecté : " + FirebaseAuth.getInstance().getCurrentUser().getUid());
        }
    }

    /**
     * Synchronise une note avec Firestore. Les images restent locales.
     */
    public void uploadNote(Note note, SyncCallback callback) {
        if (note == null) return;
        
        String username = sessionManager.getUsername();
        if (username == null) {
            Log.e(TAG, "Erreur: Aucun utilisateur connecté.");
            if (callback != null) callback.onFailure(new Exception("User not logged in"));
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("titre", note.getTitre());
        data.put("contenu", note.getContenu());
        data.put("date", note.getDate());
        data.put("subject", note.getSubject());
        data.put("author", username);
        
        long ts = note.getTimestamp() > 0 ? note.getTimestamp() : System.currentTimeMillis();
        data.put("timestamp", ts);
        note.setTimestamp(ts);

        String docId = note.getCloudId();
        
        if (docId == null || docId.isEmpty()) {
            db.collection("notes")
                    .add(data)
                    .addOnSuccessListener(docRef -> {
                        String newId = docRef.getId();
                        Log.d(TAG, "Note créée dans Firestore avec succès ID : " + newId);
                        updateLocalNoteAfterSync(note.getId(), newId);
                        if (callback != null) callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Erreur lors de la création Firestore (ADD)", e);
                        if (callback != null) callback.onFailure(e);
                    });
        } else {
            db.collection("notes").document(docId)
                    .set(data)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Note mise à jour dans Firestore avec succès : " + docId);
                        updateLocalNoteAfterSync(note.getId(), docId);
                        if (callback != null) callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Erreur lors de la mise à jour Firestore (SET)", e);
                        if (callback != null) callback.onFailure(e);
                    });
        }
    }

    private void updateLocalNoteAfterSync(int localId, String cloudId) {
        new Thread(() -> {
            Note localNote = NoteDatabase.getInstance(context).noteDao().getNoteById(localId);
            if (localNote != null) {
                localNote.setCloudId(cloudId);
                NoteDatabase.getInstance(context).noteDao().update(localNote);
                Log.d(TAG, "Note locale mise à jour avec l'ID Cloud.");
            }
        }).start();
    }

    public void fetchCloudNotes(String subject, OnCloudFetchListener listener) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            FirebaseAuth.getInstance().signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    startListening(subject, listener);
                }
            });
        } else {
            startListening(subject, listener);
        }
    }

    private void startListening(String subject, OnCloudFetchListener listener) {
        stopListening();

        Log.d(TAG, "Démarrage de l'écoute Cloud pour le sujet : " + subject);
        Query query = db.collection("notes");
        if (subject != null) {
            query = query.whereEqualTo("subject", subject);
        }

        registration = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Erreur d'écoute Cloud (SnapshotListener failed)", e);
                return;
            }

            if (snapshots != null) {
                Log.d(TAG, "Données Cloud reçues : " + snapshots.size() + " documents.");
                List<Note> cloudNotes = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snapshots) {
                    try {
                        Note note = new Note();
                        note.setCloudId(doc.getId());
                        note.setTitre(doc.getString("titre"));
                        note.setContenu(doc.getString("contenu"));
                        note.setDate(doc.getString("date"));
                        note.setSubject(doc.getString("subject"));
                        note.setAuthor(doc.getString("author"));
                        Long ts = doc.getLong("timestamp");
                        if (ts != null) note.setTimestamp(ts);
                        cloudNotes.add(note);
                    } catch (Exception docEx) {
                        Log.e(TAG, "Erreur lecture document : " + doc.getId(), docEx);
                    }
                }
                listener.onFetch(cloudNotes);
            } else {
                Log.w(TAG, "Snapshots nuls reçus du Cloud");
            }
        });
    }

    public void stopListening() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    public interface OnCloudFetchListener {
        void onFetch(List<Note> notes);
    }

    public void uploadSynthesis(com.tonnom.vostit.model.Synthesis synthesis) {
        if (synthesis == null) return;
        
        Map<String, Object> data = new HashMap<>();
        data.put("subject", synthesis.getSubject());
        data.put("content", synthesis.getContent());
        data.put("timestamp", synthesis.getTimestamp());
        data.put("specialty", synthesis.getSpecialty());
        data.put("year", synthesis.getYear());

        // On utilise le sujet comme ID de document pour qu'il n'y en ait qu'un seul par cours
        db.collection("syntheses").document(synthesis.getSubject())
                .set(data)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Synthèse partagée sur le Cloud pour " + synthesis.getSubject()))
                .addOnFailureListener(e -> Log.e(TAG, "Erreur partage synthèse", e));
    }

    public interface OnSynthesisFetchListener {
        void onFetch(com.tonnom.vostit.model.Synthesis synthesis);
    }

    public interface OnAllSynthesesFetchListener {
        void onFetch(List<com.tonnom.vostit.model.Synthesis> syntheses);
    }

    public void fetchAllSyntheses(OnAllSynthesesFetchListener listener) {
        db.collection("syntheses")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<com.tonnom.vostit.model.Synthesis> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        try {
                            Synthesis s = new Synthesis(
                                    doc.getString("subject"),
                                    doc.getString("content"),
                                    doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0
                            );
                            s.setSpecialty(doc.getString("specialty"));
                            s.setYear(doc.getString("year"));
                            list.add(s);
                        } catch (Exception e) {
                            Log.e(TAG, "Erreur lecture doc synthèse", e);
                        }
                    }
                    listener.onFetch(list);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Erreur fetch all syntheses", e);
                    listener.onFetch(new ArrayList<>());
                });
    }

    public void fetchLatestSynthesis(String subject, OnSynthesisFetchListener listener) {
        if (subject == null) return;
        
        db.collection("syntheses").document(subject)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Synthesis s = new Synthesis(
                                doc.getString("subject"),
                                doc.getString("content"),
                                doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0
                        );
                        s.setSpecialty(doc.getString("specialty"));
                        s.setYear(doc.getString("year"));
                        listener.onFetch(s);
                    } else {
                        listener.onFetch(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Erreur fetch synthèse", e);
                    listener.onFetch(null);
                });
    }
}
