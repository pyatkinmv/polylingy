plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())

    jvm("desktop")

    compilerOptions {
        optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
        getByName("desktopMain").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
        }
    }
}

sqldelight {
    databases {
        create("PolylingyDb") {
            packageName.set("com.polylingy.client.db")
        }
    }
}

// sqlite-jdbc loads a native library; JDK 24+ warns about that unless it's allowed explicitly.
tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
