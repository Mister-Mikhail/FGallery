import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.mistermikhail.fgallery"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mistermikhail.fgallery"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 29
        targetSdk = 35
        versionCode = 28
        versionName = "0.4.0"
        externalNativeBuild { cmake { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") } }
    }

    // Use the public development key explicitly; Gradle's default user directory
    // differs between environments and may silently create another debug key.
    val testKeyBytes = Base64.getMimeDecoder().decode(
        rootProject.file(".github/debug-signing/debug.keystore.b64").readText()
    )
    val testKey = layout.buildDirectory.file("signing/fgallery-debug.keystore").get().asFile
    if (!testKey.exists() || !testKey.readBytes().contentEquals(testKeyBytes)) {
        testKey.parentFile.mkdirs()
        testKey.writeBytes(testKeyBytes)
    }
    signingConfigs.getByName("debug") {
        storeFile = testKey
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
    buildTypes.getByName("debug") {
        signingConfig = signingConfigs.getByName("debug")
    }

    buildTypes.getByName("release") {
        // Test-only signing key keeps Android update compatibility.
        signingConfig = signingConfigs.getByName("debug")
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    buildTypes.create("minifiedTest") {
        initWith(buildTypes.getByName("debug"))
        signingConfig = signingConfigs.getByName("debug")
        isDebuggable = true
        isMinifyEnabled = true
        isShrinkResources = true
        matchingFallbacks += listOf("release", "debug")
        // AndroidJUnitRunner and its Kotlin-based TestStorage run in the host
        // process; keep their Kotlin runtime calls in the minified test APK.
        proguardFiles("minified-test-proguard.pro")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    // Share debug-only test fixtures with minified instrumentation, but do NOT
    // package them in the distributed release APK.
    sourceSets.getByName("minifiedTest") {
        java.srcDir("src/debug/java")
        manifest.srcFile("src/debug/AndroidManifest.xml")
    }
    testBuildType = "minifiedTest"

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
    }
    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    baselineProfile(project(":baselineprofile"))
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui:1.9.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.3")
    implementation("androidx.compose.foundation:foundation:1.9.3")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")

    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-gif:3.1.0")
    implementation("io.coil-kt.coil3:coil-video:3.1.0")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("io.legere:pdfiumandroid:2.0.0")
    implementation("com.github.dburckh:AndroidLibRaw:2.0.7")
    implementation("com.github.beyka:Android-TiffBitmapFactory:0.9.9.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.3")
    add("minifiedTestImplementation", "androidx.compose.ui:ui-test-manifest:1.9.3")
    implementation("me.saket.telephoto:zoomable-image-coil3:0.15.1")
    implementation("com.vanniktech:android-image-cropper:4.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.media3:media3-exoplayer:1.9.3")
    implementation("androidx.media3:media3-ui:1.9.3")
    implementation("androidx.media3:media3-ui-compose:1.9.3")
    implementation("androidx.media3:media3-transformer:1.9.3")
    implementation("androidx.media3:media3-effect:1.9.3")

    debugImplementation("androidx.compose.ui:ui-tooling:1.9.3")
}

baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
}

ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.absolutePath)
}
