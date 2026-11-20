package com.shd.codestudio.editorscreen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class CodeEditorView(context: Context) : View(context) {

    // ================= এক্সট্রিম প্রো হাই-কনট্রাস্ট কালার প্যালেট =================
    private val bgColor = Color.parseColor("#0E0E0E")
    private val gutterBgColor = Color.parseColor("#141414")
    private val gutterDividerColor = Color.parseColor("#262626")
    private val activeLineBgColor = Color.parseColor("#19211D")
    private val activeLineBorderColor = Color.parseColor("#24D4FF33")
    private val lineNumColor = Color.parseColor("#6A9955")
    private val activeLineNumColor = Color.parseColor("#D4FF33")
    private val foldChevronColor = Color.parseColor("#88FFFFFF")
    private val foldPillBgColor = Color.parseColor("#2A3320")
    private val foldPillTextColor = Color.parseColor("#D4FF33")
    private val defaultTextColor = Color.parseColor("#F0F0F0")
    private val handleColor = Color.parseColor("#00E5FF")
    private val cursorHandleColor = Color.parseColor("#D4FF33")
    private val handleStrokeColor = Color.parseColor("#FF0A0A0A")
    private val selectionColor = Color.parseColor("#3800E5FF")
    private val bracketBgColor = Color.parseColor("#33D4FF33")
    private val bracketStrokeColor = Color.parseColor("#CCD4FF33")

    private val searchMatchBgColor = Color.parseColor("#33FFD54F")
    private val searchMatchBorderColor = Color.parseColor("#66FFD54F")
    private val activeSearchBgColor = Color.parseColor("#50D4FF33")
    private val activeSearchBorderColor = Color.parseColor("#FFD4FF33")

    private val scrollbarColor = Color.parseColor("#66FFFFFF")
    private val scrollbarActiveColor = Color.parseColor("#D4FF33")

    private val headerPurpleStart = Color.parseColor("#A855F7")
    private val headerPurpleEnd = Color.parseColor("#7C3AED")

    private val neonBlinkColorCycle = intArrayOf(
        Color.parseColor("#E5FF00"),
        Color.parseColor("#00FFFF"),
        Color.parseColor("#FF007F"),
        Color.parseColor("#00FF66"),
        Color.parseColor("#FF9900"),
        Color.parseColor("#BF00FF"),
        Color.parseColor("#00E5FF"),
        Color.parseColor("#FF3366")
    )
    private var blinkColorIndex = 0

    private val indentGuideColors = intArrayOf(
        Color.parseColor("#406A9955"),
        Color.parseColor("#4000E5FF"),
        Color.parseColor("#40A855F7"),
        Color.parseColor("#40FFB020")
    )

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = defaultTextColor
        textSize = 13f * scaledDensity
    }
    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = lineNumColor
        textAlign = Paint.Align.RIGHT
        textSize = 13f * scaledDensity
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = foldPillTextColor
        textSize = 10f * scaledDensity
    }
    private val scrollBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.WHITE
        textSize = 11f * scaledDensity
        isFakeBoldText = true
    }
    private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursorCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colorSwatchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1.2f, density)
    }
    private val loupeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1.8f, density * 1.6f)
    }
    private val handleOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = handleStrokeColor
        strokeWidth = 1.6f * density
    }
    private val linePaint = Paint().apply {
        color = gutterDividerColor
        strokeWidth = max(1f, density)
    }
    private val guidePaint = Paint().apply {
        strokeWidth = max(1f, density * 0.85f)
    }
    private val activeGuidePaint = Paint().apply {
        color = Color.parseColor("#99D4FF33")
        strokeWidth = max(1.3f, density * 1.2f)
    }

    private var textSizePx = 13f * scaledDensity
    private var lineHeight = 1f
    private var baselineOffset = 1f
    private var spaceWidth = 1f
    private var gutterWidth = 44f * density
    private var foldColumnWidth = 14f * density

    private var topPad = 14f * density
    private var bottomPad = 56f * density
    private var codeLeftPad = 8f * density
    private var codeRightPad = 80f * density

    // ================= কোটি কোটি লাইন ও বহু GB ফাইল সাপোর্টের জন্য ডকুমেন্ট ইঞ্জিন =================
    internal val doc = HugeFileDocument()
    private var maxLineWidthPx = 0f
    private var currentFilePath = ""
    private var currentDestFile: File? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val analysisGeneration = AtomicLong(0L)
    private val searchGeneration = AtomicLong(0L)
    private val wrapGeneration = AtomicLong(0L)

    internal var cursorPos = DocPos(0L, 0)
    internal var selectionAnchorPos: DocPos? = null
    internal var composingStartPos: DocPos? = null
    internal var composingEndPos: DocPos? = null
    internal var isEditableMode = false
    private var cursorVisible = true

    internal var diagnosticMessage = ""
    private var activeIndentLevel = 0
    private val colorLiteralRegex = Regex("(#[0-9A-Fa-f]{6,8}\\b|0x[0-9A-Fa-f]{8}\\b)")

    private enum class DragMode { NONE, CURSOR_HANDLE, SELECTION_START, SELECTION_END, LONG_PRESS_SELECT, FAST_SCROLL_THUMB }
    private var currentDragMode = DragMode.NONE
    private var isCursorHandleVisible = false
    private val cursorHandleAutoHideMs = 3500L

    private var longPressOriginStart = DocPos(0L, 0)
    private var longPressOriginEnd = DocPos(0L, 0)

    private var cursorHandleX = -1f
    private var cursorHandleY = -1f
    private var startHandleX = -1f
    private var startHandleY = -1f
    private var endHandleX = -1f
    private var endHandleY = -1f
    private var selectionHandlesVisible = false
    private val handlePath = Path()

    private var dragDeltaX = 0f
    private var dragDeltaY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var autoScrollActive = false

    private var showScrollBadge = false
    private val fastScrollPillRect = RectF()
    private var fastScrollTouchGrabOffsetY = 0f

    private var loupeActive = false
    private var loupeTargetDocLine = 0L
    private var loupeTargetCol = 0
    private var loupeTargetX = 0f
    private var loupeTargetY = 0f
    private val loupeClipPath = Path()
    private val loupeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88000000")
    }

    private var matchingBrackets: Pair<DocPos, DocPos>? = null
    private var foldRegions: Map<Int, Int> = emptyMap()
    private val foldedStarts = HashSet<Int>()
    private val visibleLineList = ArrayList<Int>()
    private var docLineToVisualRow = IntArray(0)
    private var blockCommentStartStates = BooleanArray(0)

    private data class WrapRow(
        val docLine: Int,
        val startCol: Int,
        val endCol: Int,
        val isFirstSeg: Boolean
    )
    private var wordWrapEnabled = false
    private var wrapRows = ArrayList<WrapRow>()
    private var docLineToFirstWrapRow = IntArray(0)

    private var searchQuery = ""
    private var searchMatchCase = false
    private var searchWholeWord = false
    private var searchUseRegex = false
    private var searchMatches = emptyList<EditorSearchMatch>()
    private var activeMatchIndex = -1

    private val tokenCache = object : LinkedHashMap<Long, List<SyntaxToken>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<SyntaxToken>>?): Boolean = size > 1500
    }

    // ৬৪-বিট ডাবল স্ক্রল পজিশন (কোটি কোটি লাইনেও কোনো ওভারফ্লো বা কাঁপুনি হবে না)
    private var scrollXPos = 0
    private var scrollYPos = 0.0
    private var flingBaseScrollY = 0.0
    private val scroller = OverScroller(context)
    private var isScaling = false
    private var wasMultiTouch = false
    private val tempRect = RectF()

    var onFontSizeChangedListener: ((Float) -> Unit)? = null
    var onCursorChangedListener: ((Long, Int) -> Unit)? = null
    var onStateChangedListener: ((Boolean, Boolean, Boolean, Long, Long, Boolean) -> Unit)? = null
    var onSearchResultsListener: ((List<EditorSearchMatch>) -> Unit)? = null
    var onSelectionMenuListener: ((Boolean, Float, Float, Boolean) -> Unit)? = null
    var onCompletionsListener: ((String, List<CompletionItem>, Float, Float) -> Unit)? = null

    private val autoSaveRunnable = Runnable {
        val target = currentDestFile
        if (target != null && !doc.isHugeMode && doc.isDirty()) {
            bgExecutor.execute {
                if (doc.saveToFile(target)) {
                    mainHandler.post {
                        doc.markSaved()
                        notifyCursorAndState()
                    }
                }
            }
        }
    }

    internal fun scheduleAutoSave() {
        if (doc.isHugeMode) return
        mainHandler.removeCallbacks(autoSaveRunnable)
        mainHandler.postDelayed(autoSaveRunnable, 1200L)
    }

    internal val hideCursorHandleRunnable = Runnable {
        if (currentDragMode != DragMode.CURSOR_HANDLE) {
            isCursorHandleVisible = false
            invalidate()
        }
    }

    private val hideScrollBadgeRunnable = Runnable {
        if (currentDragMode != DragMode.FAST_SCROLL_THUMB) {
            showScrollBadge = false
            invalidate()
        }
    }

    private val blinkRunnable = object : Runnable {
        override fun run() {
            if (isAttachedToWindow) {
                if (currentDragMode == DragMode.CURSOR_HANDLE) {
                    cursorVisible = true
                } else {
                    cursorVisible = !cursorVisible
                    if (cursorVisible) {
                        blinkColorIndex = (blinkColorIndex + 1) % neonBlinkColorCycle.size
                    }
                }
                invalidate()
                mainHandler.postDelayed(this, 480)
            } else {
                cursorVisible = true
            }
        }
    }

    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (currentDragMode == DragMode.NONE || currentDragMode == DragMode.FAST_SCROLL_THUMB || !isAttachedToWindow) {
                autoScrollActive = false
                return
            }
            var scrolled = false
            val edgeY = lineHeight * 1.4f
            val stepY = (lineHeight * 0.42).coerceAtLeast(4.0)
            if (lastTouchY > height - edgeY) {
                val oldY = scrollYPos
                scrollYPos = (scrollYPos + stepY).coerceIn(0.0, maxScrollY())
                if (scrollYPos != oldY) scrolled = true
            } else if (lastTouchY < topPad + edgeY) {
                val oldY = scrollYPos
                scrollYPos = (scrollYPos - stepY).coerceIn(0.0, maxScrollY())
                if (scrollYPos != oldY) scrolled = true
            }

            if (!wordWrapEnabled) {
                val edgeX = 28f * density
                val stepX = (14f * density).roundToInt().coerceAtLeast(4)
                if (lastTouchX > width - edgeX) {
                    val oldX = scrollXPos
                    scrollXPos = (scrollXPos + stepX).coerceIn(0, maxScrollX())
                    if (scrollXPos != oldX) scrolled = true
                } else if (lastTouchX < gutterWidth + edgeX) {
                    val oldX = scrollXPos
                    scrollXPos = (scrollXPos - stepX).coerceIn(0, maxScrollX())
                    if (scrollXPos != oldX) scrolled = true
                }
            }

            if (scrolled) {
                updateDragPosition(lastTouchX, lastTouchY)
                invalidate()
                mainHandler.postDelayed(this, 24)
            } else {
                autoScrollActive = false
            }
        }
    }

    private fun cancelGestureDetector(sourceEvent: MotionEvent?) {
        val cancelEvent = if (sourceEvent != null) {
            MotionEvent.obtain(sourceEvent).apply { action = MotionEvent.ACTION_CANCEL }
        } else {
            val now = SystemClock.uptimeMillis()
            MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        }
        gestureDetector.onTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                wasMultiTouch = true
                scroller.forceFinished(true)
                cancelGestureDetector(null)
                hideSelectionMenu()
                dismissCompletions()
                hideMagnifier()
                hideCursorHandleImmediate()
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldSize = textSizePx
                val minPx = 5f * scaledDensity
                val maxPx = 34f * scaledDensity
                val newSize = (oldSize * detector.scaleFactor).coerceIn(minPx, maxPx)
                if (abs(newSize - oldSize) > 0.01f) {
                    val ratio = (newSize / oldSize).toDouble()
                    val pivotX = (detector.focusX - gutterWidth - codeLeftPad).coerceAtLeast(0f)
                    val pivotY = (detector.focusY - topPad).coerceAtLeast(0f).toDouble()

                    textSizePx = newSize
                    maxLineWidthPx *= ratio.toFloat()
                    updateTextMetrics()
                    if (wordWrapEnabled) {
                        rebuildWrapRows(asyncIfLarge = false)
                    }

                    val newScrollX = if (wordWrapEnabled) 0 else ((scrollXPos + pivotX) * ratio - pivotX).roundToInt()
                    val newScrollY = (scrollYPos + pivotY) * ratio - pivotY
                    scrollXPos = newScrollX.coerceIn(0, maxScrollX())
                    scrollYPos = newScrollY.coerceIn(0.0, maxScrollY())

                    onFontSizeChangedListener?.invoke(newSize / scaledDensity)
                    invalidate()
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                if (wordWrapEnabled) rebuildWrapRows(asyncIfLarge = true)
                if (hasSelection()) emitSelectionMenu()
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                scroller.forceFinished(true)
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (isScaling || wasMultiTouch || e2.pointerCount > 1 || currentDragMode != DragMode.NONE) return false
                hideSelectionMenu()
                dismissCompletions()
                hideMagnifier()
                if (!wordWrapEnabled) {
                    scrollXPos = (scrollXPos + distanceX.roundToInt()).coerceIn(0, maxScrollX())
                }
                scrollYPos = (scrollYPos + distanceY.toDouble()).coerceIn(0.0, maxScrollY())
                triggerScrollBadge()
                invalidate()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (isScaling || wasMultiTouch || currentDragMode != DragMode.NONE) return false
                hideSelectionMenu()
                hideMagnifier()
                flingBaseScrollY = scrollYPos
                scroller.fling(
                    scrollXPos,
                    0,
                    if (wordWrapEnabled) 0 else -velocityX.toInt(),
                    -velocityY.toInt(),
                    0,
                    maxScrollX(),
                    -1_000_000_000,
                    1_000_000_000
                )
                triggerScrollBadge()
                postInvalidateOnAnimation()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (isScaling || wasMultiTouch) return false
                dismissCompletions()
                if (e.x <= gutterWidth) {
                    val visCount = getVisibleRowCount()
                    if (visCount > 0L) {
                        val docY = e.y.toDouble() + scrollYPos - topPad
                        val visRow = floor(docY / lineHeight).toLong().coerceIn(0L, visCount - 1L)
                        val docLine = visualRowToDocLine(visRow)
                        if (docLine <= Int.MAX_VALUE && foldRegions.containsKey(docLine.toInt())) {
                            toggleFold(docLine.toInt())
                            return true
                        }
                    }
                }

                val tapped = hitTest(e.x, e.y)
                clearSelectionInternal()
                finishComposingInternal()
                setCursorPos(tapped, ensureVisible = false)
                hideSelectionMenu()
                showCursorHandleWithTimeout()
                invalidate()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isScaling || wasMultiTouch || e.x <= gutterWidth) return false
                if (isEditableMode && !hasSelection()) {
                    requestFocus()
                    showSoftKeyboard()
                    ensureCursorVisible()
                    invalidate()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScaling || wasMultiTouch) return false
                dismissCompletions()
                hideCursorHandleImmediate()
                if (e.x <= gutterWidth) {
                    val visCount = getVisibleRowCount()
                    if (visCount > 0L) {
                        val docY = e.y.toDouble() + scrollYPos - topPad
                        val visRow = floor(docY / lineHeight).toLong().coerceIn(0L, visCount - 1L)
                        val docLine = visualRowToDocLine(visRow)
                        selectEntireLine(docLine)
                        if (hasSelection()) emitSelectionMenu()
                        invalidate()
                        return true
                    }
                    return false
                }

                val tapped = hitTest(e.x, e.y)
                selectSmartTokenAt(tapped.line, tapped.col)
                if (hasSelection()) {
                    emitSelectionMenu()
                } else {
                    showCursorHandleWithTimeout()
                }
                invalidate()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (isScaling || wasMultiTouch || e.x <= gutterWidth) return
                dismissCompletions()
                hideCursorHandleImmediate()
                hideSelectionMenu()

                val tapped = hitTest(e.x, e.y)
                selectSmartTokenAt(tapped.line, tapped.col)
                val (s, end) = getSelectionRange()
                longPressOriginStart = s
                longPressOriginEnd = end

                currentDragMode = DragMode.LONG_PRESS_SELECT
                dragDeltaX = 0f
                dragDeltaY = 0f
                lastTouchX = e.x
                lastTouchY = e.y
                updateMagnifierForPos(cursorPos)
                invalidate()
            }
        }
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        doc.onIndexingProgress = { _, _, _, _ ->
            mainHandler.post {
                updateGutterWidth()
                notifyCursorAndState()
                invalidate()
            }
        }
        updateTextMetrics()
        updateGutterWidth()
        restartCursorBlink()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartCursorBlink()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hideMagnifier()
        mainHandler.removeCallbacks(blinkRunnable)
        mainHandler.removeCallbacks(hideCursorHandleRunnable)
        mainHandler.removeCallbacks(hideScrollBadgeRunnable)
        mainHandler.removeCallbacks(autoScrollRunnable)
        mainHandler.removeCallbacks(autoSaveRunnable)
        saveToTargetIfDirtyBlocking()
    }

    fun release() {
        hideMagnifier()
        mainHandler.removeCallbacks(blinkRunnable)
        mainHandler.removeCallbacks(hideCursorHandleRunnable)
        mainHandler.removeCallbacks(hideScrollBadgeRunnable)
        mainHandler.removeCallbacks(autoScrollRunnable)
        mainHandler.removeCallbacks(autoSaveRunnable)
    }

    fun destroyEngine() {
        release()
        doc.close()
        bgExecutor.shutdownNow()
    }

    fun notifyStateNow() {
        notifyCursorAndState()
    }

    private fun triggerScrollBadge() {
        if (maxScrollY() > 0.0) {
            showScrollBadge = true
            mainHandler.removeCallbacks(hideScrollBadgeRunnable)
            mainHandler.postDelayed(hideScrollBadgeRunnable, 1400)
        }
    }

    internal fun showCursorHandleWithTimeout() {
        if (hasSelection()) {
            isCursorHandleVisible = false
            mainHandler.removeCallbacks(hideCursorHandleRunnable)
            return
        }
        isCursorHandleVisible = true
        mainHandler.removeCallbacks(hideCursorHandleRunnable)
        mainHandler.postDelayed(hideCursorHandleRunnable, cursorHandleAutoHideMs)
    }

    internal fun hideCursorHandleImmediate() {
        isCursorHandleVisible = false
        mainHandler.removeCallbacks(hideCursorHandleRunnable)
    }

    fun setFontSizeSp(sp: Float) {
        val targetPx = sp.coerceIn(5f, 34f) * scaledDensity
        if (abs(targetPx - textSizePx) > 0.5f) {
            val ratio = targetPx / textSizePx
            textSizePx = targetPx
            maxLineWidthPx *= ratio
            updateTextMetrics()
            if (wordWrapEnabled) rebuildWrapRows(asyncIfLarge = true)
            invalidate()
        }
    }

    fun syncFontSizeFromState(sp: Float) {
        if (!isScaling) {
            val targetPx = sp.coerceIn(5f, 34f) * scaledDensity
            if (abs(targetPx - textSizePx) > 0.5f) {
                val ratio = targetPx / textSizePx
                textSizePx = targetPx
                maxLineWidthPx *= ratio
                updateTextMetrics()
                if (wordWrapEnabled) rebuildWrapRows(asyncIfLarge = true)
                scrollXPos = scrollXPos.coerceIn(0, maxScrollX())
                scrollYPos = scrollYPos.coerceIn(0.0, maxScrollY())
                invalidate()
            }
        }
    }

    fun setWordWrapEnabled(enabled: Boolean) {
        if (enabled && (doc.isHugeMode || doc.lineCount > 200_000L)) {
            if (wordWrapEnabled) wordWrapEnabled = false
            Toast.makeText(context, "Word wrap disabled for huge files (>200K lines)", Toast.LENGTH_SHORT).show()
            return
        }
        if (wordWrapEnabled == enabled) return
        wordWrapEnabled = enabled
        if (enabled) {
            scrollXPos = 0
            rebuildWrapRows(asyncIfLarge = true)
        } else {
            wrapRows.clear()
            docLineToFirstWrapRow = IntArray(0)
        }
        ensureCursorVisible()
        if (hasSelection()) emitSelectionMenu()
        invalidate()
    }

    fun goToLineNumber(line1Based: Long) {
        val maxLine = (doc.lineCount - 1L).coerceAtLeast(0L)
        val targetDocLine = (line1Based - 1L).coerceIn(0L, maxLine)
        if (targetDocLine <= Int.MAX_VALUE) unfoldLineIfHidden(targetDocLine.toInt())
        clearSelectionInternal()
        hideSelectionMenu()
        setCursorPos(DocPos(targetDocLine, 0), ensureVisible = true)
        showCursorHandleWithTimeout()
        invalidate()
    }

    fun goToLineAndSelectRange(line1Based: Long, startCol: Int, endCol: Int) {
        val maxLine = (doc.lineCount - 1L).coerceAtLeast(0L)
        val targetDocLine = (line1Based - 1L).coerceIn(0L, maxLine)
        if (targetDocLine <= Int.MAX_VALUE) unfoldLineIfHidden(targetDocLine.toInt())
        val lineLen = doc.getLineLength(targetDocLine)
        val sCol = startCol.coerceIn(0, lineLen)
        val eCol = endCol.coerceIn(sCol, lineLen)
        val startPos = DocPos(targetDocLine, sCol)
        val endPos = DocPos(targetDocLine, eCol)
        if (sCol < eCol) {
            hideCursorHandleImmediate()
            selectionAnchorPos = startPos
            setCursorPos(endPos, ensureVisible = true)
            emitSelectionMenu()
        } else {
            clearSelectionInternal()
            hideSelectionMenu()
            setCursorPos(startPos, ensureVisible = true)
            showCursorHandleWithTimeout()
        }
        invalidate()
    }

    fun setEditableMode(editable: Boolean) {
        if (isEditableMode == editable) return
        isEditableMode = editable
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (editable) {
            requestFocus()
            imm?.restartInput(this)
            post {
                showSoftKeyboard()
                ensureCursorVisible()
                if (hasSelection()) emitSelectionMenu()
                invalidate()
            }
        } else {
            finishComposingInternal()
            dismissCompletions()
            imm?.hideSoftInputFromWindow(windowToken, 0)
            clearFocus()
            if (hasSelection()) emitSelectionMenu()
            invalidate()
        }
        restartCursorBlink()
    }

    private fun showSoftKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun restartCursorBlink() {
        mainHandler.removeCallbacks(blinkRunnable)
        cursorVisible = true
        if (isAttachedToWindow) {
            mainHandler.postDelayed(blinkRunnable, 480)
        }
    }

    private fun updateMagnifierForPos(pos: DocPos) {
        val visRow = docLineColToVisRow(pos.line, pos.col).coerceAtLeast(0L)
        val lineStr = doc.getLine(pos.line)
        val segStart = if (wordWrapEnabled && visRow in wrapRows.indices) wrapRows[visRow.toInt()].startCol else 0
        loupeTargetDocLine = pos.line
        loupeTargetCol = pos.col.coerceIn(0, lineStr.length)
        loupeTargetX = gutterWidth + codeLeftPad + measureLineWidth(lineStr, segStart, loupeTargetCol) - scrollXPos
        loupeTargetY = (topPad + visRow.toDouble() * lineHeight - scrollYPos + lineHeight * 0.5).toFloat()
        loupeActive = true
    }

    private fun hideMagnifier() {
        loupeActive = false
    }

    private fun splitTextFast(text: String): ArrayList<String> {
        val sanitized = if (text.contains('\r')) {
            text.replace("\r\n", "\n").replace("\r", "\n")
        } else {
            text
        }
        val result = ArrayList<String>()
        var pos = 0
        val len = sanitized.length
        while (pos <= len) {
            val nl = sanitized.indexOf('\n', pos)
            if (nl == -1) {
                result.add(sanitized.substring(pos))
                break
            } else {
                result.add(sanitized.substring(pos, nl))
                pos = nl + 1
            }
        }
        if (result.isEmpty()) result.add("")
        return result
    }

    fun isDocumentDirty(): Boolean = doc.isDirty()

    fun saveToTargetIfDirtyBlocking(): Boolean {
        val target = currentDestFile ?: return false
        if (doc.isHugeMode || !doc.isDirty()) return false
        return if (doc.saveToFile(target)) {
            doc.markSaved()
            notifyCursorAndState()
            true
        } else {
            false
        }
    }

    fun loadDocumentText(text: String, filePath: String, destFile: File? = null) {
        loadDocumentLines(splitTextFast(text), filePath, destFile)
    }

    fun loadDocumentLines(newLines: List<String>, filePath: String, destFile: File? = null) {
        currentFilePath = filePath
        currentDestFile = destFile
        doc.loadFromLines(newLines, destFile)
        resetViewAfterDocumentLoad()
    }

    fun loadLargeFile(filePath: String, destFile: File) {
        currentFilePath = filePath
        currentDestFile = destFile
        if (wordWrapEnabled) {
            wordWrapEnabled = false
            wrapRows.clear()
            docLineToFirstWrapRow = IntArray(0)
        }
        doc.loadFromFile(destFile)
        resetViewAfterDocumentLoad()
    }

    private fun resetViewAfterDocumentLoad() {
        foldedStarts.clear()
        foldRegions = emptyMap()
        visibleLineList.clear()
        docLineToVisualRow = IntArray(0)
        blockCommentStartStates = BooleanArray(0)
        tokenCache.clear()
        diagnosticMessage = ""
        activeIndentLevel = 0

        clearSelectionInternal()
        finishComposingInternal()
        hideSelectionMenu()
        dismissCompletions()
        hideMagnifier()
        hideCursorHandleImmediate()
        cursorPos = DocPos(0L, 0)
        scrollXPos = 0
        scrollYPos = 0.0
        scroller.forceFinished(true)

        recomputeInitialMaxWidth()
        updateGutterWidth()
        if (wordWrapEnabled) rebuildWrapRows(asyncIfLarge = true)
        scheduleBackgroundAnalysis()
        updateMatchingBrackets()
        updateActiveIndentLevel()
        if (searchQuery.isNotBlank()) computeSearchMatchesAsync()
        else {
            searchMatches = emptyList()
            activeMatchIndex = -1
            onSearchResultsListener?.invoke(emptyList())
        }

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.restartInput(this)
        notifyCursorAndState()
        invalidate()
    }

    private fun recomputeInitialMaxWidth() {
        val sampleLines = min(doc.lineCount, 1500L)
        var maxW = 0f
        for (i in 0L until sampleLines) {
            val line = doc.getLine(i)
            val w = if (line.length > 4000) 4000f * spaceWidth else measureLineWidth(line, 0, line.length)
            if (w > maxW) maxW = w
        }
        maxLineWidthPx = maxW
    }

    fun saveDocumentToFile(destFile: File): Boolean = doc.saveToFile(destFile)

    fun getFullText(): String = doc.getFullText(maxChars = 2_000_000)

    fun markSaved() {
        doc.markSaved()
        notifyCursorAndState()
    }

    fun getSelectedOrAllText(): Pair<String, Boolean> {
        if (hasSelection()) {
            val (s, e) = getSelectionRange()
            return doc.getSubstring(s, e, maxChars = 400_000) to true
        }
        return doc.getFullText(maxChars = 400_000) to false
    }

    fun getSelectedTextOrNull(): String? {
        if (!hasSelection()) return null
        val (s, e) = getSelectionRange()
        if (e.line - s.line > 2L) return null
        val txt = doc.getSubstring(s, e, maxChars = 250)
        return if (txt.contains('\n') || txt.length > 200) null else txt
    }

    fun cutSelectionToClipboard() {
        if (!hasSelection()) return
        val (s, e) = getSelectionRange()
        if (e.line - s.line > 10_000L) {
            Toast.makeText(context, "Selection too large to cut", Toast.LENGTH_SHORT).show()
            return
        }
        val txt = doc.getSubstring(s, e, maxChars = 400_000)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Code", txt))
        applyEdit(s, e, "")
        clearSelectionInternal()
        hideSelectionMenu()
        hideCursorHandleImmediate()
        setCursorPos(s, ensureVisible = true)
        invalidate()
    }

    fun copyToClipboard(): Boolean {
        val isSel = hasSelection()
        if (!isSel && (doc.isHugeMode || doc.approxTotalChars > 400_000L)) {
            Toast.makeText(context, "File too large to copy all (>400KB). Select a portion instead.", Toast.LENGTH_SHORT).show()
            return false
        }
        val txt = if (isSel) {
            val (s, e) = getSelectionRange()
            if (e.line - s.line > 10_000L) {
                Toast.makeText(context, "Selection too large (>10,000 lines)", Toast.LENGTH_SHORT).show()
                return true
            }
            doc.getSubstring(s, e, maxChars = 400_000)
        } else {
            doc.getFullText(maxChars = 400_000)
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Code", txt))
        if (isSel) {
            clearSelectionInternal()
            hideSelectionMenu()
            updateActiveIndentLevel()
            invalidate()
        }
        return isSel
    }

    fun pasteFromClipboard(): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
        if (clip.isNullOrEmpty()) return false
        insertTextAtCursor(clip)
        return true
    }

    fun selectAllText() {
        val maxLine = (doc.lineCount - 1L).coerceAtLeast(0L)
        val lastCol = doc.getLineLength(maxLine)
        if (maxLine == 0L && lastCol == 0) return
        unfoldAllRegions()
        hideCursorHandleImmediate()
        selectionAnchorPos = DocPos(0L, 0)
        setCursorPos(DocPos(maxLine, lastCol), ensureVisible = false)
        emitSelectionMenu()
        invalidate()
    }

    fun selectCurrentLine() {
        selectEntireLine(cursorPos.line)
        if (hasSelection()) emitSelectionMenu()
        invalidate()
    }

    private fun selectEntireLine(docLine: Long) {
        val safeLine = docLine.coerceIn(0L, (doc.lineCount - 1L).coerceAtLeast(0L))
        hideCursorHandleImmediate()
        val len = doc.getLineLength(safeLine)
        if (len > 0) {
            selectionAnchorPos = DocPos(safeLine, 0)
            setCursorPos(DocPos(safeLine, len), ensureVisible = false)
        } else {
            clearSelectionInternal()
            setCursorPos(DocPos(safeLine, 0), ensureVisible = false)
        }
    }

    fun moveCursorStep(dxChars: Int, dyLines: Int) {
        clearSelectionInternal()
        hideSelectionMenu()
        hideCursorHandleImmediate()
        if (dyLines != 0) {
            val maxL = (doc.lineCount - 1L).coerceAtLeast(0L)
            val targetLine = (cursorPos.line + dyLines).coerceIn(0L, maxL)
            val targetCol = cursorPos.col.coerceAtMost(doc.getLineLength(targetLine))
            setCursorPos(DocPos(targetLine, targetCol), ensureVisible = true)
        } else if (dxChars != 0) {
            setCursorPos(stepPos(cursorPos, dxChars), ensureVisible = true)
        }
        invalidate()
    }

    fun moveToLineEdge(toEnd: Boolean) {
        clearSelectionInternal()
        hideSelectionMenu()
        hideCursorHandleImmediate()
        val lineStr = doc.getLine(cursorPos.line)
        val targetCol = if (toEnd) {
            lineStr.length
        } else {
            val firstNonSpace = lineStr.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) 0 else it }
            if (cursorPos.col != firstNonSpace) firstNonSpace else 0
        }
        setCursorPos(DocPos(cursorPos.line, targetCol), ensureVisible = true)
        invalidate()
    }

    fun moveCurrentLine(direction: Int) {
        val (sLine, eLine) = if (hasSelection()) {
            val (s, e) = getSelectionRange()
            s.line to e.line
        } else {
            cursorPos.line to cursorPos.line
        }
        if (eLine - sLine > 2000L) return

        val maxLine = (doc.lineCount - 1L).coerceAtLeast(0L)
        if (direction < 0 && sLine > 0L) {
            val prevLine = doc.getLine(sLine - 1L)
            val startPos = DocPos(sLine - 1L, 0)
            val endPos = DocPos(eLine, doc.getLineLength(eLine))
            val movedBlock = (sLine..eLine).joinToString("\n") { doc.getLine(it) }
            val replacement = "$movedBlock\n$prevLine"
            val oldCol = cursorPos.col
            val oldRelLine = cursorPos.line - sLine
            applyEdit(startPos, endPos, replacement)
            clearSelectionInternal()
            hideSelectionMenu()
            hideCursorHandleImmediate()
            val targetL = sLine - 1L + oldRelLine
            setCursorPos(DocPos(targetL, oldCol.coerceAtMost(doc.getLineLength(targetL))), ensureVisible = true)
            invalidate()
        } else if (direction > 0 && eLine < maxLine) {
            val nextLine = doc.getLine(eLine + 1L)
            val startPos = DocPos(sLine, 0)
            val endPos = DocPos(eLine + 1L, nextLine.length)
            val movedBlock = (sLine..eLine).joinToString("\n") { doc.getLine(it) }
            val replacement = "$nextLine\n$movedBlock"
            val oldCol = cursorPos.col
            val oldRelLine = cursorPos.line - sLine
            applyEdit(startPos, endPos, replacement)
            clearSelectionInternal()
            hideSelectionMenu()
            hideCursorHandleImmediate()
            val targetL = sLine + 1L + oldRelLine
            setCursorPos(DocPos(targetL, oldCol.coerceAtMost(doc.getLineLength(targetL))), ensureVisible = true)
            invalidate()
        }
    }

    fun deleteCurrentLine() {
        val (sLine, eLine) = if (hasSelection()) {
            val (s, e) = getSelectionRange()
            s.line to e.line
        } else {
            cursorPos.line to cursorPos.line
        }
        val maxLine = (doc.lineCount - 1L).coerceAtLeast(0L)
        hideSelectionMenu()
        hideCursorHandleImmediate()
        clearSelectionInternal()

        if (sLine == 0L && eLine == maxLine) {
            applyEdit(DocPos(0L, 0), DocPos(maxLine, doc.getLineLength(maxLine)), "")
            setCursorPos(DocPos(0L, 0), ensureVisible = true)
        } else if (eLine < maxLine) {
            applyEdit(DocPos(sLine, 0), DocPos(eLine + 1L, 0), "")
            setCursorPos(DocPos(sLine, 0), ensureVisible = true)
        } else {
            val prevLine = (sLine - 1L).coerceAtLeast(0L)
            val prevLen = doc.getLineLength(prevLine)
            applyEdit(DocPos(prevLine, prevLen), DocPos(eLine, doc.getLineLength(eLine)), "")
            setCursorPos(DocPos(prevLine, 0), ensureVisible = true)
        }
        invalidate()
    }

    fun unindentCurrentLine() {
        val (sLine, eLine) = if (hasSelection()) {
            val (s, e) = getSelectionRange()
            s.line to e.line
        } else {
            cursorPos.line to cursorPos.line
        }
        if (eLine - sLine > 2000L) return

        var removedOnCursorLine = 0
        val sb = StringBuilder()
        for (i in sLine..eLine) {
            if (i > sLine) sb.append('\n')
            val lineStr = doc.getLine(i)
            val removeCnt = when {
                lineStr.startsWith("\t") -> 1
                else -> lineStr.take(4).takeWhile { it == ' ' }.length
            }
            if (i == cursorPos.line) removedOnCursorLine = removeCnt
            sb.append(lineStr.substring(removeCnt))
        }

        val startPos = DocPos(sLine, 0)
        val endPos = DocPos(eLine, doc.getLineLength(eLine))
        val targetCol = (cursorPos.col - removedOnCursorLine).coerceAtLeast(0)
        val targetLine = cursorPos.line
        applyEdit(startPos, endPos, sb.toString())
        clearSelectionInternal()
        hideSelectionMenu()
        hideCursorHandleImmediate()
        setCursorPos(DocPos(targetLine, targetCol.coerceAtMost(doc.getLineLength(targetLine))), ensureVisible = true)
        invalidate()
    }
    fun formatDocument() {
        if (doc.isHugeMode || doc.lineCount > 100_000L) {
            Toast.makeText(context, "Format disabled for huge files (>100K lines)", Toast.LENGTH_SHORT).show()
            return
        }
        val snapshot = doc.getAllLinesIfSmall(100_000) ?: return
        if (snapshot.isEmpty()) return
        val isXml = currentFilePath.endsWith(".xml", true) || currentFilePath.endsWith(".html", true)
        val formatted = ArrayList<String>(snapshot.size)
        var changed = false

        if (isXml) {
            var depth = 0
            for (raw in snapshot) {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) {
                    if (raw.isNotEmpty()) changed = true
                    formatted.add("")
                    continue
                }
                val isClosingTag = trimmed.startsWith("</")
                if (isClosingTag) depth = max(0, depth - 1)
                val outLine = "    ".repeat(depth) + trimmed
                if (outLine != raw) changed = true
                formatted.add(outLine)

                val isOpenTag = trimmed.startsWith("<") && !trimmed.startsWith("</") &&
                    !trimmed.startsWith("<?") && !trimmed.startsWith("<!")
                val isSelfClosing = trimmed.endsWith("/>") || (isOpenTag && trimmed.contains("</"))
                if (isOpenTag && !isSelfClosing) depth++
            }
        } else {
            var braceDepth = 0
            for (raw in snapshot) {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) {
                    if (raw.isNotEmpty()) changed = true
                    formatted.add("")
                    continue
                }
                var leadingCloses = 0
                for (c in trimmed) {
                    if (c == '}' || c == ')') leadingCloses++ else break
                }
                val lineIndent = max(0, braceDepth - leadingCloses)
                val outLine = "    ".repeat(lineIndent) + trimmed
                if (outLine != raw) changed = true
                formatted.add(outLine)

                var inStr = false
                var strChar = '"'
                var idx = 0
                while (idx < trimmed.length) {
                    val ch = trimmed[idx]
                    if (!inStr && ch == '/' && idx + 1 < trimmed.length && trimmed[idx + 1] == '/') break
                    if (ch == '\\' && inStr) { idx += 2; continue }
                    if (ch == '"' || ch == '\'') {
                        if (!inStr) { inStr = true; strChar = ch }
                        else if (ch == strChar) inStr = false
                    } else if (!inStr) {
                        if (ch == '{') braceDepth++
                        else if (ch == '}') braceDepth = max(0, braceDepth - 1)
                    }
                    idx++
                }
            }
        }

        if (!changed) {
            Toast.makeText(context, "Already formatted!", Toast.LENGTH_SHORT).show()
            return
        }

        val curLine = cursorPos.line
        val lastLine = (doc.lineCount - 1L).coerceAtLeast(0L)
        applyEdit(DocPos(0L, 0), DocPos(lastLine, doc.getLineLength(lastLine)), formatted.joinToString("\n"))
        clearSelectionInternal()
        hideSelectionMenu()
        hideCursorHandleImmediate()
        val safeLine = curLine.coerceIn(0L, (doc.lineCount - 1L).coerceAtLeast(0L))
        val firstNonWs = doc.getLine(safeLine).indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        setCursorPos(DocPos(safeLine, firstNonWs), ensureVisible = true)
        Toast.makeText(context, "Code Formatted!", Toast.LENGTH_SHORT).show()
        invalidate()
    }

    fun duplicateCurrentLine() {
        val (sLine, eLine) = if (hasSelection()) {
            val (s, e) = getSelectionRange()
            s.line to e.line
        } else {
            cursorPos.line to cursorPos.line
        }
        if (eLine - sLine > 2000L) return

        val blockSb = StringBuilder()
        for (i in sLine..eLine) {
            if (i > sLine) blockSb.append('\n')
            blockSb.append(doc.getLine(i))
        }
        val endCol = doc.getLineLength(eLine)
        val insertPos = DocPos(eLine, endCol)
        val newCursor = applyEdit(insertPos, insertPos, "\n$blockSb")
        clearSelectionInternal()
        hideSelectionMenu()
        hideCursorHandleImmediate()
        setCursorPos(newCursor, ensureVisible = true)
        invalidate()
    }

    fun toggleCurrentLineComment() {
        val isXml = currentFilePath.endsWith(".xml", true) || currentFilePath.endsWith(".html", true)
        val (sLine, eLine) = if (hasSelection()) {
            val (s, e) = getSelectionRange()
            s.line to e.line
        } else {
            cursorPos.line to cursorPos.line
        }
        if (eLine - sLine > 2000L) return

        val sb = StringBuilder()
        if (isXml) {
            for (i in sLine..eLine) {
                if (i > sLine) sb.append('\n')
                val lineStr = doc.getLine(i)
                val trimmed = lineStr.trim()
                if (trimmed.startsWith("<!--") && trimmed.endsWith("-->") && trimmed.length >= 7) {
                    val indent = lineStr.takeWhile { it == ' ' || it == '\t' }
                    val inner = trimmed.removePrefix("<!--").removeSuffix("-->").trim()
                    sb.append(indent).append(inner)
                } else if (trimmed.isNotEmpty()) {
                    val indent = lineStr.takeWhile { it == ' ' || it == '\t' }
                    sb.append(indent).append("<!-- ").append(trimmed).append(" -->")
                } else {
                    sb.append(lineStr)
                }
            }
        } else {
            var allCommented = true
            for (i in sLine..eLine) {
                val trimmed = doc.getLine(i).trimStart()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("//")) {
                    allCommented = false
                    break
                }
            }
            for (i in sLine..eLine) {
                if (i > sLine) sb.append('\n')
                val lineStr = doc.getLine(i)
                if (lineStr.isBlank()) {
                    sb.append(lineStr)
                } else if (allCommented) {
                    val idx = lineStr.indexOf("//")
                    val removeLen = if (idx + 2 < lineStr.length && lineStr[idx + 2] == ' ') 3 else 2
                    sb.append(lineStr.removeRange(idx, idx + removeLen))
                } else {
                    val indent = lineStr.takeWhile { it == ' ' || it == '\t' }
                    val rest = lineStr.substring(indent.length)
                    sb.append(indent).append("// ").append(rest)
                }
            }
        }

        val startPos = DocPos(sLine, 0)
        val endPos = DocPos(eLine, doc.getLineLength(eLine))
        val newCursor = applyEdit(startPos, endPos, sb.toString())
        clearSelectionInternal()
        hideSelectionMenu()
        hideCursorHandleImmediate()
        setCursorPos(newCursor, ensureVisible = true)
        invalidate()
    }

    fun applyAutoCompletion(prefixLength: Int, insertText: String) {
        finishComposingInternal()
        val endPos = cursorPos
        val startPos = stepPos(endPos, -prefixLength)
        val caretAdjustFromEnd = when {
            insertText.endsWith("(\n    \n)") -> -2
            insertText.endsWith("{\n    \n}") -> -2
            insertText.endsWith("()") -> -1
            insertText.endsWith("\"\"") -> -1
            else -> 0
        }
        val afterPos = applyEdit(startPos, endPos, insertText)
        clearSelectionInternal()
        dismissCompletions()
        hideCursorHandleImmediate()
        setCursorPos(stepPos(afterPos, caretAdjustFromEnd), ensureVisible = true)
        invalidate()
    }

    fun dismissCompletions() {
        onCompletionsListener?.invoke("", emptyList(), 0f, 0f)
    }

    fun insertTextAtCursor(snippet: String) {
        val (s, e) = getSelectionRange()
        val endAfter = applyEdit(s, e, snippet)
        clearSelectionInternal()
        hideSelectionMenu()
        hideCursorHandleImmediate()
        setCursorPos(endAfter, ensureVisible = true)
        invalidate()
    }

    fun clearDocument() {
        val maxLine = (doc.lineCount - 1L).coerceAtLeast(0L)
        val lastLen = doc.getLineLength(maxLine)
        if (maxLine == 0L && lastLen == 0) return
        applyEdit(DocPos(0L, 0), DocPos(maxLine, lastLen), "")
        clearSelectionInternal()
        finishComposingInternal()
        hideSelectionMenu()
        dismissCompletions()
        hideCursorHandleImmediate()
        setCursorPos(DocPos(0L, 0), ensureVisible = true)
        invalidate()
    }

    internal fun applyEdit(
        start: DocPos,
        end: DocPos,
        replacement: String,
        isComposing: Boolean = false
    ): DocPos {
        val newEnd = doc.replaceRange(start, end, replacement, cursorPos, isComposing = isComposing)
        tokenCache.clear()
        updateGutterWidth()

        val lineStr = doc.getLine(newEnd.line)
        if (lineStr.length <= 4000) {
            val w = measureLineWidth(lineStr, 0, lineStr.length)
            if (w > maxLineWidthPx) maxLineWidthPx = w
        }

        if (foldedStarts.isNotEmpty()) rebuildFoldVisibility()
        if (wordWrapEnabled) rebuildWrapRows(asyncIfLarge = doc.lineCount > 4000L)
        scheduleBackgroundAnalysis()
        if (searchQuery.isNotBlank()) computeSearchMatchesAsync()
        scheduleAutoSave()
        return newEnd
    }

    fun undo() {
        val targetPos = doc.undo() ?: return
        restoreAfterUndoRedo(targetPos)
    }

    fun redo() {
        val targetPos = doc.redo() ?: return
        restoreAfterUndoRedo(targetPos)
    }

    private fun restoreAfterUndoRedo(targetPos: DocPos) {
        clearSelectionInternal()
        finishComposingInternal()
        hideSelectionMenu()
        dismissCompletions()
        hideCursorHandleImmediate()
        tokenCache.clear()
        updateGutterWidth()
        if (foldedStarts.isNotEmpty()) rebuildFoldVisibility()
        if (wordWrapEnabled) rebuildWrapRows(asyncIfLarge = true)
        scheduleBackgroundAnalysis()
        setCursorPos(clampPos(targetPos), ensureVisible = true)
        if (searchQuery.isNotBlank()) computeSearchMatchesAsync()
        scheduleAutoSave()

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.restartInput(this)
        invalidate()
    }

    private fun scheduleBackgroundAnalysis() {
        if (doc.isHugeMode || doc.lineCount > 200_000L) {
            analysisGeneration.incrementAndGet()
            blockCommentStartStates = BooleanArray(0)
            foldRegions = emptyMap()
            foldedStarts.clear()
            visibleLineList.clear()
            docLineToVisualRow = IntArray(0)
            diagnosticMessage = ""
            return
        }

        val gen = analysisGeneration.incrementAndGet()
        val snapshot = doc.getAllLinesIfSmall(200_000) ?: return
        val isXml = currentFilePath.endsWith(".xml", true) || currentFilePath.endsWith(".html", true)

        bgExecutor.execute {
            if (analysisGeneration.get() != gen) return@execute
            val commentStates = CodeLanguageEngine.computeBlockCommentStartStates(snapshot, isXml)
            if (analysisGeneration.get() != gen) return@execute
            val folds = CodeLanguageEngine.computeFoldRegions(snapshot, isXml)
            if (analysisGeneration.get() != gen) return@execute
            val diag = computeSyntaxDiagnostics(snapshot, isXml)
            if (analysisGeneration.get() != gen) return@execute

            mainHandler.post {
                if (analysisGeneration.get() != gen) return@post
                blockCommentStartStates = commentStates
                foldRegions = folds
                diagnosticMessage = diag
                foldedStarts.retainAll(folds.keys)
                rebuildFoldVisibility()
                if (wordWrapEnabled) rebuildWrapRows(asyncIfLarge = true)
                tokenCache.clear()
                notifyCursorAndState()
                invalidate()
            }
        }
    }

    private fun computeSyntaxDiagnostics(snapshot: List<String>, isXml: Boolean): String {
        if (isXml || snapshot.size > 50_000) return ""
        val stack = ArrayDeque<Pair<Char, Int>>()
        var inBlockComment = false
        for (lineIdx in snapshot.indices) {
            val line = snapshot[lineIdx]
            var i = 0
            val len = line.length
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
                    if (endC == -1) { inBlockComment = true; break }
                    i = endC + 2
                    continue
                }
                if (ch == '"' || ch == '\'') {
                    val q = ch
                    i++
                    while (i < len) {
                        if (line[i] == '\\') { i += 2; continue }
                        if (line[i] == q) { i++; break }
                        i++
                    }
                    continue
                }
                when (ch) {
                    '{', '(', '[' -> stack.addLast(ch to (lineIdx + 1))
                    '}', ')', ']' -> {
                        val expected = when (ch) { '}' -> '{'; ')' -> '('; else -> '[' }
                        if (stack.isEmpty()) return "Ln ${lineIdx + 1}: Extra '$ch'"
                        val top = stack.removeLast()
                        if (top.first != expected) return "Ln ${lineIdx + 1}: Expected match for '${top.first}'"
                    }
                }
                i++
            }
        }
        return if (stack.isNotEmpty()) {
            val unclosed = stack.last()
            "Ln ${unclosed.second}: Unclosed '${unclosed.first}'"
        } else ""
    }

    private fun rebuildWrapRows(asyncIfLarge: Boolean) {
        if (!wordWrapEnabled) return
        if (doc.isHugeMode || doc.lineCount > 200_000L) {
            wordWrapEnabled = false
            wrapRows.clear()
            docLineToFirstWrapRow = IntArray(0)
            return
        }
        val snapshot = doc.getAllLinesIfSmall(200_000) ?: return
        val wrapWidthPx = max(60f, width - gutterWidth - codeLeftPad - 12f * density)
        val docCount = if (foldedStarts.isEmpty()) snapshot.size else visibleLineList.size

        if (!asyncIfLarge || docCount <= 4000) {
            val built = computeWrapRowsSync(snapshot, visibleLineList, foldedStarts.isNotEmpty(), wrapWidthPx, codePaint)
            wrapRows = built.first
            docLineToFirstWrapRow = built.second
            return
        }

        val gen = wrapGeneration.incrementAndGet()
        val visCopy = ArrayList(visibleLineList)
        val hasFolds = foldedStarts.isNotEmpty()
        val measurePaint = Paint(codePaint)

        bgExecutor.execute {
            if (wrapGeneration.get() != gen) return@execute
            val built = computeWrapRowsSync(snapshot, visCopy, hasFolds, wrapWidthPx, measurePaint)
            if (wrapGeneration.get() != gen) return@execute
            mainHandler.post {
                if (wrapGeneration.get() != gen || !wordWrapEnabled) return@post
                wrapRows = built.first
                docLineToFirstWrapRow = built.second
                scrollYPos = scrollYPos.coerceIn(0.0, maxScrollY())
                invalidate()
            }
        }
    }

    private fun computeWrapRowsSync(
        srcLines: List<String>,
        srcVisList: List<Int>,
        hasFolds: Boolean,
        wrapWidthPx: Float,
        paint: Paint
    ): Pair<ArrayList<WrapRow>, IntArray> {
        val out = ArrayList<WrapRow>(srcLines.size)
        val firstRowMap = IntArray(srcLines.size) { -1 }
        val count = if (hasFolds) srcVisList.size else srcLines.size
        val estAsciiFit = max(8, (wrapWidthPx / max(1f, paint.measureText("M"))).toInt())

        for (v in 0 until count) {
            val docLine = if (hasFolds) srcVisList[v] else v
            val lineStr = srcLines[docLine]
            firstRowMap[docLine] = out.size

            if (lineStr.isEmpty()) {
                out.add(WrapRow(docLine, 0, 0, true))
                continue
            }

            if (lineStr.length < estAsciiFit - 2 && !lineStr.contains('\t') && isPureBasicAscii(lineStr)) {
                out.add(WrapRow(docLine, 0, lineStr.length, true))
                continue
            }

            var col = 0
            while (col < lineStr.length) {
                val fitCount = measureWrappedSlice(lineStr, col, wrapWidthPx, paint)
                val endCol = (col + fitCount).coerceAtMost(lineStr.length)
                out.add(WrapRow(docLine, col, endCol, col == 0))
                col = endCol
            }
        }
        return out to firstRowMap
    }

    private fun isPureBasicAscii(s: String): Boolean {
        for (i in s.indices) {
            if (s[i].code > 126) return false
        }
        return true
    }

    private fun measureWrappedSlice(line: String, startCol: Int, maxWidthPx: Float, paint: Paint): Int {
        val remaining = line.length - startCol
        if (remaining <= 0) return 0

        var hasTab = false
        for (i in startCol until line.length) {
            if (line[i] == '\t') { hasTab = true; break }
        }
        val rawFit = if (!hasTab) {
            paint.breakText(line, startCol, line.length, true, maxWidthPx, null).coerceAtLeast(1)
        } else {
            val tabW = paint.measureText(" ") * 4f
            var curW = 0f
            var cnt = 0
            for (i in startCol until line.length) {
                val cw = if (line[i] == '\t') tabW else paint.measureText(line, i, i + 1)
                if (curW + cw > maxWidthPx && cnt > 0) break
                curW += cw
                cnt++
            }
            cnt.coerceAtLeast(1)
        }

        if (startCol + rawFit >= line.length) return remaining

        val minBacktrack = startCol + (rawFit * 0.55f).toInt()
        for (k in (startCol + rawFit - 1) downTo minBacktrack) {
            val c = line[k]
            if (c == ' ' || c == ',' || c == ';' || c == '.' || c == '(' || c == '/' || c == '=') {
                return (k - startCol + 1).coerceAtLeast(1)
            }
        }
        return rawFit
    }

    private fun getVisibleRowCount(): Long {
        if (wordWrapEnabled && wrapRows.isNotEmpty()) return wrapRows.size.toLong()
        return if (foldedStarts.isEmpty()) doc.lineCount else visibleLineList.size.toLong()
    }

    private fun visualRowToDocLine(visRow: Long): Long {
        if (wordWrapEnabled && wrapRows.isNotEmpty()) {
            return wrapRows[visRow.toInt().coerceIn(0, wrapRows.lastIndex)].docLine.toLong()
        }
        return if (foldedStarts.isEmpty()) {
            visRow.coerceIn(0L, (doc.lineCount - 1L).coerceAtLeast(0L))
        } else {
            visibleLineList[visRow.toInt().coerceIn(0, visibleLineList.lastIndex)].toLong()
        }
    }

    private fun docLineColToVisRow(docLine: Long, col: Int): Long {
        if (wordWrapEnabled && wrapRows.isNotEmpty()) {
            val dInt = docLine.toInt()
            val firstIdx = docLineToFirstWrapRow.getOrElse(dInt) { -1 }
            if (firstIdx < 0) return -1L
            var r = firstIdx
            while (r < wrapRows.size && wrapRows[r].docLine == dInt) {
                val seg = wrapRows[r]
                val isLastSegOfLine = (r + 1 >= wrapRows.size || wrapRows[r + 1].docLine != dInt)
                if (col < seg.endCol || isLastSegOfLine) return r.toLong()
                r++
            }
            return firstIdx.toLong()
        }
        return if (foldedStarts.isEmpty()) {
            docLine.coerceIn(0L, (doc.lineCount - 1L).coerceAtLeast(0L))
        } else {
            val dInt = docLine.toInt()
            if (dInt in docLineToVisualRow.indices) docLineToVisualRow[dInt].toLong() else -1L
        }
    }

    private fun toggleFold(startLine: Int) {
        if (foldedStarts.contains(startLine)) {
            foldedStarts.remove(startLine)
        } else if (foldRegions.containsKey(startLine)) {
            foldedStarts.add(startLine)
            val endLine = foldRegions[startLine] ?: startLine
            if (cursorPos.line in (startLine + 1L)..endLine.toLong()) {
                clearSelectionInternal()
                setCursorPos(DocPos(startLine.toLong(), doc.getLineLength(startLine.toLong())), ensureVisible = false)
            }
        }
        rebuildFoldVisibility()
        if (wordWrapEnabled) rebuildWrapRows(asyncIfLarge = true)
        scrollYPos = scrollYPos.coerceIn(0.0, maxScrollY())
        invalidate()
    }

    fun foldAllRegions() {
        if (foldRegions.isEmpty()) return
        foldedStarts.clear()
        foldedStarts.addAll(foldRegions.keys)
        rebuildFoldVisibility()
        if (wordWrapEnabled) rebuildWrapRows(asyncIfLarge = true)
        if (docLineColToVisRow(cursorPos.line, 0) == -1L) {
            clearSelectionInternal()
            setCursorPos(DocPos(0L, 0), ensureVisible = false)
        }
        scrollYPos = scrollYPos.coerceIn(0.0, maxScrollY())
        invalidate()
    }

    fun unfoldAllRegions() {
        if (foldedStarts.isEmpty()) return
        foldedStarts.clear()
        rebuildFoldVisibility()
        if (wordWrapEnabled) rebuildWrapRows(asyncIfLarge = true)
        scrollYPos = scrollYPos.coerceIn(0.0, maxScrollY())
        invalidate()
    }

    private fun rebuildFoldVisibility() {
        if (foldedStarts.isEmpty() || doc.lineCount > 200_000L) {
            visibleLineList.clear()
            docLineToVisualRow = IntArray(0)
            return
        }
        val total = doc.lineCount.toInt()
        visibleLineList.clear()
        docLineToVisualRow = IntArray(total) { -1 }
        var i = 0
        while (i < total) {
            docLineToVisualRow[i] = visibleLineList.size
            visibleLineList.add(i)
            val foldEnd = if (foldedStarts.contains(i)) foldRegions[i] else null
            if (foldEnd != null && foldEnd > i && foldEnd < total) {
                i = foldEnd + 1
            } else {
                i++
            }
        }
    }

    private fun unfoldLineIfHidden(docLine: Int) {
        if (foldedStarts.isEmpty()) return
        if (docLine in docLineToVisualRow.indices && docLineToVisualRow[docLine] != -1) return
        val toUnfold = foldedStarts.filter { s ->
            val e = foldRegions[s] ?: s
            docLine in (s + 1)..e
        }
        if (toUnfold.isNotEmpty()) {
            foldedStarts.removeAll(toUnfold.toSet())
            rebuildFoldVisibility()
            if (wordWrapEnabled) rebuildWrapRows(asyncIfLarge = false)
        }
    }

    private fun buildSearchRegex(query: String, matchCase: Boolean, wholeWord: Boolean, useRegex: Boolean): Regex? {
        if (query.isBlank()) return null
        return try {
            val base = if (useRegex) query else Regex.escape(query)
            val finalPattern = if (wholeWord) "\\b(?:$base)\\b" else base
            val opts = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(finalPattern, opts)
        } catch (_: Exception) {
            null
        }
    }

    fun updateSearchQuery(query: String, matchCase: Boolean = false, wholeWord: Boolean = false, useRegex: Boolean = false) {
        searchQuery = query
        searchMatchCase = matchCase
        searchWholeWord = wholeWord
        searchUseRegex = useRegex
        activeMatchIndex = -1
        computeSearchMatchesAsync()
    }

    private fun computeSearchMatchesAsync() {
        val query = searchQuery
        val regex = buildSearchRegex(query, searchMatchCase, searchWholeWord, searchUseRegex)
        if (query.isBlank() || regex == null) {
            searchGeneration.incrementAndGet()
            searchMatches = emptyList()
            activeMatchIndex = -1
            onSearchResultsListener?.invoke(emptyList())
            invalidate()
            return
        }
        val gen = searchGeneration.incrementAndGet()
        bgExecutor.execute {
            val list = doc.streamSearch(regex, 2500) { searchGeneration.get() != gen }
            if (searchGeneration.get() != gen) return@execute
            mainHandler.post {
                if (searchGeneration.get() != gen) return@post
                searchMatches = list
                onSearchResultsListener?.invoke(list)
                invalidate()
            }
        }
    }

    fun jumpToSearchMatch(match: EditorSearchMatch, matchIndex: Int) {
        activeMatchIndex = matchIndex
        if (match.line <= Int.MAX_VALUE) unfoldLineIfHidden(match.line.toInt())
        hideCursorHandleImmediate()
        selectionAnchorPos = DocPos(match.line, match.startCol)
        setCursorPos(DocPos(match.line, match.endCol), ensureVisible = false)

        val visRow = docLineColToVisRow(match.line, match.startCol).coerceAtLeast(0L)
        val lineTargetY = topPad + visRow.toDouble() * lineHeight
        val desiredScrollY = (lineTargetY - height * 0.35).coerceIn(0.0, maxScrollY())
        scrollYPos = desiredScrollY
        ensureCursorVisible()
        invalidate()
    }

    fun replaceActiveMatch(query: String, replacement: String, matchCase: Boolean = false, wholeWord: Boolean = false, useRegex: Boolean = false) {
        if (query.isBlank() || searchMatches.isEmpty()) return
        val safeIdx = activeMatchIndex.coerceIn(0, searchMatches.lastIndex)
        val m = searchMatches[safeIdx]
        val newPos = applyEdit(DocPos(m.line, m.startCol), DocPos(m.line, m.endCol), replacement)
        clearSelectionInternal()
        setCursorPos(newPos, ensureVisible = true)
        updateSearchQuery(query, matchCase, wholeWord, useRegex)
        invalidate()
    }

    fun replaceAllMatches(query: String, replacement: String, matchCase: Boolean = false, wholeWord: Boolean = false, useRegex: Boolean = false): Int {
        val regex = buildSearchRegex(query, matchCase, wholeWord, useRegex) ?: return 0
        val count = doc.replaceAll(regex, replacement, useRegex, cursorPos)
        if (count > 0) {
            tokenCache.clear()
            updateGutterWidth()
            if (wordWrapEnabled) rebuildWrapRows(asyncIfLarge = true)
            scheduleBackgroundAnalysis()
            clearSelectionInternal()
            hideCursorHandleImmediate()
            setCursorPos(DocPos(0L, 0), ensureVisible = true)
            updateSearchQuery(query, matchCase, wholeWord, useRegex)
            scheduleAutoSave()
            invalidate()
        }
        return count
    }

    fun jumpToTop() {
        scroller.forceFinished(true)
        clearSelectionInternal()
        hideSelectionMenu()
        hideCursorHandleImmediate()
        setCursorPos(DocPos(0L, 0), ensureVisible = false)
        scrollXPos = 0
        scrollYPos = 0.0
        triggerScrollBadge()
        invalidate()
    }

    fun jumpToBottom() {
        scroller.forceFinished(true)
        clearSelectionInternal()
        hideSelectionMenu()
        hideCursorHandleImmediate()
        val maxLine = (doc.lineCount - 1L).coerceAtLeast(0L)
        setCursorPos(DocPos(maxLine, doc.getLineLength(maxLine)), ensureVisible = false)
        scrollXPos = 0
        scrollYPos = maxScrollY()
        triggerScrollBadge()
        invalidate()
    }

    fun scrollByDelta(dyPx: Int) {
        scroller.forceFinished(true)
        hideSelectionMenu()
        scrollYPos = (scrollYPos + dyPx.toDouble()).coerceIn(0.0, maxScrollY())
        triggerScrollBadge()
        invalidate()
    }

    private fun updateTextMetrics() {
        codePaint.textSize = textSizePx
        gutterPaint.textSize = textSizePx
        badgePaint.textSize = max(8f * scaledDensity, textSizePx * 0.78f)
        val fm = codePaint.fontMetrics
        val naturalHeight = fm.descent - fm.ascent
        lineHeight = max(naturalHeight * 1.35f, textSizePx * 1.5f)
        baselineOffset = ((lineHeight - naturalHeight) / 2f) - fm.ascent
        spaceWidth = max(1f, codePaint.measureText(" "))
        updateGutterWidth()
    }

    private fun updateGutterWidth() {
        val digits = max(2, doc.lineCount.toString().length)
        val digitsWidth = gutterPaint.measureText("9".repeat(digits))
        gutterWidth = max(32f * density, digitsWidth + foldColumnWidth + 12f * density)
    }

    private fun maxScrollX(): Int {
        if (wordWrapEnabled) return 0
        val viewportCodeW = max(0f, width - gutterWidth - codeLeftPad)
        return max(0f, maxLineWidthPx + codeRightPad - viewportCodeW).roundToInt()
    }

    private fun maxScrollY(): Double {
        val contentH = topPad.toDouble() + getVisibleRowCount().toDouble() * lineHeight.toDouble() + bottomPad.toDouble()
        return max(0.0, contentH - height.toDouble())
    }

    internal fun clampPos(pos: DocPos): DocPos {
        val maxL = (doc.lineCount - 1L).coerceAtLeast(0L)
        val l = pos.line.coerceIn(0L, maxL)
        val c = pos.col.coerceIn(0, doc.getLineLength(l))
        return DocPos(l, c)
    }

    internal fun stepPos(pos: DocPos, deltaChars: Int): DocPos {
        var cur = clampPos(pos)
        if (deltaChars == 0) return cur
        if (deltaChars > 0) {
            var rem = deltaChars
            val maxL = (doc.lineCount - 1L).coerceAtLeast(0L)
            while (rem > 0) {
                val len = doc.getLineLength(cur.line)
                val avail = len - cur.col
                if (rem <= avail) {
                    return DocPos(cur.line, cur.col + rem)
                }
                if (cur.line >= maxL) {
                    return DocPos(maxL, len)
                }
                rem -= (avail + 1)
                cur = DocPos(cur.line + 1L, 0)
            }
            return cur
        } else {
            var rem = -deltaChars
            while (rem > 0) {
                if (rem <= cur.col) {
                    return DocPos(cur.line, cur.col - rem)
                }
                if (cur.line <= 0L) {
                    return DocPos(0L, 0)
                }
                rem -= (cur.col + 1)
                val prevLine = cur.line - 1L
                cur = DocPos(prevLine, doc.getLineLength(prevLine))
            }
            return cur
        }
    }

    internal fun hasSelection(): Boolean = selectionAnchorPos != null && selectionAnchorPos != cursorPos

    internal fun getSelectionRange(): Pair<DocPos, DocPos> {
        val anchor = selectionAnchorPos ?: return cursorPos to cursorPos
        return if (anchor <= cursorPos) anchor to cursorPos else cursorPos to anchor
    }

    internal fun clearSelectionInternal() {
        selectionAnchorPos = null
        selectionHandlesVisible = false
    }

    internal fun finishComposingInternal() {
        composingStartPos = null
        composingEndPos = null
        doc.finishComposing()
    }

    internal fun getLineLocalSelectionForIme(): Triple<String, Int, Int> {
        val lineStr = doc.getLine(cursorPos.line)
        if (!hasSelection()) {
            val c = cursorPos.col.coerceIn(0, lineStr.length)
            return Triple(lineStr, c, c)
        }
        val (s, e) = getSelectionRange()
        val sCol = if (s.line == cursorPos.line) s.col.coerceIn(0, lineStr.length) else 0
        val eCol = if (e.line == cursorPos.line) e.col.coerceIn(0, lineStr.length) else lineStr.length
        return Triple(lineStr, sCol, max(sCol, eCol))
    }

    internal fun setCursorPos(newPos: DocPos, ensureVisible: Boolean) {
        cursorPos = clampPos(newPos)
        if (cursorPos.line <= Int.MAX_VALUE) unfoldLineIfHidden(cursorPos.line.toInt())
        updateMatchingBrackets()
        updateActiveIndentLevel()
        restartCursorBlink()
        if (ensureVisible) ensureCursorVisible()
        notifyCursorAndState()

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val (_, selS, selE) = getLineLocalSelectionForIme()
        val cs = composingStartPos
        val ce = composingEndPos
        val compS = if (cs != null && cs.line == cursorPos.line) cs.col else -1
        val compE = if (ce != null && ce.line == cursorPos.line) ce.col else -1
        imm?.updateSelection(this, selS, selE, compS, compE)
    }

    private fun updateMatchingBrackets() {
        val lineIdx = cursorPos.line
        val line = doc.getLine(lineIdx)
        val cursorCol = cursorPos.col.coerceIn(0, line.length)
        var targetCol = -1
        if (cursorCol < line.length && isBracketChar(line[cursorCol])) {
            targetCol = cursorCol
        } else if (cursorCol > 0 && isBracketChar(line[cursorCol - 1])) {
            targetCol = cursorCol - 1
        }
        if (targetCol == -1) {
            matchingBrackets = null
            return
        }

        val bracket = line[targetCol]
        val openPos = DocPos(lineIdx, targetCol)
        val (matchChar, forward) = when (bracket) {
            '(' -> ')' to true
            '{' -> '}' to true
            '[' -> ']' to true
            ')' -> '(' to false
            '}' -> '{' to false
            ']' -> '[' to false
            else -> {
                matchingBrackets = null
                return
            }
        }

        var depth = 0
        var scanned = 0
        val maxScan = 5000
        val maxLine = (doc.lineCount - 1L).coerceAtLeast(0L)

        if (forward) {
            var l = lineIdx
            var c = targetCol
            while (l <= maxLine && scanned < maxScan) {
                val curLine = doc.getLine(l)
                while (c < curLine.length && scanned < maxScan) {
                    val ch = curLine[c]
                    if (ch == bracket) depth++
                    else if (ch == matchChar) {
                        depth--
                        if (depth == 0) {
                            matchingBrackets = openPos to DocPos(l, c)
                            return
                        }
                    }
                    c++
                    scanned++
                }
                l++
                c = 0
                scanned++
            }
        } else {
            var l = lineIdx
            var c = targetCol
            while (l >= 0L && scanned < maxScan) {
                val curLine = doc.getLine(l)
                while (c >= 0 && scanned < maxScan) {
                    val ch = curLine[c]
                    if (ch == bracket) depth++
                    else if (ch == matchChar) {
                        depth--
                        if (depth == 0) {
                            matchingBrackets = DocPos(l, c) to openPos
                            return
                        }
                    }
                    c--
                    scanned++
                }
                l--
                if (l >= 0L) c = doc.getLineLength(l) - 1
                scanned++
            }
        }
        matchingBrackets = null
    }

    private fun isBracketChar(c: Char): Boolean {
        return c == '(' || c == ')' || c == '{' || c == '}' || c == '[' || c == ']'
    }

    private fun updateActiveIndentLevel() {
        val lineStr = doc.getLine(cursorPos.line)
        var spaces = 0
        for (c in lineStr) {
            if (c == ' ') spaces++
            else if (c == '\t') spaces += 4
            else break
        }
        activeIndentLevel = spaces / 4
    }

    internal fun triggerCompletionsIfTyping() {
        if (!isEditableMode) {
            dismissCompletions()
            return
        }
        val windowStart = (cursorPos.line - 150L).coerceAtLeast(0L)
        val windowEnd = (cursorPos.line + 150L).coerceAtMost((doc.lineCount - 1L).coerceAtLeast(0L))
        val (windowLines, _) = doc.getLinesWindow(windowStart, windowEnd)
        val localLine = (cursorPos.line - windowStart).toInt().coerceIn(0, windowLines.lastIndex)

        val (prefix, items) = CodeLanguageEngine.getCompletions(windowLines, localLine, cursorPos.col, currentFilePath)
        if (items.isEmpty()) {
            dismissCompletions()
            return
        }
        val visRow = docLineColToVisRow(cursorPos.line, cursorPos.col).coerceAtLeast(0L)
        val segStart = if (wordWrapEnabled && visRow in wrapRows.indices) wrapRows[visRow.toInt()].startCol else 0
        val cursorX = gutterWidth + codeLeftPad + measureLineWidth(doc.getLine(cursorPos.line), segStart, cursorPos.col) - scrollXPos
        val cursorBottomY = (topPad + (visRow + 1L).toDouble() * lineHeight - scrollYPos).toFloat()
        onCompletionsListener?.invoke(prefix, items, cursorX, cursorBottomY)
    }

    fun ensureCursorVisible() {
        if (width <= 0 || height <= 0) return
        if (cursorPos.line <= Int.MAX_VALUE) unfoldLineIfHidden(cursorPos.line.toInt())
        val visRow = docLineColToVisRow(cursorPos.line, cursorPos.col).coerceAtLeast(0L)
        val segStart = if (wordWrapEnabled && visRow in wrapRows.indices) wrapRows[visRow.toInt()].startCol else 0
        val cursorTop = topPad.toDouble() + visRow.toDouble() * lineHeight
        val cursorBottom = cursorTop + lineHeight

        val viewportH = height.toDouble()
        val vMargin = (38f * density).toDouble()

        if (cursorBottom + vMargin > scrollYPos + viewportH) {
            scrollYPos = (cursorBottom + vMargin - viewportH).coerceIn(0.0, maxScrollY())
        } else if (cursorTop - topPad - vMargin < scrollYPos) {
            scrollYPos = max(0.0, cursorTop - topPad - vMargin).coerceIn(0.0, maxScrollY())
        }

        if (wordWrapEnabled) {
            scrollXPos = 0
        } else {
            val lineStr = doc.getLine(cursorPos.line)
            val cursorX = codeLeftPad + measureLineWidth(lineStr, segStart, cursorPos.col)
            val viewportCodeW = max(1f, width - gutterWidth)
            val hMargin = 28f * density
            if (cursorX + hMargin > scrollXPos + viewportCodeW) {
                scrollXPos = (cursorX + hMargin - viewportCodeW).roundToInt().coerceIn(0, maxScrollX())
            } else if (cursorX - hMargin < scrollXPos) {
                scrollXPos = max(0f, cursorX - hMargin).roundToInt().coerceIn(0, maxScrollX())
            }
        }
    }

    private fun computeSelectionLength(): Long {
        if (!hasSelection()) return 0L
        val (s, e) = getSelectionRange()
        if (s.line == e.line) return (e.col - s.col).toLong()
        val span = e.line - s.line
        if (span > 2000L) {
            return span * 40L
        }
        var total = (doc.getLineLength(s.line) - s.col).toLong() + 1L
        for (l in (s.line + 1L) until e.line) {
            total += doc.getLineLength(l).toLong() + 1L
        }
        total += e.col.toLong()
        return total
    }

    private fun notifyCursorAndState() {
        onCursorChangedListener?.invoke(cursorPos.line + 1L, cursorPos.col + 1)
        onStateChangedListener?.invoke(
            doc.isDirty(),
            doc.canUndo(),
            doc.canRedo(),
            computeSelectionLength(),
            doc.lineCount,
            doc.isHugeMode
        )
    }

    internal fun emitSelectionMenu() {
        if (!hasSelection()) {
            hideSelectionMenu()
            return
        }
        val (s, e) = getSelectionRange()
        val visS = docLineColToVisRow(s.line, s.col).coerceAtLeast(0L)
        val visE = docLineColToVisRow(e.line, e.col).coerceAtLeast(0L)

        val segStartS = if (wordWrapEnabled && visS in wrapRows.indices) wrapRows[visS.toInt()].startCol else 0
        val segStartE = if (wordWrapEnabled && visE in wrapRows.indices) wrapRows[visE.toInt()].startCol else 0

        val x1 = gutterWidth + codeLeftPad + measureLineWidth(doc.getLine(s.line), segStartS, s.col) - scrollXPos
        val x2 = gutterWidth + codeLeftPad + measureLineWidth(doc.getLine(e.line), segStartE, e.col) - scrollXPos
        val midX = ((x1 + x2) / 2f).coerceIn(gutterWidth, width.toFloat())
        val topY = (topPad + min(visS, visE).toDouble() * lineHeight - scrollYPos).toFloat()
        onSelectionMenuListener?.invoke(true, midX, topY, true)
    }

    internal fun hideSelectionMenu() {
        onSelectionMenuListener?.invoke(false, 0f, 0f, false)
    }

    private fun measureLineWidth(line: CharSequence, start: Int, end: Int): Float {
        val s = start.coerceIn(0, line.length)
        val e = end.coerceIn(s, line.length)
        if (s >= e) return 0f

        var hasTab = false
        for (i in s until e) {
            if (line[i] == '\t') { hasTab = true; break }
        }
        if (!hasTab) return codePaint.measureText(line, s, e)

        var w = 0f
        var segStart = s
        val tabW = spaceWidth * 4f
        for (i in s until e) {
            if (line[i] == '\t') {
                if (i > segStart) w += codePaint.measureText(line, segStart, i)
                w += tabW
                segStart = i + 1
            }
        }
        if (e > segStart) w += codePaint.measureText(line, segStart, e)
        return w
    }

    private fun drawLineSegment(
        canvas: Canvas,
        line: CharSequence,
        start: Int,
        end: Int,
        startX: Float,
        baselineY: Float,
        paint: Paint
    ): Float {
        val s = start.coerceIn(0, line.length)
        val e = end.coerceIn(s, line.length)
        if (s >= e) return 0f

        var hasTab = false
        for (i in s until e) {
            if (line[i] == '\t') { hasTab = true; break }
        }
        if (!hasTab) {
            canvas.drawText(line, s, e, startX, baselineY, paint)
            return paint.measureText(line, s, e)
        }

        var curX = startX
        var segStart = s
        val tabW = spaceWidth * 4f
        for (i in s until e) {
            if (line[i] == '\t') {
                if (i > segStart) {
                    canvas.drawText(line, segStart, i, curX, baselineY, paint)
                    curX += paint.measureText(line, segStart, i)
                }
                curX += tabW
                segStart = i + 1
            }
        }
        if (e > segStart) {
            canvas.drawText(line, segStart, e, curX, baselineY, paint)
            curX += paint.measureText(line, segStart, e)
        }
        return curX - startX
    }

    private fun hitTest(touchX: Float, touchY: Float): DocPos {
        val visCount = getVisibleRowCount()
        if (visCount == 0L) return DocPos(0L, 0)
        val docY = touchY.toDouble() + scrollYPos - topPad
        val visRow = floor(docY / lineHeight).toLong().coerceIn(0L, visCount - 1L)

        val lineIdx: Long
        val segStart: Int
        val segEnd: Int
        if (wordWrapEnabled && visRow in wrapRows.indices) {
            val row = wrapRows[visRow.toInt()]
            lineIdx = row.docLine.toLong()
            segStart = row.startCol
            segEnd = row.endCol
        } else {
            lineIdx = visualRowToDocLine(visRow)
            segStart = 0
            segEnd = doc.getLineLength(lineIdx)
        }

        val targetX = (touchX + scrollXPos - gutterWidth - codeLeftPad).coerceAtLeast(0f)
        val line = doc.getLine(lineIdx)
        if (segStart >= segEnd || targetX <= 0f) return DocPos(lineIdx, segStart)

        var low = segStart
        var high = segEnd.coerceAtMost(line.length)
        while (low < high) {
            val mid = (low + high) ushr 1
            if (measureLineWidth(line, segStart, mid) < targetX) low = mid + 1 else high = mid
        }
        val prevCol = (low - 1).coerceAtLeast(segStart)
        val prevW = measureLineWidth(line, segStart, prevCol)
        val currW = measureLineWidth(line, segStart, low)
        val bestCol = if (abs(targetX - prevW) < abs(currW - targetX)) prevCol else low
        return DocPos(lineIdx, bestCol.coerceIn(segStart, segEnd.coerceAtMost(line.length)))
    }

    private fun selectSmartTokenAt(lineIdx: Long, col: Int) {
        val line = doc.getLine(lineIdx)
        if (line.isEmpty()) {
            clearSelectionInternal()
            setCursorPos(DocPos(lineIdx, 0), ensureVisible = false)
            return
        }
        val safeCol = if (col >= line.length) line.lastIndex else col.coerceIn(0, line.lastIndex)
        val ch = line[safeCol]
        var s = safeCol
        var e = safeCol + 1

        when {
            ch.isLetterOrDigit() || ch == '_' -> {
                while (s > 0 && (line[s - 1].isLetterOrDigit() || line[s - 1] == '_')) s--
                while (e < line.length && (line[e].isLetterOrDigit() || line[e] == '_')) e++
            }
            ch.isWhitespace() -> {
                while (s > 0 && line[s - 1].isWhitespace()) s--
                while (e < line.length && line[e].isWhitespace()) e++
            }
            else -> {
                val multiOps = setOf('=', '!', '<', '>', '&', '|', '+', '-', ':', '*', '/')
                if (ch in multiOps) {
                    while (s > 0 && line[s - 1] in multiOps) s--
                    while (e < line.length && line[e] in multiOps) e++
                }
            }
        }
        selectionAnchorPos = DocPos(lineIdx, s)
        setCursorPos(DocPos(lineIdx, e), ensureVisible = false)
    }

    private fun hitTestHandles(x: Float, y: Float): DragMode {
        val hitRadius = 40f * density
        val handleCenterDrop = 16f * density

        if (showScrollBadge && fastScrollPillRect.contains(x, y)) {
            return DragMode.FAST_SCROLL_THUMB
        }

        if (selectionHandlesVisible && hasSelection()) {
            val dStart = hypot(x - startHandleX, y - (startHandleY + handleCenterDrop))
            val dEnd = hypot(x - endHandleX, y - (endHandleY + handleCenterDrop))
            if (dStart <= hitRadius && dStart <= dEnd) return DragMode.SELECTION_START
            if (dEnd <= hitRadius) return DragMode.SELECTION_END
        } else if (!hasSelection() && isCursorHandleVisible && cursorHandleX >= 0f) {
            val dCursor = hypot(x - cursorHandleX, y - (cursorHandleY + handleCenterDrop))
            if (dCursor <= hitRadius) return DragMode.CURSOR_HANDLE
        }
        return DragMode.NONE
    }

    private fun getTokensForLine(lineIdx: Long): List<SyntaxToken> {
        tokenCache[lineIdx]?.let { return it }
        val lineStr = doc.getLine(lineIdx)
        if (lineStr.length > 4000) return emptyList()
        val isXml = currentFilePath.endsWith(".xml", true) || currentFilePath.endsWith(".html", true)
        val isJson = currentFilePath.endsWith(".json", true)
        val startInComment = if (lineIdx < blockCommentStartStates.size) blockCommentStartStates[lineIdx.toInt()] else false
        val tokens = when {
            isXml -> CodeLanguageEngine.tokenizeXmlLine(lineStr, startInComment, defaultTextColor).first
            isJson -> CodeLanguageEngine.tokenizeJsonLine(lineStr, defaultTextColor)
            else -> CodeLanguageEngine.tokenizeCodeLine(lineStr, startInComment, defaultTextColor).first
        }
        tokenCache[lineIdx] = tokens
        return tokens
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.drawColor(bgColor)

        val visCount = getVisibleRowCount()
        if (doc.lineCount <= 0L || visCount <= 0L) {
            canvas.restore()
            return
        }

        val firstVisRow = floor((scrollYPos - topPad) / lineHeight).toLong().coerceIn(0L, visCount - 1L)
        val lastVisRow = ceil((scrollYPos + height - topPad) / lineHeight).toLong().coerceIn(firstVisRow, visCount - 1L)

        val cursorVisRow = docLineColToVisRow(cursorPos.line, cursorPos.col)
        val hasSel = hasSelection()
        val (selStart, selEnd) = getSelectionRange()
        selectionHandlesVisible = false
        cursorHandleX = -1f
        cursorHandleY = -1f

        canvas.save()
        canvas.clipRect(gutterWidth, 0f, width.toFloat(), height.toFloat())

        val indentStepPx = spaceWidth * 4f

        for (visRow in firstVisRow..lastVisRow) {
            val lineIdx: Long
            val segStart: Int
            val segEnd: Int
            val isFirstSeg: Boolean
            val isLastSeg: Boolean

            if (wordWrapEnabled && visRow in wrapRows.indices) {
                val rIdx = visRow.toInt()
                val row = wrapRows[rIdx]
                lineIdx = row.docLine.toLong()
                segStart = row.startCol
                segEnd = row.endCol
                isFirstSeg = row.isFirstSeg
                isLastSeg = (rIdx + 1 >= wrapRows.size || wrapRows[rIdx + 1].docLine != row.docLine)
            } else {
                lineIdx = visualRowToDocLine(visRow)
                segStart = 0
                segEnd = doc.getLineLength(lineIdx)
                isFirstSeg = true
                isLastSeg = true
            }

            val lineTop = (topPad + visRow.toDouble() * lineHeight - scrollYPos).toFloat()
            val lineBottom = lineTop + lineHeight
            val baseline = lineTop + baselineOffset
            val line = doc.getLine(lineIdx)

            // আন-ইনডেক্সড বড় লাইন স্ক্রল করার সাথে সাথে maxLineWidthPx ডায়নামিক আপডেট
            if (!wordWrapEnabled && line.length <= 8000) {
                val lw = measureLineWidth(line, 0, line.length)
                if (lw > maxLineWidthPx) maxLineWidthPx = lw
            }

            if (lineIdx == cursorPos.line && !hasSel) {
                rectPaint.color = activeLineBgColor
                canvas.drawRect(gutterWidth, lineTop, width.toFloat(), lineBottom, rectPaint)
                linePaint.color = activeLineBorderColor
                canvas.drawLine(gutterWidth, lineTop, width.toFloat(), lineTop, linePaint)
                linePaint.color = gutterDividerColor
                canvas.drawLine(gutterWidth, lineBottom, width.toFloat(), lineBottom, linePaint)
            }

            if (isFirstSeg) {
                var leadingSpaces = 0
                for (c in 0 until line.length) {
                    val ch = line[c]
                    if (ch == ' ') leadingSpaces++
                    else if (ch == '\t') leadingSpaces += 4
                    else break
                }
                val guideLevels = min(leadingSpaces / 4, 24)
                for (lvl in 1..guideLevels) {
                    val gx = gutterWidth + codeLeftPad + lvl * indentStepPx - scrollXPos
                    if (gx > gutterWidth && gx < width) {
                        if (lvl == activeIndentLevel && activeIndentLevel > 0) {
                            canvas.drawLine(gx, lineTop, gx, lineBottom, activeGuidePaint)
                        } else {
                            guidePaint.color = indentGuideColors[(lvl - 1) % indentGuideColors.size]
                            canvas.drawLine(gx, lineTop, gx, lineBottom, guidePaint)
                        }
                    }
                }
            }

            if (hasSel && selEnd.line >= lineIdx && selStart.line <= lineIdx) {
                val selStartCol = if (selStart.line == lineIdx) selStart.col.coerceIn(segStart, segEnd) else segStart
                val selEndCol = if (selEnd.line == lineIdx) selEnd.col.coerceIn(segStart, segEnd) else segEnd
                val x1 = gutterWidth + codeLeftPad + measureLineWidth(line, segStart, selStartCol) - scrollXPos
                val x2 = if (selEnd.line > lineIdx || selEnd.col > segEnd) {
                    gutterWidth + codeLeftPad + measureLineWidth(line, segStart, segEnd) + spaceWidth - scrollXPos
                } else {
                    gutterWidth + codeLeftPad + measureLineWidth(line, segStart, selEndCol) - scrollXPos
                }
                if (x2 >= x1) {
                    rectPaint.color = selectionColor
                    canvas.drawRect(x1, lineTop, max(x1 + 3f, x2), lineBottom, rectPaint)
                }
            }

            val mb = matchingBrackets
            if (mb != null) {
                for (bPos in listOf(mb.first, mb.second)) {
                    if (bPos.line == lineIdx && bPos.col in segStart until segEnd) {
                        val bx1 = gutterWidth + codeLeftPad + measureLineWidth(line, segStart, bPos.col) - scrollXPos
                        val bx2 = gutterWidth + codeLeftPad + measureLineWidth(line, segStart, bPos.col + 1) - scrollXPos
                        rectPaint.color = bracketBgColor
                        tempRect.set(bx1, lineTop + 1f, bx2, lineBottom - 1f)
                        canvas.drawRoundRect(tempRect, 3f, 3f, rectPaint)
                        strokePaint.color = bracketStrokeColor
                        canvas.drawRoundRect(tempRect, 3f, 3f, strokePaint)
                    }
                }
            }

            if (searchMatches.isNotEmpty()) {
                for (mIdx in searchMatches.indices) {
                    val m = searchMatches[mIdx]
                    if (m.line != lineIdx) continue
                    val localS = m.startCol.coerceIn(segStart, segEnd)
                    val localE = m.endCol.coerceIn(segStart, segEnd)
                    if (localS < localE) {
                        val x1 = gutterWidth + codeLeftPad + measureLineWidth(line, segStart, localS) - scrollXPos
                        val x2 = gutterWidth + codeLeftPad + measureLineWidth(line, segStart, localE) - scrollXPos
                        val isActive = (mIdx == activeMatchIndex)

                        rectPaint.color = if (isActive) activeSearchBgColor else searchMatchBgColor
                        tempRect.set(x1, lineTop + 1.5f, x2, lineBottom - 1.5f)
                        canvas.drawRoundRect(tempRect, 3f * density, 3f * density, rectPaint)

                        strokePaint.color = if (isActive) activeSearchBorderColor else searchMatchBorderColor
                        strokePaint.strokeWidth = if (isActive) 1.5f * density else 0.9f * density
                        canvas.drawRoundRect(tempRect, 3f * density, 3f * density, strokePaint)
                    }
                }
            }

            val tokens = getTokensForLine(lineIdx)
            var drawX = gutterWidth + codeLeftPad - scrollXPos
            if (tokens.isEmpty()) {
                codePaint.color = defaultTextColor
                codePaint.isFakeBoldText = false
                // অতি দীর্ঘ সিঙ্গেল লাইন সেফটি স্লাইস
                val safeEnd = if (segEnd - segStart > 16_384) min(segEnd, segStart + 16_384) else segEnd
                drawX += drawLineSegment(canvas, line, segStart, safeEnd, drawX, baseline, codePaint)
            } else {
                for (token in tokens) {
                    val ts = max(token.start, segStart)
                    val te = min(token.end, segEnd)
                    if (ts < te && drawX < width) {
                        codePaint.color = token.color
                        codePaint.isFakeBoldText = token.bold
                        drawX += drawLineSegment(canvas, line, ts, te, drawX, baseline, codePaint)
                    }
                }
                codePaint.isFakeBoldText = false
            }

            if (line.length <= 1000 && (line.indexOf('#') != -1 || line.indexOf("0x", ignoreCase = true) != -1)) {
                for (cm in colorLiteralRegex.findAll(line)) {
                    val cs = cm.range.first
                    val ce = cm.range.last + 1
                    if (ce <= segStart || cs >= segEnd) continue
                    val rawColor = cm.value
                    val parsed = try {
                        if (rawColor.startsWith("#")) Color.parseColor(rawColor)
                        else rawColor.substring(2).toLong(16).toInt()
                    } catch (_: Exception) {
                        null
                    }
                    if (parsed != null) {
                        val ls = max(cs, segStart)
                        val le = min(ce, segEnd)
                        val cx1 = gutterWidth + codeLeftPad + measureLineWidth(line, segStart, ls) - scrollXPos
                        val cx2 = gutterWidth + codeLeftPad + measureLineWidth(line, segStart, le) - scrollXPos
                        colorSwatchPaint.color = parsed or (0xFF shl 24)
                        tempRect.set(cx1, baseline + 2f * density, cx2, baseline + 4.2f * density)
                        canvas.drawRoundRect(tempRect, 1.5f * density, 1.5f * density, colorSwatchPaint)
                    }
                }
            }

            if (isLastSeg && lineIdx <= Int.MAX_VALUE && foldedStarts.contains(lineIdx.toInt())) {
                val endLine = foldRegions[lineIdx.toInt()] ?: lineIdx.toInt()
                val hiddenCount = (endLine - lineIdx.toInt()).coerceAtLeast(1)
                val badgeText = " ... +$hiddenCount "
                val badgeW = badgePaint.measureText(badgeText) + 8f * density
                val pillLeft = drawX + 6f * density
                rectPaint.color = foldPillBgColor
                tempRect.set(pillLeft, lineTop + 3f, pillLeft + badgeW, lineBottom - 3f)
                canvas.drawRoundRect(tempRect, 6f * density, 6f * density, rectPaint)
                canvas.drawText(badgeText, pillLeft + 4f * density, baseline, badgePaint)
            }

            val cs = composingStartPos
            val ce = composingEndPos
            if (cs != null && ce != null && ce > cs && ce.line >= lineIdx && cs.line <= lineIdx) {
                val localS = if (cs.line == lineIdx) cs.col.coerceIn(segStart, segEnd) else segStart
                val localE = if (ce.line == lineIdx) ce.col.coerceIn(segStart, segEnd) else segEnd
                if (localS < localE) {
                    val x1 = gutterWidth + codeLeftPad + measureLineWidth(line, segStart, localS) - scrollXPos
                    val x2 = gutterWidth + codeLeftPad + measureLineWidth(line, segStart, localE) - scrollXPos
                    rectPaint.color = Color.parseColor("#00E5FF")
                    canvas.drawRect(x1, baseline + 3f, x2, baseline + 5f, rectPaint)
                }
            }

            if (visRow == cursorVisRow && !hasSel) {
                val safeCol = cursorPos.col.coerceIn(segStart, line.length)
                val cursorX = gutterWidth + codeLeftPad + measureLineWidth(line, segStart, safeCol) - scrollXPos
                cursorHandleX = cursorX
                cursorHandleY = lineBottom
                if (cursorVisible || currentDragMode == DragMode.CURSOR_HANDLE) {
                    drawRazorSharpCursor(canvas, cursorX, lineTop, lineBottom)
                }
            }
        }

        if (hasSel) {
            val visS = docLineColToVisRow(selStart.line, selStart.col)
            val visE = docLineColToVisRow(selEnd.line, selEnd.col)

            if (visS in firstVisRow..lastVisRow) {
                val segStartS = if (wordWrapEnabled && visS in wrapRows.indices) wrapRows[visS.toInt()].startCol else 0
                startHandleX = gutterWidth + codeLeftPad + measureLineWidth(doc.getLine(selStart.line), segStartS, selStart.col) - scrollXPos
                startHandleY = (topPad + (visS + 1L).toDouble() * lineHeight - scrollYPos).toFloat()
                drawTeardropHandle(canvas, startHandleX, startHandleY, handleColor)
                selectionHandlesVisible = true
            }
            if (visE in firstVisRow..lastVisRow) {
                val segStartE = if (wordWrapEnabled && visE in wrapRows.indices) wrapRows[visE.toInt()].startCol else 0
                endHandleX = gutterWidth + codeLeftPad + measureLineWidth(doc.getLine(selEnd.line), segStartE, selEnd.col) - scrollXPos
                endHandleY = (topPad + (visE + 1L).toDouble() * lineHeight - scrollYPos).toFloat()
                drawTeardropHandle(canvas, endHandleX, endHandleY, handleColor)
                selectionHandlesVisible = true
            }
        } else if (isCursorHandleVisible && cursorHandleX >= 0f) {
            drawTeardropHandle(canvas, cursorHandleX, cursorHandleY, cursorHandleColor)
        }

        canvas.restore()

        rectPaint.color = gutterBgColor
        canvas.drawRect(0f, 0f, gutterWidth, height.toFloat(), rectPaint)
        canvas.drawLine(gutterWidth, 0f, gutterWidth, height.toFloat(), linePaint)

        val gutterTextX = gutterWidth - foldColumnWidth - 2f * density
        val chevronCenterX = gutterWidth - foldColumnWidth * 0.5f

        for (visRow in firstVisRow..lastVisRow) {
            val isFirstSeg: Boolean
            val lineIdx: Long
            if (wordWrapEnabled && visRow in wrapRows.indices) {
                val r = wrapRows[visRow.toInt()]
                lineIdx = r.docLine.toLong()
                isFirstSeg = r.isFirstSeg
            } else {
                lineIdx = visualRowToDocLine(visRow)
                isFirstSeg = true
            }
            if (!isFirstSeg) continue

            val lineTop = (topPad + visRow.toDouble() * lineHeight - scrollYPos).toFloat()
            val lineBottom = lineTop + lineHeight
            val baseline = lineTop + baselineOffset
            if (lineIdx == cursorPos.line) {
                rectPaint.color = activeLineNumColor
                canvas.drawRect(0f, lineTop, 2.5f * density, lineBottom, rectPaint)
                gutterPaint.color = activeLineNumColor
                gutterPaint.isFakeBoldText = true
            } else {
                gutterPaint.color = lineNumColor
                gutterPaint.isFakeBoldText = false
            }
            canvas.drawText((lineIdx + 1L).toString(), gutterTextX, baseline, gutterPaint)

            if (lineIdx <= Int.MAX_VALUE && foldRegions.containsKey(lineIdx.toInt())) {
                val cy = lineTop + lineHeight * 0.5f
                val r = 3.2f * density
                strokePaint.color = if (foldedStarts.contains(lineIdx.toInt())) activeLineNumColor else foldChevronColor
                handlePath.reset()
                if (foldedStarts.contains(lineIdx.toInt())) {
                    handlePath.moveTo(chevronCenterX - r * 0.6f, cy - r)
                    handlePath.lineTo(chevronCenterX + r * 0.8f, cy)
                    handlePath.lineTo(chevronCenterX - r * 0.6f, cy + r)
                } else {
                    handlePath.moveTo(chevronCenterX - r, cy - r * 0.5f)
                    handlePath.lineTo(chevronCenterX, cy + r * 0.7f)
                    handlePath.lineTo(chevronCenterX + r, cy - r * 0.5f)
                }
                canvas.drawPath(handlePath, strokePaint)
            }
        }
        gutterPaint.isFakeBoldText = false

        val mScrollY = maxScrollY()
        if (mScrollY > 0.0 && height > 0) {
            val totalContentH = height.toDouble() + mScrollY
            val thumbH = max(24f * density, ((height.toDouble() / totalContentH) * height).toFloat())
            val thumbTop = ((scrollYPos / mScrollY) * (height - thumbH)).toFloat()

            rectPaint.color = if (currentDragMode == DragMode.FAST_SCROLL_THUMB) scrollbarActiveColor else scrollbarColor
            tempRect.set(width - 5f * density, thumbTop, width - 1f * density, thumbTop + thumbH)
            canvas.drawRoundRect(tempRect, 3f, 3f, rectPaint)

            if (showScrollBadge || currentDragMode == DragMode.FAST_SCROLL_THUMB) {
                val topDocLine = visualRowToDocLine(firstVisRow) + 1L
                val badgeTxt = "Ln $topDocLine"
                val txtW = scrollBadgePaint.measureText(badgeTxt)
                val pillW = txtW + 16f * density
                val pillH = 22f * density
                val pillRight = width - 10f * density
                val pillLeft = pillRight - pillW
                val pillTop = (thumbTop + thumbH * 0.5f - pillH * 0.5f).coerceIn(6f * density, height - pillH - 6f * density)

                fastScrollPillRect.set(pillLeft - 10f * density, pillTop - 6f * density, pillRight + 4f * density, pillTop + pillH + 6f * density)

                tempRect.set(pillLeft, pillTop, pillRight, pillTop + pillH)
                rectPaint.color = Color.parseColor("#EE1D1D24")
                canvas.drawRoundRect(tempRect, 8f * density, 8f * density, rectPaint)
                strokePaint.color = headerPurpleStart
                canvas.drawRoundRect(tempRect, 8f * density, 8f * density, strokePaint)
                val bFm = scrollBadgePaint.fontMetrics
                val bBaseline = pillTop + (pillH - (bFm.descent - bFm.ascent)) * 0.5f - bFm.ascent
                canvas.drawText(badgeTxt, pillLeft + 8f * density, bBaseline, scrollBadgePaint)
            } else {
                fastScrollPillRect.set(0f, 0f, 0f, 0f)
            }
        }

        if (loupeActive) {
            drawIdeMagnifier(canvas)
        }

        canvas.restore()
    }

    private fun drawRazorSharpCursor(canvas: Canvas, x: Float, top: Float, bottom: Float) {
        val cTop = top + 1f * density
        val cBottom = bottom - 1f * density
        val activeColor = neonBlinkColorCycle[blinkColorIndex]

        cursorCorePaint.shader = null
        cursorCorePaint.color = activeColor
        cursorCorePaint.alpha = 255
        val coreWidth = 1.3f * density
        canvas.drawRect(x - coreWidth * 0.5f, cTop, x + coreWidth * 0.5f, cBottom, cursorCorePaint)
    }

    private fun drawTeardropHandle(canvas: Canvas, x: Float, y: Float, fillColor: Int) {
        val r = 10.5f * density
        val cy = y + r * 1.45f

        handlePath.reset()
        handlePath.moveTo(x, y)
        handlePath.cubicTo(
            x + r * 0.9f, y + r * 0.65f,
            x + r, cy - r * 0.1f,
            x + r, cy
        )
        handlePath.arcTo(x - r, cy - r, x + r, cy + r, 0f, 180f, false)
        handlePath.cubicTo(
            x - r, cy - r * 0.1f,
            x - r * 0.9f, y + r * 0.65f,
            x, y
        )
        handlePath.close()

        rectPaint.color = fillColor
        canvas.drawPath(handlePath, rectPaint)
        canvas.drawPath(handlePath, handleOutlinePaint)

        rectPaint.color = Color.parseColor("#FF0E0E0E")
        canvas.drawCircle(x, cy + r * 0.05f, 2.3f * density, rectPaint)
    }

    private fun drawIdeMagnifier(canvas: Canvas) {
        val loupeW = 116f * density
        val loupeH = 42f * density
        val cornerR = 11f * density
        val zoom = 1.38f

        val cx = loupeTargetX.coerceIn(gutterWidth + loupeW * 0.5f + 4f * density, width - loupeW * 0.5f - 4f * density)
        val idealAboveY = loupeTargetY - lineHeight * 0.5f - loupeH * 0.5f - 22f * density
        val cy = if (idealAboveY - loupeH * 0.5f >= 4f * density) {
            idealAboveY
        } else {
            (loupeTargetY + lineHeight * 0.5f + loupeH * 0.5f + 28f * density)
                .coerceAtMost(height - loupeH * 0.5f - 6f * density)
        }

        tempRect.set(
            cx - loupeW * 0.5f + 1f * density,
            cy - loupeH * 0.5f + 3f * density,
            cx + loupeW * 0.5f + 1f * density,
            cy + loupeH * 0.5f + 3f * density
        )
        canvas.drawRoundRect(tempRect, cornerR, cornerR, loupeShadowPaint)

        tempRect.set(cx - loupeW * 0.5f, cy - loupeH * 0.5f, cx + loupeW * 0.5f, cy + loupeH * 0.5f)
        rectPaint.color = Color.parseColor("#F5141418")
        canvas.drawRoundRect(tempRect, cornerR, cornerR, rectPaint)

        canvas.save()
        loupeClipPath.reset()
        loupeClipPath.addRoundRect(tempRect, cornerR, cornerR, Path.Direction.CW)
        canvas.clipPath(loupeClipPath)

        val safeLine = loupeTargetDocLine.coerceIn(0L, (doc.lineCount - 1L).coerceAtLeast(0L))
        val lineStr = doc.getLine(safeLine)
        val visRow = docLineColToVisRow(safeLine, loupeTargetCol).coerceAtLeast(0L)
        val segStart = if (wordWrapEnabled && visRow in wrapRows.indices) wrapRows[visRow.toInt()].startCol else 0
        val segEnd = if (wordWrapEnabled && visRow in wrapRows.indices) wrapRows[visRow.toInt()].endCol else lineStr.length

        val targetOffsetInSeg = measureLineWidth(lineStr, segStart, loupeTargetCol)
        val drawStartX = cx - targetOffsetInSeg * zoom
        val fm = codePaint.fontMetrics
        val naturalH = fm.descent - fm.ascent
        val zoomedBaseline = cy + ((naturalH * 0.5f - fm.descent) * zoom)

        canvas.translate(drawStartX, zoomedBaseline)
        canvas.scale(zoom, zoom)

        if (hasSelection()) {
            val (selS, selE) = getSelectionRange()
            if (selE.line >= safeLine && selS.line <= safeLine) {
                val localS = if (selS.line == safeLine) selS.col.coerceIn(segStart, segEnd) else segStart
                val localE = if (selE.line == safeLine) selE.col.coerceIn(segStart, segEnd) else segEnd
                val sx1 = measureLineWidth(lineStr, segStart, localS)
                val sx2 = measureLineWidth(lineStr, segStart, localE)
                rectPaint.color = selectionColor
                canvas.drawRect(sx1, fm.ascent, max(sx1 + 2f, sx2), fm.descent, rectPaint)
            }
        }

        val tokens = getTokensForLine(safeLine)
        var tx = 0f
        if (tokens.isEmpty()) {
            codePaint.color = defaultTextColor
            codePaint.isFakeBoldText = false
            drawLineSegment(canvas, lineStr, segStart, segEnd, 0f, 0f, codePaint)
        } else {
            for (token in tokens) {
                val ts = max(token.start, segStart)
                val te = min(token.end, segEnd)
                if (ts < te) {
                    codePaint.color = token.color
                    codePaint.isFakeBoldText = token.bold
                    tx += drawLineSegment(canvas, lineStr, ts, te, tx, 0f, codePaint)
                }
            }
            codePaint.isFakeBoldText = false
        }

        cursorCorePaint.shader = null
        cursorCorePaint.color = neonBlinkColorCycle[blinkColorIndex]
        cursorCorePaint.alpha = 255
        canvas.drawRect(targetOffsetInSeg - 0.65f * density, fm.ascent, targetOffsetInSeg + 0.65f * density, fm.descent, cursorCorePaint)

        canvas.restore()

        loupeBorderPaint.shader = LinearGradient(
            tempRect.left, tempRect.top, tempRect.right, tempRect.bottom,
            headerPurpleStart, headerPurpleEnd, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(tempRect, cornerR, cornerR, loupeBorderPaint)
    }

    private fun updateDragPosition(rawX: Float, rawY: Float) {
        if (currentDragMode == DragMode.FAST_SCROLL_THUMB) {
            val mScrollY = maxScrollY()
            if (mScrollY > 0.0 && height > 0) {
                val totalH = height.toDouble()
                val targetY = (rawY - fastScrollTouchGrabOffsetY).toDouble().coerceIn(0.0, totalH)
                val fraction = (targetY / totalH).coerceIn(0.0, 1.0)
                scrollYPos = (fraction * mScrollY).coerceIn(0.0, mScrollY)
                triggerScrollBadge()
            }
            return
        }

        val sampleX = rawX + dragDeltaX
        val sampleY = rawY + dragDeltaY
        val newPos = hitTest(sampleX, sampleY)

        when (currentDragMode) {
            DragMode.CURSOR_HANDLE -> {
                cursorPos = newPos
                if (cursorPos.line <= Int.MAX_VALUE) unfoldLineIfHidden(cursorPos.line.toInt())
                updateMatchingBrackets()
                updateActiveIndentLevel()
                notifyCursorAndState()
                updateMagnifierForPos(cursorPos)
            }
            DragMode.LONG_PRESS_SELECT -> {
                if (newPos < longPressOriginStart) {
                    selectionAnchorPos = newPos
                    cursorPos = longPressOriginEnd
                } else if (newPos > longPressOriginEnd) {
                    selectionAnchorPos = longPressOriginStart
                    cursorPos = newPos
                } else {
                    selectionAnchorPos = longPressOriginStart
                    cursorPos = longPressOriginEnd
                }
                updateActiveIndentLevel()
                notifyCursorAndState()
                updateMagnifierForPos(newPos)
            }
            DragMode.SELECTION_START -> {
                if (selectionAnchorPos == null) selectionAnchorPos = cursorPos
                if (newPos < cursorPos) {
                    selectionAnchorPos = newPos
                    updateMagnifierForPos(newPos)
                } else if (newPos > cursorPos) {
                    selectionAnchorPos = cursorPos
                    cursorPos = newPos
                    currentDragMode = DragMode.SELECTION_END
                    updateMagnifierForPos(cursorPos)
                } else {
                    updateMagnifierForPos(newPos)
                }
                updateActiveIndentLevel()
                notifyCursorAndState()
            }
            DragMode.SELECTION_END -> {
                val anchor = selectionAnchorPos ?: cursorPos
                selectionAnchorPos = anchor
                if (newPos > anchor) {
                    cursorPos = newPos
                    updateMagnifierForPos(cursorPos)
                } else if (newPos < anchor) {
                    cursorPos = anchor
                    selectionAnchorPos = newPos
                    currentDragMode = DragMode.SELECTION_START
                    updateMagnifierForPos(newPos)
                } else {
                    updateMagnifierForPos(cursorPos)
                }
                updateActiveIndentLevel()
                notifyCursorAndState()
            }
            else -> {}
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                wasMultiTouch = false
                val hit = hitTestHandles(event.x, event.y)
                if (hit != DragMode.NONE) {
                    currentDragMode = hit
                    scroller.forceFinished(true)
                    hideSelectionMenu()
                    dismissCompletions()

                    if (hit == DragMode.FAST_SCROLL_THUMB) {
                        fastScrollTouchGrabOffsetY = event.y - fastScrollPillRect.top
                        triggerScrollBadge()
                        invalidate()
                        return true
                    }

                    if (hit == DragMode.SELECTION_START || hit == DragMode.SELECTION_END) {
                        val (s, e) = getSelectionRange()
                        selectionAnchorPos = s
                        cursorPos = e
                    }

                    val targetPos = when (hit) {
                        DragMode.SELECTION_START -> getSelectionRange().first
                        DragMode.SELECTION_END -> getSelectionRange().second
                        else -> cursorPos
                    }
                    val visRow = docLineColToVisRow(targetPos.line, targetPos.col).coerceAtLeast(0L)
                    val segStart = if (wordWrapEnabled && visRow in wrapRows.indices) wrapRows[visRow.toInt()].startCol else 0
                    val exactX = gutterWidth + codeLeftPad + measureLineWidth(doc.getLine(targetPos.line), segStart, targetPos.col) - scrollXPos
                    val exactLineCenterY = (topPad + visRow.toDouble() * lineHeight - scrollYPos + lineHeight * 0.5).toFloat()

                    dragDeltaX = exactX - event.x
                    dragDeltaY = exactLineCenterY - event.y
                    lastTouchX = event.x
                    lastTouchY = event.y

                    if (hit == DragMode.CURSOR_HANDLE) {
                        mainHandler.removeCallbacks(hideCursorHandleRunnable)
                        isCursorHandleVisible = true
                    }
                    updateMagnifierForPos(targetPos)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                wasMultiTouch = true
                cancelGestureDetector(event)
                if (currentDragMode != DragMode.NONE) {
                    currentDragMode = DragMode.NONE
                    autoScrollActive = false
                    mainHandler.removeCallbacks(autoScrollRunnable)
                    hideMagnifier()
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!wasMultiTouch && !isScaling && event.pointerCount == 1 && currentDragMode != DragMode.NONE) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    updateDragPosition(event.x, event.y)

                    if (currentDragMode != DragMode.FAST_SCROLL_THUMB) {
                        val nearEdge = event.y > height - lineHeight * 1.4f ||
                            event.y < topPad + lineHeight * 1.4f ||
                            (!wordWrapEnabled && (event.x > width - 28f * density || event.x < gutterWidth + 28f * density))
                        if (nearEdge && !autoScrollActive) {
                            autoScrollActive = true
                            mainHandler.postDelayed(autoScrollRunnable, 24)
                        }
                    }
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentDragMode != DragMode.NONE) {
                    val endedMode = currentDragMode
                    currentDragMode = DragMode.NONE
                    autoScrollActive = false
                    mainHandler.removeCallbacks(autoScrollRunnable)
                    hideMagnifier()

                    if (endedMode == DragMode.CURSOR_HANDLE) {
                        setCursorPos(cursorPos, ensureVisible = false)
                        showCursorHandleWithTimeout()
                    } else if (hasSelection()) {
                        val (s, e) = getSelectionRange()
                        selectionAnchorPos = s
                        cursorPos = e
                        emitSelectionMenu()
                    } else {
                        showCursorHandleWithTimeout()
                    }
                    wasMultiTouch = false
                    invalidate()
                    return true
                }
            }
        }

        var handled = scaleDetector.onTouchEvent(event)
        if (!isScaling && !wasMultiTouch && event.pointerCount == 1) {
            handled = gestureDetector.onTouchEvent(event) || handled
        }

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            wasMultiTouch = false
        }

        return handled || super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollXPos = scroller.currX.coerceIn(0, maxScrollX())
            val newY = flingBaseScrollY + scroller.currY
            val mY = maxScrollY()
            scrollYPos = newY.coerceIn(0.0, mY)
            if ((newY <= 0.0 || newY >= mY) && !scroller.isFinished) {
                scroller.forceFinished(true)
            }
            triggerScrollBadge()
            invalidate()
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (wordWrapEnabled && w != oldw) {
            rebuildWrapRows(asyncIfLarge = true)
        }
        scrollXPos = scrollXPos.coerceIn(0, maxScrollX())
        scrollYPos = scrollYPos.coerceIn(0.0, maxScrollY())
        if (h < oldh && isEditableMode) {
            post {
                ensureCursorVisible()
                if (hasSelection()) emitSelectionMenu()
                invalidate()
            }
        }
    }

    override fun onCheckIsTextEditor(): Boolean = isEditableMode

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!isEditableMode) return null
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI
        val (_, selS, selE) = getLineLocalSelectionForIme()
        outAttrs.initialSelStart = selS
        outAttrs.initialSelEnd = selE
        return CodeInputConnection(this)
    }
}