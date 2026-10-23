import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(libs.junit5)
}

compose.desktop {
    application {
        mainClass = "com.jordanmind.desktop.MainKt"

        buildTypes.release.proguard {
            // The Compose plugin bundles ProGuard 7.2.2, which cannot read JDK 21 class files.
            // Obfuscation buys nothing for a local desktop app anyway, so the packaged jars stay
            // readable rather than forcing a second JDK into the build.
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Jordan Mind"
            packageVersion = "1.0.0"
            description = "Offline AI agent that runs on your own machine"
            vendor = "Jordan Mind"
            windows {
                menu = true
                shortcut = true
                // Stable UUID so upgrades replace rather than duplicate the install.
                upgradeUuid = "7F3A1C2E-9B4D-4E6A-8F21-5C7D0A9B3E64"
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
