package com.shd.codestudio.nativeengine

import android.content.Context
import com.shd.codestudio.homescreen.getProjectDirectory
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.Properties

private data class TomlCatalog(
    val versions: Map<String, String> = emptyMap(),
    val libraries: Map<String, MavenCoordinate> = emptyMap(),
    val bundles: Map<String, List<String>> = emptyMap(),
    val pluginKotlinVersion: String? = null
)

object DeclarativeProjectParser {

    private const val GOOGLE_MAVEN = "https://dl.google.com/dl/android/maven2/"
    private const val MAVEN_CENTRAL = "https://repo1.maven.org/maven2/"
    private const val JITPACK = "https://jitpack.io/"
    private const val GRADLE_PLUGIN_PORTAL = "https://plugins.gradle.org/m2/"

    fun parseProject(context: Context, projectName: String): ParsedProjectModel {
        val rootDir = getProjectDirectory(context, projectName)
        return parseProjectDir(rootDir, projectName)
    }

    fun parseProjectDir(rootDir: File, projectName: String): ParsedProjectModel {
        val catalog = parseVersionCatalog(File(rootDir, "gradle/libs.versions.toml"))
        val gradleProps = parseGradleProperties(File(rootDir, "gradle.properties"))
        val nonTransitiveR = gradleProps.getProperty("android.nonTransitiveRClass", "false")
            .trim().equals("true", ignoreCase = true)

        val settingsText = readFirstExisting(
            File(rootDir, "settings.gradle.kts"),
            File(rootDir, "settings.gradle")
        )
        val rootBuildText = readFirstExisting(
            File(rootDir, "build.gradle.kts"),
            File(rootDir, "build.gradle")
        )

        val repositories = parseRepositories(settingsText + "\n" + rootBuildText)
        val moduleEntries = parseIncludedModules(rootDir, settingsText)

        val extraRepos = LinkedHashSet(repositories)
        val modules = moduleEntries.map { (modName, modDir) ->
            val modBuild = readFirstExisting(
                File(modDir, "build.gradle.kts"),
                File(modDir, "build.gradle")
            )
            extraRepos.addAll(parseRepositories(modBuild))
            parseModule(modName, modDir, modBuild, rootBuildText, catalog, projectName, nonTransitiveR)
        }

        val appModule = modules.firstOrNull { it.isApplication } ?: modules.first()
        return ParsedProjectModel(
            projectName = projectName,
            rootDir = rootDir,
            repositories = extraRepos.toList(),
            modules = modules,
            appModule = appModule
        )
    }

    private fun parseGradleProperties(file: File): Properties {
        val props = Properties()
        if (file.exists() && file.isFile) {
            runCatching {
                file.reader(Charsets.UTF_8).use { props.load(it) }
            }
        }
        return props
    }

    private fun readFirstExisting(vararg files: File): String {
        for (f in files) {
            if (f.exists() && f.isFile) {
                return runCatching { f.readText(Charsets.UTF_8) }.getOrDefault("")
            }
        }
        return ""
    }

