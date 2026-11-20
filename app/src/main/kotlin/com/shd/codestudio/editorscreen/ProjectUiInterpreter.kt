package com.shd.codestudio.editorscreen

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shd.codestudio.NeonLime
import com.shd.codestudio.TextSecondary
import com.shd.codestudio.homescreen.getProjectDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader

enum class UiElementType {
    COLUMN,
    ROW,
    BOX,
    SURFACE,
    CARD,
    SCAFFOLD,
    DIALOG,
    TEXT,
    BUTTON,
    OUTLINED_BUTTON,
    TEXT_BUTTON,
    ICON_BUTTON,
    TEXT_FIELD,
    SPACER,
    DIVIDER,
    ICON,
    SWITCH,
    CHECKBOX,
    PROGRESS,
    IMAGE_MOCK,
    UNKNOWN
}

data class ComposableParam(
    val name: String,
    val defaultExpr: String? = null
)

data class ComposableDef(
    val name: String,
    val params: List<ComposableParam>,
    val body: String
)

data class ProjectUiIndex(
    val projectName: String,
    val mainEntryBody: String,
    val mainEntryLabel: String,
    val composables: Map<String, ComposableDef>,
    val colorConstants: Map<String, Color>,
    val stringResources: Map<String, String>,
    val xmlLayouts: Map<String, String>,
    val screenOptions: List<String>
)

data class UiModifierSpec(
    val fillMaxSize: Boolean = false,
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val weight: Float? = null,
    val widthDp: Float? = null,
    val heightDp: Float? = null,
    val sizeDp: Float? = null,
    val paddingAllDp: Float = 0f,
    val paddingHorizDp: Float = 0f,
    val paddingVertDp: Float = 0f,
    val paddingStartDp: Float = 0f,
    val paddingEndDp: Float = 0f,
    val paddingTopDp: Float = 0f,
    val paddingBottomDp: Float = 0f,
    val bgColor: Color? = null,
    val bgGradient: List<Color>? = null,
    val bgCornerRadiusDp: Float? = null,
    val bgIsCircle: Boolean = false,
    val borderColor: Color? = null,
    val borderWidthDp: Float = 0f,
    val cornerRadiusDp: Float? = null,
    val topStartCornerDp: Float? = null,
    val topEndCornerDp: Float? = null,
    val bottomStartCornerDp: Float? = null,
    val bottomEndCornerDp: Float? = null,
    val isCircle: Boolean = false,
    val shadowDp: Float = 0f,
    val clickableBlock: String? = null,
    val verticalScroll: Boolean = false,
    val horizontalScroll: Boolean = false
)

data class UiNode(
    val type: UiElementType,
    val text: String = "",
    val placeholder: String = "",
    val textColor: Color? = null,
    val textSize: Float = 13f,
    val isBold: Boolean = false,
    val backgroundColor: Color? = null,
    val paddingDp: Int = 0,
    val heightDp: Int? = null,
    val widthDp: Int? = null,
    val isFillMaxWidth: Boolean = false,
    val isFillMaxSize: Boolean = false,
    val alignment: String = "start",
    val verticalSpacingDp: Int = 0,
    val horizontalSpacingDp: Int = 0,
    val horizontalCenterArrange: Boolean = false,
    val horizontalSpaceBetween: Boolean = false,
    val maxLines: Int = Int.MAX_VALUE,
    val isMonospace: Boolean = false,
    val iconName: String = "",
    val stateKey: String = "",
    val boolState: Boolean = false,
    val actionBlock: String = "",
    val localBindings: Map<String, String> = emptyMap(),
    val mod: UiModifierSpec = UiModifierSpec(),
    val children: MutableList<UiNode> = mutableListOf(),
    val overlayDialogs: MutableList<UiNode> = mutableListOf(),
    val backActions: MutableList<String> = mutableListOf(),
    val projectIndex: ProjectUiIndex? = null
)

object ProjectUiInterpreter {

    private val BUILTIN_COLORS = mapOf(
        "Color.White" to Color.White,
        "Color.Black" to Color.Black,
        "Color.Gray" to Color.Gray,
        "Color.LightGray" to Color.LightGray,
        "Color.DarkGray" to Color.DarkGray,
        "Color.Red" to Color(0xFFFF5252),
        "Color.Green" to Color(0xFF4CAF50),
        "Color.Blue" to Color(0xFF2196F3),
        "Color.Yellow" to Color(0xFFFFEB3B),
        "Color.Cyan" to Color(0xFF00E5FF),
        "Color.Magenta" to Color(0xFFE040FB),
        "Color.Transparent" to Color.Transparent
    )

    suspend fun parseProjectUi(context: Context, projectName: String): UiNode = withContext(Dispatchers.IO) {
        val rootDir = getProjectDirectory(context, projectName)
        if (!rootDir.exists()) {
            return@withContext UiNode(
                type = UiElementType.COLUMN,
                isFillMaxSize = true,
                children = mutableListOf(
                    UiNode(type = UiElementType.TEXT, text = "Project directory not found", textColor = Color(0xFFFF5252))
                )
            )
        }

        val index = indexProject(rootDir, projectName)
        val initialState = HashMap<String, Any?>()
        val initialScreen = index.screenOptions.firstOrNull() ?: "Welcome"
        buildTreeForScreen(index, initialScreen, initialState)
    }

