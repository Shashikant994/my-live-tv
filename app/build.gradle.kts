import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

fun signingProp(envKey: String, propKey: String): String? {
    val envVal = System.getenv(envKey)
    if (!envVal.isNullOrBlank()) return envVal
    return keystoreProps.getProperty(propKey)?.takeIf { it.isNotBlank() }
}

android {
    namespace = "com.tvapp.livetv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tvapp.livetv"
        minSdk = 21
        targetSdk = 35
        versionCode = 5
        versionName = "2.1.0"

        buildConfigField("String", "GITHUB_OWNER", "\"Shashikant994\"")
        buildConfigField("String", "GITHUB_REPO", "\"my-live-tv\"")
    }

    signingConfigs {
        create("release") {
            val ksPath = signingProp("KEYSTORE_FILE", "storeFile")
            if (!ksPath.isNullOrBlank()) {
                val resolved = file(ksPath)
                if (resolved.exists()) {
                    storeFile = resolved
                    storePassword = signingProp("KEYSTORE_PASSWORD", "storePassword")
                    keyAlias = signingProp("KEY_ALIAS", "keyAlias")
                    keyPassword = signingProp("KEY_PASSWORD", "keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Jetpack Compose for TV
    implementation("androidx.tv:tv-foundation:1.0.0-alpha11")
    implementation("androidx.tv:tv-material:1.0.0")

    // AndroidX Media3 (ExoPlayer + HLS + UI)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Networking, Logos & QR Code Generation
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.zxing:core:3.5.3")
}
