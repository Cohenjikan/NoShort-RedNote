import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    id("com.android.application")
}

// Release key: CI passes it through env (GitHub Secrets), locally it comes from keystore/release.properties.
// Same key every build, so each APK installs over the last one and keeps the WebView's cookies.
val keyProps = Properties().apply {
    val f = rootProject.file("keystore/release.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun key(name: String): String? = System.getenv("XHS_$name") ?: keyProps.getProperty(name)

android {
    namespace = "com.cohenjikan.xhslite"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cohenjikan.xhslite"
        minSdk = 29
        targetSdk = 36
        // every build is newer than the last (minutes since 2026-01-01 UTC), same scheme as Claude-Remote
        val built = Instant.now()
        versionCode = ((built.epochSecond - 1_767_225_600L) / 60).toInt()
        versionName = "0.1 · " + DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai")).format(built)
    }

    signingConfigs {
        create("release") {
            val store = key("KEYSTORE")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = key("KEYSTORE_PASSWORD")
                keyAlias = key("KEY_ALIAS")
                keyPassword = key("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // no key configured: fall back to the debug key so a local release build still works
            signingConfig = if (key("KEYSTORE") != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.12.4")
    // document-start scripts + client-hint UA metadata; plain WebView can't do either
    implementation("androidx.webkit:webkit:1.18.0")
}
