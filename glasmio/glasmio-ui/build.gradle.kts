plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android { namespace = "io.github.neoruaa.glasmio.ui" }
dependencies {
    api(platform("androidx.compose:compose-bom:2026.09.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation:1.12.1")
    api("androidx.compose.material3:material3:1.4.0")
    api(project(":glasmio-glass"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
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
