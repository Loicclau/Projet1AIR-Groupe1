package com.tonnom.vostit.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.tonnom.vostit.SessionManager;
import com.tonnom.vostit.database.NoteDatabase;
import com.tonnom.vostit.model.Note;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class CloudSyncHelper {

    private static final String TAG = "CloudSyncHelper";
    private final FirebaseFirestore db;
    private final FirebaseStorage storage;
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
        
        // Initialisation explicite pour plus de stabilité
        String bucket = "";
        try {
            int resId = context.getResources().getIdentifier("google_storage_bucket", "string", context.getPackageName());
            if (resId != 0) {
                bucket = context.getString(resId);
            }
        } catch (Exception ignored) {}

        if (bucket.isEmpty()) {
            this.storage = FirebaseStorage.getInstance();
        } else {
            if (!bucket.startsWith("gs://")) {
                bucket = "gs://" + bucket;
            }
            this.storage = FirebaseStorage.getInstance(bucket);
        }
        
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
     * Synchronise une note avec Firestore. Télécharge les images sur Firebase Storage.
     */
    public void uploadNote(Note note, List<String> localPaths, SyncCallback callback) {
        if (note == null) return;
        
        String username = sessionManager.getUsername();
        if (username == null) {
            Log.e(TAG, "Erreur: Aucun utilisateur connecté.");
            if (callback != null) callback.onFailure(new Exception("User not logged in"));
            return;
        }

        // On s'assure d'être connecté avant d'uploader
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.w(TAG, "Auth non prêt. Tentative de connexion avant upload...");
            FirebaseAuth.getInstance().signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    startUploads(note, localPaths, callback);
                } else {
                    Log.e(TAG, "Impossible de se connecter à Firebase pour l'upload");
                    if (callback != null) callback.onFailure(new Exception("Firebase Auth failed"));
                }
            });
        } else {
            startUploads(note, localPaths, callback);
        }
    }

    private void startUploads(Note note, List<String> localPaths, SyncCallback callback) {
        List<String> newRemoteUrls = java.util.Collections.synchronizedList(new ArrayList<>());
        
        if (localPaths == null || localPaths.isEmpty()) {
            Log.d(TAG, "Pas d'images à uploader, sauvegarde directe.");
            saveNoteToFirestore(note, new ArrayList<>(), callback);
            return;
        }

        AtomicInteger uploadCount = new AtomicInteger(0);
        int totalImages = localPaths.size();
        Log.d(TAG, "Début upload de " + totalImages + " images.");

        for (String path : localPaths) {
            if (path.startsWith("http")) {
                Log.d(TAG, "Image déjà sur le cloud : " + path);
                newRemoteUrls.add(path);
                if (uploadCount.incrementAndGet() == totalImages) {
                    saveNoteToFirestore(note, newRemoteUrls, callback);
                }
                continue;
            }

            uploadImage(path, url -> {
                if (url != null) {
                    Log.d(TAG, "Upload réussi, URL reçue : " + url);
                    newRemoteUrls.add(url);
                } else {
                    Log.e(TAG, "Échec de l'upload pour le chemin : " + path + ". L'URL retournée est nulle.");
                }

                if (uploadCount.incrementAndGet() == totalImages) {
                    Log.d(TAG, "Tous les uploads terminés. Total collecté : " + newRemoteUrls.size());
                    saveNoteToFirestore(note, newRemoteUrls, callback);
                }
            }, callback);
        }
    }

    private void uploadImage(String localPath, OnUploadCompleteListener listener, SyncCallback mainCallback) {
        File file = new File(localPath);
        if (!file.exists()) {
            Log.e(TAG, "Fichier local introuvable : " + localPath);
            listener.onComplete(null);
            return;
        }

        if (file.length() == 0) {
            Log.e(TAG, "Le fichier est vide : " + localPath);
            listener.onComplete(null);
            return;
        }

        String username = sessionManager.getUsername();
        if (username == null || username.isEmpty()) {
            username = "anonymous";
        }
        // Nettoyage du nom d'utilisateur pour éviter les caractères problématiques dans l'URL
        String safeUsername = username.replaceAll("[^a-zA-Z0-9]", "_");

        String fileName = "notes/" + safeUsername + "/" + UUID.randomUUID().toString() + ".jpg";
        StorageReference ref = storage.getReference().child(fileName);
        
        Log.d(TAG, "Tentative d'upload vers Storage. Bucket: " + ref.getBucket() + " | Path: " + fileName + " | Taille: " + file.length() + " octets");
        
        // Utilisation de putFile avec des métadonnées explicites
        com.google.firebase.storage.StorageMetadata metadata = new com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build();

        ref.putFile(Uri.fromFile(file), metadata)
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Upload Storage réussi, récupération de l'URL...");
                    ref.getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();
                        Log.d(TAG, "URL de téléchargement obtenue : " + downloadUrl);
                        listener.onComplete(downloadUrl);
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Erreur lors de la récupération de l'URL pour " + fileName, e);
                        if (mainCallback != null) mainCallback.onFailure(e);
                        listener.onComplete(null);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Échec de l'upload Storage pour " + fileName + ". Erreur: " + e.getMessage(), e);
                    if (mainCallback != null) mainCallback.onFailure(e);
                    listener.onComplete(null);
                });
    }

    private void saveNoteToFirestore(Note note, List<String> newRemoteUrls, SyncCallback callback) {
        String username = sessionManager.getUsername();
        
        Log.d(TAG, "Préparation des données Firestore. Nouvelles URLs : " + newRemoteUrls.size());

        // Fusion des anciennes URLs distantes avec les nouvelles en évitant les doublons
        List<String> allImageUrls = new ArrayList<>();
        if (note.getRemoteImageUrls() != null) {
            allImageUrls.addAll(note.getRemoteImageUrls());
        }
        for (String url : newRemoteUrls) {
            if (url != null && !allImageUrls.contains(url)) {
                allImageUrls.add(url);
            }
        }

        Log.d(TAG, "Nombre total d'URLs à enregistrer : " + allImageUrls.size());

        Map<String, Object> data = new HashMap<>();
        data.put("titre", note.getTitre());
        data.put("contenu", note.getContenu());
        data.put("date", note.getDate());
        data.put("subject", note.getSubject());
        data.put("author", username);
        data.put("imageUrls", allImageUrls);
        data.put("timestamp", System.currentTimeMillis());

        String docId = note.getCloudId();
        
        if (docId == null || docId.isEmpty()) {
            db.collection("notes")
                    .add(data)
                    .addOnSuccessListener(docRef -> {
                        String newId = docRef.getId();
                        Log.d(TAG, "Note créée dans Firestore avec succès ID : " + newId);
                        updateLocalNoteAfterSync(note.getId(), newId, allImageUrls);
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
                        updateLocalNoteAfterSync(note.getId(), docId, allImageUrls);
                        if (callback != null) callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Erreur lors de la mise à jour Firestore (SET)", e);
                        if (callback != null) callback.onFailure(e);
                    });
        }
    }

    private void updateLocalNoteAfterSync(int localId, String cloudId, List<String> remoteUrls) {
        new Thread(() -> {
            Note localNote = NoteDatabase.getInstance(context).noteDao().getNoteById(localId);
            if (localNote != null) {
                localNote.setCloudId(cloudId);
                localNote.setRemoteImageUrls(remoteUrls);
                NoteDatabase.getInstance(context).noteDao().update(localNote);
                Log.d(TAG, "Note locale mise à jour avec les infos Cloud.");
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

        Query query = db.collection("notes");
        if (subject != null) {
            query = query.whereEqualTo("subject", subject);
        }

        registration = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Listen failed", e);
                return;
            }

            if (snapshots != null) {
                List<Note> cloudNotes = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snapshots) {
                    Note note = new Note();
                    note.setCloudId(doc.getId());
                    note.setTitre(doc.getString("titre"));
                    note.setContenu(doc.getString("contenu"));
                    note.setDate(doc.getString("date"));
                    note.setSubject(doc.getString("subject"));
                    note.setAuthor(doc.getString("author"));
                    
                    Object urlsObj = doc.get("imageUrls");
                    if (urlsObj instanceof List) {
                        List<?> rawList = (List<?>) urlsObj;
                        List<String> urls = new ArrayList<>();
                        for (Object o : rawList) {
                            if (o instanceof String) urls.add((String) o);
                        }
                        note.setRemoteImageUrls(urls);
                    } else {
                        note.setRemoteImageUrls(new ArrayList<>());
                    }
                    
                    cloudNotes.add(note);
                }
                listener.onFetch(cloudNotes);
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

    public interface OnUploadCompleteListener {
        void onComplete(String url);
    }
}
