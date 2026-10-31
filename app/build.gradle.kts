plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())

    jvm("desktop")

    compilerOptions {
        optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":client"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigation.compose)
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
        }
        getByName("desktopTest").languageSettings.optIn("androidx.compose.ui.test.ExperimentalTestApi")
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
        }
    }
}

// UI tests save screenshots of key screens here for manual (or Claude's) inspection.
tasks.withType<Test>().configureEach {
    val screenshots = layout.buildDirectory.dir("screenshots")
    systemProperty("polylingy.screenshots", screenshots.get().asFile.absolutePath)
    outputs.dir(screenshots)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

compose.desktop {
    application {
        mainClass = "com.polylingy.app.MainKt"
        // Run on the toolchain JDK the code is compiled for, not on the JDK running Gradle.
        javaHome = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(libs.versions.jdk.get().toInt())
        }.get().metadata.installationPath.asFile.absolutePath
        // sqlite-jdbc loads a native library; JDK 24+ warns about that unless it's allowed explicitly.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
    }
}
