import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

dependencies {
    implementation(libs.ts3j)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.json:json:20240303")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
