import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.likkho.nativeapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.likkho.nativeapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Jetpack Compose BOM & Core
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Room Database + Coroutines KTX
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Security & Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Hardware Biometrics
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Coil for Compose (Fast Image Rendering & Caching)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Android Core KTX & DocumentFile for SAF
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Debugging & Testing
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
