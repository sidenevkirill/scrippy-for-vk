plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    // УБЕРИТЕ эту строку - она дублируется
    // id("com.android.application")
}

android {
    namespace = "ru.lisdevs.messenger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reitesstudios.mooz"
        minSdk = 21
        targetSdk = 35
        versionCode = 54
        versionName = "0.5.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true

    }

    lint {
        disable.add("AppMetricaSdkVersion")
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // ВАЖНО: Включите shrinkResources, но с правильными правилами
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isZipAlignEnabled = true

            // УБЕРИТЕ эту строку - она создает проблему
            // matchingFallbacks = ['release']
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            versionNameSuffix = " Dev"
            // УБЕРИТЕ эту строку - неправильное название
            // ndk { debugSymbolLevel.set("SYMBOTable") }
        }
    }

    // ВАЖНО: ВЫБЕРИТЕ ОДИН ИЗ ВАРИАНТОВ:

    // ВАРИАНТ 1: Для Google Play App Bundle (РЕКОМЕНДУЕТСЯ)
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    // ВАРИАНТ 2: Для Multiple APK (если нужны отдельные APK)
    // splits {
    //     abi {
    //         enable = true
    //         reset()
    //         include "armeabi-v7a", "arm64-v8a", "x86", "x86_64"
    //         universalApk = true
    //     }
    // }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources {
            excludes.addAll(setOf(
                "META-INF/*.md",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module",
                "**.kotlin_builtins",
                "**.kotlin_metadata",
                // Добавьте для ExoPlayer
                "META-INF/com.android.tools/proguard/coroutines.pro"
            ))
        }
    }
}

dependencies {
    // Core Android Libraries
    implementation("androidx.multidex:multidex:2.0.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.browser:browser:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
    implementation("androidx.viewpager2:viewpager2:1.1.0-beta02")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Image loading - ВЫБЕРИТЕ ОДНУ
    implementation("com.github.bumptech.glide:glide:4.15.1")
    // kapt("com.github.bumptech.glide:compiler:4.15.1") // если используете kapt

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.android.volley:volley:1.2.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:adapter-rxjava2:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // QR Code (ВЫБЕРИТЕ ОДНУ)
    implementation("com.google.zxing:core:3.4.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    implementation("com.squareup.picasso:picasso:2.8")

    // Media & Video
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
    implementation("com.google.android.exoplayer:extension-okhttp:2.19.1")
    implementation("com.google.android.exoplayer:extension-mediasession:2.19.1")

    // Camera
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // Barcode
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Reactive
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.6.2")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.5")

    // Memory optimization
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("androidx.collection:collection-ktx:1.3.0")

    // HTML Parsing
    implementation("org.jsoup:jsoup:1.16.2")

    // Install Referrer
    implementation("com.android.installreferrer:installreferrer:2.2")

    // MP3 Metadata
    implementation("com.mpatric:mp3agic:0.9.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// private fun String?.set(string: String) {}