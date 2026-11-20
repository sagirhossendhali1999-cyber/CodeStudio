package com.shd.codestudio.editorscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shd.codestudio.NeonLime

data class QuickActionItem(
    val label: String,
    val actionType: String,
    val payload: String = "",
    val isHighlighted: Boolean = false
)

@Composable
fun CodeQuickActionBar(
    onAction: (QuickActionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickItems = remember {
        listOf(
            // ১. কার্সর ন্যাভিগেশন ও এডিটিং শর্টকাট
            QuickActionItem("◀", "NAV_LEFT", isHighlighted = true),
            QuickActionItem("▶", "NAV_RIGHT", isHighlighted = true),
            QuickActionItem("▲", "NAV_UP", isHighlighted = true),
            QuickActionItem("▼", "NAV_DOWN", isHighlighted = true),
            QuickActionItem("Home", "NAV_HOME", isHighlighted = true),
            QuickActionItem("End", "NAV_END", isHighlighted = true),
            QuickActionItem("Tab", "INSERT", "    ", isHighlighted = true),
            QuickActionItem("Untab", "UNTAB", isHighlighted = true),

            // ২. প্রো IDE ফিচারস (Format, Move Line ইত্যাদি)
            QuickActionItem("Format", "FORMAT", isHighlighted = true),
            QuickActionItem("Dup", "DUP", isHighlighted = true),
            QuickActionItem("//", "COMMENT", isHighlighted = true),
            QuickActionItem("DelLn", "DELETE_LINE", isHighlighted = true),
            QuickActionItem("Ln↑", "MOVE_LINE_UP", isHighlighted = true),
            QuickActionItem("Ln↓", "MOVE_LINE_DOWN", isHighlighted = true),

            // ৩. ব্র্যাকেট ও পেয়ারস
            QuickActionItem("{", "INSERT", "{"),
            QuickActionItem("}", "INSERT", "}"),
            QuickActionItem("(", "INSERT", "("),
            QuickActionItem(")", "INSERT", ")"),
            QuickActionItem("[", "INSERT", "["),
            QuickActionItem("]", "INSERT", "]"),
            QuickActionItem("<", "INSERT", "<"),
            QuickActionItem(">", "INSERT", ">"),

            // ৪. কোটেশন ও সিম্বলস
            QuickActionItem("\"", "INSERT", "\""),
            QuickActionItem("'", "INSERT", "'"),
            QuickActionItem("`", "INSERT", "`"),
            QuickActionItem(";", "INSERT", ";"),
            QuickActionItem(":", "INSERT", ": "),
            QuickActionItem(",", "INSERT", ", "),
            QuickActionItem(".", "INSERT", "."),
            QuickActionItem("?", "INSERT", "?"),
            QuickActionItem("!", "INSERT", "!"),
            QuickActionItem("@", "INSERT", "@"),
            QuickActionItem("#", "INSERT", "#"),
            QuickActionItem("$", "INSERT", "$"),
            QuickActionItem("_", "INSERT", "_"),
            QuickActionItem("\\", "INSERT", "\\"),
            QuickActionItem("/", "INSERT", "/"),

            // ৫. অপারেটর ও লজিক সিম্বলস
            QuickActionItem("=", "INSERT", " = "),
            QuickActionItem("==", "INSERT", " == "),
            QuickActionItem("!=", "INSERT", " != "),
            QuickActionItem("->", "INSERT", " -> "),
            QuickActionItem("=>", "INSERT", " => "),
            QuickActionItem("::", "INSERT", "::"),
            QuickActionItem("+", "INSERT", " + "),
            QuickActionItem("-", "INSERT", " - "),
            QuickActionItem("*", "INSERT", " * "),
            QuickActionItem("%", "INSERT", " % "),
            QuickActionItem("&", "INSERT", "&"),
            QuickActionItem("&&", "INSERT", " && "),
            QuickActionItem("|", "INSERT", "|"),
            QuickActionItem("||", "INSERT", " || ")
        )
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF161616))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(quickItems, key = { it.label }) { item ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (item.isHighlighted) Color(0xFF2B3322) else Color(0xFF262626))
                    .border(
                        1.dp,
                        if (item.isHighlighted) NeonLime.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onAction(item) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.label,
                    color = if (item.isHighlighted) NeonLime else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}