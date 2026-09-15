import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import br.app.astrum.gradle.BuildAstrumCoreTask
import br.app.astrum.gradle.InspectAstrumCoreApkTask
import br.app.astrum.gradle.WriteAstrumCoreRuntimeMarkerTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

private val ASTRUM_CORE_REVISION = "abfd54d6a7c942b1bbbca3ca523fafc6b4b58604"
private val ANDROID_NDK_VERSION = "27.0.12077973"
private val rustAndroidAbis = listOf("arm64-v8a", "x86_64")

val astrumCoreDirPath = providers.gradleProperty("astrumCoreDir")
val astrumCoreRuntime = providers.gradleProperty("astrumCoreRuntime")
    .map { value ->
        when (value) {
            "true" -> true
            "false" -> false
            else -> throw GradleException(
                "astrumCoreRuntime must be explicitly true or false, but was: $value",
            )
        }
    }
    .orElse(false)
    .get()
if (astrumCoreRuntime && !astrumCoreDirPath.isPresent) {
    throw GradleException(
        "-PastrumCoreRuntime=true requires -PastrumCoreDir so libastrum_core.so can be packaged",
    )
}
val astrumCoreEnabled = astrumCoreRuntime
val astrumCoreBackend = providers.gradleProperty("astrumCoreBackend")
    .map { value ->
        when (value.trim().lowercase()) {
            "", "ts3j", "jni", "uniffi" -> value.trim().lowercase().ifEmpty { "jni" }
            else -> throw GradleException(
                "astrumCoreBackend must be one of ts3j, jni, or uniffi, but was: $value",
            )
        }
    }
    .orElse("jni")
    .get()
if (astrumCoreBackend == "uniffi" && !astrumCoreRuntime) {
    throw GradleException("astrumCoreBackend=uniffi requires -PastrumCoreRuntime=true")
}
val astrumCoreRuntimeMarkerDirectory = layout.buildDirectory.dir("generated/astrumCore")
val astrumCoreRuntimeMarkerFile = astrumCoreRuntimeMarkerDirectory.map { it.file("astrum-core-runtime-mode.txt") }
val androidNdkDirectory: Provider<Directory> = providers.environmentVariable("ANDROID_NDK_ROOT")
    .orElse(
        providers.environmentVariable("ANDROID_HOME")
            .map { "$it/ndk/$ANDROID_NDK_VERSION" },
    )
    .map { project.layout.projectDirectory.dir(it) }
val cargoJniLibs = layout.buildDirectory.dir("generated/cargo/jniLibs")

android {
    namespace = "br.app.astrum.ts6.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.app.astrum.ts6"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.0.0"

        buildConfigField("boolean", "ASTRUM_CORE_RUNTIME", astrumCoreRuntime.toString())
        buildConfigField("String", "ASTRUM_CORE_BACKEND", "\"$astrumCoreBackend\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += rustAndroidAbis
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            val keystoreFile = if (!keystorePath.isNullOrBlank()) {
                file(keystorePath)
            } else {
                rootProject.file("astrum-release.jks")
            }
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: "astrumts6community"
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "astrum"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "astrumts6community"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
        debug {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
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

    if (astrumCoreEnabled) {
        sourceSets {
            getByName("main") {
                jniLibs.srcDir(cargoJniLibs)
            }
        }
    }
    sourceSets {
        getByName("main") {
            assets.srcDir(astrumCoreRuntimeMarkerDirectory)
        }
    }

    packaging {
        jniLibs.keepDebugSymbols += "**/libastrum_core.so"
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
        )
    }
}

dependencies {
    implementation(project(":ts6-protocol"))
    implementation(project(":audio-opus"))
    implementation(libs.stream.webrtc)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
}

val buildAstrumCore = tasks.register<BuildAstrumCoreTask>("buildAstrumCore") {
    if (astrumCoreDirPath.isPresent) {
        rustDirectory.set(layout.projectDirectory.dir(astrumCoreDirPath.get()))
    }
    rustDirectoryPath.set(astrumCoreDirPath.orElse(""))
    ndkDirectory.set(androidNdkDirectory)
    expectedRevision.set(ASTRUM_CORE_REVISION)
    ndkVersion.set(ANDROID_NDK_VERSION)
    outputDirectory.set(cargoJniLibs)
}

val writeAstrumCoreRuntimeMarker = tasks.register<WriteAstrumCoreRuntimeMarkerTask>("writeAstrumCoreRuntimeMarker") {
    runtimeEnabled.set(astrumCoreRuntime)
    markerFile.set(astrumCoreRuntimeMarkerFile)
}

tasks.configureEach {
    if (name.endsWith("BuildConfig")) {
        inputs.property("astrumCoreRuntime", astrumCoreRuntime)
    }
    if (name == "assemble" || name.startsWith("assemble") ||
        name == "package" || name.startsWith("package") ||
        (name.startsWith("merge") && name.endsWith("Assets"))
    ) {
        dependsOn(writeAstrumCoreRuntimeMarker)
    }
    if (astrumCoreEnabled) {
        if (name == "assemble" || name.startsWith("assemble") ||
            name == "package" || name.startsWith("package")
        ) {
            dependsOn(buildAstrumCore)
        }
        if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
            dependsOn(buildAstrumCore)
        }
    }
}

tasks.register<InspectAstrumCoreApkTask>("inspectAstrumCoreApk") {
    dependsOn(writeAstrumCoreRuntimeMarker, "assembleDebug")
    if (astrumCoreEnabled) {
        dependsOn(buildAstrumCore)
    }
    runtimeEnabled.set(astrumCoreRuntime)
    apkFile.set(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    generatedLibraries.set(cargoJniLibs)
    runtimeMarker.set(astrumCoreRuntimeMarkerFile)
}
