package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.VibeDesignTokens

@Composable
fun EngCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val gridColor = VibeDesignTokens.Color.Grid
    Card(
        modifier = modifier.drawBehind {
            val step = 20.dp.toPx()
            for (x in 0 until (size.width / step).toInt()) {
                drawLine(
                    color = gridColor,
                    start = Offset(x * step, 0f),
                    end = Offset(x * step, size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0 until (size.height / step).toInt()) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y * step),
                    end = Offset(size.width, y * step),
                    strokeWidth = 1f
                )
            }
        },
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Panel),
        colors = CardDefaults.cardColors(
            containerColor = VibeDesignTokens.Color.SpacePanel
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(VibeDesignTokens.Spacing.Large),
            content = content
        )
    }
}

@Composable
fun EngButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(VibeDesignTokens.Shape.Control),
        colors = ButtonDefaults.buttonColors(
            containerColor = VibeDesignTokens.Color.Cyan,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = VibeDesignTokens.Color.SpacePanelRaised,
            disabledContentColor = VibeDesignTokens.Color.TextMuted
        ),
        content = content
    )
}

@Composable
fun TerminalPanel(
    title: String,
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VibeDesignTokens.Shape.Compact))
            .background(VibeDesignTokens.Color.SpaceBackground)
            .border(
                1.dp,
                VibeDesignTokens.Color.Grid,
                RoundedCornerShape(VibeDesignTokens.Shape.Compact)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibeDesignTokens.Color.SpacePanelRaised)
                .padding(
                    horizontal = VibeDesignTokens.Spacing.Medium,
                    vertical = VibeDesignTokens.Spacing.Small
                )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = VibeDesignTokens.Color.TextMuted,
                fontFamily = JetBrainsMono
            )
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .padding(VibeDesignTokens.Spacing.Medium)
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                logs.forEach { log ->
                    Text(
                        text = "> $log",
                        style = MaterialTheme.typography.labelMedium,
                        color = VibeDesignTokens.Color.Success,
                        fontFamily = JetBrainsMono
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = VibeDesignTokens.Spacing.Small)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
        ) {
            drawLine(
                color = VibeDesignTokens.Color.Grid,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2f
            )
            drawLine(
                color = VibeDesignTokens.Color.Cyan,
                start = Offset(0f, 0f),
                end = Offset(40.dp.toPx(), 0f),
                strokeWidth = 4f
            )
        }
    }
}
