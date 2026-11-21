import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseVersion = Properties().apply {
    rootProject.file("release.properties").inputStream().use { load(it) }
}
val releaseRepository = providers.environmentVariable("GITHUB_REPOSITORY")
    .orElse("yamato3010/Orbit").get()
require(releaseRepository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))

val releaseKeystore = providers.environmentVariable("ORBIT_KEYSTORE_PATH").orNull

android {
    namespace = "com.yamato3010.vrchattools"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.yamato3010.vrchattools"
        minSdk = 24
        targetSdk = 37
        versionCode = releaseVersion.getProperty("versionCode").toInt()
        versionName = releaseVersion.getProperty("versionName")
        buildConfigField("String", "RELEASE_REPOSITORY", "\"$releaseRepository\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("githubRelease") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("ORBIT_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ORBIT_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ORBIT_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("githubRelease")
            // Scoped R8 optimization broke Kotlin class access from OkHttp at startup.
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
