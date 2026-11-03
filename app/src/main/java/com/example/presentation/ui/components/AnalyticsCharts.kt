package com.example.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CategoryCount
import com.example.domain.model.PeriodComparison
import com.example.domain.model.TimeSeriesPoint
import java.util.Locale

@Composable
fun TimeSeriesBarChart(
    points: List<TimeSeriesPoint>,
    onPointClick: (TimeSeriesPoint) -> Unit,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "لا توجد بيانات زمنية لهذه الفترة",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
        return
    }

    val maxCount = points.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val density = LocalDensity.current

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val bottomPadding = 30f
                val chartHeight = canvasHeight - bottomPadding

                val barSlotWidth = canvasWidth / points.size
                val barWidth = (barSlotWidth * 0.55f).coerceAtLeast(12f).coerceAtMost(36f)

                // Draw horizontal subtle grid lines
                val gridPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    alpha = 60
                    strokeWidth = 1f
                }
                for (step in 1..3) {
                    val y = chartHeight * (1f - step / 3f)
                    drawContext.canvas.nativeCanvas.drawLine(0f, y, canvasWidth, y, gridPaint)
                }

                val textPaint = android.graphics.Paint().apply {
                    color = textColor.toArgb()
                    textSize = with(density) { 10.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }

                val valuePaint = android.graphics.Paint().apply {
                    color = barColor.toArgb()
                    textSize = with(density) { 10.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                    isAntiAlias = true
                }

                points.forEachIndexed { index, point ->
                    val centerX = (index * barSlotWidth) + (barSlotWidth / 2f)
                    val barHeightFraction = point.count.toFloat() / maxCount.toFloat()
                    val barActualHeight = (chartHeight * barHeightFraction).coerceAtLeast(if (point.count > 0) 6f else 2f)
                    val barTop = chartHeight - barActualHeight
                    val barLeft = centerX - (barWidth / 2f)

                    // Draw vertical bar with rounded top
                    drawRoundRect(
                        color = if (point.count > 0) barColor else barColor.copy(alpha = 0.2f),
                        topLeft = Offset(barLeft, barTop),
                        size = Size(barWidth, barActualHeight),
                        cornerRadius = CornerRadius(8f, 8f)
                    )

                    // Draw value above bar if > 0
                    if (point.count > 0) {
                        drawContext.canvas.nativeCanvas.drawText(
                            point.count.toString(),
                            centerX,
                            (barTop - 6f).coerceAtLeast(14f),
                            valuePaint
                        )
                    }

                    // Draw x-axis label
                    drawContext.canvas.nativeCanvas.drawText(
                        point.label,
                        centerX,
                        canvasHeight - 6f,
                        textPaint
                    )
                }
            }
        }
    }
}

@Composable
fun HorizontalDistributionList(
    items: List<CategoryCount>,
    onItemClick: (CategoryCount) -> Unit,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { item ->
            val animatedProgress by animateFloatAsState(
                targetValue = (item.percentage / 100f).coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 600),
                label = "bar_progress"
            )

            Card(
                onClick = { onItemClick(item) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = barColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${item.count}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = barColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.1f%%", item.percentage),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(barColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OsSplitComparisonCard(
    androidCount: Int,
    iosCount: Int,
    otherCount: Int,
    onAndroidClick: () -> Unit,
    onIosClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val total = (androidCount + iosCount + otherCount).coerceAtLeast(1)
    val androidPct = (androidCount.toFloat() / total) * 100f
    val iosPct = (iosCount.toFloat() / total) * 100f

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "توزيع الانهيارات حسب النظام",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "إجمالي: $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Dual split progress bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.LightGray.copy(alpha = 0.3f))
            ) {
                if (androidCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(androidPct.coerceAtLeast(1f))
                            .fillMaxHeight()
                            .background(Color(0xFF3DDC84)) // Android green
                    )
                }
                if (iosCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(iosPct.coerceAtLeast(1f))
                            .fillMaxHeight()
                            .background(Color(0xFF007AFF)) // iOS blue
                    )
                }
            }

            // Interactive clickable breakdown buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedCard(
                    onClick = onAndroidClick,
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF3DDC84).copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF3DDC84), CircleShape))
                            Text("Android", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("$androidCount انهيار", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(String.format(Locale.US, "%.1f%%", androidPct), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                OutlinedCard(
                    onClick = onIosClick,
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF007AFF).copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF007AFF), CircleShape))
                            Text("iOS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("$iosCount انهيار", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(String.format(Locale.US, "%.1f%%", iosPct), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ComparisonCard(
    title: String,
    comparison: PeriodComparison,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGood = comparison.changePercentage <= 0f // reduction in crashes is good
    val deltaColor = if (isGood) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    val deltaIcon = if (isGood) Icons.Default.TrendingDown else Icons.Default.TrendingUp
    val sign = if (comparison.changePercentage > 0) "+" else ""

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Surface(
                    color = deltaColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(deltaIcon, contentDescription = null, tint = deltaColor, modifier = Modifier.size(16.dp))
                        Text(
                            text = "$sign${String.format(Locale.US, "%.1f%%", comparison.changePercentage)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = deltaColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(comparison.labelCurrent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${comparison.currentCount}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("انهيارات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }

                Text("مقارنة بـ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(comparison.labelPrevious, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${comparison.previousCount}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("انهيارات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
