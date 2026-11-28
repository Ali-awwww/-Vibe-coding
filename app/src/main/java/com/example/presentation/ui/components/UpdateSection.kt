package com.example.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.util.UpdateChecker
import kotlinx.coroutines.launch

private sealed interface UpdateSectionState {
    data object Idle : UpdateSectionState
    data object Checking : UpdateSectionState
    data object UpToDate : UpdateSectionState
    data class Available(val update: UpdateChecker.UpdateInfo) : UpdateSectionState
}

/**
 * قسم «التحديثات» في شاشة الإعدادات: يعرض الإصدار الحالي وزر التحقق اليدوي
 * مع حالة التحميل، ثم «إصدارك هو الأحدث» أو «يوجد تحديث جديد X» مع زر «تحديث».
 */
@Composable
fun UpdateSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateSectionState>(UpdateSectionState.Idle) }
    val isChecking = state is UpdateSectionState.Checking

    Column(modifier = modifier.testTag("update_section")) {
        Text(
            text = "التحديثات",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(
                        R.string.settings_app_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            state = UpdateSectionState.Checking
                            val latest = UpdateChecker.fetchLatestUpdate(context)
                            state = if (latest == null) {
                                UpdateSectionState.UpToDate
                            } else {
                                UpdateSectionState.Available(latest)
                            }
                        }
                    },
                    enabled = !isChecking,
                    modifier = Modifier.testTag("update_check_button")
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "جارٍ التحقق…")
                    } else {
                        Text(text = "التحقق من وجود تحديث")
                    }
                }
                when (val currentState = state) {
                    is UpdateSectionState.Available -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "يوجد تحديث جديد ${currentState.update.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val newsLine = UpdateChecker.firstNewsLine(currentState.update.body)
                        if (newsLine.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = newsLine,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { UpdateChecker.openUpdate(context, currentState.update) },
                            modifier = Modifier.testTag("update_section_update_button")
                        ) {
                            Text(text = "تحديث")
                        }
                    }
                    is UpdateSectionState.UpToDate -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "إصدارك هو الأحدث",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("update_status_text")
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}
