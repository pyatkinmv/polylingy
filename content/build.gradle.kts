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
        optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "kotlinx.serialization.ExperimentalSerializationApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
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
