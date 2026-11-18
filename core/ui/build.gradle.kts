plugins {
    alias(libs.plugins.musicrecognizer.android.library)
    alias(libs.plugins.musicrecognizer.android.library.compose)
}

android {
    namespace = "com.mrsep.musicrecognizer.core.ui"
}

dependencies {
    implementation(projects.core.strings)
    implementation(projects.core.domain)

    api(libs.materialKolor)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtimeKtx)
    implementation(libs.coil.compose)
}