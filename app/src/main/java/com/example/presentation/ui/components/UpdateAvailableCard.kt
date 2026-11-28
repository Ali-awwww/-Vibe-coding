package com.example.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.util.UpdateChecker

/**
 * مضيف فحص التحديث للوحة التحكم: يفحص صامتاً عند فتح التطبيق،
 * ويعرض بطاقة التحديث عند وجود إصدار أحدث غير مثبّت.
 */
@Composable
fun UpdateCheckHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var availableUpdate by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        availableUpdate = UpdateChecker.checkUpdateForLaunch(context)
    }

    val update = availableUpdate ?: return
    UpdateAvailableCard(
        update = update,
        onDismissToday = {
            UpdateChecker.dismissUpdateForToday(context, update.version)
            availableUpdate = null
        },
        modifier = modifier
    )
}

/**
 * بطاقة «يوجد تحديث جديد»: سطر واحد من الجديد مع زرّي «تحديث» و«لا تذكرني اليوم».
 */
@Composable
fun UpdateAvailableCard(
    update: UpdateChecker.UpdateInfo,
    onDismissToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val newsLine = UpdateChecker.firstNewsLine(update.body)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("update_available_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "يوجد تحديث جديد ${update.version}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (newsLine.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = newsLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismissToday,
                    modifier = Modifier.testTag("update_card_dismiss_button")
                ) {
                    Text(text = "لا تذكرني اليوم")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { UpdateChecker.openUpdate(context, update) },
                    modifier = Modifier.testTag("update_card_update_button")
                ) {
                    Text(text = "تحديث")
                }
            }
        }
    }
}
