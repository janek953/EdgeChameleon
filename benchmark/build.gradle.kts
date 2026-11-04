plugins { alias(libs.plugins.android.test) }

android {
    namespace = "dev.schattauer.edgechameleon.benchmark"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidSampleMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Measures the sample app in its benchmark build type, which is not debuggable
    targetProjectPath = ":sample:androidApp"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents { beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" } }