    // ================= ১. রিপোজিটরি পার্সার =================
    private fun parseRepositories(text: String): List<String> {
        val clean = stripCommentsPreservingStrings(text)
        val repos = LinkedHashSet<String>()

        if (clean.contains("google()") || !clean.contains("repositories")) {
            repos.add(GOOGLE_MAVEN)
        }
        if (clean.contains("mavenCentral()") || !clean.contains("repositories")) {
            repos.add(MAVEN_CENTRAL)
        }
        if (clean.contains("jitpack.io", ignoreCase = true)) {
            repos.add(JITPACK)
        }
        if (clean.contains("gradlePluginPortal()")) {
            repos.add(GRADLE_PLUGIN_PORTAL)
        }

        val customUrlRegex = Regex("""(?:uri\s*\(\s*|url\s*[= ]\s*)["'](https?://[^"']+)["']""")
        customUrlRegex.findAll(clean).forEach { m ->
            val rawUrl = m.groupValues[1].trim()
            if (rawUrl.isNotEmpty()) {
                repos.add(if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/")
            }
        }

        repos.add(GOOGLE_MAVEN)
        repos.add(MAVEN_CENTRAL)
        return repos.toList()
    }

    // ================= ২. মডিউল ডিসকভারি (settings.gradle.kts) =================
    private fun parseIncludedModules(rootDir: File, settingsText: String): List<Pair<String, File>> {
        val clean = stripCommentsPreservingStrings(settingsText)
        val names = LinkedHashSet<String>()

        val includeRegex = Regex("""include\s*\(([^)]+)\)|include\s+([^\n\r]+)""")
        includeRegex.findAll(clean).forEach { m ->
            val argBlock = m.groupValues[1].ifBlank { m.groupValues[2] }
            Regex("""["'](:?[^"']+)["']""").findAll(argBlock).forEach { sm ->
                val raw = sm.groupValues[1].trim()
                if (raw.isNotEmpty()) {
                    names.add(if (raw.startsWith(":")) raw else ":$raw")
                }
            }
        }

        if (names.isEmpty()) {
            if (File(rootDir, "app").isDirectory) names.add(":app")
            else return listOf(":app" to rootDir)
        }

        val customDirs = HashMap<String, File>()
        val dirOverrideRegex = Regex("""project\s*\(\s*["'](:?[^"']+)["']\s*\)\.projectDir\s*=\s*file\s*\(\s*["']([^"']+)["']\s*\)""")
        dirOverrideRegex.findAll(clean).forEach { m ->
            val modName = m.groupValues[1].let { if (it.startsWith(":")) it else ":$it" }
            val path = m.groupValues[2].trim()
            customDirs[modName] = File(rootDir, path)
        }

        return names.map { modName ->
            val defaultDir = File(rootDir, modName.removePrefix(":").replace(":", "/"))
            val dir = customDirs[modName] ?: defaultDir
            modName to dir
        }
    }

    // ================= ৩. Version Catalog (libs.versions.toml) পার্সার =================
    private fun parseVersionCatalog(tomlFile: File): TomlCatalog {
        if (!tomlFile.exists() || !tomlFile.isFile) return TomlCatalog()
        val lines = runCatching { tomlFile.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())

        val versions = HashMap<String, String>()
        val libraries = HashMap<String, MavenCoordinate>()
        val bundles = HashMap<String, List<String>>()
        var pluginKotlinVer: String? = null
        var section = ""

        for (rawLine in lines) {
            val line = stripTomlComment(rawLine).trim()
            if (line.isEmpty()) continue

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim().lowercase()
                continue
            }

            val eqIdx = line.indexOf('=')
            if (eqIdx <= 0) continue
            val rawKey = line.substring(0, eqIdx).trim().removeSurrounding("\"").removeSurrounding("'")
            val rhs = line.substring(eqIdx + 1).trim()
            val normalizedKey = normalizeCatalogAlias(rawKey)

            when (section) {
                "versions" -> {
                    val v = rhs.removeSurrounding("\"").removeSurrounding("'").trim()
                    if (v.isNotEmpty()) versions[rawKey] = v
                }

                "libraries" -> {
                    if (rhs.startsWith("{") && rhs.endsWith("}")) {
                        val inner = rhs.substring(1, rhs.length - 1)
                        val group = extractTomlField(inner, "group")
                        val name = extractTomlField(inner, "name")
                        val module = extractTomlField(inner, "module")
                        val verDirect = extractTomlField(inner, "version")
                        val verRef = extractTomlField(inner, "version.ref")

                        val resolvedVer = when {
                            verRef != null -> versions[verRef] ?: ""
                            verDirect != null -> verDirect
                            else -> ""
                        }

                        if (module != null && module.contains(":")) {
                            val parts = module.split(":")
                            libraries[normalizedKey] = MavenCoordinate(parts[0].trim(), parts[1].trim(), resolvedVer)
                        } else if (group != null && name != null) {
                            libraries[normalizedKey] = MavenCoordinate(group, name, resolvedVer)
                        }
                    } else {
                        val notation = rhs.removeSurrounding("\"").removeSurrounding("'").trim()
                        val parts = notation.split(":")
                        if (parts.size >= 2) {
                            libraries[normalizedKey] = MavenCoordinate(
                                group = parts[0].trim(),
                                artifact = parts[1].trim(),
                                version = parts.getOrNull(2)?.trim() ?: ""
                            )
                        }
                    }
                }

                "bundles" -> {
                    if (rhs.startsWith("[") && rhs.endsWith("]")) {
                        val items = Regex("""["']([^"']+)["']""").findAll(rhs)
                            .map { normalizeCatalogAlias(it.groupValues[1]) }
                            .toList()
                        bundles[normalizedKey] = items
                    }
                }

                "plugins" -> {
                    if (rawKey.contains("kotlin", ignoreCase = true)) {
                        val verRef = extractTomlField(rhs, "version.ref")
                        val verDirect = extractTomlField(rhs, "version")
                        pluginKotlinVer = verRef?.let { versions[it] } ?: verDirect ?: pluginKotlinVer
                    }
                }
            }
        }

        return TomlCatalog(
            versions = versions,
            libraries = libraries,
            bundles = bundles,
            pluginKotlinVersion = pluginKotlinVer
        )
    }

