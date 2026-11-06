plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.mazidulhaque.kokhon.core.ocr"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:parser"))
    // The bundled model works offline and without Google Play services.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.core.ktx)
}
