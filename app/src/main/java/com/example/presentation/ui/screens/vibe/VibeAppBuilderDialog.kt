package com.example.presentation.ui.screens.vibe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.*

/**
 * Modern Engineering Dialog for AI App Builder.
 * Converts natural language prompt into full working applications.
 * Adheres strictly to requirements:
 * - Requirements analysis (Type, Features, Screens, Schema, Roles, Security, Justified Tech Stack)
 * - Interactive Blueprint customization before execution
 * - Real-time progress pipeline
 * - Integration with Crash Intelligence, Sandbox and Live Preview
 */
@Composable
fun VibeAppBuilderDialog(
    prompt: String,
    preferredPlatform: String,
    blueprint: AppBlueprint?,
    isAnalyzing: Boolean,
    buildProgress: AppBuildProgress?,
    onPromptChange: (String) -> Unit,
    onPlatformChange: (String) -> Unit,
    onAnalyze: (String, String) -> Unit,
    onUpdateBlueprint: (AppBlueprint) -> Unit,
    onBuildApp: (AppBlueprint) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (buildProgress == null || buildProgress.stage == AppBuildStage.COMPLETED || buildProgress.stage == AppBuildStage.FAILED) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Build with AI – من الفكرة إلى تطبيق يعمل",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "تحليل المتطلبات، بناء الواجهات الحديثة، وهيكلة قاعدة البيانات تلقائيًا",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = buildProgress == null || buildProgress.stage == AppBuildStage.COMPLETED || buildProgress.stage == AppBuildStage.FAILED
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                // Body content based on state
                Box(modifier = Modifier.weight(1f).padding(20.dp)) {
                    when {
                        // Stage 3: Live Build Pipeline
                        buildProgress != null -> {
                            BuildPipelineView(buildProgress)
                        }

                        // Stage 2: Interactive Blueprint Review
                        blueprint != null -> {
                            BlueprintReviewView(
                                blueprint = blueprint,
                                onUpdateBlueprint = onUpdateBlueprint,
                                onBack = { onUpdateBlueprint(blueprint.copy(id = "")) /* Back to step 1 */ },
                                onConfirmBuild = { onBuildApp(blueprint) }
                            )
                        }

                        // Stage 1: Prompt Input & Analysis
                        else -> {
                            PromptInputView(
                                prompt = prompt,
                                preferredPlatform = preferredPlatform,
                                isAnalyzing = isAnalyzing,
                                onPromptChange = onPromptChange,
                                onPlatformChange = onPlatformChange,
                                onAnalyze = onAnalyze
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptInputView(
    prompt: String,
    preferredPlatform: String,
    isAnalyzing: Boolean,
    onPromptChange: (String) -> Unit,
    onPlatformChange: (String) -> Unit,
    onAnalyze: (String, String) -> Unit
) {
    val platforms = listOf(
        "Web Full-Stack",
        "Mobile Multiplatform",
        "Single Page App",
        "Android Native",
        "Backend API"
    )

    val ideaChips = listOf(
        "🛒 متجر إلكتروني لعرض المنتجات وإدارة الطلبات وحساب المستخدمين",
        "📋 تطبيق لإدارة المهام والمشاريع مع Flutter وتخزين محلي",
        "📊 لوحة تحكم SaaS تفاعلية مع رسوم بيانية ومصادقة مستخدمين",
        "🍕 تطبيق طلب وجبات ومأكولات مع تتبع الطلبات المباشر",
        "📝 منصة تدوين ومقالات تقنية تدعم Markdown والوضع الليلي"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "صف فكرة تطبيقك أو موقعك بالتفصيل:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = {
                Text(
                    "اكتب وصفًا كاملًا لوظائف التطبيق والشاشات المطلوبة وقاعدة البيانات...\n\n" +
                    "مثال: أنشئ متجرًا إلكترونيًا لعرض المنتجات وإدارة الطلبات وحساب المستخدمين وسلة مشتريات مع لوحة تحكم إدارية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(14.dp))

        // Target Platform Selection
        Text(
            "المنصة المستهدفة المفضلة:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            platforms.forEach { platform ->
                FilterChip(
                    selected = preferredPlatform == platform,
                    onClick = { onPlatformChange(platform) },
                    label = { Text(platform, fontSize = 12.sp) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Quick Idea Chips
        Text(
            "أفكار سريعة ومقترحة:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ideaChips.forEach { chipText ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { onPromptChange(chipText) }
                ) {
                    Text(
                        chipText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Action Button
        Button(
            onClick = { onAnalyze(prompt, preferredPlatform) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = prompt.trim().isNotBlank() && !isAnalyzing,
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("جاري استخراج المتطلبات وتصميم الهيكل المعماري...")
            } else {
                Icon(Icons.Default.Psychology, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("تحليل المتطلبات وصياغة الهيكل الهندسي (Analyze Blueprint)")
            }
        }
    }
}

@Composable
private fun BlueprintReviewView(
    blueprint: AppBlueprint,
    onUpdateBlueprint: (AppBlueprint) -> Unit,
    onBack: () -> Unit,
    onConfirmBuild: () -> Unit
) {
    var projectName by remember { mutableStateOf(blueprint.name) }
    var selectedFeatures by remember { mutableStateOf(blueprint.coreFeatures.toSet()) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Blueprint banner
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("تم استخراج المخطط الهندسي للمشروع بنجاح", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("راجع التفاصيل والشاشات وقواعد البيانات أدناه، ويمكنك تعديلها قبل البدء في إنشاء الملفات.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Project Name & Tech Stack
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("بيانات المشروع والتقنية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = projectName,
                            onValueChange = {
                                projectName = it
                                onUpdateBlueprint(blueprint.copy(name = it))
                            },
                            label = { Text("اسم المشروع") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("نوع التطبيق", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(blueprint.appType, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("حزمة التقنيات المقترحة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(blueprint.suggestedTechStack, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (blueprint.techStackJustification.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Text(
                                    "💡 مبرر التقنية: ${blueprint.techStackJustification}",
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // Core Features
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("الوظائف الرئيسية (Core Features)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        blueprint.coreFeatures.forEach { feature ->
                            val isChecked = selectedFeatures.contains(feature)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFeatures = if (isChecked) selectedFeatures - feature else selectedFeatures + feature
                                        onUpdateBlueprint(blueprint.copy(coreFeatures = selectedFeatures.toList()))
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedFeatures = if (checked) selectedFeatures + feature else selectedFeatures - feature
                                        onUpdateBlueprint(blueprint.copy(coreFeatures = selectedFeatures.toList()))
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(feature, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Screens & Routes
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("الشاشات ومسارات التنقل (${blueprint.screens.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0x204CAF50)
                            ) {
                                Text("RTL / LTR متوافق", color = Color(0xFF4CAF50), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        blueprint.screens.forEach { screen ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(screen.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(screen.purpose, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                screen.route,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Database Schema & Entities
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("قاعدة البيانات والنماذج (Database Schema)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        blueprint.databaseSchema.forEach { entity ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("جدول / Entity: ${entity.name}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            entity.fields.joinToString(" • "),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Crash Intelligence & Security
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("معايير الأمان والتكاملات المضمنة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x2010B981),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("تكامل مسبق مع Crash Intelligence SDK", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF10B981))
                                    Text("سيتم تضمين محرك رصد الأخطاء التلقائي فور تشغيل المشروع.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }

                        blueprint.securityRequirements.forEach { sec ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.width(6.dp))
                                Text(sec, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Confirm & Back Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("تعديل الوصف")
            }

            Button(
                onClick = onConfirmBuild,
                modifier = Modifier.weight(2f).height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.RocketLaunch, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("تأكيد وبناء التطبيق الفعلي")
            }
        }
    }
}

@Composable
private fun BuildPipelineView(progress: AppBuildProgress) {
    val stages = listOf(
        AppBuildStage.SCAFFOLDING_FILES to "إنشاء مساحة العمل وهيكلة المجلدات الأساسية",
        AppBuildStage.CREATING_UI_COMPONENTS to "توليد الشاشات والمكونات ونظام التصميم المتجاوب",
        AppBuildStage.WIRING_DATA_SERVICES to "ربط طبقة البيانات والتخزين المحلي ومسارات التنقل",
        AppBuildStage.CONFIGURING_CRASH_INTELLIGENCE to "تفعيل حزمة Crash Intelligence SDK لرصد الأعطال",
        AppBuildStage.STARTING_PREVIEW to "تشغيل خادم التطوير والمعاينة الحية المباشرة"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.progressPercent },
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
                Text(
                    "${(progress.progressPercent * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "جاري بناء تطبيقك الفعلي...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            progress.currentTask,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Stages checklist
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stages.forEachIndexed { index, (stage, label) ->
                    val isDone = progress.stage.ordinal > stage.ordinal || progress.stage == AppBuildStage.COMPLETED
                    val isCurrent = progress.stage == stage

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                isDone -> Color(0xFF4CAF50)
                                isCurrent -> MaterialTheme.colorScheme.primary
                                else -> Color.Gray.copy(alpha = 0.3f)
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isDone) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp), tint = Color.White)
                                } else {
                                    Text("${index + 1}", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrent || isDone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
