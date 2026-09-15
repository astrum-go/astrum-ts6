package br.app.astrum.gradle

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.work.DisableCachingByDefault
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

private const val ASTRUM_CORE_REVISION = "abfd54d6a7c942b1bbbca3ca523fafc6b4b58604"
private const val ANDROID_NDK_VERSION = "27.0.12077973"
private const val ASTRUM_CORE_LIBRARY = "libastrum_core.so"
private const val UNIFFI_BINDGEN_VERSION = "0.32.1"

private data class RustAndroidTarget(
    val abi: String,
    val triple: String,
    val linker: String,
)

private val rustAndroidTargets = listOf(
    RustAndroidTarget("arm64-v8a", "aarch64-linux-android", "aarch64-linux-android26-clang"),
    RustAndroidTarget("x86_64", "x86_64-linux-android", "x86_64-linux-android26-clang"),
)

@DisableCachingByDefault(because = "Validates the external Git checkout and Android toolchain on every invocation")
abstract class BuildAstrumCoreTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    init {
        outputs.upToDateWhen { false }
    }

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rustDirectory: DirectoryProperty

    @get:Input
    abstract val rustDirectoryPath: Property<String>

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val ndkDirectory: DirectoryProperty

    @get:Input
    abstract val expectedRevision: Property<String>

    @get:Input
    abstract val ndkVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun build() {
        val rustDir = rustDirectory.orNull?.asFile
            ?: throw GradleException(
                "Missing -PastrumCoreDir. Supply a clean astrum-core checkout at revision " +
                    "$ASTRUM_CORE_REVISION; no default checkout is selected.",
            )
        if (!rustDir.isDirectory) {
            throw GradleException("astrumCoreDir is not a directory: ${rustDir.absolutePath}")
        }

        val actualRevision = captureGit(rustDir, "rev-parse", "HEAD")
        if (actualRevision != expectedRevision.get()) {
            throw GradleException(
                "astrumCoreDir must be at ${expectedRevision.get()}, but HEAD is $actualRevision: " +
                    rustDir.absolutePath,
            )
        }
        val dirtyFiles = captureGit(rustDir, "status", "--porcelain", "--untracked-files=all")
        if (dirtyFiles.isNotEmpty()) {
            throw GradleException(
                "Refusing to build dirty astrumCoreDir: ${rustDir.absolutePath}. " +
                    "Create a clean worktree at ${expectedRevision.get()} and pass it with " +
                    "-PastrumCoreDir; the supplied checkout was not modified.",
            )
        }

        val ndkDir = ndkDirectory.orNull?.asFile
            ?: throw GradleException(
                "Android NDK not found. Set ANDROID_NDK_ROOT or ANDROID_HOME with " +
                    "ndk/$ANDROID_NDK_VERSION.",
            )
        if (!ndkDir.isDirectory) {
            throw GradleException("Android NDK directory does not exist: ${ndkDir.absolutePath}")
        }
        val sourcePropertiesFile = ndkDir.resolve("source.properties")
        if (!sourcePropertiesFile.isFile) {
            throw GradleException("Android NDK source.properties does not exist: ${sourcePropertiesFile.absolutePath}")
        }
        val sourceProperties = Properties()
        sourcePropertiesFile.inputStream().use(sourceProperties::load)
        val actualNdkVersion = sourceProperties.getProperty("Pkg.Revision")
        if (actualNdkVersion != ndkVersion.get()) {
            throw GradleException(
                "Android NDK must declare Pkg.Revision=${ndkVersion.get()} in " +
                    "${sourcePropertiesFile.absolutePath}, but it declares ${actualNdkVersion ?: "<missing>"}",
            )
        }
        val llvmBin = ndkDir.resolve("toolchains/llvm/prebuilt/linux-x86_64/bin")
        val linkers = rustAndroidTargets.associateWith { target ->
            llvmBin.resolve(target.linker).also { linker ->
                if (!linker.isFile) {
                    throw GradleException("Required NDK API 26 linker does not exist: ${linker.absolutePath}")
                }
            }
        }

        val generatedDir = outputDirectory.get().asFile
        generatedDir.deleteRecursively()
        generatedDir.mkdirs()
        val cargoTargetDir = generatedDir.parentFile.resolve("cargo-target")
        cargoTargetDir.deleteRecursively()

        rustAndroidTargets.forEach { target ->
            val linker = linkers.getValue(target)
            val targetEnv = target.triple.uppercase().replace('-', '_')
            execOperations.exec {
                workingDir(rustDir)
                commandLine(
                    "cargo",
                    "build",
                    "--locked",
                    "--release",
                    "--lib",
                    "--target",
                    target.triple,
                    "--target-dir",
                    cargoTargetDir.absolutePath,
                )
                environment("ANDROID_NDK_ROOT", ndkDir.absolutePath)
                environment("CARGO_TARGET_${targetEnv}_LINKER", linker.absolutePath)
            }

            val builtLibrary = cargoTargetDir.resolve("${target.triple}/release/$ASTRUM_CORE_LIBRARY")
            if (!builtLibrary.isFile) {
                throw GradleException("Cargo did not produce ${builtLibrary.absolutePath}")
            }
            val packagedLibrary = generatedDir.resolve("${target.abi}/$ASTRUM_CORE_LIBRARY")
            packagedLibrary.parentFile.mkdirs()
            Files.copy(
                builtLibrary.toPath(),
                packagedLibrary.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun captureGit(directory: java.io.File, vararg arguments: String): String {
        val stdout = ByteArrayOutputStream()
        execOperations.exec {
            workingDir(directory)
            commandLine("git", *arguments)
            standardOutput = stdout
        }
        return stdout.toString(StandardCharsets.UTF_8.name()).trim()
    }
}

/**
 * Generates the Kotlin UniFFI module from one of the ABI libraries produced by
 * [BuildAstrumCoreTask]. The generated file is deliberately kept under
 * build/; it is an input to the protocol module, not checked-in source.
 */
@DisableCachingByDefault(because = "The input cdylibs are generated by the opt-in core build")
abstract class GenerateAstrumCoreKotlinTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val coreDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val libraryDirectory: DirectoryProperty

    @get:Input
    abstract val expectedRevision: Property<String>

    @get:Input
    abstract val bindgenVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        check(expectedRevision.get() == ASTRUM_CORE_REVISION) {
            "Unexpected Astrum Core revision configured for UniFFI generation: " +
                expectedRevision.get()
        }
        check(bindgenVersion.get() == UNIFFI_BINDGEN_VERSION) {
            "UniFFI bindgen must be pinned to $UNIFFI_BINDGEN_VERSION"
        }

        val libraries = rustAndroidTargets.map { target ->
            target to libraryDirectory.get().asFile.resolve("${target.abi}/$ASTRUM_CORE_LIBRARY")
        }
        libraries.forEach { (target, library) ->
            check(library.isFile) {
                "Generated Astrum Core library is missing for ${target.abi}: ${library.absolutePath}"
            }
        }

        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val versionOutput = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("uniffi-bindgen", "--version")
            standardOutput = versionOutput
        }
        val actualVersion = versionOutput.toString(StandardCharsets.UTF_8.name()).trim()
        check(actualVersion == "uniffi-bindgen $UNIFFI_BINDGEN_VERSION") {
            "Expected uniffi-bindgen $UNIFFI_BINDGEN_VERSION, but found $actualVersion"
        }

        // UniFFI metadata is ABI-independent. Generate once from arm64-v8a;
        // both packaged cdylibs expose the same component contract.
        val sourceLibrary = libraries.first { it.first.abi == "arm64-v8a" }.second
        execOperations.exec {
            workingDir(coreDirectory)
            commandLine(
                "uniffi-bindgen",
                "generate",
                "--library",
                sourceLibrary.absolutePath,
                "--language",
                "kotlin",
                "--out-dir",
                output.absolutePath,
            )
        }

        check(output.resolve("uniffi/astrum_core/astrum_core.kt").isFile) {
            "UniFFI did not generate uniffi/astrum_core/astrum_core.kt in ${output.absolutePath}"
        }
    }
}

