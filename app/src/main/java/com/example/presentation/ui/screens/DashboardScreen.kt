package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.domain.model.CrashReport
import com.example.domain.model.Severity
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.DashboardViewModel

import androidx.compose.ui.res.stringResource
import com.example.R

import com.example.ui.components.EngCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.EngGridLine
import com.example.ui.theme.JetBrainsMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    onSignOut: () -> Unit,
    viewModel: DashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNotificationsSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFF070B18),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF090F20),
                    titleContentColor = Color(0xFFEAF4FF),
                    actionIconContentColor = Color(0xFF8DEBFF)
                ),
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.DeveloperMode, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.dashboard),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showNotificationsSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadNotificationCount > 0) {
                                    Badge {
                                        Text(uiState.unreadNotificationCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "الإشعارات")
                        }
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign Out")
                    }
                }
            )
        },

        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0B1224),
                contentColor = Color(0xFFB8C8E8),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Dashboard, null) },
                    label = { Text(stringResource(R.string.dashboard)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Projects) },
                    icon = { Icon(Icons.Default.Folder, null) },
                    label = { Text(stringResource(R.string.projects)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Crashes()) },
                    icon = { Icon(Icons.Default.BugReport, null) },
                    label = { Text(stringResource(R.string.crashes)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Issues()) },
                    icon = { Icon(Icons.Default.Warning, null) },
                    label = { Text(stringResource(R.string.issues)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Settings) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text(stringResource(R.string.settings)) }
                )
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF070B18))) {
                CosmicDashboardBackdrop(modifier = Modifier.fillMaxSize())
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
                ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        uiState.currentUser?.let { user ->
                            NeonPanel(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = CircleShape,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.VerifiedUser, 
                                                null, 
                                                tint = MaterialTheme.colorScheme.primary, 
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                "مرحبًا، ${user.displayName}", 
                                                fontWeight = FontWeight.Bold, 
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                "بيئة مطور معزولة ومشفرة | SECURE_ENV_01", 
                                                style = MaterialTheme.typography.labelSmall, 
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontFamily = JetBrainsMono
                                            )
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "ENCRYPTED",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = JetBrainsMono,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                item {
                    SectionHeader(title = stringResource(R.string.overview))
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            label = stringResource(R.string.projects),
                            value = uiState.projectCount.toString(),
                            icon = Icons.Default.Folder,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = stringResource(R.string.issues),
                            value = uiState.issueCount.toString(),
                            icon = Icons.Default.Warning,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = stringResource(R.string.crashes),
                            value = uiState.recentCrashes.size.toString(),
                            icon = Icons.Default.BugReport,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                item {
                    SectionHeader(title = "أدوات الهندسة والذكاء الاصطناعي")
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EngineeringToolCard(
                            title = "AI Vibe Studio",
                            subtitle = "بيئة تطوير ذكية لبناء التطبيقات باللغة الطبيعية (Vibe Coding)",
                            icon = Icons.Default.AutoAwesome,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { navController.navigate(Screen.VibeStudio) }
                        )
                        
                        EngineeringToolCard(
                            title = "AI Model Teams",
                            subtitle = "تعاون ذكاء اصطناعي متعدد النماذج للمهام المعقدة والـ Multi-Agent",
                            icon = Icons.Default.GroupWork,
                            color = MaterialTheme.colorScheme.tertiary,
                            onClick = { navController.navigate(Screen.ModelTeams) }
                        )

                        EngineeringToolCard(
                            title = stringResource(R.string.dev_debug_center),
                            subtitle = stringResource(R.string.dev_debug_center_desc),
                            icon = Icons.Default.Healing,
                            color = MaterialTheme.colorScheme.secondary,
                            onClick = { navController.navigate(Screen.DevDebugCenter()) }
                        )
                    }
                }

                item {
                    SectionHeader(title = "تحليل الأعطال والاختبار")
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SquareToolCard(
                                title = stringResource(R.string.analytics_title),
                                icon = Icons.Default.BarChart,
                                modifier = Modifier.weight(1f),
                                onClick = { navController.navigate(Screen.Analytics) }
                            )
                            SquareToolCard(
                                title = stringResource(R.string.live_testing),
                                icon = Icons.Default.PlayCircle,
                                modifier = Modifier.weight(1f),
                                onClick = { navController.navigate(Screen.LiveTesting) }
                            )
                        }
                        
                        EngineeringToolCard(
                            title = "بوابة الاعتماد وجودة المنتج (Release Gate)",
                            subtitle = "فحص 13 معيارًا هندسيًا، التحقق من الشاشات الـ 26، ومنع الإصدارات غير المكتملة",
                            icon = Icons.Default.FactCheck,
                            color = Color(0xFF3FB950),
                            onClick = { navController.navigate(Screen.ProductQualityCenter) }
                        )

                        EngineeringToolCard(
                            title = "مركز التحقق والاعتماد (AI Teams E2E)",
                            subtitle = "فحص 20 معيارًا للتعاون الفعلي، عزل المفاتيح، والـ Sandboxes",
                            icon = Icons.Default.Verified,
                            color = MaterialTheme.colorScheme.secondary,
                            onClick = { navController.navigate(Screen.TeamValidation) }
                        )
                    }
                }

                
                item {
                    SectionHeader(title = stringResource(R.string.recent_crashes))
                }
                
                if (uiState.recentCrashes.isEmpty()) {
                    item {
                        EngCard(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.no_recent_crashes), textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    items(uiState.recentCrashes) { crash ->
                        CrashItem(crash = crash, onClick = {
                            navController.navigate(Screen.CrashDetail(crash.id))
                        })
                    }
                }
                }
            }
        }

        if (showNotificationsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showNotificationsSheet = false }
            ) {
                NotificationCenterContent(uiState, viewModel, navController) {
                    showNotificationsSheet = false
                }
            }
        }
    }
}

