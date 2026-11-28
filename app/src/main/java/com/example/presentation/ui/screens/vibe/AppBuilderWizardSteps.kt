package com.example.presentation.ui.screens.vibe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.*
import com.example.ui.theme.VibeDesignTokens

internal data class PlatformOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val stackSummary: String,
    val icon: ImageVector
)

private val WIZARD_PLATFORMS = listOf(
    PlatformOption(
        id = "Web Full-Stack",
        title = "Web Full-Stack",
        subtitle = "تطبيق ويب متكامل بواجهات أمامية وخلفية وقاعدة بيانات",
        stackSummary = "Next.js + Tailwind + TypeScript",
        icon = Icons.Default.Language
    ),
    PlatformOption(
        id = "Android Native",
        title = "Android Native",
        subtitle = "تطبيق أندرويد أصلي بواجهات Material 3 حديثة",
        stackSummary = "Kotlin + Jetpack Compose + Room",
        icon = Icons.Default.Android
    ),
    PlatformOption(
        id = "Mobile Multiplatform",
        title = "Mobile Multiplatform",
        subtitle = "تطبيق جوال مشترك لأنظمة Android و iOS",
        stackSummary = "Flutter + Dart",
        icon = Icons.Default.PhoneAndroid
    ),
    PlatformOption(
        id = "Single Page App",
        title = "Single Page App",
        subtitle = "لوحة تحكم أو تطبيق ويب تفاعلي سريع الاستجابة",
        stackSummary = "React + Vite + TypeScript",
        icon = Icons.Default.WebAsset
    ),
    PlatformOption(
        id = "Backend API",
        title = "Backend API",
        subtitle = "خدمة API ومصادقة ونماذج بيانات مهيكلة",
        stackSummary = "Ktor / Node API + JSON",
        icon = Icons.Default.Storage
    )
)

