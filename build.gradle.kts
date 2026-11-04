plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.klibs.io.notifier) apply false
    alias(libs.plugins.ktfmt)
}

allprojects {
    pluginManager.apply("com.ncorti.ktfmt.gradle")
    extensions.configure<com.ncorti.ktfmt.gradle.KtfmtExtension> { kotlinLangStyle() }
}
