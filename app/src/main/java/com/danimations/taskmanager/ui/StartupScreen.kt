package com.danimations.taskmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danimations.taskmanager.TaskManagerViewModel
import com.danimations.taskmanager.data.StartupEntry
import com.danimations.taskmanager.data.StartupStatus
import com.danimations.taskmanager.ui.theme.LocalResourceColors

private enum class StartupSort { NAME, STATUS, LAST_USED }

@Composable
fun StartupScreen(vm: TaskManagerViewModel) {
    LaunchedEffect(Unit) { vm.ensureStartupLoaded() }
    val entries by vm.startup.collectAsState()
    val loading by vm.startupLoading.collectAsState()
    val showSystem by vm.showSystem.collectAsState()
    val access by vm.hasUsageAccess.collectAsState()
    val context = LocalContext.current
    val rc = LocalResourceColors.current

    var sortKey by rememberSaveable { mutableStateOf(StartupSort.STATUS) }
    var descending by rememberSaveable { mutableStateOf(false) }

    val rows = remember(entries, sortKey, descending, showSystem) {
        val c: Comparator<StartupEntry> = when (sortKey) {
            StartupSort.NAME -> compareBy<StartupEntry> { it.label.lowercase() }
            StartupSort.STATUS -> compareBy<StartupEntry> { it.status.ordinal }.thenBy { it.label.lowercase() }
            StartupSort.LAST_USED -> compareBy<StartupEntry> { it.lastUsed }
        }
        entries.filter { showSystem || !it.isSystem }.sortedWith(if (descending) c.reversed() else c)
    }

    fun sortBy(key: StartupSort) {
        if (sortKey == key) descending = !descending else {
            sortKey = key
            descending = key == StartupSort.LAST_USED
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Apps that ask to launch when your phone starts. Android doesn't let one app switch off another's " +
                "startup, but a force-stopped app won't start again until you open it. Tap an app to manage it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        val enabled = rows.count { it.status == StartupStatus.ENABLED }
        Text(
            "$enabled of ${rows.size} enabled",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("Name", sortKey == StartupSort.NAME, descending, Modifier.weight(1f), alignEnd = false) { sortBy(StartupSort.NAME) }
            if (access) {
                HeaderCell("Last used", sortKey == StartupSort.LAST_USED, descending, Modifier.width(84.dp)) { sortBy(StartupSort.LAST_USED) }
            }
            HeaderCell("Status", sortKey == StartupSort.STATUS, descending, Modifier.width(96.dp)) { sortBy(StartupSort.STATUS) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when {
            loading && entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No startup apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.packageName }) { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { openAppInfo(context, e.packageName) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(e.packageName, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${e.receivers} startup ${if (e.receivers == 1) "trigger" else "triggers"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (access) {
                            Text(
                                if (e.lastUsed > 0) formatRelative(e.lastUsed) else "Not used",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(84.dp),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                            )
                        }
                        Text(
                            e.status.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (e.status) {
                                StartupStatus.ENABLED -> rc.good
                                StartupStatus.STOPPED -> rc.warn
                                StartupStatus.DISABLED -> MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.width(96.dp),
                            textAlign = TextAlign.End,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
