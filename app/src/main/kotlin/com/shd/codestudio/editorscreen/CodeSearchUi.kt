package com.shd.codestudio.editorscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shd.codestudio.NeonLime
import com.shd.codestudio.TextSecondary

@Composable
fun CodeSearchUi(
    isProjectSearchMode: Boolean,
    onSearchModeChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showReplaceRow: Boolean,
    onToggleReplaceRow: () -> Unit,
    replaceQuery: String,
    onReplaceQueryChange: (String) -> Unit,
    matchCase: Boolean,
    onToggleMatchCase: () -> Unit,
    wholeWord: Boolean,
    onToggleWholeWord: () -> Unit,
    useRegex: Boolean,
    onToggleUseRegex: () -> Unit,
    inFileMatchesCount: Int,
    currentInFileIndex: Int,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAllInFile: () -> Unit,
    projectSearchResults: List<ProjectSearchResult>,
    projectFileCount: Int,
    isSearchingProject: Boolean,
    onSelectProjectResult: (ProjectSearchResult) -> Unit,
    onReplaceAllInProject: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ১. ট্যাব বার (Find in File / Find in Project)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF141414))
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (!isProjectSearchMode) NeonLime else Color.Transparent)
                    .clickable { onSearchModeChange(false) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Find in File",
                    color = if (!isProjectSearchMode) Color.Black else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isProjectSearchMode) NeonLime else Color.Transparent)
                    .clickable { onSearchModeChange(true) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Find in Project",
                    color = if (isProjectSearchMode) Color.Black else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ২. সার্চ ইনপুট (কখনোই ক্লিপ বা অদৃশ্য হবে না) এবং অপশন ফিল্টার
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(NeonLime),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = if (isProjectSearchMode) "Search in project..." else "Find in file...",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, if (searchQuery.isNotEmpty()) NeonLime.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp)
            )

            // অপশন টগলস: Case Sensitive, Whole Word, Regex
            SearchFilterChip(label = "Aa", active = matchCase, onClick = onToggleMatchCase)
            SearchFilterChip(label = "W", active = wholeWord, onClick = onToggleWholeWord)
            SearchFilterChip(label = ".*", active = useRegex, onClick = onToggleUseRegex)

            if (!isProjectSearchMode) {
                Text(
                    text = if (inFileMatchesCount == 0) "0" else "${currentInFileIndex + 1}/$inFileMatchesCount",
                    color = if (inFileMatchesCount == 0) TextSecondary else NeonLime,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                IconButton(onClick = onPrevMatch, modifier = Modifier.size(26.dp), enabled = inFileMatchesCount > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onNextMatch, modifier = Modifier.size(26.dp), enabled = inFileMatchesCount > 0) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }

            IconButton(onClick = onToggleReplaceRow, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Default.FindReplace,
                    contentDescription = "Replace Mode",
                    tint = if (showReplaceRow) NeonLime else Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }

        // ৩. রিপ্লেস রো (ক্লিপ-মুক্ত ইনপুট বক্স)
        if (showReplaceRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BasicTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(NeonLime),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (replaceQuery.isEmpty()) {
                                Text(
                                    text = "Replace with...",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141414))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp)
                )

                if (!isProjectSearchMode) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2B2B2B))
                            .clickable(enabled = inFileMatchesCount > 0, onClick = onReplaceCurrent)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Replace", color = if (inFileMatchesCount > 0) NeonLime else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (inFileMatchesCount > 0) NeonLime else Color(0xFF2B2B2B))
                            .clickable(enabled = inFileMatchesCount > 0, onClick = onReplaceAllInFile)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("All", color = if (inFileMatchesCount > 0) Color.Black else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (projectSearchResults.isNotEmpty()) Color(0xFFFF5252) else Color(0xFF2B2B2B))
                            .clickable(enabled = projectSearchResults.isNotEmpty(), onClick = onReplaceAllInProject)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Replace All Files", color = if (projectSearchResults.isNotEmpty()) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ৪. প্রজেক্ট সার্চ রেজাল্ট প্রিভিউ লিস্ট
        if (isProjectSearchMode) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSearchingProject) "Searching in project..." else "${projectSearchResults.size} matches in $projectFileCount file(s)",
                    color = NeonLime,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isSearchingProject) {
                    CircularProgressIndicator(color = NeonLime, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                }
            }

            if (projectSearchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .background(Color(0xFF141414), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(projectSearchResults) { item ->
                        ProjectSearchResultRow(result = item, onClick = { onSelectProjectResult(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 24.dp, height = 28.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (active) NeonLime.copy(alpha = 0.25f) else Color(0xFF282828))
            .border(
                1.dp,
                if (active) NeonLime else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(5.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) NeonLime else Color.LightGray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ProjectSearchResultRow(
    result: ProjectSearchResult,
    onClick: () -> Unit
) {
    val annotatedLine = buildAnnotatedString {
        val s = result.matchStartCol.coerceIn(0, result.lineText.length)
        val e = result.matchEndCol.coerceIn(s, result.lineText.length)
        val prefix = result.lineText.substring(0, s)
        val matched = result.lineText.substring(s, e)
        val suffix = result.lineText.substring(e)

        withStyle(SpanStyle(color = Color.LightGray)) { append(prefix.trimStart()) }
        withStyle(SpanStyle(color = Color.Black, background = NeonLime, fontWeight = FontWeight.Bold)) { append(matched) }
        withStyle(SpanStyle(color = Color.LightGray)) { append(suffix) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = NeonLime, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.filePath.substringAfterLast("/"),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Ln ${result.lineNumber}",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = annotatedLine,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}