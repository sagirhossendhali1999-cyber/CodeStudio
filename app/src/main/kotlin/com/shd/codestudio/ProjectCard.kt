package com.shd.codestudio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PremiumProjectCard(
    project: Project, 
    onMenuClick: () -> Unit
) {
    val gradientBackground = Brush.linearGradient(
        colors = listOf(
            Color(0xFFA855F7).copy(alpha = 0.12f),
            SurfaceDark,
            NeonLime.copy(alpha = 0.08f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(gradientBackground)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(project.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(project.color, RoundedCornerShape(6.dp))
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Texts
            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(project.type, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            
            // শুধু 3-Dot Button (সার্কেল বাদ দেওয়া হয়েছে)
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Outlined.MoreVert, 
                    contentDescription = "Options", 
                    tint = TextPrimary, 
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}