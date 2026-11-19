import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
            implementation(project(":content-compose"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigation.compose)
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.logback.classic)
            implementation(libs.jlayer)
        }
        getByName("desktopTest").languageSettings.optIn("androidx.compose.ui.test.ExperimentalTestApi")
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
        }
    }
}

// APP_VERSION constant generated from `version` in gradle.properties, so the UI shows the built version.
val generateAppVersion = tasks.register("generateAppVersion") {
    val appVersion = project.version.toString()
    val stage = (project.findProperty("releaseStage") as String?).orEmpty().trim()
    inputs.property("stage", stage)
    val outputDir = layout.buildDirectory.dir("generated/appVersion")
    inputs.property("version", appVersion)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("com/polylingy/app/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.polylingy.app
            |
            |/** Generated from `version` in gradle.properties. */
            |const val APP_VERSION = "$appVersion"
            |
            |/** Generated from `releaseStage` in gradle.properties: empty for a final release, else e.g. "alpha". */
            |const val APP_STAGE = "$stage"
            |
            |/** What the UI shows: the version and, when there is one, the stage. */
            |const val APP_VERSION_LABEL = "$appVersion${if (stage.isEmpty()) "" else " $stage"}"
            |""".trimMargin(),
        )
    }
}
kotlin.sourceSets.commonMain { kotlin.srcDir(generateAppVersion) }

// UI tests save screenshots of key screens here for manual (or Claude's) inspection.
tasks.withType<Test>().configureEach {
    val screenshots = layout.buildDirectory.dir("screenshots")
    systemProperty("polylingy.screenshots", screenshots.get().asFile.absolutePath)
    systemProperty("polylingy.logs", layout.buildDirectory.dir("test-logs").get().asFile.absolutePath)
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

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Polylingy"
            packageVersion = project.version.toString()
            description = "Spaced-repetition language learning"
            vendor = "Polylingy"
            // From ./gradlew :app:suggestRuntimeModules; java.desktop is always included.
            modules("java.instrument", "java.net.http", "java.sql", "jdk.unsupported", "java.naming")

            windows {
                iconFile = project.file("icons/polylingy.ico")
                menuGroup = "Polylingy"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                // Must never change: lets a newer .msi upgrade an installed version in place.
                upgradeUuid = "024500e5-77f0-4b9a-93dd-5617686569a0"
            }
        }
    }
}

// The installer under a name that says the stage: ./gradlew :app:releaseMsi -> app/build/release/Polylingy-0.8.0-alpha.msi
tasks.register<Copy>("releaseMsi") {
    group = "distribution"
    description = "Builds the Windows installer and copies it to build/release under a name with the version and stage."
    dependsOn("packageMsi")
    val stage = (project.findProperty("releaseStage") as String?).orEmpty().trim()
    val name = "Polylingy-${project.version}" + (if (stage.isEmpty()) "" else "-$stage") + ".msi"
    from(layout.buildDirectory.dir("compose/binaries/main/msi")) { include("*.msi"); rename { name } }
    into(layout.buildDirectory.dir("release"))
}
