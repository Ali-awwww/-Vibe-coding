package com.example.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.R
import com.example.domain.model.*
import com.example.presentation.navigation.Screen
import com.example.presentation.viewmodel.SetupGuideViewModel
import com.example.presentation.viewmodel.SetupGuideUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupGuideScreen(
    projectId: String,
    applicationId: String,
    navController: NavController,
    viewModel: SetupGuideViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_guide_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.setup_guide_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (uiState.tech == null) {
                TechnologySelection(onTechSelected = viewModel::selectManualTech)
            } else {
                GuideContent(
                    uiState = uiState,
                    selectedService = uiState.selectedService,
                    onServiceSelected = { viewModel.generateGuide(it, projectId) },
                    isVerificationAvailable = viewModel.isVerificationTransportAvailable,
                    onVerify = { viewModel.verifyIntegration(applicationId) },
                    onResetTech = { viewModel.selectManualTech(ProjectTechnology.UNKNOWN) },
                    onOpenIntegrations = { navController.navigate(Screen.Integrations(projectId)) },
                    onOpenCrashes = { navController.navigate(Screen.Crashes(projectId = projectId, applicationId = applicationId)) }
                )
            }
        }
    }
}

@Composable
fun TechnologySelection(onTechSelected: (ProjectTechnology) -> Unit) {
    val technologies = listOf(
        ProjectTechnology.ANDROID_KOTLIN to R.string.setup_guide_tech_android_kotlin,
        ProjectTechnology.ANDROID_JAVA to R.string.setup_guide_tech_android_java,
        ProjectTechnology.IOS_SWIFT to R.string.setup_guide_tech_ios_swift,
        ProjectTechnology.IOS_OBJC to R.string.setup_guide_tech_ios_objc,
        ProjectTechnology.FLUTTER to R.string.setup_guide_tech_flutter,
        ProjectTechnology.REACT_NATIVE to R.string.setup_guide_tech_react_native,
        ProjectTechnology.UNITY to R.string.setup_guide_tech_unity
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.setup_guide_technology_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.setup_guide_technology_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.setup_guide_auto_detect_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.setup_guide_manual_choice), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        technologies.forEach { (tech, label) ->
            OutlinedButton(onClick = { onTechSelected(tech) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(stringResource(label))
            }
        }
    }
}

