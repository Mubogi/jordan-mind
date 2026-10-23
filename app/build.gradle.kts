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
        versionCode = 2
        versionName = "1.1"
        vectorDrawables { useSupportLibrary = true }

        // Phone ABIs only. Dropping x86/x86_64 removes ~63 MB of emulator-only native libraries.
        // Re-add them if you want to run this on an Android emulator.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            // A checked-in debug keystore would be a real credential in the repo. Instead the
            // release key is generated on first use and kept out of version control; supply
            // RELEASE_STORE_FILE / _PASSWORD / _ALIAS / _KEY_PASSWORD to sign reproducibly.
            val storePath = System.getenv("RELEASE_STORE_FILE")
                ?: rootProject.file("keystore/release.jks").absolutePath
            val store = file(storePath)
            if (store.exists()) {
                storeFile = store
                storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "jordanmind"
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "jordanmind"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "jordanmind"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
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

    testOptions {
        unitTests {
            // Robolectric renders the real Compose UI on the JVM, so it needs merged resources.
            isIncludeAndroidResources = true
        }
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

// Any task that reads the assets directory must wait for the model to be staged there. Lint's
// model writer reads it too, which is easy to miss and fails the build with an implicit-dependency
// error rather than anything obviously related to the model.
tasks.matching {
    val name = it.name.lowercase()
    (name.startsWith("merge") && name.endsWith("assets")) || name.contains("lint")
}.configureEach {
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
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

