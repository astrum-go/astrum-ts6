import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import br.app.astrum.gradle.BuildAstrumCoreTask
import br.app.astrum.gradle.InspectAstrumCoreApkTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

private val ASTRUM_CORE_REVISION = "52001dd2738e1509253b5264012ef907f940e938"
private val ANDROID_NDK_VERSION = "27.0.12077973"
private val rustAndroidAbis = listOf("arm64-v8a", "x86_64")

val astrumCoreDir: Provider<Directory> = providers.gradleProperty("astrumCoreDir")
    .map { project.layout.projectDirectory.dir(it) }
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

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(cargoJniLibs)
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
    rustDirectory.set(astrumCoreDir)
    rustDirectoryPath.set(providers.gradleProperty("astrumCoreDir").orElse(""))
    rustInputs.from(
        astrumCoreDir.map { directory ->
            project.fileTree(directory.asFile) {
                exclude(".git/**", "target/**", "build/**")
            }
        },
    )
    ndkDirectory.set(androidNdkDirectory)
    expectedRevision.set(ASTRUM_CORE_REVISION)
    ndkVersion.set(ANDROID_NDK_VERSION)
    outputDirectory.set(cargoJniLibs)
}

tasks.configureEach {
    if (name == "assemble" || name.startsWith("assemble") ||
        name == "package" || name.startsWith("package")
    ) {
        dependsOn(buildAstrumCore)
    }
    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
        dependsOn(buildAstrumCore)
    }
}

tasks.register<InspectAstrumCoreApkTask>("inspectAstrumCoreApk") {
    dependsOn(buildAstrumCore, "assembleDebug")
    apkFile.set(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    generatedLibraries.set(cargoJniLibs)
}
