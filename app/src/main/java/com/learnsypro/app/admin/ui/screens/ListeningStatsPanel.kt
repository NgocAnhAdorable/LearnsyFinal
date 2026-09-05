package com.learnsypro.app.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.admin.data.ListeningItem
import com.learnsypro.app.admin.ui.theme.LearnsyColors

// Tương đương StatsPanel trong listening-panel.jsx
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListeningStatsPanel(items: List<ListeningItem>, colors: LearnsyColors) {
    val totalBlanks = items.sumOf { it.answers.size }
    val totalStmts = items.sumOf { it.statements.size }
    val totalWords = items.sumOf { it.wordBox.size }
    val withWB = items.count { it.wordBox.isNotEmpty() }
    val withTFNM = items.count { it.statements.isNotEmpty() }

    val stats = listOf(
        Triple("Tổng câu", items.size, colors.lav),
        Triple("Chỗ trống", totalBlanks, Color(0xFF059669)),
        Triple("T/F/NM", totalStmts, Color(0xFFDC2626)),
        Triple("Từ WB", totalWords, Color(0xFF4338CA)),
        Triple("Có WB", withWB, Color(0xFF7C3AED)),
        Triple("Có T/F", withTFNM, Color(0xFFB45309))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.lavPale)
            .border(1.5.dp, colors.border2, RoundedCornerShape(14.dp))
            .padding(14.dp, 12.dp)
    ) {
        Text("THỐNG KÊ", fontSize = 11.sp, fontWeight = FontWeight.Black, color = colors.lav, modifier = Modifier.padding(bottom = 10.dp))
        FlowRow {
            stats.forEach { (label, value, color) ->
                Column(
                    modifier = Modifier
                        .padding(3.dp)
                        .width(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.5.dp, colors.border2, RoundedCornerShape(12.dp))
                        .padding(10.dp, 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("$value", fontSize = 18.sp, fontWeight = FontWeight.Black, color = color)
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.text3)
                }
            }
        }
    }
}