@Composable
fun GuideContent(
    uiState: SetupGuideUiState,
    selectedService: IntegrationType,
    onServiceSelected: (IntegrationType) -> Unit,
    isVerificationAvailable: Boolean,
    onVerify: () -> Unit,
    onResetTech: () -> Unit,
    onOpenIntegrations: () -> Unit = {},
    onOpenCrashes: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val guide = uiState.guide
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.setup_guide_current_technology), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(technologyLabel(uiState.tech), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onResetTech) { Text(stringResource(R.string.setup_guide_change_technology)) }
            }
        }
        item {
            Text(stringResource(R.string.setup_guide_progress_saved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text(stringResource(R.string.setup_guide_service_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedService == IntegrationType.SENTRY,
                    onClick = { onServiceSelected(IntegrationType.SENTRY) },
                    label = { Text(stringResource(R.string.setup_guide_service_sentry)) }
                )
                FilterChip(
                    selected = selectedService == IntegrationType.BUGSNAG,
                    onClick = { onServiceSelected(IntegrationType.BUGSNAG) },
                    label = { Text(stringResource(R.string.setup_guide_service_bugsnag)) }
                )
                FilterChip(
                    selected = selectedService == IntegrationType.FIREBASE_CRASHLYTICS,
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.testTag("setup_guide_firebase_chip"),
                    label = { Text(stringResource(R.string.setup_guide_firebase_unavailable_label)) }
                )
            }
            Text(stringResource(R.string.setup_guide_firebase_unavailable_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (uiState.error != null || uiState.errorResId != null) {
            item { Text(uiState.errorResId?.let { stringResource(it) } ?: uiState.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        }
        if (guide == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    if (uiState.isScanning) CircularProgressIndicator()
                    else Button(onClick = { onServiceSelected(selectedService) }) { Text(stringResource(R.string.setup_guide_show_steps)) }
                }
            }
        } else {
            if (guide.prerequisites.isNotEmpty()) item { PrerequisitesSection(guide.prerequisites) }
            guide.documentationUrl?.let { url ->
                item {
                    TextButton(onClick = { uriHandler.openUri(url) }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.setup_guide_open_documentation))
                    }
                }
            }
            guide.unavailableReason?.let { reason ->
                item { Text(guideText(reason), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
            itemsIndexed(guide.steps, key = { _, step -> step.id }) { index, step ->
                StepCard(step, index + 1)
            }
            if (guide.steps.isNotEmpty()) {
                if (guide.troubleshooting.isNotEmpty()) item { TroubleshootingSection(guide.troubleshooting) }
                item { AutomatedChangesSection() }
                item {
                    VerificationSection(
                        isVerifying = uiState.isVerifying,
                        result = uiState.verificationResult,
                        isVerificationAvailable = isVerificationAvailable,
                        error = uiState.error,
                        errorResId = uiState.errorResId,
                        onVerify = onVerify
                    )
                }
                item { NavigationLinksSection(onOpenIntegrations, onOpenCrashes) }
            }
        }
    }
}

@Composable
fun StepCard(step: IntegrationStep, number: Int) {
    val clipboard = LocalClipboardManager.current
    val titleRes = stepTitleResource(step.id)
    val descriptionRes = stepDescriptionResource(step.id)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(number.toString(), fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.width(10.dp))
                Text(if (titleRes != null) stringResource(titleRes) else step.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(if (descriptionRes != null) stringResource(descriptionRes) else step.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            step.fileToModify?.let { Text(stringResource(R.string.setup_guide_file_label, it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            step.codeSnippet?.let { code ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.setup_guide_configuration_label), style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.setup_guide_copy_code))
                            }
                        }
                        Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.setup_guide_step_validation_pending), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            Text(stringResource(R.string.setup_guide_step_validation_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PrerequisitesSection(prerequisites: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_guide_requirements_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            prerequisites.forEach { Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(guideText(it), style = MaterialTheme.typography.bodySmall)
            } }
        }
    }
}

@Composable
fun TroubleshootingSection(issues: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.setup_guide_troubleshooting_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            issues.forEach { issue -> Row(Modifier.padding(top = 4.dp)) {
                Text("• ", fontWeight = FontWeight.Bold)
                Text(guideText(issue), style = MaterialTheme.typography.bodySmall)
            } }
        }
    }
}

@Composable
fun AutomatedChangesSection() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.setup_guide_automated_changes_unavailable_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.setup_guide_automated_changes_unavailable_body), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun VerificationSection(
    isVerifying: Boolean,
    result: Boolean?,
    isVerificationAvailable: Boolean,
    error: String?,
    errorResId: Int?,
    onVerify: () -> Unit
) {
    val verificationError = errorResId?.let { stringResource(it) } ?: error
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.setup_guide_verification_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.setup_guide_verification_body), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            when {
                isVerifying -> CircularProgressIndicator(Modifier.size(24.dp))
                result == true -> Text(stringResource(R.string.setup_guide_verification_success), color = MaterialTheme.colorScheme.primary)
                verificationError != null -> Text(verificationError, color = MaterialTheme.colorScheme.error)
                result == false -> Text(stringResource(R.string.setup_guide_verification_failed), color = MaterialTheme.colorScheme.error)
                !isVerificationAvailable -> Text(stringResource(R.string.setup_guide_verification_unavailable), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onVerify, enabled = !isVerifying && isVerificationAvailable, modifier = Modifier.testTag("setup_guide_verify_button")) {
                Text(if (isVerificationAvailable) stringResource(R.string.setup_guide_verification_start) else stringResource(R.string.setup_guide_verification_unavailable_button))
            }
        }
    }
}

