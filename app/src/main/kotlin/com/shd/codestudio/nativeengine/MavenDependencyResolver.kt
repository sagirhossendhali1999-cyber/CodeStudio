package com.shd.codestudio.nativeengine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

data class MavenResolutionResult(
    val libraries: List<ResolvedLibrary>,
    val allJars: List<File>,
    val warnings: List<String> = emptyList()
)

private data class PomMetadata(
    val packaging: String = "jar",
    val parent: MavenCoordinate? = null,
    val properties: Map<String, String> = emptyMap(),
    val managedVersions: Map<String, String> = emptyMap(),
    val bomImports: List<MavenCoordinate> = emptyList(),
    val dependencies: List<MavenCoordinate> = emptyList()
)

private data class ModuleVariantMeta(
    val redirectTo: MavenCoordinate? = null,
    val fileNames: List<String> = emptyList(),
    val dependencies: List<MavenCoordinate> = emptyList(),
    val constraints: Map<String, String> = emptyMap()
)

object MavenDependencyResolver {

    private const val MAX_TRANSITIVE_ARTIFACTS = 260
    private const val MISSING_MARKER_TTL_MS = 30L * 60L * 1000L

    fun getCacheRoot(context: Context): File =
        File(context.filesDir, "CodeStudioEngine/maven-cache").apply { if (!exists()) mkdirs() }

    private fun getRepoCacheDir(context: Context): File =
        File(getCacheRoot(context), "repo").apply { if (!exists()) mkdirs() }

    private fun getUnpackedCacheDir(context: Context): File =
        File(getCacheRoot(context), "unpacked").apply { if (!exists()) mkdirs() }

    private fun getGraphsCacheDir(context: Context): File =
        File(getCacheRoot(context), "graphs").apply { if (!exists()) mkdirs() }

