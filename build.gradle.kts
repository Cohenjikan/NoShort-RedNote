plugins {
    // same toolchain as Claude-Remote: AGP 9 compiles Kotlin itself, no kotlin-android plugin
    id("com.android.application") version "9.4.1" apply false
}
