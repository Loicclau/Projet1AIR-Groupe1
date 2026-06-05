import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

android {
    namespace = "com.tonnom.vostit"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.tonnom.vostit"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Récupération de la clé depuis local.properties pour éviter de la commit
        val apiKey: String = localProperties.getProperty("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$apiKey\"")

        // Firebase Security
        val fbKey: String = localProperties.getProperty("FIREBASE_API_KEY") ?: "MISSING_KEY"
        val fbAppId: String = localProperties.getProperty("FIREBASE_APP_ID") ?: "MISSING_ID"
        val fbProjectId: String = localProperties.getProperty("FIREBASE_PROJECT_ID") ?: "vost-it"

        buildConfigField("String", "FIREBASE_API_KEY", "\"$fbKey\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$fbAppId\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$fbProjectId\"")
        
        // Injection dans les ressources pour écraser les valeurs du google-services.json
        resValue("string", "google_api_key", fbKey)
        resValue("string", "google_app_id", fbAppId)
        resValue("string", "project_id", fbProjectId)
    }

    buildFeatures {
        buildConfig = true
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

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-auth")

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