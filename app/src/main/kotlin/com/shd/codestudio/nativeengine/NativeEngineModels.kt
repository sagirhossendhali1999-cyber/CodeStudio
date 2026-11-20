package com.shd.codestudio.nativeengine

import java.io.File

enum class EngineBuildVariant(val label: String, val isDebug: Boolean) {
    DEBUG("Debug", true),
    RELEASE("Release", false)
}

data class MavenCoordinate(
    val group: String,
    val artifact: String,
    val version: String,
    val packaging: String = "jar"
) {
    val groupArtifactKey: String
        get() = "$group:$artifact"

    val notation: String
        get() = if (version.isNotBlank()) "$group:$artifact:$version" else "$group:$artifact"
}

data class ResolvedLibrary(
    val coordinate: MavenCoordinate,
    val jarFiles: List<File> = emptyList(),
    val resDir: File? = null,
    val manifestFile: File? = null,
    val rTxtFile: File? = null,
    val jniDir: File? = null,
    val assetsDir: File? = null,
    val proguardFile: File? = null,
    val isAar: Boolean = false
)

data class ModuleConfig(
    val moduleName: String,
    val moduleDir: File,
    val isApplication: Boolean = true,
    val namespace: String,
    val applicationId: String,
    val compileSdk: Int = 36,
    val minSdk: Int = 30,
    val targetSdk: Int = 36,
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val composeEnabled: Boolean = false,
    val serializationEnabled: Boolean = false,
    val parcelizeEnabled: Boolean = false,
    val viewBindingEnabled: Boolean = false,
    val buildConfigEnabled: Boolean = true,
    val nonTransitiveRClass: Boolean = false,
    val kotlinVersion: String = "2.4.21",
    val javaVersion: Int = 17,
    val sourceDirs: List<File> = emptyList(),
    val resDirs: List<File> = emptyList(),
    val assetsDirs: List<File> = emptyList(),
    val manifestFile: File? = null,
    val googleServicesFile: File? = null,
    val proguardFiles: List<File> = emptyList(),
    val localJarFiles: List<File> = emptyList(),
    val dependencies: List<MavenCoordinate> = emptyList(),
    val bomCoordinates: List<MavenCoordinate> = emptyList(),
    val projectDependencies: List<String> = emptyList()
)

data class ParsedProjectModel(
    val projectName: String,
    val rootDir: File,
    val repositories: List<String>,
    val modules: List<ModuleConfig>,
    val appModule: ModuleConfig
)

data class BuildDiagnosticItem(
    val filePath: String,
    val line: Int = 0,
    val column: Int = 0,
    val message: String,
    val isError: Boolean = true
)

data class NativeBuildRequest(
    val projectName: String,
    val variant: EngineBuildVariant = EngineBuildVariant.DEBUG,
    val cleanFirst: Boolean = false,
    val buildAppBundle: Boolean = false,
    val customKeystore: CustomKeystoreConfig? = null
)

data class NativeBuildSummary(
    val success: Boolean,
    val durationMs: Long,
    val outputApkPath: String = "",
    val outputAabPath: String = "",
    val message: String = "",
    val executedTasks: Int = 0,
    val skippedUpToDateTasks: Int = 0,
    val diagnostics: List<BuildDiagnosticItem> = emptyList()
)

data class TaskExecutionContext(
    val log: (String) -> Unit,
    val isCancelled: () -> Boolean
)

data class TaskNode(
    val id: String,
    val displayName: String,
    val dependencies: List<String> = emptyList(),
    val inputFiles: () -> List<File> = { emptyList() },
    val inputStrings: () -> List<String> = { emptyList() },
    val outputFiles: () -> List<File> = { emptyList() },
    val forceRun: Boolean = false,
    val action: suspend (TaskExecutionContext) -> Unit
)