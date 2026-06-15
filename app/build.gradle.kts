plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 37                          // ← ИСПРАВЛЕНО: было compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
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



    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    // Единый BOM из version catalog (2026.04.01) — совместим с Kotlin 2.3.21
    // ← ИСПРАВЛЕНО: был жёстко прописан compose-bom:2024.02.00, несовместимый с Kotlin 2.x
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    // УДАЛЕНО: implementation(libs.androidx.compose.foundation)  — явная версия 1.11.2 конфликтовала с BOM
    // УДАЛЕНО: implementation(libs.androidx.ui)                  — явная версия 1.11.1 конфликтовала с BOM
    // УДАЛЕНО: implementation(libs.billing)                      — ЭТО com.google.androidbrowserhelper:billing,
    //                                                              а НЕ Google Play Billing!

    // Compose UI — версии управляются BOM 2026.04.01, без явных версий
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")     // ← версию берёт из BOM
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.1")

    // AndroidX core
    implementation("androidx.core:core-ktx:1.19.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Network
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")

    // Google Play Billing 9.0.0 — ЕДИНСТВЕННАЯ библиотека биллинга, без libs.billing
    implementation("com.android.billingclient:billing:9.0.0")
    implementation("com.android.billingclient:billing-ktx:9.0.0")

    // Google Pay API (Wallet)
    implementation("com.google.android.gms:play-services-wallet:20.0.0")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.google.accompanist:accompanist-pager:0.36.0")
    implementation("com.google.accompanist:accompanist-pager-indicators:0.36.0")
}