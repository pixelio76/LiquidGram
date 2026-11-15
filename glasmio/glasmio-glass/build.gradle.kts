plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android { namespace = "io.github.neoruaa.glasmio.glass" }
dependencies {
    api(platform("androidx.compose:compose-bom:2026.09.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation:1.12.1")
    api("androidx.compose.material3:material3:1.4.0")
    api(project(":glasmio-core"))
    api(project(":glasmio-icons"))
    api("io.github.kyant0:shapes:1.2.1")
}

android {
    // The native SDF field ships prebuilt (src/main/jniLibs, from the published 0.1.3 AAR): no NDK needed.
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
}

// Vendored Glasmio 0.1.3 (github.com/NEORUAA/Glasmio, Apache-2.0; see glasmio/LICENSE) with LiquidGram's glass tuning
// (GlasmioTuning). These settings came from Glasmio's root build.
android {
    compileSdk = 37
    defaultConfig { minSdk = 33 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true }
}
