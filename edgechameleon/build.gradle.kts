import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.klibs.io.notifier)
}

group = "dev.schattauer.edgechameleon"

// The publish workflow passes the release version with -PreleaseVersion
version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")

kotlin {
    explicitApi()

    // The public API is published, so a change of it has to show up in the review
    @OptIn(ExperimentalAbiValidation::class) abiValidation {}

    android {
        namespace = "dev.schattauer.edgechameleon"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        withHostTest { isReturnDefaultValues = true }
        withDeviceTestBuilder { sourceSetTreeName = "test" }
            .configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    jvm()
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonAndroid") {
                withApple()
                withJvm()
                withJs()
                withWasmJs()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies { implementation(libs.androidx.core) }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.junit)
            implementation(libs.compose.foundation)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    // Local builds have no key, and can still use publishToMavenLocal
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(group.toString(), "edgechameleon", version.toString())

    pom {
        name.set("EdgeChameleon")
        description.set(
            "Picks light or dark system bar icons from the content behind the bars, for Compose Multiplatform."
        )
        inceptionYear.set("2026")
        url.set("https://github.com/rschattauer/EdgeChameleon")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("rschattauer")
                name.set("Richard Schattauer")
                url.set("https://github.com/rschattauer")
            }
        }
        scm {
            url.set("https://github.com/rschattauer/EdgeChameleon")
            connection.set("scm:git:https://github.com/rschattauer/EdgeChameleon.git")
            developerConnection.set("scm:git:https://github.com/rschattauer/EdgeChameleon.git")
        }
    }
}

// The ktfmt plugin does not see the source sets of the Android multiplatform library plugin
val androidSources = fileTree("src") { include("android*/**/*.kt") }
val ktfmtCheckAndroid =
    tasks.register<KtfmtCheckTask>("ktfmtCheckAndroid") { source(androidSources) }
val ktfmtFormatAndroid =
    tasks.register<KtfmtFormatTask>("ktfmtFormatAndroid") { source(androidSources) }

tasks.named("ktfmtCheck") { dependsOn(ktfmtCheckAndroid) }

tasks.named("ktfmtFormat") { dependsOn(ktfmtFormatAndroid) }