@Composable
fun NavigationLinksSection(onOpenIntegrations: () -> Unit, onOpenCrashes: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.setup_guide_navigation_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onOpenIntegrations, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Extension, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.setup_guide_open_integrations))
            }
            OutlinedButton(onClick = onOpenCrashes, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.setup_guide_open_crashes))
            }
        }
    }
}

@Composable
private fun guideText(key: String): String {
    val res = when (key) {
        "setup_guide_sentry_prerequisite_account" -> R.string.setup_guide_sentry_prerequisite_account
        "setup_guide_sentry_prerequisite_platform" -> R.string.setup_guide_sentry_prerequisite_platform
        "setup_guide_bugsnag_prerequisite_account" -> R.string.setup_guide_bugsnag_prerequisite_account
        "setup_guide_bugsnag_prerequisite_platform" -> R.string.setup_guide_bugsnag_prerequisite_platform
        "setup_guide_troubleshooting_sentry_dsn" -> R.string.setup_guide_troubleshooting_sentry_dsn
        "setup_guide_troubleshooting_sentry_token" -> R.string.setup_guide_troubleshooting_sentry_token
        "setup_guide_troubleshooting_bugsnag_key" -> R.string.setup_guide_troubleshooting_bugsnag_key
        "setup_guide_troubleshooting_bugsnag_application" -> R.string.setup_guide_troubleshooting_bugsnag_application
        "setup_guide_platform_unsupported" -> R.string.setup_guide_platform_unsupported
        "setup_guide_firebase_unavailable_body" -> R.string.setup_guide_firebase_unavailable_body
        else -> R.string.setup_guide_service_unavailable
    }
    return stringResource(res)
}

private fun stepTitleResource(id: String): Int? = when (id) {
    "sentry_install" -> R.string.setup_guide_sentry_step_install_title
    "sentry_credentials" -> R.string.setup_guide_sentry_step_protect_title
    "sentry_verify" -> R.string.setup_guide_sentry_step_verify_title
    "sentry_dashboard" -> R.string.setup_guide_sentry_step_dashboard_title
    "bugsnag_dependency" -> R.string.setup_guide_bugsnag_step_dependency_title
    "bugsnag_manifest" -> R.string.setup_guide_bugsnag_step_manifest_title
    "bugsnag_initialize" -> R.string.setup_guide_bugsnag_step_initialize_title
    "bugsnag_verify" -> R.string.setup_guide_bugsnag_step_verify_title
    else -> null
}

private fun stepDescriptionResource(id: String): Int? = when (id) {
    "sentry_install" -> R.string.setup_guide_sentry_step_install_description
    "sentry_credentials" -> R.string.setup_guide_sentry_step_protect_description
    "sentry_verify" -> R.string.setup_guide_sentry_step_verify_description
    "sentry_dashboard" -> R.string.setup_guide_sentry_step_dashboard_description
    "bugsnag_dependency" -> R.string.setup_guide_bugsnag_step_dependency_description
    "bugsnag_manifest" -> R.string.setup_guide_bugsnag_step_manifest_description
    "bugsnag_initialize" -> R.string.setup_guide_bugsnag_step_initialize_description
    "bugsnag_verify" -> R.string.setup_guide_bugsnag_step_verify_description
    else -> null
}

@Composable
private fun technologyLabel(tech: ProjectTechnology?): String = stringResource(
    when (tech) {
        ProjectTechnology.ANDROID_KOTLIN -> R.string.setup_guide_tech_android_kotlin
        ProjectTechnology.ANDROID_JAVA -> R.string.setup_guide_tech_android_java
        ProjectTechnology.IOS_SWIFT -> R.string.setup_guide_tech_ios_swift
        ProjectTechnology.IOS_OBJC -> R.string.setup_guide_tech_ios_objc
        ProjectTechnology.FLUTTER -> R.string.setup_guide_tech_flutter
        ProjectTechnology.REACT_NATIVE -> R.string.setup_guide_tech_react_native
        ProjectTechnology.UNITY -> R.string.setup_guide_tech_unity
        else -> R.string.setup_guide_unknown_technology
    }
)
