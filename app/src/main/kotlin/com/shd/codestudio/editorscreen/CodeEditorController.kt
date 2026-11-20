package com.shd.codestudio.editorscreen

import android.content.Context
import android.view.ViewGroup
import java.io.File

class CodeEditorController {
    internal var editorView: CodeEditorView? = null
    internal var loadedFileKey: String = ""

    internal fun getOrCreateView(context: Context): CodeEditorView {
        val existing = editorView
        if (existing != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        return CodeEditorView(context).also { editorView = it }
    }

    fun isSameFileLoaded(fileKey: String): Boolean = editorView != null && loadedFileKey == fileKey

    fun loadLines(fileKey: String, linesList: List<String>, filePath: String, destFile: File? = null) {
        loadedFileKey = fileKey
        editorView?.loadDocumentLines(linesList, filePath, destFile)
    }

    fun loadText(fileKey: String, text: String, filePath: String, destFile: File? = null) {
        loadedFileKey = fileKey
        editorView?.loadDocumentText(text, filePath, destFile)
    }

    fun loadLargeFile(fileKey: String, filePath: String, destFile: File) {
        loadedFileKey = fileKey
        editorView?.loadLargeFile(filePath, destFile)
    }

    fun isDirty(): Boolean = editorView?.isDocumentDirty() ?: false

    fun autoSaveIfDirty(): Boolean = editorView?.saveToTargetIfDirtyBlocking() ?: false

    fun saveToFile(destFile: File): Boolean = editorView?.saveDocumentToFile(destFile) ?: false

    fun syncUiState() { editorView?.notifyStateNow() }
    fun getText(): String = editorView?.getFullText() ?: ""
    fun getSelectedOrAllText(): Pair<String, Boolean> = editorView?.getSelectedOrAllText() ?: ("" to false)
    fun getSelectedTextOrNull(): String? = editorView?.getSelectedTextOrNull()
    fun getDiagnosticMessage(): String = editorView?.diagnosticMessage ?: ""
    fun insertSnippet(snippet: String) { editorView?.insertTextAtCursor(snippet) }
    fun clearAll() { editorView?.clearDocument() }
    fun undo() { editorView?.undo() }
    fun redo() { editorView?.redo() }
    fun cutSelection() { editorView?.cutSelectionToClipboard() }
    fun copySelectionOrAll(): Boolean = editorView?.copyToClipboard() ?: false
    fun pasteFromClipboard(): Boolean = editorView?.pasteFromClipboard() ?: false
    fun selectAll() { editorView?.selectAllText() }
    fun duplicateLine() { editorView?.duplicateCurrentLine() }
    fun toggleComment() { editorView?.toggleCurrentLineComment() }
    fun foldAll() { editorView?.foldAllRegions() }
    fun unfoldAll() { editorView?.unfoldAllRegions() }
    fun setWordWrap(enabled: Boolean) { editorView?.setWordWrapEnabled(enabled) }
    fun goToLine(line1Based: Long) { editorView?.goToLineNumber(line1Based) }
    fun goToLineAndSelect(line1Based: Long, startCol: Int, endCol: Int) {
        editorView?.goToLineAndSelectRange(line1Based, startCol, endCol)
    }
    fun applyCompletion(prefixLength: Int, insertText: String) { editorView?.applyAutoCompletion(prefixLength, insertText) }
    fun dismissCompletions() { editorView?.dismissCompletions() }

    // সার্চ ও রিপ্লেস কন্ট্রোল
    fun findMatches(query: String, matchCase: Boolean = false, wholeWord: Boolean = false, useRegex: Boolean = false) {
        editorView?.updateSearchQuery(query, matchCase, wholeWord, useRegex)
    }
    fun jumpToMatch(match: EditorSearchMatch, activeIndex: Int) {
        editorView?.jumpToSearchMatch(match, activeIndex)
    }
    fun replaceCurrentMatch(query: String, replacement: String, matchCase: Boolean = false, wholeWord: Boolean = false, useRegex: Boolean = false) {
        editorView?.replaceActiveMatch(query, replacement, matchCase, wholeWord, useRegex)
    }
    fun replaceAllMatches(query: String, replacement: String, matchCase: Boolean = false, wholeWord: Boolean = false, useRegex: Boolean = false): Int =
        editorView?.replaceAllMatches(query, replacement, matchCase, wholeWord, useRegex) ?: 0

    // প্রো IDE কুইক অ্যাকশন ও নেভিগেশন
    fun moveCursorLeft() { editorView?.moveCursorStep(-1, 0) }
    fun moveCursorRight() { editorView?.moveCursorStep(1, 0) }
    fun moveCursorUp() { editorView?.moveCursorStep(0, -1) }
    fun moveCursorDown() { editorView?.moveCursorStep(0, 1) }
    fun moveToLineStart() { editorView?.moveToLineEdge(toEnd = false) }
    fun moveToLineEnd() { editorView?.moveToLineEdge(toEnd = true) }
    fun moveLineUp() { editorView?.moveCurrentLine(-1) }
    fun moveLineDown() { editorView?.moveCurrentLine(1) }
    fun deleteLine() { editorView?.deleteCurrentLine() }
    fun selectCurrentLine() { editorView?.selectCurrentLine() }
    fun unindentLine() { editorView?.unindentCurrentLine() }
    fun formatCode() { editorView?.formatDocument() }

    fun jumpToTop() { editorView?.jumpToTop() }
    fun jumpToBottom() { editorView?.jumpToBottom() }
    fun scrollByDelta(dyPx: Int) { editorView?.scrollByDelta(dyPx) }
    fun markSaved() { editorView?.markSaved() }
    fun pauseBlink() { editorView?.release() }

    fun destroy() {
        val view = editorView
        view?.saveToTargetIfDirtyBlocking()
        view?.destroyEngine()
        (view?.parent as? ViewGroup)?.removeView(view)
        editorView = null
        loadedFileKey = ""
    }
}