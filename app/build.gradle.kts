import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.liquidgram"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "org.liquidgram"
        minSdk = 33 // Glasmio needs Android 13 (RuntimeShader)
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        // Your own keys from my.telegram.org go into local.properties (never committed):
        //   telegram.apiId=12345
        //   telegram.apiHash=abcdef...
        // Without them the app runs on demo data.
        val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use(::load) }
        buildConfigField("int", "TG_API_ID", local.getProperty("telegram.apiId", "0"))
        buildConfigField("String", "TG_API_HASH", "\"" + local.getProperty("telegram.apiHash", "") + "\"")
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // shortcut: release is signed with the debug key so it installs; use your own keystore before publishing
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    // iOS Liquid Glass rendering and components (Apache-2.0, github.com/NEORUAA/Glasmio); replaces the vendored Kyant backdrop.
    implementation(project(":glasmio-ui")) // vendored, see glasmio/
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation("org.jetbrains.compose.animation:animation:1.12.0")
    implementation(libs.compose.ui)
    implementation(libs.compose.material.ripple)
    implementation(libs.kyant.shapes)
    implementation(libs.material.icons)
    // Animated stickers (.tgs = gzipped Lottie)
    implementation("com.airbnb.android:lottie-compose:6.6.2")
    // Round video messages: camera preview + recording
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-video:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
}
