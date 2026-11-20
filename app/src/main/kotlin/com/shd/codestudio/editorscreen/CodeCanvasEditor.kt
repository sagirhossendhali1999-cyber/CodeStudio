package com.shd.codestudio.editorscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun CodeCanvasEditor(
    controller: CodeEditorController,
    fontSizeSp: Float,
    isKeyboardActive: Boolean,
    isWordWrap: Boolean,
    onFontSizeChange: (Float) -> Unit,
    onCursorChange: (line: Long, col: Int) -> Unit,
    onStateChange: (
        isDirty: Boolean,
        canUndo: Boolean,
        canRedo: Boolean,
        selectionLength: Long,
        lineCount: Long,
        isHugeMode: Boolean
    ) -> Unit,
    onSearchResultsChange: (List<EditorSearchMatch>) -> Unit,
    onSelectionMenuChange: (visible: Boolean, xPx: Float, yPx: Float, hasSelection: Boolean) -> Unit,
    onCompletionsChange: (prefix: String, items: List<CompletionItem>, xPx: Float, yPx: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        onDispose { controller.pauseBlink() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            controller.getOrCreateView(ctx).apply {
                setFontSizeSp(fontSizeSp)
                setWordWrapEnabled(isWordWrap)
                setEditableMode(isKeyboardActive)
                onFontSizeChangedListener = onFontSizeChange
                onCursorChangedListener = onCursorChange
                onStateChangedListener = onStateChange
                onSearchResultsListener = onSearchResultsChange
                onSelectionMenuListener = onSelectionMenuChange
                onCompletionsListener = onCompletionsChange
                notifyStateNow()
            }
        },
        update = { view ->
            view.onFontSizeChangedListener = onFontSizeChange
            view.onCursorChangedListener = onCursorChange
            view.onStateChangedListener = onStateChange
            view.onSearchResultsListener = onSearchResultsChange
            view.onSelectionMenuListener = onSelectionMenuChange
            view.onCompletionsListener = onCompletionsChange
            view.syncFontSizeFromState(fontSizeSp)
            view.setWordWrapEnabled(isWordWrap)
            view.setEditableMode(isKeyboardActive)
        }
    )
}