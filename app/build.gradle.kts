import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Optional: bundle a LiteRT-LM / MediaPipe `.task` model straight into the APK.
//   ./gradlew assembleDebug -PbundleModel=/path/to/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task
// When unset, the APK ships without a model and the app installs one from device storage.
val bundleModelPath: String? = (project.findProperty("bundleModel") as String?)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.jordanmind.agent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jordanmind.agent"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }

        // Phone ABIs only. Dropping x86/x86_64 removes ~63 MB of emulator-only native libraries.
        // Re-add them if you want to run this on an Android emulator.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    // Model blobs are already dense; keeping them uncompressed makes asset copies
    // fast and avoids AAPT2's large-asset compression edge cases.
    androidResources {
        noCompress += listOf("task", "litertlm", "bin", "gguf")
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
            )
        }
    }

    sourceSets {
        getByName("main") {
            if (bundleModelPath != null) {
                assets.srcDirs("src/main/assets", file("build/bundled-assets"))
            }
        }
    }
}

// Stage the model into build/bundled-assets/models/<name> before packaging.
val stageBundledModel by tasks.registering(Copy::class) {
    val src = bundleModelPath?.let { file(it) }
    if (src != null && src.exists()) {
        from(src) { into("models") }
        into(layout.buildDirectory.dir("bundled-assets"))
        rename { "qwen-bundled.task" }
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    if (bundleModelPath != null) dependsOn(stageBundledModel)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.pdfbox.android)

    testImplementation("junit:junit:4.13.2")
}
