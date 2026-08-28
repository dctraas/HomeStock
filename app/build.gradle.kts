plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.dtraas.homestock"
    // 35, not 34 — androidx.core:core-splashscreen 1.2.0 (see the splash-screen dependency
    // below) requires compiling against API 35+. This only changes what's visible at compile
    // time; targetSdk/minSdk below are untouched, so runtime behavior doesn't change.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dtraas.homestock"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    // Backs AppCompatDelegate.setApplicationLocales for in-app language switching
    // (with autoStoreLocales handling persistence, see AndroidManifest.xml).
    implementation(libs.androidx.appcompat)
    // Cold-start splash screen (see Theme.HomeStock.Starting + MainActivity.installSplashScreen)
    // — the AndroidX shim brings the Android 12+ SplashScreen API back to minSdk 26.
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    // Backs synced household-member profile photos (see HouseholdMembersRepository) —
    // profile photos were purely local before this; this is what lets a housemate's photo,
    // not just their name, show up on another device's members list.
    implementation(libs.firebase.storage.ktx)
    // Callable backend for premium AI-productherkenning (recognizeProduct) — see
    // AiRecognitionRepository and functions/src/index.ts. Keeps the Anthropic API key off
    // the device entirely.
    implementation(libs.firebase.functions.ktx)
    // Premium funnel instrumentation (AnalyticsRepository) — paywall views, plan selection,
    // purchase outcomes. No PII is logged, only event names/params (see AnalyticsRepository).
    implementation(libs.firebase.analytics.ktx)
    // Server-tunable monetization knobs without an app update (RemoteConfigRepository) — e.g.
    // the Premium household member cap. Same trust model as the rest of this app's household
    // logic: a soft business limit read client-side, not a hard security boundary.
    implementation(libs.firebase.config.ktx)
    // Real-time cross-device push (HomeStockMessagingService) — a huisgenoot's activity, and
    // household membership changes. See functions/src/index.ts's Firestore-triggered exports
    // for the server side that calls admin.messaging() against the tokens this registers.
    implementation(libs.firebase.messaging.ktx)
    // See HomeStockApplication.installAppCheck's doc — both providers are plain (not
    // debug-only) implementation deps so the same source compiles for every build type;
    // installAppCheck itself picks the right one at runtime via BuildConfig.DEBUG.
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.play.services)
    // Firestore pulls in gRPC, which drags in a plain (non-Android) Guava that can win
    // Gradle's version conflict resolution over CameraX's own Guava/ListenableFuture
    // dependency, leaving ListenableFuture unresolvable. Forcing the Android-flavored
    // artifact explicitly avoids that.
    implementation(libs.guava)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.glance.appwidget)

    implementation(libs.billing.ktx)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Local (JVM, no device/emulator) unit tests — see app/src/test. Kept deliberately plain:
    // no mocking framework yet, so tests only cover logic that's already free of Android
    // framework/Firebase/Play Billing dependencies (data class getters, pure business rules).
    // A mocking library (e.g. MockK) would be the natural next addition once repository logic
    // needs covering too.
    testImplementation(libs.junit)
}
