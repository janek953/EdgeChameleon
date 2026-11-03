plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.schattauer.edgechameleon.sample"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.schattauer.edgechameleon.sample"
        minSdk = libs.versions.androidSampleMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    // A throwaway key for this sample only, so that builds never depend on a key of the machine
    signingConfigs {
        create("sample") {
            storeFile = file("sample-debug.keystore")
            storePassword = "android"
            keyAlias = "sample"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("sample") }
        // Not debuggable, as debug builds of Compose are too slow to measure anything
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("sample")
            matchingFallbacks += "release"
        }
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":sample:shared"))
    implementation(libs.androidx.activity.compose)
}
