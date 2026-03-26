import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cognautic.app"
    compileSdk = 34

    // Reading from properties
    val localProperties = Properties()
    // Prefer repo root local.properties (Android Studio convention), not module-local.
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { stream ->
            localProperties.load(stream)
        }
    }

    // Optional env file (so you don't have to rely on Gradle daemon inheriting shell env vars)
    val envProperties = Properties()
    val envFile = rootProject.file(".env.release-signing")
    if (envFile.exists()) {
        envFile.forEachLine { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEachLine
            val idx = line.indexOf('=')
            if (idx <= 0) return@forEachLine
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            if (key.isNotBlank()) envProperties.setProperty(key, value)
        }
    }

    defaultConfig {
        applicationId = "com.cognautic.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL") ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties.getProperty("SUPABASE_ANON_KEY") ?: ""}\"")
    }

    // Release signing (configure via local.properties)
    // Required keys:
    // RELEASE_STORE_FILE=/absolute/path/to/keystore.jks
    // RELEASE_STORE_PASSWORD=...
    // RELEASE_KEY_ALIAS=...
    // RELEASE_KEY_PASSWORD=...
    // You can also set these as environment variables (e.g. source .env.release-signing).
    fun envOrProp(key: String): String {
        return (System.getenv(key)?.trim()).takeUnless { it.isNullOrBlank() }
            ?: (envProperties.getProperty(key)?.trim()).takeUnless { it.isNullOrBlank() }
            ?: (localProperties.getProperty(key)?.trim()).orEmpty()
    }

    val releaseStoreFile = envOrProp("RELEASE_STORE_FILE")
    val releaseStorePassword = envOrProp("RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = envOrProp("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = envOrProp("RELEASE_KEY_PASSWORD")

    signingConfigs {
        if (releaseStoreFile.isNotBlank() &&
            releaseStorePassword.isNotBlank() &&
            releaseKeyAlias.isNotBlank() &&
            releaseKeyPassword.isNotBlank()
        ) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
    
    // Allow reading standard input in AS slightly easier if needed, though mostly for unit tests.
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // ViewModels
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Icons
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    
    // SAF Support
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    // Web Scraping & Network
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AdMob
    implementation("com.google.android.gms:play-services-ads:23.0.0")
}
