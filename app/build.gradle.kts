@file:Suppress(names = ["UnstableApiUsage", "SpellCheckingInspection"])

import java.util.Properties

plugins {
    alias(libs.plugins.musicrecognizer.android.application)
    alias(libs.plugins.musicrecognizer.android.application.compose)
    alias(libs.plugins.musicrecognizer.hilt)
    alias(libs.plugins.aboutLibraries)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { stream ->
        localProperties.load(stream)
    }
}

android {
    namespace = "com.mrsep.musicrecognizer"
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "com.mrsep.musicrecognizer"
        versionCode = 50
        versionName = "1.19.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val devOptionsEnabled = localProperties.getProperty("dev.options", "false")
        buildConfigField("boolean", "DEV_OPTIONS", devOptionsEnabled)
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Audile[Debug]")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        buildConfig = true
        resValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            resources.excludes += "DebugProbesKt.bin"
        }
    }
    androidResources {
        generateLocaleConfig = true
        // Excluded unfinished translations: "az", "br", "ca", "el", "fa", "gu", "ml", "ro", "sq", "vi"
        localeFilters += listOf("en", "ar", "cs", "de", "es", "et", "fr", "gl", "hu", "in", "it", "iw", "ja", "nl", "pl", "pt", "pt-rBR", "ru", "sk", "sv", "tr", "uk", "zh-rCN")
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// ./gradlew app:exportLibraryDefinitions
aboutLibraries {
    license.additionalLicenses = setOf("GPL-3.0-or-later")
    collect.gitHubApiToken = localProperties.getProperty("github.token")
    export.apply {
        prettyPrint = true
        excludeFields = setOf("funding", "scm")
        outputPath = layout.projectDirectory.file("src/main/res/raw/aboutlibraries.json")
    }
}

hilt {
    enableAggregatingTask = true
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.recognition)
    implementation(projects.core.metadata)
    implementation(projects.core.audio)
    implementation(projects.core.ui)
    implementation(projects.core.strings)
    implementation(projects.core.common)
    implementation(projects.feature.library)
    implementation(projects.feature.track)
    implementation(projects.feature.recognition)
    implementation(projects.feature.preferences)
    implementation(projects.feature.backup)
    implementation(projects.feature.developerMode)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtimeKtx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.windowManager)
    implementation(libs.androidx.compose.material3.windowSizeClass)

    implementation(libs.kotlinx.coroutinesAndroid)
    implementation(libs.kotlinx.collectionImmutable)

    implementation(libs.coil.compose)

    implementation(libs.androidx.workKtx)
    implementation(libs.hilt.ext.work)
    ksp(libs.hilt.ext.compiler)

    implementation(libs.acra.core)
    implementation(libs.acra.mail)
    implementation(libs.acra.dialog)
    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