    suspend fun resolveAll(
        context: Context,
        repositories: List<String>,
        directDependencies: List<MavenCoordinate>,
        bomCoordinates: List<MavenCoordinate>,
        bypassGraphCache: Boolean = false,
        isCancelled: () -> Boolean = { false },
        onLog: (String) -> Unit
    ): MavenResolutionResult = withContext(Dispatchers.IO) {
        if (directDependencies.isEmpty() && bomCoordinates.isEmpty()) {
            return@withContext MavenResolutionResult(emptyList(), emptyList(), emptyList())
        }

        val graphKey = computeGraphCacheKey(repositories, directDependencies, bomCoordinates)
        val graphFile = File(getGraphsCacheDir(context), "$graphKey.json")

        if (!bypassGraphCache && graphFile.exists()) {
            val cached = tryLoadGraphCache(graphFile)
            if (cached != null) {
                onLog("  Dependency graph cache hit (${cached.libraries.size} artifact(s) in <10ms)")
                return@withContext cached
            }
        }

        val repoCache = getRepoCacheDir(context)
        val unpackedCache = getUnpackedCacheDir(context)
        val warnings = mutableListOf<String>()

        // ১. BOM (Bill of Materials) থেকে ভার্সন ম্যাপ লোড করা
        val managedVersions = ConcurrentHashMap<String, String>()
        val loadedBoms = HashSet<String>()

        fun loadBomRecursive(bom: MavenCoordinate, depth: Int = 0) {
            if (depth > 3 || isCancelled() || bom.version.isBlank()) return
            if (!loadedBoms.add(bom.notation)) return
            if (depth == 0) onLog("  Resolving BOM: ${bom.notation}")
            val pom = fetchAndParsePomWithParent(repoCache, repositories, bom)
            if (pom != null) {
                pom.bomImports.forEach { nested -> loadBomRecursive(nested, depth + 1) }
                managedVersions.putAll(pom.managedVersions)
            } else if (depth == 0) {
                warnings.add("Could not fetch BOM: ${bom.notation}")
            }
        }

        for (bom in bomCoordinates) {
            loadBomRecursive(bom, 0)
        }

        // ২. Breadth-First ট্রানজিটিভ গ্রাফ রিজল্ভ (Version Conflict -> Highest Version Wins)
        val chosenVersions = LinkedHashMap<String, String>()
        val artifactFilesMap = LinkedHashMap<String, Pair<MavenCoordinate, List<String>>>()
        val queue = ArrayDeque<MavenCoordinate>()
        val processedQueueKeys = HashSet<String>()

        fun enqueue(raw: MavenCoordinate) {
            val resolvedVer = raw.version.ifBlank { managedVersions[raw.groupArtifactKey] ?: "" }
            if (resolvedVer.isBlank()) {
                warnings.add("No version found for ${raw.groupArtifactKey}")
                return
            }
            val clean = raw.copy(version = resolvedVer)
            val existingVer = chosenVersions[clean.groupArtifactKey]
            if (existingVer == null || compareVersions(clean.version, existingVer) > 0) {
                chosenVersions[clean.groupArtifactKey] = clean.version
                val qKey = clean.notation
                if (processedQueueKeys.add(qKey)) {
                    queue.addLast(clean)
                }
            }
        }

        directDependencies.forEach { enqueue(it) }

        while (queue.isNotEmpty() && artifactFilesMap.size < MAX_TRANSITIVE_ARTIFACTS) {
            if (isCancelled()) break
            val item = queue.removeFirst()
            if (chosenVersions[item.groupArtifactKey] != item.version) continue

            val moduleMeta = fetchModuleMetadata(repoCache, repositories, item)
            if (moduleMeta != null) {
                moduleMeta.constraints.forEach { (k, v) -> managedVersions.putIfAbsent(k, v) }
                if (moduleMeta.redirectTo != null) {
                    enqueue(moduleMeta.redirectTo)
                    continue
                }
                artifactFilesMap[item.groupArtifactKey] = item to moduleMeta.fileNames
                moduleMeta.dependencies.forEach { dep -> enqueue(dep) }
                continue
            }

            // .module না থাকলে .pom পার্স করা
            val pom = fetchAndParsePomWithParent(repoCache, repositories, item)
            if (pom != null) {
                pom.bomImports.forEach { imp -> loadBomRecursive(imp, 1) }
                pom.managedVersions.forEach { (k, v) -> managedVersions.putIfAbsent(k, v) }
                val ext = when {
                    pom.packaging.equals("aar", true) -> "aar"
                    pom.packaging.equals("pom", true) -> ""
                    else -> "jar"
                }
                val fileNames = if (ext.isNotEmpty()) listOf("${item.artifact}-${item.version}.$ext") else emptyList()
                artifactFilesMap[item.groupArtifactKey] = item.copy(packaging = ext.ifEmpty { "pom" }) to fileNames
                pom.dependencies.forEach { dep -> enqueue(dep) }
            } else {
                warnings.add("Metadata not found for ${item.notation}")
            }
        }

        // Kotlin 1.8+ থাকলে পুরোনো redundant kotlin-stdlib-jdk7 / jdk8 ফিল্টার করা
        val stdlibVer = chosenVersions["org.jetbrains.kotlin:kotlin-stdlib"]
        val hasModernStdlib = stdlibVer != null && compareVersions(stdlibVer, "1.8.0") >= 0

        // ৩. প্যারালাল আর্টিফ্যাক্ট (.aar / .jar) ডাউনলোড ও আনপ্যাকিং
        val finalTargets = artifactFilesMap.values.filter { (coord, files) ->
            val keep = chosenVersions[coord.groupArtifactKey] == coord.version && files.isNotEmpty()
            val redundantJdkShim = hasModernStdlib &&
                coord.group == "org.jetbrains.kotlin" &&
                (coord.artifact == "kotlin-stdlib-jdk7" || coord.artifact == "kotlin-stdlib-jdk8") &&
                compareVersions(coord.version, "1.8.0") < 0
            keep && !redundantJdkShim
        }

        if (finalTargets.isNotEmpty()) {
            onLog("  Syncing ${finalTargets.size} resolved artifact(s)...")
        }
        val semaphore = Semaphore(8)
        val resolvedLibs = coroutineScope {
            finalTargets.map { (coord, fileNames) ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        if (isCancelled()) return@withPermit null
                        downloadAndUnpackArtifact(repoCache, unpackedCache, repositories, coord, fileNames, onLog)
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val allJars = resolvedLibs.flatMap { it.jarFiles }.distinctBy { it.absolutePath }
        val result = MavenResolutionResult(
            libraries = resolvedLibs,
            allJars = allJars,
            warnings = warnings
        )

        if (!isCancelled() && resolvedLibs.isNotEmpty()) {
            saveGraphCache(graphFile, result)
        }
        result
    }

    private fun computeGraphCacheKey(
        repositories: List<String>,
        deps: List<MavenCoordinate>,
        boms: List<MavenCoordinate>
    ): String {
        val raw = buildString {
            append("v2|")
            append(repositories.joinToString(","))
            append("|")
            append(deps.sortedBy { it.notation }.joinToString(",") { it.notation })
            append("|")
            append(boms.sortedBy { it.notation }.joinToString(",") { it.notation })
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) }
    }

    private fun saveGraphCache(graphFile: File, result: MavenResolutionResult) {
        try {
            graphFile.parentFile?.mkdirs()
            val libArr = JSONArray()
            for (lib in result.libraries) {
                libArr.put(JSONObject().apply {
                    put("group", lib.coordinate.group)
                    put("artifact", lib.coordinate.artifact)
                    put("version", lib.coordinate.version)
                    put("packaging", lib.coordinate.packaging)
                    put("isAar", lib.isAar)
                    put("jars", JSONArray(lib.jarFiles.map { it.absolutePath }))
                    put("resDir", lib.resDir?.absolutePath ?: "")
                    put("manifestFile", lib.manifestFile?.absolutePath ?: "")
                    put("rTxtFile", lib.rTxtFile?.absolutePath ?: "")
                    put("jniDir", lib.jniDir?.absolutePath ?: "")
                    put("assetsDir", lib.assetsDir?.absolutePath ?: "")
                    put("proguardFile", lib.proguardFile?.absolutePath ?: "")
                })
            }
            val warnArr = JSONArray(result.warnings)
            val root = JSONObject().apply {
                put("libraries", libArr)
                put("warnings", warnArr)
            }
            graphFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (_: Exception) { }
    }

    private fun tryLoadGraphCache(graphFile: File): MavenResolutionResult? {
        return try {
            val root = JSONObject(graphFile.readText(Charsets.UTF_8))
            val libArr = root.optJSONArray("libraries") ?: return null
            val libs = ArrayList<ResolvedLibrary>(libArr.length())
            for (i in 0 until libArr.length()) {
                val o = libArr.getJSONObject(i)
                val coord = MavenCoordinate(
                    group = o.getString("group"),
                    artifact = o.getString("artifact"),
                    version = o.getString("version"),
                    packaging = o.optString("packaging", "jar")
                )
                val jarsArr = o.optJSONArray("jars")
                val jars = ArrayList<File>()
                if (jarsArr != null) {
                    for (j in 0 until jarsArr.length()) {
                        val jf = File(jarsArr.getString(j))
                        if (!jf.exists() || jf.length() <= 0L) return null
                        jars.add(jf)
                    }
                }
                fun optExisting(key: String, dir: Boolean): File? {
                    val p = o.optString(key, "")
                    if (p.isEmpty()) return null
                    val f = File(p)
                    val ok = if (dir) f.exists() && f.isDirectory else f.exists() && f.isFile
                    if (!ok) throw IllegalStateException("Missing cached file $p")
                    return f
                }

                libs.add(
                    ResolvedLibrary(
                        coordinate = coord,
                        jarFiles = jars,
                        resDir = optExisting("resDir", true),
                        manifestFile = optExisting("manifestFile", false),
                        rTxtFile = optExisting("rTxtFile", false),
                        jniDir = optExisting("jniDir", true),
                        assetsDir = optExisting("assetsDir", true),
                        proguardFile = optExisting("proguardFile", false),
                        isAar = o.optBoolean("isAar", false)
                    )
                )
            }
            val warnArr = root.optJSONArray("warnings")
            val warns = mutableListOf<String>()
            if (warnArr != null) {
                for (i in 0 until warnArr.length()) warns.add(warnArr.optString(i))
            }
            val allJars = libs.flatMap { it.jarFiles }.distinctBy { it.absolutePath }
            MavenResolutionResult(libs, allJars, warns)
        } catch (_: Exception) {
            null
        }
    }

    // ================= Gradle .module (JSON) পার্সার =================
    private fun fetchModuleMetadata(
        repoCache: File,
        repositories: List<String>,
        coord: MavenCoordinate
    ): ModuleVariantMeta? {
        val fileName = "${coord.artifact}-${coord.version}.module"
        val file = ensureCachedTextFile(repoCache, repositories, coord, fileName) ?: return null
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val variants = root.optJSONArray("variants") ?: return null

            var bestVariant: JSONObject? = null
            var bestScore = -1

            for (i in 0 until variants.length()) {
                val v = variants.getJSONObject(i)
                val vName = v.optString("name", "").lowercase()
                if (vName.contains("sources") || vName.contains("javadoc") || vName.contains("metadata") || vName.contains("test")) {
                    continue
                }
                val attrs = v.optJSONObject("attributes")
                val category = attrs?.optString("org.gradle.category", "") ?: ""
                if (category == "documentation" || category == "verification") continue

                val platformType = attrs?.optString("org.jetbrains.kotlin.platform.type", "") ?: ""
                val usage = attrs?.optString("org.gradle.usage", "") ?: ""
                val buildType = attrs?.optString("com.android.build.api.attributes.BuildTypeAttr", "") ?: ""

                var score = 0
                if (platformType == "androidJvm") score += 100
                else if (platformType == "jvm") score += 60
                else if (platformType.isEmpty()) score += 40
                else if (platformType == "common" || platformType == "native" || platformType == "js" || platformType == "wasm") continue

                if (usage == "java-runtime") score += 30
                else if (usage == "java-api") score += 20

                if (buildType == "release") score += 15
                if (vName.contains("androidruntime") || vName.contains("releaseruntime")) score += 25
                else if (vName.contains("runtime")) score += 10

                if (score > bestScore) {
                    bestScore = score
                    bestVariant = v
                }
            }

            val chosen = bestVariant ?: return null

            val availableAt = chosen.optJSONObject("available-at")
            if (availableAt != null) {
                val rGroup = availableAt.optString("group", coord.group)
                val rMod = availableAt.optString("module", coord.artifact)
                val rVer = availableAt.optString("version", coord.version)
                return ModuleVariantMeta(redirectTo = MavenCoordinate(rGroup, rMod, rVer))
            }

            val filesArr = chosen.optJSONArray("files")
            val fileNames = mutableListOf<String>()
            if (filesArr != null) {
                for (i in 0 until filesArr.length()) {
                    val fName = filesArr.getJSONObject(i).optString("name", "")
                    if (fName.endsWith(".aar") || fName.endsWith(".jar")) {
                        fileNames.add(fName)
                    }
                }
            }

            val depsArr = chosen.optJSONArray("dependencies")
            val deps = mutableListOf<MavenCoordinate>()
            if (depsArr != null) {
                for (i in 0 until depsArr.length()) {
                    val d = depsArr.getJSONObject(i)
                    val dGroup = d.optString("group", "")
                    val dMod = d.optString("module", "")
                    val verObj = d.optJSONObject("version")
                    val dVer = verObj?.optString("requires", "")?.ifBlank {
                        verObj.optString("strictly", "").ifBlank {
                            verObj.optString("prefers", "")
                        }
                    } ?: ""
                    if (dGroup.isNotBlank() && dMod.isNotBlank()) {
                        deps.add(MavenCoordinate(dGroup, dMod, dVer))
                    }
                }
            }

            val constraintsArr = chosen.optJSONArray("dependencyConstraints")
            val constraints = HashMap<String, String>()
            if (constraintsArr != null) {
                for (i in 0 until constraintsArr.length()) {
                    val c = constraintsArr.getJSONObject(i)
                    val cGroup = c.optString("group", "")
                    val cMod = c.optString("module", "")
                    val verObj = c.optJSONObject("version")
                    val cVer = verObj?.optString("requires", "")?.ifBlank {
                        verObj.optString("strictly", "").ifBlank {
                            verObj.optString("prefers", "")
                        }
                    } ?: ""
                    if (cGroup.isNotBlank() && cMod.isNotBlank() && cVer.isNotBlank()) {
                        constraints["$cGroup:$cMod"] = cVer
                    }
                }
            }

            ModuleVariantMeta(fileNames = fileNames, dependencies = deps, constraints = constraints)
        } catch (_: Exception) {
            null
        }
    }

    // ================= Maven .pom XML পার্সার =================
    private fun fetchAndParsePomWithParent(
        repoCache: File,
        repositories: List<String>,
        coord: MavenCoordinate,
        depth: Int = 0
    ): PomMetadata? {
        if (depth > 4) return null
        val pomFile = ensureCachedTextFile(repoCache, repositories, coord, "${coord.artifact}-${coord.version}.pom")
            ?: return null

        val rawPom = parsePomXml(pomFile.readText(Charsets.UTF_8)) ?: return null

        val props = HashMap<String, String>()
        val mergedManaged = HashMap<String, String>()
        val mergedBomImports = mutableListOf<MavenCoordinate>()

        rawPom.parent?.let { parentCoord ->
            props["project.parent.groupId"] = parentCoord.group
            props["project.parent.artifactId"] = parentCoord.artifact
            props["project.parent.version"] = parentCoord.version
            props["parent.version"] = parentCoord.version
            val parentPom = fetchAndParsePomWithParent(repoCache, repositories, parentCoord, depth + 1)
            if (parentPom != null) {
                props.putAll(parentPom.properties)
                mergedManaged.putAll(parentPom.managedVersions)
                mergedBomImports.addAll(parentPom.bomImports)
            }
        }

        props["project.groupId"] = coord.group
        props["project.artifactId"] = coord.artifact
        props["project.version"] = coord.version
        props["pom.version"] = coord.version
        props.putAll(rawPom.properties)

        fun resolveProp(raw: String): String {
            var cur = raw
            repeat(3) {
                val next = Regex("""\$\{([^}]+)\}""").replace(cur) { m ->
                    props[m.groupValues[1]] ?: m.value
                }
                if (next == cur) return@repeat
                cur = next
            }
            return cur.trim()
        }

        rawPom.bomImports.forEach { imp ->
            val g = resolveProp(imp.group)
            val a = resolveProp(imp.artifact)
            val v = resolveProp(imp.version)
            if (g.isNotBlank() && a.isNotBlank() && v.isNotBlank() && !v.startsWith("\${")) {
                mergedBomImports.add(MavenCoordinate(g, a, v, "pom"))
            }
        }

        rawPom.managedVersions.forEach { (k, v) ->
            val rk = resolveProp(k)
            val rv = resolveProp(v)
            if (!rv.startsWith("\${")) mergedManaged[rk] = rv
        }

        val resolvedDeps = rawPom.dependencies.mapNotNull { dep ->
            val g = resolveProp(dep.group)
            val a = resolveProp(dep.artifact)
            val vRaw = resolveProp(dep.version)
            val v = if (vRaw.isBlank() || vRaw.startsWith("\${")) {
                mergedManaged["$g:$a"] ?: ""
            } else {
                vRaw
            }
            if (g.isBlank() || a.isBlank()) null else MavenCoordinate(g, a, v, dep.packaging)
        }

        return PomMetadata(
            packaging = rawPom.packaging,
            parent = rawPom.parent,
            properties = props,
            managedVersions = mergedManaged,
            bomImports = mergedBomImports.distinctBy { it.notation },
            dependencies = resolvedDeps
        )
    }

    private fun parsePomXml(xml: String): PomMetadata? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var packaging = "jar"
            var parentGroup = ""; var parentArtifact = ""; var parentVersion = ""
            val props = HashMap<String, String>()
            val managed = HashMap<String, String>()
            val bomImports = mutableListOf<MavenCoordinate>()
            val deps = mutableListOf<MavenCoordinate>()

            var inParent = false
            var inProperties = false
            var inDepMgmt = false
            var inDeps = false
            var inDependency = false
            var inExclusions = false

            var dGroup = ""; var dArtifact = ""; var dVersion = ""; var dScope = ""; var dType = "jar"; var dOptional = false
            var currentTag = ""

            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when (currentTag) {
                            "parent" -> inParent = true
                            "properties" -> inProperties = true
                            "dependencyManagement" -> inDepMgmt = true
                            "dependencies" -> if (!inExclusions) inDeps = true
                            "exclusions" -> inExclusions = true
                            "dependency" -> if (inDeps && !inExclusions) {
                                inDependency = true
                                dGroup = ""; dArtifact = ""; dVersion = ""; dScope = ""; dType = "jar"; dOptional = false
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val txt = parser.text?.trim() ?: ""
                        if (txt.isNotEmpty()) {
                            when {
                                inParent -> when (currentTag) {
                                    "groupId" -> parentGroup = txt
                                    "artifactId" -> parentArtifact = txt
                                    "version" -> parentVersion = txt
                                }
                                inProperties && currentTag != "properties" -> {
                                    props[currentTag] = txt
                                }
                                inDependency && !inExclusions -> when (currentTag) {
                                    "groupId" -> dGroup = txt
                                    "artifactId" -> dArtifact = txt
                                    "version" -> dVersion = txt
                                    "scope" -> dScope = txt
                                    "type" -> dType = txt
                                    "optional" -> dOptional = txt.equals("true", true)
                                }
                                !inDeps && !inDepMgmt && currentTag == "packaging" -> packaging = txt
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "parent" -> inParent = false
                            "properties" -> inProperties = false
                            "dependencyManagement" -> inDepMgmt = false
                            "dependencies" -> if (!inExclusions) inDeps = false
                            "exclusions" -> inExclusions = false
                            "dependency" -> if (inDependency && !inExclusions) {
                                inDependency = false
                                if (inDepMgmt) {
                                    if (dGroup.isNotBlank() && dArtifact.isNotBlank() && dVersion.isNotBlank()) {
                                        if (dScope == "import" && dType == "pom") {
                                            bomImports.add(MavenCoordinate(dGroup, dArtifact, dVersion, "pom"))
                                        } else {
                                            managed["$dGroup:$dArtifact"] = dVersion
                                        }
                                    }
                                } else {
                                    val includeScope = dScope.isEmpty() || dScope == "compile" || dScope == "runtime"
                                    val validType = dType != "test-jar"
                                    if (includeScope && validType && !dOptional && dGroup.isNotBlank() && dArtifact.isNotBlank()) {
                                        deps.add(MavenCoordinate(dGroup, dArtifact, dVersion, dType))
                                    }
                                }
                            }
                        }
                        currentTag = ""
                    }
                }
                ev = parser.next()
            }

            val parentCoord = if (parentGroup.isNotBlank() && parentArtifact.isNotBlank() && parentVersion.isNotBlank()) {
                MavenCoordinate(parentGroup, parentArtifact, parentVersion, "pom")
            } else null

            PomMetadata(
                packaging = packaging,
                parent = parentCoord,
                properties = props,
                managedVersions = managed,
                bomImports = bomImports,
                dependencies = deps
            )
        } catch (_: Exception) {
            null
        }
    }

    // ================= ডাউনলোড ও .aar আনপ্যাকিং =================
    private fun downloadAndUnpackArtifact(
        repoCache: File,
        unpackedCache: File,
        repositories: List<String>,
        coord: MavenCoordinate,
        fileNames: List<String>,
        onLog: (String) -> Unit
    ): ResolvedLibrary? {
        val jarList = mutableListOf<File>()
        var resDir: File? = null
        var manifestFile: File? = null
        var rTxtFile: File? = null
        var jniDir: File? = null
        var assetsDir: File? = null
        var proguardFile: File? = null
        var isAar = false

        for (fName in fileNames) {
            val cachedFile = ensureCachedBinaryFile(repoCache, repositories, coord, fName) ?: continue
            if (fName.endsWith(".jar", ignoreCase = true)) {
                jarList.add(cachedFile)
            } else if (fName.endsWith(".aar", ignoreCase = true)) {
                isAar = true
                val safeFolder = "${coord.group}_${coord.artifact}_${coord.version}".replace(Regex("[^A-Za-z0-9._-]"), "_")
                val unpackDir = File(unpackedCache, safeFolder)
                val marker = File(unpackDir, ".unpacked_ok")
                if (!marker.exists()) {
                    onLog("    Unpacking ${coord.artifact}-${coord.version}.aar")
                    if (unpackDir.exists()) unpackDir.deleteRecursively()
                    unpackDir.mkdirs()
                    unzipFile(cachedFile, unpackDir)
                    marker.writeText("ok")
                }

                val classesJar = File(unpackDir, "classes.jar")
                if (classesJar.exists() && classesJar.length() > 0L) {
                    jarList.add(classesJar)
                }
                val libsDir = File(unpackDir, "libs")
                if (libsDir.exists() && libsDir.isDirectory) {
                    libsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".jar", true) }?.forEach {
                        jarList.add(it)
                    }
                }
                resDir = File(unpackDir, "res").takeIf { it.exists() && it.isDirectory }
                manifestFile = File(unpackDir, "AndroidManifest.xml").takeIf { it.exists() && it.isFile }
                rTxtFile = File(unpackDir, "R.txt").takeIf { it.exists() && it.isFile }
                jniDir = File(unpackDir, "jni").takeIf { it.exists() && it.isDirectory }
                assetsDir = File(unpackDir, "assets").takeIf { it.exists() && it.isDirectory }
                proguardFile = File(unpackDir, "proguard.txt").takeIf { it.exists() && it.isFile }
            }
        }

        if (jarList.isEmpty() && resDir == null && manifestFile == null) return null

        return ResolvedLibrary(
            coordinate = coord,
            jarFiles = jarList.distinctBy { it.absolutePath },
            resDir = resDir,
            manifestFile = manifestFile,
            rTxtFile = rTxtFile,
            jniDir = jniDir,
            assetsDir = assetsDir,
            proguardFile = proguardFile,
            isAar = isAar
        )
    }

    private fun ensureCachedTextFile(
        repoCache: File,
        repositories: List<String>,
        coord: MavenCoordinate,
        fileName: String
    ): File? = ensureCachedBinaryFile(repoCache, repositories, coord, fileName)

    private fun ensureCachedBinaryFile(
        repoCache: File,
        repositories: List<String>,
        coord: MavenCoordinate,
        fileName: String
    ): File? {
        val relPath = "${coord.group.replace('.', '/')}/${coord.artifact}/${coord.version}/$fileName"
        val destFile = File(repoCache, relPath)
        if (destFile.exists() && destFile.length() > 0L) {
            return destFile
        }
        val missingMarker = File(destFile.parentFile, "$fileName.missing")
        if (missingMarker.exists()) {
            val age = System.currentTimeMillis() - missingMarker.lastModified()
            if (age in 0L until MISSING_MARKER_TTL_MS) {
                return null
            } else {
                missingMarker.delete()
            }
        }

        destFile.parentFile?.mkdirs()
        var sawNetworkError = false
        for (repo in repositories) {
            val base = if (repo.endsWith("/")) repo else "$repo/"
            val fullUrl = "$base$relPath"
            val (ok, isDefinitiveNotFound) = downloadUrlToFile(fullUrl, destFile)
            if (ok) {
                return destFile
            }
            if (!isDefinitiveNotFound) {
                sawNetworkError = true
            }
        }
        if (!sawNetworkError) {
            try {
                missingMarker.writeText("404")
            } catch (_: Exception) { }
        }
        return null
    }

    private fun downloadUrlToFile(urlStr: String, destFile: File): Pair<Boolean, Boolean> {
        val tmp = File(destFile.parentFile, "${destFile.name}.tmp")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CodeStudio-Android/2.0")
                connectTimeout = 15_000
                readTimeout = 45_000
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val notFound = code == 404 || code == 410
                return false to notFound
            }

            val buf = ByteArray(65_536)
            BufferedInputStream(conn.inputStream, 65_536).use { bis ->
                BufferedOutputStream(FileOutputStream(tmp), 65_536).use { bos ->
                    var r: Int
                    while (bis.read(buf).also { r = it } > 0) {
                        bos.write(buf, 0, r)
                    }
                }
            }
            if (destFile.exists()) destFile.delete()
            val moved = tmp.renameTo(destFile) || run {
                tmp.copyTo(destFile, overwrite = true)
                tmp.delete()
                true
            }
            moved to false
        } catch (_: Exception) {
            tmp.delete()
            false to false
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) { }
        }
    }

    private fun unzipFile(zipFile: File, destDir: File) {
        val canonicalRoot = destDir.canonicalPath + File.separator
        val buffer = ByteArray(65_536)
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile), 65_536)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(destDir, entry.name.replace("\\", "/"))
                if (out.canonicalPath.startsWith(canonicalRoot) || out.canonicalPath == destDir.canonicalPath) {
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(out), 65_536).use { bos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                bos.write(buffer, 0, len)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        if (v1 == v2) return 0
        val p1 = v1.split(".", "-")
        val p2 = v2.split(".", "-")
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val s1 = p1.getOrNull(i) ?: "0"
            val s2 = p2.getOrNull(i) ?: "0"
            val n1 = s1.toIntOrNull()
            val n2 = s2.toIntOrNull()
            val cmp = if (n1 != null && n2 != null) {
                n1.compareTo(n2)
            } else {
                s1.compareTo(s2)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }
}