abstract class WriteAstrumCoreRuntimeMarkerTask : DefaultTask() {
    @get:Input
    abstract val runtimeEnabled: Property<Boolean>

    @get:OutputFile
    abstract val markerFile: RegularFileProperty

    @TaskAction
    fun writeMarker() {
        val file = markerFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText("${runtimeEnabled.get()}\n", StandardCharsets.UTF_8)
    }
}

abstract class InspectAstrumCoreApkTask : DefaultTask() {
    @get:Input
    abstract val runtimeEnabled: Property<Boolean>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkFile: RegularFileProperty

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedLibraries: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeMarker: RegularFileProperty

    @TaskAction
    fun inspect() {
        val expectedRuntimeMarker = "${runtimeEnabled.get()}\n"
        check(runtimeMarker.get().asFile.readText(StandardCharsets.UTF_8) == expectedRuntimeMarker) {
            "Generated Astrum Core runtime marker does not match the requested runtime mode"
        }
        check(runtimeEnabled.get()) {
            "inspectAstrumCoreApk requires -PastrumCoreRuntime=true; Rust runtime is disabled"
        }
        val expectedEntries = rustAndroidTargets.map { target ->
            "lib/${target.abi}/$ASTRUM_CORE_LIBRARY"
        }.toSet()
        ZipFile(apkFile.get().asFile).use { apk ->
            val apkMarkerEntry = apk.getEntry("assets/astrum-core-runtime-mode.txt")
                ?: error("APK is missing the Astrum Core runtime marker")
            val apkMarker = apk.getInputStream(apkMarkerEntry).use { it.readBytes().toString(StandardCharsets.UTF_8) }
            check(apkMarker == expectedRuntimeMarker) {
                "APK Astrum Core runtime marker is $apkMarker, expected $expectedRuntimeMarker"
            }
            logger.lifecycle("Astrum Core runtime marker: ${apkMarker.trim()}")

            val actualEntries = apk.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("lib/") && it.name.endsWith("/$ASTRUM_CORE_LIBRARY") }
                .map { it.name }
                .toSet()
            check(actualEntries == expectedEntries) {
                "APK contains $ASTRUM_CORE_LIBRARY entries $actualEntries, expected $expectedEntries"
            }

            rustAndroidTargets.forEach { target ->
                val generated = generatedLibraries.get().asFile
                    .resolve("${target.abi}/$ASTRUM_CORE_LIBRARY")
                check(generated.isFile) { "Generated library is missing: ${generated.absolutePath}" }
                val apkEntry = apk.getEntry("lib/${target.abi}/$ASTRUM_CORE_LIBRARY")
                    ?: error("APK entry is missing for ${target.abi}")
                val generatedHash = sha256(generated.readBytes())
                val apkHash = apk.getInputStream(apkEntry).use { sha256(it.readBytes()) }
                check(generatedHash == apkHash) {
                    "Hash mismatch for ${target.abi}: generated=$generatedHash apk=$apkHash"
                }
                logger.lifecycle("${target.abi}: $generatedHash")
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
