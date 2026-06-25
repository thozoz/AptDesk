import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// BUILD-01/BUILD-02: compile the Svelte+Vite frontend into src/main/assets/www
// before assets are merged, so the packaged APK never ships a stale dashboard.
// The Vite build (outDir -> assets/www, emptyOutDir:false) writes index.html +
// hashed assets/ while leaving the runtime-only www/bin and www/libs untouched.
val frontendDir = rootProject.file("frontend")
val isWindows = System.getProperty("os.name").lowercase().contains("win")

val buildFrontend by tasks.registering(Exec::class) {
    group = "build"
    description = "Build the Svelte/Vite dashboard into src/main/assets/www."
    workingDir = frontendDir

    // Re-run only when frontend sources or the manifest change; the compiled
    // bundle under assets/www is the output Gradle tracks for up-to-date checks.
    inputs.dir(File(frontendDir, "src"))
    inputs.file(File(frontendDir, "package.json"))
    inputs.file(File(frontendDir, "vite.config.js"))
    inputs.file(File(frontendDir, "index.html"))
    outputs.file(File(projectDir, "src/main/assets/www/index.html"))
    outputs.dir(File(projectDir, "src/main/assets/www/assets"))

    val npmCmd = if (isWindows) "npm.cmd" else "npm"
    // `npm ci` when a lockfile exists (reproducible CI installs), else `npm install`.
    val installArg = if (File(frontendDir, "package-lock.json").exists()) "ci" else "install"

    // Warn-and-skip when Node/npm is unavailable instead of failing the whole
    // Android build: developers without a Node toolchain can still assemble the
    // APK using whatever bundle is already present under assets/www. CI and
    // release machines are expected to have Node, so the dashboard stays fresh.
    val npmOnPath = System.getenv("PATH")
        ?.split(File.pathSeparator)
        ?.any { dir ->
            File(dir, npmCmd).exists() || (isWindows && File(dir, "npm.exe").exists())
        } ?: false

    if (frontendDir.exists() && npmOnPath) {
        commandLine(npmCmd, "run", "build")
        // Ensure deps exist before the first build on a clean checkout.
        doFirst {
            if (!File(frontendDir, "node_modules").exists()) {
                exec {
                    workingDir = frontendDir
                    commandLine(npmCmd, installArg)
                }
            }
        }
    } else {
        // No-op placeholder keeps the task graph valid; emit a clear warning.
        commandLine(if (isWindows) "cmd" else "sh", if (isWindows) "/c" else "-c", "echo")
        doFirst {
            logger.warn(
                "⚠ buildFrontend skipped: " +
                    (if (!frontendDir.exists()) "frontend/ not found. "
                    else "npm not found on PATH. ") +
                    "The APK will ship the existing assets/www bundle, which may be stale. " +
                    "Install Node.js and rebuild to refresh the dashboard."
            )
        }
    }
}

// BUILD-02: every variant (assembleDebug/assembleRelease/bundleRelease) runs
// preBuild, so depending on it here guarantees a fresh dashboard before merge.
tasks.named("preBuild") {
    dependsOn(buildFrontend)
}

// Release signing secrets live in an untracked keystore.properties at the repo root
// (see keystore.properties.example). When absent (debug/CI without a keystore), the
// release build still configures cleanly and is simply left unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

android {
    namespace = "com.aptdesk.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aptdesk.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"

        buildConfigField(
            "String",
            "ROOTFS_URL",
            "\"https://github.com/thozoz/AptDesk/releases/latest/download/aptdesk-rootfs-arm64.tar.gz\""
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // D-01: keep R8/minify OFF — it risks stripping PRoot JNI / native loaders /
            // reflection classes; APK size is dominated by .so libs R8 cannot shrink.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign only when a local keystore is present so keystore-less builds still configure.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Required for PRoot: AGP 8.0+ defaults to compressed .so files inside APK,
            // which Android cannot execute. useLegacyPackaging=true extracts them to disk
            // so the OS can set the executable bit (W^X rule, API 29+).
            useLegacyPackaging = true
            // App is ARM64-only; ProotManager.kt only ever loads libproot-loader.so
            // (the 64-bit loader). The bundled 32-bit loader has no code path that
            // references it, so it is excluded from packaging to trim APK size.
            excludes += "lib/*/libproot-loader32.so"
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // Provides Theme.Material3.DayNight.NoActionBar used in themes.xml
    implementation("com.google.android.material:material:1.12.0")

    // JVM Unit Test Dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.json:json:20240303")
}
