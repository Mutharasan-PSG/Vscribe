plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id ("kotlin-kapt")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.vs"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.vs"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    // Correct placement for viewBinding
    buildFeatures {
        viewBinding = true
    }

}

dependencies {
  /* implementation ("com.squareup.okhttp3:okhttp:4.11.0")
    implementation ("com.spotify.android:auth:1.2.3")
    implementation ("com.spotify.android:spotify-auth:1.2.5")
    implementation ("com.spotify.android:spotify-player:1.2.3") */

    implementation ("org.tensorflow:tensorflow-lite:2.12.0")  // Check for the latest version
    implementation ("org.tensorflow:tensorflow-lite-support:0.4.0") // Optional, for additional support functions

    
    implementation ("com.google.firebase:firebase-firestore:25.1.0")
    implementation ("com.google.firebase:firebase-firestore-ktx")


    implementation ("io.coil-kt:coil:2.1.0")
    implementation ("io.coil-kt:coil-gif:2.1.0")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // RecyclerView
//    implementation ("androidx.recyclerview:recyclerview:1.3.2")

    implementation ("androidx.room:room-ktx:2.6.1")
    // Room Database
    implementation ("androidx.room:room-runtime:2.6.1")
    implementation(libs.firebase.storage.ktx)
    implementation(libs.annotations)
    kapt ("androidx.room:room-compiler:2.6.1")

    implementation ("net.objecthunter:exp4j:0.4.8")
    // Apache POI for DOCX
    implementation ("org.apache.poi:poi:5.2.3")
    implementation ("org.apache.poi:poi-ooxml:5.2.3")

    // iText for PDF
    implementation ("com.itextpdf:itext7-core:7.2.6")
    implementation ("androidx.fragment:fragment-ktx:1.8.1")

    implementation ("com.github.bumptech.glide:glide:4.15.1")
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.recyclerview)
    implementation(libs.common)
    kapt ("com.github.bumptech.glide:compiler:4.15.1")
    implementation ("com.squareup.picasso:picasso:2.71828")

    implementation ("com.google.code.gson:gson:2.10.1")

    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.firebase:firebase-database:21.0.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
