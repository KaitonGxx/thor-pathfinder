import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Release signing. The key and its password never live in the repository:
 * they come from keystore.properties in the project root (gitignored), or from
 * environment variables on a build server. Every release must use the same
 * key, or Android refuses to install it over the previous one.
 */
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun signingValue(property: String, environment: String): String? =
    signing.getProperty(property) ?: System.getenv(environment)

android {
    namespace = "com.thorpathfinder.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thorpathfinder.app"
        // The AYN Thor ships Android 13.
        minSdk = 33
        targetSdk = 35
        versionCode = 12
        versionName = "0.6.0"
    }

    signingConfigs {
        create("release") {
            signingValue("storeFile", "PATHFINDER_KEYSTORE")?.let { storeFile = file(it) }
            storePassword = signingValue("storePassword", "PATHFINDER_KEYSTORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "PATHFINDER_KEY_ALIAS")
            keyPassword = signingValue("keyPassword", "PATHFINDER_KEY_PASSWORD")
        }
    }

    // Without the key (anyone but the maintainer), builds fall back to the
    // local debug key: they install fine but can't update a released copy.
    val releaseKey = signingConfigs.getByName("release").takeIf { it.storeFile?.isFile == true }
    if (releaseKey == null) {
        logger.warn("Thor Pathfinder: no release key (keystore.properties); signing with the debug key.")
    }

    buildTypes {
        debug {
            // Same key for debug builds, so either kind installs over the other.
            releaseKey?.let { signingConfig = it }
        }
        release {
            signingConfig = releaseKey ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Shizuku: shell-level (UID 2000) commands without root
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
    // Android's own org.json is only a stub in JVM unit tests
    testImplementation("org.json:json:20240303")
}
