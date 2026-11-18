plugins {
    alias(libs.plugins.musicrecognizer.android.feature)
    alias(libs.plugins.musicrecognizer.android.library.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.mrsep.musicrecognizer.feature.recognition"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.audio)
    implementation(projects.core.metadata)

    implementation(libs.accompanist.permissions)
    implementation(libs.coil.compose)

    implementation(libs.kotlinx.coroutinesGuava)
    implementation(libs.androidx.workKtx)
    implementation(libs.hilt.ext.work)
    ksp(libs.hilt.ext.compiler)

    implementation(libs.androidx.glance)
    implementation(libs.androidx.glanceAppWidget)
    implementation(libs.androidx.glanceMaterial3)

    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.turbine)
}