    private fun normalizeCatalogAlias(alias: String): String =
        alias.trim().replace("-", ".").replace("_", ".")

    private fun extractTomlField(tableInner: String, field: String): String? {
        val escaped = Regex.escape(field)
        val m = Regex("""\b$escaped\s*=\s*["']([^"']+)["']""").find(tableInner)
        return m?.groupValues?.getOrNull(1)?.trim()
    }

    private fun stripTomlComment(line: String): String {
        var inQuote = false
        var quoteChar = '"'
        for (i in line.indices) {
            val c = line[i]
            if (!inQuote && (c == '"' || c == '\'')) {
                inQuote = true
                quoteChar = c
            } else if (inQuote && c == quoteChar && (i == 0 || line[i - 1] != '\\')) {
                inQuote = false
            } else if (!inQuote && c == '#') {
                return line.substring(0, i)
            }
        }
        return line
    }

    // ================= ৪. মডিউল কনফিগারেশন ও ডিপেন্ডেন্সি পার্সার =================
    private fun parseModule(
        moduleName: String,
        moduleDir: File,
        buildText: String,
        rootBuildText: String,
        catalog: TomlCatalog,
        projectName: String,
        nonTransitiveR: Boolean
    ): ModuleConfig {
        val clean = stripCommentsPreservingStrings(buildText)
        val manifestFile = File(moduleDir, "src/main/AndroidManifest.xml").takeIf { it.exists() }
        val manifestPackage = manifestFile?.let { readManifestPackageAttr(it) }

        val isApp = !clean.contains("com.android.library") &&
            !clean.contains("android.library") &&
            !clean.contains("java-library") &&
            !clean.contains("kotlin(\"jvm\")") &&
            !clean.contains("org.jetbrains.kotlin.jvm")

        val namespace = Regex("""namespace\s*(?:=|\s)\s*["']([^"']+)["']""").find(clean)?.groupValues?.getOrNull(1)
            ?: manifestPackage
            ?: "com.example.${projectName.lowercase().replace(Regex("[^a-z0-9]"), "")}"

        val applicationId = Regex("""applicationId\s*(?:=|\s)\s*["']([^"']+)["']""").find(clean)?.groupValues?.getOrNull(1)
            ?: namespace

        val compileSdk = Regex("""compileSdk(?:Version)?\s*(?:=|\(|\s)\s*([0-9]+)""").find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""release\s*\(\s*([0-9]+)\s*\)""").find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 36

        val minSdk = Regex("""minSdk(?:Version)?\s*(?:=|\(|\s)\s*([0-9]+)""").find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 24

        val targetSdk = Regex("""targetSdk(?:Version)?\s*(?:=|\(|\s)\s*([0-9]+)""").find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: compileSdk

        val versionCode = Regex("""versionCode\s*(?:=|\s)\s*([0-9]+)""").find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 1

        val versionName = Regex("""versionName\s*(?:=|\s)\s*["']([^"']+)["']""").find(clean)?.groupValues?.getOrNull(1)
            ?: "1.0"

        val composeEnabled = Regex("""compose\s*=\s*true""").containsMatchIn(clean) ||
            clean.contains("kotlin.compose") ||
            clean.contains("kotlin-compose") ||
            clean.contains("composeOptions")

        val viewBindingEnabled = Regex("""viewBinding\s*(?:=|\s)\s*true""").containsMatchIn(clean)
        val buildConfigExplicitFalse = Regex("""buildConfig\s*=\s*false""").containsMatchIn(clean)

        val kotlinVersion = catalog.pluginKotlinVersion
            ?: Regex("""kotlin[^"\n]*version\s*["']([^"']+)["']""").find(rootBuildText)?.groupValues?.getOrNull(1)
            ?: "2.4.21"

        val javaVersion = Regex("""VERSION_([0-9]+)""").find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""jvmTarget\s*(?:=|\s)\s*["']([0-9]+)["']""").find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 17

        val sourceDirs = listOfNotNull(
            File(moduleDir, "src/main/kotlin").takeIf { it.exists() },
            File(moduleDir, "src/main/java").takeIf { it.exists() }
        )

        val resDirs = listOfNotNull(
            File(moduleDir, "src/main/res").takeIf { it.exists() }
        )

        val assetsDirs = listOfNotNull(
            File(moduleDir, "src/main/assets").takeIf { it.exists() }
        )

        val proguardFiles = ArrayList<File>()
        val localPro = File(moduleDir, "proguard-rules.pro")
        if (localPro.exists()) proguardFiles.add(localPro)

        val localJars = ArrayList<File>()
        val libsDir = File(moduleDir, "libs")
        if (libsDir.exists() && libsDir.isDirectory) {
            libsDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".jar", ignoreCase = true) }
                .forEach { localJars.add(it) }
        }

        val deps = ArrayList<MavenCoordinate>()
        val boms = ArrayList<MavenCoordinate>()
        val projectDeps = ArrayList<String>()

        val depsBlock = extractBraceBlockAfter(clean, "dependencies") ?: ""
        if (depsBlock.isNotEmpty()) {
            for (rawLine in depsBlock.lines()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("testImplementation") ||
                    line.startsWith("androidTestImplementation") ||
                    line.startsWith("testCompileOnly") ||
                    line.startsWith("testRuntimeOnly") ||
                    line.startsWith("debugImplementation")
                ) {
                    continue
                }

                // project(":core")
                Regex("""project\s*\(\s*["'](:?[^"']+)["']\s*\)""").find(line)?.let { pm ->
                    val targetMod = pm.groupValues[1].let { if (it.startsWith(":")) it else ":$it" }
                    if (targetMod !in projectDeps) projectDeps.add(targetMod)
                    return@let
                }

                // files("libs/xxx.jar")
                Regex("""files\s*\(\s*["']([^"']+)["']\s*\)""").find(line)?.let { fm ->
                    val jf = File(moduleDir, fm.groupValues[1])
                    if (jf.exists() && jf.isFile && jf !in localJars) localJars.add(jf)
                }

                val isPlatform = line.contains("platform(")

                // ১. সরাসরি স্ট্রিং ডিপেন্ডেন্সি
                Regex("""["']([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+)(?::([A-Za-z0-9_.-]+))?["']""").find(line)?.let { gm ->
                    val coord = MavenCoordinate(
                        group = gm.groupValues[1],
                        artifact = gm.groupValues[2],
                        version = gm.groupValues[3]
                    )
                    if (isPlatform) boms.add(coord) else deps.add(coord)
                    continue
                }

                // ২. Version Catalog রেফারেন্স
                Regex("""\blibs\.([A-Za-z0-9_.]+)""").find(line)?.let { lm ->
                    val aliasPath = lm.groupValues[1]
                    if (aliasPath.startsWith("bundles.")) {
                        val bundleKey = normalizeCatalogAlias(aliasPath.removePrefix("bundles."))
                        catalog.bundles[bundleKey]?.forEach { libKey ->
                            catalog.libraries[libKey]?.let { deps.add(it) }
                        }
                    } else {
                        val libKey = normalizeCatalogAlias(aliasPath)
                        catalog.libraries[libKey]?.let { coord ->
                            if (isPlatform) boms.add(coord) else deps.add(coord)
                        }
                    }
                }
            }
        }

