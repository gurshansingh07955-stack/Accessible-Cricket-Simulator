plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cricketsim"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cricketsim"
        // 24 = Android 7.0 (Nougat). This already covers the requirement of
        // "Android 9 at minimum, 8 if possible" — 7.0 is lower than both, so
        // nothing needed to change here. There is no native (NDK/C++) code
        // anywhere in this app — no bundled .so libraries, nothing
        // architecture-specific — so a single APK built from this module
        // already runs unmodified on 32-bit devices (armeabi-v7a, x86)
        // exactly as it does on 64-bit ones. Nothing here needs an
        // `ndk { abiFilters }` or a `splits { abi }` block, because there is
        // nothing to filter or split.
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-logic-port"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The key/IV that decrypt the bundled audio pack (see
        // audio/AudioPack.kt and tools/fetch_audio.sh) — read into
        // BuildConfig so a plain build works with zero setup, using the
        // same constant checked into fetch_audio.sh. Passing
        // -PaudioPackKeyHex=... -PaudioPackIvHex=... (from a CI secret
        // that is NEVER committed) overrides it. See AudioPack.kt's doc
        // comment for why that is the meaningfully more secure option.
        buildConfigField(
            "String", "AUDIO_PACK_KEY_HEX",
            "\"${project.findProperty("audioPackKeyHex") ?: "df49894bbeb5bc80ee17563f080390e2eae82814a6d58bcf8315519abc24ec66"}\""
        )
        buildConfigField(
            "String", "AUDIO_PACK_IV_HEX",
            "\"${project.findProperty("audioPackIvHex") ?: "48b415240d5285efe644906d4b4a3b2a"}\""
        )
    }

    signingConfigs {
        // Overrides AGP's implicit debug signingConfig, which otherwise
        // auto-generates a NEW keystore per machine (e.g. every fresh CI
        // runner) — meaning every build previously had a DIFFERENT
        // signing key, so installing a new build over an old one failed
        // with "App not installed" (a signature mismatch) unless the old
        // one was uninstalled first. This debug keystore is checked in
        // as base64 (tools/debug-keystore.b64, decoded by the CI
        // workflow before the build) precisely so every build — local or
        // CI — shares the same key and can update in place. It carries
        // none of the risk a real signing key would: it is a debug-only
        // key with the standard well-known Android debug credentials,
        // never used for a release build.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            // Obfuscates and shrinks the compiled code, and removes unused
            // resources. Two things that would otherwise break silently:
            // - Gson reads the persistence data classes by field NAME to
            //   save/load a match; proguard-rules.pro keeps those classes
            //   exactly as compiled, or every saved match would fail to
            //   read back after this is turned on.
            // - R.raw.audio_pack is only ever looked up dynamically
            //   (context.resources.getIdentifier("audio_pack", "raw", ...)),
            //   which the resource shrinker cannot see as a "use"; without
            //   res/raw/keep.xml explicitly protecting it, shrinkResources
            //   would delete the entire bundled audio pack from the APK.
            isMinifyEnabled = true
            isShrinkResources = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    // Stated explicitly (it already arrives transitively) because the audio
    // engine relies on Dispatchers.Main and coroutine timers.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Saving and resuming a match: reflection-based JSON over the existing
    // data classes (see persistence/MatchSaveStore.kt). Kept safe under
    // minification by proguard-rules.pro's keep rules.
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