@Composable
private fun CosmicDashboardBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF101B39), Color(0xFF080E1E), Color(0xFF050914))
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x553D55C8), Color(0x003D55C8)),
                center = Offset(size.width * 0.82f, size.height * 0.12f),
                radius = size.minDimension * 0.62f
            ),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width * 0.82f, size.height * 0.12f)
        )
        val grid = Color(0x183D83B8)
        val step = 28.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.7.dp.toPx())
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.7.dp.toPx())
            y += step
        }
        val stars = listOf(
            Offset(size.width * .12f, size.height * .08f),
            Offset(size.width * .72f, size.height * .22f),
            Offset(size.width * .32f, size.height * .38f),
            Offset(size.width * .91f, size.height * .51f),
            Offset(size.width * .18f, size.height * .72f),
            Offset(size.width * .61f, size.height * .84f)
        )
        stars.forEach { drawCircle(Color(0x998DEBFF), radius = 1.4.dp.toPx(), center = it) }
    }
}

@Composable
private fun NeonPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .border(1.dp, Color(0x6656CFE8), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD9101B31)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0x331B3D68), Color(0x11101B31), Color(0x22152A49))
                    )
                )
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun EngineeringToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    EngCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun SquareToolCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    EngCard(
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    EngCard(
        modifier = modifier
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMono)
        }
    }
}

@Composable
fun CrashItem(
    crash: CrashReport,
    onClick: () -> Unit
) {
    EngCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val severityColor = when (crash.severity) {
                Severity.LOW -> MaterialTheme.colorScheme.primary
                Severity.MEDIUM -> Color(0xFFD29922)
                Severity.HIGH -> Color(0xFFF85149)
                Severity.CRITICAL -> Color(0xFFF85149)
            }
            
            Box(
                modifier = Modifier
                    .size(4.dp, 32.dp)
                    .background(severityColor, RoundedCornerShape(2.dp))
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    crash.exceptionType, 
                    style = MaterialTheme.typography.titleSmall, 
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMono
                )
                Text(
                    crash.message, 
                    style = MaterialTheme.typography.bodySmall, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun NotificationCenterContent(
    uiState: com.example.presentation.viewmodel.DashboardUiState,
    viewModel: DashboardViewModel,
    navController: NavController,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "مركز الإشعارات والتنبيهات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row {
                if (uiState.notifications.isNotEmpty()) {
                    TextButton(onClick = { viewModel.markAllNotificationsAsRead() }) {
                        Text("قراءة الكل")
                    }
                    TextButton(onClick = { viewModel.clearAllNotifications() }) {
                        Text("مسح", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "لا توجد إشعارات جديدة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
            ) {
                items(uiState.notifications, key = { it.id }) { notif ->
                    val notifColor = when (notif.type) {
                        com.example.domain.model.NotificationType.CRASH_ALERT -> MaterialTheme.colorScheme.error
                        com.example.domain.model.NotificationType.ANR_ALERT -> Color(0xFFED6C02)
                        com.example.domain.model.NotificationType.FIX_READY -> MaterialTheme.colorScheme.primary
                        com.example.domain.model.NotificationType.PR_CREATED -> Color(0xFF8E24AA)
                        com.example.domain.model.NotificationType.VERIFICATION_PASSED -> Color(0xFF2E7D32)
                        com.example.domain.model.NotificationType.VERIFICATION_FAILED -> MaterialTheme.colorScheme.error
                        com.example.domain.model.NotificationType.SECURITY_ALERT -> MaterialTheme.colorScheme.secondary
                    }

                    val notifIcon = when (notif.type) {
                        com.example.domain.model.NotificationType.CRASH_ALERT -> Icons.Default.BugReport
                        com.example.domain.model.NotificationType.ANR_ALERT -> Icons.Default.HourglassBottom
                        com.example.domain.model.NotificationType.FIX_READY -> Icons.Default.AutoFixHigh
                        com.example.domain.model.NotificationType.PR_CREATED -> Icons.Default.CallMerge
                        com.example.domain.model.NotificationType.VERIFICATION_PASSED -> Icons.Default.CheckCircle
                        com.example.domain.model.NotificationType.VERIFICATION_FAILED -> Icons.Default.Cancel
                        com.example.domain.model.NotificationType.SECURITY_ALERT -> Icons.Default.Security
                    }

                    Surface(
                        onClick = {
                            viewModel.markNotificationAsRead(notif.id)
                            if (!notif.targetCrashId.isNullOrBlank()) {
                                onClose()
                                navController.navigate(Screen.CrashDetail(notif.targetCrashId))
                            }
                        },
                        color = if (!notif.isRead) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = if (!notif.isRead) 2.dp else 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = notifColor.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    notifIcon,
                                    contentDescription = null,
                                    tint = notifColor,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        notif.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (!notif.isRead) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (!notif.isRead) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape,
                                            modifier = Modifier.size(8.dp)
                                        ) {}
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    notif.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

