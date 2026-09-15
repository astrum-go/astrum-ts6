import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import br.app.astrum.gradle.GenerateAstrumCoreKotlinTask

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

private val astrumCoreRuntime = providers.gradleProperty("astrumCoreRuntime")
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

// This source set is opt-in with the native runtime. The default build keeps
// the existing JVM-only protocol and never resolves/generated UniFFI code.
val astrumCoreKotlinOutput = layout.buildDirectory.dir("generated/astrumCore/kotlin")
if (astrumCoreRuntime) {
    sourceSets {
        named("main") {
            java.srcDir(astrumCoreKotlinOutput)
            java.srcDir("src/uniffi/kotlin")
        }
    }
}

val generateAstrumCoreKotlin = tasks.register<GenerateAstrumCoreKotlinTask>("generateAstrumCoreKotlin") {
    enabled = astrumCoreRuntime
    dependsOn(":app:buildAstrumCore")
    libraryDirectory.set(project(":app").layout.buildDirectory.dir("generated/cargo/jniLibs"))
    expectedRevision.set("7f53aee5db25b17c04d9f2238ab7a75380b11ecb")
    bindgenVersion.set("0.32.1")
    outputDirectory.set(astrumCoreKotlinOutput)
}

if (astrumCoreRuntime) {
    dependencies {
        // UniFFI's Kotlin backend uses JNA for the generated FFI declarations.
        implementation("net.java.dev.jna:jna:5.14.0")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (astrumCoreRuntime) dependsOn(generateAstrumCoreKotlin)
}

dependencies {
    implementation(libs.ts3j)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.json:json:20240303")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
