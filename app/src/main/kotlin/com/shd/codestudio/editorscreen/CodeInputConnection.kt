package com.shd.codestudio.editorscreen

import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import kotlin.math.min

internal class CodeInputConnection(
    private val editor: CodeEditorView
) : BaseInputConnection(editor, false) {

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
        val (s, _) = editor.getSelectionRange()
        return editor.doc.getTextBefore(s, n)
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
        val (_, e) = editor.getSelectionRange()
        return editor.doc.getTextAfter(e, n)
    }

    override fun getSelectedText(flags: Int): CharSequence? {
        if (!editor.hasSelection()) return null
        val (s, e) = editor.getSelectionRange()
        return editor.doc.getSubstring(s, e, maxChars = 65_536)
    }

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
        val extracted = ExtractedText()
        val (lineText, selS, selE) = editor.getLineLocalSelectionForIme()
        if (editor.doc.approxTotalChars <= 15_000L && !editor.doc.isHugeMode) {
            extracted.text = editor.getFullText()
            extracted.startOffset = 0
            extracted.selectionStart = selS
            extracted.selectionEnd = selE
        } else {
            extracted.text = lineText
            extracted.startOffset = 0
            extracted.selectionStart = selS
            extracted.selectionEnd = selE
        }
        return extracted
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (!editor.isEditableMode) return false
        editor.hideSelectionMenu()
        editor.hideCursorHandleImmediate()
        val rep = text?.toString() ?: ""
        val cs = editor.composingStartPos
        val ce = editor.composingEndPos
        val (s, e) = if (cs != null && ce != null && ce >= cs) {
            cs to ce
        } else {
            editor.getSelectionRange()
        }

        val endAfterEdit = editor.applyEdit(s, e, rep, isComposing = true)
        editor.clearSelectionInternal()
        if (rep.isNotEmpty()) {
            editor.composingStartPos = s
            editor.composingEndPos = endAfterEdit
        } else {
            editor.finishComposingInternal()
        }

        val targetPos = if (newCursorPosition > 0) {
            editor.stepPos(endAfterEdit, newCursorPosition - 1)
        } else {
            editor.stepPos(s, newCursorPosition)
        }
        editor.setCursorPos(targetPos, ensureVisible = true)
        editor.triggerCompletionsIfTyping()
        editor.invalidate()
        return true
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        if (!editor.isEditableMode) return false
        val line = editor.cursorPos.line
        val len = editor.doc.getLineLength(line)
        val s = start.coerceIn(0, len)
        val e = end.coerceIn(s, len)
        if (s < e) {
            editor.composingStartPos = DocPos(line, s)
            editor.composingEndPos = DocPos(line, e)
        } else {
            editor.finishComposingInternal()
        }
        editor.invalidate()
        return true
    }

    override fun finishComposingText(): Boolean {
        editor.finishComposingInternal()
        editor.invalidate()
        return true
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (!editor.isEditableMode) return false
        editor.hideSelectionMenu()
        editor.hideCursorHandleImmediate()
        var insertStr = text?.toString() ?: ""
        val cs = editor.composingStartPos
        val ce = editor.composingEndPos
        val (s, e) = if (cs != null && ce != null && ce >= cs) {
            cs to ce
        } else {
            editor.getSelectionRange()
        }

        if (s == e && insertStr.length == 1) {
            val ch = insertStr[0]
            val nextChar = editor.doc.getTextAfter(s, 1).firstOrNull()

            if ((ch == ')' || ch == '}' || ch == ']' || ch == '"' || ch == '\'') && nextChar == ch) {
                editor.finishComposingInternal()
                editor.setCursorPos(editor.stepPos(s, 1), ensureVisible = true)
                editor.dismissCompletions()
                editor.invalidate()
                return true
            }

            val pairClosing = when (ch) {
                '(' -> ")"; '{' -> "}"; '[' -> "]"; '"' -> "\""; '\'' -> "'"; else -> null
            }
            if (pairClosing != null) {
                editor.applyEdit(s, e, "$ch$pairClosing")
                editor.clearSelectionInternal()
                editor.finishComposingInternal()
                editor.setCursorPos(editor.stepPos(s, 1), ensureVisible = true)
                editor.dismissCompletions()
                editor.invalidate()
                return true
            }

            if (ch == '\n') {
                val line = editor.doc.getLine(s.line)
                val col = s.col.coerceIn(0, line.length)
                val indent = StringBuilder()
                val limit = min(col, line.length)
                for (i in 0 until limit) {
                    val c = line[i]
                    if (c == ' ' || c == '\t') indent.append(c) else break
                }
                val prevChar = if (col > 0 && col <= line.length) line[col - 1] else null
                if (prevChar == '{' && nextChar == '}') {
                    val expanded = "\n$indent    \n$indent"
                    editor.applyEdit(s, e, expanded)
                    editor.clearSelectionInternal()
                    editor.finishComposingInternal()
                    editor.setCursorPos(DocPos(s.line + 1L, indent.length + 4), ensureVisible = true)
                    editor.dismissCompletions()
                    editor.invalidate()
                    return true
                } else {
                    insertStr = if (prevChar == '{') "\n$indent    " else "\n$indent"
                }
            }
        }

        val endAfterEdit = editor.applyEdit(s, e, insertStr)
        editor.clearSelectionInternal()
        editor.finishComposingInternal()
        val targetPos = if (newCursorPosition > 0) {
            editor.stepPos(endAfterEdit, newCursorPosition - 1)
        } else {
            editor.stepPos(s, newCursorPosition)
        }
        editor.setCursorPos(targetPos, ensureVisible = true)
        if (insertStr.length == 1 && (insertStr[0].isLetterOrDigit() || insertStr[0] == '_' || insertStr[0] == '@' || insertStr[0] == ':')) {
            editor.triggerCompletionsIfTyping()
        } else {
            editor.dismissCompletions()
        }
        editor.invalidate()
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if (!editor.isEditableMode) return false
        editor.hideSelectionMenu()
        editor.hideCursorHandleImmediate()
        editor.finishComposingInternal()
        if (editor.hasSelection()) {
            val (s, e) = editor.getSelectionRange()
            editor.applyEdit(s, e, "")
            editor.clearSelectionInternal()
            editor.setCursorPos(s, ensureVisible = true)
        } else {
            if (beforeLength == 1 && afterLength == 0) {
                val prev = editor.doc.getTextBefore(editor.cursorPos, 1).firstOrNull()
                val next = editor.doc.getTextAfter(editor.cursorPos, 1).firstOrNull()
                val isEmptyPair = (prev == '(' && next == ')') || (prev == '{' && next == '}') ||
                    (prev == '[' && next == ']') || (prev == '"' && next == '"') || (prev == '\'' && next == '\'')
                if (isEmptyPair) {
                    val s = editor.stepPos(editor.cursorPos, -1)
                    val e = editor.stepPos(editor.cursorPos, 1)
                    editor.applyEdit(s, e, "")
                    editor.setCursorPos(s, ensureVisible = true)
                    editor.dismissCompletions()
                    editor.invalidate()
                    return true
                }
            }
            val s = editor.stepPos(editor.cursorPos, -beforeLength)
            val e = editor.stepPos(editor.cursorPos, afterLength)
            if (s < e) {
                editor.applyEdit(s, e, "")
                editor.setCursorPos(s, ensureVisible = true)
            }
        }
        editor.triggerCompletionsIfTyping()
        editor.invalidate()
        return true
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
        deleteSurroundingText(beforeLength, afterLength)

    override fun setSelection(start: Int, end: Int): Boolean {
        val line = editor.cursorPos.line
        val len = editor.doc.getLineLength(line)
        val s = start.coerceIn(0, len)
        val e = end.coerceIn(0, len)
        editor.selectionAnchorPos = if (s == e) null else DocPos(line, s)
        editor.setCursorPos(DocPos(line, e), ensureVisible = true)
        if (editor.hasSelection()) {
            editor.hideCursorHandleImmediate()
            editor.emitSelectionMenu()
        } else {
            editor.hideSelectionMenu()
        }
        editor.invalidate()
        return true
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> { if (editor.isEditableMode) deleteSurroundingText(1, 0); return true }
                KeyEvent.KEYCODE_ENTER -> { if (editor.isEditableMode) commitText("\n", 1); return true }
                KeyEvent.KEYCODE_TAB -> { if (editor.isEditableMode) commitText("    ", 1); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    editor.clearSelectionInternal(); editor.hideSelectionMenu(); editor.hideCursorHandleImmediate()
                    editor.setCursorPos(editor.stepPos(editor.cursorPos, -1), ensureVisible = true); editor.invalidate(); return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    editor.clearSelectionInternal(); editor.hideSelectionMenu(); editor.hideCursorHandleImmediate()
                    editor.setCursorPos(editor.stepPos(editor.cursorPos, 1), ensureVisible = true); editor.invalidate(); return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    editor.moveCursorStep(0, -1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    editor.moveCursorStep(0, 1)
                    return true
                }
            }
        }
        return super.sendKeyEvent(event)
    }

    override fun performContextMenuAction(id: Int): Boolean {
        when (id) {
            android.R.id.selectAll -> { editor.selectAllText(); return true }
            android.R.id.copy -> { editor.copyToClipboard(); return true }
            android.R.id.cut -> { editor.cutSelectionToClipboard(); return true }
            android.R.id.paste -> { editor.pasteFromClipboard(); return true }
        }
        return super.performContextMenuAction(id)
    }
}