        return ModuleConfig(
            moduleName = moduleName,
            moduleDir = moduleDir,
            isApplication = isApp,
            namespace = namespace,
            applicationId = applicationId,
            compileSdk = compileSdk,
            minSdk = minSdk,
            targetSdk = targetSdk,
            versionCode = versionCode,
            versionName = versionName,
            composeEnabled = composeEnabled,
            viewBindingEnabled = viewBindingEnabled,
            buildConfigEnabled = !buildConfigExplicitFalse,
            nonTransitiveRClass = nonTransitiveR,
            kotlinVersion = kotlinVersion,
            javaVersion = javaVersion,
            sourceDirs = sourceDirs,
            resDirs = resDirs,
            assetsDirs = assetsDirs,
            manifestFile = manifestFile,
            proguardFiles = proguardFiles,
            localJarFiles = localJars,
            dependencies = deps.distinctBy { it.groupArtifactKey },
            bomCoordinates = boms.distinctBy { it.groupArtifactKey },
            projectDependencies = projectDeps
        )
    }

    private fun readManifestPackageAttr(manifestFile: File): String? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(manifestFile.readText(Charsets.UTF_8)))
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "manifest") {
                    return parser.getAttributeValue(null, "package")?.takeIf { it.isNotBlank() }
                }
                ev = parser.next()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractBraceBlockAfter(text: String, keyword: String): String? {
        val m = Regex("""\b$keyword\s*\{""").find(text) ?: return null
        val start = m.range.last
        var depth = 0
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (c == '"' || c == '\'') {
                i = skipQuoted(text, i) + 1
                continue
            }
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return text.substring(start + 1, i)
            }
            i++
        }
        return null
    }

    private fun stripCommentsPreservingStrings(code: String): String {
        val sb = StringBuilder(code.length)
        var i = 0
        val len = code.length
        while (i < len) {
            val c = code[i]
            if (c == '"' || c == '\'') {
                val endIdx = skipQuoted(code, i)
                sb.append(code, i, minOf(len, endIdx + 1))
                i = endIdx + 1
                continue
            }
            if (c == '/' && i + 1 < len && code[i + 1] == '/') {
                while (i < len && code[i] != '\n') i++
                continue
            }
            if (c == '/' && i + 1 < len && code[i + 1] == '*') {
                val endC = code.indexOf("*/", i + 2)
                i = if (endC == -1) len else endC + 2
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun skipQuoted(str: String, i: Int): Int {
        val q = str[i]
        if (q == '"' && str.startsWith("\"\"\"", i)) {
            val end = str.indexOf("\"\"\"", i + 3)
            return if (end == -1) str.length - 1 else end + 2
        }
        var j = i + 1
        while (j < str.length) {
            if (str[j] == '\\') {
                j += 2
                continue
            }
            if (str[j] == q || str[j] == '\n') return j
            j++
        }
        return str.length - 1
    }
}