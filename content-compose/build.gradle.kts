plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())

    // Only the desktop (JVM) target for now; iOS/Android/JS are added here when needed.
    jvm("desktop")

    compilerOptions {
        optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "androidx.compose.foundation.layout.ExperimentalLayoutApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":content"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        getByName("desktopTest").languageSettings.optIn("androidx.compose.ui.test.ExperimentalTestApi")
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
        }
    }
}
