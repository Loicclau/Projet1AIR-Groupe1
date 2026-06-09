# VOST-IT 🎓

**VOST-IT** est une application Android conçue pour aider les étudiants de l'École nationale supérieure d'ingénieurs Sud-Alsace (ENSISA) à numériser, organiser et synthétiser leurs notes de cours manuscrites ou imprimées.

## 🚀 Fonctionnalités principales
- **OCR Intelligent** : Extraction de texte à partir de photos de notes.
- **Synthèse Automatique** : Génération de résumés structurés à partir de vos notes.
- **Gestion par Matières** : Organisation des documents.
- **Export PDF** : Exportez vos synthèses et notes au format PDF.

## 🛠️ Technologies utilisées
- **Langage** : Java (Android SDK)
- **Intelligence Artificielle** :
    - **Gemini 2.5 Flash** : Pour l'OCR et la lecture du manuscrit.
    - **Groq (Llama 3.3 70B)** : Pour la génération de synthèse.
- **Base de données** :
    - **Room (SQLite)** 
    - **Firebase Firestore** 
- **Stockage** : Firebase Storage pour les images.

## 📦 Installation
1. Clonez le projet.
2. Ajoutez vos clés API dans le fichier `local.properties` :
   ```properties
   GEMINI_API_KEY=votre_cle_gemini
   GROQ_API_KEY=votre_cle_groq
   ```
3. Compilez et lancez sur Android Studio.
4. **VOST-IT !** 

---
*Projet développé par des étudiant en 1A IR.*