    private fun indexProject(rootDir: File, projectName: String): ProjectUiIndex {
        val composables = LinkedHashMap<String, ComposableDef>()
        val colorConstants = LinkedHashMap<String, Color>()
        val stringResources = LinkedHashMap<String, String>()
        val xmlLayouts = LinkedHashMap<String, String>()

        val resDir = File(rootDir, "app/src/main/res")
        if (resDir.exists()) {
            parseXmlStrings(File(resDir, "values/strings.xml"), stringResources)
            parseXmlColors(File(resDir, "values/colors.xml"), colorConstants)
            File(resDir, "layout").listFiles()
                ?.filter { it.isFile && it.name.endsWith(".xml", true) }
                ?.sortedBy { if (it.name == "activity_main.xml") "" else it.name }
                ?.forEach { f ->
                    runCatching { xmlLayouts["XML: ${f.name}"] = f.readText(Charsets.UTF_8) }
                }
        }

        val ktFiles = mutableListOf<File>()
        listOf("app/src/main/kotlin", "app/src/main/java").forEach { rel ->
            val dir = File(rootDir, rel)
            if (dir.exists()) {
                dir.walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".kt", true) }
                    .forEach { ktFiles.add(it) }
            }
        }

        var mainActivityCode = ""
        for (file in ktFiles) {
            val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: continue
            val clean = stripKotlinComments(raw)
            if (file.name == "MainActivity.kt") {
                mainActivityCode = clean
            }
            extractColorConstants(clean, colorConstants)
            extractComposableFunctions(clean, composables)
        }

        val setContentBody = if (mainActivityCode.isNotEmpty()) {
            extractCallLambda(mainActivityCode, "setContent") ?: ""
        } else ""

        val screenOptions = mutableListOf<String>()
        if (setContentBody.isNotBlank()) {
            screenOptions.add("MainActivity (App)")
        }
        composables.keys
            .filter { name ->
                val lower = name.lowercase()
                (lower.endsWith("screen") || lower.endsWith("page") || lower.contains("preview")) &&
                    !lower.contains("dialog")
            }
            .forEach { screenOptions.add(it) }
        xmlLayouts.keys.forEach { screenOptions.add(it) }
        if (screenOptions.isEmpty()) {
            composables.keys.take(6).forEach { screenOptions.add(it) }
        }
        if (screenOptions.isEmpty()) {
            screenOptions.add("Default Preview")
        }

        return ProjectUiIndex(
            projectName = projectName,
            mainEntryBody = setContentBody,
            mainEntryLabel = screenOptions.first(),
            composables = composables,
            colorConstants = colorConstants,
            stringResources = stringResources,
            xmlLayouts = xmlLayouts,
            screenOptions = screenOptions.distinct()
        )
    }

    fun buildTreeForScreen(
        index: ProjectUiIndex,
        screenName: String,
        runtimeState: MutableMap<String, Any?>
    ): UiNode {
        val overlayDialogs = mutableListOf<UiNode>()
        val backActions = mutableListOf<String>()
        val rootChildren = mutableListOf<UiNode>()
        var rootBg: Color? = Color(0xFF0A0A0A)

        when {
            screenName == "MainActivity (App)" && index.mainEntryBody.isNotBlank() -> {
                val env = EvalEnv(index, runtimeState, HashMap(), overlayDialogs, backActions, depth = 0)
                evalBlockStatements(index.mainEntryBody, env, rootChildren)
            }
            index.xmlLayouts.containsKey(screenName) -> {
                val xml = index.xmlLayouts[screenName] ?: ""
                val xmlRoot = parseXmlLayout(xml, index)
                if (xmlRoot != null) {
                    rootChildren.add(xmlRoot)
                    rootBg = xmlRoot.backgroundColor ?: Color(0xFF14151B)
                }
            }
            index.composables.containsKey(screenName) -> {
                val def = index.composables[screenName]!!
                val env = EvalEnv(index, runtimeState, HashMap(), overlayDialogs, backActions, depth = 0)
                def.params.forEach { p ->
                    if (p.defaultExpr != null) {
                        val defClean = p.defaultExpr.trim()
                        env.locals[p.name] = if (defClean == "null") null else defClean
                    }
                }
                evalBlockStatements(def.body, env, rootChildren)
            }
            else -> {
                rootChildren.add(
                    UiNode(
                        type = UiElementType.COLUMN,
                        isFillMaxSize = true,
                        alignment = "center",
                        paddingDp = 20,
                        children = mutableListOf(
                            UiNode(type = UiElementType.TEXT, text = index.projectName, textSize = 20f, isBold = true, textColor = Color.White),
                            UiNode(type = UiElementType.SPACER, heightDp = 8),
                            UiNode(type = UiElementType.TEXT, text = "No Compose setContent or XML layout found yet.", textSize = 12f, textColor = Color.LightGray, alignment = "center")
                        )
                    )
                )
            }
        }

        return UiNode(
            type = UiElementType.BOX,
            isFillMaxSize = true,
            backgroundColor = rootBg,
            children = rootChildren,
            overlayDialogs = overlayDialogs,
            backActions = backActions,
            projectIndex = index
        )
    }

    private class EvalEnv(
        val index: ProjectUiIndex,
        val runtimeState: MutableMap<String, Any?>,
        val locals: MutableMap<String, Any?>,
        val overlayDialogs: MutableList<UiNode>,
        val backActions: MutableList<String>,
        val depth: Int,
        val nodeBudget: IntArray = intArrayOf(0)
    ) {
        fun childEnv(extraLocals: Map<String, Any?> = emptyMap()): EvalEnv {
            val merged = HashMap(locals)
            merged.putAll(extraLocals)
            return EvalEnv(index, runtimeState, merged, overlayDialogs, backActions, depth + 1, nodeBudget)
        }
    }

    private fun evalBlockStatements(body: String, env: EvalEnv, out: MutableList<UiNode>): Boolean {
        if (env.depth > 14 || env.nodeBudget[0] > 320) return false
        val statements = splitTopLevelStatements(body)

        for (stmt in statements) {
            if (env.nodeBudget[0] > 320) break
            val trimmed = stmt.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed == "return" || trimmed.startsWith("return@")) {
                return true
            }

            if (trimmed.startsWith("var ") || trimmed.startsWith("val ")) {
                handleVariableDeclaration(trimmed, env)
                continue
            }

            if (trimmed.startsWith("if ") || trimmed.startsWith("if(")) {
                val returned = handleIfStatement(trimmed, env, out)
                if (returned) return true
                continue
            }

            if (trimmed.startsWith("when ") || trimmed.startsWith("when(")) {
                handleWhenStatement(trimmed, env, out)
                continue
            }

            if (trimmed.startsWith("repeat(") || trimmed.contains(".forEach") || trimmed.contains(".chunked(")) {
                handleLoopStatement(trimmed, env, out)
                continue
            }

            parseAndEvalComposableCall(trimmed, env, out)
        }
        return false
    }

    private fun handleVariableDeclaration(stmt: String, env: EvalEnv) {
        val byMatch = Regex("""^(?:var|val)\s+([A-Za-z_][A-Za-z0-9_]*)\s+by\s+remember(?:\([^)]*\))?\s*\{([\s\S]*)\}""").find(stmt)
        if (byMatch != null) {
            val varName = byMatch.groupValues[1]
            val inner = byMatch.groupValues[2].trim()
            if (!env.runtimeState.containsKey(varName)) {
                val initArg = extractStateInitArg(inner)
                env.runtimeState[varName] = evalValueExpr(initArg, env)
            }
            return
        }

        val eqMatch = Regex("""^(?:var|val)\s+([A-Za-z_][A-Za-z0-9_]*)(?:\s*:\s*[^=]+)?\s*=\s*([\s\S]+)$""").find(stmt)
        if (eqMatch != null) {
            val varName = eqMatch.groupValues[1]
            val rhs = eqMatch.groupValues[2].trim()
            if (rhs.startsWith("remember")) {
                val braceContent = extractFirstBracedBody(rhs) ?: ""
                if (braceContent.contains("mutableStateOf") || braceContent.contains("mutableIntStateOf")) {
                    if (!env.runtimeState.containsKey(varName)) {
                        env.runtimeState[varName] = evalValueExpr(extractStateInitArg(braceContent), env)
                    }
                    return
                }
                val simpleInit = evalListOrValue(braceContent, env)
                env.locals[varName] = simpleInit
                return
            }
            env.locals[varName] = evalListOrValue(rhs, env)
        }
    }

    private fun evalListOrValue(rhs: String, env: EvalEnv): Any? {
        val trimmed = rhs.trim()
        if (trimmed.startsWith("listOf(") || trimmed.contains(".sorted") || trimmed.contains(".filter")) {
            return evalListExpr(trimmed, env)
        }
        if (trimmed.contains("loadProjects(") || trimmed.contains("emptyList(")) {
            return emptyList<Any?>()
        }
        return evalValueExpr(trimmed, env)
    }

    private fun extractStateInitArg(inner: String): String {
        val m = Regex("""mutable(?:Int|Float|Long|Double)?StateOf(?:<[^>]+>)?\s*\(([\s\S]*)\)""").find(inner)
        return m?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    private fun handleIfStatement(stmt: String, env: EvalEnv, out: MutableList<UiNode>): Boolean {
        val parenStart = stmt.indexOf('(')
        if (parenStart == -1) return false
        val parenEnd = findMatchingDelim(stmt, parenStart, '(', ')')
        if (parenEnd == -1) return false

        val condExpr = stmt.substring(parenStart + 1, parenEnd).trim()
        val afterCond = stmt.substring(parenEnd + 1).trim()
        val condResult = evalCondition(condExpr, env)

        if (afterCond.startsWith("{")) {
            val blockEnd = findMatchingDelim(afterCond, 0, '{', '}')
            if (blockEnd == -1) return false
            val ifBody = afterCond.substring(1, blockEnd)
            val rest = afterCond.substring(blockEnd + 1).trim()

            if (condResult) {
                return evalBlockStatements(ifBody, env, out)
            } else if (rest.startsWith("else")) {
                val afterElse = rest.removePrefix("else").trim()
                if (afterElse.startsWith("if")) {
                    return handleIfStatement(afterElse, env, out)
                } else if (afterElse.startsWith("{")) {
                    val elseEnd = findMatchingDelim(afterElse, 0, '{', '}')
                    if (elseEnd != -1) {
                        val elseBody = afterElse.substring(1, elseEnd)
                        return evalBlockStatements(elseBody, env, out)
                    }
                }
            }
        }
        return false
    }

    private fun handleWhenStatement(stmt: String, env: EvalEnv, out: MutableList<UiNode>) {
        val parenStart = stmt.indexOf('(')
        val braceStart = stmt.indexOf('{')
        if (braceStart == -1) return
        val subjectExpr = if (parenStart in 0 until braceStart) {
            val pEnd = findMatchingDelim(stmt, parenStart, '(', ')')
            if (pEnd != -1) stmt.substring(parenStart + 1, pEnd).trim() else ""
        } else ""
        val braceEnd = findMatchingDelim(stmt, braceStart, '{', '}')
        if (braceEnd == -1) return

        val subjectVal = if (subjectExpr.isNotEmpty()) evalValueExpr(subjectExpr, env) else null
        val body = stmt.substring(braceStart + 1, braceEnd)
        val branches = splitTopLevelStatements(body)

        for (br in branches) {
            val arrowIdx = findTopLevelArrow(br)
            if (arrowIdx == -1) continue
            val lhs = br.substring(0, arrowIdx).trim()
            val rhs = br.substring(arrowIdx + 2).trim()

            val matches = if (lhs == "else") {
                true
            } else if (subjectExpr.isEmpty()) {
                evalCondition(lhs, env)
            } else {
                val lhsVals = splitTopLevelArgs(lhs).map { evalValueExpr(it, env) }
                lhsVals.any { it.toString() == subjectVal.toString() }
            }

            if (matches) {
                val cleanRhs = if (rhs.startsWith("{") && rhs.endsWith("}")) {
                    rhs.substring(1, rhs.length - 1)
                } else rhs
                evalBlockStatements(cleanRhs, env, out)
                break
            }
        }
    }

    private fun handleLoopStatement(stmt: String, env: EvalEnv, out: MutableList<UiNode>) {
        if (stmt.startsWith("repeat(")) {
            val pEnd = findMatchingDelim(stmt, 6, '(', ')')
            if (pEnd == -1) return
            val count = (evalValueExpr(stmt.substring(7, pEnd), env) as? Number)?.toInt()?.coerceIn(0, 20) ?: 0
            val bStart = stmt.indexOf('{', pEnd)
            val bEnd = if (bStart != -1) findMatchingDelim(stmt, bStart, '{', '}') else -1
            if (bEnd != -1) {
                val body = stmt.substring(bStart + 1, bEnd)
                for (i in 0 until count) {
                    evalBlockStatements(body, env.childEnv(mapOf("it" to i)), out)
                }
            }
            return
        }

        val chunkMatch = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\.chunked\((\d+)\)\.forEach\s*\{([\s\S]*)\}$""").find(stmt)
        if (chunkMatch != null) {
            val listVar = chunkMatch.groupValues[1]
            val chunkSz = chunkMatch.groupValues[2].toIntOrNull()?.coerceAtLeast(1) ?: 4
            val rawBody = chunkMatch.groupValues[3]
            val (params, cleanBody) = splitLambdaParams(rawBody)
            val srcList = evalListExpr(listVar, env)
            srcList.chunked(chunkSz).forEach { chunk ->
                val loopLocals = HashMap<String, Any?>()
                if (params.isNotEmpty()) loopLocals[params[0]] = chunk else loopLocals["it"] = chunk
                evalBlockStatements(cleanBody, env.childEnv(loopLocals), out)
            }
            return
        }

        val forEachIdx = stmt.indexOf(".forEach")
        if (forEachIdx != -1) {
            val targetExpr = stmt.substring(0, forEachIdx).trim()
            val isIndexed = stmt.startsWith(".forEachIndexed", forEachIdx)
            val bStart = stmt.indexOf('{', forEachIdx)
            val bEnd = if (bStart != -1) findMatchingDelim(stmt, bStart, '{', '}') else -1
            if (bEnd == -1) return

            val items = evalListExpr(targetExpr, env)
            val rawBody = stmt.substring(bStart + 1, bEnd).trim()
            val (lambdaParams, cleanBody) = splitLambdaParams(rawBody)

            items.take(25).forEachIndexed { idx, item ->
                val loopLocals = HashMap<String, Any?>()
                if (isIndexed && lambdaParams.size >= 2) {
                    loopLocals[lambdaParams[0]] = idx
                    bindLambdaParam(lambdaParams[1], item, loopLocals)
                } else if (lambdaParams.isNotEmpty()) {
                    bindLambdaParam(lambdaParams[0], item, loopLocals)
                } else {
                    loopLocals["it"] = item
                }
                evalBlockStatements(cleanBody, env.childEnv(loopLocals), out)
            }
        }
    }

    private fun bindLambdaParam(paramSpec: String, item: Any?, map: MutableMap<String, Any?>) {
        val clean = paramSpec.trim()
        if (clean.startsWith("(") && clean.endsWith(")") && item is Pair<*, *>) {
            val parts = clean.substring(1, clean.length - 1).split(",").map { it.trim() }
            if (parts.isNotEmpty()) map[parts[0]] = item.first
            if (parts.size >= 2) map[parts[1]] = item.second
        } else {
            map[clean] = item
        }
    }

    private fun parseAndEvalComposableCall(stmt: String, env: EvalEnv, out: MutableList<UiNode>) {
        val parsed = parseCallSyntax(stmt) ?: return
        val name = parsed.name.substringAfterLast(".")
        val args = parsed.namedArgs
        val lambdaBody = parsed.trailingLambda

        if (env.nodeBudget[0] > 320) return

        when (name) {
            "BackHandler" -> {
                val body = lambdaBody ?: extractFirstBracedBody(args["onBack"] ?: "") ?: ""
                if (body.isNotBlank()) env.backActions.add(resolveBoundActions(body, env))
            }

            "MaterialTheme", "Crossfade", "CompositionLocalProvider", "BoxWithConstraints" -> {
                val rawContent = lambdaBody ?: args["content"]?.let { extractFirstBracedBody(it) ?: it }
                if (rawContent != null) {
                    val trimmedContent = rawContent.trim()
                    val resolved = if (trimmedContent == "content" && env.locals["content"] is String) {
                        env.locals["content"] as String
                    } else {
                        rawContent
                    }
                    evalBlockStatements(resolved, env, out)
                }
            }

            "AnimatedVisibility" -> {
                val visibleExpr = args["visible"] ?: args["_0"] ?: "true"
                if (evalCondition(visibleExpr, env)) {
                    val rawContent = lambdaBody ?: args["content"]?.let { extractFirstBracedBody(it) ?: it }
                    if (rawContent != null) evalBlockStatements(rawContent, env, out)
                }
            }

            "Column", "LazyColumn" -> {
                val mod = parseModifier(args["modifier"], env)
                val vSpace = extractSpacedBy(args["verticalArrangement"])
                val align = if ((args["horizontalAlignment"] ?: "").contains("Center")) "center" else "start"
                val node = UiNode(
                    type = UiElementType.COLUMN,
                    alignment = align,
                    verticalSpacingDp = vSpace,
                    mod = mod.copy(verticalScroll = name == "LazyColumn" || mod.verticalScroll)
                )
                env.nodeBudget[0]++
                val bodyToRun = lambdaBody ?: ""
                if (name == "LazyColumn") {
                    evalLazyColumnItems(bodyToRun, env, node.children)
                } else {
                    evalBlockStatements(bodyToRun, env, node.children)
                }
                out.add(node)
            }

            "Row", "LazyRow" -> {
                val mod = parseModifier(args["modifier"], env)
                val hSpace = extractSpacedBy(args["horizontalArrangement"])
                val arrStr = args["horizontalArrangement"] ?: ""
                val node = UiNode(
                    type = UiElementType.ROW,
                    horizontalSpacingDp = hSpace,
                    horizontalCenterArrange = arrStr.contains("Center"),
                    horizontalSpaceBetween = arrStr.contains("SpaceBetween"),
                    mod = mod.copy(horizontalScroll = name == "LazyRow" || mod.horizontalScroll)
                )
                env.nodeBudget[0]++
                val bodyToRun = lambdaBody ?: ""
                if (name == "LazyRow") {
                    evalLazyColumnItems(bodyToRun, env, node.children)
                } else {
                    evalBlockStatements(bodyToRun, env, node.children)
                }
                out.add(node)
            }

            "Box", "Surface", "Card", "ElevatedCard" -> {
                val mod = parseModifier(args["modifier"], env)
                val bgExpr = args["color"] ?: args["containerColor"]
                val bg = bgExpr?.let { parseColorExpr(it, env) }
                val align = if ((args["contentAlignment"] ?: "").contains("Center")) "center" else "start"
                val elemType = when (name) {
                    "Surface" -> UiElementType.SURFACE
                    "Card", "ElevatedCard" -> UiElementType.CARD
                    else -> UiElementType.BOX
                }
                val node = UiNode(
                    type = elemType,
                    backgroundColor = bg,
                    alignment = align,
                    mod = mod
                )
                env.nodeBudget[0]++
                if (lambdaBody != null) evalBlockStatements(lambdaBody, env, node.children)
                out.add(node)
            }

            "Scaffold" -> {
                val mod = parseModifier(args["modifier"], env)
                val bg = args["containerColor"]?.let { parseColorExpr(it, env) }
                val col = UiNode(type = UiElementType.COLUMN, backgroundColor = bg, mod = mod.copy(fillMaxSize = true))
                env.nodeBudget[0]++
                args["topBar"]?.let { tb ->
                    evalBlockStatements(extractFirstBracedBody(tb) ?: tb, env, col.children)
                }
                val contentBox = UiNode(type = UiElementType.BOX, mod = UiModifierSpec(fillMaxWidth = true, weight = 1f))
                if (lambdaBody != null) evalBlockStatements(lambdaBody, env, contentBox.children)
                col.children.add(contentBox)
                args["bottomBar"]?.let { bb ->
                    evalBlockStatements(extractFirstBracedBody(bb) ?: bb, env, col.children)
                }
                out.add(col)
            }

            "AlertDialog", "Dialog" -> {
                val dlg = UiNode(
                    type = UiElementType.DIALOG,
                    backgroundColor = args["containerColor"]?.let { parseColorExpr(it, env) } ?: Color(0xFF1A1B22)
                )
                args["title"]?.let { t ->
                    evalBlockStatements(extractFirstBracedBody(t) ?: t, env, dlg.children)
                }
                args["text"]?.let { t ->
                    evalBlockStatements(extractFirstBracedBody(t) ?: t, env, dlg.children)
                }
                if (lambdaBody != null) {
                    evalBlockStatements(lambdaBody, env, dlg.children)
                }
                val btnRow = UiNode(type = UiElementType.ROW, horizontalSpacingDp = 8, mod = UiModifierSpec(fillMaxWidth = true))
                args["dismissButton"]?.let { d ->
                    evalBlockStatements(extractFirstBracedBody(d) ?: d, env, btnRow.children)
                }
                args["confirmButton"]?.let { c ->
                    evalBlockStatements(extractFirstBracedBody(c) ?: c, env, btnRow.children)
                }
                if (btnRow.children.isNotEmpty()) dlg.children.add(btnRow)
                env.overlayDialogs.add(dlg)
            }

            "Text" -> {
                val txtExpr = args["text"] ?: args["_0"] ?: "\"\""
                val resolvedTxt = evalStringExpr(txtExpr, env)
                val color = (args["color"] ?: extractStyleProp(args["style"], "color"))?.let { parseColorExpr(it, env) }
                val fSize = (args["fontSize"] ?: extractStyleProp(args["style"], "fontSize"))
                    ?.let { parseDpOrSp(it) } ?: 14f
                val fw = args["fontWeight"] ?: ""
                val bold = fw.contains("Bold") || fw.contains("SemiBold") || fw.contains("Black")
                val align = when {
                    (args["textAlign"] ?: "").contains("Center") -> "center"
                    (args["textAlign"] ?: "").contains("End") -> "end"
                    else -> "start"
                }
                val mono = (args["fontFamily"] ?: "").contains("Monospace")
                val mod = parseModifier(args["modifier"], env)
                env.nodeBudget[0]++
                out.add(
                    UiNode(
                        type = UiElementType.TEXT,
                        text = resolvedTxt,
                        textColor = color,
                        textSize = fSize,
                        isBold = bold,
                        alignment = align,
                        isMonospace = mono,
                        maxLines = args["maxLines"]?.toIntOrNull() ?: Int.MAX_VALUE,
                        mod = mod
                    )
                )
            }

            "Button", "OutlinedButton", "TextButton", "ElevatedButton", "FilledTonalButton" -> {
                val onClickExpr = args["onClick"] ?: ""
                val actionBody = extractFirstBracedBody(onClickExpr) ?: onClickExpr
                val mod = parseModifier(args["modifier"], env)
                val bg = extractButtonContainerColor(args["colors"], env)
                val contentCol = extractButtonContentColor(args["colors"], env)
                val elemType = when (name) {
                    "OutlinedButton" -> UiElementType.OUTLINED_BUTTON
                    "TextButton" -> UiElementType.TEXT_BUTTON
                    else -> UiElementType.BUTTON
                }
                val btn = UiNode(
                    type = elemType,
                    backgroundColor = bg,
                    textColor = contentCol,
                    actionBlock = resolveBoundActions(actionBody, env),
                    localBindings = snapshotLocals(env),
                    mod = mod
                )
                env.nodeBudget[0]++
                if (lambdaBody != null) evalBlockStatements(lambdaBody, env, btn.children)
                out.add(btn)
            }

            "IconButton", "FloatingActionButton" -> {
                val onClickExpr = args["onClick"] ?: ""
                val actionBody = extractFirstBracedBody(onClickExpr) ?: onClickExpr
                val mod = parseModifier(args["modifier"], env)
                val btn = UiNode(
                    type = UiElementType.ICON_BUTTON,
                    actionBlock = resolveBoundActions(actionBody, env),
                    localBindings = snapshotLocals(env),
                    mod = mod
                )
                env.nodeBudget[0]++
                if (lambdaBody != null) evalBlockStatements(lambdaBody, env, btn.children)
                out.add(btn)
            }

            "OutlinedTextField", "TextField", "BasicTextField" -> {
                val valExpr = (args["value"] ?: "").trim()
                val currentVal = evalStringExpr(valExpr, env)
                val placeholderNodes = mutableListOf<UiNode>()
                val phRaw = args["placeholder"] ?: args["label"]
                if (phRaw != null) {
                    evalBlockStatements(extractFirstBracedBody(phRaw) ?: phRaw, env, placeholderNodes)
                }
                val phText = placeholderNodes.firstOrNull { it.text.isNotEmpty() }?.text ?: "Type here..."
                val mod = parseModifier(args["modifier"], env)
                env.nodeBudget[0]++
                out.add(
                    UiNode(
                        type = UiElementType.TEXT_FIELD,
                        text = currentVal,
                        placeholder = phText,
                        stateKey = if (valExpr.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) valExpr else "",
                        mod = mod
                    )
                )
            }

            "Switch", "Checkbox" -> {
                val checkedExpr = (args["checked"] ?: "false").trim()
                val isChecked = evalCondition(checkedExpr, env)
                val onChecked = extractFirstBracedBody(args["onCheckedChange"] ?: "") ?: ""
                env.nodeBudget[0]++
                out.add(
                    UiNode(
                        type = if (name == "Switch") UiElementType.SWITCH else UiElementType.CHECKBOX,
                        boolState = isChecked,
                        stateKey = if (checkedExpr.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) checkedExpr else "",
                        actionBlock = resolveBoundActions(onChecked, env)
                    )
                )
            }

            "Icon" -> {
                val rawVecExpr = (args["imageVector"] ?: args["_0"] ?: "").trim()
                val resolvedVec = if (env.locals.containsKey(rawVecExpr)) {
                    env.locals[rawVecExpr] as? String
                } else {
                    rawVecExpr
                }
                if (resolvedVec.isNullOrBlank() || resolvedVec == "null") return
                val tint = args["tint"]?.let { parseColorExpr(it, env) }
                val mod = parseModifier(args["modifier"], env)
                env.nodeBudget[0]++
                out.add(
                    UiNode(
                        type = UiElementType.ICON,
                        iconName = resolvedVec,
                        textColor = tint,
                        mod = mod
                    )
                )
            }

            "Spacer" -> {
                val mod = parseModifier(args["modifier"], env)
                env.nodeBudget[0]++
                out.add(UiNode(type = UiElementType.SPACER, mod = mod))
            }

            "HorizontalDivider", "Divider" -> {
                val color = args["color"]?.let { parseColorExpr(it, env) } ?: Color.White.copy(alpha = 0.1f)
                env.nodeBudget[0]++
                out.add(UiNode(type = UiElementType.DIVIDER, backgroundColor = color))
            }

            "CircularProgressIndicator", "LinearProgressIndicator" -> {
                val color = args["color"]?.let { parseColorExpr(it, env) } ?: NeonLime
                env.nodeBudget[0]++
                out.add(UiNode(type = UiElementType.PROGRESS, text = name, textColor = color))
            }

            else -> {
                val customDef = env.index.composables[name]
                if (customDef != null && env.depth < 12) {
                    val callLocals = HashMap<String, Any?>()
                    customDef.params.forEachIndexed { idx, p ->
                        val passed = args[p.name] ?: args["_$idx"]
                        if (passed != null) {
                            val cleanPassed = passed.trim()
                            callLocals[p.name] = when {
                                cleanPassed == "null" -> null
                                cleanPassed.startsWith("{") -> cleanPassed
                                p.name == "content" || p.name == "modifier" || p.name == "icon" || p.name.startsWith("on") -> cleanPassed
                                else -> evalValueExpr(cleanPassed, env)
                            }
                        } else if (p.defaultExpr != null) {
                            val cleanDef = p.defaultExpr.trim()
                            callLocals[p.name] = when {
                                cleanDef == "null" -> null
                                p.name == "modifier" || p.name == "icon" -> cleanDef
                                else -> evalValueExpr(cleanDef, env)
                            }
                        }
                    }
                    if (lambdaBody != null) {
                        callLocals["content"] = lambdaBody
                    }
                    evalBlockStatements(customDef.body, env.childEnv(callLocals), out)
                } else if (name == "content" && env.locals["content"] is String) {
                    val contentLambda = env.locals["content"] as String
                    evalBlockStatements(contentLambda, env.childEnv(), out)
                }
            }
        }
    }

    private fun evalLazyColumnItems(body: String, env: EvalEnv, out: MutableList<UiNode>) {
        val statements = splitTopLevelStatements(body)
        for (stmt in statements) {
            val trimmed = stmt.trim()
            if (trimmed.startsWith("item ") || trimmed.startsWith("item(") || trimmed.startsWith("item{")) {
                val bStart = trimmed.indexOf('{')
                val bEnd = if (bStart != -1) findMatchingDelim(trimmed, bStart, '{', '}') else -1
                if (bEnd != -1) {
                    evalBlockStatements(trimmed.substring(bStart + 1, bEnd), env, out)
                }
            } else if (trimmed.startsWith("items(")) {
                val pEnd = findMatchingDelim(trimmed, 5, '(', ')')
                if (pEnd == -1) continue
                val firstArg = splitTopLevelArgs(trimmed.substring(6, pEnd)).firstOrNull() ?: ""
                val list = evalListExpr(firstArg, env)
                val bStart = trimmed.indexOf('{', pEnd)
                val bEnd = if (bStart != -1) findMatchingDelim(trimmed, bStart, '{', '}') else -1
                if (bEnd != -1) {
                    val rawBody = trimmed.substring(bStart + 1, bEnd).trim()
                    val (params, cleanBody) = splitLambdaParams(rawBody)
                    list.take(15).forEach { item ->
                        val locals = HashMap<String, Any?>()
                        if (params.isNotEmpty()) bindLambdaParam(params[0], item, locals)
                        else locals["it"] = item
                        evalBlockStatements(cleanBody, env.childEnv(locals), out)
                    }
                }
            } else {
                evalBlockStatements(trimmed, env, out)
            }
        }
    }

    private fun resolveBoundActions(actionBody: String, env: EvalEnv): String {
        val trimmed = actionBody.trim()
        val cleanName = trimmed.removeSuffix("()").trim()
        val bound = env.locals[cleanName]
        if (bound is String && bound.contains("{")) {
            return extractFirstBracedBody(bound) ?: bound
        }
        return trimmed
    }

    private fun snapshotLocals(env: EvalEnv): Map<String, String> {
        val out = HashMap<String, String>()
        env.locals.forEach { (k, v) ->
            if (v is String) out[k] = v
        }
        return out
    }

// ==================== PART 1 / 2 ENDS HERE ====================
// ================= ৬. অ্যাকশন ও স্টেট এক্সিকিউটর =================
    fun executeActionBlock(
        actionBlock: String,
        runtimeState: MutableMap<String, Any?>,
        localBindings: Map<String, String>,
        context: Context
    ) {
        if (actionBlock.isBlank()) return
        val dummyIndex = ProjectUiIndex("", "", "", emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList())
        val env = EvalEnv(dummyIndex, runtimeState, HashMap(localBindings), mutableListOf(), mutableListOf(), depth = 0)
        executeStatementsForAction(actionBlock, env, runtimeState, context)
    }

    private fun executeStatementsForAction(
        block: String,
        env: EvalEnv,
        runtimeState: MutableMap<String, Any?>,
        context: Context
    ) {
        val stmts = splitTopLevelStatements(block)
        for (raw in stmts) {
            val s = raw.trim()
            if (s.isEmpty()) continue

            if (s.startsWith("if ") || s.startsWith("if(")) {
                val pStart = s.indexOf('(')
                val pEnd = if (pStart != -1) findMatchingDelim(s, pStart, '(', ')') else -1
                if (pEnd != -1) {
                    val cond = evalCondition(s.substring(pStart + 1, pEnd), env)
                    val rest = s.substring(pEnd + 1).trim()
                    if (rest.startsWith("{")) {
                        val bEnd = findMatchingDelim(rest, 0, '{', '}')
                        if (bEnd != -1) {
                            val ifBody = rest.substring(1, bEnd)
                            val after = rest.substring(bEnd + 1).trim()
                            if (cond) {
                                executeStatementsForAction(ifBody, env, runtimeState, context)
                            } else if (after.startsWith("else")) {
                                val elseBody = extractFirstBracedBody(after) ?: after.removePrefix("else").trim()
                                executeStatementsForAction(elseBody, env, runtimeState, context)
                            }
                            continue
                        }
                    }
                }
            }

            if (s.contains("Toast.makeText")) {
                val msg = Regex("""Toast\.makeText\s*\([^,]+,\s*([^,]+)""").find(s)?.groupValues?.getOrNull(1)
                if (msg != null) {
                    val text = evalStringExpr(msg, env)
                    if (text.isNotEmpty()) {
                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    }
                }
                continue
            }

            val incMatch = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*(\+\+|--)$""").find(s)
            if (incMatch != null) {
                val v = incMatch.groupValues[1]
                val op = incMatch.groupValues[2]
                val cur = (runtimeState[v] as? Number)?.toInt() ?: 0
                runtimeState[v] = if (op == "++") cur + 1 else cur - 1
                continue
            }

            val augMatch = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*(\+=|-=)\s*(.+)$""").find(s)
            if (augMatch != null) {
                val v = augMatch.groupValues[1]
                val op = augMatch.groupValues[2]
                val rhsVal = evalValueExpr(augMatch.groupValues[3], env)
                val cur = runtimeState[v]
                if (cur is Number && rhsVal is Number) {
                    runtimeState[v] = if (op == "+=") cur.toInt() + rhsVal.toInt() else cur.toInt() - rhsVal.toInt()
                } else if (cur is String && op == "+=") {
                    runtimeState[v] = cur + (rhsVal?.toString() ?: "")
                }
                continue
            }

            val eqIdx = findTopLevelSingleEquals(s)
            if (eqIdx > 0) {
                val lhs = s.substring(0, eqIdx).trim()
                val rhs = s.substring(eqIdx + 1).trim()
                if (lhs.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
                    val evaluated = evalValueExpr(rhs, env)
                    runtimeState[lhs] = evaluated
                    env.locals[lhs] = evaluated
                }
            }
        }
    }

    // ================= ৭. এক্সপ্রেশন, স্ট্রিং, কন্ডিশন ও মডিফায়ার পার্সার =================
    private fun evalValueExpr(expr: String, env: EvalEnv): Any? {
        val e = expr.trim()
        if (e.isEmpty() || e == "null") return null
        if (e == "true") return true
        if (e == "false") return false

        if (e.startsWith("if ") || e.startsWith("if(")) {
            return evalInlineIfValue(e, env)
        }

        if (e.startsWith("!")) {
            return !evalCondition(e.substring(1).trim(), env)
        }

        if ((e.startsWith("\"") && e.endsWith("\"")) || e.contains("\"")) {
            return evalStringExpr(e, env)
        }

        e.removeSuffix("f").removeSuffix("F").removeSuffix("L").let { numStr ->
            numStr.toIntOrNull()?.let { return it }
            numStr.toFloatOrNull()?.let { return it }
        }

        if (env.runtimeState.containsKey(e)) return env.runtimeState[e]
        if (env.locals.containsKey(e)) return env.locals[e]

        if (e.contains(".")) {
            val base = e.substringBefore(".")
            val prop = e.substringAfter(".")
            if (prop == "size") {
                val obj = env.runtimeState[base] ?: env.locals[base]
                return if (obj is Collection<*>) obj.size else 0
            }
            if (prop == "length") {
                val obj = env.runtimeState[base] ?: env.locals[base]
                return if (obj is String) obj.length else 0
            }
            if (prop == "isEmpty()") {
                val obj = env.runtimeState[base] ?: env.locals[base]
                if (obj is Collection<*>) return obj.isEmpty()
                if (obj is String) return obj.isEmpty()
                return true
            }
            if (prop == "isNotEmpty()") {
                val obj = env.runtimeState[base] ?: env.locals[base]
                if (obj is Collection<*>) return obj.isNotEmpty()
                if (obj is String) return obj.isNotEmpty()
                return false
            }
            if (e.startsWith("Icons.") || e.startsWith("Color.") || e.startsWith("Modifier")) {
                return e
            }
        }

        val plusIdx = findTopLevelOperator(e, '+')
        if (plusIdx > 0) {
            val l = evalValueExpr(e.substring(0, plusIdx), env)
            val r = evalValueExpr(e.substring(plusIdx + 1), env)
            return when {
                l is Int && r is Int -> l + r
                l is Number && r is Number -> l.toFloat() + r.toFloat()
                else -> "${l ?: ""}${r ?: ""}"
            }
        }

        val minusIdx = findTopLevelOperator(e, '-')
        if (minusIdx > 0) {
            val l = evalValueExpr(e.substring(0, minusIdx), env)
            val r = evalValueExpr(e.substring(minusIdx + 1), env)
            if (l is Int && r is Int) return l - r
            if (l is Number && r is Number) return l.toFloat() - r.toFloat()
        }

        if (e.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
            return null
        }

        return e
    }

    private fun evalInlineIfValue(e: String, env: EvalEnv): Any? {
        val pStart = e.indexOf('(')
        val pEnd = if (pStart != -1) findMatchingDelim(e, pStart, '(', ')') else -1
        if (pEnd == -1) return e
        val cond = evalCondition(e.substring(pStart + 1, pEnd), env)
        val rest = e.substring(pEnd + 1).trim()
        val elseIdx = findTopLevelKeyword(rest, "else")
        if (elseIdx == -1) return null
        val trueExpr = rest.substring(0, elseIdx).trim().removeSurrounding("{", "}").trim()
        val falseExpr = rest.substring(elseIdx + 4).trim().removeSurrounding("{", "}").trim()
        return evalValueExpr(if (cond) trueExpr else falseExpr, env)
    }

    private fun evalStringExpr(expr: String, env: EvalEnv): String {
        val e = expr.trim()
        if (e.isEmpty() || e == "null") return ""

        if (e.startsWith("if ") || e.startsWith("if(")) {
            return evalInlineIfValue(e, env)?.toString() ?: ""
        }

        val srMatch = Regex("""stringResource\s*\(\s*R\.string\.([A-Za-z0-9_]+)\s*\)""").find(e)
        if (srMatch != null) {
            val key = srMatch.groupValues[1]
            return env.index.stringResources[key] ?: key
        }

        val plusIdx = findTopLevelOperator(e, '+')
        if (plusIdx > 0) {
            return evalStringExpr(e.substring(0, plusIdx), env) +
                evalStringExpr(e.substring(plusIdx + 1), env)
        }

        if (e.startsWith("\"") && e.endsWith("\"") && e.length >= 2) {
            val raw = e.substring(1, e.length - 1)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
            return interpolateEnvString(raw, env)
        }

        if (env.runtimeState.containsKey(e)) return env.runtimeState[e]?.toString() ?: ""
        if (env.locals.containsKey(e)) return env.locals[e]?.toString() ?: ""

        return e.removeSurrounding("\"")
    }

    private fun interpolateEnvString(raw: String, env: EvalEnv): String {
        var res = Regex("""\$\{([^}]+)\}""").replace(raw) { m ->
            evalValueExpr(m.groupValues[1], env)?.toString() ?: ""
        }
        res = Regex("""\$([A-Za-z_][A-Za-z0-9_]*)""").replace(res) { m ->
            val key = m.groupValues[1]
            val v = env.runtimeState[key] ?: env.locals[key]
            v?.toString() ?: ""
        }
        return res
    }

    private fun evalCondition(expr: String, env: EvalEnv): Boolean {
        val e = expr.trim()
        if (e.isEmpty()) return false
        if (e == "true") return true
        if (e == "false") return false

        val orIdx = e.indexOf("||")
        if (orIdx > 0) {
            return evalCondition(e.substring(0, orIdx), env) || evalCondition(e.substring(orIdx + 2), env)
        }
        val andIdx = e.indexOf("&&")
        if (andIdx > 0) {
            return evalCondition(e.substring(0, andIdx), env) && evalCondition(e.substring(andIdx + 2), env)
        }

        if (e.startsWith("!")) {
            return !evalCondition(e.substring(1).trim(), env)
        }

        val eqIdx = e.indexOf("==")
        if (eqIdx > 0) {
            val l = evalValueExpr(e.substring(0, eqIdx), env)
            val r = evalValueExpr(e.substring(eqIdx + 2), env)
            return l?.toString() == r?.toString()
        }
        val neqIdx = e.indexOf("!=")
        if (neqIdx > 0) {
            val l = evalValueExpr(e.substring(0, neqIdx), env)
            val r = evalValueExpr(e.substring(neqIdx + 2), env)
            return l?.toString() != r?.toString()
        }

        val gtIdx = findTopLevelOperator(e, '>')
        if (gtIdx > 0) {
            val l = (evalValueExpr(e.substring(0, gtIdx), env) as? Number)?.toFloat() ?: 0f
            val r = (evalValueExpr(e.substring(gtIdx + 1), env) as? Number)?.toFloat() ?: 0f
            return l > r
        }

        val ltIdx = findTopLevelOperator(e, '<')
        if (ltIdx > 0) {
            val l = (evalValueExpr(e.substring(0, ltIdx), env) as? Number)?.toFloat() ?: 0f
            val r = (evalValueExpr(e.substring(ltIdx + 1), env) as? Number)?.toFloat() ?: 0f
            return l < r
        }

        val v = evalValueExpr(e, env)
        return when (v) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is Collection<*> -> v.isNotEmpty()
            is String -> v.isNotEmpty() && v != "false" && v != "null"
            else -> v != null
        }
    }

    private fun evalListExpr(expr: String, env: EvalEnv): List<Any?> {
        val e = expr.trim()
        if (e.startsWith("listOf(") && e.endsWith(")")) {
            val inner = e.substring(7, e.length - 1)
            return splitTopLevelArgs(inner).map { item ->
                val toIdx = item.indexOf(" to ")
                if (toIdx != -1) {
                    evalValueExpr(item.substring(0, toIdx), env) to evalValueExpr(item.substring(toIdx + 4), env)
                } else {
                    evalValueExpr(item, env)
                }
            }
        }
        val v = env.runtimeState[e] ?: env.locals[e]
        return if (v is List<*>) v else emptyList()
    }

    private fun parseModifier(modExpr: String?, env: EvalEnv): UiModifierSpec {
        if (modExpr.isNullOrBlank()) return UiModifierSpec()
        var resolvedExpr = modExpr.trim()

        val firstIdent = resolvedExpr.substringBefore(".").trim()
        val localMod = env.locals[firstIdent]
        if (firstIdent != "Modifier" && localMod is String && localMod.contains("Modifier")) {
            resolvedExpr = if (resolvedExpr == firstIdent) {
                localMod
            } else {
                localMod + resolvedExpr.removePrefix(firstIdent)
            }
        }

        var spec = UiModifierSpec()

        // নেস্টেড ব্র্যাকেটসহ মডিফায়ার চেইন (background, shadow, clip, border, padding, clickable) নির্ভুলভাবে পার্স করা
        val chainCalls = parseModifierChain(resolvedExpr)
        for ((fnName, rawArgs, rawLambda) in chainCalls) {
            val argsList = splitTopLevelArgs(rawArgs)
            when (fnName) {
                "fillMaxSize" -> spec = spec.copy(fillMaxSize = true)
                "fillMaxWidth" -> spec = spec.copy(fillMaxWidth = true)
                "fillMaxHeight" -> spec = spec.copy(fillMaxHeight = true)
                "verticalScroll" -> spec = spec.copy(verticalScroll = true)
                "horizontalScroll" -> spec = spec.copy(horizontalScroll = true)
                "weight" -> {
                    val wVal = argsList.firstOrNull()?.removeSuffix("f")?.removeSuffix("F")?.toFloatOrNull()
                    if (wVal != null) spec = spec.copy(weight = wVal)
                }
                "width" -> {
                    val wDp = argsList.firstOrNull()?.let { parseDpOrSp(it) }
                    if (wDp != null) spec = spec.copy(widthDp = wDp)
                }
                "height" -> {
                    val hDp = argsList.firstOrNull()?.let { parseDpOrSp(it) }
                    if (hDp != null) spec = spec.copy(heightDp = hDp)
                }
                "size" -> {
                    if (argsList.size >= 2) {
                        var wVal: Float? = null
                        var hVal: Float? = null
                        argsList.forEachIndexed { idx, arg ->
                            val k = arg.substringBefore("=").trim()
                            val v = parseDpOrSp(arg.substringAfter("="))
                            if (k == "width" || idx == 0) wVal = v ?: wVal
                            if (k == "height" || idx == 1) hVal = v ?: hVal
                        }
                        spec = spec.copy(widthDp = wVal, heightDp = hVal)
                    } else {
                        val sDp = argsList.firstOrNull()?.let { parseDpOrSp(it) }
                        if (sDp != null) spec = spec.copy(sizeDp = sDp)
                    }
                }
                "padding" -> {
                    if (argsList.size == 1 && !argsList[0].contains("=")) {
                        val dpVal = parseDpOrSp(argsList[0]) ?: 0f
                        spec = spec.copy(paddingAllDp = spec.paddingAllDp + dpVal)
                    } else {
                        argsList.forEach { arg ->
                            val k = arg.substringBefore("=").trim()
                            val v = parseDpOrSp(arg.substringAfter("=")) ?: 0f
                            spec = when (k) {
                                "horizontal" -> spec.copy(paddingHorizDp = spec.paddingHorizDp + v)
                                "vertical" -> spec.copy(paddingVertDp = spec.paddingVertDp + v)
                                "start" -> spec.copy(paddingStartDp = spec.paddingStartDp + v)
                                "end" -> spec.copy(paddingEndDp = spec.paddingEndDp + v)
                                "top" -> spec.copy(paddingTopDp = spec.paddingTopDp + v)
                                "bottom" -> spec.copy(paddingBottomDp = spec.paddingBottomDp + v)
                                else -> spec
                            }
                        }
                    }
                }
                "clip" -> {
                    val shapeArg = argsList.firstOrNull() ?: ""
                    spec = applyShapeSpec(spec, shapeArg, forBackground = false)
                }
                "shadow" -> {
                    val elev = argsList.firstOrNull()?.let { parseDpOrSp(it.substringAfter("=")) } ?: 6f
                    spec = spec.copy(shadowDp = elev)
                    argsList.getOrNull(1)?.let { sh ->
                        spec = applyShapeSpec(spec, sh.substringAfter("="), forBackground = false)
                    }
                }
                "background" -> {
                    val firstArg = argsList.firstOrNull() ?: ""
                    if (firstArg.contains("Gradient", ignoreCase = true)) {
                        val listMatch = Regex("""listOf\s*\(([\s\S]*)\)""").find(firstArg)
                        val listContent = listMatch?.groupValues?.getOrNull(1) ?: ""
                        val cols = splitTopLevelArgs(listContent).mapNotNull { parseColorExpr(it, env) }
                        if (cols.size >= 2) spec = spec.copy(bgGradient = cols)
                    } else {
                        parseColorExpr(firstArg.substringAfter("="), env)?.let { c ->
                            spec = spec.copy(bgColor = c)
                        }
                    }
                    argsList.getOrNull(1)?.let { sh ->
                        spec = applyShapeSpec(spec, sh.substringAfter("="), forBackground = true)
                    }
                }
                "border" -> {
                    val first = argsList.firstOrNull() ?: ""
                    if (first.contains("BorderStroke")) {
                        val innerStart = first.indexOf('(')
                        val innerEnd = if (innerStart != -1) findMatchingDelim(first, innerStart, '(', ')') else -1
                        if (innerEnd != -1) {
                            val bsArgs = splitTopLevelArgs(first.substring(innerStart + 1, innerEnd))
                            val bw = bsArgs.firstOrNull()?.let { parseDpOrSp(it) } ?: 1f
                            val bc = bsArgs.getOrNull(1)?.let { parseColorExpr(it, env) }
                            if (bc != null) spec = spec.copy(borderWidthDp = bw, borderColor = bc)
                        }
                    } else {
                        val bw = parseDpOrSp(first) ?: 1f
                        val bc = argsList.getOrNull(1)?.let { parseColorExpr(it, env) }
                        if (bc != null) spec = spec.copy(borderWidthDp = bw, borderColor = bc)
                    }
                    argsList.lastOrNull()?.let { sh ->
                        if (sh.contains("Shape")) spec = applyShapeSpec(spec, sh, forBackground = false)
                    }
                }
                "clickable" -> {
                    val body = rawLambda ?: extractFirstBracedBody(rawArgs) ?: ""
                    if (body.isNotBlank()) {
                        spec = spec.copy(clickableBlock = resolveBoundActions(body, env))
                    }
                }
            }
        }

        return spec
    }

    private fun applyShapeSpec(spec: UiModifierSpec, shapeExpr: String, forBackground: Boolean): UiModifierSpec {
        val clean = shapeExpr.trim()
        if (clean.contains("CircleShape")) {
            return if (forBackground) spec.copy(bgIsCircle = true) else spec.copy(isCircle = true)
        }
        val rcStart = clean.indexOf("RoundedCornerShape")
        if (rcStart != -1) {
            val pStart = clean.indexOf('(', rcStart)
            val pEnd = if (pStart != -1) findMatchingDelim(clean, pStart, '(', ')') else -1
            if (pEnd != -1) {
                val inner = clean.substring(pStart + 1, pEnd)
                val args = splitTopLevelArgs(inner)
                if (args.size == 1 && !args[0].contains("=")) {
                    val r = parseDpOrSp(args[0]) ?: 12f
                    return if (forBackground) spec.copy(bgCornerRadiusDp = r) else spec.copy(cornerRadiusDp = r)
                } else {
                    var updated = spec
                    args.forEach { a ->
                        val k = a.substringBefore("=").trim()
                        val v = parseDpOrSp(a.substringAfter("="))
                        updated = when (k) {
                            "topStart" -> updated.copy(topStartCornerDp = v)
                            "topEnd" -> updated.copy(topEndCornerDp = v)
                            "bottomStart" -> updated.copy(bottomStartCornerDp = v)
                            "bottomEnd" -> updated.copy(bottomEndCornerDp = v)
                            else -> updated
                        }
                    }
                    return updated
                }
            }
        }
        return spec
    }

    private fun parseModifierChain(expr: String): List<Triple<String, String, String?>> {
        val result = mutableListOf<Triple<String, String, String?>>()
        var i = 0
        val len = expr.length
        while (i < len) {
            if (expr[i] == '"' || expr[i] == '\'') {
                i = skipKotlinStringOrChar(expr, i) + 1
                continue
            }
            if (expr[i] == '.') {
                var j = i + 1
                while (j < len && (expr[j].isLetterOrDigit() || expr[j] == '_')) j++
                val fnName = expr.substring(i + 1, j)
                while (j < len && expr[j].isWhitespace()) j++
                var rawArgs = ""
                var rawLambda: String? = null
                if (j < len && expr[j] == '(') {
                    val pEnd = findMatchingDelim(expr, j, '(', ')')
                    if (pEnd != -1) {
                        rawArgs = expr.substring(j + 1, pEnd)
                        j = pEnd + 1
                        while (j < len && expr[j].isWhitespace()) j++
                    }
                }
                if (j < len && expr[j] == '{') {
                    val bEnd = findMatchingDelim(expr, j, '{', '}')
                    if (bEnd != -1) {
                        rawLambda = expr.substring(j + 1, bEnd)
                        j = bEnd + 1
                    }
                }
                if (fnName.isNotEmpty()) {
                    result.add(Triple(fnName, rawArgs, rawLambda))
                }
                i = j
                continue
            }
            i++
        }
        return result
    }

    private fun parseColorExpr(expr: String, env: EvalEnv): Color? {
        val e = expr.trim()
        if (e.isEmpty() || e == "null") return null

        if (e.startsWith("if ") || e.startsWith("if(")) {
            val pStart = e.indexOf('(')
            val pEnd = if (pStart != -1) findMatchingDelim(e, pStart, '(', ')') else -1
            if (pEnd != -1) {
                val cond = evalCondition(e.substring(pStart + 1, pEnd), env)
                val rest = e.substring(pEnd + 1).trim()
                val elseIdx = findTopLevelKeyword(rest, "else")
                if (elseIdx != -1) {
                    val chosen = if (cond) rest.substring(0, elseIdx) else rest.substring(elseIdx + 4)
                    return parseColorExpr(chosen, env)
                }
            }
        }

        val copyIdx = e.indexOf(".copy(")
        if (copyIdx != -1) {
            val baseCol = parseColorExpr(e.substring(0, copyIdx), env) ?: return null
            val alphaMatch = Regex("""alpha\s*=\s*([0-9.]+)f?""").find(e.substring(copyIdx))
            val alphaVal = alphaMatch?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 1f
            return baseCol.copy(alpha = alphaVal.coerceIn(0f, 1f))
        }

        Regex("""Color\s*\(\s*0x([0-9A-Fa-f]{8})\s*\)""").find(e)?.let { m ->
            val argb = m.groupValues[1].toLongOrNull(16) ?: return null
            return Color(argb)
        }

        if (e.startsWith("#")) {
            return runCatching { Color(android.graphics.Color.parseColor(e)) }.getOrNull()
        }

        BUILTIN_COLORS[e]?.let { return it }
        env.index.colorConstants[e]?.let { return it }
        env.index.colorConstants[e.substringAfterLast(".")]?.let { return it }

        if (e.contains("primary", true)) return NeonLime
        if (e.contains("surface", true)) return Color(0xFF161616)
        if (e.contains("background", true)) return Color(0xFF0A0A0A)

        return null
    }

    private fun extractButtonContainerColor(colorsExpr: String?, env: EvalEnv): Color? {
        if (colorsExpr.isNullOrBlank()) return null
        val m = Regex("""containerColor\s*=\s*([^,)]+)""").find(colorsExpr) ?: return null
        return parseColorExpr(m.groupValues[1], env)
    }

    private fun extractButtonContentColor(colorsExpr: String?, env: EvalEnv): Color? {
        if (colorsExpr.isNullOrBlank()) return null
        val m = Regex("""contentColor\s*=\s*([^,)]+)""").find(colorsExpr) ?: return null
        return parseColorExpr(m.groupValues[1], env)
    }

    private fun extractStyleProp(styleExpr: String?, propName: String): String? {
        if (styleExpr.isNullOrBlank()) return null
        val m = Regex("""$propName\s*=\s*([^,)]+)""").find(styleExpr)
        return m?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractSpacedBy(expr: String?): Int {
        if (expr.isNullOrBlank()) return 0
        val m = Regex("""spacedBy\s*\(\s*([0-9]+)\.dp""").find(expr)
        return m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun parseDpOrSp(expr: String): Float? {
        val m = Regex("""([0-9]+(?:\.[0-9]+)?)\.(?:dp|sp)""").find(expr)
        return m?.groupValues?.getOrNull(1)?.toFloatOrNull()
    }

    // ================= ৮. Kotlin সিনট্যাক্স স্প্লিটার ও স্ট্যাটিক স্ক্যানার =================
    private data class ParsedCall(
        val name: String,
        val namedArgs: Map<String, String>,
        val trailingLambda: String?
    )

    private fun parseCallSyntax(stmt: String): ParsedCall? {
        val trimmed = stmt.trim()
        val m = Regex("""^([A-Za-z_][A-Za-z0-9_.]*)\s*(?:\(|(?=\{))""").find(trimmed) ?: return null
        val name = m.groupValues[1]
        var cursor = m.groupValues[1].length
        while (cursor < trimmed.length && trimmed[cursor].isWhitespace()) cursor++

        val argsMap = LinkedHashMap<String, String>()
        if (cursor < trimmed.length && trimmed[cursor] == '(') {
            val pEnd = findMatchingDelim(trimmed, cursor, '(', ')')
            if (pEnd == -1) return null
            val argStr = trimmed.substring(cursor + 1, pEnd)
            val argList = splitTopLevelArgs(argStr)
            argList.forEachIndexed { idx, arg ->
                val eqIdx = findTopLevelSingleEquals(arg)
                if (eqIdx > 0) {
                    val key = arg.substring(0, eqIdx).trim()
                    val value = arg.substring(eqIdx + 1).trim()
                    argsMap[key] = value
                } else {
                    argsMap["_$idx"] = arg.trim()
                }
            }
            cursor = pEnd + 1
            while (cursor < trimmed.length && trimmed[cursor].isWhitespace()) cursor++
        }

        var trailingLambda: String? = null
        if (cursor < trimmed.length && trimmed[cursor] == '{') {
            val bEnd = findMatchingDelim(trimmed, cursor, '{', '}')
            if (bEnd != -1) {
                trailingLambda = trimmed.substring(cursor + 1, bEnd)
            }
        }

        return ParsedCall(name, argsMap, trailingLambda)
    }

    private fun extractComposableFunctions(code: String, out: MutableMap<String, ComposableDef>) {
        val compRegex = Regex("""@Composable\b""")
        var searchFrom = 0
        while (true) {
            val compMatch = compRegex.find(code, searchFrom) ?: break
            val funMatch = Regex("""fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""").find(code, compMatch.range.last) ?: break
            val funName = funMatch.groupValues[1]
            val pStart = funMatch.range.last
            val pEnd = findMatchingDelim(code, pStart, '(', ')')
            if (pEnd == -1) {
                searchFrom = funMatch.range.last + 1
                continue
            }

            val paramsStr = code.substring(pStart + 1, pEnd)
            val params = splitTopLevelArgs(paramsStr).mapNotNull { rawParam ->
                val clean = rawParam.trim()
                if (clean.isEmpty()) null
                else {
                    val colonIdx = clean.indexOf(':')
                    val pName = if (colonIdx != -1) clean.substring(0, colonIdx).trim() else clean
                    val eqIdx = findTopLevelSingleEquals(clean)
                    val defExpr = if (eqIdx != -1) clean.substring(eqIdx + 1).trim() else null
                    ComposableParam(pName, defExpr)
                }
            }

            val bStart = code.indexOf('{', pEnd)
            if (bStart == -1) {
                searchFrom = pEnd + 1
                continue
            }
            val bEnd = findMatchingDelim(code, bStart, '{', '}')
            if (bEnd == -1) {
                searchFrom = bStart + 1
                continue
            }

            val body = code.substring(bStart + 1, bEnd)
            out[funName] = ComposableDef(funName, params, body)
            searchFrom = bEnd + 1
        }
    }

    private fun extractColorConstants(code: String, map: MutableMap<String, Color>) {
        Regex("""val\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*Color\s*\(\s*0x([0-9A-Fa-f]{8})\s*\)""").findAll(code).forEach { m ->
            val argb = m.groupValues[2].toLongOrNull(16)
            if (argb != null) map[m.groupValues[1]] = Color(argb)
        }
    }

    private fun extractCallLambda(code: String, callName: String): String? {
        val m = Regex("""\b$callName\s*(?:\([^)]*\))?\s*\{""").find(code) ?: return null
        val bStart = m.range.last
        val bEnd = findMatchingDelim(code, bStart, '{', '}')
        return if (bEnd != -1) code.substring(bStart + 1, bEnd) else null
    }

    private fun extractFirstBracedBody(expr: String): String? {
        val bStart = expr.indexOf('{')
        if (bStart == -1) return null
        val bEnd = findMatchingDelim(expr, bStart, '{', '}')
        return if (bEnd != -1) expr.substring(bStart + 1, bEnd) else null
    }

    private fun skipKotlinStringOrChar(str: String, i: Int): Int {
        if (str.startsWith("\"\"\"", i)) {
            val end = str.indexOf("\"\"\"", i + 3)
            return if (end == -1) str.length - 1 else end + 2
        }
        if (str[i] == '"') {
            var j = i + 1
            while (j < str.length) {
                if (str[j] == '\\') { j += 2; continue }
                if (str[j] == '"') return j
                j++
            }
            return str.length - 1
        }
        if (str[i] == '\'') {
            var j = i + 1
            if (j < str.length && str[j] == '\\') j += 2 else j++
            while (j < str.length && j - i <= 8) {
                if (str[j] == '\'') return j
                j++
            }
        }
        return i
    }

    private fun stripKotlinComments(code: String): String {
        val sb = StringBuilder(code.length)
        var i = 0
        val len = code.length
        while (i < len) {
            if (code[i] == '"' || code[i] == '\'') {
                val endIdx = skipKotlinStringOrChar(code, i)
                sb.append(code, i, minOf(len, endIdx + 1))
                i = endIdx + 1
                continue
            }
            if (code[i] == '/' && i + 1 < len && code[i + 1] == '/') {
                while (i < len && code[i] != '\n') i++
                continue
            }
            if (code[i] == '/' && i + 1 < len && code[i + 1] == '*') {
                val endC = code.indexOf("*/", i + 2)
                i = if (endC == -1) len else endC + 2
                continue
            }
            sb.append(code[i])
            i++
        }
        return sb.toString()
    }

    private fun splitTopLevelStatements(body: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var paren = 0
        var brace = 0
        var bracket = 0
        var i = 0

        while (i < body.length) {
            val c = body[i]
            if (c == '"' || c == '\'') {
                val endIdx = skipKotlinStringOrChar(body, i)
                cur.append(body, i, minOf(body.length, endIdx + 1))
                i = endIdx + 1
                continue
            }
            when (c) {
                '(' -> paren++
                ')' -> if (paren > 0) paren--
                '{' -> brace++
                '}' -> if (brace > 0) brace--
                '[' -> bracket++
                ']' -> if (bracket > 0) bracket--
            }

            if ((c == '\n' || c == ';') && paren == 0 && brace == 0 && bracket == 0) {
                val candidate = cur.toString().trim()
                val rest = body.substring(i + 1).trimStart()
                if (candidate.isNotEmpty() && !rest.startsWith(".") && !rest.startsWith("else")) {
                    result.add(candidate)
                    cur.setLength(0)
                    i++
                    continue
                }
            }
            cur.append(c)
            i++
        }
        val tail = cur.toString().trim()
        if (tail.isNotEmpty()) result.add(tail)
        return result
    }

    private fun splitTopLevelArgs(str: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var paren = 0
        var brace = 0
        var bracket = 0
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '"' || c == '\'') {
                val endIdx = skipKotlinStringOrChar(str, i)
                cur.append(str, i, minOf(str.length, endIdx + 1))
                i = endIdx + 1
                continue
            }
            when (c) {
                '(' -> paren++
                ')' -> if (paren > 0) paren--
                '{' -> brace++
                '}' -> if (brace > 0) brace--
                '[' -> bracket++
                ']' -> if (bracket > 0) bracket--
                ',' -> if (paren == 0 && brace == 0 && bracket == 0) {
                    val s = cur.toString().trim()
                    if (s.isNotEmpty()) out.add(s)
                    cur.setLength(0)
                    i++
                    continue
                }
            }
            cur.append(c)
            i++
        }
        val tail = cur.toString().trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }

    private fun splitLambdaParams(body: String): Pair<List<String>, String> {
        val trimmed = body.trimStart()
        val firstLine = trimmed.lineSequence().firstOrNull() ?: ""
        val arrowIdx = findTopLevelArrow(firstLine)
        if (arrowIdx != -1) {
            val paramPart = firstLine.substring(0, arrowIdx).trim()
            val params = splitTopLevelArgs(paramPart)
            val rest = trimmed.substring(arrowIdx + 2).trim()
            return params to rest
        }
        return emptyList<String>() to trimmed
    }

    private fun findMatchingDelim(str: String, openIdx: Int, openCh: Char, closeCh: Char): Int {
        var depth = 0
        var i = openIdx
        while (i < str.length) {
            val c = str[i]
            if (c == '"' || c == '\'') {
                i = skipKotlinStringOrChar(str, i) + 1
                continue
            }
            if (c == openCh) depth++
            else if (c == closeCh) {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return -1
    }

    private fun findTopLevelSingleEquals(str: String): Int {
        var paren = 0; var brace = 0; var bracket = 0
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '"' || c == '\'') { i = skipKotlinStringOrChar(str, i) + 1; continue }
            when (c) {
                '(' -> paren++; ')' -> if (paren > 0) paren--
                '{' -> brace++; '}' -> if (brace > 0) brace--
                '[' -> bracket++; ']' -> if (bracket > 0) bracket--
                '=' -> if (paren == 0 && brace == 0 && bracket == 0) {
                    val prev = if (i > 0) str[i - 1] else ' '
                    val next = if (i + 1 < str.length) str[i + 1] else ' '
                    if (prev != '=' && prev != '!' && prev != '<' && prev != '>' && next != '=' && next != '>') {
                        return i
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun findTopLevelArrow(str: String): Int {
        var paren = 0; var brace = 0
        var i = 0
        while (i < str.length - 1) {
            val c = str[i]
            if (c == '"' || c == '\'') { i = skipKotlinStringOrChar(str, i) + 1; continue }
            if (c == '(') paren++ else if (c == ')' && paren > 0) paren--
            else if (c == '{') brace++ else if (c == '}' && brace > 0) brace--
            else if (c == '-' && str[i + 1] == '>' && paren == 0 && brace == 0) return i
            i++
        }
        return -1
    }

    private fun findTopLevelOperator(str: String, op: Char): Int {
        var paren = 0; var brace = 0; var bracket = 0
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '"' || c == '\'') { i = skipKotlinStringOrChar(str, i) + 1; continue }
            when (c) {
                '(' -> paren++; ')' -> if (paren > 0) paren--
                '{' -> brace++; '}' -> if (brace > 0) brace--
                '[' -> bracket++; ']' -> if (bracket > 0) bracket--
                op -> if (paren == 0 && brace == 0 && bracket == 0 && i > 0) return i
            }
            i++
        }
        return -1
    }

    private fun findTopLevelKeyword(str: String, kw: String): Int {
        var paren = 0; var brace = 0
        var i = 0
        while (i <= str.length - kw.length) {
            val c = str[i]
            if (c == '"' || c == '\'') { i = skipKotlinStringOrChar(str, i) + 1; continue }
            if (c == '(') paren++ else if (c == ')' && paren > 0) paren--
            else if (c == '{') brace++ else if (c == '}' && brace > 0) brace--
            else if (paren == 0 && brace == 0 && str.startsWith(kw, i)) {
                val prevOk = i == 0 || !str[i - 1].isLetterOrDigit()
                val nextOk = i + kw.length >= str.length || !str[i + kw.length].isLetterOrDigit()
                if (prevOk && nextOk) return i
            }
            i++
        }
        return -1
    }

    // ================= ৯. XML রিসোর্স ও লেআউট পার্সার =================
    private fun parseXmlStrings(file: File, map: MutableMap<String, String>) {
        if (!file.exists()) return
        runCatching {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(file.readText(Charsets.UTF_8)))
            var ev = parser.eventType
            var curName: String? = null
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "string") {
                    curName = parser.getAttributeValue(null, "name")
                } else if (ev == XmlPullParser.TEXT && curName != null) {
                    map[curName] = parser.text ?: ""
                    curName = null
                }
                ev = parser.next()
            }
        }
    }

    private fun parseXmlColors(file: File, map: MutableMap<String, Color>) {
        if (!file.exists()) return
        runCatching {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(file.readText(Charsets.UTF_8)))
            var ev = parser.eventType
            var curName: String? = null
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "color") {
                    curName = parser.getAttributeValue(null, "name")
                } else if (ev == XmlPullParser.TEXT && curName != null) {
                    val c = runCatching { Color(android.graphics.Color.parseColor(parser.text.trim())) }.getOrNull()
                    if (c != null) map[curName] = c
                    curName = null
                }
                ev = parser.next()
            }
        }
    }

    private fun parseXmlLayout(xmlString: String, index: ProjectUiIndex): UiNode? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))
            val stack = ArrayDeque<UiNode>()
            var root: UiNode? = null

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.substringAfterLast(".")
                        var textAttr = getAttr(parser, "text") ?: getAttr(parser, "hint") ?: ""
                        if (textAttr.startsWith("@string/")) {
                            val k = textAttr.removePrefix("@string/")
                            textAttr = index.stringResources[k] ?: k
                        }
                        val widthAttr = getAttr(parser, "layout_width") ?: ""
                        val heightAttr = getAttr(parser, "layout_height") ?: ""
                        val orientAttr = getAttr(parser, "orientation") ?: "vertical"
                        val gravityAttr = getAttr(parser, "gravity") ?: ""
                        val bgAttr = getAttr(parser, "background")
                        val txtColorAttr = getAttr(parser, "textColor")
                        val txtSizeAttr = getAttr(parser, "textSize")?.removeSuffix("sp")?.removeSuffix("dp")?.toFloatOrNull() ?: 14f

                        val elemType = when (name.lowercase()) {
                            "linearlayout" -> if (orientAttr == "horizontal") UiElementType.ROW else UiElementType.COLUMN
                            "relativelayout", "framelayout", "constraintlayout", "scrollview" -> UiElementType.COLUMN
                            "textview" -> UiElementType.TEXT
                            "button", "materialbutton" -> UiElementType.BUTTON
                            "edittext", "textinputedittext" -> UiElementType.TEXT_FIELD
                            "switch", "switchmaterial" -> UiElementType.SWITCH
                            "checkbox" -> UiElementType.CHECKBOX
                            "cardview", "materialcardview" -> UiElementType.CARD
                            else -> UiElementType.BOX
                        }

                        val bgCol = bgAttr?.let { resolveXmlColor(it, index) }
                        val fgCol = txtColorAttr?.let { resolveXmlColor(it, index) }

                        val node = UiNode(
                            type = elemType,
                            text = textAttr,
                            textColor = fgCol,
                            textSize = txtSizeAttr,
                            backgroundColor = bgCol,
                            mod = UiModifierSpec(
                                fillMaxWidth = widthAttr.contains("match_parent"),
                                fillMaxSize = widthAttr.contains("match_parent") && heightAttr.contains("match_parent"),
                                paddingAllDp = 10f,
                                bgColor = bgCol
                            ),
                            alignment = if (gravityAttr.contains("center")) "center" else "start"
                        )
                        if (elemType == UiElementType.BUTTON && node.children.isEmpty()) {
                            node.children.add(UiNode(type = UiElementType.TEXT, text = textAttr.ifEmpty { "Button" }, isBold = true))
                        }

                        if (stack.isNotEmpty()) stack.last().children.add(node) else root = node
                        stack.addLast(node)
                    }
                    XmlPullParser.END_TAG -> if (stack.isNotEmpty()) stack.removeLast()
                }
                event = parser.next()
            }
            root
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveXmlColor(raw: String, index: ProjectUiIndex): Color? {
        if (raw.startsWith("#")) return runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrNull()
        if (raw.startsWith("@color/")) return index.colorConstants[raw.removePrefix("@color/")]
        return null
    }

    private fun getAttr(parser: XmlPullParser, attrName: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).equals(attrName, ignoreCase = true)) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }
}

// ================= ১০. রিয়েল ভার্চুয়াল ফোন স্কেলিং ও ইন্টারেক্টিভ হোস্ট =================
@Composable
fun InteractiveAppHost(
    rootNode: UiNode,
    modifier: Modifier = Modifier,
    externalSelectedScreen: String? = null
) {
    val context = LocalContext.current
    val index = rootNode.projectIndex
    val screenOptions = index?.screenOptions ?: listOf("Preview")

    var activeScreen by remember(index, externalSelectedScreen) {
        mutableStateOf(externalSelectedScreen ?: screenOptions.firstOrNull() ?: "Preview")
    }
    val runtimeState = remember(index) { mutableStateMapOf<String, Any?>() }
    var stateTick by remember { mutableIntStateOf(0) }

    val activeTree by remember(rootNode, activeScreen, stateTick) {
        mutableStateOf(
            if (index != null) {
                ProjectUiInterpreter.buildTreeForScreen(index, activeScreen, runtimeState)
            } else {
                rootNode
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(activeTree.backgroundColor ?: Color(0xFF0A0A0A))
    ) {
        val baseDensity = LocalDensity.current
        // আসল ৩৯২dp ফোনের অনুপাতে ভার্চুয়াল ডেনসিটি স্কেলিং যাতে কোনো লেখা না ভাঙে
        val targetVirtualWidth = if (maxWidth.value < 315f) 392f else 540f
        val scaleFactor = (maxWidth.value / targetVirtualWidth).coerceIn(0.55f, 1.15f)
        val virtualDensity = remember(baseDensity, scaleFactor) {
            Density(
                density = baseDensity.density * scaleFactor,
                fontScale = baseDensity.fontScale
            )
        }

        CompositionLocalProvider(
            LocalDensity provides virtualDensity,
            LocalContentColor provides Color.White
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                activeTree.children.forEach { child ->
                    RenderNodeTree(
                        node = child,
                        rowScope = null,
                        colScope = null,
                        isInsideVerticalScroll = false,
                        isInsideHorizontalScroll = false,
                        runtimeState = runtimeState,
                        onStateMutated = { stateTick++ }
                    )
                }

                if (activeTree.overlayDialogs.isNotEmpty()) {
                    val dlg = activeTree.overlayDialogs.last()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable(enabled = false) {}
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = dlg.backgroundColor ?: Color(0xFF1A1B22),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                dlg.children.forEach { dChild ->
                                    RenderNodeTree(
                                        node = dChild,
                                        rowScope = null,
                                        colScope = this,
                                        isInsideVerticalScroll = true,
                                        isInsideHorizontalScroll = false,
                                        runtimeState = runtimeState,
                                        onStateMutated = { stateTick++ }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderNodeTree(
    node: UiNode,
    rowScope: RowScope?,
    colScope: ColumnScope?,
    isInsideVerticalScroll: Boolean,
    isInsideHorizontalScroll: Boolean,
    runtimeState: MutableMap<String, Any?>,
    onStateMutated: () -> Unit
) {
    val context = LocalContext.current
    val canVScroll = node.mod.verticalScroll && (!isInsideVerticalScroll || node.mod.heightDp != null)
    val canHScroll = node.mod.horizontalScroll && (!isInsideHorizontalScroll || node.mod.widthDp != null)

    val composedMod = buildComposeModifier(
        spec = node.mod,
        node = node,
        rowScope = rowScope,
        colScope = colScope,
        allowVerticalScroll = canVScroll,
        allowHorizontalScroll = canHScroll
    ) {
        if (!node.mod.clickableBlock.isNullOrBlank()) {
            ProjectUiInterpreter.executeActionBlock(node.mod.clickableBlock, runtimeState, node.localBindings, context)
            onStateMutated()
        }
    }

    val nextVScroll = isInsideVerticalScroll || canVScroll
    val nextHScroll = isInsideHorizontalScroll || canHScroll

    when (node.type) {
        UiElementType.COLUMN -> {
            val hAlign = if (node.alignment == "center") Alignment.CenterHorizontally else Alignment.Start
            val vArr = if (node.verticalSpacingDp > 0) Arrangement.spacedBy(node.verticalSpacingDp.dp) else Arrangement.Top
            Column(
                modifier = composedMod,
                horizontalAlignment = hAlign,
                verticalArrangement = vArr
            ) {
                node.children.forEach { child ->
                    RenderNodeTree(child, null, this, nextVScroll, nextHScroll, runtimeState, onStateMutated)
                }
            }
        }

        UiElementType.ROW -> {
            val hArr = when {
                node.horizontalSpaceBetween -> Arrangement.SpaceBetween
                node.horizontalCenterArrange -> Arrangement.Center
                node.horizontalSpacingDp > 0 -> Arrangement.spacedBy(node.horizontalSpacingDp.dp)
                else -> Arrangement.Start
            }
            Row(
                modifier = composedMod,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = hArr
            ) {
                node.children.forEach { child ->
                    RenderNodeTree(child, this, null, nextVScroll, nextHScroll, runtimeState, onStateMutated)
                }
            }
        }

        UiElementType.BOX -> {
            val cAlign = if (node.alignment == "center") Alignment.Center else Alignment.TopStart
            Box(modifier = composedMod, contentAlignment = cAlign) {
                node.children.forEach { child ->
                    RenderNodeTree(child, null, null, nextVScroll, nextHScroll, runtimeState, onStateMutated)
                }
            }
        }

        UiElementType.SURFACE, UiElementType.CARD -> {
            val shape = buildNodeShape(node.mod, defaultRadiusDp = 12f)
            Surface(
                color = node.backgroundColor ?: Color(0xFF1A1B22),
                contentColor = Color.White,
                shape = shape,
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.09f)),
                modifier = composedMod
            ) {
                Column(modifier = Modifier.padding(2.dp)) {
                    node.children.forEach { child ->
                        RenderNodeTree(child, null, this, nextVScroll, nextHScroll, runtimeState, onStateMutated)
                    }
                }
            }
        }

        UiElementType.BUTTON, UiElementType.OUTLINED_BUTTON, UiElementType.TEXT_BUTTON -> {
            val onClick = {
                if (node.actionBlock.isNotBlank()) {
                    ProjectUiInterpreter.executeActionBlock(node.actionBlock, runtimeState, node.localBindings, context)
                    onStateMutated()
                }
            }
            val shape = buildNodeShape(node.mod, defaultRadiusDp = 12f)
            when (node.type) {
                UiElementType.OUTLINED_BUTTON -> OutlinedButton(
                    onClick = onClick,
                    shape = shape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = composedMod
                ) {
                    if (node.children.isNotEmpty()) {
                        node.children.forEach { RenderNodeTree(it, this, null, nextVScroll, nextHScroll, runtimeState, onStateMutated) }
                    } else {
                        Text(node.text.ifEmpty { "Button" }, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                UiElementType.TEXT_BUTTON -> TextButton(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = composedMod
                ) {
                    if (node.children.isNotEmpty()) {
                        node.children.forEach { RenderNodeTree(it, this, null, nextVScroll, nextHScroll, runtimeState, onStateMutated) }
                    } else {
                        Text(node.text.ifEmpty { "Action" }, color = NeonLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                else -> Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = node.backgroundColor ?: NeonLime,
                        contentColor = node.textColor ?: Color.Black
                    ),
                    shape = shape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = composedMod
                ) {
                    if (node.children.isNotEmpty()) {
                        node.children.forEach { RenderNodeTree(it, this, null, nextVScroll, nextHScroll, runtimeState, onStateMutated) }
                    } else {
                        Text(node.text.ifEmpty { "Button" }, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        UiElementType.ICON_BUTTON -> {
            IconButton(
                onClick = {
                    if (node.actionBlock.isNotBlank()) {
                        ProjectUiInterpreter.executeActionBlock(node.actionBlock, runtimeState, node.localBindings, context)
                        onStateMutated()
                    }
                },
                modifier = composedMod
            ) {
                node.children.forEach { RenderNodeTree(it, null, null, nextVScroll, nextHScroll, runtimeState, onStateMutated) }
            }
        }

        UiElementType.TEXT_FIELD -> {
            var localText by remember(node.stateKey, node.text) { mutableStateOf(node.text) }
            OutlinedTextField(
                value = localText,
                onValueChange = { updated ->
                    localText = updated
                    if (node.stateKey.isNotEmpty()) {
                        runtimeState[node.stateKey] = updated
                        onStateMutated()
                    }
                },
                placeholder = { Text(node.placeholder, color = TextSecondary, fontSize = 13.sp, maxLines = 1) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF16171D),
                    unfocusedContainerColor = Color(0xFF16171D),
                    focusedBorderColor = NeonLime,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                    cursorColor = NeonLime
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = composedMod
            )
        }

        UiElementType.TEXT -> {
            val tAlign = when (node.alignment) {
                "center" -> TextAlign.Center
                "end" -> TextAlign.End
                else -> TextAlign.Start
            }
            Text(
                text = node.text,
                color = node.textColor ?: LocalContentColor.current,
                fontSize = node.textSize.coerceIn(9f, 28f).sp,
                fontWeight = if (node.isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = if (node.isMonospace) FontFamily.Monospace else FontFamily.Default,
                textAlign = tAlign,
                maxLines = node.maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = composedMod
            )
        }

        UiElementType.ICON -> {
            val vec = resolveMaterialIcon(node.iconName)
            if (vec != null) {
                Icon(
                    imageVector = vec,
                    contentDescription = null,
                    tint = node.textColor ?: LocalContentColor.current,
                    modifier = composedMod.then(
                        if (node.mod.sizeDp == null) Modifier.size(20.dp) else Modifier
                    )
                )
            }
        }

        UiElementType.SWITCH -> {
            Switch(
                checked = node.boolState,
                onCheckedChange = { newVal ->
                    if (node.stateKey.isNotEmpty()) {
                        runtimeState[node.stateKey] = newVal
                        onStateMutated()
                    }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = NeonLime)
            )
        }

        UiElementType.CHECKBOX -> {
            Checkbox(
                checked = node.boolState,
                onCheckedChange = { newVal ->
                    if (node.stateKey.isNotEmpty()) {
                        runtimeState[node.stateKey] = newVal
                        onStateMutated()
                    }
                },
                colors = CheckboxDefaults.colors(checkedColor = NeonLime, checkmarkColor = Color.Black)
            )
        }

        UiElementType.SPACER -> {
            val w = node.mod.widthDp ?: 0f
            val h = node.mod.heightDp ?: (node.heightDp?.toFloat() ?: 0f)
            if (w > 0f || h > 0f) {
                Spacer(modifier = Modifier.size(width = w.dp, height = h.dp))
            } else {
                Spacer(modifier = composedMod)
            }
        }

        UiElementType.DIVIDER -> {
            HorizontalDivider(color = node.backgroundColor ?: Color.White.copy(alpha = 0.08f))
        }

        UiElementType.PROGRESS -> {
            CircularProgressIndicator(
                color = node.textColor ?: NeonLime,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        }

        else -> {
            Box(modifier = composedMod) {
                node.children.forEach { RenderNodeTree(it, null, null, nextVScroll, nextHScroll, runtimeState, onStateMutated) }
            }
        }
    }
}

private fun buildNodeShape(spec: UiModifierSpec, defaultRadiusDp: Float = 0f): Shape {
    if (spec.isCircle || spec.bgIsCircle) return CircleShape
    if (spec.topStartCornerDp != null || spec.topEndCornerDp != null ||
        spec.bottomStartCornerDp != null || spec.bottomEndCornerDp != null
    ) {
        return RoundedCornerShape(
            topStart = (spec.topStartCornerDp ?: 0f).dp,
            topEnd = (spec.topEndCornerDp ?: 0f).dp,
            bottomStart = (spec.bottomStartCornerDp ?: 0f).dp,
            bottomEnd = (spec.bottomEndCornerDp ?: 0f).dp
        )
    }
    val r = spec.cornerRadiusDp ?: spec.bgCornerRadiusDp ?: defaultRadiusDp
    return if (r > 0f) RoundedCornerShape(r.dp) else RoundedCornerShape(0.dp)
}

@Composable
private fun buildComposeModifier(
    spec: UiModifierSpec,
    node: UiNode,
    rowScope: RowScope?,
    colScope: ColumnScope?,
    allowVerticalScroll: Boolean,
    allowHorizontalScroll: Boolean,
    onClickable: () -> Unit
): Modifier {
    var m: Modifier = Modifier

    if (spec.fillMaxSize || node.isFillMaxSize) m = m.fillMaxSize()
    else {
        if (spec.fillMaxWidth || node.isFillMaxWidth) m = m.fillMaxWidth()
        if (spec.fillMaxHeight) m = m.fillMaxHeight()
    }

    if (spec.weight != null && spec.weight > 0f) {
        if (rowScope != null) m = with(rowScope) { m.weight(spec.weight) }
        else if (colScope != null) m = with(colScope) { m.weight(spec.weight) }
    }

    if (spec.sizeDp != null) {
        m = m.size(spec.sizeDp.dp)
    } else {
        if (spec.widthDp != null) m = m.width(spec.widthDp.dp)
        if (spec.heightDp != null) m = m.height(spec.heightDp.dp)
    }

    val shape = buildNodeShape(spec, defaultRadiusDp = 0f)

    if (spec.shadowDp > 0f) {
        m = m.shadow(spec.shadowDp.dp, shape)
    }

    if (spec.isCircle || spec.cornerRadiusDp != null || spec.topStartCornerDp != null) {
        m = m.clip(shape)
    }

    if (spec.bgGradient != null && spec.bgGradient.size >= 2) {
        m = m.background(Brush.linearGradient(spec.bgGradient), shape)
    } else if (spec.bgColor != null) {
        m = m.background(spec.bgColor, shape)
    }

    if (spec.borderColor != null && spec.borderWidthDp > 0f) {
        m = m.border(spec.borderWidthDp.dp, spec.borderColor, shape)
    }

    if (allowVerticalScroll) m = m.verticalScroll(rememberScrollState())
    if (allowHorizontalScroll) m = m.horizontalScroll(rememberScrollState())

    if (!spec.clickableBlock.isNullOrBlank()) {
        m = m.clickable(onClick = onClickable)
    }

    if (spec.paddingAllDp > 0f) m = m.padding(spec.paddingAllDp.dp)
    if (spec.paddingHorizDp > 0f || spec.paddingVertDp > 0f) {
        m = m.padding(horizontal = spec.paddingHorizDp.dp, vertical = spec.paddingVertDp.dp)
    }
    if (spec.paddingStartDp > 0f || spec.paddingEndDp > 0f || spec.paddingTopDp > 0f || spec.paddingBottomDp > 0f) {
        m = m.padding(
            start = spec.paddingStartDp.dp,
            end = spec.paddingEndDp.dp,
            top = spec.paddingTopDp.dp,
            bottom = spec.paddingBottomDp.dp
        )
    }
    if (node.paddingDp > 0 && spec.paddingAllDp == 0f) {
        m = m.padding(node.paddingDp.dp)
    }

    return m
}

private fun resolveMaterialIcon(iconExpr: String): ImageVector? {
    val trimmed = iconExpr.trim()
    if (trimmed.isEmpty() || trimmed == "null") return null
    val clean = trimmed.substringAfterLast(".")
    return when (clean) {
        "Add" -> Icons.Default.Add
        "ArrowBack" -> Icons.AutoMirrored.Filled.ArrowBack
        "Close" -> Icons.Default.Close
        "Search" -> Icons.Outlined.Search
        "Menu" -> Icons.Outlined.Menu
        "MoreVert" -> Icons.Outlined.MoreVert
        "Settings" -> Icons.Outlined.Settings
        "Home" -> Icons.Outlined.Home
        "Check", "CheckCircle" -> Icons.Outlined.CheckCircle
        "Warning" -> Icons.Outlined.Warning
        "Delete" -> Icons.Default.Delete
        "Edit" -> Icons.Default.Edit
        "PushPin" -> Icons.Filled.PushPin
        "PlayArrow" -> Icons.Outlined.PlayArrow
        "Cloud" -> Icons.Outlined.Cloud
        "Tune" -> Icons.Outlined.Tune
        "Code" -> Icons.Outlined.Code
        "Folder" -> Icons.Outlined.Folder
        "Terminal" -> Icons.Outlined.Terminal
        "Refresh" -> Icons.Default.Refresh
        "Star" -> Icons.Default.Star
        "Favorite" -> Icons.Default.Favorite
        "Share" -> Icons.Default.Share
        "Info" -> Icons.Default.Info
        "Person" -> Icons.Default.Person
        "Build" -> Icons.Outlined.Build
        else -> null
    }
}