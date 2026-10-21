import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from environment variables (CI) or keystore.properties (local).
// Without either, local release builds fall back to the debug key so they still install;
// on CI a missing key fails the build instead of publishing a debug-signed APK.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)

val releaseStorePath = signingValue("CRAFTY_KEYSTORE_PATH", "storeFile")
val onCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
// Set by the workflow for pull requests only, which build but never publish.
val allowDebugSigning = System.getenv("CRAFTY_ALLOW_DEBUG_SIGNING") == "true"

android {
    namespace = "com.vknxworks.craftyctrl"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vknxworks.craftyctrl"
        minSdk = 33
        targetSdk = 35
        versionCode = 39
        versionName = "2.5.4"
        // 64-bit ARM phones only (arm64-v8a), like every Ctrl app.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        create("release") {
            releaseStorePath?.let { path ->
                storeFile = file(path)
                storePassword = signingValue("CRAFTY_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("CRAFTY_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("CRAFTY_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (releaseStorePath != null) {
                signingConfigs.getByName("release")
            } else if (onCi && !allowDebugSigning) {
                throw GradleException(
                    "Release signing key is missing on CI (CRAFTY_KEYSTORE_PATH). " +
                        "Set the KEYSTORE_BASE64, KEYSTORE_PASSWORD and KEY_PASSWORD secrets.",
                )
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Backdrop blur behind the floating tab bar, as in the other Ctrl apps.
    implementation("dev.chrisbanes.haze:haze:1.6.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
