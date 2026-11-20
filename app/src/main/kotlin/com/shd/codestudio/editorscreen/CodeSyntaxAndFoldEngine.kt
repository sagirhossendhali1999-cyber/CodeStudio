package com.shd.codestudio.editorscreen

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

internal data class SyntaxToken(
    val start: Int,
    val end: Int,
    val color: Int,
    val bold: Boolean = false
)

internal data class CompletionItem(
    val label: String,
    val insertText: String,
    val kind: String,
    val detail: String
)

internal object CodeLanguageEngine {

    private val COLOR_COMMENT = Color.parseColor("#6A9955")
    private val COLOR_STRING = Color.parseColor("#CE9178")
    private val COLOR_KEYWORD = Color.parseColor("#C586C0")
    private val COLOR_ANNOTATION = Color.parseColor("#DCDCAA")
    private val COLOR_NUMBER = Color.parseColor("#B5CEA8")
    private val COLOR_TYPE = Color.parseColor("#4EC9B0")
    private val COLOR_PUNCT = Color.parseColor("#FFD700")
    private val COLOR_XML_TAG = Color.parseColor("#569CD6")
    private val COLOR_XML_ATTR = Color.parseColor("#9CDCFE")

    // ================= ১. ফাস্ট সিনট্যাক্স টোকেনাইজার =================
    fun tokenizeCodeLine(
        line: String,
        startInComment: Boolean,
        defaultTextColor: Int
    ): Pair<List<SyntaxToken>, Boolean> {
        val list = ArrayList<SyntaxToken>()
        val len = line.length
        var i = 0
        var inComment = startInComment

        while (i < len) {
            if (inComment) {
                val endIdx = line.indexOf("*/", i)
                if (endIdx == -1) {
                    list.add(SyntaxToken(i, len, COLOR_COMMENT))
                    return list to true
                } else {
                    list.add(SyntaxToken(i, endIdx + 2, COLOR_COMMENT))
                    i = endIdx + 2
                    inComment = false
                    continue
                }
            }

            val ch = line[i]
            if (ch == '/' && i + 1 < len && line[i + 1] == '/') {
                list.add(SyntaxToken(i, len, COLOR_COMMENT))
                break
            }
            if (ch == '/' && i + 1 < len && line[i + 1] == '*') {
                val endIdx = line.indexOf("*/", i + 2)
                if (endIdx == -1) {
                    list.add(SyntaxToken(i, len, COLOR_COMMENT))
                    return list to true
                } else {
                    list.add(SyntaxToken(i, endIdx + 2, COLOR_COMMENT))
                    i = endIdx + 2
                    continue
                }
            }
            if (ch == '"' || ch == '\'') {
                val quote = ch
                var j = i + 1
                while (j < len) {
                    if (line[j] == '\\' && j + 1 < len) {
                        j += 2
                        continue
                    }
                    if (line[j] == quote) {
                        j++
                        break
                    }
                    j++
                }
                list.add(SyntaxToken(i, j, COLOR_STRING))
                i = j
                continue
            }
            if (ch == '@' && i + 1 < len && (line[i + 1].isLetter() || line[i + 1] == '_')) {
                var j = i + 1
                while (j < len && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                list.add(SyntaxToken(i, j, COLOR_ANNOTATION))
                i = j
                continue
            }
            if (ch.isDigit()) {
                var j = i + 1
                while (j < len && (line[j].isLetterOrDigit() || line[j] == '.' || line[j] == '_')) j++
                list.add(SyntaxToken(i, j, COLOR_NUMBER))
                i = j
                continue
            }
            if (ch.isLetter() || ch == '_') {
                var j = i + 1
                while (j < len && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                val word = line.substring(i, j)
                val color = when {
                    word in CODE_KEYWORDS -> COLOR_KEYWORD
                    word[0].isUpperCase() -> COLOR_TYPE
                    else -> defaultTextColor
                }
                list.add(SyntaxToken(i, j, color, bold = word in CODE_KEYWORDS))
                i = j
                continue
            }
            if (ch == '{' || ch == '}' || ch == '(' || ch == ')' || ch == '[' || ch == ']') {
                list.add(SyntaxToken(i, i + 1, COLOR_PUNCT))
                i++
                continue
            }
            var j = i + 1
            while (j < len && !isCodeBoundary(line[j])) j++
            list.add(SyntaxToken(i, j, defaultTextColor))
            i = j
        }
        return list to false
    }

    private fun isCodeBoundary(c: Char): Boolean {
        return c == '/' || c == '"' || c == '\'' || c == '@' || c.isDigit() ||
            c.isLetter() || c == '_' || c == '{' || c == '}' || c == '(' || c == ')' || c == '[' || c == ']'
    }

    fun tokenizeXmlLine(
        line: String,
        startInComment: Boolean,
        defaultTextColor: Int
    ): Pair<List<SyntaxToken>, Boolean> {
        val list = ArrayList<SyntaxToken>()
        val len = line.length
        var i = 0
        var inComment = startInComment

        while (i < len) {
            if (inComment) {
                val endIdx = line.indexOf("-->", i)
                if (endIdx == -1) {
                    list.add(SyntaxToken(i, len, COLOR_COMMENT))
                    return list to true
                } else {
                    list.add(SyntaxToken(i, endIdx + 3, COLOR_COMMENT))
                    i = endIdx + 3
                    inComment = false
                    continue
                }
            }
            if (line.startsWith("<!--", i)) {
                val endIdx = line.indexOf("-->", i + 4)
                if (endIdx == -1) {
                    list.add(SyntaxToken(i, len, COLOR_COMMENT))
                    return list to true
                } else {
                    list.add(SyntaxToken(i, endIdx + 3, COLOR_COMMENT))
                    i = endIdx + 3
                    continue
                }
            }
            val ch = line[i]
            if (ch == '<') {
                var j = i + 1
                if (j < len && (line[j] == '/' || line[j] == '?')) j++
                while (j < len && !line[j].isWhitespace() && line[j] != '>' && line[j] != '/') j++
                list.add(SyntaxToken(i, j, COLOR_XML_TAG, bold = true))
                i = j
                continue
            }
            if ((ch == '/' && i + 1 < len && line[i + 1] == '>') || ch == '>' || (ch == '?' && i + 1 < len && line[i + 1] == '>')) {
                val step = if (ch == '>') 1 else 2
                list.add(SyntaxToken(i, i + step, COLOR_XML_TAG, bold = true))
                i += step
                continue
            }
            if (ch == '"') {
                var j = i + 1
                while (j < len && line[j] != '"') j++
                if (j < len) j++
                list.add(SyntaxToken(i, j, COLOR_STRING))
                i = j
                continue
            }
            if (ch.isLetter()) {
                var j = i + 1
                while (j < len && (line[j].isLetterOrDigit() || line[j] == ':' || line[j] == '_' || line[j] == '-')) j++
                val isAttr = j < len && (line[j] == '=' || line.substring(i, j).contains(':'))
                list.add(SyntaxToken(i, j, if (isAttr) COLOR_XML_ATTR else defaultTextColor))
                i = j
                continue
            }
            var j = i + 1
            while (j < len && line[j] != '<' && line[j] != '>' && line[j] != '/' && line[j] != '"' && !line[j].isLetter()) j++
            list.add(SyntaxToken(i, j, defaultTextColor))
            i = j
        }
        return list to false
    }

    fun tokenizeJsonLine(line: String, defaultTextColor: Int): List<SyntaxToken> {
        val list = ArrayList<SyntaxToken>()
        val len = line.length
        var i = 0

        while (i < len) {
            val ch = line[i]
            if (ch == '"') {
                var j = i + 1
                while (j < len) {
                    if (line[j] == '\\' && j + 1 < len) { j += 2; continue }
                    if (line[j] == '"') { j++; break }
                    j++
                }
                var k = j
                while (k < len && line[k].isWhitespace()) k++
                val isKey = k < len && line[k] == ':'
                list.add(SyntaxToken(i, j, if (isKey) COLOR_XML_ATTR else COLOR_STRING))
                i = j
                continue
            }
            if (ch.isDigit() || (ch == '-' && i + 1 < len && line[i + 1].isDigit())) {
                var j = i + 1
                while (j < len && (line[j].isDigit() || line[j] == '.' || line[j] == 'e' || line[j] == 'E')) j++
                list.add(SyntaxToken(i, j, COLOR_NUMBER))
                i = j
                continue
            }
            if (ch.isLetter()) {
                var j = i + 1
                while (j < len && line[j].isLetter()) j++
                val word = line.substring(i, j)
                val c = if (word == "true" || word == "false" || word == "null") COLOR_XML_TAG else defaultTextColor
                list.add(SyntaxToken(i, j, c))
                i = j
                continue
            }
            list.add(SyntaxToken(i, i + 1, defaultTextColor))
            i++
        }
        return list
    }

    // ================= ২. ব্লক কমেন্ট স্টেট স্ক্যানার (ব্যাকগ্রাউন্ডে লাখো লাইনের জন্য) =================
    fun computeBlockCommentStartStates(lines: List<String>, isXml: Boolean): BooleanArray {
        val states = BooleanArray(lines.size)
        var inComment = false

        if (isXml) {
            for (lineIdx in lines.indices) {
                states[lineIdx] = inComment
                val line = lines[lineIdx]
                var i = 0
                val len = line.length
                while (i < len) {
                    if (inComment) {
                        val endC = line.indexOf("-->", i)
                        if (endC == -1) break
                        i = endC + 3
                        inComment = false
                    } else {
                        val startC = line.indexOf("<!--", i)
                        if (startC == -1) break
                        val endC = line.indexOf("-->", startC + 4)
                        if (endC == -1) {
                            inComment = true
                            break
                        }
                        i = endC + 3
                    }
                }
            }
            return states
        }

        for (lineIdx in lines.indices) {
            states[lineIdx] = inComment
            val line = lines[lineIdx]
            var i = 0
            val len = line.length
            while (i < len) {
                if (inComment) {
                    val endC = line.indexOf("*/", i)
                    if (endC == -1) break
                    i = endC + 2
                    inComment = false
                    continue
                }
                val ch = line[i]
                if (ch == '/' && i + 1 < len && line[i + 1] == '/') break
                if (ch == '/' && i + 1 < len && line[i + 1] == '*') {
                    val endC = line.indexOf("*/", i + 2)
                    if (endC == -1) {
                        inComment = true
                        break
                    }
                    i = endC + 2
                    continue
                }
                if (ch == '"' || ch == '\'') {
                    val quote = ch
                    i++
                    while (i < len) {
                        if (line[i] == '\\') { i += 2; continue }
                        if (line[i] == quote) { i++; break }
                        i++
                    }
                    continue
                }
                i++
            }
        }
        return states
    }

    // ================= ৩. কোড ফোল্ডিং রিজিয়ন ডিটেক্টর (২.৫ লাখ লাইন পর্যন্ত) =================
    fun computeFoldRegions(lines: List<String>, isXml: Boolean): Map<Int, Int> {
        val result = HashMap<Int, Int>()
        if (lines.size > 250_000) return result

        if (isXml) {
            val tagStack = ArrayDeque<Pair<String, Int>>()
            var inComment = false
            for (lineIdx in lines.indices) {
                val line = lines[lineIdx]
                var i = 0
                val len = line.length
                while (i < len) {
                    if (inComment) {
                        val endC = line.indexOf("-->", i)
                        if (endC == -1) break
                        i = endC + 3
                        inComment = false
                        continue
                    }
                    if (line.startsWith("<!--", i)) {
                        val endC = line.indexOf("-->", i + 4)
                        if (endC == -1) {
                            inComment = true
                            break
                        }
                        i = endC + 3
                        continue
                    }
                    if (line[i] == '<') {
                        if (i + 1 < len && line[i + 1] == '/') {
                            var j = i + 2
                            while (j < len && !line[j].isWhitespace() && line[j] != '>') j++
                            val closeTag = line.substring(i + 2, j)
                            for (idx in tagStack.lastIndex downTo 0) {
                                if (tagStack[idx].first == closeTag) {
                                    val startLine = tagStack[idx].second
                                    while (tagStack.size > idx) tagStack.removeLast()
                                    if (lineIdx > startLine && !result.containsKey(startLine)) {
                                        result[startLine] = lineIdx
                                    }
                                    break
                                }
                            }
                            i = j
                            continue
                        } else if (i + 1 < len && line[i + 1] != '?' && line[i + 1] != '!') {
                            var j = i + 1
                            while (j < len && !line[j].isWhitespace() && line[j] != '>' && line[j] != '/') j++
                            val tagName = line.substring(i + 1, j)
                            var k = j
                            var selfClosing = false
                            var inStr = false
                            while (k < len) {
                                val c = line[k]
                                if (c == '"') inStr = !inStr
                                else if (!inStr && c == '/' && k + 1 < len && line[k + 1] == '>') {
                                    selfClosing = true
                                    k += 2
                                    break
                                } else if (!inStr && c == '>') {
                                    k++
                                    break
                                }
                                k++
                            }
                            if (!selfClosing && tagName.isNotEmpty()) {
                                tagStack.addLast(tagName to lineIdx)
                            }
                            i = k
                            continue
                        }
                    }
                    i++
                }
            }
            return result
        }

        val braceStack = ArrayDeque<Int>()
        var inBlockComment = false
        for (lineIdx in lines.indices) {
            val line = lines[lineIdx]
            val len = line.length
            var i = 0
            while (i < len) {
                if (inBlockComment) {
                    val endC = line.indexOf("*/", i)
                    if (endC == -1) break
                    i = endC + 2
                    inBlockComment = false
                    continue
                }
                val ch = line[i]
                if (ch == '/' && i + 1 < len && line[i + 1] == '/') break
                if (ch == '/' && i + 1 < len && line[i + 1] == '*') {
                    val endC = line.indexOf("*/", i + 2)
                    if (endC == -1) {
                        inBlockComment = true
                        break
                    }
                    i = endC + 2
                    continue
                }
                if (ch == '"' || ch == '\'') {
                    val quote = ch
                    i++
                    while (i < len) {
                        if (line[i] == '\\') { i += 2; continue }
                        if (line[i] == quote) { i++; break }
                        i++
                    }
                    continue
                }
                if (ch == '{') {
                    braceStack.addLast(lineIdx)
                } else if (ch == '}' && braceStack.isNotEmpty()) {
                    val startLine = braceStack.removeLast()
                    if (lineIdx > startLine && !result.containsKey(startLine)) {
                        result[startLine] = lineIdx
                    }
                }
                i++
            }
        }
        return result
    }

    // ================= ৪. ব্র্যাকেট ম্যাচিং ইঞ্জিন =================
    fun findMatchingBracket(
        lines: List<String>,
        lineStarts: IntArray,
        cursorLine: Int,
        cursorCol: Int
    ): Pair<Int, Int>? {
        if (lines.isEmpty() || cursorLine !in lines.indices) return null
        val line = lines[cursorLine]
        var targetCol = -1
        if (cursorCol < line.length && isBracket(line[cursorCol])) {
            targetCol = cursorCol
        } else if (cursorCol > 0 && isBracket(line[cursorCol - 1])) {
            targetCol = cursorCol - 1
        }
        if (targetCol == -1) return null

        val bracket = line[targetCol]
        val openOffset = lineStarts[cursorLine] + targetCol
        val (matchChar, forward) = when (bracket) {
            '(' -> ')' to true
            '{' -> '}' to true
            '[' -> ']' to true
            ')' -> '(' to false
            '}' -> '{' to false
            ']' -> '[' to false
            else -> return null
        }

        var depth = 0
        var scanned = 0
        val maxScan = 5000

        if (forward) {
            var l = cursorLine
            var c = targetCol
            while (l < lines.size && scanned < maxScan) {
                val curLine = lines[l]
                while (c < curLine.length && scanned < maxScan) {
                    val ch = curLine[c]
                    if (ch == bracket) depth++
                    else if (ch == matchChar) {
                        depth--
                        if (depth == 0) return openOffset to (lineStarts[l] + c)
                    }
                    c++
                    scanned++
                }
                l++
                c = 0
                scanned++
            }
        } else {
            var l = cursorLine
            var c = targetCol
            while (l >= 0 && scanned < maxScan) {
                val curLine = lines[l]
                while (c >= 0 && scanned < maxScan) {
                    val ch = curLine[c]
                    if (ch == bracket) depth++
                    else if (ch == matchChar) {
                        depth--
                        if (depth == 0) return (lineStarts[l] + c) to openOffset
                    }
                    c--
                    scanned++
                }
                l--
                if (l >= 0) c = lines[l].length - 1
                scanned++
            }
        }
        return null
    }

    private fun isBracket(c: Char): Boolean {
        return c == '(' || c == ')' || c == '{' || c == '}' || c == '[' || c == ']'
    }

    // ================= ৫. স্মার্ট অটো-কমপ্লিট ইঞ্জিন =================
    fun getCompletions(
        lines: List<String>,
        cursorLine: Int,
        cursorCol: Int,
        filePath: String
    ): Pair<String, List<CompletionItem>> {
        if (cursorLine !in lines.indices || cursorCol <= 0) return "" to emptyList()
        val line = lines[cursorLine]
        val safeCol = cursorCol.coerceIn(0, line.length)

        var startCol = safeCol
        while (startCol > 0) {
            val c = line[startCol - 1]
            if (c.isLetterOrDigit() || c == '_' || c == ':' || c == '@' || c == '-') {
                startCol--
            } else {
                break
            }
        }
        val prefix = line.substring(startCol, safeCol)
        if (prefix.length < 2 && !prefix.startsWith("@") && !prefix.contains(":")) {
            return "" to emptyList()
        }

        val isXml = filePath.endsWith(".xml", true) || filePath.endsWith(".html", true)
        val candidates = LinkedHashMap<String, CompletionItem>()

        val scanStart = max(0, cursorLine - 150)
        val scanEnd = min(lines.lastIndex, cursorLine + 150)
        val wordRegex = "[A-Za-z_][A-Za-z0-9_]{2,}".toRegex()
        for (l in scanStart..scanEnd) {
            val lStr = lines[l]
            if (lStr.length > 1000) continue
            wordRegex.findAll(lStr).forEach { m ->
                val w = m.value
                if (w != prefix && w.startsWith(prefix, ignoreCase = true) && !candidates.containsKey(w)) {
                    val kind = if (w[0].isUpperCase()) "CL" else "ID"
                    candidates[w] = CompletionItem(w, w, kind, "In file")
                }
            }
        }

        val dictionary = if (isXml) XML_COMPLETIONS else KOTLIN_COMPOSE_COMPLETIONS
        for (item in dictionary) {
            if (item.label.startsWith(prefix, ignoreCase = true) && item.label != prefix) {
                candidates[item.label] = item
            }
        }

        val sorted = candidates.values
            .sortedWith(
                compareByDescending<CompletionItem> { it.label.startsWith(prefix, ignoreCase = false) }
                    .thenBy { it.label.length }
                    .thenBy { it.label }
            )
            .take(10)

        return prefix to sorted
    }

    val CODE_KEYWORDS = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var",
        "if", "else", "when", "for", "while", "do", "return", "override", "private",
        "public", "protected", "internal", "data", "enum", "sealed", "suspend",
        "companion", "init", "by", "as", "is", "in", "out", "try", "catch", "finally",
        "throw", "true", "false", "null", "this", "super", "void", "int", "float",
        "double", "long", "boolean", "char", "new", "extends", "implements", "static",
        "final", "include", "extern", "std", "const", "struct", "typedef", "using"
    )

    private val KOTLIN_COMPOSE_COMPLETIONS = listOf(
        CompletionItem("fun", "fun name() {\n    \n}", "SN", "Function"),
        CompletionItem("class", "class Name {\n    \n}", "SN", "Class"),
        CompletionItem("data class", "data class Name()", "SN", "Data Class"),
        CompletionItem("override fun", "override fun ", "KW", "Override"),
        CompletionItem("private", "private ", "KW", "Modifier"),
        CompletionItem("return", "return ", "KW", "Return"),
        CompletionItem("remember", "remember { mutableStateOf() }", "FN", "Compose State"),
        CompletionItem("mutableStateOf", "mutableStateOf()", "FN", "Compose"),
        CompletionItem("LaunchedEffect", "LaunchedEffect(Unit) {\n    \n}", "FN", "Side Effect"),
        CompletionItem("DisposableEffect", "DisposableEffect(Unit) {\n    onDispose { }\n}", "FN", "Side Effect"),
        CompletionItem("@Composable", "@Composable\nfun ", "SN", "Annotation"),
        CompletionItem("Column", "Column(modifier = Modifier.fillMaxSize()) {\n    \n}", "CL", "Compose UI"),
        CompletionItem("Row", "Row(verticalAlignment = Alignment.CenterVertically) {\n    \n}", "CL", "Compose UI"),
        CompletionItem("Box", "Box(modifier = Modifier.fillMaxSize()) {\n    \n}", "CL", "Compose UI"),
        CompletionItem("Text", "Text(text = \"\")", "CL", "Material3"),
        CompletionItem("Button", "Button(onClick = { }) {\n    Text(\"\")\n}", "CL", "Material3"),
        CompletionItem("Icon", "Icon(imageVector = Icons.Default., contentDescription = null)", "CL", "Material3"),
        CompletionItem("IconButton", "IconButton(onClick = { }) {\n    \n}", "CL", "Material3"),
        CompletionItem("Surface", "Surface(color = Color.Black) {\n    \n}", "CL", "Material3"),
        CompletionItem("LazyColumn", "LazyColumn {\n    items() { }\n}", "CL", "Compose List"),
        CompletionItem("LazyRow", "LazyRow {\n    items() { }\n}", "CL", "Compose List"),
        CompletionItem("Modifier", "Modifier", "CL", "Compose"),
        CompletionItem("fillMaxSize", "fillMaxSize()", "FN", "Modifier"),
        CompletionItem("fillMaxWidth", "fillMaxWidth()", "FN", "Modifier"),
        CompletionItem("padding", "padding(8.dp)", "FN", "Modifier"),
        CompletionItem("background", "background(Color.Black)", "FN", "Modifier"),
        CompletionItem("clickable", "clickable { }", "FN", "Modifier"),
        CompletionItem("RoundedCornerShape", "RoundedCornerShape(12.dp)", "CL", "Shape"),
        CompletionItem("Spacer", "Spacer(modifier = Modifier.height(8.dp))", "CL", "Compose"),
        CompletionItem("Toast", "Toast.makeText(context, \"\", Toast.LENGTH_SHORT).show()", "CL", "Android"),
        CompletionItem("println", "println()", "FN", "Kotlin"),
        CompletionItem("when", "when () {\n    else -> {}\n}", "KW", "Kotlin"),
        CompletionItem("import", "import ", "KW", "Kotlin"),
        CompletionItem("package", "package ", "KW", "Kotlin")
    )

    private val XML_COMPLETIONS = listOf(
        CompletionItem("android:layout_width", "android:layout_width=\"match_parent\"", "AT", "Attribute"),
        CompletionItem("android:layout_height", "android:layout_height=\"wrap_content\"", "AT", "Attribute"),
        CompletionItem("android:orientation", "android:orientation=\"vertical\"", "AT", "Attribute"),
        CompletionItem("android:padding", "android:padding=\"16dp\"", "AT", "Attribute"),
        CompletionItem("android:margin", "android:layout_margin=\"8dp\"", "AT", "Attribute"),
        CompletionItem("android:text", "android:text=\"\"", "AT", "Attribute"),
        CompletionItem("android:textSize", "android:textSize=\"16sp\"", "AT", "Attribute"),
        CompletionItem("android:textColor", "android:textColor=\"#FFFFFF\"", "AT", "Attribute"),
        CompletionItem("android:background", "android:background=\"#0A0A0A\"", "AT", "Attribute"),
        CompletionItem("android:id", "android:id=\"@+id/\"", "AT", "Attribute"),
        CompletionItem("android:name", "android:name=\"\"", "AT", "Manifest"),
        CompletionItem("uses-permission", "<uses-permission android:name=\"android.permission.\" />", "SN", "Manifest"),
        CompletionItem("LinearLayout", "<LinearLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\">\n</LinearLayout>", "SN", "Layout"),
        CompletionItem("TextView", "<TextView\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"Hello\" />", "SN", "Widget"),
        CompletionItem("Button", "<Button\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"Click\" />", "SN", "Widget")
    )
}