@Composable
internal fun ChoosePlatformStep(
    selectedPlatform: String,
    onSelectPlatform: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "اختر المنصة المستهدفة",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "حدد بيئة التشغيل أولاً حتى نصمم المخطط والحزم التقنية بما يناسبها تماماً.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(WIZARD_PLATFORMS, key = { it.id }) { option ->
                val isSelected = selectedPlatform == option.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPlatform(option.id) }
                        .testTag("wizard_platform_${option.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        }
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = option.title,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = option.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = option.stackSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectPlatform(option.id) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("wizard_next_button"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(stringResource(R.string.builder_next))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
internal fun DescribeAppStep(
    selectedPlatform: String,
    prompt: String,
    isAnalyzing: Boolean,
    onPromptChange: (String) -> Unit,
    onBack: () -> Unit,
    onAnalyze: () -> Unit
) {
    val ideaSuggestions = remember(selectedPlatform) {
        when (selectedPlatform) {
            "Android Native" -> listOf(
                "تطبيق لتتبع الميزانية والمصروفات اليومية مع تنبيهات الميزانية",
                "تطبيق لإدارة العادات اليومية والمهام مع إحصائيات التقدم",
                "تطبيق لقراءة الأخبار التقنية وحفظ المقالات للمطالعة دون اتصال"
            )
            "Mobile Multiplatform" -> listOf(
                "تطبيق لطلب الوجبات وتتبع حالة التوصيل خطوة بخطوة",
                "تطبيق لإدارة المهام والمشاريع الجماعية وتوزيع الأدوار",
                "تطبيق لحجز المواعيد وإدارة تنبيهات العيادة"
            )
            "Backend API" -> listOf(
                "خدمة API لإدارة الطلبات والمخزون مع مصادقة JWT وصلاحيات الأدوار",
                "واجهة برمجية لمنصة حجوزات وفواتير مع سجلات تدقيق"
            )
            else -> listOf(
                "متجر إلكتروني لعرض المنتجات وإدارة الطلبات وحساب المستخدمين",
                "لوحة تحكم SaaS تفاعلية مع رسوم بيانية وإدارة الاشتراكات",
                "منصة تدوين ومقالات تقنية تدعم Markdown والتصنيفات"
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "صف فكرة التطبيق",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            AssistChip(
                onClick = onBack,
                label = { Text(selectedPlatform) },
                leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("wizard_prompt_input"),
            placeholder = {
                Text(
                    "اكتب وصفاً واضحاً لوظائف التطبيق، الشاشات الأساسية، ونوع البيانات المطلوبة...",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "اقتراحات متوافقة مع $selectedPlatform:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ideaSuggestions.forEach { idea ->
                SuggestionChip(
                    onClick = { onPromptChange(idea) },
                    label = { Text(idea, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isAnalyzing,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("wizard_back_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.builder_back))
            }

            Button(
                onClick = onAnalyze,
                enabled = !isAnalyzing,
                modifier = Modifier
                    .weight(2f)
                    .heightIn(min = 48.dp)
                    .testTag("wizard_analyze_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("جارٍ التحليل...")
                } else {
                    Icon(Icons.Default.Psychology, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("تحليل المخطط")
                }
            }
        }
    }
}

@Composable
internal fun ReviewBlueprintStep(
    blueprint: AppBlueprint,
    onUpdateBlueprint: (AppBlueprint) -> Unit,
    onBack: () -> Unit,
    onConfirmBuild: () -> Unit
) {
    var projectName by remember(blueprint.id) { mutableStateOf(blueprint.name) }
    var selectedFeatures by remember(blueprint.id) { mutableStateOf(blueprint.coreFeatures.toSet()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "مراجعة المخطط الهندسي",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "تحقق من الاسم والميزات والشاشات وجداول البيانات قبل بدء التوليد الفعلي.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = projectName,
                            onValueChange = {
                                projectName = it
                                onUpdateBlueprint(blueprint.copy(name = it))
                            },
                            label = { Text(stringResource(R.string.project_name)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wizard_project_name_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("المنصة", style = MaterialTheme.typography.labelSmall)
                                    Text(blueprint.targetPlatform, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("الحزمة التقنية", style = MaterialTheme.typography.labelSmall)
                                    Text(blueprint.suggestedTechStack, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (blueprint.techStackJustification.isNotBlank()) {
                            Text(
                                text = blueprint.techStackJustification,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (blueprint.coreFeatures.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "الميزات الأساسية",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            blueprint.coreFeatures.forEach { feature ->
                                val isChecked = selectedFeatures.contains(feature)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable {
                                            selectedFeatures = if (isChecked) selectedFeatures - feature else selectedFeatures + feature
                                            onUpdateBlueprint(blueprint.copy(coreFeatures = selectedFeatures.toList()))
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedFeatures = if (checked) selectedFeatures + feature else selectedFeatures - feature
                                            onUpdateBlueprint(blueprint.copy(coreFeatures = selectedFeatures.toList()))
                                        }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(feature, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            if (blueprint.screens.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "الشاشات (${blueprint.screens.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            blueprint.screens.forEach { screen ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(screen.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                        Text(screen.purpose, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            screen.route,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (blueprint.databaseSchema.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "نماذج البيانات (${blueprint.databaseSchema.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            blueprint.databaseSchema.forEach { entity ->
                                Text(
                                    text = "${entity.name}: ${entity.fields.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("wizard_back_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.builder_back))
            }

            Button(
                onClick = onConfirmBuild,
                enabled = projectName.isNotBlank(),
                modifier = Modifier
                    .weight(2f)
                    .heightIn(min = 48.dp)
                    .testTag("wizard_build_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.RocketLaunch, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.builder_step_build))
            }
        }
    }
}

@Composable
internal fun BuildStep(
    progress: AppBuildProgress,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    val stages = listOf(
        AppBuildStage.SCAFFOLDING_FILES to "إنشاء مساحة العمل وهيكلة الملفات الأساسية",
        AppBuildStage.CREATING_UI_COMPONENTS to "توليد الواجهات والمكونات المتجاوبة",
        AppBuildStage.WIRING_DATA_SERVICES to "ربط طبقة البيانات ومسارات التنقل",
        AppBuildStage.CONFIGURING_CRASH_INTELLIGENCE to "تهيئة تكامل Crash Intelligence SDK",
        AppBuildStage.STARTING_PREVIEW to "تجهيز مساحة التطوير والمعاينة"
    )
    val isFailed = progress.stage == AppBuildStage.FAILED

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = if (isFailed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isFailed) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(34.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { progress.progressPercent.coerceIn(0f, 1f) },
                        modifier = Modifier.size(72.dp),
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = "${(progress.progressPercent * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isFailed) "تعذر إكمال البناء" else stringResource(R.string.building_project),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = progress.currentTask.ifBlank { "جارٍ تجهيز المشروع..." },
            style = MaterialTheme.typography.bodySmall,
            color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                stages.forEachIndexed { index, (stage, label) ->
                    val isDone = !isFailed && (progress.stage.ordinal > stage.ordinal || progress.stage == AppBuildStage.COMPLETED)
                    val isCurrent = !isFailed && progress.stage == stage
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                isDone -> VibeDesignTokens.Color.Success
                                isCurrent -> MaterialTheme.colorScheme.primary
                                else -> Color.Gray.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isDone) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(13.dp), tint = Color.White)
                                } else {
                                    Text("${index + 1}", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent || isDone) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (isFailed) {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("wizard_back_button")
                ) {
                    Text(stringResource(R.string.builder_back))
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("wizard_retry_build_button")
                ) {
                    Text("إعادة المحاولة")
                }
            }
        }
    }
}

@Composable
internal fun OpenIdeStep(
    workspace: Workspace?,
    fileCount: Int,
    onOpenIde: () -> Unit,
    onStartNewBuild: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = VibeDesignTokens.Color.Success.copy(alpha = 0.16f),
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = VibeDesignTokens.Color.Success,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.builder_built_success_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (workspace != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(workspace.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(workspace.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("التقنية: ${workspace.techStack}", style = MaterialTheme.typography.labelMedium)
                        Text("الملفات المنشأة: $fileCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOpenIde,
            enabled = workspace != null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("wizard_open_ide_button"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Code, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.builder_open_ide_cta))
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onStartNewBuild,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("wizard_new_build_button"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("بناء مشروع جديد")
        }
    }
}
