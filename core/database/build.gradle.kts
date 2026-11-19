plugins {
    alias(libs.plugins.musicrecognizer.android.library)
    alias(libs.plugins.musicrecognizer.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.mrsep.musicrecognizer.core.database"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.audio)

    implementation(libs.kotlinx.coroutinesAndroid)
    implementation(libs.androidx.core)
    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.uuidCreator)
}