import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.likkho.diary"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.likkho.diary"
        minSdk = 24
        targetSdk = 34
        versionCode = 23
        versionName = "2.1.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        aaptOptions {
            ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"
        }
    }

    buildFeatures {
        compose = true
    }

    val isReleaseTaskRequested = gradle.startParameter.taskNames.any {
        it.contains("Release", ignoreCase = true)
    }

    signingConfigs {
        create("release") {
            val keystoreBase64 = System.getenv("KEYSTORE_BASE64")
            if (isReleaseTaskRequested && keystoreBase64.isNullOrBlank()) {
                throw GradleException("FATAL: KEYSTORE_BASE64 secret is missing. Release build cannot proceed without release keystore.")
            }
            if (!keystoreBase64.isNullOrBlank()) {
                val keystoreFile = file("release-keystore.jks")
                keystoreFile.parentFile?.mkdirs()
                val decodedBytes = Base64.getMimeDecoder().decode(keystoreBase64.trim())
                keystoreFile.writeBytes(decodedBytes)
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: if (isReleaseTaskRequested) throw GradleException("FATAL: KEYSTORE_PASSWORD secret is missing.") else ""
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: if (isReleaseTaskRequested) throw GradleException("FATAL: KEY_ALIAS secret is missing.") else ""
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: if (isReleaseTaskRequested) throw GradleException("FATAL: KEY_PASSWORD secret is missing.") else ""
            }
        }
    }

    buildTypes {
        getByName("release") {
            val releaseSigning = signingConfigs.getByName("release")
            if (isReleaseTaskRequested && (releaseSigning.storeFile == null || !releaseSigning.storeFile!!.exists())) {
                throw GradleException("FATAL: Release keystore file does not exist. Debug fallback is disabled.")
            }
            signingConfig = releaseSigning
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

repositories {
    flatDir {
        dirs("../capacitor-cordova-android-plugins/src/main/libs", "libs")
    }
}

dependencies {
    implementation(fileTree(mapOf("include" to listOf("*.jar"), "dir" to "libs")))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Jetpack Compose & Native AndroidX Stack (Option 1 Hybrid Native Migration)
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Native Security, Media, WebView AssetBridge & Storage
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(project(":capacitor-android"))
    implementation(project(":capacitor-cordova-android-plugins"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

apply(from = "capacitor.build.gradle")
