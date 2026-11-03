plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "dev.schattauer.edgechameleon.sample.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidSampleMinSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":edgechameleon"))
            implementation(libs.compose.foundation)
        }
    }
}
