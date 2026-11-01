plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())

    // Only the desktop (JVM) target for now; iOS/Android/JS are added here when needed.
    jvm()

    compilerOptions {
        optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "kotlin.time.ExperimentalTime", "kotlinx.serialization.ExperimentalSerializationApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
            api(project(":content"))
            api(libs.kotlin.logging)
        }
        jvmMain.dependencies {
            // kotlin-logging writes through SLF4J on the JVM; the app picks the backend (logback).
            api(libs.slf4j.api)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

kover {
    reports {
        verify {
            rule { minBound(90) }
        }
    }
}

tasks.named("check") { dependsOn("koverVerify") }
