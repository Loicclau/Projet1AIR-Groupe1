import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    // alias(libs.plugins.google.services) // Désactivé pour éviter "Duplicate resources" avec les resValue ci-dessous
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

android {
    namespace = "com.tonnom.vostit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tonnom.vostit"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Récupération des clés depuis local.properties
        val keyList = mutableListOf<String>()
        for (i in 1..2) {
            val key = localProperties.getProperty("GEMINI_API_KEY$i")
            if (!key.isNullOrEmpty()) {
                keyList.add(key)
            }
        }
        val joinedKeys = keyList.joinToString(",")
        buildConfigField("String", "GEMINI_API_KEYS", "\"$joinedKeys\"")

        val groqKey: String = localProperties.getProperty("GROQ_API_KEY") ?: ""
        buildConfigField("String", "GROQ_API_KEY", "\"$groqKey\"")

        // Firebase Security
        val fbKey: String = localProperties.getProperty("FIREBASE_API_KEY") ?: "MISSING_KEY"
        val fbAppId: String = localProperties.getProperty("FIREBASE_APP_ID") ?: "MISSING_ID"
        val fbProjectId: String = localProperties.getProperty("FIREBASE_PROJECT_ID") ?: "vost-it"
        val fbStorage: String = localProperties.getProperty("FIREBASE_STORAGE_BUCKET") ?: "vost-it.appspot.com"
        val fbSenderId: String = localProperties.getProperty("FIREBASE_SENDER_ID") ?: "756511378354"

        buildConfigField("String", "FIREBASE_API_KEY", "\"$fbKey\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$fbAppId\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$fbProjectId\"")
        
        // Injection dans les ressources pour écraser les valeurs du google-services.json
        resValue("string", "google_api_key", fbKey)
        resValue("string", "google_app_id", fbAppId)
        resValue("string", "project_id", fbProjectId)
        resValue("string", "google_storage_bucket", fbStorage)
        resValue("string", "gcm_defaultSenderId", fbSenderId)
        resValue("string", "google_crash_reporting_api_key", fbKey)
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{INDEX.LIST,DEPENDENCIES,LICENSE,NOTICE,LICENSE.txt,NOTICE.txt,ASL2.0}"
        }
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    implementation(libs.okhttp)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-auth")

    // Play Services Base
    implementation("com.google.android.gms:play-services-base:18.5.0")

    // Room (SQLite)
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // Google AI SDK for Android (Optimized for Android)
    implementation(libs.generativeai)
    // Guava for ListenableFuture
    implementation("com.google.guava:guava:31.1-android")

    // Pour la gestion des bitmaps et entrées/sorties
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")

    // Glide pour l'affichage des images distantes
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Zoom sur les images
    implementation(libs.photoview)
}
